package com.example.earthonline.ui.notes

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.components.UiDimens
import com.example.earthonline.ui.theme.AmberPrimary
import com.example.earthonline.ui.theme.GlassCard
import com.example.earthonline.util.rememberImagePicker
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 笔记编辑页（v1.0.12 第三版：标题 + 工具栏 + 正文一次到位）。
 *
 * 本轮解决的三个硬问题：
 *  1. 【新建即完整】标题输入框 / 格式化工具栏 / 正文区同时在场，进来就能写、就能排版；
 *  2. 【绝不显示源码】正文用 richeditor 富文本渲染（WYSIWYG）。载入时先 setMarkdown，
 *     若解析后仍残留 `**` / `<u>` / `##` 等源码标记，再走 HTML 解析兜底，
 *     最后退化为纯文本 —— 任何情况下都不会把 Markdown 源码糊在正文里；
 *  3. 【工具栏恒在】工具栏挂在 Scaffold 的 bottomBar 上并带 imePadding，
 *     键盘弹出时整体上浮贴在键盘之上，不会被遮挡。
 *
 * 存储：markdown 列（跨端兼容）+ text 纯文本列兜底；标题以 `# 标题` 形式存在
 * Markdown 首行，不新增数据库列，升级零迁移。
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
    val rich = rememberRichTextState()

    var title by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf(listOf<String>()) }
    var tagInput by remember { mutableStateOf("") }
    var showTagRow by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var bodyScale by remember { mutableFloatStateOf(1f) }
    // v1.0.14：编辑器焦点句柄 —— 点工具栏按钮会把焦点从编辑器挪走，
    // 必须立刻要回来，否则「无选中时按 B」的待应用样式不会落到后续输入上
    val editorFocus = remember { FocusRequester() }

    /* ---------- 撤销 / 重做：自建快照栈（richeditor 本版无内置 undo） ---------- */
    val history = remember { mutableStateListOf<String>() }
    var histIndex by remember { mutableIntStateOf(-1) }
    var applyingHistory by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id.isNotBlank()) {
            vm.getNote(id)?.let { m ->
                val md = m.markdown?.takeIf { it.isNotBlank() } ?: m.text
                val (t, rest) = splitTitle(md)
                title = t
                loadRich(rich, rest)
                tags = NotesViewModel.tagsOf(m)
            }
        }
        loaded = true
        history.clear()
        history.add(rich.toHtml())
        histIndex = 0
    }

    // 输入停顿 400ms 记一次快照；主动 undo/redo 期间不记录
    LaunchedEffect(Unit) {
        snapshotFlow { rich.toHtml() }.collectLatest { html ->
            if (applyingHistory) return@collectLatest
            delay(200)
            if (history.getOrNull(histIndex) != html) {
                while (history.size > histIndex + 1) history.removeAt(history.lastIndex)
                history.add(html)
                if (history.size > 80) history.removeAt(0)
                histIndex = history.lastIndex
            }
        }
    }

    fun applyHistory(target: Int) {
        if (target < 0 || target > history.lastIndex) return
        applyingHistory = true
        histIndex = target
        runCatching { rich.setHtml(history[target]) }
        applyingHistory = false
    }

    /* v1.0.14：撤销/重做修复（死命令）。
       旧实现只靠 snapshotFlow 防抖记录 —— 若快照尚未落栈（连续输入后立即点按），
       histIndex 恒为 0，撤销按钮永远「点了没反应」。
       现在点击瞬间先把当前状态压栈、再回退/前进一步，
       无论防抖是否落栈，撤销/重做都必然生效。 */
    fun snapshotCurrent() {
        val cur = runCatching { rich.toHtml() }.getOrDefault("")
        if (history.getOrNull(histIndex) != cur) {
            while (history.size > histIndex + 1) history.removeAt(history.lastIndex)
            history.add(cur)
            if (history.size > 80) history.removeAt(0)
            histIndex = history.lastIndex
        }
    }

    fun undo() {
        snapshotCurrent()
        if (histIndex > 0) applyHistory(histIndex - 1)
    }

    fun redo() {
        snapshotCurrent()
        if (histIndex < history.lastIndex) applyHistory(histIndex + 1)
    }

    /** v1.0.14：加粗/斜体/下划线/标题统一切换 —— 切换后立刻把焦点还给编辑器 */
    fun applySpanStyle(style: SpanStyle) {
        rich.toggleSpanStyle(style)
        runCatching { editorFocus.requestFocus() }
    }

    fun commitTag() {
        val t = tagInput.trim().trim('#')
        if (t.isNotBlank()) tags = NotesViewModel.normalizeTags(tags + t)
        tagInput = ""
    }

    /* ---------- 图片 / 附件：复制到私有目录，富文本里以链接态呈现 ---------- */
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
        importIntoNoteMedia(uri, "image.jpg") { path -> rich.addLink("🖼 图片", "file://$path") }
    })
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            importIntoNoteMedia(uri, "file") { path ->
                rich.addLink("📎 ${path.substringAfterLast('_')}", "file://$path")
            }
        }
    }

    /* ---------- 待办：行首 ☐ / ☑，再点一次切换勾选态 ---------- */
    fun toggleTodo() {
        val txt = rich.toText()
        val caret = rich.selection.start.coerceIn(0, txt.length)
        val nl = txt.lastIndexOf('\n', (caret - 1).coerceAtLeast(0))
        val lineStart = if (nl < 0) 0 else nl + 1
        if (lineStart < txt.length && (txt[lineStart] == '☐' || txt[lineStart] == '☑')) {
            val next = if (txt[lineStart] == '☐') "☑" else "☐"
            runCatching { rich.replaceTextRange(TextRange(lineStart, lineStart + 1), next) }
        } else {
            runCatching { rich.addTextAtIndex(lineStart, "☐ ") }
        }
    }

    fun save() {
        val plain = rich.toText().trim()
        val t = title.trim()
        if (t.isBlank() && plain.isBlank()) {
            onBack()
            return
        }
        commitTag()
        val mdBody = rich.toMarkdown().trim()
        val markdown = if (t.isBlank()) mdBody else "# $t\n\n$mdBody"
        val plainBody = if (t.isBlank()) plain
        else listOf(t, plain).filter { it.isNotBlank() }.joinToString("\n\n")
        scope.launch {
            vm.saveNote(id.ifBlank { null }, plainBody.ifBlank { t }, tags, markdown)
            onBack()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        // v1.0.14：本页 inset 清零 —— 键盘/导航栏避让全部交给 bottomBar 的 safeDrawing 处理，
        // 消除「内容 ime padding + 工具栏 ime/navigation padding」双重叠加造成的空白与间隙
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(if (id.isBlank()) "新建笔记" else "编辑笔记") },
                navigationIcon = {
                    IconButton(onClick = { save() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回并保存")
                    }
                },
                actions = {
                    TextButton(onClick = { save() }, enabled = loaded) { Text("保存") }
                    if (id.isNotBlank()) {
                        IconButton(onClick = { showDelete = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        },
        /* 工具栏挂在 bottomBar：imePadding 让它在键盘弹出时贴在键盘上沿，永不被遮挡 */
        bottomBar = {
            /* v1.0.14：currentSpanStyle 是快照态 —— 点 B 后立即重组，按钮高亮 + 正文即时变粗 */
            val curSpanStyle = rich.currentSpanStyle
            EditorFormatBar(
                modifier = Modifier,
                canUndo = histIndex > 0,
                canRedo = histIndex >= 0 && histIndex < history.lastIndex,
                boldActive = curSpanStyle.fontWeight == FontWeight.Bold,
                italicActive = curSpanStyle.fontStyle == FontStyle.Italic,
                underlineActive = curSpanStyle.textDecoration == TextDecoration.Underline,
                onUndo = { undo() },
                onRedo = { redo() },
                onBold = { applySpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) },
                onItalic = { applySpanStyle(SpanStyle(fontStyle = FontStyle.Italic)) },
                onUnderline = { applySpanStyle(SpanStyle(textDecoration = TextDecoration.Underline)) },
                onHeading = { applySpanStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp)) },
                onTodo = { toggleTodo() },
                onBullet = { rich.toggleUnorderedList() },
                onOrdered = { rich.toggleOrderedList() },
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
            /* 标题：进来第一眼就是标题框（对标参考图四的大标题） */
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.replace("\n", "") },
                placeholder = { Text("标题") },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )

            // 标签：默认收起，避免首屏被标签区挤占
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
                                Icons.Default.Close,
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
                    placeholder = { Text("输入标签，回车确认") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commitTag() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            /* 正文区：外层玻璃卡 + 内层高不透明底板 ——
               壁纸再花，正文底色也稳定，字永远读得清（参考图二的磨砂质感）。 */
            GlassCard(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(UiDimens.CardRadius)
            ) {
                RichTextEditor(
                    state = rich,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = (16 * bodyScale).sp,
                        lineHeight = (28 * bodyScale).sp
                    ),
                    placeholder = { Text("写点什么…支持加粗、列表、待办、插图") },
                    shape = RoundedCornerShape(UiDimens.CardRadius),
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(editorFocus)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            RoundedCornerShape(UiDimens.CardRadius)
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
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
 * 富文本载入的三级兜底 —— 保证「绝不在正文里显示 Markdown 源码」：
 * ① setMarkdown（正常路径，同库往返）；
 * ② 若仍残留源码标记 → 走 HTML 解析（`<u>` 之类内联标签只有 HTML 通道认）；
 * ③ 再不行 → 纯文本，至少用户看到的是干净文字。
 */
private fun loadRich(rich: RichTextState, source: String) {
    if (source.isBlank()) return
    runCatching { rich.setMarkdown(source) }
    val probe = runCatching { rich.toText() }.getOrDefault("")
    val hasResidue = probe.contains("**") ||
        probe.contains("<u>") || probe.contains("</u>") ||
        probe.contains("](") || probe.contains("##")
    if (hasResidue) {
        val htmlOk = runCatching { rich.setHtml(markdownToHtml(source)) }.isSuccess
        val after = runCatching { rich.toText() }.getOrDefault("")
        if (!htmlOk || after.contains("**") || after.contains("<u>")) {
            runCatching { rich.setText(markdownToPlain(source)) }
        }
    }
}

/**
 * 极简 Markdown → HTML（只服务兜底路径）：标题 / 粗体 / 斜体 / 链接 / 换行。
 * 列表标记降级为「• 」文字 —— 宁可少一层语义，也不让源码露出来。
 */
private fun markdownToHtml(md: String): String {
    val sb = StringBuilder()
    md.lines().forEach { raw ->
        val line = raw.trimEnd()
        when {
            line.isBlank() -> sb.append("<br>")
            line.startsWith("### ") ->
                sb.append("<h3>").append(inlineHtml(line.removePrefix("### "))).append("</h3>")
            line.startsWith("## ") ->
                sb.append("<h2>").append(inlineHtml(line.removePrefix("## "))).append("</h2>")
            line.startsWith("# ") ->
                sb.append("<h1>").append(inlineHtml(line.removePrefix("# "))).append("</h1>")
            line.startsWith("- ") || line.startsWith("* ") ->
                sb.append("<p>• ").append(inlineHtml(line.substring(2))).append("</p>")
            else -> sb.append("<p>").append(inlineHtml(line)).append("</p>")
        }
    }
    return sb.toString()
}

private fun inlineHtml(text: String): String {
    var s = text
    s = s.replace(Regex("\\*\\*(.+?)\\*\\*"), "<b>$1</b>")
    s = s.replace(Regex("(?<!\\*)\\*(?!\\*)(.+?)\\*(?!\\*)"), "<i>$1</i>")
    s = s.replace(Regex("\\[([^]]{0,120})]\\(([^)]{0,300})\\)"), "<a href=\"$2\">$1</a>")
    return s
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
 * 编辑工具栏（对标参考图四）：撤销 / 重做 | B I U H | ☑ 待办 • 列表 1. 有序 | 🖼 图片 📎 附件 | A±
 * 单行横向滚动，恒定可见；全部按钮直接作用于富文本，不插入任何 Markdown 符号。
 */
@Composable
private fun EditorFormatBar(
    modifier: Modifier = Modifier,
    canUndo: Boolean,
    canRedo: Boolean,
    boldActive: Boolean,
    italicActive: Boolean,
    underlineActive: Boolean,
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 3.dp
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                // v1.0.14：键盘间隙根治 —— safeDrawing 底部 = max(键盘, 导航栏)：
                // 键盘弹出时工具栏贴键盘上沿，收起时避让手势条，不再双重叠加留出空白
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolIcon(Icons.Default.Undo, "撤销", canUndo, onClick = onUndo)
            ToolIcon(Icons.Default.Redo, "重做", canRedo, onClick = onRedo)
            ToolDivider()
            ToolIcon(Icons.Default.FormatBold, "加粗", true, boldActive, onBold)
            ToolIcon(Icons.Default.FormatItalic, "斜体", true, italicActive, onItalic)
            ToolIcon(Icons.Default.FormatUnderlined, "下划线", true, underlineActive, onUnderline)
            ToolIcon(Icons.Default.Title, "标题", true, onClick = onHeading)
            ToolDivider()
            ToolIcon(Icons.Default.CheckBox, "待办", true, onClick = onTodo)
            ToolIcon(Icons.Default.FormatListBulleted, "无序列表", true, onClick = onBullet)
            ToolIcon(Icons.Default.FormatListNumbered, "有序列表", true, onClick = onOrdered)
            ToolDivider()
            ToolIcon(Icons.Default.Image, "插入图片", true, onClick = onImage)
            ToolIcon(Icons.Default.AttachFile, "插入附件", true, onClick = onFile)
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
