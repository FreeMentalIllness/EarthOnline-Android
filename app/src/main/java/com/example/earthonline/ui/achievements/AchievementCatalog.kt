package com.example.earthonline.ui.achievements

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backpack
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Egg
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.LocalLibrary
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.earthonline.util.birthPlusDaysIso
import com.example.earthonline.util.birthPlusIso
import com.example.earthonline.util.dayToIso
import com.example.earthonline.util.localDayOf
import com.example.earthonline.util.localHourOf
import java.text.SimpleDateFormat
import java.util.Locale

/** 成就分类（自动成就按规则归属；手动成就由用户选择，默认 custom） */
data class AchCategory(
    val id: String,
    val label: String,
    /** v1.0.18：emoji → 圆角矢量图标 */
    val icon: ImageVector
)

/** 顺序即 UI 展示顺序；custom 放最后 */
val ACH_CATEGORIES: List<AchCategory> = listOf(
    AchCategory("task", "任务", Icons.Rounded.Checklist),
    AchCategory("bag", "背包", Icons.Rounded.Backpack),
    AchCategory("collection", "收藏", Icons.Rounded.LocalLibrary),
    AchCategory("map", "足迹", Icons.Rounded.Map),
    AchCategory("memo", "日志", Icons.Rounded.EditNote),
    AchCategory("growth", "成长", Icons.Rounded.Eco),
    AchCategory("general", "综合", Icons.Rounded.MilitaryTech),
    // v1.2.0：彩蛋（隐藏成就）。未解锁时 UI 打码显示，解锁后才现身。
    AchCategory("egg", "彩蛋", Icons.Rounded.Egg),
    AchCategory("custom", "自定义", Icons.Rounded.Edit)
)

/** 未分类（v2 旧行）统一落到 custom */
const val ACH_CAT_CUSTOM = "custom"

/** 彩蛋分类 id：未解锁的彩蛋不在列表里剧透标题 */
const val ACH_CAT_EGG = "egg"

/** 彩蛋未解锁时的占位标题（对齐 Web 端的「？？？」） */
const val ACH_EGG_MASK = "？？？"

/** 彩蛋未解锁时的说明文案 */
const val ACH_EGG_MASK_DESC = "隐藏成就 · 达成条件保密。触发一次，就会自己现身。"

fun achCategoryLabel(id: String?): String =
    ACH_CATEGORIES.firstOrNull { it.id == id }?.label ?: "自定义"

fun achCategoryIcon(id: String?): ImageVector =
    ACH_CATEGORIES.firstOrNull { it.id == id }?.icon ?: Icons.Rounded.Edit

/**
 * 成就引擎的实时计数快照。
 * v1.2.0：末尾一组字段专供彩蛋规则 —— 它们都不是"累计数量"，而是从
 * 时间戳 / 标题文本里推导出来的特征（凌晨几点、连续多少天、标题有多长……）。
 */
data class AchStats(
    val tasksDone: Int = 0,
    val items: Int = 0,
    val collections: Int = 0,
    val memos: Int = 0,
    val locations: Int = 0,
    val daysLived: Int = 0,
    val age: Int = 0,
    // —— 彩蛋用 ——
    val gender: String = "",
    val am3Memos: Int = 0,          // 凌晨 3 点写的日志数
    val nightOwlMemos: Int = 0,     // 0~5 点写的日志数
    val earlyBirdMemos: Int = 0,    // 5~7 点写的日志数
    val am3TasksDone: Int = 0,      // 凌晨 3 点完成的任务数
    val blankTitleTries: Int = 0,   // 空白标题保存被拒次数（唯一无法推导、需持久化的计数）
    val longTitleTasks: Int = 0,    // 标题 ≥30 字
    val emojiTitleTasks: Int = 0,   // 标题含 ≥5 个 emoji
    val periodTitleTasks: Int = 0,  // 标题以「。」结尾
    val testTitleTasks: Int = 0,    // 标题含「测试 / test」
    val overdueOpenTodos: Int = 0,  // 逾期未完成待办
    val maxDoneInDay: Int = 0,      // 单日完成任务峰值
    val recordStreak: Int = 0,      // 连续记录（日志）天数
    val emojiOnlyMemos: Int = 0,    // 纯 emoji 日志
    val newYearBirth: Int = 0,      // 生日是 1 月 1 日
    // —— v1.0.3 新增彩蛋 ——
    val throwbackSeen: Int = 0,     // 查看「历年今日」回忆卡次数（需持久化计数）
    val memoryPhotos: Int = 0       // 记忆相册已导入照片数
)

