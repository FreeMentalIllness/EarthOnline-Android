package com.example.earthonline.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val ISO_FMT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}
private val DAY_FMT = SimpleDateFormat("yyyy-MM-dd", Locale.US)

/** 生成全局唯一 id（对应 HTML 的 uid(prefix)，用纳秒+毫秒避免 java.time 以兼容 minSdk 24） */
fun uid(prefix: String = "id"): String {
    val rnd = (System.nanoTime() and 0xFFFFFF).toString(36)
    return "${prefix}_${System.currentTimeMillis().toString(36)}_${rnd}"
}

/** 当前 UTC 时间，ISO 字符串（对应 HTML 的 ISO 时间戳字段） */
fun nowIso(): String = ISO_FMT.format(Date())

/** 当前本地日键 YYYY-MM-DD（对应 HTML 的 todayStr / 日字符串字段） */
fun todayStr(): String = DAY_FMT.format(Date())

/** ISO(UTC) → 本地短日期 MM-DD（非法 / 空返回空串）。列表卡片「类型 · 日期」用。 */
fun formatDateShort(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val ms = isoToMillis(iso) ?: return ""
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    val m = (c.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
    val d = c.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
    return "$m-$d"
}

/** 毫秒 -> YYYY-MM-DD（日期选择器回填用） */
fun millisToDayStr(millis: Long): String = DAY_FMT.format(Date(millis))

/* --------------------------------------------------------------------------
 * v1.2.0：彩蛋成就需要「本地时区」的小时与日键
 * 数据层统一存 UTC ISO（nowIso），直接切子串拿到的是 UTC 时间 —— 东八区晚上 8 点
 * 记的日志会被算成"第二天"，凌晨 3 点也会被算成上午 11 点。所以这里统一先解析成
 * 毫秒再按本地日历取值。
 * Web 端 toISOString() 带毫秒（...T12:00:00.000Z），本端 nowIso() 不带，两种都要认。
 * -------------------------------------------------------------------------- */
fun isoToMillis(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    val raw = iso.trim()
    val normalized = if (raw.length > 20 && raw[10] == 'T' && (raw[19] == '.' || raw[19] == '+')) {
        raw.substring(0, 19) + "Z"
    } else raw
    return try {
        ISO_FMT.parse(normalized)?.time
    } catch (e: Exception) {
        null
    }
}

/** ISO(UTC) -> 本地小时 0~23；解析失败返回 null */
fun localHourOf(iso: String?): Int? {
    val ms = isoToMillis(iso) ?: return null
    return Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.HOUR_OF_DAY)
}

/** ISO(UTC) -> 本地日键 YYYY-MM-DD；解析失败返回 null */
fun localDayOf(iso: String?): String? {
    val ms = isoToMillis(iso) ?: return null
    return DAY_FMT.format(Date(ms))
}

/**
 * 由出生日期（YYYY-MM-DD）计算周岁（= 等级）。
 * 对应 HTML 的 getAge：禁止对 ISO 时间做 slice，统一走本地日历比较。
 */
fun ageFromBirthDate(birth: String?): Int? {
    if (birth.isNullOrBlank()) return null
    val parts = birth.split("-").mapNotNull { it.toIntOrNull() }
    if (parts.size != 3) return null
    val (y, m, d) = parts
    val b = Calendar.getInstance().apply {
        set(y, m - 1, d, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (b.get(Calendar.YEAR) != y) return null // 非法日期（如 2 月 30 日）
    val now = Calendar.getInstance()
    var age = now.get(Calendar.YEAR) - b.get(Calendar.YEAR)
    if (now.get(Calendar.DAY_OF_YEAR) < b.get(Calendar.DAY_OF_YEAR)) age--
    return if (age in 0..200) age else null
}

/* --------------------------------------------------------------------------
 * v1.0.0：周期性报告的时间边界
 *
 * 报告要「按时间范围查库」，边界必须落在**本地时区的 00:00**：
 * 东八区用户的「今天」是 UTC 昨天 16:00 到今天 16:00，若直接拿 UTC 的 00:00 切，
 * 早上 8 点完成的 3 个任务会算进"昨天"。所以统一由本地 Calendar 归零后再转 ISO。
 *
 * 返回的仍是 UTC ISO 定长串，与 doneAt / createdAt / unlockedAt 同一格式，
 * 可以直接做字符串区间比较（字典序 = 时间序）。
 * -------------------------------------------------------------------------- */

/** 本地日偏移 offsetDays 天后的 00:00 → ISO（0=今天零点，1=明天零点，-6=6 天前零点） */
fun dayStartIso(offsetDays: Int = 0): String {
    val c = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    c.add(Calendar.DATE, offsetDays)
    return ISO_FMT.format(c.time)
}

/** 本地日偏移 offsetDays 天后的日键 YYYY-MM-DD */
fun dayStrOffset(offsetDays: Int = 0): String {
    val c = Calendar.getInstance()
    c.add(Calendar.DATE, offsetDays)
    return DAY_FMT.format(c.time)
}

/** 今年 1 月 1 日 00:00（本地）→ ISO；yearOffset=-1 即去年元旦 */
fun yearStartIso(yearOffset: Int = 0): String {
    val c = Calendar.getInstance().apply {
        set(Calendar.MONTH, Calendar.JANUARY)
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    c.add(Calendar.YEAR, yearOffset)
    return ISO_FMT.format(c.time)
}

/** ISO(UTC) → 本地月份下标 0~11；解析失败返回 null（年报按 12 个月聚合用） */
fun localMonthOf(iso: String?): Int? {
    val ms = isoToMillis(iso) ?: return null
    return Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.MONTH)
}

/** 两个本地日键闭区间判断（字典序即时间序，对应 HTML inDayRange/dayKeyOf） */
fun inDayRange(k: String?, start: String?, end: String?): Boolean {
    if (k.isNullOrBlank()) return false
    if (!start.isNullOrBlank() && k < start) return false
    if (!end.isNullOrBlank() && k > end) return false
    return true
}

/** YYYY-MM-DD（本地日键）→ ISO UTC 午夜；非法返回 null（对应 HTML 的 dayToIso） */
fun dayToIso(day: String?): String? {
    if (day.isNullOrBlank()) return null
    val parts = day.split("-").mapNotNull { it.toIntOrNull() }
    if (parts.size != 3) return null
    val (y, m, d) = parts
    val c = Calendar.getInstance().apply {
        set(y, m - 1, d, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (c.get(Calendar.YEAR) != y || c.get(Calendar.MONTH) != m - 1) return null
    return ISO_FMT.format(c.time)
}

/** 出生日期 + N 年（周岁生日）→ ISO；无生日/非法返回 null */
fun birthPlusIso(birth: String?, years: Int): String? {
    if (birth.isNullOrBlank()) return null
    val parts = birth.split("-").mapNotNull { it.toIntOrNull() }
    if (parts.size != 3) return null
    val (y, m, d) = parts
    val c = Calendar.getInstance().apply {
        set(y + years, m - 1, d, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (c.get(Calendar.YEAR) != y + years) return null
    return ISO_FMT.format(c.time)
}

/** 出生日期 + N 天（存活天数里程碑，如 day_100）→ ISO；非法返回 null */
fun birthPlusDaysIso(birth: String?, days: Int): String? {
    if (birth.isNullOrBlank()) return null
    val parts = birth.split("-").mapNotNull { it.toIntOrNull() }
    if (parts.size != 3) return null
    val (y, m, d) = parts
    val c = Calendar.getInstance().apply {
        set(y, m - 1, d, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }
    c.add(Calendar.DATE, days)
    return ISO_FMT.format(c.time)
}
