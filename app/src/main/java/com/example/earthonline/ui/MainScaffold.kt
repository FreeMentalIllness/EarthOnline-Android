package com.example.earthonline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.earthonline.ui.theme.AmberPrimary
import com.example.earthonline.ui.theme.GlassSpring
import com.example.earthonline.ui.theme.GlassSurface
import androidx.activity.compose.BackHandler
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.earthonline.ui.achievements.AchievementUnlockHost
import com.example.earthonline.ui.achievements.AchievementViewModel
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.home.AccountingDownloadDialog
import com.example.earthonline.ui.navigation.AppNavHost
import com.example.earthonline.ui.navigation.AnimatedNavIcon
import com.example.earthonline.ui.navigation.NavIconKind
import com.example.earthonline.ui.navigation.Screen
import com.example.earthonline.ui.navigation.bottomNavItems
import com.example.earthonline.ui.navigation.settingsSubRoutes
import com.example.earthonline.ui.theme.ProvideWallpaper
import com.example.earthonline.ui.theme.ProvideLiquidGlass
import com.example.earthonline.ui.settings.SettingsViewModel
import com.example.earthonline.util.AchievementSound
import com.example.earthonline.util.rememberIsOnline
import android.app.Activity
import android.widget.Toast
import java.io.File

/**
 * 底部 Tab / 返回键统一的跳转实现。
 *
 * v1.2.0 修复「从主页点入其他页面后，再点主页 Tab 无反应」：
 * 之前用的是官方 sample 的 `popUpTo{saveState=true} + launchSingleTop + restoreState`
 * 三件套。这套组合在「目标 destination 已在返回栈中且带 SavedState」时会把 navigate
 * 判成 no-op —— 现象正是点 Tab 毫无反应（尤其是从主页进过二级页再点回主页时）。
 *
 * 改成两步、每步都可判定成败：
 *   ① 目标已在返回栈里 → popBackStack 直接弹回（有 Boolean 返回值）
 *   ② 不在栈里（首次进入该 Tab）→ 常规 navigate 压栈
 * 代价是不再记忆各 Tab 的滚动位置（saveState/restoreState 是它的实现前提），
 * 换来的是「点了一定动」—— 导航的确定性比滚动位置重要得多。
 */
private fun navigateToTab(navController: NavHostController, route: String) {
    if (navController.currentDestination?.route == route) return
    if (!navController.popBackStack(route, false)) {
        navController.navigate(route) { launchSingleTop = true }
    }
}

/**
 * 主框架：底部导航（4 个 Tab）+ 内容区 + 记账下载对话框。
 * 「更多」入口是各页面右上角的胶囊按钮，点击在其下方展开下拉菜单（不再占用底部 Tab）；
 * 菜单行为与记账对话框状态统一在此管理。
 * 系统返回键由 NavHost 自动处理（弹栈）；抽屉打开时先关抽屉。
 * 壁纸：不透明度可调（设置页滑杆），作为背景铺底。
 */
/** 壁纸全屏模糊半径上限（dp）：防止滑块拉到高值时低端机帧率崩塌 */
private const val MAX_WALLPAPER_BLUR_DP = 12f