/**
 * 自动成就规则。
 * - goal > 0：current >= goal 即解锁，UI 显示 current/goal 进度
 * - 复合条件：goal 设为子条件总数（如 5），current 返回已满足的子条件数，并用 test 兜底判定
 * 参数顺序上 current 放最后，方便用尾随 lambda 书写。
 */
data class AutoRule(
    val key: String,
    val title: String,
    val desc: String,
    val category: String,
    val goal: Int = 0,
    val test: ((AchStats) -> Boolean)? = null,
    val current: (AchStats) -> Int = { 0 }
) {
    /** 进度分子（展示用，封顶到 goal 免得进度条溢出） */
    fun progressOf(s: AchStats): Int = current(s).coerceAtMost(goal.coerceAtLeast(1))

    fun goalOf(): Int = goal.coerceAtLeast(1)

    fun satisfied(s: AchStats): Boolean = test?.invoke(s) ?: (goal > 0 && current(s) >= goal)
}

/* --------------------------------------------------------------------------
 * v1.2.0：彩蛋规则用的文本 / 时间特征工具
 * -------------------------------------------------------------------------- */

/** 统计 emoji 个数（代理对 + 常见符号区，与 Web 端 EMOJI_RE 口径一致） */
fun countEmoji(text: String): Int {
    var count = 0
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) {
            count++
            i += 2
            continue
        }
        val code = c.code
        if (code in 0x2600..0x27BF || code in 0x2B00..0x2BFF ||
            code in 0x2190..0x21FF || code in 0x2300..0x23FF || code in 0x25A0..0x25FF
        ) count++
        i++
    }
    return count
}

/** 去掉 emoji 与标点后剩下的内容（判断「纯表情」用） */
fun stripEmojiAndPunct(text: String): String =
    text.filter { it.isLetterOrDigit() || it.isWhitespace() }

/** 只含 emoji（≥2 个）且没有实质文字 */
fun isEmojiOnlyText(text: String): Boolean =
    text.isNotBlank() && countEmoji(text) >= 2 && stripEmojiAndPunct(text).isBlank()

/**
 * 最长连续记录天数（日键 YYYY-MM-DD 去重后按自然日相邻计数）。
 * 对应 Web 端 longestRecordStreak。
 */
fun longestStreak(days: List<String>): Int {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val set = days.map { it.trim() }.filter { it.isNotBlank() }.toSortedSet()
    if (set.isEmpty()) return 0
    var best = 1
    var cur = 1
    var prev: Long? = null
    for (d in set) {
        val ms = try {
            fmt.parse(d)?.time
        } catch (e: Exception) {
            null
        } ?: continue
        cur = if (prev != null && ms - prev <= 36 * 3600_000L && ms > prev) cur + 1 else 1
        prev = ms
        if (cur > best) best = cur
    }
    return best
}

/** ISO(UTC) 时间戳里落在 [from, to) 本地小时区间的条数 */
fun countInHourRange(list: List<String?>, from: Int, to: Int): Int =
    list.mapNotNull { localHourOf(it) }.count { it >= from && it < to }

