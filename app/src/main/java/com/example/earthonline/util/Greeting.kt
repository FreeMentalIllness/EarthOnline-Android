package com.example.earthonline.util

import java.util.Calendar

/**
 * 动态问候语（v1.0.3）。
 * 按时间段切换，并感知用户最近的心情记录 —— 让主页开口说第一句话。
 * 全部本地规则，无网络依赖。
 */
object Greeting {

    /**
     * @param hour 当前小时（0-23）
     * @param latestMoodText 最近一条「心情」类日志文本（可为 null）；命中关键词时换成关心式文案
     * @param streakDays 连续记录天数（>0 时附上认可）
     */
    fun build(hour: Int, latestMoodText: String?, streakDays: Int): String {
        val base = when (hour) {
            in 0..4 -> "夜深了，早点休息"
            in 5..8 -> "早上好，新的一天开始了"
            in 9..11 -> "上午好，今天想推进点什么？"
            in 12..13 -> "午安，记得吃口饭"
            in 14..17 -> "下午好，慢慢来也可以"
            in 18..22 -> "晚上好，今天辛苦了"
            else -> "夜深了，今天辛苦了"
        }
        val moodLine = moodCareLine(latestMoodText)
        val streakLine = if (streakDays >= 3) "已连续记录 $streakDays 天" else null
        return listOf(base, moodLine, streakLine).filterNotNull().joinToString(" · ")
    }

    /** 最近心情里带着雨/累/低落等词时，多一句关心（正面心情不画蛇添足） */
    private fun moodCareLine(moodText: String?): String? {
        val t = moodText?.trim() ?: return null
        if (t.isEmpty()) return null
        val lowKeyWords = listOf("累", "烦", "低落", "难过", "焦虑", "压力", "emo", "丧", "哭", "失眠")
        val rainWords = listOf("雨", "阴", "降温")
        return when {
            lowKeyWords.any { t.contains(it) } -> "无论晴雨，你的记录都在"
            rainWords.any { t.contains(it) } -> "外面天气一般，愿心里有光"
            else -> null
        }
    }

    fun now(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
}
