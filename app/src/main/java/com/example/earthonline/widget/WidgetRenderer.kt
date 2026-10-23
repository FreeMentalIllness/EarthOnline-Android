package com.example.earthonline.widget

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import android.appwidget.AppWidgetManager
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.example.earthonline.MainActivity
import com.example.earthonline.R
import com.example.earthonline.data.local.entity.ProfileEntity
import com.example.earthonline.data.repository.AchievementRepository
import com.example.earthonline.data.repository.LocationRepository
import com.example.earthonline.data.repository.MemoRepository
import com.example.earthonline.data.repository.ProfileRepository
import com.example.earthonline.data.repository.TaskRepository
import com.example.earthonline.ui.navigation.Screen
import com.example.earthonline.util.ImageDownsampler
import com.example.earthonline.util.XpRules
import com.example.earthonline.util.lifeStatsOf
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 小组件渲染：读一次库 -> 生成一份 RemoteViews -> 推给所有已添加的实例。
 *
 * 依赖通过 Hilt 的 @EntryPoint 拿，而不是给 AppWidgetProvider 加 @AndroidEntryPoint：
 * BroadcastReceiver 的注入发生在 super.onReceive 之前、且和 goAsync 的生命周期纠缠，
 * 显式取 EntryPoint 时机更可控，也避免 Hilt 给 receiver 生成的包装类与 AppWidgetProvider 冲突。
 */
object WidgetRenderer {

    /** 头像渲染尺寸（px）。小组件里只显示 40dp，96px 足够清晰又不浪费内存 */
    private const val AVATAR_PX = 96

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetDeps {
        fun profileRepo(): ProfileRepository
        fun taskRepo(): TaskRepository
        fun achRepo(): AchievementRepository
        fun memoRepo(): MemoRepository
        fun locationRepo(): LocationRepository
        fun itemRepo(): com.example.earthonline.data.repository.ItemRepository
        fun settings(): com.example.earthonline.data.local.datastore.SettingsDataStore
        fun json(): kotlinx.serialization.json.Json
    }