/** 五类各达到 threshold 的已满足项数（「全能」系列的分步进度） */
private fun multiCount(s: AchStats, threshold: Int): Int = listOf(
    s.tasksDone >= threshold,
    s.items >= threshold,
    s.collections >= threshold,
    s.memos >= threshold,
    s.locations >= threshold
).count { it }

/**
 * 自动成就总表（27 条常规 + 18 条彩蛋 = 45 条）。
 * 新增规则只需往这里加一行 —— 引擎会自动建行、回填分类、判定解锁。
 * v1.2.0：彩蛋规则 category="egg"，UI 未解锁时打码；它们的事件时间无法从数据推导
 * （比如"凌晨三点到底是哪一天"可以推导，但"空标题尝试"只能靠计数），一律用解锁瞬间的时间。
 */
val AUTO_RULES: List<AutoRule> = listOf(
    // ————— 任务 —————
    AutoRule("first_task", "初出茅庐", "完成你的第一个任务", "task", 1) { it.tasksDone },
    AutoRule("five_tasks", "渐入佳境", "累计完成 5 个任务", "task", 5) { it.tasksDone },
    AutoRule("ten_tasks", "小有成就", "累计完成 10 个任务", "task", 10) { it.tasksDone },
    AutoRule("thirty_tasks", "三十而立", "累计完成 30 个任务", "task", 30) { it.tasksDone },
    AutoRule("fifty_tasks", "任务大师", "累计完成 50 个任务", "task", 50) { it.tasksDone },
    AutoRule("hundred_tasks", "百炼成钢", "累计完成 100 个任务", "task", 100) { it.tasksDone },

    // ————— 背包 —————
    AutoRule("first_item", "初拾一物", "拾取你的第一个物品", "bag", 1) { it.items },
    AutoRule("ten_items", "背包满满", "累计收集 10 个物品", "bag", 10) { it.items },
    AutoRule("fifty_items", "移动仓库", "累计收集 50 个物品", "bag", 50) { it.items },

    // ————— 收藏 —————
    AutoRule("first_collection", "珍藏", "完成第一次收藏", "collection", 1) { it.collections },
    AutoRule("ten_collections", "博览群书", "累计 10 条收藏", "collection", 10) { it.collections },
    AutoRule("fifty_collections", "收藏大家", "累计 50 条收藏", "collection", 50) { it.collections },

    // ————— 足迹 —————
    AutoRule("first_location", "探索者", "记录第一个足迹", "map", 1) { it.locations },
    AutoRule("five_locations", "走南闯北", "记录 5 个足迹", "map", 5) { it.locations },
    AutoRule("twenty_locations", "环游世界", "记录 20 个足迹", "map", 20) { it.locations },

    // ————— 日志 —————
    AutoRule("first_memo", "记录者", "写下第一条世界日志", "memo", 1) { it.memos },
    AutoRule("ten_memos", "笔耕不辍", "累计 10 条世界日志", "memo", 10) { it.memos },
    AutoRule("fifty_memos", "生活诗人", "累计 50 条世界日志", "memo", 50) { it.memos },

    // ————— 成长（按存活天数 / 周岁） —————
    AutoRule("day_100", "百日之约", "来到地球满 100 天", "growth", 100) { it.daysLived },
    AutoRule("day_365", "一周岁", "来到地球满 365 天", "growth", 365) { it.daysLived },
    AutoRule("day_1000", "千日之行", "来到地球满 1000 天", "growth", 1000) { it.daysLived },
    AutoRule("day_5000", "万水千山", "来到地球满 5000 天", "growth", 5000) { it.daysLived },
    AutoRule("day_10000", "万日玩家", "来到地球满 10000 天", "growth", 10000) { it.daysLived },
    AutoRule("adult_18", "成年礼", "等级（周岁）达到 18", "growth", 18) { it.age },

    // ————— 综合（复合条件：goal = 子条件数 5，current = 已满足数） —————
    AutoRule(
        "all_rounder", "全能玩家", "任务 / 物品 / 收藏 / 日志 / 足迹各至少 1", "general", 5,
        test = { multiCount(it, 1) == 5 }
    ) { multiCount(it, 1) },
    AutoRule(
        "five_star", "五光十色", "任务20 · 物品20 · 收藏10 · 日志10 · 足迹5", "general", 5,
        test = {
            it.tasksDone >= 20 && it.items >= 20 && it.collections >= 10 &&
                it.memos >= 10 && it.locations >= 5
        }
    ) {
        listOf(
            it.tasksDone >= 20, it.items >= 20, it.collections >= 10,
            it.memos >= 10, it.locations >= 5
        ).count { b -> b }
    },
    AutoRule(
        "grand_slam", "大满贯", "任务 / 物品 / 收藏 / 日志 / 足迹各达到 20", "general", 5,
        test = { multiCount(it, 20) == 5 }
    ) { multiCount(it, 20) },

    // ————— v1.2.0：彩蛋（隐藏成就，未解锁时 UI 打码） —————
    AutoRule(
        "egg_walmart", "购物袋玩家", "把性别设置成「沃尔玛购物袋」", "egg", 1,
        test = { it.gender == "walmart" }
    ) { if (it.gender == "walmart") 1 else 0 },
    AutoRule(
        "egg_gender_fluid", "性别是流动的", "性别选一个更离谱的答案", "egg", 1,
        test = { it.gender == "helicopter" || it.gender == "potato" }
    ) { if (it.gender == "helicopter" || it.gender == "potato") 1 else 0 },
    AutoRule("egg_3am", "凌晨三点俱乐部", "在凌晨 3 点写下一条世界日志", "egg", 1) { it.am3Memos },
    AutoRule("egg_night_owl", "夜猫子", "累计 3 条写于 0~5 点的日志", "egg", 3) { it.nightOwlMemos },
    AutoRule("egg_early_bird", "早起的鸟儿", "累计 3 条写于 5~7 点的日志", "egg", 3) { it.earlyBirdMemos },
    AutoRule("egg_midnight_task", "肝帝", "在凌晨 3 点完成一个任务", "egg", 1) { it.am3TasksDone },
    AutoRule("egg_blank_title", "空白也是一种态度", "连续 3 次想保存一个空标题", "egg", 3) { it.blankTitleTries },
    AutoRule("egg_long_title", "一句话说不完", "给任务起一个 ≥30 字的标题", "egg", 1) { it.longTitleTasks },
    AutoRule("egg_emoji_title", "表情包本人", "任务标题里塞进 ≥5 个 emoji", "egg", 1) { it.emojiTitleTasks },
    AutoRule("egg_period_title", "句号强迫症", "累计 3 个以「。」结尾的任务标题", "egg", 3) { it.periodTitleTasks },
    AutoRule("egg_test_title", "测试工程师", "累计 3 个名字里带「测试」的任务", "egg", 3) { it.testTitleTasks },
    AutoRule("egg_overdue", "拖延症晚期", "同时挂着 5 个逾期待办", "egg", 5) { it.overdueOpenTodos },
    AutoRule("egg_marathon", "一日十杀", "同一天里完成 10 个任务", "egg", 10) { it.maxDoneInDay },
    AutoRule("egg_streak_7", "七日之约", "连续 7 天记录世界日志", "egg", 7) { it.recordStreak },
    AutoRule("egg_streak_30", "一个月不断更", "连续 30 天记录世界日志", "egg", 30) { it.recordStreak },
    AutoRule("egg_streak_365", "全年无休", "连续 365 天记录世界日志", "egg", 365) { it.recordStreak },
    AutoRule("egg_memo_emoji", "此时无声胜有声", "写一条只有表情的日志", "egg", 1) { it.emojiOnlyMemos },
    AutoRule(
        "egg_newyear", "元旦宝宝", "生日是 1 月 1 日", "egg", 1,
        test = { it.newYearBirth > 0 }
    ) { it.newYearBirth },
    AutoRule(
        "egg_throwback", "时光回声", "翻开一次「历年今日」的旧时光", "egg", 1
    ) { it.throwbackSeen },
    AutoRule(
        "egg_memory_album", "记忆管理员", "往记忆相册导入第一张老照片", "egg", 1
    ) { it.memoryPhotos }
)

