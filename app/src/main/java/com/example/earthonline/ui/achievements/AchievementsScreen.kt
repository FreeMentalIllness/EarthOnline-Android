package com.example.earthonline.ui.achievements

import com.example.earthonline.ui.theme.GlassCard
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Egg
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.earthonline.ui.components.AnimatedAlertDialog
import com.example.earthonline.ui.components.EmptyHint
import com.example.earthonline.ui.components.MeterBar
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.components.SectionHeader
import com.example.earthonline.ui.components.UiDimens
import com.example.earthonline.ui.theme.AmberPrimary

/** 成就页（对应 HTML achievements 页）：分类筛选 + 分组展示 + 进度 */
@Composable
fun AchievementsRoute(moreActions: MoreMenuActions, vm: AchievementViewModel = hiltViewModel()) {
    AchievementsScreen(vm, moreActions)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(vm: AchievementViewModel, moreActions: MoreMenuActions) {
    val list by vm.uiState.collectAsStateWithLifecycle(initialValue = emptyList())
    val unlocked by vm.unlockedCount.collectAsStateWithLifecycle(initialValue = 0)
    val summary by vm.categorySummary.collectAsStateWithLifecycle(initialValue = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    var editTitle by remember { mutableStateOf("") }
    var editDesc by remember { mutableStateOf("") }
    var editCategory by remember { mutableStateOf<String?>(null) }
    // null = 全部
    var filter by remember { mutableStateOf<String?>(null) }

    val visible = remember(list, filter) {
        if (filter == null) list else list.filter { it.categoryId == filter }
    }
    // 按 ACH_CATEGORIES 的固定顺序分组
    val groups = remember(visible) {
        ACH_CATEGORIES.map { c -> c to visible.filter { it.categoryId == c.id } }
            .filter { it.second.isNotEmpty() }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("成就") },
                actions = { }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAdd = true },
                containerColor = AmberPrimary
            ) { Icon(Icons.Rounded.Add, "新增手动成就") }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // 总览
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = UiDimens.ListPad, vertical = 8.dp),
                shape = RoundedCornerShape(UiDimens.CardRadius)
            ) {
                Column(
                    Modifier.padding(UiDimens.CardPad),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "已解锁 $unlocked / ${list.size}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    MeterBar(
                        current = unlocked,
                        goal = list.size,
                        text = if (list.isEmpty()) "0%" else "${unlocked * 100 / list.size}%"
                    )
                    Text(
                        "共 ${list.size} 项成就 · ${summary.size} 个分类",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // v1.2.0：彩蛋提示（否则用户看到一排「？？？」会以为出了 bug）
                    Text(
                        "彩蛋为隐藏成就，解锁前不显示标题与条件 —— 触发一次就自己现身",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 分类筛选
            if (summary.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = UiDimens.ListPad),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        FilterChip(
                            selected = filter == null,
                            onClick = { filter = null },
                            label = { Text("全部 ${list.size}", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    items(summary, key = { it.id }) { s ->
                        FilterChip(
                            selected = filter == s.id,
                            onClick = { filter = if (filter == s.id) null else s.id },
                            leadingIcon = {
                                Icon(s.icon, contentDescription = null, modifier = Modifier.size(14.dp))
                            },
                            label = {
                                Text(
                                    "${s.label} ${s.unlocked}/${s.total}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (visible.isEmpty()) {
                EmptyHint("还没有成就，点右下角 + 添加一条自定义成就", icon = Icons.Rounded.EmojiEvents)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = UiDimens.ListPad),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    groups.forEach { (cat, sub) ->
                        item(key = "hdr_${cat.id}") {
                            SectionHeader(
                                text = cat.label,
                                icon = cat.icon,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(
                                    "${sub.count { it.entity.unlocked }}/${sub.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        items(
                            sub,
                            key = { it.entity.id },
                            contentType = { "achCard" }
                        ) { ui ->
                            Box(Modifier.animateItem()) {
                                AchievementCard(
                                    ui = ui,
                                    onToggle = { vm.toggleManual(ui.entity) },
                                    onDelete = { vm.deleteManual(ui.entity) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AnimatedAlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("新增自定义成就") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("标题") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editDesc,
                        onValueChange = { editDesc = it },
                        label = { Text("描述（可选）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("分类", style = MaterialTheme.typography.labelMedium)
                    // 用平铺 chip 而不是下拉：AlertDialog 内的 Popup 类组件触摸坐标会错位
                    ACH_CATEGORIES.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { c ->
                                FilterChip(
                                    selected = editCategory == c.id,
                                    onClick = { editCategory = if (editCategory == c.id) null else c.id },
                                    leadingIcon = {
                                        Icon(c.icon, contentDescription = null, modifier = Modifier.size(14.dp))
                                    },
                                    label = {
                                        Text(
                                            c.label,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.addManual(editTitle, editDesc, editCategory)
                        editTitle = ""
                        editDesc = ""
                        editCategory = null
                        showAdd = false
                    },
                    enabled = editTitle.isNotBlank()
                ) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun AchievementCard(
    ui: AchievementViewModel.AchievementUi,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val a = ui.entity
    val locked = !a.unlocked
    val isAuto = ui.isAuto
    // v1.2.0：彩蛋（egg）未解锁时不剧透标题 / 描述 / 进度
    val masked = locked && ui.categoryId == ACH_CAT_EGG
    val showMeter = isAuto && locked && ui.goal > 1 && !masked

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!isAuto) Modifier.clickable { onToggle() } else Modifier),
        shape = RoundedCornerShape(UiDimens.ItemRadius)
    ) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                when {
                    masked -> Icons.Rounded.Egg
                    locked -> Icons.Rounded.Lock
                    else -> Icons.Rounded.EmojiEvents
                },
                contentDescription = null,
                tint = if (locked) MaterialTheme.colorScheme.onSurfaceVariant else AmberPrimary,
                modifier = Modifier.size(22.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (masked) ACH_EGG_MASK else a.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (locked) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (masked) ACH_EGG_MASK_DESC else a.desc.ifBlank { "达成后自动解锁" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = AmberPrimary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            if (isAuto) "自动" else "自定义",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        achCategoryLabel(ui.categoryId),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (showMeter) {
                    MeterBar(current = ui.current, goal = ui.goal)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (locked) {
                    Icon(
                        Icons.Rounded.Lock,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = AmberPrimary, modifier = Modifier.size(16.dp))
                }
                if (!isAuto) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Rounded.Delete,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
