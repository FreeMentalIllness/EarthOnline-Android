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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.components.SettingsIconButton
import com.example.earthonline.util.rememberImagePicker
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

    /* ==================== v1.0.9：完整外显工具栏所需的状态与助手 ==================== */
    val context = LocalContext.current
    /** 字号档位（1f / 1.15f / 1.3f 循环），作用于编辑器正文 */
    var bodyScale by remember { mutableFloatStateOf(1f) }

    /** 在光标处直接插入 [text]，光标移到插入内容末尾 */
    fun insertAt(text: String) {
        val tv = body
        val pos = tv.selection.start.coerceAtMost(tv.text.length)
        val newText = tv.text.substring(0, pos) + text + tv.text.substring(pos)
        body = TextFieldValue(text = newText, selection = TextRange(pos + text.length))
    }

    /** 把 content uri 复制进应用私有 note_media 目录，成功回调文件名；失败 Toast */
    fun importIntoNoteMedia(uri: Uri, fallbackName: String, onDone: (String) -> Unit) {
        scope.launch {
            val name = withContext(Dispatchers.IO) {
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
            if (name == null) {
                Toast.makeText(context, "导入失败，请重试", Toast.LENGTH_SHORT).show()
            } else {
                onDone(name)
            }
        }
    }

    // 插入图片（相册）/ 插入本地文件（任意类型）两个选择器
    val pickNoteImage = rememberImagePicker(onPicked = { uri ->
        importIntoNoteMedia(uri, "image.jpg") { path ->
            insertAt("![图片](file://$path)\n")
        }
    })
    val pickNoteFile = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            importIntoNoteMedia(uri, "file") { path ->
                val display = path.substringAfterLast('_')
                insertAt("[$display](file://$path)")
            }
        }
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
        containerColor = Color.Transparent,
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

            // v1.0.9：完整外显格式工具栏（死命令）—— 恒定可见、单行横向滚动。
            // 包含：标题 / 加粗 / 斜体 / 下划线 / 字号档位 / 待办 / 引用 / 无序 / 有序 / 插入图片 / 插入文件。
            if (!preview) {
                FormatToolbar(
                    onTitle = { linePrefix("# ") },
                    onBold = { surround("**", "**", "加粗文本") },
                    onItalic = { surround("*", "*", "斜体文本") },
                    onUnderline = { surround("<u>", "</u>", "下划线文本") },
                    onFontSize = {
                        bodyScale = when (bodyScale) {
                            1f -> 1.15f
                            1.15f -> 1.3f
                            else -> 1f
                        }
                    },
                    onTodo = { linePrefix("- [ ] ") },
                    onQuote = { linePrefix("> ") },
                    onList = { linePrefix("- ") },
                    onOrdered = { linePrefix("1. ") },
                    onImage = { pickNoteImage() },
                    onFile = { pickNoteFile.launch("*/*") }
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
                    // v1.0.7：加大行距（默认 1.4 倍偏挤），光标所在行不再与邻行糊在一起
                    // v1.0.9：字号随工具栏档位缩放（1f / 1.15f / 1.3f）
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = (16 * bodyScale).sp,
                        lineHeight = (28 * bodyScale).sp
                    ),
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
 * v1.0.9：完整外显笔记格式工具栏（死命令）—— 恒定可见、单行横向滚动，绝不折叠。
 * 行内语法（加粗 *..* / 斜体 *..* / 下划线 <u>..</u>）由编辑器包裹选区；
 * 行首语法（标题 / 引用 / 列表 / 有序 / 待办）插到光标行行首；
 * 字号档位切换编辑器正文缩放；插入图片 / 文件走系统选择器并把文件复制进应用私有目录。
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
    onList: () -> Unit,
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
            FormatButton("H", onTitle)
            FormatButton("B", onBold, bold = true)
            FormatButton("I", onItalic, italic = true)
            FormatButton("U", onUnderline, underline = true)
            FormatButton("A±", onFontSize)
            FormatButton("☑ 待办", onTodo)
            FormatButton("❝", onQuote)
            FormatButton("• 列表", onList)
            FormatButton("1. 有序", onOrdered)
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
