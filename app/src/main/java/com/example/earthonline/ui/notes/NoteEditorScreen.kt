package com.example.earthonline.ui.notes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.components.SettingsIconButton
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText
import kotlinx.coroutines.launch

/**
 * 笔记编辑页（v1.0.6 重写）。
 *
 * 形态：一整页就是「正文输入框 + 标签编辑区」。正文用系统原生输入框，
 * 不再依赖任何富文本编辑控件 —— 写入链路越短越不容易坏。
 * 需要看排版效果时点右上角「预览」，用只读渲染器把 Markdown 画出来（只读，不参与写入）。
 * 保存即写库并返回列表；返回键在有内容时顺手保存，避免用户以为内容丢了。
 */
@Composable
fun NoteEditorRoute(
    id: String,
    moreActions: MoreMenuActions,
    onBack: () -> Unit
) {
    val vm = hiltViewModel<NotesViewModel>()
    NoteEditorScreen(vm = vm, id = id, moreActions = moreActions, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    vm: NotesViewModel,
    id: String,
    moreActions: MoreMenuActions,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var body by remember { mutableStateOf(TextFieldValue("")) }
    var tags by remember { mutableStateOf(listOf<String>()) }
    var tagInput by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id.isNotBlank()) {
            val m = vm.getNote(id)
            if (m != null) {
                body = TextFieldValue(m.text)
                tags = NotesViewModel.tagsOf(m)
            }
        }
        loaded = true
    }

    fun commitTag() {
        val t = tagInput.trim().trim('#')
        if (t.isNotBlank()) tags = NotesViewModel.normalizeTags(tags + t)
        tagInput = ""
    }

    /** 在光标选区两侧包裹 [prefix]/[suffix]（无选区则插入占位文本并选中），用于加粗等行内语法 */
    fun surround(prefix: String, suffix: String = prefix, placeholder: String = "文本") {
        val tv = body
        val start = tv.selection.start.coerceAtMost(tv.text.length)
        val end = tv.selection.end.coerceAtMost(tv.text.length)
        val sel = tv.text.substring(start, end)
        val inner = if (sel.isEmpty()) placeholder else sel
        val newText = tv.text.substring(0, start) + prefix + inner + suffix + tv.text.substring(end)
        body = TextFieldValue(
            text = newText,
            selection = TextRange(start + prefix.length, start + prefix.length + inner.length)
        )
    }

    /** 在当前行行首插入 [linePrefix]（如列表 / 待办项），光标同步右移 */
    fun linePrefix(linePrefix: String) {
        val tv = body
        val caret = tv.selection.start.coerceAtMost(tv.text.length)
        val lineStart = tv.text.lastIndexOf('\n', caret - 1) + 1
        val newText = tv.text.substring(0, lineStart) + linePrefix + tv.text.substring(lineStart)
        val shift = linePrefix.length
        body = TextFieldValue(
            text = newText,
            selection = TextRange(tv.selection.start + shift, tv.selection.end + shift)
        )
    }

    fun save(): Boolean {
        val text = body.text
        if (text.isBlank()) return false
        commitTag()
        scope.launch {
            vm.saveNote(id.ifBlank { null }, text, tags)
            onBack()
        }
        return true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (id.isBlank()) "新建笔记" else "编辑笔记") },
                navigationIcon = {
                    IconButton(onClick = {
                        // 有内容就顺手存一下，避免用户以为丢了
                        if (body.text.isNotBlank()) save() else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { preview = !preview }) {
                        Text(if (preview) "编辑" else "预览")
                    }
                    if (id.isNotBlank()) {
                        IconButton(onClick = { showDelete = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    SettingsIconButton(actions = moreActions)
                }
            )
        }
    ) { padding ->
        if (!loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 标签编辑区
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
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "移除标签",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = {
                        if (it.contains(',') || it.contains('，')) {
                            val parts = it.split(',', '，')
                            tags = NotesViewModel.normalizeTags(
                                tags + parts.dropLast(1).map { s -> s.trim().trim('#') }
                            )
                            tagInput = parts.last()
                        } else tagInput = it
                    },
                    placeholder = { Text("加标签") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commitTag() }),
                    modifier = Modifier.weight(1f).defaultMinSize(minWidth = 100.dp)
                )
            }

            // v1.0.7：格式化工具栏（加粗 / 列表 / 待办项）—— 直接插入 Markdown 语法，
            // 与预览渲染器（RichText）天然兼容，所见即所得。
            if (!preview) {
                FormatToolbar(
                    onBold = { surround("**", "**", "加粗文本") },
                    onList = { linePrefix("- ") },
                    onTodo = { linePrefix("- [ ] ") }
                )
            }

            if (preview) {
                MarkdownPreview(
                    markdown = body.text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                )
            } else {
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    placeholder = { Text("写点什么…") },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Button(
                onClick = { save() },
                enabled = body.text.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("保存") }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除这条笔记？") },
            text = { Text("删除后可在回收站保留 30 天。") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    scope.launch {
                        vm.deleteNote(id)
                        onBack()
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("取消") } }
        )
    }
}

/**
 * Markdown 只读预览。
 * 用只读渲染器而非可编辑控件：只读路径不参与输入与状态回写，天然不会把编辑弄坏。
 */
@Composable
private fun MarkdownPreview(markdown: String, modifier: Modifier = Modifier) {
    val state = rememberRichTextState()
    LaunchedEffect(markdown) {
        runCatching { state.setMarkdown(markdown) }
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Box(Modifier.padding(14.dp)) {
            RichText(state = state)
        }
    }
}

/**
 * v1.0.7：笔记格式化工具栏。三个按钮对应主流笔记最常用语法，
 * 点击即在正文光标处插入 Markdown（加粗 / 无序列表 / 待办项）。
 * 行内语法走 [FormatToolbar] 的回调由编辑器自行包裹选区。
 */
@Composable
private fun FormatToolbar(
    onBold: () -> Unit,
    onList: () -> Unit,
    onTodo: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OutlinedButton(
            onClick = onBold,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
            modifier = Modifier.height(34.dp)
        ) {
            Text("B", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        }
        OutlinedButton(
            onClick = onList,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
            modifier = Modifier.height(34.dp)
        ) {
            Text("• 列表", style = MaterialTheme.typography.labelMedium)
        }
        OutlinedButton(
            onClick = onTodo,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
            modifier = Modifier.height(34.dp)
        ) {
            Text("☑ 待办", style = MaterialTheme.typography.labelMedium)
        }
    }
}
