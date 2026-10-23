package com.example.earthonline.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.earthonline.data.backup.AutoBackupManager
import com.example.earthonline.data.backup.BackupRepository
import com.example.earthonline.data.local.datastore.SettingsDataStore
import com.example.earthonline.data.network.AiConfig
import com.example.earthonline.data.network.LocalModelConfig
import com.example.earthonline.data.network.WebDavConfig
import com.example.earthonline.data.network.UpdateRepository
import com.example.earthonline.data.network.WebDavService
import com.example.earthonline.BuildConfig
import com.example.earthonline.data.photos.MemoryPhoto
import com.example.earthonline.data.photos.PhotoAlbum
import com.example.earthonline.data.sync.CloudSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** 一次更新检查的展示结果 */
data class UpdateCheck(
    /** 远端最新版本号 */
    val latest: String,
    /** 是否比当前版本新 */
    val hasUpdate: Boolean,
    /** 更新说明（可能为空） */
    val notes: String,
    /** 打开地址：有 APK 直链用直链，否则 Release 页面 */
    val url: String
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val backupRepo: BackupRepository,
    private val webDav: WebDavService,
    private val json: Json,
    private val cloudSync: CloudSyncManager,
    private val autoBackup: AutoBackupManager,
    private val updateRepo: UpdateRepository,
    // v1.0.5：APK 下载 + 引导安装（与启动更新弹窗共用同一应用级单例）
    val installer: com.example.earthonline.util.ApkInstaller,
    // v1.0.5：CSV / Markdown 文本导入
    private val textImporter: com.example.earthonline.data.backup.TextImportParser,
    private val taskDao: com.example.earthonline.data.local.dao.TaskDao,
    private val memoDao: com.example.earthonline.data.local.dao.MemoDao,
    // v1.0.5：分享人生卡片数据源
    private val profileRepo: com.example.earthonline.data.repository.ProfileRepository,
    // v1.0.5 清空数据：记忆相册原图文件清理
    private val memoryPhotoStore: com.example.earthonline.data.photos.MemoryPhotoStore
) : ViewModel() {

    val theme: Flow<String> = settings.theme
    val wallpaper: Flow<String> = settings.wallpaper
    val wallpaperAlpha: Flow<Float> = settings.wallpaperAlpha
    /** v1.0.11：壁纸模糊度（0~24dp，用户自平衡美观与性能） */
    val wallpaperBlur: Flow<Float> = settings.wallpaperBlur

    /** v1.0.18：卡片背景不透明度（设置→外观滑块，DataStore 持久化） */
    val cardOpacity: Flow<Float> = settings.cardOpacity
    val notify: Flow<Boolean> = settings.notify
    val aiConfigJson: Flow<String> = settings.aiConfig
    /** 本地模型（离线推理）配置 JSON */
    val localModelJson: Flow<String> = settings.localModel
    val webdavConfigJson: Flow<String> = settings.webdavConfig
    /** 自动同步开关（启动拉取 / 切后台推送） */
    val autoSync: Flow<Boolean> = settings.autoSync
    /** 上次同步时间（空串 = 从未同步） */
    val lastSyncAt: Flow<String> = settings.lastSyncAt
    /** v1.2.1：成就解锁音效开关 */
    val achSound: Flow<Boolean> = settings.achSound
    /** v1.0.7：液态玻璃特效全局开关（默认开启；关闭 = 性能模式） */
    val liquidGlass: Flow<Boolean> = settings.liquidGlass
    // v1.0.4：字号档位（fontScale / setFontScale）已随「字号大小设置移除」一并下线，
    // 文字大小改为完全跟随系统字体缩放。

    // v1.0.3：壁纸随机轮换 / 记忆相册 / 通知栏快捷记录
    val wallpaperRotate: Flow<Boolean> = settings.wallpaperRotate
    val memoryPhotosJson: Flow<String> = settings.memoryPhotosJson
    val quickNotif: Flow<Boolean> = settings.quickNotif
    fun setWallpaperRotate(v: Boolean) = viewModelScope.launch { settings.setWallpaperRotate(v) }
    fun setQuickNotif(v: Boolean) = viewModelScope.launch { settings.setQuickNotif(v) }

    // v1.0.6：壁纸轮换指定相册（空串 = 全部照片，取消数量限制）
    val wallpaperAlbum: Flow<String> = settings.wallpaperAlbum
    fun setWallpaperAlbum(v: String) = viewModelScope.launch { settings.setWallpaperAlbum(v) }

    /** 壁纸轮换来源相册列表（供外观页下拉选择，空串代表「全部照片」） */
    val albums: Flow<List<PhotoAlbum>> = settings.albumsJson.map { txt ->
        if (txt.isBlank()) emptyList()
        else runCatching { json.decodeFromString(ListSerializer(PhotoAlbum.serializer()), txt) }.getOrDefault(emptyList())
    }
        // v1.0.6：相册 JSON 解析移出主线程
        .flowOn(Dispatchers.Default)

    /**
     * 当日应显示的壁纸路径（综合 静态壁纸 / 轮换开关 / 相册筛选）：
     * - 轮换关 → 静态壁纸；
     * - 轮换开 → 从指定相册（空=全部照片，无数量限制）按日期确定性挑一张。
     */
    val effectiveWallpaper: Flow<String> = combine(wallpaper, wallpaperRotate, memoryPhotosJson, wallpaperAlbum) { wp, rotate, photosJson, albumId ->
        if (rotate && photosJson.contains("\"path\"")) {
            val all = runCatching { json.decodeFromString(ListSerializer(MemoryPhoto.serializer()), photosJson) }.getOrDefault(emptyList())
            val pool = if (albumId.isBlank()) all else all.filter { it.albumId == albumId }
            if (pool.isNotEmpty()) {
                val day = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
                pool[day % pool.size].path
            } else wp
        } else wp
    }
        // v1.0.6：相册 JSON 解析较重，移出主线程，避免首启 / 切壁纸轮换时主线程卡顿
        .flowOn(Dispatchers.Default)

    fun setTheme(v: String) = viewModelScope.launch { settings.setTheme(v) }
    fun setAchSound(v: Boolean) = viewModelScope.launch { settings.setAchSound(v) }
    fun setLiquidGlass(v: Boolean) = viewModelScope.launch { settings.setLiquidGlass(v) }
    fun setAutoSync(v: Boolean) = viewModelScope.launch { settings.setAutoSync(v) }
    fun setNotify(v: Boolean) = viewModelScope.launch { settings.setNotify(v) }
    fun setWallpaper(path: String) = viewModelScope.launch { settings.setWallpaper(path) }

    private var alphaJob: Job? = null

    /**
     * 壁纸不透明度：拖动时高频回调，这里做 120ms 防抖再落盘，
     * 避免每帧一次 DataStore 写入 + 全局 Flow 发射引发重组风暴。
     */
    fun setWallpaperAlpha(v: Float) {
        val c = v.coerceIn(0.05f, 0.9f)
        alphaJob?.cancel()
        alphaJob = viewModelScope.launch {
            delay(120)
            settings.setWallpaperAlpha(c)
        }
    }

    private var blurJob: Job? = null
    private var cardOpacityJob: Job? = null

    /**
     * v1.0.11：壁纸模糊度滑块 —— 与不透明度同样 120ms 防抖落盘，实时预览走 Flow 重组。
     */
    fun setWallpaperBlur(v: Float) {
        val c = v.coerceIn(0f, 24f)
        blurJob?.cancel()
        blurJob = viewModelScope.launch {
            delay(120)
            settings.setWallpaperBlur(c)
        }
    }

    /** v1.0.18：卡片背景不透明度滑块 —— 120ms 防抖落盘，拖动即时经 Flow 重组预览 */
    fun setCardOpacity(v: Float) {
        val c = v.coerceIn(0.40f, 0.95f)
        cardOpacityJob?.cancel()
        cardOpacityJob = viewModelScope.launch {
            delay(120)
            settings.setCardOpacity(c)
        }
    }

    fun saveAiConfig(c: AiConfig) = viewModelScope.launch { settings.setAiConfig(json.encodeToString(AiConfig.serializer(), c)) }
    fun parseLocalModel(s: String): LocalModelConfig =
        runCatching { json.decodeFromString<LocalModelConfig>(s) }.getOrDefault(LocalModelConfig())
    fun saveLocalModel(c: LocalModelConfig) = viewModelScope.launch { settings.setLocalModel(json.encodeToString(LocalModelConfig.serializer(), c)) }
    /** v1.0.6（融合重构）：本地模型「已加载」标志持久化（冷启动静默自动加载依据） */
    fun setLocalModelLoaded(v: Boolean) = viewModelScope.launch { settings.setLocalModelLoaded(v) }
    fun saveWebDavConfig(c: WebDavConfig) = viewModelScope.launch { settings.setWebdavConfig(json.encodeToString(WebDavConfig.serializer(), c)) }

    fun parseAiConfig(s: String): AiConfig =
        runCatching { json.decodeFromString<AiConfig>(s) }.getOrDefault(AiConfig())
    fun parseWebdavConfig(s: String): WebDavConfig =
        runCatching { json.decodeFromString<WebDavConfig>(s) }.getOrDefault(WebDavConfig())

    suspend fun exportJson(): String = backupRepo.exportJson()
    suspend fun importJson(text: String) = backupRepo.importJson(text)

    /* ---------------- v1.2.1：本地自动备份（保留最近 3 份） ---------------- */

    /** 现有自动快照（新 -> 旧） */
    suspend fun autoSnapshots(): List<AutoBackupManager.Snapshot> = autoBackup.listSnapshots()

    /** 立即留一份（不受 60s 最小间隔限制） */
    suspend fun autoBackupNow(): Boolean = autoBackup.snapshot(reasonCheck = false) != null

    /** 从某份快照恢复（与导入备份同为「按主键合并」语义） */
    suspend fun restoreAutoSnapshot(s: AutoBackupManager.Snapshot): Boolean = autoBackup.restore(s)

    /* ---------------- v1.0.5：清空数据 ---------------- */

    /**
     * 清空全部用户数据（语义与 Web 端 resetAllData 对齐），返回给 UI 的提示语。
     *
     * 执行顺序（每步都有明确理由）：
     *  ① 先置防拉回标记 —— 本地清空期间绝不能被云端旧备份拉回（切前台/冷启动都会触发拉取）；
     *  ② 勾选删云端时先 DELETE 远端备份（成败都不阻断本地清空，结果体现在返回语里）；
     *  ③ 本地清空：DB 十表 + 头像原图文件 + 记忆相册图片文件 + DataStore 用户键（含壁纸五键）+ 壁纸原图文件 + 自动备份快照；
     *  ④ 保留：主题 / WebDAV / AI 配置 / 提醒与音效开关等应用设置键（壁纸自 v1.0.6 起一并重置为默认）。
     *
     * @param deleteCloud 是否同时删除云端备份（确认框勾选项，默认不勾）
     */
    suspend fun clearAllData(deleteCloud: Boolean): String {
        // ① 防拉回（下一次非手动拉取消费一次即失效）
        settings.setSkipNextPull(true)
        // ② 可选删云端
        val cloudMsg = if (deleteCloud) {
            val r = cloudSync.deleteRemote()
            r.message
        } else ""
        // ③ 本地清空
        backupRepo.clearAllData()      // Room 十表全清（主键级 DELETE，不走 DROP）
        memoryPhotoStore.clearAllFiles() // 相册图片文件
        // 先放掉 native 上下文：模型权重的 mmap 页还挂着的话，下面删 filesDir 会删不干净
        runCatching { com.example.earthonline.ui.ai.LocalLlmRuntime.engine.release() }
        runCatching { com.example.earthonline.ui.ai.LocalLlmRuntime.activate(com.example.earthonline.ui.ai.MockLlmEngine) }
        autoBackup.clearSnapshots()
        // ④ 偏好 + 文件系统彻底铲（主题 / 引导状态 / AI 配置 / 壁纸 / 模型目录 / 缓存）
        val freed = settings.wipeAll()
        val sizeText = com.example.earthonline.ui.ai.LocalLlmRuntime.formatBytes(freed)
        // 返回语：删云端结果 + 本地清空提示
        return buildString {
            if (deleteCloud) append(cloudMsg).append("；")
            append("本地数据已全部清空")
            if (freed > 0) append("，释放 $sizeText")
            append("。应用将回到首次启动状态")
        }
    }

    /**
     * v1.0.5 清空后的「重新同步」：把当前（清空后的）数据上传云端覆盖旧备份，
     * 恢复多设备同步。直接复用 push(force=true) —— 与 Web 端 webdavUploadBackup 等价。
     */
    suspend fun resyncToCloud(): String = cloudSync.push(force = true).message

    /** v1.0.5：CSV / Markdown 文本文件导入（增量 REPLACE，重复导入自动去重） */
    suspend fun importTextFile(content: String): String =
        runCatching { textImporter.import(content, taskDao, memoDao).message }
            .getOrElse { "导入失败：${it.message ?: "文件内容无法解析"}" }

    /* ---------------- v1.2.1：应用内检查更新 ---------------- */

    /**
     * 检查更新。成功返回 [UpdateCheck]（含是否有新版、更新说明、下载地址），
     * 失败返回 Result.failure —— 无外网 / 仓库不可达 / 被限流都走这里，由 UI 提示人话。
     */
    suspend fun checkUpdate(): Result<UpdateCheck> {
        val info = updateRepo.checkLatest(AppInfo.repoOwner, AppInfo.repoName)
            .getOrElse { return Result.failure(it) }
        val hasUpdate = info.version.isNotBlank() && updateRepo.isNewer(info.version, BuildConfig.VERSION_NAME)
        return Result.success(
            UpdateCheck(
                latest = info.version.ifBlank { AppInfo.version },
                hasUpdate = hasUpdate,
                notes = info.notes,
                url = info.apkUrl?.takeIf { it.isNotBlank() } ?: info.releaseUrl
            )
        )
    }

    suspend fun testDav(config: WebDavConfig): String =
        if (config.url.isBlank()) "请先填写服务器地址"
        else if (webDav.list(WebDavService.DavConfig(config.url, config.user, config.pass), "/").isSuccess)
            "连接成功" else "连接失败（检查地址/账号/密码）"

    /** 手动推送（force：忽略自动同步开关）。远端路径与网页端默认一致，双端互通。 */
    suspend fun syncToDav(config: WebDavConfig): String {
        if (config.url.isBlank()) return "请先填写服务器地址"
        return cloudSync.push(force = true).message
    }

    /** 手动拉取（force：忽略自动同步开关）。仅当云端 exportedAt 更新时才覆盖本地。 */
    suspend fun syncFromDav(config: WebDavConfig): String {
        if (config.url.isBlank()) return "请先填写服务器地址"
        return cloudSync.pullIfRemoteNewer(force = true).message
    }
}
