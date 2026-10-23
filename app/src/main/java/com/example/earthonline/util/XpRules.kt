package com.example.earthonline.util

/**
 * 经验值（XP）换算规则 —— 全 App 唯一口径。
 *
 * v1.0.3：经验来源多元化 —— 在任务 / 成就 / 日志 / 足迹之外，
 * 新增「拾取物品」「导入记忆照片」「连续记录加成」三类来源。
 * 所有展示 XP 的地方（报告页 / 桌面小组件）统一走 [totalXp]，
 * 不要再手写乘加表达式 —— 以前三个地方各写一份，改规则要改三处。
 */
object XpRules {
    const val TASK_DONE = 10
    const val ACHIEVEMENT = 50
    const val MEMO = 5
    const val LOCATION = 8
    /** v1.0.3：背包每拾取一件物品 */
    const val ITEM = 3
    /** v1.0.3：记忆相册每导入一张照片 */
    const val PHOTO = 2
    /**
     * v1.0.4：主页时间轴自定义里程碑（kind=custom）。
     * 注意：自定义里程碑只进 xp_events 流水，不计入 totalXp 派生口径
     *（派生口径只认任务/成就/日志/足迹/物品/照片六类可枚举计数，无法从行数推导自定义事件）。
     */
    const val CUSTOM_MILESTONE = 15

    /**
     * 累计经验总值。所有端（App 内 / 小组件）共用这一个函数。
     * [items] / [photos] 允许缺省：调用方拿不到这两项计数时传 0。
     */
    fun totalXp(
        tasksDone: Int,
        achievements: Int,
        memos: Int,
        locations: Int,
        items: Int = 0,
        photos: Int = 0
    ): Int = tasksDone * TASK_DONE + achievements * ACHIEVEMENT +
        memos * MEMO + locations * LOCATION + items * ITEM + photos * PHOTO

    /* 连击 / 连续加成机制已废弃（终极重构 · 工程三 死命令）。
       原 streakBonus() 是死代码（从未计入 totalXp），且「短时间内连点」不该获得
       额外收益 —— 成长只由真实累计行为决定，故整体移除，不再保留。 */

    /**
     * 等级称号（v1.0.3）：简单化 —— 不搞花哨命名，用户没自定义时
     * 一律显示「旅行者」；设置过自定义称号则完全以用户的为准。
     */
    const val DEFAULT_TITLE = "旅行者"

    fun titleFor(custom: String?): String =
        custom?.trim()?.take(12)?.ifBlank { null } ?: DEFAULT_TITLE
}
