package com.example.earthonline.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * v1.0.0：人生分享卡片。
 *
 * ## 为什么用原生 Canvas 而不是「Compose 组件截图」
 * 1. 截图方案要先等 Compose 完成一帧布局，再 `draw` 进 Bitmap，中间多一次完整合成，
 *    在真机上是一次肉眼可见的卡顿（尤其卡片里有图片时）；
 * 2. 截图依赖 Composable 上下文，生成时机被绑死在 UI 上，后台预生成做不了；
 * 3. 原生 Canvas 直接画 1080×1440 一张图，实测十几毫秒，且不参与任何重组。
 * 代价是要自己算坐标，但卡片版式是固定的，算一次即可。
 *
 * 画布按 1080 宽设计（分享到微信 / QQ 都够清晰，文件约 200~400KB）。
 */
data class ShareCardData(
    val name: String,
    val level: Int,
    /** 距离下一级的进度 0~1（无生日时为 0） */
    val levelProgress: Float,
    val achievements: Int,
    val totalAchievements: Int,
    val tasksDone: Int,
    val signature: String,
    // ————— v1.0.3 扩展 —————
    /** 称号（自定义优先，默认「旅行者」） */
    val title: String = "",
    /** 当前连续记录天数（0 = 隐藏该栏） */
    val streakDays: Int = 0,
    /** 本月关键词（如「本月 42 次记录」，空 = 隐藏该栏） */
    val monthKeyword: String = "",
    /** 模板：1=暖米(默认) 2=深夜 3=樱粉 */
    val template: Int = 1
)

object ShareCardRenderer {

    private const val W = 1080
    private const val H = 1440

    /** v1.0.3 三套模板配色：暖米（治愈系）/ 深夜 / 樱粉 */
    private class Palette(
        val bgTop: Int, val bgBottom: Int, val card: Int,
        val accent: Int, val accentSoft: Int, val ink: Int, val ink2: Int
    )

    private val P_WARM = Palette(
        Color.parseColor("#FDF8F0"), Color.parseColor("#F3EADC"), Color.WHITE,
        Color.parseColor("#D4A373"), Color.parseColor("#F0E2D2"),
        Color.parseColor("#1E1A16"), Color.parseColor("#7A7268")
    )
    private val P_NIGHT = Palette(
        Color.parseColor("#1C1B22"), Color.parseColor("#26242E"), Color.parseColor("#2E2C38"),
        Color.parseColor("#E0B589"), Color.parseColor("#3A3746"),
        Color.parseColor("#F2EFE9"), Color.parseColor("#A8A1B3")
    )
    private val P_SAKURA = Palette(
        Color.parseColor("#FDF1F4"), Color.parseColor("#F7DFE6"), Color.WHITE,
        Color.parseColor("#D98BA4"), Color.parseColor("#F4D8E0"),
        Color.parseColor("#3A2B30"), Color.parseColor("#9A8189")
    )

    private fun paletteOf(template: Int): Palette = when (template) {
        2 -> P_NIGHT
        3 -> P_SAKURA
        else -> P_WARM
    }

