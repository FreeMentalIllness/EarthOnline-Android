package com.example.earthonline.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 键值型设置（对应 HTML 的 localStorage / EOStore 简单键）：
 *  - onboarding_done  -> 原“首次引导”分流（本端由它决定是否进引导页）
 *  - earth_theme     -> 原键 `earth_theme`（light / dark / system）
 *  - earth_wallpaper -> 原键 `earth_wallpaper`（配置 JSON；图片二进制存沙盒，不进此表）
 *  - earth_notify    -> 原键 `earth_notify`（到期提醒开关）
 *  - ai_config       -> 原 state.aiConfig（baseUrl/apiKey/model 的 JSON）
 *  - webdav_config   -> 原键 `earth_online_webdav_v1`（统一收口到 DataStore，不再明文裸存 localStorage）
 *  - earth_autosync  -> 自动同步开关（启动拉取 / 切后台推送）
 *  - earth_last_sync -> 上次同步时间（ISO，冲突比对基准：与云端 exportedAt 比大小）
 */
@Singleton
class SettingsDataStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val ds = context.dataStore

    companion object {
        /**
         * DataStore 委托必须放 companion（类级单例）而不是类体：
         * BootReceiver 会手动 new SettingsDataStore(context)（绕过 Hilt），
         * 委托在类体时每个实例各自持有一个 DataStore，同一文件双实例直接崩
         *（IllegalStateException: multiple DataStores active for the same file）。
         */
        private val Context.dataStore by preferencesDataStore(name = "earth_settings")

        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val THEME = stringPreferencesKey("earth_theme")
        val WALLPAPER = stringPreferencesKey("earth_wallpaper")
        val WALLPAPER_ALPHA = floatPreferencesKey("earth_wallpaper_alpha")
        /** v1.0.11：壁纸模糊度（dp，0~24） */
        val WALLPAPER_BLUR = floatPreferencesKey("earth_wallpaper_blur")
        /** v1.0.18：卡片背景不透明度（0.40~0.95，仅作用于全局 GlassCard） */
        val CARD_OPACITY = floatPreferencesKey("earth_card_opacity")
        val NOTIFY = booleanPreferencesKey("earth_notify")
        val AI_CONFIG = stringPreferencesKey("ai_config")
        val WEBDAV_CONFIG = stringPreferencesKey("webdav_config")
        val AUTO_SYNC = booleanPreferencesKey("earth_autosync")
        val LAST_SYNC = stringPreferencesKey("earth_last_sync")
        /** v1.2.0：彩蛋计数器（对应 Web 的 state.eggs）。只有无法从数据推导的事件才存这里。 */
        val EGG_BLANK_TITLE = intPreferencesKey("egg_blank_title")
        /**
         * v1.2.0 引入的字号档位键。v1.0.4 起字号设置已移除（跟随系统字体缩放），
         * 键保留仅作历史数据占位，不再读取也不再写入。
         */
        val FONT_SCALE = stringPreferencesKey("earth_font_scale")
        /** v1.2.1：成就解锁音效开关（对应 Web 的 earth_ach_sound） */
        val ACH_SOUND = booleanPreferencesKey("earth_ach_sound")
        /** 主页「人生时间轴」用户自定义里程碑事件（JSON 列表，避免动 DB 表结构） */
        val CUSTOM_TIMELINE = stringPreferencesKey("custom_timeline_events")
        /** 主页「最近动态」显示类型筛选（JSON 集合：task/ach/item/memo，全空则显示全部） */
        val HOME_FEED_FILTER = stringPreferencesKey("home_feed_filter")
        /** 主页「最近动态」首页显示条数（0 = 不限制，默认 3） */
        val HOME_FEED_LIMIT = intPreferencesKey("home_feed_limit")
        /** 主页速览（概览）自定义入口开关（JSON 字符串集合：tasks/backpack/achievements/map/accounting/ai） */
        val HOME_QUICK_ENTRIES = stringPreferencesKey("home_quick_entries")

        /* ————— v1.0.3 体验升级批次 ————— */
        /** 用户自定义称号（空 = 用默认「旅行者」） */
        val CUSTOM_TITLE = stringPreferencesKey("custom_title")
        /** 记忆相册照片元数据（JSON 列表；图片文件在 filesDir/photos，不进这里） */
        val MEMORY_PHOTOS = stringPreferencesKey("memory_photos")
        /** 主页壁纸从记忆相册随机轮换开关 */
        val WALLPAPER_ROTATE = booleanPreferencesKey("wallpaper_rotate")
        /** 主页佩戴徽章（JSON 字符串数组：成就 id，最多 3 枚） */
        val HOME_BADGES = stringPreferencesKey("home_badges")
        /** v1.0.6：记忆相册自定义分类（JSON 列表；零 Room 迁移，规避数据损坏风险） */
        val ALBUMS = stringPreferencesKey("photo_albums")
        /** v1.0.6：壁纸轮换指定相册 id（空串 = 全部照片，取消 5 张数量限制） */
        val WALLPAPER_ALBUM = stringPreferencesKey("wallpaper_album")
        /** 通知栏常驻「快捷记录」开关 */
        val QUICK_NOTIF = booleanPreferencesKey("quick_add_notif")
        /** 彩蛋计数器：查看「历年今日」卡片的次数 */
        val EGG_THROWBACK = intPreferencesKey("egg_throwback")
        /** 已庆祝过的等级（= 周岁）；-1 = 从未记录（首次记录不庆祝，避免升级后首启误弹） */
        val LAST_CELEBRATED_LEVEL = intPreferencesKey("last_celebrated_level")
        /** v1.0.4：任务列表「隐藏已完成任务」开关（对齐 Web 端 v1.0.3 同名能力） */
        val HIDE_DONE_TASKS = booleanPreferencesKey("hide_done_tasks")
        /** v1.0.5 清空数据防拉回：置位后下一次非手动自动拉取被跳过（消费一次即失效，对齐 Web skip_next_pull） */
        val SKIP_NEXT_PULL = booleanPreferencesKey("skip_next_pull")
        /** v1.0.6：高德 Web 服务 Key（地图搜索 / 路径规划走 REST，与 Android 地图 Key 是两套） */
        val AMAP_WEB_KEY = stringPreferencesKey("amap_web_key")
        /**
         * v1.0.7：液态玻璃特效（Liquid Glass）全局开关。
         * - true（默认）：启用毛玻璃背景模糊 + 高光描边 + 半透明折射观感；
         * - false（性能模式）：去掉所有高斯模糊 / RenderEffect，降级为高对比度半透明纯色底板，
         *   保障低端机与旧机型绝对流畅。读取方见 ui/theme/Glass.kt 的 GlassSurface。
         */
        val LIQUID_GLASS = booleanPreferencesKey("liquid_glass")
    }

    val onboardingDone: Flow<Boolean> = ds.data.map { it[ONBOARDING_DONE] ?: false }
    val theme: Flow<String> = ds.data.map { it[THEME] ?: "system" }
    val wallpaper: Flow<String> = ds.data.map { it[WALLPAPER] ?: "" }

    /** 壁纸不透明度 0.05 ~ 0.90（0 = 完全看不见，1 = 完全遮挡内容，故上限收紧） */
    val wallpaperAlpha: Flow<Float> = ds.data.map { (it[WALLPAPER_ALPHA] ?: 0.35f).coerceIn(0.05f, 0.9f) }

    /** v1.0.11：壁纸模糊度 0~24dp（0 = 清晰，越大越毛玻璃；用户自平衡美观与性能） */
    val wallpaperBlur: Flow<Float> = ds.data.map { (it[WALLPAPER_BLUR] ?: 0f).coerceIn(0f, 24f) }

    /** v1.0.18：卡片背景不透明度（0.40~0.95，默认 0.74 视觉基线；低=通透感，高=可读性） */
    val cardOpacity: Flow<Float> = ds.data.map { (it[CARD_OPACITY] ?: 0.74f).coerceIn(0.40f, 0.95f) }
    val notify: Flow<Boolean> = ds.data.map { it[NOTIFY] ?: false }
    val aiConfig: Flow<String> = ds.data.map { it[AI_CONFIG] ?: "" }
    val webdavConfig: Flow<String> = ds.data.map { it[WEBDAV_CONFIG] ?: "" }
    /** 自动同步开关：默认开启（配置好 WebDAV 即生效，未配置时不会发起任何请求） */
    val autoSync: Flow<Boolean> = ds.data.map { it[AUTO_SYNC] ?: true }
    /** 上次同步时间（空串 = 从未同步过） */
    val lastSyncAt: Flow<String> = ds.data.map { it[LAST_SYNC] ?: "" }

    /** 彩蛋：空白标题保存被拒次数（≥3 解锁「空白也是一种态度」） */
    val eggBlankTitle: Flow<Int> = ds.data.map { it[EGG_BLANK_TITLE] ?: 0 }

    // v1.0.4：fontScale flow 与 setFontScale 已随字号设置移除（跟随系统字体缩放）

    suspend fun setOnboardingDone(v: Boolean) = ds.edit { it[ONBOARDING_DONE] = v }
    suspend fun setTheme(v: String) = ds.edit { it[THEME] = v }
    suspend fun setWallpaper(v: String) = ds.edit { it[WALLPAPER] = v }
    suspend fun setWallpaperAlpha(v: Float) =
        ds.edit { it[WALLPAPER_ALPHA] = v.coerceIn(0.05f, 0.9f) }
    suspend fun setWallpaperBlur(v: Float) =
        ds.edit { it[WALLPAPER_BLUR] = v.coerceIn(0f, 24f) }
    /** v1.0.18：卡片背景不透明度落盘 */
    suspend fun setCardOpacity(v: Float) =
        ds.edit { it[CARD_OPACITY] = v.coerceIn(0.40f, 0.95f) }
    suspend fun setNotify(v: Boolean) = ds.edit { it[NOTIFY] = v }
    suspend fun setAiConfig(v: String) = ds.edit { it[AI_CONFIG] = v }
    suspend fun setWebdavConfig(v: String) = ds.edit { it[WEBDAV_CONFIG] = v }
    suspend fun setAutoSync(v: Boolean) = ds.edit { it[AUTO_SYNC] = v }
    suspend fun setLastSyncAt(v: String) = ds.edit { it[LAST_SYNC] = v }

    /** 空白标题被拒时 +1（上限 999999，防止有人挂机刷爆 int） */
    suspend fun bumpEggBlankTitle() = ds.edit {
        it[EGG_BLANK_TITLE] = ((it[EGG_BLANK_TITLE] ?: 0) + 1).coerceAtMost(999999)
    }

    /** 设置字号档位 —— v1.0.4 已移除（跟随系统），保留空实现注释占位避免误用 */

    /** 成就解锁音效开关（默认开启） */
    val achSound: Flow<Boolean> = ds.data.map { it[ACH_SOUND] ?: true }

    /** 主页时间轴自定义事件（JSON 列表，空串 = 无自定义事件） */
    val customTimelineJson: Flow<String> = ds.data.map { it[CUSTOM_TIMELINE] ?: "" }

    /** 主页动态流筛选（JSON 集合，空串 = 显示全部） */
    val homeFeedFilterJson: Flow<String> = ds.data.map { it[HOME_FEED_FILTER] ?: "" }

    /** 主页「最近动态」显示条数（0 = 不限制） */
    val homeFeedLimit: Flow<Int> = ds.data.map { it[HOME_FEED_LIMIT] ?: 3 }

    /** 主页速览自定义入口（JSON 字符串集合，空串 = 全部显示） */
    val homeQuickEntriesJson: Flow<String> = ds.data.map { it[HOME_QUICK_ENTRIES] ?: "" }

    suspend fun setAchSound(v: Boolean) = ds.edit { it[ACH_SOUND] = v }
    suspend fun setCustomTimelineJson(v: String) = ds.edit { it[CUSTOM_TIMELINE] = v }
    suspend fun setHomeFeedFilterJson(v: String) = ds.edit { it[HOME_FEED_FILTER] = v }
    suspend fun setHomeFeedLimit(v: Int) = ds.edit { it[HOME_FEED_LIMIT] = v.coerceIn(0, 200) }
    suspend fun setHomeQuickEntriesJson(v: String) = ds.edit { it[HOME_QUICK_ENTRIES] = v }

    /* ————— v1.0.3 体验升级批次 ————— */

    val customTitle: Flow<String> = ds.data.map { it[CUSTOM_TITLE] ?: "" }
    suspend fun setCustomTitle(v: String) = ds.edit { it[CUSTOM_TITLE] = v.trim().take(12) }

    /** 记忆相册元数据（JSON 列表，空串 = 相册为空） */
    val memoryPhotosJson: Flow<String> = ds.data.map { it[MEMORY_PHOTOS] ?: "" }
    suspend fun setMemoryPhotosJson(v: String) = ds.edit { it[MEMORY_PHOTOS] = v }

    /** v1.0.6：记忆相册自定义分类（JSON 列表，空串 = 未建分类） */
    val albumsJson: Flow<String> = ds.data.map { it[ALBUMS] ?: "" }
    suspend fun setAlbumsJson(v: String) = ds.edit { it[ALBUMS] = v }

    /** v1.0.6：壁纸轮换指定相册（空串 = 全部照片） */
    val wallpaperAlbum: Flow<String> = ds.data.map { it[WALLPAPER_ALBUM] ?: "" }
    suspend fun setWallpaperAlbum(v: String) = ds.edit { it[WALLPAPER_ALBUM] = v }

    val wallpaperRotate: Flow<Boolean> = ds.data.map { it[WALLPAPER_ROTATE] ?: false }
    suspend fun setWallpaperRotate(v: Boolean) = ds.edit { it[WALLPAPER_ROTATE] = v }

    /** 主页佩戴徽章（JSON 字符串数组，空串 = 未佩戴） */
    val homeBadgesJson: Flow<String> = ds.data.map { it[HOME_BADGES] ?: "" }
    suspend fun setHomeBadgesJson(v: String) = ds.edit { it[HOME_BADGES] = v }

    val quickNotif: Flow<Boolean> = ds.data.map { it[QUICK_NOTIF] ?: false }
    suspend fun setQuickNotif(v: Boolean) = ds.edit { it[QUICK_NOTIF] = v }

    /** 彩蛋：查看「历年今日」次数（≥1 解锁「时光回声」） */
    val eggThrowback: Flow<Int> = ds.data.map { it[EGG_THROWBACK] ?: 0 }
    suspend fun bumpEggThrowback() = ds.edit {
        it[EGG_THROWBACK] = ((it[EGG_THROWBACK] ?: 0) + 1).coerceAtMost(999999)
    }

    /** 已庆祝过的等级（-1 = 从未记录） */
    val lastCelebratedLevel: Flow<Int> = ds.data.map { it[LAST_CELEBRATED_LEVEL] ?: -1 }
    suspend fun setLastCelebratedLevel(v: Int) = ds.edit { it[LAST_CELEBRATED_LEVEL] = v }

    /** v1.0.4：任务列表「隐藏已完成任务」开关（默认关 = 显示全部） */
    val hideDoneTasks: Flow<Boolean> = ds.data.map { it[HIDE_DONE_TASKS] ?: false }
    suspend fun setHideDoneTasks(v: Boolean) = ds.edit { it[HIDE_DONE_TASKS] = v }

    /* ————— v1.0.5 清空数据 ————— */

    /** 防同步拉回标记（清空数据时置位；CloudSyncManager 冷启动拉取时消费一次） */
    val skipNextPull: Flow<Boolean> = ds.data.map { it[SKIP_NEXT_PULL] ?: false }
    suspend fun setSkipNextPull(v: Boolean) = ds.edit { it[SKIP_NEXT_PULL] = v }

    /** v1.0.6：高德 Web 服务 Key（地图搜索 / 路径规划走 REST API，与 Android 地图 Key 是两套） */
    val amapWebKey: Flow<String> = ds.data.map { it[AMAP_WEB_KEY] ?: "" }
    suspend fun setAmapWebKey(v: String) = ds.edit { it[AMAP_WEB_KEY] = v.trim() }

    /** v1.0.7：液态玻璃特效开关（默认开启；关闭 = 性能模式，去所有高斯模糊 / RenderEffect） */
    val liquidGlass: Flow<Boolean> = ds.data.map { it[LIQUID_GLASS] ?: true }
    suspend fun setLiquidGlass(v: Boolean) = ds.edit { it[LIQUID_GLASS] = v }

    /**
     * 清空「用户数据键」，保留应用设置键。与 Web 端 resetAllData 的键语义对齐：
     * - 清：自定义时间轴 / 动态流筛选与条数 / 速览入口 / 自定义称号 / 记忆相册元数据 /
     *      佩戴徽章 / 彩蛋计数器 ×2 / 已庆祝等级；
     * - 留：onboarding / 主题 / 壁纸（含不透明度与轮换开关）/ 提醒 / AI / WebDAV /
     *      自动同步与上次同步时间 / 成就音效 / 常驻通知 / 隐藏已完成 / 字号占位。
     */
    suspend fun clearUserDataKeys() = ds.edit {
        it.remove(CUSTOM_TIMELINE)
        it.remove(HOME_FEED_FILTER)
        it.remove(HOME_FEED_LIMIT)
        it.remove(HOME_QUICK_ENTRIES)
        it.remove(CUSTOM_TITLE)
        it.remove(MEMORY_PHOTOS)
        it.remove(ALBUMS)
        it.remove(HOME_BADGES)
        it.remove(EGG_BLANK_TITLE)
        it.remove(EGG_THROWBACK)
        it.remove(LAST_CELEBRATED_LEVEL)
    }
}
