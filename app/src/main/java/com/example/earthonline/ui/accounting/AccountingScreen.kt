package com.example.earthonline.ui.accounting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.earthonline.ui.components.AnimatedAlertDialog
import com.example.earthonline.ui.components.EmptyHint
import com.example.earthonline.ui.components.GlassTextField
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.components.UiDimens
import com.example.earthonline.ui.theme.AmberPrimary
import com.example.earthonline.ui.theme.AppCard
import com.example.earthonline.ui.theme.OnAmber

/**
 * 记账页（v1.0.6.9 恢复「老版记账页面」形态）。
 *
 * 原来「记账」在原生端只是一个外部 App 下载引导框；本页把账本搬回应用内：
 *  - 顶部：本月汇总（支出 / 收入 / 结余，收入红、支出绿，对齐国内账本体感）；
 *  - 中部：快捷录入卡（收入/支出切换 + 金额数字键盘 + 分类快选 + 备注）；
 *  - 下部：流水列表（按日分组，可删除）。
 *
 * 速览「记账」进入时 [autoAddInput] = true：金额框自动聚焦并弹出数字键盘，
 * 即「先跳页面、再弹操作面板」的交互要求。
 * 存储走 DataStore JSON（见 [AccountingViewModel]），零 Room 迁移、不动跨端同步契约。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountingRoute(
    moreActions: MoreMenuActions,
    autoAddInput: Boolean = false,
    onBack: (() -> Unit)? = null,
    vm: AccountingViewModel = hiltViewModel()
) {
    AccountingScreen(vm = vm, autoAddInput = autoAddInput, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AccountingScreen(
    vm: AccountingViewModel,
    autoAddInput: Boolean = false,
    onBack: (() -> Unit)? = null
) {
    val entries by vm.entries.collectAsStateWithLifecycle(initialValue = emptyList())
    val summary by vm.summary.collectAsStateWithLifecycle(initialValue = LedgerSummary())

    var income by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(EXPENSE_CATEGORIES.first()) }
    var toDelete by remember { mutableStateOf<LedgerEntry?>(null) }

    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    // 速览入口进入：自动聚焦金额框 + 弹出数字键盘
    LaunchedEffect(autoAddInput) {
        if (autoAddInput) {
            runCatching { focusRequester.requestFocus() }
            keyboardController?.show()
        }
    }

    // 收/支切换时分类快选跟着换默认值，避免「收入」下挂着「餐饮」
    LaunchedEffect(income) {
        category = (if (income) INCOME_CATEGORIES else EXPENSE_CATEGORIES).first()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("记账") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = UiDimens.ListPad, end = UiDimens.ListPad,
                    top = 4.dp, bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // —— 本月汇总 ——
                item(key = "summary") {
                    AppCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.BarChart,
                                contentDescription = null,
                                tint = AmberPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.size(10.dp))
                            Text(
                                "本月汇总",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            SummaryCell("支出", summary.expense, MoneyGreen)
                            Spacer(Modifier.size(14.dp))
                            SummaryCell("收入", summary.income, MoneyRed)
                            Spacer(Modifier.size(14.dp))
                            SummaryCell("结余", summary.balance, MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                // —— 快捷录入卡 ——
                item(key = "input") {
                    AppCard(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "记一笔",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            // 收入 / 支出切换（未选中加深描边，保证可见性）
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = !income,
                                    onClick = { income = false },
                                    label = { Text("支出") }
                                )
                                FilterChip(
                                    selected = income,
                                    onClick = { income = true },
                                    label = { Text("收入") }
                                )
                            }
                            GlassTextField(
                                value = amount,
                                onValueChange = { v -> amount = v.filter { it.isDigit() || it == '.' }.take(12) },
                                label = "金额（元）",
                                placeholder = "0.00",
                                singleLine = true,
                                leadingIcon = {
                                    Text(
                                        "¥",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = AmberPrimary
                                    )
                                },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal,
                                    imeAction = ImeAction.Done
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                            )
                            // 分类快选：FlowRow 自动换行，绝不挤压
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                (if (income) INCOME_CATEGORIES else EXPENSE_CATEGORIES).forEach { c ->
                                    FilterChip(
                                        selected = category == c,
                                        onClick = { category = c },
                                        label = { Text(c, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                            GlassTextField(
                                value = note,
                                onValueChange = { note = it.take(40) },
                                label = "备注（可选）",
                                placeholder = "例如：和本周的蔬菜采购",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    val v = amount.toDoubleOrNull()
                                    if (v != null && v > 0.0) {
                                        vm.add(v, income, category, note)
                                        amount = ""
                                        note = ""
                                    }
                                },
                                enabled = amount.toDoubleOrNull()?.let { it > 0.0 } == true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AmberPrimary,
                                    contentColor = OnAmber
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("记一笔", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // —— 流水列表（按日分组） ——
                if (entries.isEmpty()) {
                    item(key = "empty") {
                        EmptyHint("还没有记账，先在上面记一笔吧", icon = Icons.Rounded.BarChart)
                    }
                } else {
                    val grouped = entries.groupBy { it.time.take(10) }
                    grouped.forEach { (day, dayEntries) ->
                        item(key = "day-$day") {
                            Text(
                                day,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp, start = 2.dp)
                            )
                        }
                        items(dayEntries, key = { it.id }) { e ->
                            AppCard(Modifier.fillMaxWidth()) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            e.category,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (e.note.isNotBlank()) {
                                            Text(
                                                e.note,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Text(
                                        "${if (e.income) "+" else "-"}¥${fmtMoney(e.amount)}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        // 国内账本体感：收入红、支出绿
                                        color = if (e.income) MoneyRed else MoneyGreen
                                    )
                                    IconButton(
                                        onClick = { toDelete = e },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            contentDescription = "删除",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
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

    toDelete?.let { e ->
        AnimatedAlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("删除这条记录？") },
            text = { Text("「${e.category} ¥${fmtMoney(e.amount)}」删除后不可恢复。") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    vm.delete(e.id)
                    toDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { toDelete = null }) { Text("取消") }
            }
        )
    }
}

/** 汇总单格（标签 + 数值） */
@Composable
private fun SummaryCell(label: String, value: Double, color: Color) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "¥${fmtMoney(value)}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

/** 收入 = 红（涨红），支出 = 绿（跌绿）—— 对齐国内账本体感 */
private val MoneyRed = Color(0xFFBC4749)
private val MoneyGreen = Color(0xFF588157)
