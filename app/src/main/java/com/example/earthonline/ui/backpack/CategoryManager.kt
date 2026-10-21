package com.example.earthonline.ui.backpack
import com.example.earthonline.ui.components.AnimatedAlertDialog

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.earthonline.data.local.entity.BagCategoryEntity

/**
 * 分类筛选条：全部 / 未分类 / 各自定义分类 + 管理入口。
 *
 * [selected] 语义：null=全部，""=未分类，其它=具体分类名（与条目上的 category 字段一致）。
 */
@Composable
fun CategoryFilterRow(
    categories: List<BagCategoryEntity>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("全部") })
        FilterChip(selected = selected == "", onClick = { onSelect("") }, label = { Text("未分类") })
        categories.forEach { c ->
            FilterChip(
                selected = selected == c.name,
                onClick = { onSelect(c.name) },
                label = { Text(c.name) }
            )
        }
    }
}

/**
 * 分类管理对话框：新增 / 重命名 / 删除自定义分类。
 *
 * 删除分类不会删除条目 —— 条目会回落为「未分类」，这一步由 ViewModel 保证，
 * 这里只负责把意图传出去并做二次确认。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagerDialog(
    title: String,
    categories: List<BagCategoryEntity>,
    error: String?,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onRename: (BagCategoryEntity, String) -> Unit,
    onDelete: (BagCategoryEntity) -> Unit
) {
    var newName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<BagCategoryEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<BagCategoryEntity?>(null) }

    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
        title = { Text(title) },
        text = {
            // v1.0.4：内容可滚动 —— 顶部说明行 + 分类列表在长内容 / 大字号下不再溢出对话框
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // v1.0.4：说清两个按钮的分工——「添加」保存新分类，「完成」只是关窗。
                // 此前只有一个「+」图标按钮，与右下角「完成」并列时用户常分不清哪个才是保存。
                Text(
                    "改动即时生效：输入名称后点「添加」保存；右下角「完成」仅关闭本窗口。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 新增
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("新分类名称") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (newName.isNotBlank()) {
                                onAdd(newName.trim())
                                newName = ""
                            }
                        },
                        enabled = newName.isNotBlank()
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("添加")
                    }
                }

                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium)
                }

                HorizontalDivider()

                if (categories.isEmpty()) {
                    Text(
                        "还没有自定义分类，添加一个试试",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text("已有分类（${categories.size}）", style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold)
                    // 分类数量可控（用户自建），直接 Column 即可，无需 LazyColumn
                    Column {
                        categories.forEach { c ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(c.name, modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium)
                                IconButton(onClick = { renaming = c }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "重命名",
                                        modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { deleteTarget = c }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "删除",
                                        modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    )

    renaming?.let { c ->
        var name by remember(c.id) { mutableStateOf(c.name) }
        AnimatedAlertDialog(
            onDismissRequest = { renaming = null },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        onRename(c, name.trim())
                        renaming = null
                    }
                }, enabled = name.isNotBlank()) { Text("保存") }
            },
            dismissButton = { TextButton({ renaming = null }) { Text("取消") } },
            title = { Text("重命名分类") },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("分类名称") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }

    deleteTarget?.let { c ->
        AnimatedAlertDialog(
            onDismissRequest = { deleteTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(c)
                    deleteTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton({ deleteTarget = null }) { Text("取消") } },
            title = { Text("删除分类「${c.name}」？") },
            text = { Text("该分类下的条目不会被删除，会回落为「未分类」。") }
        )
    }
}

/**
 * 新增条目时的分类选择器：从已有分类里选，也可以现场新建。
 * 返回值语义同 [CategoryFilterRow]：null=未分类。
 *
 * 注意：这里刻意**不用 ExposedDropdownMenuBox**。该组件在 AlertDialog 内会失效 ——
 * 下拉能正常画出来，但 Popup 的触摸区域按 Dialog 窗口坐标计算、与屏幕坐标差一个
 * Dialog 居中偏移，导致点选项被判成「外部点击」而只关闭、不回调 onSelect（实测踩过）。
 * 分类数量本来就少，直接用 chips 铺开，既避开这个坑也少一次点击。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoryPicker(
    categories: List<BagCategoryEntity>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onCreate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var creating by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text("分类", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("未分类") }
            )
            categories.forEach { c ->
                FilterChip(
                    selected = selected == c.name,
                    onClick = { onSelect(c.name) },
                    label = { Text(c.name) }
                )
            }
            AssistChip(
                onClick = { creating = true },
                label = { Text("新建") },
                leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(16.dp)) }
            )
        }
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        AnimatedAlertDialog(
            onDismissRequest = { creating = false },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        onCreate(name.trim())
                        onSelect(name.trim())
                        creating = false
                    }
                }, enabled = name.isNotBlank()) { Text("创建") }
            },
            dismissButton = { TextButton({ creating = false }) { Text("取消") } },
            title = { Text("新建分类") },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("分类名称") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }
}
