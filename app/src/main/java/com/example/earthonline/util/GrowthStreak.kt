package com.example.earthonline.util

/**
 * 连续记录成长机制（v1.0.3）。
 *
 * 口径：一天内留下任何一种记录（世界日志 / 完成任务 / 足迹）都算「记录日」。
 * 对用户不惩罚 —— 今天还没记录不断档（昨天连着就算），断更只鼓励回归、不清空历史荣誉
 * （最长的连续天数由成就系统 egg_streak_* 单独记录，这里只管「当前连续」与「生长阶段」）。
 */
object GrowthStreak {

    /** 当前连续记录天数。今天没有记录时从昨天起算（今天还没过完，不算断）。 */
    fun currentStreak(dayKeys: Set<String>): Int {
        if (dayKeys.isEmpty()) return 0
        val day = runCatching { DAY_FMT.parse(todayStr())?.time }.getOrNull() ?: return 0
        val DAY_MS = 24 * 3600_000L
        // 今天有记录从今天数；今天还没有则从昨天数（宽限今天）
        var cursor = if (todayStr() in dayKeys) day else day - DAY_MS
        var streak = 0
        while (millisToDayStr(cursor) in dayKeys) {
            streak++
            cursor -= DAY_MS
        }
        return streak
    }

    /**
     * 生长阶段（界面随投入逐渐生长）：
     * 0 无记录 · 1 萌芽(≥3天) · 2 抽枝(≥7天) · 3 繁茂(≥21天) · 4 参天(≥60天)
     * 阈值刻意低 —— 3 天就有可见变化，别让用户等一周。
     */
    fun stage(streakDays: Int): Int = when {
        streakDays >= 60 -> 4
        streakDays >= 21 -> 3
        streakDays >= 7 -> 2
        streakDays >= 3 -> 1
        else -> 0
    }

    /** 阶段名（主页时间轴卡展示） */
    fun stageLabel(stage: Int): String = when (stage) {
        4 -> "参天"
        3 -> "繁茂"
        2 -> "抽枝"
        1 -> "萌芽"
        else -> ""
    }

    /**
     * 回归鼓励语（不惩罚）：距上次记录已断 N 天时给一句温和的邀请；
     * [lastRecordDay] 为 null（从未记录）或未断更返回 null。
     */
    fun comebackMessage(dayKeys: Set<String>): String? {
        if (dayKeys.isEmpty()) return null
        val last = dayKeys.maxOrNull() ?: return null
        val gap = daysBetween(last, todayStr()) ?: return null
        return when {
            gap >= 2 -> "有 $gap 天没写日记了。回来继续，你的星球一直在。"
            else -> null
        }
    }

    private fun daysBetween(fromDay: String, toDay: String): Int? {
        val f = runCatching { DAY_FMT.parse(fromDay)?.time }.getOrNull() ?: return null
        val t = runCatching { DAY_FMT.parse(toDay)?.time }.getOrNull() ?: return null
        return ((t - f) / (24 * 3600_000L)).toInt()
    }

    private val DAY_FMT = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
}
