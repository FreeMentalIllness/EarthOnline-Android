package com.example.earthonline.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** 下载阶段：UI 据此显示「下载中 / 换线重试 / 成功 / 失败」 */
enum class DownloadPhase { Idle, Downloading, Retrying, Success, Failed }

/** 一条下载进度快照。percent 为 -1 表示服务端没给总大小（此时 UI 显示已下载 MB 数） */
data class DownloadProgress(
    val phase: DownloadPhase,
    val percent: Int,
    val receivedBytes: Long,
    val totalBytes: Long,
    /** 当前是第几条线路（1 基） */
    val attempt: Int,
    val totalAttempts: Int,
    val message: String? = null
)

/**
 * 应用内更新下载 + 引导安装（应用级单例，供「关于」页与启动更新弹窗共用）。
 *
 * 职责：
 *  - 下载 APK：先走 GitHub 官方直链，失败则**自动换备用镜像**继续尝试（最多 6 条线路）；
 *  - 实时进度：[progress] 每秒刷新一次百分比 / 已下载字节，UI 可直接画进度条；
 *  - 停滞重试：某条线路持续 45 秒没有任何字节增长（典型症状：连上了但不动），判定该线路不可用并换下一条；
 *  - 下载完成后用 FileProvider 包 content:// URI 再调起系统安装器
 *    （v1.0.5 修复：此前直接把 DownloadManager 返回的 file:// URI 传给安装器，
 *     Android 7.0+ 会抛 FileUriExposedException，且私有目录文件安装器侧无读权限，安装必然失败）；
 *  - 用户消息统一走 [events] 流（StateFlow），由 UI 层收集显示为 Snackbar；UI 消费后调 [consumeEvent] 清空。
 *
 * 关于镜像：GitHub 直链在国内网络上成功率极低（连不上 / 连上不动 / 中途断），
 * 单靠一条线路必然「下载失败」。这里内置多条公共加速镜像作为后备，
 * 全部走完仍失败才给出「请到 Release 页面手动下载」的兜底提示。
 * 镜像是公开只读的 CDN 代理，只转发 release 资产下载请求，不涉及任何凭据。
 *
 * 「安装未知应用」权限（Android 8.0+）的检查与跳系统设置页留在 UI 层 ——
 * ActivityResult 回调必须在 Composable 里注册，单例层拿不到回执。
 * 清单已声明 REQUEST_INSTALL_PACKAGES。
 */
