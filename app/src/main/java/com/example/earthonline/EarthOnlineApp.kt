package com.example.earthonline

import android.app.Application
import com.amap.api.maps.MapsInitializer
import androidx.work.Configuration
import com.example.earthonline.data.backup.AutoBackupManager
import com.example.earthonline.data.local.datastore.SettingsDataStore
import com.example.earthonline.data.sync.AppForegroundTracker
import com.example.earthonline.data.sync.CloudSyncManager
import com.example.earthonline.reminder.ReminderScheduler
import com.example.earthonline.widget.WidgetUpdateDispatcher
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class EarthOnlineApp : Application(), Configuration.Provider {

    /** Hilt 注入的 Worker 工厂（WorkManager 据此构造 @HiltWorker） */
    @Inject lateinit var workerFactory: androidx.hilt.work.HiltWorkerFactory

    /** 注入的设置仓储（读取到期提醒开关） */
    @Inject lateinit var settings: SettingsDataStore

    /** WebDAV 双端同步：进入前台拉取、切到后台推送 */
    @Inject lateinit var cloudSync: CloudSyncManager

    /** v1.2.1：本地自动备份（表变更防抖后落盘，保留最近 3 份） */
    @Inject lateinit var autoBackup: AutoBackupManager

    /** v1.0.0：桌面小组件刷新调度（表变更防抖后推一次 RemoteViews） */
    @Inject lateinit var widgetUpdates: WidgetUpdateDispatcher

    /** v1.0.5：回收站 30 天到期物理清理（启动触发点，另一处在打开回收站页时） */
    @Inject lateinit var recycleBin: com.example.earthonline.data.repository.RecycleBinRepository

    /**
     * 用 Hilt 的 WorkerFactory 构造 WorkManager 配置。
     * 必须用 getter（首次访问时才求值），否则会在构造期早于 Hilt 注入时读到未初始化的 workerFactory。
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // v1.0.6.8：把 Application 上下文注入本地推理运行时 —— 加载模型前要用它
        // 读 ActivityManager 做内存体检（Arm 架构 / 可用内存），挡住一切会 native 崩溃的加载。
        // 存的是 applicationContext（进程级），无 Activity 泄漏风险。
        com.example.earthonline.ui.ai.LocalLlmRuntime.bind(this)
        // 高德地图隐私合规：必须在任何 MapView 创建前同意隐私政策，否则新版 SDK 会抛异常导致地图闪退。
        // 放在 Application 启动处一次性同意，覆盖所有地图入口。
        // 参数必须为 (true, true)：表示隐私政策包含高德相关内容声明 + 调整说明；
        // 传 (false, false) 会导致 SDK 报 errorCode 555571「隐私合规校验失败」→ 瓦片拒绝加载（白屏）。
        runCatching { MapsInitializer.updatePrivacyAgree(this, true) }
        runCatching { MapsInitializer.updatePrivacyShow(this, true, true) }
        // 通知渠道随应用启动创建（权限未授予也不影响渠道存在）
        ReminderScheduler.createChannel(this)
        // 若用户此前已开启到期提醒，重启应用后重新调度每日闹钟
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            val enabled = runCatching { settings.notify.first() }.getOrElse { false }
            if (enabled) ReminderScheduler.schedule(this@EarthOnlineApp)
            // v1.0.5：启动即清理回收站中软删超过 30 天的行（ISO 串字典序比较）
            runCatching { recycleBin.purgeExpired(purgeCutoffIso()) }
        }
        // v1.2.1：本地自动备份 —— 注册 Room 表变更监听（幂等，内部有 started 标志）
        autoBackup.start()
        // v1.0.0：桌面小组件 —— 同一个表变更信号，防抖后刷新（幂等，未添加小组件时零开销）
        widgetUpdates.start()
        // WebDAV 自动同步：首个 Activity 启动（= 冷启动/回前台）→ 拉取；全部退到后台 → 推送
        registerActivityLifecycleCallbacks(AppForegroundTracker(cloudSync))
    }

    /** 30 天前的 UTC ISO 时间戳（与软删 deletedAt 同格式，字典序即时间序） */
    private fun purgeCutoffIso(): String {
        val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -30) }
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return fmt.format(cal.time)
    }
}
