package com.example.earthonline.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.example.earthonline.ui.report.ReportKind
import com.example.earthonline.ui.achievements.AchievementViewModel
import com.example.earthonline.ui.achievements.AchievementsRoute
import com.example.earthonline.ui.accounting.AccountingRoute
import com.example.earthonline.ui.ai.AiRoute
import com.example.earthonline.ui.album.AlbumRoute
import com.example.earthonline.ui.recyclebin.RecycleBinRoute
import com.example.earthonline.ui.steam.SteamLibraryRoute
import com.example.earthonline.ui.backpack.BackpackRoute
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.data.DataRoute
import com.example.earthonline.ui.home.HomeRoute
import com.example.earthonline.ui.home.AllActivitiesRoute
import com.example.earthonline.ui.map.MapRoute
import com.example.earthonline.ui.profile.ProfileRoute
import com.example.earthonline.ui.report.ReportRoute
import com.example.earthonline.ui.settings.SettingsRoute
import com.example.earthonline.ui.settings.AppearanceRoute
import com.example.earthonline.ui.settings.GeneralRoute
import com.example.earthonline.ui.settings.BackupSyncRoute
import com.example.earthonline.ui.settings.AboutRoute
import com.example.earthonline.ui.tasks.TasksRoute
import com.example.earthonline.ui.notes.NotesRoute
import com.example.earthonline.ui.notes.NoteEditorRoute
import com.example.earthonline.ui.theme.LocalWallpaperPath
import com.example.earthonline.ui.theme.ProvideWallpaper
import androidx.navigation.NavType

/**
 * 底部 Tab 路由（与 [bottomNavItems] 保持一致）：这些路由之间的切换视为「Tab 切换」，
 * 转场用淡入 + 极轻微垂直位移；其余路由（二级页）之间的切换视为「入栈 / 出栈」，
 * 走标准右侧推入 / 平滑推出。
 */
private val TAB_ROUTES = setOf(
    Screen.Home.route,
    Screen.Tasks.route,
    Screen.Backpack.route,
    Screen.Notes.route
)

/**
 * v1.0.6.9（死命令 去壁纸化）：壁纸仅允许在「全局一级页面」透出 —— 底部四个 Tab 及其
 * 同界变体（new_task / new_todo / collections）。其余二级详情页一律注入 null 壁纸路径，
 * 其上的 GlassCard / GlassSurface 全部退化为纯色底板，壁纸零透出。
 */
private val WALLPAPER_ROUTES = TAB_ROUTES + setOf(
    Screen.NewTask.route,
    Screen.NewTodo.route,
    Screen.Collections.route
)

/** v1.0.6.9（死命令）：全局统一弹簧转场规格 —— Fade + Slide，中低刚度 + 高阻尼，跟手不弹跳。
 *  注意：Compose 的 slideIn/OutVertically/Horizontally 动画值类型都是 IntOffset（不是 Int），
 *  只有 fadeIn/fadeOut 是 Float —— 两类规格必须分开，混用直接编译期类型不匹配。 */
private fun navSpringFloat() = spring<Float>(
    dampingRatio = 0.92f,
    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
)
private fun navSpringOffset() = spring<androidx.compose.ui.unit.IntOffset>(
    dampingRatio = 0.92f,
    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
)

