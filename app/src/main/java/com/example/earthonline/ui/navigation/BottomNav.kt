package com.example.earthonline.ui.navigation

import com.example.earthonline.R

/**
 * 底部导航项（对应 HTML 的 MOBILE_TABS）。「更多」已移到各页面右上角胶囊按钮。
 *
 * [iconRes]：改用与 Web 端一致的自定义 PNG 图标（res/drawable，48/96 双密度），
 * 不再依赖 Material 图标，保证双端视觉统一。
 *
 * [altRoutes]：与该 Tab 同属一个界面、但路由不同的目标（如 new_task 属于任务页）。
 * 命中这些路由时底部 Tab 也要高亮，否则用户从主页点「新建任务」会看到没有任何 Tab 选中。
 */
data class BottomNavItem(
    val route: String,
    val label: String,
    val iconRes: Int,
    val altRoutes: List<String> = emptyList()
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home.route, Screen.Home.label, R.drawable.ic_nav_home),
    BottomNavItem(
        Screen.Tasks.route, Screen.Tasks.label, R.drawable.ic_nav_tasks,
        altRoutes = listOf(Screen.NewTask.route)
    ),
    BottomNavItem(
        Screen.Backpack.route, Screen.Backpack.label, R.drawable.ic_nav_backpack,
        altRoutes = listOf(Screen.Collections.route)
    ),
    BottomNavItem(Screen.Notes.route, Screen.Notes.label, R.drawable.ic_nav_data),
)