/**
 * 事件时间源快照（批次2：用真实事件发生时间回填 unlockedAt，而非解锁时的当前时间戳）。
 * 各列表均为 ISO（doneAt/memo.createdAt）或 YYYY-MM-DD 日键（其余），排序在引擎内完成。
 */
data class EventSources(
    val doneAtList: List<String>,
    val itemCreatedList: List<String>,
    val collectionCreatedList: List<String>,
    val memoCreatedList: List<String>,
    val locationDateList: List<String>,
    val birthDate: String?
)

/** 升序列表取第 n 个（1-based），不足返回 null */
private fun nth(list: List<String>, n: Int): String? =
    list.filter { it.isNotBlank() }.sorted().getOrNull(n - 1)

/**
 * 给定规则 key，返回其「真实事件时间」ISO；无法推导（如复合成就但某项从未发生）返回 null。
 * - 计数类（任务/物品/收藏/日志/足迹）：取跨越阈值那一条记录的时间
 * - 成长类（存活天数 / 周岁）：由 birthDate 推算对应里程碑日期
 * - 复合成就：取各「首次」事件的最早者
 */
fun eventAtFor(key: String, src: EventSources): String? = when (key) {
    "first_task" -> nth(src.doneAtList, 1)
    "five_tasks" -> nth(src.doneAtList, 5)
    "ten_tasks" -> nth(src.doneAtList, 10)
    "thirty_tasks" -> nth(src.doneAtList, 30)
    "fifty_tasks" -> nth(src.doneAtList, 50)
    "hundred_tasks" -> nth(src.doneAtList, 100)

    "first_item" -> dayToIso(nth(src.itemCreatedList, 1))
    "ten_items" -> dayToIso(nth(src.itemCreatedList, 10))
    "fifty_items" -> dayToIso(nth(src.itemCreatedList, 50))

    "first_collection" -> dayToIso(nth(src.collectionCreatedList, 1))
    "ten_collections" -> dayToIso(nth(src.collectionCreatedList, 10))
    "fifty_collections" -> dayToIso(nth(src.collectionCreatedList, 50))

    "first_location" -> dayToIso(nth(src.locationDateList, 1))
    "five_locations" -> dayToIso(nth(src.locationDateList, 5))
    "twenty_locations" -> dayToIso(nth(src.locationDateList, 20))

    "first_memo" -> nth(src.memoCreatedList, 1)
    "ten_memos" -> nth(src.memoCreatedList, 10)
    "fifty_memos" -> nth(src.memoCreatedList, 50)

    "day_100" -> birthPlusDaysIso(src.birthDate, 100)
    "day_365" -> birthPlusDaysIso(src.birthDate, 365)
    "day_1000" -> birthPlusDaysIso(src.birthDate, 1000)
    "day_5000" -> birthPlusDaysIso(src.birthDate, 5000)
    "day_10000" -> birthPlusDaysIso(src.birthDate, 10000)
    "adult_18" -> birthPlusIso(src.birthDate, 18)

    // 复合成就：取各维度「首次」事件的最早者
    "all_rounder", "five_star", "grand_slam" -> listOfNotNull(
        nth(src.doneAtList, 1),
        dayToIso(nth(src.itemCreatedList, 1)),
        dayToIso(nth(src.collectionCreatedList, 1)),
        nth(src.memoCreatedList, 1),
        dayToIso(nth(src.locationDateList, 1))
    ).sorted().firstOrNull()

    else -> null
}