/**
 * @param moreActions 右上角设置按钮的行为（导航 / 记账页），由 MainScaffold 统一注入
 * @param achVm 成就 VM（由 MainScaffold 创建并共享）。必须复用同一实例 ——
 *              它可以挂在 NavBackStackEntry 上，但那样会与框架层那份并存，
 *              两个引擎同时跑会各写一条「解锁成就」动态。
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    /** 起始页（桌面小组件入口可直达某个 Tab）；NavHost 只在构建时用它，后续跳转走 MainScaffold */
    startDestination: String = Screen.Home.route,
    achVm: AchievementViewModel? = null,
    moreActions: MoreMenuActions = MoreMenuActions(onNavigate = {}, onAccounting = {})
) {
    /* v1.0.6.9（死命令）：壁纸作用域收编 —— 非一级页注入 null，二级页 / 弹窗彻底去壁纸化 */
    val inheritedWallpaper = LocalWallpaperPath.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val wallpaperAllowed = currentRoute == null || currentRoute in WALLPAPER_ROUTES

    ProvideWallpaper(if (wallpaperAllowed) inheritedWallpaper else null) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        /* 转场规范（v1.0.6.9 死命令：全局统一弹簧阻尼）：
           - Tab 间切换：淡入 + 极轻微垂直位移（交叉淡变，翻页不抢眼）；
           - 进入 / 返回二级页：从右侧推入 / 平滑推出（标准栈式位移，推满整屏）。
           四向全部走同一 spring 规格（damping 0.92 / MediumLow），杜绝各向速度不一致的割裂感。 */
        enterTransition = {
            if (initialState.destination.route in TAB_ROUTES && targetState.destination.route in TAB_ROUTES) {
                fadeIn(navSpringFloat()) + slideInVertically(navSpringOffset()) { it / 12 }
            } else {
                fadeIn(navSpringFloat()) + slideInHorizontally(navSpringOffset()) { it }
            }
        },
        exitTransition = {
            if (initialState.destination.route in TAB_ROUTES && targetState.destination.route in TAB_ROUTES) {
                fadeOut(navSpringFloat()) + slideOutVertically(navSpringOffset()) { -it / 12 }
            } else {
                fadeOut(navSpringFloat()) + slideOutHorizontally(navSpringOffset()) { -it }
            }
        },
        popEnterTransition = {
            if (initialState.destination.route in TAB_ROUTES && targetState.destination.route in TAB_ROUTES) {
                fadeIn(navSpringFloat()) + slideInVertically(navSpringOffset()) { -it / 12 }
            } else {
                fadeIn(navSpringFloat()) + slideInHorizontally(navSpringOffset()) { -it }
            }
        },
        popExitTransition = {
            if (initialState.destination.route in TAB_ROUTES && targetState.destination.route in TAB_ROUTES) {
                fadeOut(navSpringFloat()) + slideOutVertically(navSpringOffset()) { it / 12 }
            } else {
                fadeOut(navSpringFloat()) + slideOutHorizontally(navSpringOffset()) { it }
            }
        }
    ) {
        composable(Screen.Home.route) {
            // 主页内的卡片 / 快速入口 / 头像统一走这里跳转（头像 -> 个人资料）
            HomeRoute(
                onNavigate = { route ->
                    navController.navigate(route) { launchSingleTop = true }
                },
                moreActions = MoreMenuActions(
                    onNavigate = { route ->
                        navController.navigate(route) { launchSingleTop = true }
                    },
                    onAccounting = moreActions.onAccounting
                )
            )
        }
        // 阶段 2 批次A：任务 + 背包 已落地
        composable(Screen.Tasks.route) { TasksRoute(moreActions = moreActions) }
        // 主页「新建任务」：进入任务页即弹出新增对话框（独立路由，见 Screen.NewTask 注释）
        composable(Screen.NewTask.route) {
            TasksRoute(moreActions = moreActions, openAddOnEntry = true)
        }
        // v1.0.6.9：速览「Todo」→ 任务页 Todo Tab + 自动弹新建对话框（见 Screen.NewTodo 注释）
        composable(Screen.NewTodo.route) {
            TasksRoute(moreActions = moreActions, openAddOnEntry = true, initialTab = "todo")
        }
        composable(Screen.Backpack.route) {
            BackpackRoute(startTab = 0, moreActions = moreActions)
        }
        // 「收藏」= 背包页的收藏 Tab，独立路由（见 Screen.Collections 注释）
        composable(Screen.Collections.route) {
            BackpackRoute(startTab = 1, moreActions = moreActions)
        }
        // 阶段 2 批次B：成就 + 数据看板（含日历）已落地
        composable(Screen.Achievements.route) {
            if (achVm != null) AchievementsRoute(vm = achVm, moreActions = moreActions)
            else AchievementsRoute(moreActions = moreActions)
        }
        composable(Screen.Data.route) { DataRoute(moreActions = moreActions) }
        // v1.0.6：笔记系统（替换原「数据」底部 Tab；数据看板改由主页卡片 / 入口进入）
        composable(Screen.Notes.route) {
            NotesRoute(
                moreActions = moreActions,
                onEdit = { id, folder ->
                    navController.navigate(
                        Screen.NoteEditor.route + "?id=${id ?: ""}&folder=${folder ?: ""}"
                    ) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(
            route = Screen.NoteEditor.route + "?id={id}&folder={folder}",
            arguments = listOf(
                navArgument("id") { defaultValue = ""; type = NavType.StringType },
                navArgument("folder") { defaultValue = ""; type = NavType.StringType }
            )
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id").orEmpty()
            // v1.0.6.9：从某个分类下新建笔记时带上 folderId，保存后自动归入该分类
            val folder = backStackEntry.arguments?.getString("folder")?.takeIf { it.isNotBlank() }
            NoteEditorRoute(
                id = id,
                moreActions = moreActions,
                folderId = folder,
                onBack = { navController.popBackStack() }
            )
        }
        // v1.0.0：周期报告（日报 / 周报 / 年报），从数据看板进入（带 kind 参数决定首屏周期）
        composable(
            route = Screen.Report.route + "?kind={kind}",
            arguments = listOf(navArgument("kind") { defaultValue = "day" })
        ) { backStackEntry ->
            val kind = when (backStackEntry.arguments?.getString("kind")) {
                "week" -> ReportKind.WEEK
                "year" -> ReportKind.YEAR
                else -> ReportKind.DAY
            }
            ReportRoute(moreActions = moreActions, startKind = kind)
        }
        // 阶段 3：个人资料页 + 高德足迹地图已落地
        composable(Screen.Profile.route) { ProfileRoute(moreActions = moreActions) }
        composable(Screen.Map.route) { MapRoute(moreActions = moreActions) }
        // v1.0.2：全部动态列表页（主页「最近动态」右上角「全部」进入）
        composable(Screen.AllActivities.route) {
            AllActivitiesRoute(
                onBack = { navController.popBackStack() },
                moreActions = moreActions
            )
        }
        // v1.0.3：记忆相册（数据看板「记忆相册」入口进入）
        composable(Screen.Album.route) { AlbumRoute(moreActions = moreActions) }
        // v1.0.5：回收站（设置-通用「回收站」入口进入）
        composable(Screen.RecycleBin.route) {
            RecycleBinRoute(onBack = { navController.popBackStack() })
        }
        // v1.0.6.6：Steam 游戏数据独立页（背包「收藏 → 我的游戏库」入口进入）
        composable(Screen.SteamLibrary.route) {
            SteamLibraryRoute(onBack = { navController.popBackStack() })
        }
        composable(Screen.Ai.route) { AiRoute(moreActions = moreActions) }
        // v1.0.6.9：记账页（应用内账本，取代原外部 App 下载引导；autoAdd=1 时自动弹键盘录入面板）
        composable(
            route = Screen.Accounting.route + "?autoAdd={autoAdd}",
            arguments = listOf(navArgument("autoAdd") { defaultValue = false; type = NavType.BoolType })
        ) { backStackEntry ->
            val autoAdd = backStackEntry.arguments?.getBoolean("autoAdd") ?: false
            AccountingRoute(moreActions = moreActions, autoAddInput = autoAdd)
        }
        composable(Screen.Settings.route) { SettingsRoute(moreActions = moreActions) }
        // v1.2.3：集中设置页拆出的二级页（独立路由，返回栈由 NavHost 维护）
        composable(Screen.SettingsAppearance.route) {
            AppearanceRoute(navController = navController, moreActions = moreActions)
        }
        composable(Screen.SettingsGeneral.route) {
            GeneralRoute(navController = navController, moreActions = moreActions)
        }
        composable(Screen.SettingsBackupSync.route) {
            BackupSyncRoute(navController = navController, moreActions = moreActions)
        }
        composable(Screen.SettingsAbout.route) {
            AboutRoute(navController = navController, moreActions = moreActions)
        }
    }
    } // v1.0.6.9：ProvideWallpaper（壁纸作用域收编）闭合
}