@Composable
fun MainScaffold(
    /** 外部入口（桌面小组件）指定的落地页；默认主页 */
    startRoute: String = Screen.Home.route
) {
    val navController = rememberNavController()
    var showAccounting by remember { mutableStateOf(false) }
    val settingsVm: SettingsViewModel = hiltViewModel()
    /* 成就引擎提到框架层：解锁提示要在任意页面都能弹。
       ⚠️ 同一份 VM 必须往下传给成就页（AppNavHost），否则会存在两个引擎实例，
       各自写一遍「解锁成就」动态。 */
    val achVm: AchievementViewModel = hiltViewModel()
    /* v1.0.0：桌面小组件入口的落地页。
       冷启动时 startRoute 已经作为 NavHost 的 startDestination 生效（不会先闪一帧主页）；
       这里是给「Activity 已存在、onNewIntent 改了路由」的情况补一次导航。 */
    LaunchedEffect(startRoute) {
        if (startRoute != Screen.Home.route && navController.currentDestination?.route != startRoute) {
            navController.navigate(startRoute) { launchSingleTop = true }
        }
    }

    val wallpaper by settingsVm.wallpaper.collectAsStateWithLifecycle(initialValue = "")
    // v1.0.19：壁纸不透明度全域 0~100%（0% = 壁纸完全隐藏，显示纯背景色）
    val wallpaperAlpha by settingsVm.wallpaperAlpha.collectAsStateWithLifecycle(initialValue = 0.35f)
    // v1.0.11：壁纸模糊度（设置→外观 滑块，0=清晰，24=重度毛玻璃）
    val wallpaperBlur by settingsVm.wallpaperBlur.collectAsStateWithLifecycle(initialValue = 0f)
    // v1.0.18：卡片背景不透明度（设置→外观 滑块，全局 GlassCard 生效）
    val cardOpacity by settingsVm.cardOpacity.collectAsStateWithLifecycle(initialValue = 0.74f)
    val achSound by settingsVm.achSound.collectAsStateWithLifecycle(initialValue = true)
    // v1.0.6：当日轮换壁纸（含相册筛选）由 ViewModel 统一计算，避免组合层做 JSON 解析
    val effectiveWallpaper by settingsVm.effectiveWallpaper.collectAsStateWithLifecycle(initialValue = "")
    val quickNotif by settingsVm.quickNotif.collectAsStateWithLifecycle(initialValue = false)
    // v1.0.7：液态玻璃特效开关（默认开启；关闭 = 性能模式，去所有高斯模糊）
    val liquidGlass by settingsVm.liquidGlass.collectAsStateWithLifecycle(initialValue = true)

    // 音效开关变化时同步给播放器（关闭时顺带释放 SoundPool，省一份常驻内存）
    LaunchedEffect(achSound) { AchievementSound.setEnabled(achSound) }
    DisposableEffect(Unit) { onDispose { AchievementSound.release() } }

    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    // 通知栏常驻快捷记录：跟随开关（无通知权限时静默不显示）
    LaunchedEffect(quickNotif) {
        com.example.earthonline.util.QuickAddNotification.update(context, quickNotif)
    }

    /* 性能：壁纸按屏幕尺寸解码。原实现直接用 File 作为 model，Coil 拿不到尺寸约束时
       会按原图解码（现在手机动辄 12MP，一张壁纸 ≈ 48MB 位图），既是内存尖峰也是掉帧源。 */
    val screenW = configuration.screenWidthDp
    val screenH = configuration.screenHeightDp
    // v1.0.19：壁纸不透明度 0% = 完全隐藏 —— 不解码、不渲染、不供玻璃采样，纯背景色接管
    val wallpaperVisible = wallpaperAlpha > 0.01f && effectiveWallpaper.isNotBlank()
    val wallpaperRequest = remember(effectiveWallpaper, screenW, screenH, wallpaperVisible) {
        if (!wallpaperVisible) {
            null
        } else {
            val w = with(density) { screenW.dp.roundToPx() }
            val h = with(density) { screenH.dp.roundToPx() }
            ImageRequest.Builder(context)
                .data(File(effectiveWallpaper))
                .size(w, h)
                .precision(Precision.INEXACT)
                .crossfade(false)
                .build()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    /* 返回层级（Android 端统一）：
       ① 有弹窗（AlertDialog / DropdownMenu）时，Compose 的弹窗自身返回回调优先级更高，
          会先关弹窗，本 BackHandler 不会抢先触发；
       ② 无弹窗且非主页：返回主页（如从 任务/背包/数据 按返回 -> 回主页）；
       ③ 已在主页：连按两次退出（2 秒内第二次按才退出，期间 Toast 提示）。 */
    var lastBackPress by remember { mutableStateOf(0L) }
    BackHandler(enabled = true) {
        val home = Screen.Home.route
        when {
            // v1.2.3：设置二级页（外观/通用/数据与备份/关于）按返回应弹回「设置」主页，
            // 而非直接跳回首页 —— 返回栈由 NavHost 维护，这里只负责触发一次 pop。
            currentRoute in settingsSubRoutes -> navController.popBackStack()
            currentRoute != null && currentRoute != home -> navigateToTab(navController, home)
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPress < 2000) {
                    (context as? Activity)?.finish()
                } else {
                    lastBackPress = now
                    Toast.makeText(context, "再按一次退出", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    ProvideLiquidGlass(liquidGlass) {
        Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        /* v1.0.12：全面屏（edge-to-edge）适配。
           MainActivity 已 setDecorFitsSystemWindows(false)，窗口内容直达屏幕边缘 ——
           底部那条「白线」的根因就是旧配置 `navigationBarColor = ?attr/colorSurface`
           （浅色主题下是纯白），在壁纸上像一条割裂的白杠。
           外层 Scaffold 不再自己吃系统栏 inset（contentWindowInsets = 0），
           由各页内层 Scaffold 的 TopAppBar / bottomBar 各自处理，
           避免「外层 + 内层」双重加深，把内容顶下去两倍状态栏高度。 */
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            /* v1.0.9：底部导航终版 —— 严格对标参考截图的紧凑悬浮胶囊。
               ① 【占位根因修复】此前 120dp 渐隐蒙版 Box 参与 bottomBar 测量，
                  把 innerPadding 底部撑高一截，内容区出现大块死留白且最后一行被顶走。
                  现改为 drawBehind 把蒙版画在边界之外 —— 视觉不变，测量高度只含胶囊本身。
               ② 胶囊尺寸对标参考图：高 58dp、圆角 28dp、左右 12dp / 底部 6dp 留白，
                  图标 20dp + 10sp 小字，贴合屏幕最底部靠渐变 + 模糊自然融合。 */
            /* v1.0.11：底部导航 —— 移除 110dp 渐隐蒙版（默认壁纸下它是「突兀横杠」的根因：
               蒙版用纯 background 色从透明渐变到不透明，在壁纸上会形成一条清晰的色带边界）。
               胶囊本身自带高光描边 + 16dp 环境投影，内容在底栏上方的过渡交给玻璃材质即可；
               navigationBarsPadding 继续负责手势条沉浸式避让。 */
            // v1.0.19：壁纸隐藏时玻璃容器同步断供采样路径（底栏玻璃走纯色档）
            ProvideWallpaper(if (!wallpaperVisible) null else effectiveWallpaper) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 6.dp)
                ) {
                    GlassSurface(
                        shape = RoundedCornerShape(28.dp),
                        showBorder = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            bottomNavItems.forEach { item ->
                                val selected = currentRoute == item.route ||
                                        currentRoute in item.altRoutes ||
                                        navBackStackEntry?.destination?.hierarchy?.any { it.route == item.route } == true
                                NavTab(
                                    selected = selected,
                                    label = item.label,
                                    kind = item.kind,
                                    onClick = { navigateToTab(navController, item.route) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        val darkTheme = isSystemInDarkTheme()
        val isOnline by rememberIsOnline()
        /* v1.0.20：底部让位统一收敛（主页搜索键盘空白断层的根因修复）——
           此前 innerPadding.bottom（导航胶囊排除高度 ≈96dp，普通 padding 不消费 inset）
           与页面自己的 imePadding() 叠加，键盘弹出时内容被双重抬高，
           键盘上方拖出一截空白断层。
           现在内容区底部让位 = max(bottomBar 高度, IME 高度)：
           键盘弹出 → 内容紧贴键盘上沿（胶囊已被键盘盖住，无需再让）；
           键盘收起 → 照常让开悬浮胶囊。各页不再自行叠加 imePadding。 */
        val contentBottomPad = maxOf(
            innerPadding.calculateBottomPadding(),
            with(density) { WindowInsets.ime.getBottom(this).toDp() }
        )
        Box(Modifier.fillMaxSize()) {
            if (wallpaperRequest != null) {
                AsyncImage(
                    model = wallpaperRequest,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        // v1.0.11：壁纸模糊度滑块（0 时不挂 blur，避免无谓的离屏合成开销）
                        // v1.0.6.7：全屏 blur 是本项目最贵的一处 GPU 操作（每帧一次全屏高斯），
                        // 这里给半径加 12dp 硬上限 —— 超出部分观感收益极低但开销指数上升。
                        .then(
                            if (wallpaperBlur > 0.5f) Modifier.blur(wallpaperBlur.coerceAtMost(MAX_WALLPAPER_BLUR_DP).dp)
                            else Modifier
                        ),
                    contentScale = ContentScale.Crop
                )
                /* 性能：用「背景色蒙层」表达不透明度，而不是给图片设 alpha。
                   alpha < 1 会为整张全屏图创建离屏图层（saveLayer），每帧多一次全屏合成；
                   蒙层只是一个半透明矩形，几乎零成本。
                   深色模式：壁纸透出量压到浅色的 4 折 —— 否则亮色壁纸（如蓝橙渐变）
                   会在近黑卡片背后强烈透出，破坏夜间一致性。 */
                val showThrough = if (darkTheme) wallpaperAlpha * 0.4f else wallpaperAlpha
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.background.copy(
                                alpha = (1f - showThrough).coerceIn(0f, 1f)
                            )
                        )
                )
            }
            /* v1.0.14：顶部硬遮挡（应用户要求撤销状态栏沉浸透出壁纸的设计）。
               状态栏区域（时间/电量处）用主题基础背景色实心填充 —— 绝不透出壁纸，
               页面标题/列表从色块下方开始，不再被通知栏遮挡。
               windowInsetsTopHeight 精确取状态栏高度，深浅色主题自动匹配。 */
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(MaterialTheme.colorScheme.background)
            )
            // 大屏/折叠屏适配：内容区限宽居中，避免展开态单列拉满全宽
            // 注入全局壁纸路径，供各处 GlassSurface 就地采样毛玻璃背景
            // v1.0.19：壁纸隐藏（0%）时注入 null —— 卡片玻璃同步退化为纯色底，视觉与背景一致
            ProvideWallpaper(if (!wallpaperVisible) null else effectiveWallpaper) {
                androidx.compose.runtime.CompositionLocalProvider(
                    com.example.earthonline.ui.theme.LocalCardOpacity provides cardOpacity
                ) {
                AppNavHost(
                navController = navController,
                startDestination = startRoute,
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 560.dp)
                    .align(Alignment.TopCenter)
                    .padding(bottom = contentBottomPad),
                achVm = achVm,
                moreActions = MoreMenuActions(
                    onNavigate = { route -> navController.navigate(route) { launchSingleTop = true } },
                    onAccounting = { showAccounting = true }
                )
                )
                } // v1.0.18：CompositionLocalProvider(LocalCardOpacity) 闭合
            }
            // 全局离线横幅：覆盖所有 Tab 与二级页（含地图 / WebDAV / AI 页）
            if (!isOnline) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        // v1.0.12：全面屏后外层 Scaffold 不再吃 inset，横幅自己避让状态栏
                        .statusBarsPadding()
                        .padding(top = 8.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    tonalElevation = 4.dp
                ) {
                    Text(
                        "网络不可用，请检查网络连接",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            // v1.2.1：成就解锁提示（右下角卡片 + 音效），浮在所有页面与导航栏之上
            AchievementUnlockHost(vm = achVm)
            if (showAccounting) {
                AccountingDownloadDialog(onDismiss = { showAccounting = false })
            }
        }
    }
    }
}

/**
 * v1.0.10：底部导航自绘项终版。
 * 动效：点击时触发专属微动画（门开 / 画勾 / 拉链 / 书写，由 AnimatedNavIcon 的 progress 驱动），
 * 同时图标「压下→弹起」回弹（MediumBouncy spring），选中背景胶囊 spring 淡入淡出。
 * 图标为自绘 Canvas 动效（不再依赖 PNG），紧凑胶囊 + 10sp 小字对标参考图。
 */
@Composable
private fun NavTab(
    selected: Boolean,
    label: String,
    kind: NavIconKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = if (selected) AmberPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val bgAlpha by animateFloatAsState(
        targetValue = if (selected) 0.12f else 0f,
        animationSpec = GlassSpring,
        label = "navTabBg"
    )
    /* 图标点击微动画：每次点击 playKey 自增触发一段 0→1 的动画，
       平时（progress=1）显示静态常驻图标。 */
    var playKey by remember { mutableIntStateOf(0) }
    val anim = remember { Animatable(1f) }
    LaunchedEffect(playKey) {
        if (playKey > 0) {
            anim.snapTo(0f)
            anim.animateTo(1f, tween(380))
        }
    }
    /* 选中即触发「压下 → 弹起」回弹 */
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            bounce.snapTo(0.86f)
            bounce.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        } else {
            bounce.snapTo(1f)
        }
    }
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(20.dp))
            .background(AmberPrimary.copy(alpha = bgAlpha))
            .clickable(onClick = {
                playKey++
                onClick()
            }),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedNavIcon(
                kind = kind,
                progress = anim.value,
                tint = tint,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = bounce.value
                        scaleY = bounce.value
                    }
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = tint,
                maxLines = 1
            )
        }
    }
}
