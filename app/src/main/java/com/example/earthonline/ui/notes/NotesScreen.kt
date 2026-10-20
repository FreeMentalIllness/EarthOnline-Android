package com.example.earthonline.ui.notes

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.components.SettingsIconButton
import kotlinx.coroutines.launch

/**
 * 笔记主页（v1.0.6 重写：极简卡片流）。
 *
 * 形态：打开就能写 —— 页面顶部常驻一张「记录卡」，正文往里打、标签随手加、点「记下」即存；
 * 下面就是按时间倒序的卡片流，点任意一张进入编辑。
 * 取消「笔记 / 重要 / 灵感」三个写死分类，改为自定义标签，顶部可按标签筛选、按关键词搜索。
 *
 * 为什么不再用悬浮玻璃按钮：玻璃容器的尺寸测量有过撑满全页、吞掉所有点击的前科，
 * 笔记页的写入入口改成页面内的常驻卡片，热区边界清晰可见，不可能再出现「点哪都在保存」。
 */
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
    val allTags by vm.allTags.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeTag by vm.tag.collectAsStateWithLifecycle(initialValue = NotesViewModel.ALL_TAGS)

    var searchOpen by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("笔记") },
                actions = {
                    IconButton(onClick = {
                        searchOpen = !searchOpen
                        if (!searchOpen) vm.setQuery("")
                    }) {
                        Icon(
                            imageVector = if (searchOpen) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "搜索"
                        )
                    }
                    SettingsIconButton(actions = moreActions)
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (searchOpen) {
                OutlinedTextField(
                    value = query,
                    onValueChange = vm::setQuery,
                    placeholder = { Text("搜正文或标签") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // 常驻记录卡：打开笔记页就能写，不用先点「+」
            QuickNoteCard(
                onSave = { body, tags ->
                    scope.launch {
                        vm.saveNote(null, body, tags)
                    }
                }
            )

            // 标签筛选：全部 + 已用过的标签（按使用次数排序）
            if (allTags.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = activeTag == NotesViewModel.ALL_TAGS,
                        onClick = { vm.setTag(NotesViewModel.ALL_TAGS) },
                        label = { Text("全部") }
                    )
                    allTags.forEach { t ->
                        FilterChip(
                            selected = activeTag == t,
                            onClick = { vm.setTag(if (activeTag == t) NotesViewModel.ALL_TAGS else t) },
                            label = { Text("#$t") }
                        )
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (notes.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("📝", style = MaterialTheme.typography.displaySmall)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (query.isBlank() && activeTag == NotesViewModel.ALL_TAGS)
                                "还没有笔记\n想到什么，在上面写下来"
                            else "没有匹配的笔记",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(notes, key = { it.id }) { m ->
                            NoteCard(
                                text = m.text,
                                tags = NotesViewModel.tagsOf(m),
                                time = formatNoteTime(m.createdAt),
                                onClick = { onEdit(m.id) },
                                onDelete = { toDelete = m.id }
                            )
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("删除这条笔记？") },
            text = { Text("删除后可在回收站保留 30 天，之后彻底清除。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { vm.deleteNote(id) }
                    toDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("取消") } }
        )
    }
}

/**
 * 快速记录卡：一段正文 + 若干标签，点「记下」即存。
 * 标签用一行输入框，回车或逗号即切分成一个标签。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickNoteCard(onSave: (String, List<String>) -> Unit) {
    var body by remember { mutableStateOf("") }
    var tagInput by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf(listOf<String>()) }
    val expanded = remember { mutableStateOf(false) }

    fun commitTag() {
        val t = tagInput.trim().trim('#')
        if (t.isNotBlank()) {
            tags = NotesViewModel.normalizeTags(tags + t)
        }
        tagInput = ""
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                placeholder = { Text("此刻在想什么？") },
                minLines = if (expanded.value) 4 else 2,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth()
            )
            if (expanded.value || tags.isNotEmpty() || tagInput.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tags.forEach { t ->
                        InputChip(
                            selected = false,
                            onClick = { tags = tags - t },
                            label = { Text("#$t") },
                            trailingIcon = {
                                Icon(Icons.Default.Close, contentDescription = "移除标签", modifier = Modifier.size(14.dp))
                            }
                        )
                    }
                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = {
                            // 逗号即切分，支持一次粘贴多个
                            if (it.contains(',') || it.contains('，')) {
                                val parts = it.split(',', '，')
                                val last = parts.last()
                                tags = NotesViewModel.normalizeTags(
                                    tags + parts.dropLast(1).map { s -> s.trim().trim('#') }
                                )
                                tagInput = last
                            } else tagInput = it
                        },
                        placeholder = { Text("标签") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { commitTag() }),
                        modifier = Modifier.weight(1f).defaultMinSize(minWidth = 90.dp)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { expanded.value = !expanded.value }) {
                    Text(if (expanded.value) "收起" else "展开")
                }
                Spacer(Modifier.width(6.dp))
                Button(
                    onClick = {
                        commitTag()
                        val finalBody = body.trim()
                        if (finalBody.isBlank()) return@Button
                        onSave(finalBody, tags)
                        body = ""
                        tagInput = ""
                        tags = emptyList()
                        expanded.value = false
                    },
                    enabled = body.isNotBlank()
                ) { Text("记下") }
            }
        }
    }
}

/** 卡片流里的一张笔记 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteCard(
    text: String,
    tags: List<String>,
    time: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tags.take(4).forEach { t ->
                        AssistChip(
                            onClick = {},
                            label = { Text("#$t", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Text(
                    time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/** 2026-10-19T12:34 → 10-19 12:34 */
private fun formatNoteTime(iso: String): String {
    if (iso.isBlank()) return ""
    val d = iso.take(10)
    val t = iso.substringAfter('T', "").take(5)
    return if (t.isBlank()) d else "$d $t"
}
