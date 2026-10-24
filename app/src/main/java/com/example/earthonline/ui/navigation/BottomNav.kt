package com.example.earthonline.ui.navigation

/**
 * 底部导航项（对应 HTML 的 MOBILE_TABS）。「更多」已移到各页面右上角胶囊按钮。
 *
 * [kind]：导航图标的动画类型（v1.0.10 起图标改为自绘 Canvas 动效，
 * 点击时播放对应微动画：门开 / 画勾 / 拉链 / 书写），不再依赖任何 PNG 资源。
 *
 * [altRoutes]：与该 Tab 同属一个界面、但路由不同的目标（如 new_task 属于任务页）。
 * 命中这些路由时底部 Tab 也要高亮，否则用户从主页点「新建任务」会看到没有任何 Tab 选中。
 */
enum class NavIconKind { HOME, TASKS, BACKPACK, NOTES }

data class BottomNavItem(
    val route: String,
    val label: String,
    val kind: NavIconKind,
    val altRoutes: List<String> = emptyList()
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home.route, Screen.Home.label, NavIconKind.HOME),
    BottomNavItem(
        Screen.Tasks.route, Screen.Tasks.label, NavIconKind.TASKS,
        altRoutes = listOf(Screen.NewTask.route, Screen.NewTodo.route)
    ),
    BottomNavItem(
        Screen.Backpack.route, Screen.Backpack.label, NavIconKind.BACKPACK,
        altRoutes = listOf(Screen.Collections.route)
    ),
    BottomNavItem(Screen.Notes.route, Screen.Notes.label, NavIconKind.NOTES),
)
