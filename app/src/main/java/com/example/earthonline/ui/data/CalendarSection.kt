package com.example.earthonline.ui.data

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.earthonline.data.local.entity.ActivityEntity
import com.example.earthonline.data.local.entity.TaskEntity
import com.example.earthonline.ui.theme.AmberPrimary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val YM_FMT = SimpleDateFormat("yyyy-MM", Locale.US)
private val WEEK_LABELS = listOf("日", "一", "二", "三", "四", "五", "六")

/** 当前 yyyy-MM（本地） */
private fun currentYearMonth(): String = YM_FMT.format(Calendar.getInstance().time)

/** 今天所在月的日（1-31），非本月返回 -1 */
private fun todayDayOfMonth(yearMonth: String): Int {
    val now = currentYearMonth()
    if (now != yearMonth) return -1
    return Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
}

/** 月份 +delta（可为负） */
private fun shiftMonth(yearMonth: String, delta: Int): String {
    val (y, m) = yearMonth.split("-").map { it.toInt() }
    val cal = Calendar.getInstance().apply { set(y, m - 1, 1) }
    cal.add(Calendar.MONTH, delta)
    return YM_FMT.format(cal.time)
}

/** 生成月历网格：前置空格(null) + 1..daysInMonth + 后置空格，保证 7 的倍数 */
private fun buildMonthCells(yearMonth: String): List<Int?> {
    val (y, m) = yearMonth.split("-").map { it.toInt() }
    val cal = Calendar.getInstance().apply { set(y, m - 1, 1) }
    val firstWeekday = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY) // 0..6
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val cells = mutableListOf<Int?>()
    repeat(firstWeekday) { cells.add(null) }
    for (d in 1..daysInMonth) cells.add(d)
    while (cells.size % 7 != 0) cells.add(null)
    return cells
}

private fun dayKey(yearMonth: String, day: Int): String =
    "$yearMonth-${String.format(Locale.US, "%02d", day)}"

/**
 * 活跃日历组件：嵌入「数据」页。展示所选月份的每日活跃强度（点大小）与任务截止标记，
 * 点击某日下钻显示当天动态与截止任务。对应 HTML 的世界日志日历 / 活跃热力。
 */
@Composable
fun CalendarSection(activities: List<ActivityEntity>, tasks: List<TaskEntity>) {
    var yearMonth by remember { mutableStateOf(currentYearMonth()) }
    var selectedDay by remember { mutableStateOf(todayDayOfMonth(yearMonth).coerceAtLeast(1)) }

    val cells by remember(yearMonth) { mutableStateOf(buildMonthCells(yearMonth)) }
    val actMap by remember(activities, yearMonth) {
        mutableStateOf(
            activities.filter { it.time.take(7) == yearMonth }
                .groupingBy { it.time.take(10).takeLast(2).toIntOrNull() ?: -1 }
                .eachCount()
        )
    }
    val tasksByDay by remember(tasks, yearMonth) {
        mutableStateOf(
            tasks.filter { it.dueDate?.take(7) == yearMonth && it.dueDate != null }
                .groupBy { it.dueDate!!.takeLast(2).toIntOrNull() ?: -1 }
        )
    }

    val selectedKey = dayKey(yearMonth, selectedDay)
    val dayActs = remember(activities, selectedKey) {
        activities.filter { it.time.take(10) == selectedKey }
    }
    val dayTasks = remember(tasksByDay, selectedDay) { tasksByDay[selectedDay] ?: emptyList() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = {
                yearMonth = shiftMonth(yearMonth, -1)
                selectedDay = todayDayOfMonth(yearMonth).coerceAtLeast(1)
            }) { Icon(Icons.Rounded.ChevronLeft, "上个月") }
            Text(yearMonth, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = {
                yearMonth = shiftMonth(yearMonth, 1)
                selectedDay = todayDayOfMonth(yearMonth).coerceAtLeast(1)
            }) { Icon(Icons.Rounded.ChevronRight, "下个月") }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            WEEK_LABELS.forEach { w ->
                Text(
                    w, Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            cells.chunked(7).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { day ->
                        Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                            if (day != null) {
                                val count = actMap[day] ?: 0
                                val hasDue = (tasksByDay[day]?.isNotEmpty() == true)
                                val selected = day == selectedDay
                                val intensity = when {
                                    count <= 0 -> 0f
                                    count < 3 -> 0.35f
                                    count < 6 -> 0.65f
                                    else -> 1f
                                }
                                Surface(
                                    color = if (selected) AmberPrimary
                                    else if (count > 0) AmberPrimary.copy(alpha = intensity)
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxSize().clickable {
                                        selectedDay = day
                                    }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            "$day",
                                            color = if (selected || count > 0) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        if (hasDue) {
                                            Box(
                                                Modifier.align(Alignment.TopEnd).padding(3.dp)
                                                    .size(6.dp).clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.error)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        Text(
            "$selectedKey 的明细",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        if (dayTasks.isNotEmpty()) {
            dayTasks.forEach { t ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            "截止",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(t.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
        }
        if (dayActs.isNotEmpty()) {
            dayActs.forEach { a ->
                Text(
                    "• ${a.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (dayTasks.isEmpty() && dayActs.isEmpty()) {
            Text("这一天还没有记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
