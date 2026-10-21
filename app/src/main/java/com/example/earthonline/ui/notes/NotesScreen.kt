package com.example.earthonline.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.components.UiDimens
import com.example.earthonline.ui.theme.GlassCard
import com.example.earthonline.ui.theme.GlassSurface
import kotlinx.coroutines.launch
import com.example.earthonline.ui.theme.AmberPrimary

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
                // v1.0.11：Header 透明化，与主页一致透出壁纸，去掉顶部悬浮覆盖感
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    IconButton(onClick = {
                        searchOpen = !searchOpen
                        if (!searchOpen) vm.setQuery("")
                    }) {
                        Icon(
                            imageVector = if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = "搜索"
                        )
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
            if (searchOpen) {
                OutlinedTextField(
                    value = query,
                    onValueChange = vm::setQuery,
                    placeholder = { Text("搜正文或标签") },
                    singleLine = true,
                    // v1.0.11：全局搜索栏统一胶囊形
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // v1.0.12：新建笔记 = 直接进完整编辑页（标题 + 工具栏 + 正文一次到位）。
            // 不再在列表页内联输入 —— 内联框没有工具栏，格式根本没法用，
            // 这正是用户反馈「加粗/斜体点不动」的一半原因。
            NewNoteEntry(onClick = { onEdit(null) })

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
                        Icon(Icons.Rounded.EditNote, contentDescription = null, tint = AmberPrimary, modifier = Modifier.size(56.dp))
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
                                markdown = m.markdown ?: m.text,
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
 * v1.0.12：新建笔记入口卡。
 * 点击直接进入完整编辑页 —— 标题输入 + 格式化工具栏 + 正文区一步到位，
 * 不再在列表页内联输入（内联框没有工具栏，格式根本用不了）。
 */
@Composable
private fun NewNoteEntry(onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(UiDimens.CardRadius)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Edit, contentDescription = "编辑", tint = AmberPrimary, modifier = Modifier.size(18.dp)) }
            Column(Modifier.weight(1f)) {
                Text(
                    "新建笔记",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "标题 · 正文 · 加粗 / 列表 / 待办 / 插图",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary) {
                Text(
                    "＋ 写",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
}

/**
 * 卡片流里的一张笔记（v1.0.12：标题 + 纯文本摘要）。
 * 关键修复：列表绝不直接渲染 Markdown 源码 —— 之前卡片上会出现 `### *小鸡小鸡*`
 * 这种原始标记。现在标题取正文第一行、摘要做 markdown -> 纯文本收敛，
 * 链接留文字、图片/HTML 标签直接剔除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteCard(
    text: String,
    markdown: String,
    tags: List<String>,
    time: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val plainLines = markdownToPlain(markdown).lines().filter { it.isNotBlank() }
    val title = plainLines.firstOrNull()?.take(60).orEmpty().ifBlank {
        text.lineSequence().firstOrNull { it.isNotBlank() }?.take(60).orEmpty()
    }.ifBlank { "无标题笔记" }
    val preview = plainLines.drop(1).joinToString(" ").take(180)

    GlassSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(UiDimens.CardRadius)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (preview.isNotBlank()) {
                Text(
                    preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tags.take(3).forEach { t ->
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
                        Icons.Rounded.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * v1.0.12：Markdown -> 纯文本（列表摘要 / 标题提取共用）。
 * 只做「看得见」的收敛：删标记、留文字，不追求完整解析 —— 全文渲染交给编辑页。
 */
internal fun markdownToPlain(md: String): String {
    var s = md
    s = s.replace(Regex("<[^>]{1,40}>"), "")
    s = s.replace(Regex("!\\[[^]]{0,80}]\\([^)]{0,200}\\)"), "")
    s = s.replace(Regex("\\[([^]]{0,120})]\\([^)]{0,200}\\)")) { it.groupValues[1] }
    s = s.replace(Regex("^\\s{0,3}#{1,6}\\s*", RegexOption.MULTILINE), "")
    s = s.replace(Regex("^\\s{0,3}[-*+]\\s+", RegexOption.MULTILINE), "")
    s = s.replace(Regex("^\\s{0,3}\\d+[.)]\\s+", RegexOption.MULTILINE), "")
    s = s.replace(Regex("^\\s{0,3}>\\s?", RegexOption.MULTILINE), "")
    s = s.replace("**", "").replace("__", "").replace("~~", "").replace("`", "")
    s = s.replace(Regex("(?<!\\*)\\*(?!\\*)"), "")
    s = s.replace(Regex("[ \\t]{2,}"), " ")
    return s.trim()
}

/** 2026-10-19T12:34 → 10-19 12:34 */
private fun formatNoteTime(iso: String): String {
    if (iso.isBlank()) return ""
    val d = iso.take(10)
    val t = iso.substringAfter('T', "").take(5)
    return if (t.isBlank()) d else "$d $t"
}
