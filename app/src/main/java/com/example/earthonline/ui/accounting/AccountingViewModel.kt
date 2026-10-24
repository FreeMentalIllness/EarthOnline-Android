package com.example.earthonline.ui.accounting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.earthonline.data.local.datastore.SettingsDataStore
import com.example.earthonline.util.nowIso
import com.example.earthonline.util.uid
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** 账本流水一条（v1.0.6.9：DataStore JSON 持久化，不动 Room 表、不碰跨端同步契约） */
@Serializable
data class LedgerEntry(
    val id: String,
    /** 金额（元，正数；收入/支出由 [income] 区分） */
    val amount: Double,
    val income: Boolean,
    val category: String,
    val note: String = "",
    /** ISO 时间串 */
    val time: String
)

/** 月度汇总（当年当月口径） */
data class LedgerSummary(
    val expense: Double = 0.0,
    val income: Double = 0.0,
    val count: Int = 0
) {
    val balance: Double get() = income - expense
}

/**
 * 记账页 ViewModel（v1.0.6.9）。
 *
 * 背景：原生端原来没有账本，「记账」入口只给一个外部 App 下载引导框。
 * 本轮按用户要求恢复「老版记账页面」形态：应用内账本 —— 快捷录入 + 流水 + 月度汇总。
 * 存储走 DataStore JSON（与相册分类 / 时间轴自定义事件同套路），**零 Room 迁移**，
 * 因此不影响 WebDAV 备份与跨端数据同步契约。
 */
@HiltViewModel
class AccountingViewModel @Inject constructor(
    private val settingsDs: SettingsDataStore
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** 全部流水（按时间倒序，新的在上） */
    val entries: StateFlow<List<LedgerEntry>> = settingsDs.accountingLedgerJson
        .map { txt ->
            if (txt.isBlank()) emptyList()
            else runCatching {
                json.decodeFromString(ListSerializer(LedgerEntry.serializer()), txt)
                    .sortedByDescending { it.time }
            }.getOrDefault(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 本月汇总 */
    val summary: StateFlow<LedgerSummary> = entries.map { list ->
        val month = nowIso().take(7)
        val monthEntries = list.filter { it.time.take(7) == month }
        LedgerSummary(
            expense = monthEntries.filter { !it.income }.sumOf { it.amount },
            income = monthEntries.filter { it.income }.sumOf { it.amount },
            count = monthEntries.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LedgerSummary())

    /** 记一笔（金额 <= 0 直接忽略） */
    fun add(amount: Double, income: Boolean, category: String, note: String) {
        if (amount <= 0.0) return
        viewModelScope.launch {
            val current = entries.value
            val entry = LedgerEntry(
                id = uid("led"),
                amount = amount,
                income = income,
                category = category.ifBlank { "其他" },
                note = note.trim(),
                time = nowIso()
            )
            persist(current + entry)
        }
    }

    /** 删除一条流水 */
    fun delete(id: String) {
        viewModelScope.launch {
            persist(entries.value.filter { it.id != id })
        }
    }

    private suspend fun persist(list: List<LedgerEntry>) {
        settingsDs.setAccountingLedgerJson(
            json.encodeToString(ListSerializer(LedgerEntry.serializer()), list)
        )
    }
}

/** 支出分类快选 */
val EXPENSE_CATEGORIES = listOf("餐饮", "交通", "购物", "居住", "娱乐", "医疗", "学习", "其他")

/** 收入分类快选 */
val INCOME_CATEGORIES = listOf("工资", "奖金", "兼职", "理财", "人情", "其他")

/** Float 展示：去掉多余的 .0 */
fun fmtMoney(v: Double): String {
    val r = Math.round(v * 100) / 100.0
    return if (r == r.toLong().toDouble()) r.toLong().toString() else String.format("%.2f", r)
}
