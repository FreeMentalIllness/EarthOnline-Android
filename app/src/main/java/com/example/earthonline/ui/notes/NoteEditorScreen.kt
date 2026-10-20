package com.example.earthonline.ui.notes

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.components.SettingsIconButton
import com.example.earthonline.util.rememberImagePicker
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 笔记编辑页（v1.0.11 彻底重写为 WYSIWYG 富文本）。
 *
 * 核心变化：正文改用 richeditor-compose 的 [RichTextEditor]（所见即所得）——
 * 加粗 / 斜体 / 下划线 / 列表等操作直接在渲染后的文本上生效（toggleSpanStyle），
 * 不再向输入框插入 `**` `<u>` 等 Markdown 源码；存储层仍是 Markdown
 * （保存时 toMarkdown()，载入时 setMarkdown()），跨端与旧数据完全兼容。
 * 取消「无/灵感/待办/日记/想法」默认分类，标签完全由用户自定义。
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
    // v1.0.11：WYSIWYG 富文本状态（编辑即渲染，不再暴露 Markdown 源码）
    val rich = rememberRichTextState()
    var tags by remember { mutableStateOf(listOf<String>()) }
    var tagInput by remember { mutableStateOf("") }
    var showDelete by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id.isNotBlank()) {
            val m = vm.getNote(id)
            if (m != null) {
                runCatching { rich.setMarkdown(m.text) }
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

    /* ==================== 工具栏状态与助手 ==================== */
    val context = LocalContext.current
    /** 字号档位（1f / 1.15f / 1.3f 循环），作用于编辑器正文 */
    var bodyScale by remember { mutableFloatStateOf(1f) }

    /** 把 content uri 复制进应用私有 note_media 目录，成功回调文件路径；失败 Toast */
    fun importIntoNoteMedia(uri: Uri, fallbackName: String, onDone: (String) -> Unit) {
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = context.contentResolver
                    var fileName = fallbackName
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0)?.let { fileName = it } }
                    val safe = fileName.replace(Regex("[\\\\/:*?\"<>|\\s]"), "_").ifBlank { fallbackName }
                    val dir = File(context.filesDir, "note_media").apply { mkdirs() }
                    val out = File(dir, "${System.currentTimeMillis()}_$safe")
                    resolver.openInputStream(uri)?.use { input ->
                        out.outputStream().use { input.copyTo(it) }
                    } ?: return@runCatching null
                    out.absolutePath
                }.getOrNull()
            }
            if (path == null) {
                Toast.makeText(context, "导入失败，请重试", Toast.LENGTH_SHORT).show()
            } else {
                onDone(path)
            }
        }
    }

    // 插入图片 / 插入本地文件：以富文本链接形态插入（WYSIWYG 可见可点），
    // 导出 Markdown 时为 [名称](file://路径)，与列表页附件打开逻辑完全兼容。
    val pickNoteImage = rememberImagePicker(onPicked = { uri ->
        importIntoNoteMedia(uri, "image.jpg") { path ->
            rich.addLink("🖼 图片", "file://$path")
        }
    })
    val pickNoteFile = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            importIntoNoteMedia(uri, "file") { path ->
                val display = path.substringAfterLast('_')
                rich.addLink("📎 $display", "file://$path")
            }
        }
    }

    fun save(): Boolean {
        val text = rich.toMarkdown()
        if (text.isBlank()) return false
        commitTag()
        scope.launch {
            vm.saveNote(id.ifBlank { null }, text, tags)
            onBack()
        }
        return true
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(if (id.isBlank()) "新建笔记" else "编辑笔记") },
                navigationIcon = {
                    IconButton(onClick = {
                        // 有内容就顺手存一下，避免用户以为丢了
                        if (rich.toMarkdown().isNotBlank()) save() else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 标签编辑区（完全用户自定义，无默认分类）
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

            // v1.0.11：WYSIWYG 工具栏 —— 点击直接在富文本上生效，绝不插入 Markdown 符号
            FormatToolbar(
                onTitle = { rich.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp)) },
                onBold = { rich.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) },
                onItalic = { rich.toggleSpanStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) },
                onUnderline = { rich.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline)) },
                onFontSize = {
                    bodyScale = when (bodyScale) {
                        1f -> 1.15f
                        1.15f -> 1.3f
                        else -> 1f
                    }
                },
                onTodo = { rich.toggleUnorderedList() },
                onQuote = { rich.toggleCodeSpan() },
                onOrdered = { rich.toggleOrderedList() },
                onImage = { pickNoteImage() },
                onFile = { pickNoteFile.launch("*/*") }
            )

            // WYSIWYG 编辑器：编辑即渲染，所见即所得
            RichTextEditor(
                state = rich,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = (16 * bodyScale).sp,
                    lineHeight = (28 * bodyScale).sp
                ),
                placeholder = { Text("写点什么…") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().weight(1f)
            )

            Button(
                onClick = { save() },
                enabled = rich.toMarkdown().isNotBlank(),
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
 * v1.0.11：WYSIWYG 笔记格式工具栏（对标专业笔记软件）——
 * B / I / U / A± / 待办 / H / 1. 有序 / ❝ / 🖼 / 📎，单行横向滚动恒定可见。
 * 全部操作直接作用于富文本（toggleSpanStyle / toggle 列表），不再插入 Markdown 符号。
 */
@Composable
private fun FormatToolbar(
    onTitle: () -> Unit,
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onUnderline: () -> Unit,
    onFontSize: () -> Unit,
    onTodo: () -> Unit,
    onQuote: () -> Unit,
    onOrdered: () -> Unit,
    onImage: () -> Unit,
    onFile: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FormatButton("B", onBold, bold = true)
            FormatButton("I", onItalic, italic = true)
            FormatButton("U", onUnderline, underline = true)
            FormatButton("A±", onFontSize)
            FormatButton("☑ 待办", onTodo)
            FormatButton("H", onTitle)
            FormatButton("1. 有序", onOrdered)
            FormatButton("❝", onQuote)
            FormatButton("🖼 图片", onImage)
            FormatButton("📎 文件", onFile)
        }
    }
}

@Composable
private fun FormatButton(
    text: String,
    onClick: () -> Unit,
    bold: Boolean = false,
    italic: Boolean = false,
    underline: Boolean = false
) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
        modifier = Modifier.height(32.dp)
    ) {
        Text(
            text,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            fontStyle = if (italic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
            textDecoration = if (underline) TextDecoration.Underline else TextDecoration.None,
            style = MaterialTheme.typography.labelMedium
        )
    }
}
