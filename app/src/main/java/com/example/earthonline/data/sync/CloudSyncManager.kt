package com.example.earthonline.data.sync

import com.example.earthonline.data.backup.BackupRepository
import com.example.earthonline.data.local.datastore.SettingsDataStore
import com.example.earthonline.data.network.WebDavConfig
import com.example.earthonline.data.network.WebDavService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** 同步结果：ok=是否成功，changed=数据是否真的变动过（用于决定是否提示/刷新 UI） */
data class SyncResult(val ok: Boolean, val message: String, val changed: Boolean = false)

/**
 * WebDAV 双端同步（对应 Web 端 modules/webdav.js 的自动同步）。
 *
 * 冲突策略：**按修改时间** —— 云端备份的 `exportedAt` 与本地记录的「上次同步时间」比对，
 * 只有云端更新才拉取覆盖本地；推送时把本地导出时间写回，作为下次比对的基准。
 *
 * 触发时机（与 Web 端一致）：
 *  - 进入前台 / 冷启动 → `syncOnForeground()` → 拉取（云端更新才写库）
 *  - 切到后台 → `syncOnBackground()` → 推送本地最新存档
 *
 * 远端文件名与 Web 端默认路径保持一致（`/earth_online_backup.json`），双端才能真正互通。
 */
@Singleton
class CloudSyncManager @Inject constructor(
    private val settings: SettingsDataStore,
    private val backupRepo: BackupRepository,
    private val webDav: WebDavService,
    private val json: Json,
    // v1.0.5：灵感接力（拉到新灵感 → 本地通知）
    private val memoRepo: com.example.earthonline.data.repository.MemoRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) {

    companion object {
        /** 与 Web 端 WEBDAV_DEFAULT_PATH 一致（下划线），保证两端读写同一个文件 */
        const val DEFAULT_REMOTE_PATH = "/earth_online_backup.json"

        /** 用户可在 Android 端自定义路径；留空则用上面的默认值 */
        fun remotePathOf(cfg: WebDavConfig): String {
            val p = cfg.path.trim()
            if (p.isEmpty()) return DEFAULT_REMOTE_PATH
            return if (p.startsWith("/")) p else "/$p"
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** 串行化：避免拉/推并发互相覆盖 */
    private val mutex = Mutex()

    /** 冷启动 / 回前台：云端更新才拉取覆盖本地 */
    fun syncOnForeground() {
        scope.launch { pullIfRemoteNewer() }
    }

    /** 切后台：把本地最新存档推上去 */
    fun syncOnBackground() {
        scope.launch { push() }
    }

    /**
     * 拉取：云端 exportedAt 新于本地上次同步时间时才导入。
     * @param force true 时忽略自动同步开关（设置页手动点「从云端恢复」用）
     */
    suspend fun pullIfRemoteNewer(force: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            // v1.0.5 清空数据防拉回：非手动（冷启动/回前台）时消费跳过标记，
            // 命中则本次不拉取 —— 防止云端旧备份把刚清空的本地数据整包拉回来。
            // 手动「从云端恢复」走 force=true，不受影响。
            if (!force && settings.skipNextPull.first()) {
                settings.setSkipNextPull(false)
                return@withLock SyncResult(false, "已跳过本次自动拉取（清空数据保护）")
            }
            val cfg = readConfig()
                ?: return@withLock SyncResult(false, "未配置 WebDAV")
            if (!force && !settings.autoSync.first()) {
                return@withLock SyncResult(false, "自动同步已关闭")
            }
            val bytes = webDav.download(davOf(cfg), remotePathOf(cfg))
                .getOrElse { return@withLock SyncResult(false, "下载失败：${it.message}") }
            val text = bytes.toString(Charsets.UTF_8)
            val remote = runCatching { json.decodeFromString<BackupRepository.Payload>(text) }
                .getOrElse { return@withLock SyncResult(false, "云端文件不是有效的备份") }

            val remoteAt = parseMillis(remote.exportedAt)
            val localAt = parseMillis(settings.lastSyncAt.first())
            if (remoteAt <= localAt) {
                return@withLock SyncResult(true, "云端无更新", changed = false)
            }
            // v1.0.5：灵感接力 —— 拉取前先记本地已有 memo id，导入后比对出「跨端新灵感」，
            // 有新增则发本地通知（点击进 App 提供转待办）。
            val localMemoIds = runCatching { memoRepo.observeAll().first().map { it.id }.toSet() }
                .getOrElse { emptySet() }
            runCatching { backupRepo.importJson(text) }
                .getOrElse { return@withLock SyncResult(false, "本地写入失败：${it.message}") }
            settings.setLastSyncAt(remote.exportedAt)
            notifySyncResult(remote, remote.memos.filter { it.id !in localMemoIds })
            SyncResult(true, "已拉取云端最新数据", changed = true)
        }
    }

    /**
     * 推送：导出本地全量 → PUT → 记录同步时间。
     * @param force true 时忽略自动同步开关（设置页手动点「立即同步」用）
     */
    suspend fun push(force: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val cfg = readConfig()
                ?: return@withLock SyncResult(false, "未配置 WebDAV")
            if (!force && !settings.autoSync.first()) {
                return@withLock SyncResult(false, "自动同步已关闭")
            }
            val text = backupRepo.exportJson()
            val at = runCatching { json.decodeFromString<BackupRepository.Payload>(text).exportedAt }
                .getOrDefault(LocalDateTime.now().toString())
            webDav.upload(davOf(cfg), remotePathOf(cfg), text.toByteArray(Charsets.UTF_8))
                .getOrElse { return@withLock SyncResult(false, "上传失败：${it.message}") }
            settings.setLastSyncAt(at)
            SyncResult(true, "已推送到云端", changed = true)
        }
    }

    /**
     * v1.0.5 清空数据：删除云端备份文件（HTTP DELETE）。
     * 404 视为成功（云端本来就没有备份，目标已达成）—— OkHttp 对 404 抛的是
     * friendlyError，这里按 message 含「404」宽松放行，其余失败原样返回。
     */
    suspend fun deleteRemote(): SyncResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val cfg = readConfig()
                ?: return@withLock SyncResult(false, "未配置 WebDAV，云端无备份")
            val r = webDav.delete(davOf(cfg), remotePathOf(cfg))
            r.fold(
                onSuccess = { SyncResult(true, "云端备份已删除") },
                onFailure = {
                    val msg = it.message.orEmpty()
                    if (msg.contains("404")) SyncResult(true, "云端备份已删除")
                    else SyncResult(false, "云端删除失败：$msg")
                }
            )
        }
    }

    private suspend fun readConfig(): WebDavConfig? {
        val raw = settings.webdavConfig.first()
        if (raw.isBlank()) return null
        val cfg = runCatching { json.decodeFromString<WebDavConfig>(raw) }.getOrNull() ?: return null
        return if (cfg.url.isBlank()) null else cfg
    }

    /**
     * v1.0.5：拉取成功后的本地通知（跟随「到期提醒通知」总开关，默认用户关了就不吵）。
     *  - 有跨端新灵感 → 「灵感接力」通知，点击进 App 转待办（MainActivity 消费 extra）；
     *  - 无灵感但确有更新 → 轻量「同步完成」通知（一条，不逐项罗列）。
     */
    private suspend fun notifySyncResult(remote: BackupRepository.Payload, newMemos: List<com.example.earthonline.data.local.entity.MemoEntity>) {
        val enabled = runCatching { settings.notify.first() }.getOrDefault(false)
        if (!enabled) return
        val N = com.example.earthonline.reminder.AppNotifier
        if (newMemos.isNotEmpty()) {
            val first = newMemos.first().text.take(60)
            val intent = android.content.Intent(appContext, com.example.earthonline.MainActivity::class.java).apply {
                putExtra("inspire", true)
            }
            N.post(
                appContext, N.CHANNEL_SYNC, N.ID_INSPIRE_BASE,
                "跨端灵感接力（${newMemos.size} 条）",
                first + if (newMemos.size > 1) " 等 ${newMemos.size} 条新灵感已同步，点击转为待办" else " 已同步，点击转为待办",
                intent
            )
        } else {
            N.post(
                appContext, N.CHANNEL_SYNC, N.ID_SYNC,
                "地球Online · 同步完成",
                "已拉取云端最新数据（${remote.exportedAt.take(16).replace('T', ' ')}）"
            )
        }
    }

    private fun davOf(c: WebDavConfig) = WebDavService.DavConfig(c.url, c.user, c.pass)

    /**
     * 时间字符串 → 毫秒。
     * 兼容三种来源：Web 端 `toISOString()`（带 Z）、Android `LocalDateTime.now()`（无时区）、空串。
     */
    private fun parseMillis(s: String): Long {
        val v = s.trim()
        if (v.isEmpty()) return 0L
        return runCatching { OffsetDateTime.parse(v).toInstant().toEpochMilli() }
            .getOrElse {
                runCatching { LocalDateTime.parse(v).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
                    .getOrDefault(0L)
            }
    }
}
