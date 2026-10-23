package com.example.earthonline.util

/**
 * 游戏化引擎（终极重构 · 工程三）—— 全 App 唯一的「行为 → XP → 等级」口径。
 *
 * 背景：此前 XP 与等级是**断链**的 ——
 *  - XP 由 [XpRules.totalXp] 派生 + [com.example.earthonline.data.repository.XpLedger] 对账，
 *    存在 Room `profile.xp`；
 *  - 而「等级」实际显示的是**周岁**（[lifeStatsOf]），与 XP 毫无换算关系。
 * 用户看到「Lv.28」却和做任何任务无关，这是游戏化机制最大的逻辑空洞。
 *
 * 本引擎补上这一环：给出 XP → 等级的换算曲线与称号体系，
 * 让「做任务 / 写笔记 / 记账」真正推动等级增长。
 *
 * **连击机制已废弃**（用户死命令）：
 *  - [XpRules.streakBonus]（连续记录加成）已删除 —— 它本是死代码，从未计入总量；
 *  - 任务完成连击（TasksScreen 的 combo 撒花放大）同步移除，
 *    「短时间内连点」不该获得额外收益，成长只由**真实累计行为**决定。
 */
object GamificationEngine {

    /** 行为类型：所有能产出 XP 的用户行为都收敛到这里，禁止业务页自己写数字 */
    enum class Action {
        TASK_DONE,
        NOTE,
        /** 记账（与笔记同源口径，保持跨端一致） */
        LEDGER,
        ACHIEVEMENT,
        LOCATION,
        ITEM,
        PHOTO,
        MILESTONE
    }

    /** 单笔行为产出多少 XP（唯一口径，业务页禁止硬编码） */
    fun xpFor(action: Action): Int = when (action) {
        Action.TASK_DONE -> XpRules.TASK_DONE
        Action.NOTE -> XpRules.MEMO
        Action.LEDGER -> XpRules.MEMO
        Action.ACHIEVEMENT -> XpRules.ACHIEVEMENT
        Action.LOCATION -> XpRules.LOCATION
        Action.ITEM -> XpRules.ITEM
        Action.PHOTO -> XpRules.PHOTO
        Action.MILESTONE -> XpRules.CUSTOM_MILESTONE
    }

    /** 第 1 级升第 2 级所需 XP */
    private const val BASE_STEP = 100
    /** 每升一级，下一级门槛的递增步长 */
    private const val STEP_GROWTH = 50

    /**
     * 从 1 级升到 [level] 级所需的**累计** XP 门槛（level=1 时为 0）。
     * 曲线：第 n 级→n+1 级需要 `BASE_STEP + (n-1) * STEP_GROWTH`。
     */
    fun xpToReachLevel(level: Int): Int {
        if (level <= 1) return 0
        var total = 0
        for (n in 1 until level) {
            total += BASE_STEP + (n - 1) * STEP_GROWTH
        }
        return total
    }

    /** 当前等级升下一级所需的 XP 增量 */
    fun xpForNextLevel(level: Int): Int = BASE_STEP + (level - 1) * STEP_GROWTH

    /**
     * XP → 等级信息。这是全 App 唯一的换算入口，
     * 业务页（主页等级卡 / 报告页 / 小组件）一律走它，不要再各写一份。
     */
    fun levelInfo(totalXp: Int): LevelInfo {
        val xp = totalXp.coerceAtLeast(0)
        var level = 1
        while (level < MAX_LEVEL && xp >= xpToReachLevel(level + 1)) level++
        val floor = xpToReachLevel(level)
        val need = xpForNextLevel(level)
        val into = (xp - floor).coerceAtLeast(0)
        // 满级时进度按 1f 处理，避免出现「99% 卡住」的观感
        val progress = if (level >= MAX_LEVEL) 1f else (into.toFloat() / need).coerceIn(0f, 1f)
        return LevelInfo(
            level = level,
            xpIntoLevel = into,
            xpForNext = need,
            progress = progress,
            totalXp = xp,
            title = titleForLevel(level)
        )
    }

    /** 等级上限（防止无限增长导致的数值失控） */
    const val MAX_LEVEL = 60

    /** 称号体系：随等级解锁，给成长一点仪式感 */
    fun titleForLevel(level: Int): String = when {
        level >= 50 -> "传说旅人"
        level >= 35 -> "引路人"
        level >= 20 -> "守望者"
        level >= 10 -> "冒险家"
        level >= 5 -> "探索者"
        else -> XpRules.DEFAULT_TITLE
    }
}

/**
 * 等级快照。
 * @property level 当前等级
 * @property xpIntoLevel 当前级内已积累的 XP
 * @property xpForNext 升到下一级所需 XP
 * @property progress 级内进度 0f~1f（可直接喂给进度条做动画）
 * @property totalXp 累计总 XP
 * @property title 等级称号
 */
data class LevelInfo(
    val level: Int,
    val xpIntoLevel: Int,
    val xpForNext: Int,
    val progress: Float,
    val totalXp: Int,
    val title: String
)
