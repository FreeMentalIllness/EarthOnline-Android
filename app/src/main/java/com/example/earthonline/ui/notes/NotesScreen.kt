package com.example.earthonline.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.components.SettingsIconButton
import com.example.earthonline.ui.theme.GlassSurface
import com.example.earthonline.util.formatDateShort
import kotlinx.coroutines.launch

/** 笔记列表页（v1.0.6，替换原「数据」底部 Tab）。 */
@Composable
fun NotesRoute(
    moreActions: MoreMenuActions,
    onEdit: (String?) -> Unit
) {
    val vm = hiltViewModel<NotesViewModel>()
    NotesScreen(vm = vm, moreActions = moreActions, onEdit = onEdit)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    vm: NotesViewModel,
    moreActions: MoreMenuActions,
    onEdit: (String?) -> Unit
) {
    val scope = rememberCoroutineScope()
    val notes by vm.notes.collectAsStateWithLifecycle(initialValue = emptyList())
    val query by vm.query.collectAsStateWithLifecycle(initialValue = "")
    val type by vm.type.collectAsStateWithLifecycle(initialValue = "all")

    var searchOpen by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<String?>(null) }

    val typeChips = listOf("all" to "全部", "note" to "笔记", "important" to "重要", "idea" to "灵感")
    val typeLabels = mapOf("note" to "笔记", "important" to "重要", "idea" to "灵感")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("笔记") },
                actions = {
                    IconButton(onClick = { searchOpen = !searchOpen }) {
                        Icon(
                            imageVector = if (searchOpen) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "搜索"
                        )
                    }
                    SettingsIconButton(actions = moreActions)
                }
            )
        }
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            Column(Modifier.fillMaxSize()) {
                if (searchOpen) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = vm::setQuery,
                        placeholder = { Text("搜索笔记内容") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    typeChips.forEach { (key, label) ->
                        FilterChip(
                            selected = type == key,
                            onClick = { vm.setType(key) },
                            label = { Text(label) }
                        )
                    }
                }
                if (notes.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (query.isBlank() && type == "all") "还没有笔记，点右下角开始写第一篇" else "没有匹配的笔记",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(notes, key = { it.id }) { m ->
                            NoteCard(
                                title = (m.markdown ?: m.text).lineSequence().firstOrNull()?.take(40)
                                    ?: "无标题笔记",
                                preview = (m.markdown ?: m.text).replace("\n", " "),
                                typeLabel = typeLabels[m.type] ?: "笔记",
                                date = m.createdAt,
                                onClick = { onEdit(m.id) },
                                onDelete = { toDelete = m.id }
                            )
                        }
                    }
                }
            }

            // 液态玻璃悬浮按钮
            GlassSurface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                shape = CircleShape,
                onClick = { onEdit(null) }
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "新建笔记",
                    modifier = Modifier.padding(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    // 删除确认
    toDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("删除笔记") },
            text = { Text("将移入回收站（30 天内可恢复）。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { vm.deleteNote(id) }
                    toDelete = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun NoteCard(
    title: String,
    preview: String,
    typeLabel: String,
    date: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(
                    preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${typeLabel} · ${formatDateShort(date)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