@Singleton
class ApkInstaller @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** 一次用户可见的消息（Snackbar 文案），UI 消费后置 null */
    private val _events = MutableStateFlow<String?>(null)
    val events: StateFlow<String?> = _events

    /** 实时下载进度；空闲 / 结束后为 null（UI 据此收起进度条） */
    private val _progress = MutableStateFlow<DownloadProgress?>(null)
    val progress: StateFlow<DownloadProgress?> = _progress

    private val downloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 最近一次 enqueue 的下载 id（广播按它过滤，避免误响应历史下载） */
    private var lastDownloadId = -1L

    /** 已完成（成功安装或最终失败）守卫：轮询已处理完，广播就不必再触发一次安装 */
    @Volatile
    private var settled = false

    init {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id != lastDownloadId) return
                // 轮询已在跑并且会处理完成事件；这里只在轮询不在场时兜底（例如进程被系统回收后广播先到）
                if (settled) return
                settled = true
                onDownloadComplete(id)
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    /** 用系统下载器开始下载 APK（应用私有外部目录，卸载即清，不占公共下载） */
    fun download(apkUrl: String) {
        val candidates = buildCandidates(apkUrl)
        settled = false
        _events.value = "开始下载更新，完成后自动提示安装"
        scope.launch { runCandidates(candidates) }
    }

    /** UI 消费完消息后清空，防止重组重复弹出 */
    fun consumeEvent() {
        _events.value = null
    }

    /** 手动收起进度 UI（用户在进度条上点了关闭） */
    fun clearProgress() {
        _progress.value = null
    }

    /**
     * 逐条线路尝试：任意一条成功即安装；失败 / 停滞则清掉该次下载换下一条。
     */
    private suspend fun runCandidates(candidates: List<String>) {
        for ((index, url) in candidates.withIndex()) {
            val attempt = index + 1
            if (attempt > 1) {
                _progress.value = DownloadProgress(
                    phase = DownloadPhase.Retrying,
                    percent = 0, receivedBytes = 0, totalBytes = 0,
                    attempt = attempt, totalAttempts = candidates.size,
                    message = "线路 ${attempt - 1} 不可用，正在切换到备用线路 $attempt/${candidates.size}"
                )
            }
            if (attemptDownload(url, attempt, candidates.size)) {
                settled = true
                return
            }
        }
        settled = true
        _progress.value = DownloadProgress(
            phase = DownloadPhase.Failed,
            percent = 0, receivedBytes = 0, totalBytes = 0,
            attempt = candidates.size, totalAttempts = candidates.size,
            message = "所有下载线路都失败了"
        )
        _events.value = "下载失败：已尝试官方直链与全部备用镜像，请到 Release 页面手动下载"
    }

    /** 单条线路的下载 + 轮询。返回 true 表示成功并已触发安装 */
    private suspend fun attemptDownload(url: String, attempt: Int, totalAttempts: Int): Boolean {
        val fileName = "earth-online-update.apk"
        val id = runCatching {
            val req = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle("地球Online 更新")
                setDescription(
                    if (attempt == 1) "正在下载安装包…"
                    else "备用线路 $attempt/$totalAttempts 下载中…"
                )
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            downloadManager.enqueue(req)
        }.getOrElse { return false }

        lastDownloadId = id
        var stalledSeconds = 0
        var lastBytes = -1L

        while (true) {
            delay(1000)
            val cursor = downloadManager.query(DownloadManager.Query().setFilterById(id))
                ?: return false
            var done = false
            var success = false
            cursor.use { c ->
                if (!c.moveToFirst()) {
                    done = true
                    return@use
                }
                val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val totalIdx = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val recvIdx = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val total = if (totalIdx >= 0) c.getLong(totalIdx) else 0L
                val received = if (recvIdx >= 0) c.getLong(recvIdx) else 0L
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        _progress.value = DownloadProgress(
                            DownloadPhase.Success, 100, received, total, attempt, totalAttempts,
                            "下载完成，正在唤起安装"
                        )
                        installDownloaded(id)
                        done = true
                        success = true
                    }
                    DownloadManager.STATUS_FAILED -> {
                        runCatching { downloadManager.remove(id) }
                        done = true
                    }
                    DownloadManager.STATUS_PAUSED -> {
                        // 系统判定「等待网络 / 等待重试」：给它 45 秒，超时就换线
                        stalledSeconds++
                        if (stalledSeconds >= 45) {
                            runCatching { downloadManager.remove(id) }
                            done = true
                        }
                    }
                    else -> {
                        val pct = if (total > 0) (received * 100 / total).toInt() else -1
                        _progress.value = DownloadProgress(
                            DownloadPhase.Downloading, pct, received, total, attempt, totalAttempts
                        )
                        // 停滞检测：连上了但一个字节都不动，是国内网络访问 GitHub 的典型症状
                        if (received <= lastBytes) stalledSeconds++ else stalledSeconds = 0
                        lastBytes = received
                        if (stalledSeconds >= 45) {
                            runCatching { downloadManager.remove(id) }
                            done = true
                        }
                    }
                }
            }
            if (done) return success
        }
    }

    /**
     * 候选下载线路：官方直链优先，其后是多条公共加速镜像。
     * 这些镜像都是「URL 前缀式」代理：把完整 https 链接拼在后面即可转发。
     */
    private fun buildCandidates(url: String): List<String> {
        val mirrors = listOf(
            "https://ghproxy.net/",
            "https://ghfast.top/",
            "https://gh-proxy.com/",
            "https://gh-proxy.net/",
            "https://mirror.ghproxy.com/"
        )
        val result = ArrayList<String>(mirrors.size + 1)
        result.add(url)
        for (m in mirrors) {
            val candidate = m + url
            if (candidate !in result) result.add(candidate)
        }
        return result
    }

    /** 查询下载结果并调起安装（file:// → FileProvider content://，杜绝 FileUriExposedException） */
    private fun installDownloaded(id: Long) {
        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(id)) ?: run {
            _events.value = "安装包定位失败，请到 Release 页面手动下载"
            return
        }
        cursor.use { c ->
            if (!c.moveToFirst()) {
                _events.value = "安装包定位失败，请到 Release 页面手动下载"
                return
            }
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val uriStr = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
            if (status != DownloadManager.STATUS_SUCCESSFUL || uriStr.isNullOrBlank()) {
                _events.value = "下载失败（网络中断或空间不足），请稍后重试"
                return
            }
            val path = Uri.parse(uriStr).path ?: run {
                _events.value = "安装包定位失败，请到 Release 页面手动下载"
                return
            }
            val contentUri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", File(path)
            )
            val install = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(install) }
                .onFailure { _events.value = "无法启动安装，请到 Release 页面手动下载" }
        }
    }

    /** 广播兜底路径（轮询不在场时） */
    private fun onDownloadComplete(id: Long) {
        installDownloaded(id)
    }
}
