package com.example.earthonline.ui.navigation

/** 路由表（对应 HTML 的 9 个 PAGES key + 引导页） */
sealed class Screen(val route: String, val label: String) {
    object Home : Screen("home", "主页")
    object Profile : Screen("profile", "个人资料")
    object Tasks : Screen("tasks", "任务")
    object Backpack : Screen("backpack", "背包")
    object Achievements : Screen("achievements", "成就")
    object Data : Screen("data", "数据")
    /** v1.0.6：笔记系统（替换原「数据」底部 Tab；数据看板改由主页卡片/入口进入） */
    object Notes : Screen("notes", "笔记")
    /** v1.0.6：笔记编辑器（新建/编辑，独立路由带 id 参数） */
    object NoteEditor : Screen("note_editor", "编辑笔记")
    object Map : Screen("map", "足迹")
    object Ai : Screen("ai", "系统")
    object Settings : Screen("settings", "设置")

    /**
     * v1.2.3：集中设置页拆出的二级页（点击列表项跳转，各自带返回导航栏）。
     * 用独立路由而非折叠面板，返回栈由 NavHost 维护，从二级页返回正确落到设置主页。
     */
    object SettingsAppearance : Screen("settings_appearance", "外观")
    object SettingsGeneral : Screen("settings_general", "通用")
    object SettingsBackupSync : Screen("settings_backup_sync", "数据与备份")
    object SettingsAbout : Screen("settings_about", "关于")

    /** v1.0.0：周期报告（日报 / 周报 / 年报）。从数据看板页进入，不是底部 Tab。 */
    object Report : Screen("report", "周期报告")

    /** v1.0.2：全部动态列表页。从主页「最近动态」右上角「全部」进入，不是底部 Tab。 */
    object AllActivities : Screen("all_activities", "全部动态")

    /** v1.0.3：记忆相册。从数据看板「记忆相册」入口进入，不是底部 Tab。 */
    object Album : Screen("album", "记忆相册")

    /** v1.0.5：回收站（任务/灵感/物品/收藏软删行，30 天保留）。从「通用」设置进入。 */
    object RecycleBin : Screen("recycle_bin", "回收站")

    /** v1.0.6.6：Steam 游戏数据独立页（背包「收藏 → 我的游戏库」入口进入）。 */
    object SteamLibrary : Screen("steam_library", "我的游戏库")

    /**
     * 「收藏」与「背包」复用同一个界面（背包页的收藏 Tab），但必须是**独立路由**：
     * 若用同一路由加查询参数（backpack?tab=1），两者会共享 destination id 与 SavedState，
     * 导致「点底部背包却停在收藏 Tab」。
     */
    object Collections : Screen("collections", "收藏")

    /**
     * 主页「新建任务」入口：复用任务页并直接弹出新增对话框。
     * 同样必须是独立路由（理由同 Collections），否则与底部「任务」Tab 共享 SavedState，
     * 会出现「从主页进过一次新增后，再点底部任务 Tab 又弹出对话框」。
     */
    object NewTask : Screen("new_task", "新建任务")

    /**
     * v1.0.6.9：速览「Todo」入口：复用任务页（Todo Tab）+ 直接弹出新增对话框。
     * 独立路由理由同 [NewTask]；与 new_task 分开是为了预选 Todo 分类。
     */
    object NewTodo : Screen("new_todo", "新建 Todo")
}

/**
 * 设置二级页路由集合（供 MainScaffold 判断「系统返回键应弹栈而非回主页」）。
 *
 * v1.2.3：必须是**文件级顶层**声明。MainScaffold 通过
 * `import com.example.earthonline.ui.navigation.settingsSubRoutes` 直接引用它；
 * 若误放进 Screen 类体内，它会变成每个路由对象的实例属性，顶层引用将无法解析（编译报错）。
 */
val settingsSubRoutes = setOf(
    Screen.SettingsAppearance.route,
    Screen.SettingsGeneral.route,
    Screen.SettingsBackupSync.route,
    Screen.SettingsAbout.route
)
