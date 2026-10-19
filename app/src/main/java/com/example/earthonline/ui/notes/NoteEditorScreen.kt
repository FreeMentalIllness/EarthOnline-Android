package com.example.earthonline.ui.notes

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.components.SettingsIconButton
import com.example.earthonline.ui.theme.GlassSurface
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditor
import kotlinx.coroutines.launch

/** 笔记编辑器路由（v1.0.6）。id 空 = 新建，否则编辑既有笔记。 */
@Composable
fun NoteEditorRoute(id: String, moreActions: MoreMenuActions, onBack: () -> Unit) {
    val vm = hiltViewModel<NotesViewModel>()
    NoteEditorScreen(id = id, vm = vm, moreActions = moreActions, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    id: String,
    vm: NotesViewModel,
    moreActions: MoreMenuActions,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val state = rememberRichTextState()
    var type by remember { mutableStateOf("note") }
    var typeMenu by remember { mutableStateOf(false) }
    var linkDialog by remember { mutableStateOf(false) }
    var imageDialog by remember { mutableStateOf(false) }

    // 编辑既有笔记：载入 Markdown（回退纯文本）
    LaunchedEffect(id) {
        if (id.isNotBlank()) {
            val note = vm.getNote(id)
            if (note != null) {
                type = note.type
                state.setMarkdown(note.markdown ?: note.text ?: "")
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (id.isBlank()) "新建笔记" else "编辑笔记") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    TextButton(onClick = { typeMenu = true }) { Text(typeLabelOf(type)) }
                    DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                        listOf("note" to "笔记", "important" to "重要", "idea" to "灵感").forEach { (k, l) ->
                            DropdownMenuItem(text = { Text(l) }, onClick = { type = k; typeMenu = false })
                        }
                    }
                    SettingsIconButton(actions = moreActions)
                }
            )
        },
        floatingActionButton = {
            GlassSurface(
                shape = CircleShape,
                onClick = {
                    scope.launch {
                        val md = state.toMarkdown()
                        val txt = state.toText()
                        vm.saveNote(id.ifBlank { null }, type, txt, md)
                        onBack()
                    }
                }
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "保存",
                    modifier = Modifier.padding(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            // 富文本工具栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                EditorAction(Icons.Default.FormatBold, "加粗") {
                    state.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold))
                }
                EditorAction(Icons.Default.FormatItalic, "斜体") {
                    state.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic))
                }
                EditorAction(Icons.Default.Title, "标题") { state.addTextAfterSelection("# ") }
                EditorAction(Icons.Default.FormatListBulleted, "无序列表") { state.toggleUnorderedList() }
                EditorAction(Icons.Default.FormatListNumbered, "有序列表") { state.toggleOrderedList() }
                EditorAction(Icons.Default.Code, "代码") { state.toggleCodeSpan() }
                EditorAction(Icons.Default.Link, "链接") { linkDialog = true }
                EditorAction(Icons.Default.Image, "图片") { imageDialog = true }
            }
            HorizontalDivider()
            RichTextEditor(
                state = state,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            )
        }
    }

    if (linkDialog) {
        InsertDialog(
            title = "插入链接",
            label = "链接地址（https://...）",
            onDismiss = { linkDialog = false }
        ) { url ->
            val u = url.ifBlank { "https://" }
            state.addTextAfterSelection("[链接]($u)")
            linkDialog = false
        }
    }
    if (imageDialog) {
        InsertDialog(
            title = "插入图片",
            label = "图片路径或 URL",
            onDismiss = { imageDialog = false }
        ) { path ->
            state.addTextAfterSelection("![]($path)")
            imageDialog = false
        }
    }
}

@Composable
private fun EditorAction(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(icon, contentDescription = contentDescription, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InsertDialog(
    title: String,
    label: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("插入") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun typeLabelOf(type: String): String = when (type) {
    "important" -> "重要"
    "idea" -> "灵感"
    else -> "笔记"
}
