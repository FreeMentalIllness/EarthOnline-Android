package com.example.earthonline.ui.notes

import com.example.earthonline.ui.components.AnimatedAlertDialog

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.earthonline.ui.components.GlassTextField
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.components.UiDimens
import com.example.earthonline.ui.theme.AmberPrimary
import com.example.earthonline.ui.theme.GlassCard
import com.example.earthonline.ui.theme.GlassSurface
import com.example.earthonline.util.rememberImagePicker
import com.example.earthonline.util.uid
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * v1.0.6.6（死命令 #4）：笔记待办改为**标准 Compose Checkbox** 的独立清单，
 * 彻底废弃「- [ ] 」文字符号替换。勾选时文字加删除线、取消勾选恢复原状。
 * 存储仍序列化为 Markdown 待办行（跨端兼容），载入时解析回本结构。
 */
private data class TodoItem(
    val id: String,
    val text: String,
    val done: Boolean
)

/**
 * 笔记编辑页（v1.0.6.5 终版：richeditor-compose rc10 真实富文本）。
 *
 * 本轮按死命令彻底废弃上一版「Markdown 源码 + 透明标记」的手搓方案：
 *  - 正文唯一真相源是 [RichTextState]（真实富文本对象树），选中文字点 B 后
 *    文字立刻肉眼可见地变粗，取消选中后文本流连贯 —— 绝无隐藏符号占位或空白；
 *  - 存储层依旧 Markdown：载入 [RichTextState.setMarkdown]，保存 [RichTextState.toMarkdown]，
 *    跨端兼容与双写兜底完全不变；
 *  - 撤销 / 重做：rc10 库内未内建 undo/redo，这里维护 [RichTextState.copy] 快照栈
 *    （输入停顿 400ms 记一次，格式化动作即时落栈），出栈直接换状态实例；
 *  - 待办：行首插入 / 切换「- [ ] 」「- [x]」纯文本标记，原文字绝不清空；
 *  - 列表 / 标题样式走库内 toggleUnorderedList / toggleOrderedList 与 SpanStyle 切换。
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
    val context = LocalContext.current

    // 正文：真实富文本状态（richeditor rc10）
    var editorState by remember { mutableStateOf(RichTextState()) }
    var title by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf(listOf<String>()) }
    var tagInput by remember { mutableStateOf("") }
    var showTagRow by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    // v1.0.6.6（死命令 #4）：笔记待办清单（标准 Checkbox，非文字符号）
    var todos by remember { mutableStateOf(listOf<TodoItem>()) }
    var bodyScale by remember { mutableFloatStateOf(1f) }
    val editorFocus = remember { FocusRequester() }
    var historyJob by remember { mutableStateOf<Job?>(null) }

    /* ---------- 撤销 / 重做：RichTextState 快照栈（rc10 无内建 undo/redo） ---------- */
    val history = remember { SnapshotStateList<RichTextState>() }
    var histIndex by remember { mutableIntStateOf(0) }

    /** 入栈：纯文本与栈顶相同则跳过（避免 undo/redo 后防抖重复入栈污染 redo 分支） */
    fun pushHistory(s: RichTextState) {
        if (history.isNotEmpty() && history[histIndex].toText() == s.toText()) return
        while (history.size > histIndex + 1) history.removeAt(history.lastIndex)
        history.add(s.copy())
        if (history.size > 200) history.removeAt(0)
        histIndex = history.lastIndex
    }

    fun undo() {
        if (histIndex > 0) {
            histIndex--
            editorState = history[histIndex].copy()
        }
    }

    fun redo() {
        if (histIndex < history.lastIndex) {
            histIndex++
            editorState = history[histIndex].copy()
        }
    }

    /* ---------- 行内样式：真实富文本切换（B / I / U / 标题） ---------- */
    fun toggleStyle(style: SpanStyle) {
        editorState.toggleSpanStyle(style)
        pushHistory(editorState)
        runCatching { editorFocus.requestFocus() }
    }

    /**
     * 加粗样式 = 字重（有 Bold 字重的字体直接真加粗）+「伪粗体（Fake Bold）」合成加粗。
     *
     * 两次踩坑的收敛结论（务必保持）：
     *  ① 只写 fontWeight=FontWeight.Bold 时，无 Bold 字重的国产中文字体族渲染零变化
     *     （Compose 不做合成加粗）→ 用户反馈「看不出加粗」；
     *  ② 曾经用 drawStyle=Stroke 做假粗体：Compose 会切换到**描边模式**绘制字形，
     *     只画轮廓不填充，中文笔画本就仅约 1px，结果整段字变成空心的浅色描边 ——
     *     视觉上就是「加粗后字体变白看不见」（本次修复的根因）。
     *
     * 现在用 SpanStyle.shadow 做伪粗体：同色（[LocalContentColor]，随主题明/暗自动切换）
     * 的投影向对角线偏移 ~0.9px 并带极小的 0.6 模糊半径，等效于把字形重描一遍加厚，
     * 既能被任何字体族（含缺 Bold 字重的中文字体）肉眼识别为加粗，又完全不碰原本的文字颜色。
     * 偏移 + 微模糊双管齐下，比纯 0.6px 横向位移更扎实，不会因设备 DPI 不同而「看不出」。
     */
    val BoldStyle = SpanStyle(
        fontWeight = FontWeight.Bold,
        shadow = Shadow(
            color = LocalContentColor.current,
            offset = Offset(x = 0.9f, y = 0.9f),
            blurRadius = 0.6f
        )
    )

    // 加粗激活态：以「是否带假粗体投影」为准 —— 避免与同为 Bold 字重的标题样式混淆
    val boldActive = editorState.currentSpanStyle.shadow != null
    val italicActive = editorState.currentSpanStyle.fontStyle == FontStyle.Italic
    val underlineActive = editorState.currentSpanStyle.textDecoration == TextDecoration.Underline
    val headingActive = editorState.currentSpanStyle.fontSize == 20.sp

    /* ---------- 待办：独立 Checkbox 清单（死命令 #4，废弃文字符号） ---------- */
    fun addTodo(text: String = "") {
        todos = todos + TodoItem(uid("td"), text, false)
    }
    fun toggleTodoDone(index: Int) {
        todos = todos.mapIndexed { i, it -> if (i == index) it.copy(done = !it.done) else it }
    }
    fun editTodoText(index: Int, text: String) {
        todos = todos.mapIndexed { i, it -> if (i == index) it.copy(text = text) else it }
    }
    fun removeTodo(index: Int) {
        todos = todos.filterIndexed { i, _ -> i != index }
    }

    /** 从 Markdown 正文解析出待办清单，返回「剔除待办行后的正文」与待办列表（兼容旧版已存的 - [ ] 行）。 */
    fun extractTodos(md: String): Pair<String, List<TodoItem>> {
        val kept = mutableListOf<String>()
        val items = mutableListOf<TodoItem>()
        val re = Regex("^\\s*-\\s*\\[([ xX])\\]\\s*(.*)$")
        md.lines().forEach { line ->
            val m = re.find(line)
            if (m != null) {
                items += TodoItem(
                    uid("td"),
                    m.groupValues[2],
                    m.groupValues[1].equals("x", ignoreCase = true)
                )
            } else kept += line
        }
        return kept.joinToString("\n") to items
    }

    fun commitTag() {
        val t = tagInput.trim().trim('#')
        if (t.isNotBlank()) tags = NotesViewModel.normalizeTags(tags + t)
        tagInput = ""
    }

    /* ---------- 图片 / 附件：复制到私有目录，正文中以 Markdown 链接文本呈现 ---------- */
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

    val pickImage = rememberImagePicker(onPicked = { uri ->
        importIntoNoteMedia(uri, "image.jpg") { path ->
            editorState.addTextAfterSelection("![图片](file://$path)")
            pushHistory(editorState)
        }
    })
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            importIntoNoteMedia(uri, "file") { path ->
                editorState.addTextAfterSelection("[${path.substringAfterLast('_')}]($path)")
                pushHistory(editorState)
            }
        }
    }

    LaunchedEffect(id) {
        if (id.isNotBlank()) {
            vm.getNote(id)?.let { m ->
                val md = m.markdown?.takeIf { it.isNotBlank() } ?: m.text
                val (t, rest) = splitTitle(md)
                title = t
                val (body, extracted) = extractTodos(rest)
                todos = extracted
                editorState.setMarkdown(body)
                tags = NotesViewModel.tagsOf(m)
            }
        }
        history.clear()
        history.add(editorState.copy())
        histIndex = 0
        loaded = true
    }

    fun save() {
        val bodyMarkdown = editorState.toMarkdown().trim()
        val plain = editorState.toText().trim()
        val t = title.trim()
        if (t.isBlank() && plain.isBlank() && todos.none { it.text.isNotBlank() }) {
            onBack()
            return
        }
        commitTag()
        // v1.0.6.6（死命令 #4）：待办清单序列化为 Markdown 待办行，跨端兼容，不与正文混排。
        val effectiveTodos = todos.filter { it.text.isNotBlank() }
        val todoBlock = if (effectiveTodos.isEmpty()) "" else "\n\n" + effectiveTodos.joinToString("\n") {
            "- [${if (it.done) "x" else " "}] ${it.text}"
        }
        val markdown = (if (t.isBlank()) bodyMarkdown else "# $t\n\n$bodyMarkdown") + todoBlock
        val plainBody = if (t.isBlank()) plain
        else listOf(t, plain).filter { it.isNotBlank() }.joinToString("\n\n")
        scope.launch {
            vm.saveNote(id.ifBlank { null }, plainBody.ifBlank { t }, tags, markdown)
            onBack()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    title = { Text(if (id.isBlank()) "新建笔记" else "编辑笔记") },
                    navigationIcon = {
                        IconButton(onClick = { save() }) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "返回并保存")
                        }
                    },
                    actions = {
                        TextButton(onClick = { save() }, enabled = loaded) { Text("保存") }
                        if (id.isNotBlank()) {
                            IconButton(onClick = { showDelete = true }) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                )
                EditorFormatBar(
                    modifier = Modifier,
                    canUndo = histIndex > 0,
                    canRedo = histIndex >= 0 && histIndex < history.lastIndex,
                    boldActive = boldActive,
                    italicActive = italicActive,
                    underlineActive = underlineActive,
                    headingActive = headingActive,
                    bulletActive = editorState.isUnorderedList,
                    orderedActive = editorState.isOrderedList,
                    onUndo = { undo() },
                    onRedo = { redo() },
                    onBold = { toggleStyle(BoldStyle) },
                    onItalic = { toggleStyle(SpanStyle(fontStyle = FontStyle.Italic)) },
                    onUnderline = { toggleStyle(SpanStyle(textDecoration = TextDecoration.Underline)) },
                    onHeading = { toggleStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp)) },
                    onTodo = { addTodo() },
                    onBullet = { editorState.toggleUnorderedList(); pushHistory(editorState) },
                    onOrdered = { editorState.toggleOrderedList(); pushHistory(editorState) },
                    onImage = { pickImage() },
                    onFile = { pickFile.launch("*/*") },
                    onFontSize = {
                        bodyScale = when (bodyScale) {
                            1f -> 1.15f
                            1.15f -> 1.3f
                            else -> 1f
                        }
                    }
                )
            }
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
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassTextField(
                value = title,
                onValueChange = { title = it.replace("\n", "") },
                placeholder = "标题",
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tags.take(4).forEach { t ->
                    InputChip(
                        selected = false,
                        onClick = { tags = tags - t },
                        label = { Text("#$t", style = MaterialTheme.typography.labelSmall) },
                        trailingIcon = {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "移除标签",
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    )
                }
                TextButton(onClick = { showTagRow = !showTagRow }) {
                    Text(
                        if (tags.isEmpty() && !showTagRow) "＋ 标签" else "标签",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            if (showTagRow) {
                GlassTextField(
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
                    placeholder = "输入标签，逗号或回车确认",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commitTag() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // v1.0.6.6（死命令 #4）：标准 Checkbox 待办清单（独立于正文富文本，勾选加删除线）
            TodoSection(
                todos = todos,
                onToggle = { toggleTodoDone(it) },
                onTextChange = { i, s -> editTodoText(i, s) },
                onDelete = { removeTodo(it) },
                onAdd = { addTodo(it) }
            )

            GlassCard(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(UiDimens.CardRadius)
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            RoundedCornerShape(UiDimens.CardRadius)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    BasicRichTextEditor(
                        state = editorState,
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(editorFocus),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = (16 * bodyScale).sp,
                            lineHeight = (28 * bodyScale).sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { inner ->
                            if (editorState.annotatedString.text.isEmpty()) {
                                Text(
                                    "写点什么…支持加粗、列表、待办、插图",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            inner()
                        }
                    )
                }
            }
        }
    }

    // 输入停顿 400ms 记一次快照（程序化 undo/redo 与格式化动作即时落栈）
    LaunchedEffect(Unit) {
        snapshotFlow { editorState.toText() }.collect {
            if (loaded) {
                historyJob?.cancel()
                historyJob = scope.launch {
                    delay(400)
                    pushHistory(editorState)
                }
            }
        }
    }

    if (showDelete) {
        AnimatedAlertDialog(
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

/** 把首行 `# 标题` 拆出来；没有标题行时 title 为空、正文原样。 */
private fun splitTitle(md: String): Pair<String, String> {
    val firstLine = md.lineSequence().firstOrNull().orEmpty()
    if (!firstLine.trimStart().startsWith("#")) return "" to md
    val t = firstLine.trim().trimStart('#').trim()
    val rest = md.substring(firstLine.length).trimStart('\n')
    return t to rest
}

/**
 * v1.0.6.6（死命令 #4）：标准 Compose Checkbox 待办清单。
 * - 行首标准 Checkbox，勾选后文字加删除线、取消勾选恢复原状；
 * - 与正文富文本解耦，数据经 Markdown 待办行序列化（跨端兼容）；
 * - 高度上限 220dp、超出内部滚动，正文编辑区仍占据主要空间。
 */
@Composable
private fun TodoSection(
    todos: List<TodoItem>,
    onToggle: (Int) -> Unit,
    onTextChange: (Int, String) -> Unit,
    onDelete: (Int) -> Unit,
    onAdd: (String) -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
        shape = RoundedCornerShape(UiDimens.CardRadius)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.CheckBox,
                    contentDescription = null,
                    tint = AmberPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "待办清单",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(6.dp))
            if (todos.isEmpty()) {
                Text(
                    "还没有待办。点下方「添加」，或点上方工具栏 ☑ 快速新建一条。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            todos.forEachIndexed { index, item ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = item.done,
                        onCheckedChange = { onToggle(index) },
                        modifier = Modifier.size(22.dp)
                    )
                    TextField(
                        value = item.text,
                        onValueChange = { onTextChange(index, it) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("待办事项") },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            textDecoration = if (item.done) TextDecoration.LineThrough else null
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent
                        )
                    )
                    IconButton(onClick = { onDelete(index) }, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "删除待办",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            var newText by remember { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = newText,
                    onValueChange = { newText = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("添加一项待办…") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent
                    )
                )
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = {
                    if (newText.isNotBlank()) {
                        onAdd(newText.trim())
                        newText = ""
                    }
                }) { Text("添加") }
            }
        }
    }
}

/**
 * 编辑工具栏（对标主流 Markdown 编辑器）：撤销 / 重做 | B I U H | ☑ 待办 • 列表 1. 有序 | 图片 附件 | A±。
 * 单行横向滚动，恒定可见；按钮直接作用于 [RichTextState]（真实富文本，无源码符号）。
 */
@Composable
private fun EditorFormatBar(
    modifier: Modifier = Modifier,
    canUndo: Boolean,
    canRedo: Boolean,
    boldActive: Boolean,
    italicActive: Boolean,
    underlineActive: Boolean,
    headingActive: Boolean,
    bulletActive: Boolean,
    orderedActive: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onUnderline: () -> Unit,
    onHeading: () -> Unit,
    onTodo: () -> Unit,
    onBullet: () -> Unit,
    onOrdered: () -> Unit,
    onImage: () -> Unit,
    onFile: () -> Unit,
    onFontSize: () -> Unit
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
        showBorder = true
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolIcon(Icons.Rounded.Undo, "撤销", canUndo, onClick = onUndo)
            ToolIcon(Icons.Rounded.Redo, "重做", canRedo, onClick = onRedo)
            ToolDivider()
            ToolIcon(Icons.Rounded.FormatBold, "加粗", true, boldActive, onBold)
            ToolIcon(Icons.Rounded.FormatItalic, "斜体", true, italicActive, onItalic)
            ToolIcon(Icons.Rounded.FormatUnderlined, "下划线", true, underlineActive, onUnderline)
            ToolIcon(Icons.Rounded.Title, "标题", true, headingActive, onHeading)
            ToolDivider()
            ToolIcon(Icons.Rounded.CheckBox, "待办", true, onClick = onTodo)
            ToolIcon(Icons.Rounded.FormatListBulleted, "无序列表", true, bulletActive, onBullet)
            ToolIcon(Icons.Rounded.FormatListNumbered, "有序列表", true, orderedActive, onOrdered)
            ToolDivider()
            ToolIcon(Icons.Rounded.Image, "插入图片", true, onClick = onImage)
            ToolIcon(Icons.Rounded.AttachFile, "插入附件", true, onClick = onFile)
            ToolDivider()
            ToolText("A±", onFontSize)
        }
    }
}

@Composable
private fun ToolIcon(
    icon: ImageVector,
    desc: String,
    enabled: Boolean,
    active: Boolean = false,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(38.dp)) {
        Icon(
            icon,
            contentDescription = desc,
            modifier = Modifier.size(20.dp),
            tint = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                active -> AmberPrimary
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun ToolText(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.height(38.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ToolDivider() {
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .width(1.dp)
            .height(20.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    )
}
