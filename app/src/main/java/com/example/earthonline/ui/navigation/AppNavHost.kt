package com.example.earthonline.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.earthonline.ui.report.ReportKind
import com.example.earthonline.ui.achievements.AchievementViewModel
import com.example.earthonline.ui.achievements.AchievementsRoute
import com.example.earthonline.ui.ai.AiRoute
import com.example.earthonline.ui.album.AlbumRoute
import com.example.earthonline.ui.recyclebin.RecycleBinRoute
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
 * @param moreActions 右上角设置按钮的行为（导航 / 记账下载框），由 MainScaffold 统一注入
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
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        /* 转场规范（死磕细节批）：
           - Tab 间切换：淡入 + 极轻微垂直位移（交叉淡变，翻页不抢眼）；
           - 进入 / 返回二级页：从右侧推入 / 平滑推出（标准栈式位移，推满整屏）。 */
        enterTransition = {
            if (initialState.destination.route in TAB_ROUTES && targetState.destination.route in TAB_ROUTES) {
                fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 10 }
            } else {
                fadeIn(tween(200)) + slideInHorizontally(tween(300)) { it }
            }
        },
        exitTransition = {
            if (initialState.destination.route in TAB_ROUTES && targetState.destination.route in TAB_ROUTES) {
                fadeOut(tween(200)) + slideOutVertically(tween(260)) { -it / 10 }
            } else {
                fadeOut(tween(200)) + slideOutHorizontally(tween(300)) { -it }
            }
        },
        popEnterTransition = {
            if (initialState.destination.route in TAB_ROUTES && targetState.destination.route in TAB_ROUTES) {
                fadeIn(tween(220)) + slideInVertically(tween(260)) { -it / 10 }
            } else {
                fadeIn(tween(200)) + slideInHorizontally(tween(300)) { -it }
            }
        },
        popExitTransition = {
            if (initialState.destination.route in TAB_ROUTES && targetState.destination.route in TAB_ROUTES) {
                fadeOut(tween(200)) + slideOutVertically(tween(260)) { it / 10 }
            } else {
                fadeOut(tween(200)) + slideOutHorizontally(tween(300)) { it }
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
                onEdit = { id ->
                    navController.navigate(Screen.NoteEditor.route + "?id=${id ?: ""}") {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(
            route = Screen.NoteEditor.route + "?id={id}",
            arguments = listOf(navArgument("id") { defaultValue = ""; type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id").orEmpty()
            NoteEditorRoute(
                id = id,
                moreActions = moreActions,
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
        composable(Screen.Ai.route) { AiRoute(moreActions = moreActions) }
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
}
