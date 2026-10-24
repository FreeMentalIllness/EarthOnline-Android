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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * v1.0.6.7（死命令 #4）：笔记正文改为**行式编辑器 + 行内标准 Checkbox**。
 * richeditor-compose 不支持在文本流中插入 Composable，故自研行式编辑器：
 *  - 每个逻辑行是一个 [NoteLine]，待办行行首渲染**真实 Compose Checkbox**，勾选加删除线；
 *  - 回车拆分为新行、行首退格与上一行合并；长行自动软换行；
 *  - 存取仍是 Markdown：待办行序列化为 "- [ ] "/"- [x] "，跨端兼容，旧数据自动解析回行结构。
 */
private data class NoteLine(
    val id: Long,
    val value: TextFieldValue,
    val todo: Boolean,
    val done: Boolean
) {
    val text: String get() = value.text
}

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

    var title by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf(listOf<String>()) }
    var tagInput by remember { mutableStateOf("") }
    var showTagRow by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var bodyScale by remember { mutableFloatStateOf(1f) }

    // v1.0.6.7（死命令 #4）：行式正文（含行内复选框）
    val lines = remember { mutableStateListOf<NoteLine>() }
    var nextId by remember { mutableLongStateOf(1L) }
    var focusedId by remember { mutableLongStateOf(-1L) }
    var pendingFocusId by remember { mutableLongStateOf(-1L) }
    val focusRequesters = remember { mutableMapOf<Long, FocusRequester>() }

    fun focusedIndex(): Int = lines.indexOfFirst { it.id == focusedId }

    /** 在 index 之后插入新行并返回；调用方负责把 pendingFocusId 指向它以聚焦。 */
    fun insertLine(afterIndex: Int, text: String = "", todo: Boolean = false, done: Boolean = false): NoteLine {
        val nl = NoteLine(nextId++, TextFieldValue(text), todo, done)
        lines.add((afterIndex + 1).coerceIn(0, lines.size), nl)
        return nl
    }

    fun commitTag() {
        val t = tagInput.trim().trim('#')
        if (t.isNotBlank()) tags = NotesViewModel.normalizeTags(tags + t)
        tagInput = ""
    }

    /* ---------- 工具栏动作：作用于当前聚焦行（行级，Markdown 标记存取） ---------- */
    fun wrapFocused(open: String, close: String = open) {
        val i = focusedIndex(); if (i < 0) return
        val txt = lines[i].text
        val wrapped = if (txt.length >= open.length + close.length && txt.startsWith(open) && txt.endsWith(close))
            txt.substring(open.length, txt.length - close.length) else "$open$txt$close"
        lines[i] = lines[i].copy(value = TextFieldValue(wrapped))
    }
    fun toggleHeading() {
        val i = focusedIndex(); if (i < 0) return
        val txt = lines[i].text
        val newText = if (txt.startsWith("# ")) txt.removePrefix("# ") else "# $txt"
        lines[i] = lines[i].copy(value = TextFieldValue(newText))
    }
    fun toggleLineTodo() {
        val i = focusedIndex(); if (i < 0) return
        lines[i] = lines[i].copy(todo = !lines[i].todo)
    }
    fun toggleListPrefix(prefix: String) {
        val i = focusedIndex(); if (i < 0) return
        val cur = lines[i].text.trimStart()
        val body = when {
            cur.startsWith("- ") -> cur.removePrefix("- ")
            cur.startsWith("* ") -> cur.removePrefix("* ")
            cur.startsWith("1. ") -> cur.removePrefix("1. ")
            else -> cur
        }
        val already = cur.startsWith(prefix)
        val newText = if (already) body else "$prefix$body"
        lines[i] = lines[i].copy(value = TextFieldValue(newText))
    }
    fun appendToFocused(snippet: String) {
        val i = focusedIndex(); if (i < 0) return
        val merged = lines[i].text + snippet
        lines[i] = lines[i].copy(value = TextFieldValue(merged))
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
        importIntoNoteMedia(uri, "image.jpg") { path -> appendToFocused("![图片](file://$path)") }
    })
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            importIntoNoteMedia(uri, "file") { path ->
                appendToFocused("[${path.substringAfterLast('_')}]($path)")
            }
        }
    }

    LaunchedEffect(id) {
        if (id.isNotBlank()) {
            vm.getNote(id)?.let { m ->
                val md = m.markdown?.takeIf { it.isNotBlank() } ?: m.text
                val (t, rest) = splitTitle(md)
                title = t
                lines.clear()
                val re = Regex("^\\s*-\\s*\\[([ xX])\\]\\s*(.*)$")
                rest.lines().forEach { raw ->
                    val mm = re.find(raw)
                    if (mm != null) {
                        lines += NoteLine(
                            nextId++, TextFieldValue(mm.groupValues[2]), true,
                            mm.groupValues[1].equals("x", ignoreCase = true)
                        )
                    } else {
                        lines += NoteLine(nextId++, TextFieldValue(raw), false, false)
                    }
                }
                if (lines.isEmpty()) lines += NoteLine(nextId++, TextFieldValue(""), false, false)
                tags = NotesViewModel.tagsOf(m)
            }
        } else {
            if (lines.isEmpty()) lines += NoteLine(nextId++, TextFieldValue(""), false, false)
        }
        // 载入后聚焦首行，工具栏立即可用
        pendingFocusId = lines.firstOrNull()?.id ?: -1L
        loaded = true
    }

    // 结构性变更（新增/删除/合并行）后聚焦目标行
    LaunchedEffect(pendingFocusId) {
        val pid = pendingFocusId
        if (pid >= 0L) {
            runCatching { focusRequesters[pid]?.requestFocus() }
            pendingFocusId = -1L
        }
    }

    fun save() {
        val bodyMd = lines.joinToString("\n") { l ->
            if (l.todo) "- [${if (l.done) "x" else " "}] ${l.text}" else l.text
        }.trim()
        val plain = lines.joinToString("\n") { it.text }.trim()
        val t = title.trim()
        if (t.isBlank() && plain.isBlank()) {
            onBack()
            return
        }
        commitTag()
        val markdown = if (t.isBlank()) bodyMd else "# $t\n\n$bodyMd"
        val plainBody = if (t.isBlank()) plain
        else listOf(t, plain).filter { it.isNotBlank() }.joinToString("\n\n")
        scope.launch {
            vm.saveNote(id.ifBlank { null }, plainBody.ifBlank { t }, tags, markdown)
            onBack()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
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
                    onBold = { wrapFocused("**") },
                    onItalic = { wrapFocused("*") },
                    onUnderline = { wrapFocused("<u>", "</u>") },
                    onHeading = { toggleHeading() },
                    onTodo = { toggleLineTodo() },
                    onBullet = { toggleListPrefix("- ") },
                    onOrdered = { toggleListPrefix("1. ") },
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

            // v1.0.6.7（死命令 #4）：行式正文 —— 待办行行首真实 Checkbox，勾选加删除线
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
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        val allBlank = lines.all { it.text.isBlank() }
                        if (allBlank) {
                            Text(
                                "写点什么…支持加粗、列表、行内待办（工具栏 ☑）、插图",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = (16 * bodyScale).sp
                                )
                            )
                        }
                        lines.forEachIndexed { i, line ->
                            val fr = focusRequesters.getOrPut(line.id) { FocusRequester() }
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                if (line.todo) {
                                    Checkbox(
                                        checked = line.done,
                                        onCheckedChange = { lines[i] = line.copy(done = !line.done) },
                                        modifier = Modifier.padding(top = 10.dp).size(24.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                BasicTextField(
                                    value = line.value,
                                    onValueChange = { nv ->
                                        if (nv.text.contains('\n')) {
                                            // 回车拆行：在首个换行处切成两行，聚焦新行
                                            val parts = nv.text.split("\n", limit = 2)
                                            val before = parts[0]
                                            val after = if (parts.size > 1) parts[1] else ""
                                            lines[i] = line.copy(value = TextFieldValue(before))
                                            val nl = NoteLine(nextId++, TextFieldValue(after), false, false)
                                            lines.add(i + 1, nl)
                                            pendingFocusId = nl.id
                                        } else {
                                            lines[i] = line.copy(value = nv)
                                        }
                                    },
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = (16 * bodyScale).sp,
                                        lineHeight = (28 * bodyScale).sp,
                                        textDecoration = if (line.done) TextDecoration.LineThrough else null,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(fr)
                                        .onFocusChanged { if (it.isFocused) focusedId = line.id }
                                        .onKeyEvent { ev ->
                                            if (ev.type == KeyEventType.KeyDown && ev.key == Key.Backspace) {
                                                if (line.value.selection.min == 0) {
                                                    if (i > 0) {
                                                        // 行首退格：与上一行合并
                                                        val prev = lines[i - 1]
                                                        val merged = prev.text + line.text
                                                        lines[i - 1] = prev.copy(
                                                            value = TextFieldValue(merged, TextRange(merged.length))
                                                        )
                                                        lines.removeAt(i)
                                                        pendingFocusId = prev.id
                                                        true
                                                    } else false
                                                } else false
                                            } else false
                                        }
                                )
                            }
                            if (i < lines.lastIndex) Spacer(Modifier.height(2.dp))
                        }
                    }
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
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("取消") }
            }
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
 * 编辑工具栏：B I U H | ☑ 行内待办 • 列表 1. 有序 | 图片 附件 | A±。
 * 单行横向滚动，恒定可见；按钮作用于当前聚焦行（行级 Markdown 标记 / 行内复选框切换）。
 */
@Composable
private fun EditorFormatBar(
    modifier: Modifier = Modifier,
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
            ToolIcon(Icons.Rounded.FormatBold, "加粗", true, onClick = onBold)
            ToolIcon(Icons.Rounded.FormatItalic, "斜体", true, onClick = onItalic)
            ToolIcon(Icons.Rounded.FormatUnderlined, "下划线", true, onClick = onUnderline)
            ToolIcon(Icons.Rounded.Title, "标题", true, onClick = onHeading)
            ToolDivider()
            ToolIcon(Icons.Rounded.CheckBox, "行内待办", true, onClick = onTodo)
            ToolIcon(Icons.Rounded.FormatListBulleted, "无序列表", true, onClick = onBullet)
            ToolIcon(Icons.Rounded.FormatListNumbered, "有序列表", true, onClick = onOrdered)
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
