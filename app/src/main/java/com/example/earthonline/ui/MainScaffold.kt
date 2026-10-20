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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.res.painterResource
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
    val wallpaperAlpha by settingsVm.wallpaperAlpha.collectAsStateWithLifecycle(initialValue = 0.35f)
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
    val wallpaperRequest = remember(effectiveWallpaper, screenW, screenH) {
        if (effectiveWallpaper.isBlank()) {
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
        bottomBar = {
            /* v1.0.9：底部导航终版 —— 严格对标参考截图的紧凑悬浮胶囊。
               ① 【占位根因修复】此前 120dp 渐隐蒙版 Box 参与 bottomBar 测量，
                  把 innerPadding 底部撑高一截，内容区出现大块死留白且最后一行被顶走。
                  现改为 drawBehind 把蒙版画在边界之外 —— 视觉不变，测量高度只含胶囊本身。
               ② 胶囊尺寸对标参考图：高 58dp、圆角 28dp、左右 12dp / 底部 6dp 留白，
                  图标 20dp + 10sp 小字，贴合屏幕最底部靠渐变 + 模糊自然融合。 */
            ProvideWallpaper(effectiveWallpaper.ifBlank { null }) {
                val fadeColor = MaterialTheme.colorScheme.background
                Box(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .drawBehind {
                            // 底部渐隐蒙版：向上画 110dp，内容在底栏上方柔和淡出
                            val h = 110.dp.toPx()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to fadeColor.copy(alpha = 0f),
                                        0.55f to fadeColor.copy(alpha = 0f),
                                        1.0f to fadeColor
                                    )
                                ),
                                topLeft = Offset(0f, -h),
                                size = Size(size.width, h)
                            )
                        }
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 6.dp)
                ) {
                    GlassSurface(
                        shape = RoundedCornerShape(28.dp),
                        showBorder = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .shadow(
                                elevation = 12.dp,
                                shape = RoundedCornerShape(28.dp),
                                ambientColor = Color.Black,
                                spotColor = Color.Black
                            )
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
                                    iconRes = item.iconRes,
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
        Box(Modifier.fillMaxSize()) {
            if (wallpaperRequest != null) {
                AsyncImage(
                    model = wallpaperRequest,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
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
            // 大屏/折叠屏适配：内容区限宽居中，避免展开态单列拉满全宽
            // 注入全局壁纸路径，供各处 GlassSurface 就地采样毛玻璃背景
            ProvideWallpaper(if (effectiveWallpaper.isBlank()) null else effectiveWallpaper) {
                AppNavHost(
                navController = navController,
                startDestination = startRoute,
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 560.dp)
                    .align(Alignment.TopCenter)
                    .padding(innerPadding),
                achVm = achVm,
                moreActions = MoreMenuActions(
                    onNavigate = { route -> navController.navigate(route) { launchSingleTop = true } },
                    onAccounting = { showAccounting = true }
                )
                )
            }
            // 全局离线横幅：覆盖所有 Tab 与二级页（含地图 / WebDAV / AI 页）
            if (!isOnline) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = innerPadding.calculateTopPadding() + 8.dp),
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
 * v1.0.9：底部导航自绘项终版。
 * 动效对标参考视频：点击选中时图标「压下→弹起」回弹（MediumBouncy spring），
 * 选中背景胶囊 spring 淡入淡出，切换 Tab 时是一段连续过渡而非硬切。
 * 图标换用 v1.0.9 矢量图标（ic_tab_*），20dp + 10sp 小字，对标参考图的紧凑层级。
 */
@Composable
private fun NavTab(
    selected: Boolean,
    label: String,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = if (selected) AmberPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val bgAlpha by animateFloatAsState(
        targetValue = if (selected) 0.12f else 0f,
        animationSpec = GlassSpring,
        label = "navTabBg"
    )
    /* 选中即触发一次「压下 → 弹起」：snapTo 0.86 后用 MediumBouncy 弹回 1f，
       视觉上就是参考视频里图标被按下后 Q 弹复位的点击反馈。 */
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            bounce.snapTo(0.86f)
            bounce.animateTo(
                1f,
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        } else {
            bounce.snapTo(1f)
        }
    }
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(20.dp))
            .background(AmberPrimary.copy(alpha = bgAlpha))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                tint = tint,
                modifier = Modifier
                    .size(20.dp)
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
