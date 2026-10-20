package com.example.earthonline

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.earthonline.data.network.UpdateInfo
import com.example.earthonline.data.network.UpdateRepository
import com.example.earthonline.ui.Root.EarthOnlineAppRoot
import com.example.earthonline.ui.navigation.Screen
import com.example.earthonline.ui.settings.AppInfo
import com.example.earthonline.util.ApkInstaller
import com.example.earthonline.util.openUrlInBrowser
import com.example.earthonline.widget.OverviewWidget
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * 启动后要落到哪个页面。
     * 桌面小组件的四个入口带这个 extra 直达对应 Tab；默认主页。
     * ⚠️ 用 Compose 的 state 持有：Activity 已存在时（点小组件属于 SINGLE_TOP + CLEAR_TOP）
     * 走的是 onNewIntent，改这个 state 就能让下面的 content 重组并导航过去。
     */
    private var startRoute by mutableStateOf(Screen.Home.route)

    /** 首帧内容是否已就绪（偏好读完）。由 EarthOnlineAppRoot 回调置位。 */
    @Volatile
    private var contentReady = false

    // v1.0.5：启动静默检查更新（读 Release latest + 版本号比较）
    @Inject lateinit var updateRepo: UpdateRepository
    // v1.0.5：APK 下载 + 引导安装（与「关于」页共用同一单例）
    @Inject lateinit var installer: ApkInstaller

    // v1.0.5 灵感接力：转待办需要读写灵感与任务
    @Inject lateinit var taskRepo: com.example.earthonline.data.repository.TaskRepository
    @Inject lateinit var memoRepo: com.example.earthonline.data.repository.MemoRepository

    // v1.0.5：启动检查到新版本（非空 = 弹更新提示；用户点「稍后」本次启动不再打扰）
    private var startupUpdate by mutableStateOf<UpdateInfo?>(null)

    // v1.0.5 灵感接力：点通知进入 App 后弹「转待办」面板
    private var inspirePending by mutableStateOf(false)

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 12+ SplashScreen API：清单已设 Theme.EarthOnline.Splash（postSplashScreenTheme 指回主主题）
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)

        /* v1.0.12：全面屏（edge-to-edge）—— 修复底部导航栏下方那条「白线」的根因。
           旧配置里主题把 android:navigationBarColor 设成 ?attr/colorSurface（浅色主题=纯白），
           而窗口内容又不延伸到系统栏 —— 于是胶囊底栏与屏幕底缘之间露出一条系统栏白条。
           现在：内容直达屏幕边缘 + 系统栏全透明 + 关闭系统强制对比遮罩，
           底栏下方直接透出壁纸；纯色背景下与背景同色，不再有割裂白杠。 */
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        val nightMode = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !nightMode
            isAppearanceLightNavigationBars = !nightMode
        }

        /* v1.2.2：撤掉系统默认的退出动画，直接移除启动图层。
           默认行为是让启动图上的图标再「缩放 + 淡出」一次，
           而此刻 Compose 开屏页的图标已经在同一个位置淡入了 ——
           两套动画叠在一起就是两个图标重影着动，正是割裂感的来源之一。
           直接 remove 之后：底色一致（windowSplashScreenBackground = SplashBackground）、
           图标同位置同尺寸（splash_logo 84dp = SplashOverlay 84dp），画面就是原地接力。
           万一 Compose 首帧还没画完，露出的也是主主题的 windowBackground（同样是米色），不会白闪。 */
        splash.setOnExitAnimationListener { splashScreenView -> splashScreenView.remove() }

        /* v1.0.7：撤掉 keepOnScreenCondition —— 这是「开屏动画丢失」的根因。
           之前用 `!contentReady` 把系统启动图一直摁在屏幕上：偏好读取慢的设备上，
           用户看到的是好几秒「纯色空屏」（系统启动图只有一张无图标的纯色底），
           等 Compose 开屏页 underneath 早就把动画播完了 —— 动画等于没放。
           现在改为：系统启动图在首帧（= Compose 开屏页第一帧）立即让位，
           「纯色背景 -> 图标放大/文字上浮/进度线 -> 主页」全程由 SplashOverlay 播放，
           偏好读取期间动画照常进行，无空屏、无闪跳。
           首帧内容安全：onboardingDone 读出前 Root 只铺 SplashBackground 纯色层。 */
        // 首帧绘制前读偏好只需几十毫秒（v1.0.7 起不再用它摁住系统启动图）

        startRoute = intent?.getStringExtra(EXTRA_ROUTE) ?: Screen.Home.route
        // v1.0.5 灵感接力：通知点击带 inspire extra
        if (intent?.getBooleanExtra(EXTRA_INSPIRE, false) == true) inspirePending = true

        setContent {
            EarthOnlineAppRoot(onReady = { contentReady = true }, startRoute = startRoute)
            // v1.0.5：启动检查到新版本 → 弹更新提示（失败静默，不打扰）
            StartupUpdateDialog(
                update = startupUpdate,
                installer = installer,
                onDismiss = { startupUpdate = null }
            )
            // v1.0.5 灵感接力：转待办面板
            if (inspirePending) {
                InspireDialog(taskRepo = taskRepo, memoRepo = memoRepo, onDismiss = { inspirePending = false })
            }
        }

        // v1.0.5：启动 3 秒后静默检查更新（避开首帧渲染高峰）；
        // 无外网 / 被墙 / 限流一律静默，只有确有新版本才弹窗 —— 「稍后」后本次启动不再提醒。
        lifecycleScope.launch {
            delay(3000)
            val info = runCatching {
                updateRepo.checkLatest(AppInfo.repoOwner, AppInfo.repoName).getOrNull()
            }.getOrNull() ?: return@launch
            if (updateRepo.isNewer(info.version, BuildConfig.VERSION_NAME)) {
                startupUpdate = info
            }
        }
    }

    /** 已经在前台时点小组件入口：Activity 复用，只更新目标路由 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        startRoute = intent.getStringExtra(EXTRA_ROUTE) ?: Screen.Home.route
        if (intent.getBooleanExtra(EXTRA_INSPIRE, false)) inspirePending = true
    }

    override fun onPause() {
        super.onPause()
        /* v1.0.0：切到后台时顺手刷一次桌面小组件。
           放在这里而不是每次数据变更都刷：onPause 频率低、时机固定，
           且 Renderer 内部会先判断"有没有已添加的小组件"，没装就是一次空转判断。 */
        OverviewWidget.refresh(this)
    }

    companion object {
        /** 小组件 / 通知等外部入口指定落地页面的 key（值 = Screen 的 route） */
        const val EXTRA_ROUTE = "route"

        /** v1.0.5 灵感接力通知点击标记 */
        const val EXTRA_INSPIRE = "inspire"
    }
}