    /** 生成卡片位图。调用方负责 recycle（生成后立刻写文件，写完即可回收）。 */
    fun render(data: ShareCardData, avatarFile: File? = null): Bitmap {
        val P = paletteOf(data.template)
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        // 背景：竖向渐变
        c.drawRect(
            0f, 0f, W.toFloat(), H.toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, 0f, H.toFloat(), P.bgTop, P.bgBottom, Shader.TileMode.CLAMP)
            }
        )
        // 两个柔光装饰圆（低透明度，只做氛围）
        val deco = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = P.accent; alpha = 36 }
        c.drawCircle(W - 100f, 150f, 240f, deco)
        c.drawCircle(80f, H - 160f, 280f, deco)

        // 主体卡片
        val cardTop = 180f
        val cardBottom = H - 180f
        c.drawRoundRect(
            RectF(72f, cardTop, (W - 72).toFloat(), cardBottom.toFloat()),
            44f, 44f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = P.card }
        )

        // 顶部品牌行
        val brand = textPaint(28f, P.ink2)
        drawCenter(c, "地 球 O n l i n e", W / 2f, cardTop + 78f, brand)

        // 头像（圆形 + 描边）
        val avatarSize = 240f
        val cx = W / 2f
        val avatarTop = cardTop + 124f
        drawAvatar(c, avatarFile, cx, avatarTop, avatarSize, P)

        // 名字 + 称号
        val namePaint = textPaint(52f, P.ink, bold = true)
        drawCenter(c, data.name.ifBlank { "地球玩家" }, cx, avatarTop + avatarSize + 92f, namePaint)
        if (data.title.isNotBlank()) {
            drawCenter(
                c, "「${data.title}」", cx, avatarTop + avatarSize + 138f,
                textPaint(30f, P.accent)
            )
        }

        // 等级
        val levelPaint = textPaint(66f, P.accent, bold = true)
        drawCenter(c, "Lv.${data.level}", cx, avatarTop + avatarSize + 214f, levelPaint)

        // 经验条（距下一级进度）
        val barTop = avatarTop + avatarSize + 248f
        val barLeft = 190f
        val barRight = (W - 190).toFloat()
        val barH = 18f
        c.drawRoundRect(
            RectF(barLeft, barTop, barRight, barTop + barH),
            barH / 2f, barH / 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = P.accentSoft }
        )
        val p = data.levelProgress.coerceIn(0f, 1f)
        if (p > 0f) {
            c.drawRoundRect(
                RectF(barLeft, barTop, barLeft + (barRight - barLeft) * p, barTop + barH),
                barH / 2f, barH / 2f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = P.accent }
            )
        }

        // 四个数据块：成就 / 完成任务 / 连续记录 / 本月关键词
        val statTop = barTop + 86f
        val gap = W / 4f
        drawStat(c, "成就", "${data.achievements}/${data.totalAchievements}", gap * 0.5f, statTop, P)
        drawStat(c, "任务", "${data.tasksDone}", gap * 1.5f, statTop, P)
        if (data.streakDays > 0) {
            drawStat(c, "连续", "${data.streakDays} 天", gap * 2.5f, statTop, P)
        } else {
            drawStat(c, "🔥 连续", "—", gap * 2.5f, statTop, P)
        }
        if (data.monthKeyword.isNotBlank()) {
            drawStat(c, "本月", data.monthKeyword.take(6), gap * 3.5f, statTop, P)
        } else {
            drawStat(c, "📅 本月", "—", gap * 3.5f, statTop, P)
        }

        // 签名（多行居中，超长自动换行，最多 3 行）
        val sigTop = statTop + 150f
        val sig = data.signature.trim()
        if (sig.isNotBlank()) {
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = P.ink2
                textSize = 32f
            }
            val layout = StaticLayout.Builder
                .obtain(sig, 0, sig.length, tp, 760)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setMaxLines(3)
                .setEllipsize(android.text.TextUtils.TruncateAt.END)
                .build()
            c.save()
            c.translate(90f, sigTop)
            layout.draw(c)
            c.restore()
        }

        // 底部签名行
        val footer = textPaint(26f, P.ink2)
        drawCenter(c, "把人生当成一场开放世界游戏", cx, cardBottom - 70f, footer)
        drawCenter(c, todayStr(), cx, cardBottom - 34f, footer)

        return bmp
    }

    /**
     * @param file 头像**原图文件**（4K 也原样存着）。这里只需小尺寸的圆，
     *             所以按目标尺寸降采样解码 —— 原图虽大，进内存的只有需要的那点像素。
     */
    private fun drawAvatar(c: Canvas, file: File?, cx: Float, top: Float, size: Float, P: Palette) {
        val r = size / 2f
        // 外圈：淡底（头像透明或加载失败时也不至于是个洞）
        c.drawCircle(cx, top + r, r + 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = P.accentSoft })
        val avatar = file?.let { ImageDownsampler.decodeSampledFile(it, size.toInt()) }
        if (avatar != null) {
            /* 居中裁剪：原图可能是任意长宽比（手机照片普遍 4:3），
               按「填满圆」缩放后把正中间那一块贴进圆里，不留透明带。 */
            val scale = maxOf(size / avatar.width, size / avatar.height)
            val dx = (size - avatar.width * scale) / 2f
            val dy = (size - avatar.height * scale) / 2f
            val shader = BitmapShader(avatar, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            shader.setLocalMatrix(
                Matrix().apply {
                    setScale(scale, scale)
                    postTranslate(cx - r + dx, top + dy)
                }
            )
            c.drawCircle(
                cx, top + r, r,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader; isFilterBitmap = true }
            )
            avatar.recycle()
        } else {
            // 没有上传头像时画默认地球 emoji
            val p = textPaint(110f, P.accent)
            drawCenter(c, "地", cx, top + r + 40f, p)
        }
        // 描边
        c.drawCircle(
            cx, top + r, r,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = P.accent
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
        )
    }

    private fun drawStat(c: Canvas, label: String, value: String, cx: Float, top: Float, P: Palette) {
        drawCenter(c, label, cx, top, textPaint(26f, P.ink2))
        drawCenter(c, value, cx, top + 50f, textPaint(38f, P.ink, bold = true))
    }

    private fun textPaint(size: Float, color: Int, bold: Boolean = false): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            textAlign = Paint.Align.CENTER
            typeface = if (bold) Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD) else Typeface.DEFAULT
        }

    private fun drawCenter(c: Canvas, text: String, cx: Float, baselineY: Float, paint: Paint) {
        c.drawText(text, cx, baselineY, paint)
    }

    /* ---------------- 落盘 / 分享 / 存相册 ---------------- */

    /** 写到 cache 目录（FileProvider 已配置 cache-path），返回文件 */
    fun saveToCache(context: Context, bmp: Bitmap, fileName: String = "earth_card.png"): File? {
        return try {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            // 只留最新一张，避免反复生成把 cache 撑大
            dir.listFiles()?.forEach { if (it.isFile) it.delete() }
            val f = File(dir, fileName)
            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
            f
        } catch (e: Exception) {
            null
        }
    }

    /** 用 FileProvider 包出可分享的 Uri（给其他 App 临时读权限） */
    fun shareUri(context: Context, file: File): Uri? =
        runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()

    /** ACTION_SEND 分享意图（type=image/png） */
    fun shareIntent(context: Context, file: File, extraText: String = "我在地球Online 的人生存档"): Intent? {
        val uri = shareUri(context, file) ?: return null
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, extraText)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(intent, "分享人生卡片")
    }

    /**
     * 保存到系统相册（Pictures/地球Online）。
     * Android 10+ 走 MediaStore（无需权限）；9 及以下需要 WRITE_EXTERNAL_STORAGE，
     * manifest 里已按 maxSdkVersion=28 声明。
     */
    fun saveToGallery(context: Context, bmp: Bitmap, displayName: String): Boolean {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/地球Online")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            val ok = context.contentResolver.openOutputStream(uri)?.use {
                bmp.compress(Bitmap.CompressFormat.PNG, 95, it)
            } != null
            if (!ok) return false
            if (Build.VERSION.SDK_INT >= 29) {
                val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                context.contentResolver.update(uri, done, null, null)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
