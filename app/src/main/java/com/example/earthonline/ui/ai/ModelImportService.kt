package com.example.earthonline.ui.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 模型导入进度（UI 与前台服务之间的桥）。
 * 服务负责真正解压（800MB 级），UI 只观察这里的状态 —— 界面退出/旋转都不会丢进度。
 */
object ModelImportProgress {
    val running: StateFlow<Boolean> get() = _running
    private val _running = MutableStateFlow(false)
    val label: StateFlow<String> get() = _label
    private val _label = MutableStateFlow("")
    val percent: StateFlow<Int> get() = _percent
    private val _percent = MutableStateFlow(0)
    /** 导入结果（null = 进行中）。UI 用 `first { it != null }` 等待即可。 */
    val result: StateFlow<LocalModelManager.ModelImportResult?> get() = _result
    internal val _result = MutableStateFlow<LocalModelManager.ModelImportResult?>(null)

    internal fun reset() {
        _running.value = true
        _label.value = ""
        _percent.value = 0
        _result.value = null
    }

    internal fun done(r: LocalModelManager.ModelImportResult) {
        _running.value = false
        _result.value = r
    }

    internal fun progress(entry: String, pct: Int) {
        _label.value = entry
        _percent.value = pct
    }
}

/**
 * 本地模型导入前台服务（终极重构 · 工程四）。
 *
 * 为什么必须是前台服务：模型包动辄 800MB，解压要几分钟。此前在 Activity 的协程里跑，
 * 一旦用户切后台或系统回收进程，解压就半途而废，且进度条卡死看起来像「App 崩了」。
 * 前台服务 + 常驻通知带来三个确定性：
 *  1. 进程优先级提升，解压期间不容易被系统杀；
 *  2. 通知栏实时显示「正在解压：xxx (xx%)」，用户切出去也知道在跑；
 *  3. 结果通过 [ModelImportProgress] 回传，UI 回来时能接着拿到结果，不丢状态。
 */
class ModelImportService : Service() {

    companion object {
        private const val EXTRA_URI = "extra_uri"
        private const val EXTRA_NAME = "extra_name"
        private const val CHANNEL_ID = "model_import"
        private const val CHANNEL_NAME = "模型导入"
        private const val NOTIF_ID = 9001

        /** 启动导入（内部区分 startForegroundService / startService） */
        fun start(context: Context, uri: Uri, name: String) {
            ModelImportProgress.reset()
            val intent = Intent(context, ModelImportService::class.java)
                .putExtra(EXTRA_URI, uri.toString())
                .putExtra(EXTRA_NAME, name)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uriStr = intent?.getStringExtra(EXTRA_URI)
        val name = intent?.getStringExtra(EXTRA_NAME) ?: "model.zip"
        if (uriStr == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        createChannelIfNeeded()
        startForeground(NOTIF_ID, buildNotification("准备导入模型…", 0))

        scope.launch {
            val result = LocalModelManager.importModelPackage(
                this@ModelImportService,
                Uri.parse(uriStr),
                name
            ) { entry, written, total ->
                val pct = if (total > 0) {
                    ((written * 100) / total).toInt().coerceIn(0, 100)
                } else 0
                ModelImportProgress.progress(entry, pct)
                notifyNow("正在解压：$entry", pct)
            }
            ModelImportProgress.done(result)
            notifyNow(
                when (result) {
                    is LocalModelManager.ModelImportResult.Ok -> "导入完成"
                    is LocalModelManager.ModelImportResult.Fail -> "导入失败：${result.reason}"
                },
                100
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        super.onDestroy()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (nm?.getNotificationChannel(CHANNEL_ID) != null) return
        nm?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
                description = "本地模型解压进度"
                setSound(null, null)
            }
        )
    }

    private fun notifyNow(text: String, pct: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        nm.notify(NOTIF_ID, buildNotification(text, pct))
    }

    private fun buildNotification(text: String, pct: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("本地模型导入")
            .setContentText(text)
            .setProgress(100, pct.coerceIn(0, 100), pct <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
}