/**
 * v1.0.5 灵感接力面板：列出最近的灵感（世界日志），点「转待办」写入 To Do 分类。
 */
@Composable
private fun InspireDialog(
    taskRepo: com.example.earthonline.data.repository.TaskRepository,
    memoRepo: com.example.earthonline.data.repository.MemoRepository,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var memos by remember { mutableStateOf<List<com.example.earthonline.data.local.entity.MemoEntity>>(emptyList()) }
    var converted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        memos = runCatching { memoRepo.observeRecent(10).first() }.getOrElse { emptyList() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("💡 跨端灵感接力") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (converted) "已转为待办，去任务页的 To Do 分类看看吧"
                    else "最近的灵感，点「转待办」放进 To Do：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (memos.isEmpty()) {
                    Text("还没有灵感记录", style = MaterialTheme.typography.bodySmall)
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        memos.forEach { m ->
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(
                                    m.text.take(40),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    scope.launch {
                                        runCatching {
                                            taskRepo.insert(
                                                com.example.earthonline.data.local.entity.TaskEntity(
                                                    id = "task_${System.currentTimeMillis()}",
                                                    category = "todo",
                                                    title = m.text.take(80),
                                                    createdAt = com.example.earthonline.util.todayStr(),
                                                    lastModified = com.example.earthonline.util.todayStr()
                                                )
                                            )
                                        }
                                        converted = true
                                    }
                                }) { Text("转待办") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("稍后处理") } }
    )
}

/**
 * v1.0.5：启动检查到新版本时的提示弹窗。
 * - 展示新版本号 + Release 更新说明（截断已有，最长 400 字）；
 * - 有 APK 直链 → 「下载并安装」（Android 8.0+ 先引导授予「安装未知应用」，
 *   回来后自动开始下载；下载完成由 ApkInstaller 的应用级广播调起安装器）；
 * - 无直链 → 打开 Release 页面手动下载；
 * - 「稍后」仅关闭本次弹窗，下次启动或手动「检查更新」会再次提醒。
 * - ApkInstaller 的消息（开始下载 / 下载失败）在此处用 Toast 呈现 ——
 *   「关于」页则用 Snackbar 消费同一条流，两者谁在前台谁消费。
 */
@Composable
private fun StartupUpdateDialog(
    update: UpdateInfo?,
    installer: ApkInstaller,
    onDismiss: () -> Unit
) {
    if (update == null) return
    val context = LocalContext.current
    var pendingUrl by remember { mutableStateOf<String?>(null) }

    // ApkInstaller 消息 → Toast（此页没有 Snackbar 宿主，弹窗场景用 Toast 更轻）
    LaunchedEffect(Unit) {
        installer.events.collect { msg ->
            msg?.let {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                installer.consumeEvent()
            }
        }
    }

    // 「安装未知应用」授权回执：授权成功 → 继续下载；拒绝 → 静默（下次启动再提醒）
    val installPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        val canInstall = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
        val url = pendingUrl
        pendingUrl = null
        if (canInstall && url != null) installer.download(url)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本 v${update.version}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "当前版本 v${AppInfo.version}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (update.notes.isNotBlank()) {
                    Text(
                        update.notes,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .heightIn(max = 180.dp)
                    )
                }
                Text(
                    if (update.apkUrl != null)
                        "下载完成后将自动提示安装；Android 8.0 及以上首次安装需授予「安装未知应用」权限。覆盖安装不会丢失本地数据。"
                    else
                        "未找到 APK 直链，将打开 Release 页面手动下载。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val url = update.apkUrl
                if (url.isNullOrBlank()) {
                    onDismiss()
                    openUrlInBrowser(context, update.releaseUrl)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    !context.packageManager.canRequestPackageInstalls()
                ) {
                    // 先授权再回来下载；弹窗先收起，回来后由授权回执继续
                    pendingUrl = url
                    installPermLauncher.launch(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.fromParts("package", context.packageName, null)
                        )
                    )
                    onDismiss()
                } else {
                    installer.download(url)
                    onDismiss()
                }
            }) { Text(if (update.apkUrl != null) "下载并安装" else "前往更新") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("稍后") } }
    )
}