    /** 刷新所有已添加的小组件。没添加时直接返回（一次 IPC 都不做，这是省电的一部分） */
    suspend fun updateAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, OverviewWidget::class.java))
        if (ids.isEmpty()) return
        val views = build(context)
        // 同一份 RemoteViews 可以复用给多个实例（内部是逐次 IPC 拷贝）
        ids.forEach { mgr.updateAppWidget(it, views) }
    }

    suspend fun build(context: Context): RemoteViews {
        val deps = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetDeps::class.java
        )

        /* 每一项都单独兜底：任何一张表查询失败都不该让整个小组件变成空壳。
           ⚠️ 注意第一个参数的语义是「失败时的默认值」，别再写 `fetch(repo.get()) { null }`
           —— 那样默认值虽被求值，但 block 恒为 null，结果永远是 null（曾因此让小组件一直显示"未设置生日"）。 */
        val profile = fetch<ProfileEntity?>(null) { deps.profileRepo().get() }
        val doneTasks = fetch(0) { deps.taskRepo().doneCount().first() }
        val unlocked = fetch(0) { deps.achRepo().unlockedCount().first() }
        val memos = fetch(0) { deps.memoRepo().count().first() }
        val locations = fetch(0) { deps.locationRepo().count().first() }
        // v1.0.3：经验来源多元化（物品 / 记忆照片），任一失败不影响小组件其余内容
        val items = fetch(0) { deps.itemRepo().count().first() }
        val photos = fetch(0) {
            val txt = deps.settings().memoryPhotosJson.first()
            runCatching {
                (deps.json().parseToJsonElement(txt) as? kotlinx.serialization.json.JsonArray)?.size ?: 0
            }.getOrDefault(0)
        }

        val stats = lifeStatsOf(profile?.birthDate)
        val xp = XpRules.totalXp(doneTasks, unlocked, memos, locations, items, photos)

        val views = RemoteViews(context.packageName, R.layout.widget_overview)

        val name = profile?.name?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.widget_default_name)
        views.setTextViewText(R.id.widget_name, name)

        views.setTextViewText(
            R.id.widget_level,
            if (stats.hasBirth) {
                "Lv.${stats.age} · 已存活 ${stats.daysLived} 天 · 距 ${stats.age + 1} 岁 ${stats.daysToNext} 天"
            } else {
                "Lv.1 · 设置生日后显示真实等级"
            }
        )
        views.setTextViewText(R.id.widget_xp, "$xp XP")
        // 进度条：生日周期进度（距下一岁走过的比例）；未设生日时为 0，条是空的
        views.setProgressBar(
            R.id.widget_xp_bar, 100,
            (stats.progress * 100).roundToInt().coerceIn(0, 100),
            false
        )

        // 头像：上传过图片就画圆形 Bitmap，否则落回预设 emoji
        // v1.0.0：优先读**原图文件**（4K 也存着原图）；文件不在（换设备/备份没带图）时回落旧字节
        val avatar = ImageDownsampler.decodeAvatar(
            profile?.avatarPath, profile?.avatarData, AVATAR_PX
        )?.let { roundAvatar(it, AVATAR_PX) }
        // v1.0.6：不再用 emoji 兜底 —— 无头像时直接用默认头像位图（布局里已写好 src）
        if (avatar != null) {
            views.setImageViewBitmap(R.id.widget_avatar, avatar)
        } else {
            views.setImageViewResource(R.id.widget_avatar, R.drawable.ic_default_avatar)
        }

        // 四个入口图标：RemoteViews 对矢量支持不一致（不同桌面 / 系统版本表现不同），
        // 所以这里把矢量在渲染阶段画成位图再塞进 ImageView，杜绝 emoji 与矢量兼容问题。
        views.setImageViewBitmap(R.id.widget_icon_tasks, iconBitmap(context, R.drawable.ic_widget_tasks))
        views.setImageViewBitmap(
            R.id.widget_icon_backpack,
            iconBitmap(context, R.drawable.ic_widget_backpack)
        )
        views.setImageViewBitmap(
            R.id.widget_icon_achievements,
            iconBitmap(context, R.drawable.ic_widget_achievements)
        )
        views.setImageViewBitmap(R.id.widget_icon_data, iconBitmap(context, R.drawable.ic_widget_data))

        // 整块背景 -> 打开主页
        views.setOnClickPendingIntent(R.id.widget_root, openIntent(context, Screen.Home.route))
        views.setOnClickPendingIntent(R.id.widget_entry_tasks, openIntent(context, Screen.Tasks.route))
        views.setOnClickPendingIntent(R.id.widget_entry_backpack, openIntent(context, Screen.Backpack.route))
        views.setOnClickPendingIntent(
            R.id.widget_entry_achievements,
            openIntent(context, Screen.Achievements.route)
        )
        views.setOnClickPendingIntent(R.id.widget_entry_data, openIntent(context, Screen.Data.route))

        return views
    }

    /**
     * 把矢量图标画成位图。
     *
     * ⚠️ 为什么不能直接 `setImageViewResource` 传矢量：RemoteViews 是在**桌面进程**里 inflate 的，
     * 不同桌面 / 系统版本对 VectorDrawable 的支持不一致（历史上出现过直接 inflate 失败），
     * 而位图是 100% 通用的。所以入口图标统一在这里预渲染成 ARGB_8888 位图再塞给 ImageView。
     */
    private fun iconBitmap(context: Context, id: Int): Bitmap? {
        val d: Drawable = runCatching {
            ResourcesCompat.getDrawable(context.resources, id, context.theme)
        }.getOrNull() ?: return null
        val size = (18f * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, size, size)
        d.draw(Canvas(bmp))
        return bmp
    }

    /** 单项查询的兜底：异常时返回默认值，不让整块小组件挂掉 */
    private suspend fun <T> fetch(default: T, block: suspend () -> T): T =
        runCatching { block() }.getOrDefault(default)

    /**
     * 打开指定页面的 PendingIntent。
     *
     * ⚠️ 两个坑：
     *  ① PendingIntent 的判等**不看 extra**，只看 action / data / type / class / categories。
     *     四个入口如果只靠 extra 区分，后创建的会把前面的覆盖掉 —— 四个按钮全跳同一个页面。
     *     所以这里给每个路由配了唯一的 data URI。
     *  ② FLAG_IMMUTABLE 在 targetSdk 31+ 是强制的（不写会直接抛异常）。
     */
    private fun openIntent(context: Context, route: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.example.earthonline.widget.open"
            data = Uri.parse("earthonline://widget/$route")
            putExtra(MainActivity.EXTRA_ROUTE, route)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            route.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * 把位图裁成 **圆形**。
     * RemoteViews 的 ImageView 没法做圆形裁剪（没有 outlineProvider / 没有 ShapeableImageView），
     * 所以圆形必须在渲染阶段就画好。入参已经是按 AVATAR_PX 降采样过的（见 ImageDownsampler.decodeAvatar），
     * 这里只负责裁形 —— 小组件渲染跑在广播 / 后台进程里，绝不能解 4K 原图的全尺寸位图。
     */
    private fun roundAvatar(src: Bitmap, sizePx: Int): Bitmap? {
        if (src.width <= 0 || src.height <= 0) return null
        val result = runCatching {
            val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val side = min(src.width, src.height)
            val left = (src.width - side) / 2
            val top = (src.height - side) / 2
            val scale = sizePx.toFloat() / side
            val matrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(-left * scale, -top * scale)
            }
            val shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            shader.setLocalMatrix(matrix)
            paint.shader = shader
            canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)
            out
        }
        // 中间的大图用完即弃：小组件渲染跑在广播/后台进程里，别留着几十 MB 的位图占坑
        src.recycle()
        return result.getOrNull()
    }

}
