package com.example.earthonline.ui.tasks

import com.example.earthonline.ui.components.bouncyPress

import com.example.earthonline.ui.theme.GlassCard
import android.content.Intent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.ListAlt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SubdirectoryArrowRight
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.earthonline.data.local.entity.TaskEntity
import com.example.earthonline.ui.components.AnimatedAlertDialog
import com.example.earthonline.ui.components.ConfettiBurst
import com.example.earthonline.ui.components.EmptyState
import com.example.earthonline.ui.components.GlassTextField
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.components.UiDimens
import com.example.earthonline.ui.components.TrackScrollBlur
import com.example.earthonline.ui.design.EOConfirmDialog
import com.example.earthonline.ui.theme.AmberPrimary
import com.example.earthonline.ui.theme.AppCard
import com.example.earthonline.util.AchievementSound
import com.example.earthonline.util.millisToDayStr
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

private val CATEGORIES = listOf("main" to "主线", "side" to "支线", "todo" to "To Do")

/** 任务状态（对应 TaskEntity.status；done 由 doneAt 口径统一维护） */
private val STATUSES = listOf(
    "planning" to "计划中",
    "active" to "进行中",
    "paused" to "已暂停",
    "done" to "已完成"
)

private val DAY_FMT = SimpleDateFormat("yyyy-MM-dd", Locale.US)

/** 每层缩进宽度（与 TreeGuide 的单格宽度保持一致） */
// v1.0.6.7（死命令 #6）：层级缩进步进由 18dp 加大到 26dp，子任务左缩进更明显
private val INDENT = 26.dp

@Composable
fun TasksRoute(
    moreActions: MoreMenuActions,
    openAddOnEntry: Boolean = false,
    vm: TaskViewModel = hiltViewModel()
) {
    TasksScreen(vm, moreActions, openAddOnEntry)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    vm: TaskViewModel,
    moreActions: MoreMenuActions,
    openAddOnEntry: Boolean = false
) {
    val allTasks by vm.tasks.collectAsStateWithLifecycle()
    // v1.0.4：「隐藏已完成任务」开关（DataStore 持久化，跨会话记住）
    val hideDone by vm.hideDone.collectAsStateWithLifecycle(initialValue = false)
    // v1.0.10：顶层「日常 / Todo」；「日常」下不再强行拆分主线/支线，
    // 合并为统一的日常列表（减轻认知负担）。Todo 走独立 Tab + 表格视图。
    var topTab by remember { mutableStateOf("daily") }   // daily | todo
    // v1.0.10：daily 用 null 表示「主线 + 支线」合并视图；todo 走独立分类。
    val category = if (topTab == "daily") null else "todo"
    val collapsed = remember { mutableStateOf(setOf<String>()) }
    var showAdd by remember { mutableStateOf(openAddOnEntry) }
    var addParentId by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<TaskEntity?>(null) }
    // v1.2.3：「已完成」视图开关，便于回溯已经做完的任务
    var showDoneOnly by remember { mutableStateOf(false) }
    // v1.0.5 批量操作：多选模式（工具行「多选」进入；点卡片勾选；退出时清空选择）
    var selectMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateOf(setOf<String>()) }

    // v1.0.10：日常 = 主线 + 支线 合并；Todo = 独立分类
    val filtered = remember(allTasks, category) {
        if (category == null) allTasks.filter { it.category == "main" || it.category == "side" }
        else allTasks.filter { it.category == category }
    }
    // v1.0.4：「只看已完成 / 隐藏已完成」在建树前过滤源列表 ——
    //  - 只看已完成：仅保留 done 节点，已完成分支可完整回看；
    //  - 隐藏已完成：移除 done 节点，其下未完成的子任务由 buildTaskTree 的孤儿兜底提升为根，不会整支消失。
    // 在此之前先拍平再过滤会破坏父子连接线的层级信息。
    val treeSource = remember(filtered, showDoneOnly, hideDone) {
        when {
            showDoneOnly -> filtered.filter { it.status == "done" }
            hideDone -> filtered.filter { it.status != "done" }
            else -> filtered
        }
    }
    val tree = remember(treeSource) { buildTaskTree(treeSource) }
    val flat = remember(tree, collapsed.value) { flattenTaskTree(tree, collapsed.value) }
    val visible = flat

    // 编辑对话框里的「父任务」候选：同分类下的全部任务（编辑自身时要排除自己及其后代，避免成环）
    val parentOptions = remember(filtered, editing) {
        val exclude = editing?.let { e ->
            val self = setOf(e.id)
            fun descendants(id: String, acc: MutableSet<String>): MutableSet<String> {
                filtered.filter { it.parentId == id }.forEach {
                    acc.add(it.id); descendants(it.id, acc)
                }
                return acc
            }
            self + descendants(e.id, mutableSetOf())
        } ?: emptySet()
        filtered.filter { it.id !in exclude }
    }

    val doneCount = remember(filtered) { filtered.count { it.status == "done" } }

    // ————— 庆祝三件套：勾选完成时轻震动 + 琥珀光晕 + 清脆音效 —————
    // 终极重构 · 工程三（死命令）：**连击（combo）机制已废弃** —— 光晕不再随
    // 「短时间内连点」递增，成长只由真实累计行为决定，不鼓励无意义连点刷收益。
    val haptic = LocalHapticFeedback.current
    val ctx = LocalContext.current
    var celebrateId by remember { mutableStateOf<String?>(null) }
    // 待确认删除的任务 id（红线：破坏性操作必须走 EOConfirmDialog 二次确认）
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    fun celebrate(taskId: String) {
        celebrateId = taskId
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        // 复用成就音效播放器（SoundPool 低延迟，且跟随设置里的音效开关）
        AchievementSound.play(ctx)
    }
    LaunchedEffect(celebrateId) {
        // 950ms = 撒花动画（900ms）+ 50ms 余量，光晕/撒花共用该窗口
        if (celebrateId != null) { delay(950); celebrateId = null }
    }

    // ————— v1.0.3 长按拖拽排序：仅允许在同级兄弟间交换（不破坏任务树结构） —————
    val listState = rememberLazyListState()
    TrackScrollBlur(listState)
    var dragIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        topBar = {
            TopAppBar(
                title = { Text("任务") },
                // v1.0.11：Header 透明化，与主页一致透出壁纸，去掉顶部悬浮覆盖感
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = { }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { addParentId = null; showAdd = true },
                containerColor = AmberPrimary,
                // v1.0.6 封板：全套 FAB 统一圆形（M3 默认是 16dp 圆角方块，与主页圆形按钮不一致）
                shape = CircleShape,
                modifier = Modifier.bouncyPress()
            ) { Icon(Icons.Rounded.Add, contentDescription = "新增任务") }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 顶层 Tab：日常 / Todo
            // v1.0.6.8（死命令 #2）：TabRow 加深容器底色 + 分割线；未选中 Tab 加 1.5dp
            // 主题色描边胶囊，解决「未选中 Tab 看不见、不知道能点」的问题。
            TabRow(
                selectedTabIndex = if (topTab == "daily") 0 else 1,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                divider = {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                }
            ) {
                Tab(
                    selected = topTab == "daily",
                    onClick = { topTab = "daily" },
                    text = { TasksTabLabel("日常", topTab == "daily") }
                )
                Tab(
                    selected = topTab == "todo",
                    onClick = { topTab = "todo" },
                    text = { TasksTabLabel("Todo", topTab == "todo") }
                )
            }
            // 工具行：概览 + 视图开关 + 全部展开 / 折叠
            // v1.0.5 QA 修复：两个区块拆开判定。
            // 原来整体挂在 flat.isNotEmpty() 上 —— 当某分类唯一任务是「已完成」时，
            // 打开「隐藏已完成」后列表变空，开关行随工具行一起消失，
            // 用户再也无法关闭开关恢复视图（实测抓到）。
            // 现在开关行只要求「该分类下有任务」就常驻，保证任何时刻都能切回。
            if (flat.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = UiDimens.ListPad, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "共 ${flat.size} 项 · 已完成 $doneCount",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { collapsed.value = emptySet() }) {
                        Icon(Icons.Rounded.UnfoldMore, contentDescription = "全部展开")
                    }
                    IconButton(onClick = {
                        collapsed.value = tree
                            .filter { it.children.isNotEmpty() }
                            .map { it.task.id }
                            .toSet()
                    }) {
                        Icon(Icons.Rounded.UnfoldLess, contentDescription = "全部折叠")
                    }
                }
            }
            // v1.0.4：视图开关行 —— 「隐藏已完成」只看未完成，「只看已完成」回看做完的；
            // 两个开关互斥（打开一个自动关掉另一个），持久化走 DataStore。
            if (filtered.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = UiDimens.ListPad),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = hideDone,
                        onClick = {
                            val next = !hideDone
                            vm.setHideDone(next)
                            if (next) showDoneOnly = false
                        },
                        label = { Text("隐藏已完成") },
                        modifier = Modifier.height(32.dp)
                    )
                    FilterChip(
                        selected = showDoneOnly,
                        onClick = {
                            showDoneOnly = !showDoneOnly
                            if (showDoneOnly && hideDone) vm.setHideDone(false)
                        },
                        label = { Text("只看已完成") },
                        modifier = Modifier.height(32.dp)
                    )
                    // v1.0.5：批量操作入口 —— 长按已被拖拽排序占用，改用「多选」chip 进入
                    FilterChip(
                        selected = selectMode,
                        onClick = {
                            selectMode = !selectMode
                            if (!selectMode) selected.value = emptySet()
                        },
                        label = { Text("多选") },
                        modifier = Modifier.height(32.dp)
                    )
                }
                // v1.0.5 批量操作栏：完成 / 删除（进回收站）/ 导出 / 全选
                if (selectMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = UiDimens.ListPad, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "已选 ${selected.value.size} 项",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            selected.value =
                                if (selected.value.size == visible.size) emptySet()
                                else visible.map { it.task.id }.toSet()
                        }) {
                            Text(if (selected.value.size == visible.size) "取消全选" else "全选")
                        }
                        TextButton(
                            enabled = selected.value.isNotEmpty(),
                            onClick = {
                                visible.filter { it.task.id in selected.value && it.task.status != "done" }
                                    .forEach { vm.toggleDone(it.task) }
                            }
                        ) { Text("完成") }
                        TextButton(
                            enabled = selected.value.isNotEmpty(),
                            onClick = {
                                // 批量删除同样走回收站（软删），30 天内可到回收站恢复
                                visible.filter { it.task.id in selected.value }
                                    .forEach { vm.deleteCascade(it.task.id) }
                                selected.value = emptySet()
                            }
                        ) { Text("删除", color = MaterialTheme.colorScheme.error) }
                        TextButton(
                            enabled = selected.value.isNotEmpty(),
                            onClick = {
                                val text = visible
                                    .filter { it.task.id in selected.value }
                                    .joinToString("\n") { n ->
                                        val t = n.task
                                        val mark = if (t.status == "done") "x" else " "
                                        val note = t.note?.let { "（$it）" } ?: ""
                                        "- [$mark] ${t.title}$note"
                                    }
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                runCatching {
                                    ctx.startActivity(Intent.createChooser(send, "导出任务"))
                                }
                            }
                        ) { Text("导出") }
                    }
                }
            }

            if (visible.isEmpty()) {
                if (showDoneOnly) {
                    // v1.2.3：「已完成」视图为空时，给一句正向引导（不再重复「创建」按钮，添加走右下角 FAB）
                    EmptyState(
                        icon = Icons.Rounded.TaskAlt,
                        title = "还没有已完成的任务",
                        message = "完成一条任务就会出现在这里，方便你回顾一路走来的脚印。"
                    )
                } else if (hideDone && filtered.isNotEmpty()) {
                    // v1.0.4：隐藏已完成模式下全部做完时的空状态（区别于「分类本身是空的」）
                    EmptyState(
                        icon = Icons.Rounded.Celebration,
                        title = "这个分类下没有未完成的任务",
                        message = "都清空啦。想回看已完成的记录，可以打开「只看已完成」。"
                    )
                } else {
                    // v1.2.1：空状态引导 —— 光秃秃一行「暂无任务」不告诉用户下一步该干嘛
                    val (label, hint) = when (category) {
                        null -> "日常还是空白的" to
                            "把今天想推进的事记下来——大的拆成子任务，小的直接做完划掉。先立一条，动起来最重要。"
                        "todo" -> "To Do 是空的" to
                            "临时冒出来的小事往这儿丢：交水电费、回个消息、买瓶酱油。做完划掉就行。"
                        else -> "这个分类下还没有任务" to
                            "点右下角的 + 新建一条任务开始吧。"
                    }
                    // v1.2.3：去掉重复的「创建」按钮 —— 新建任务统一由右下角 FAB 负责，避免两个入口打架
                    EmptyState(
                        icon = Icons.Rounded.ListAlt,
                        title = label,
                        message = hint
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = UiDimens.ListPad)
                        .pointerInput(visible) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { off ->
                                    val info = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                        off.y >= it.offset && off.y <= it.offset + it.size
                                    }
                                    dragIndex = info?.index
                                    dragOffset = 0f
                                },
                                onDrag = { change, amount ->
                                    val i = dragIndex ?: return@detectDragGesturesAfterLongPress
                                    change.consume()
                                    dragOffset += amount.y
                                    val info = listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.index == i } ?: return@detectDragGesturesAfterLongPress
                                    val cur = visible.getOrNull(i) ?: return@detectDragGesturesAfterLongPress
                                    val half = info.size / 2f
                                    if (dragOffset > half) {
                                        // 向下找最近的同级兄弟
                                        for (j in i + 1 until visible.size) {
                                            if (visible[j].task.parentId != cur.task.parentId) break
                                            vm.swapOrder(cur.task, visible[j].task)
                                            dragIndex = j
                                            dragOffset -= info.size
                                            break
                                        }
                                    } else if (dragOffset < -half) {
                                        for (j in i - 1 downTo 0) {
                                            if (visible[j].task.parentId != cur.task.parentId) break
                                            vm.swapOrder(cur.task, visible[j].task)
                                            dragIndex = j
                                            dragOffset += info.size
                                            break
                                        }
                                    }
                                },
                                onDragEnd = { dragIndex = null; dragOffset = 0f },
                                onDragCancel = { dragIndex = null; dragOffset = 0f }
                            )
                        },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    // Todo 表格视图：表头
                    if (topTab == "todo") {
                        item(key = "__todo_header__") { TodoTableHeader() }
                    }
                    itemsIndexed(
                        visible,
                        key = { _, node -> node.task.id },
                        // contentType 告诉 Compose「这些格子长得一样」，滚动时可以直接复用
                        // 上一个同类格子的测量结果与组合结果，省掉一次重新组合
                        contentType = { _, _ -> if (topTab == "todo") "todoRow" else "taskRow" }
                    ) { index, node ->
                        val dragging = dragIndex == index
                        if (topTab == "todo") {
                            TodoTableRow(
                                task = node.task,
                                selectMode = selectMode,
                                selected = node.task.id in selected.value,
                                onToggleSelect = {
                                    val id = node.task.id
                                    selected.value = if (id in selected.value)
                                        selected.value - id else selected.value + id
                                },
                                onToggleDone = {
                                    if (node.task.status != "done") celebrate(node.task.id)
                                    vm.toggleDone(node.task)
                                },
                                onEdit = { editing = node.task }
                            )
                        } else {
                        Box(
                            Modifier
                                .animateItem()
                                .graphicsLayer {
                                    if (dragging) {
                                        translationY = dragOffset
                                        scaleX = 1.02f
                                        scaleY = 1.02f
                                    }
                                }
                        ) {
                        TaskRow(
                            node = node,
                            collapsed = collapsed.value.contains(node.task.id),
                            celebrate = celebrateId == node.task.id,
                            selectMode = selectMode,
                            selected = node.task.id in selected.value,
                            onToggleSelect = {
                                val id = node.task.id
                                selected.value = if (id in selected.value)
                                    selected.value - id else selected.value + id
                            },
                            onToggleCollapse = {
                                val id = node.task.id
                                collapsed.value = if (collapsed.value.contains(id))
                                    collapsed.value - id else collapsed.value + id
                            },
                            onToggleDone = {
                                if (node.task.status != "done") celebrate(node.task.id)
                                vm.toggleDone(node.task)
                            },
                            onSetProgress = { vm.setProgress(node.task, it) },
                            onEdit = { editing = node.task },
                            onAddChild = { addParentId = node.task.id; showAdd = true },
                            // 红线：不直接删除，先弹破坏性确认
                            onDelete = { pendingDeleteId = node.task.id }
                        )
                        }
                        }
                    }
                }
            }
        }
    }

        // v1.0.4 撒花庆祝：完成任务的粒子迸发，覆盖层不拦截点击（Canvas 无触摸处理）
        if (celebrateId != null) {
            ConfettiBurst(burstKey = celebrateId)
        }

        // 红线（终极重构 · 工程一）：所有破坏性操作强制走 EOConfirmDialog 二次确认，
        // 业务页严禁直接执行删除。id 先局部捕获，避免确认回调执行时状态已被清空。
        val deleteId = pendingDeleteId
        if (deleteId != null) {
            EOConfirmDialog(
                title = "删除任务",
                message = "该任务及其全部子任务都会被移入回收站，30 天内可在回收站恢复。",
                confirmLabel = "移入回收站",
                destructive = true,
                onConfirm = { vm.deleteCascade(deleteId) },
                onDismiss = { pendingDeleteId = null }
            )
        }
    }

    if (showAdd) {
        TaskEditDialog(
            category = category ?: "main",
            parentOptions = parentOptions,
            initialParentId = addParentId,
            onDismiss = { showAdd = false },
            onSubmit = { title, due, note, status, parentId ->
                vm.addTask(title, category ?: "main", parentId, due, note, status)
                showAdd = false
            }
        )
    }
    if (editing != null) {
        val t = editing!!
        TaskEditDialog(
            initialTitle = t.title,
            initialDue = t.dueDate,
            initialNote = t.note,
            initialStatus = t.status,
            initialParentId = t.parentId,
            category = t.category,
            parentOptions = parentOptions,
            onDismiss = { editing = null },
            onSubmit = { title, due, note, status, parentId ->
                vm.update(
                    t.copy(
                        title = title, dueDate = due, note = note,
                        status = status, parentId = parentId
                    )
                )
                editing = null
            },
            onDelete = { vm.deleteCascade(t.id); editing = null }
        )
    }
}

/**
 * v1.0.6.8（死命令 #2）：任务页顶层 Tab 文字。
 * 未选中态外包 1.5dp 主题色描边胶囊（提高边框线宽 + 加深颜色），选中态实心琥珀淡底，
 * 让两个 Tab 在任何壁纸 / 背景下都清晰可辨。
 */
@Composable
private fun TasksTabLabel(text: String, selected: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = if (selected) AmberPrimary.copy(alpha = 0.14f) else Color.Transparent,
        contentColor = if (selected) AmberPrimary else scheme.onSurface,
        shape = RoundedCornerShape(8.dp),
        border = if (selected) null else BorderStroke(1.5.dp, AmberPrimary.copy(alpha = 0.85f)),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/**
 * v1.0.7：Todo 表格视图 —— 表头。列：完成勾选 / 任务 / 截止 / 状态。
 */
@Composable
private fun TodoTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = UiDimens.ListPad, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(48.dp)) // 与行首勾选框对齐，让「任务」列左边缘一致
        Text(
            "任务",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(start = 8.dp)
        )
        Text(
            "截止",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(72.dp)
        )
        Text(
            "状态",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(56.dp)
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/**
 * v1.0.7：Todo 表格视图 —— 单行。
 * 勾选框直接切换完成态（多选模式下改为切换选中）；点行进入编辑。
 */
@Composable
private fun TodoTableRow(
    task: TaskEntity,
    selectMode: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onToggleDone: () -> Unit,
    onEdit: () -> Unit
) {
    val done = task.status == "done"
    val statusLabel = STATUSES.firstOrNull { it.first == task.status }?.second ?: task.status
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (selectMode) onToggleSelect() else onEdit() }
            .padding(horizontal = UiDimens.ListPad, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = if (selectMode) selected else done,
            onCheckedChange = { if (selectMode) onToggleSelect() else onToggleDone() },
            modifier = Modifier.size(44.dp)
        )
        Text(
            task.title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (done) TextDecoration.LineThrough else null,
            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(start = 4.dp)
        )
        Text(
            task.dueDate ?: "—",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(72.dp)
        )
        Text(
            statusLabel,
            style = MaterialTheme.typography.labelSmall,
            color = if (done) AmberPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(56.dp)
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

/**
 * 树形连接线引导：为每一层祖先画一格 18dp。
 * - 该层下方还有兄弟 -> 竖线贯穿整行
 * - 已是该层最后一个 -> 竖线只到中部，并向右画一条横线形成「└」拐角
 */
@Composable
private fun TreeGuide(depth: Int, ancestorLast: List<Boolean>, isLast: Boolean) {
    if (depth <= 0) return
    // v1.0.6.7（死命令 #6）：引导线加深 + 祖先列加淡色「车道」底色，层级关系一目了然
    val line = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val lane = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
    val corner = 22.dp
    Row(Modifier.fillMaxHeight()) {
        repeat(depth) { level ->
            val isFinalLevel = level == depth - 1
            val hasFollowingSibling =
                if (isFinalLevel) !isLast else !ancestorLast.getOrElse(level + 1) { true }
            Box(
                Modifier
                    .width(INDENT)
                    .fillMaxHeight()
                    .background(if (isFinalLevel) androidx.compose.ui.graphics.Color.Transparent else lane)
            ) {
                if (hasFollowingSibling) {
                    Box(
                        Modifier.fillMaxHeight().width(2.dp).align(Alignment.TopCenter).background(line)
                    )
                } else {
                    Box(
                        Modifier.height(corner).width(2.dp).align(Alignment.TopCenter).background(line)
                    )
                }
                if (isFinalLevel) {
                    Box(
                        Modifier
                            .padding(top = corner)
                            .height(2.dp)
                            .width(INDENT)
                            .align(Alignment.TopCenter)
                            .background(line)
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    node: TaskNode,
    collapsed: Boolean,
    celebrate: Boolean,
    selectMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onToggleCollapse: () -> Unit,
    onToggleDone: () -> Unit,
    onSetProgress: (Int) -> Unit,
    onEdit: () -> Unit,
    onAddChild: () -> Unit,
    onDelete: () -> Unit
) {
    val t = node.task
    val hasChildren = node.children.isNotEmpty()
    val done = t.status == "done"

    // 完成庆祝的琥珀光晕（固定强度 —— 连击机制已废弃，不再随连点递增）
    val glow = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(celebrate) {
        if (celebrate) {
            glow.snapTo(0.75f)
            glow.animateTo(0f, androidx.compose.animation.core.tween(650))
        }
    }

    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        TreeGuide(depth = node.depth, ancestorLast = node.ancestorLast, isLast = node.isLast)
        AppCard(
            modifier = Modifier
                .weight(1f)
                .clickable { if (selectMode) onToggleSelect() else onEdit() }
                // v1.0.5：多选模式下选中项高亮描边
                .then(
                    if (selected) Modifier.border(
                        2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(UiDimens.CardRadius)
                    ) else Modifier
                )
                .drawWithContent {
                    drawContent()
                    if (glow.value > 0f) {
                        // 外圈扩散描边 + 内侧淡填充，构成一次「完成」的光效反馈
                        drawRoundRect(
                            color = AmberPrimary.copy(alpha = glow.value),
                            cornerRadius = CornerRadius(40f, 40f),
                            style = Stroke(width = 8f + 40f * glow.value)
                        )
                        drawRoundRect(
                            color = AmberPrimary.copy(alpha = glow.value * 0.25f),
                            cornerRadius = CornerRadius(40f, 40f)
                        )
                    }
                }
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (hasChildren) {
                        IconButton(onClick = onToggleCollapse, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = if (collapsed) "展开子任务" else "折叠子任务",
                                modifier = Modifier.rotate(if (collapsed) 0f else 90f)
                            )
                        }
                    } else {
                        Spacer(Modifier.width(28.dp))
                    }
                    Checkbox(checked = done, onCheckedChange = { onToggleDone() })
                    Column(Modifier.weight(1f)) {
                        Text(
                            t.title,
                            style = if (hasChildren) MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ) else MaterialTheme.typography.bodyMedium,
                            textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
                            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusChip(t.status)
                            t.dueDate?.let {
                                // v1.0.17：📅 emoji → CalendarMonth 矢量
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.CalendarMonth,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    // 父任务：显示子任务聚合进度
                    if (hasChildren && node.childTotal > 0) {
                        AssistChip(
                            onClick = onToggleCollapse,
                            label = { Text("${node.childDone}/${node.childTotal}") }
                        )
                    }
                    IconButton(onClick = onAddChild, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.SubdirectoryArrowRight, contentDescription = "添加子任务")
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Delete, contentDescription = "删除")
                    }
                }

                // 父任务进度由子任务推导（不提供手动滑杆，避免两处进度口径打架）
                if (hasChildren && node.childTotal > 0) {
                    LinearProgressIndicator(
                        progress = { node.childDone.toFloat() / node.childTotal },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (collapsed) {
                        Text(
                            "已折叠 ${node.childTotal} 项子任务",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (!done) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("进度", style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = t.progress.toFloat(),
                            onValueChange = { onSetProgress(it.toInt()) },
                            valueRange = 0f..100f,
                            modifier = Modifier.weight(1f)
                        )
                        Text("${t.progress}%", style = MaterialTheme.typography.labelSmall)
                    }
                }

                t.note?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val (label, color) = when (status) {
        "active" -> "进行中" to MaterialTheme.colorScheme.primary
        "paused" -> "已暂停" to MaterialTheme.colorScheme.tertiary
        "done" -> "已完成" to MaterialTheme.colorScheme.outline
        else -> "计划中" to MaterialTheme.colorScheme.secondary
    }
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.Medium
    )
}

/**
 * 任务备注「待办」按钮：在光标处插入标准 Checkbox 结构（`[ ]` / `[x]`）。
 *
 * 与笔记编辑器 [NoteEditorScreen] 完全同一套语义 —— 一律用**空区间 replaceTextRange** 插入，
 * 删除长度为 0，绝不可能吃掉已有文字（早期 `addTextAtIndex` 在存在选区时会替换选区，即"吞字"）。
 * 光标所在行已是待办行时，按行首切换未勾选 / 已勾选。
 */
private fun toggleTaskTodo(state: com.mohamedrejeb.richeditor.model.RichTextState) {
    val text = state.toText()
    val caret = state.selection.min.coerceIn(0, text.length)
    val nl = text.lastIndexOf('\n', (caret - 1).coerceAtLeast(0))
    val ls = if (nl < 0) 0 else nl + 1
    val ne = text.indexOf('\n', caret)
    val le = if (ne < 0) text.length else ne
    val line = text.substring(ls, le)
    val prefixLen = "- [ ] ".length
    when {
        line.startsWith("- [ ] ") ->
            state.replaceTextRange(TextRange(ls, ls + prefixLen), "- [x] ")
        line.startsWith("- [x] ") || line.startsWith("- [X] ") ->
            state.replaceTextRange(TextRange(ls, ls + prefixLen), "- [ ] ")
        else ->
            state.replaceTextRange(TextRange(caret, caret), "- [ ] ")
    }
}

/**
 * v1.0.6.7（死命令 #5）：自绘钟表盘时间选择器（Analog Clock dial）。
 * 受控组件：传入 hour / minute，用户拖动或点选表盘时回调 onChange；
 * 「时针 / 分针」chip 决定当前在调哪个；表盘含 60 刻度 + 时针 / 分针。
 */
@Composable
private fun AnalogClockPicker(
    hour: Int,
    minute: Int,
    onChange: (Int, Int) -> Unit
) {
    var selectingHour by remember { mutableStateOf(true) }
    val accent = AmberPrimary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val outline = MaterialTheme.colorScheme.outlineVariant
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            String.format(java.util.Locale.US, "%02d:%02d", hour, minute),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = accent
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = selectingHour, onClick = { selectingHour = true }, label = { Text("时针") })
            FilterChip(selected = !selectingHour, onClick = { selectingHour = false }, label = { Text("分针") })
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .size(220.dp)
                .pointerInput(selectingHour) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    fun set(px: Float, py: Float) {
                        var ang = Math.toDegrees(
                            Math.atan2((py - cy).toDouble(), (px - cx).toDouble())
                        ).toFloat()
                        ang = (ang + 90f + 360f) % 360f
                        if (selectingHour) {
                            var h = Math.round(ang / 30f) % 12
                            if (h == 0) h = 12
                            onChange(h, minute)
                        } else {
                            onChange(hour, Math.round(ang / 6f) % 60)
                        }
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        set(down.position.x, down.position.y)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.pressed } ?: break
                            set(change.position.x, change.position.y)
                        }
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                val cx = size.width / 2f
                val cy = size.height / 2f
                drawCircle(
                    color = outline.copy(alpha = 0.35f),
                    radius = r,
                    center = Offset(cx, cy),
                    style = Stroke(width = 3f)
                )
                for (i in 0 until 60) {
                    val rad = Math.toRadians((i * 6).toDouble())
                    val isHourTick = i % 5 == 0
                    val outer = r - 6f
                    val inner = if (isHourTick) r - 24f else r - 14f
                    drawLine(
                        color = if (isHourTick) onSurface else outline,
                        start = Offset(cx + Math.cos(rad).toFloat() * outer, cy + Math.sin(rad).toFloat() * outer),
                        end = Offset(cx + Math.cos(rad).toFloat() * inner, cy + Math.sin(rad).toFloat() * inner),
                        strokeWidth = if (isHourTick) 3f else 1.5f
                    )
                }
                val hourRad = Math.toRadians(((hour % 12) * 30 + minute * 0.5 - 90).toDouble())
                val minRad = Math.toRadians((minute * 6 - 90).toDouble())
                drawLine(
                    color = accent,
                    start = Offset(cx, cy),
                    end = Offset(cx + Math.cos(hourRad).toFloat() * r * 0.5f, cy + Math.sin(hourRad).toFloat() * r * 0.5f),
                    strokeWidth = 8f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = accent,
                    start = Offset(cx, cy),
                    end = Offset(cx + Math.cos(minRad).toFloat() * r * 0.72f, cy + Math.sin(minRad).toFloat() * r * 0.72f),
                    strokeWidth = 5f,
                    cap = StrokeCap.Round
                )
                drawCircle(color = accent, radius = 9f, center = Offset(cx, cy))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (selectingHour) "拖动或点选表盘设置小时" else "拖动或点选表盘设置分钟",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 任务编辑 / 新增对话框。
 * 相比旧版补齐：父任务选择（决定层级）、状态选择、到期日不再限于 todo 分类。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TaskEditDialog(
    initialTitle: String = "",
    initialDue: String? = null,
    initialNote: String? = null,
    initialStatus: String = "planning",
    initialParentId: String? = null,
    category: String,
    parentOptions: List<TaskEntity>,
    onDismiss: () -> Unit,
    onSubmit: (title: String, due: String?, note: String?, status: String, parentId: String?) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var title by remember { mutableStateOf(initialTitle) }
    // 终极重构 · 工程二：任务备注**复用笔记的富文本组件**（richeditor-compose）。
    // v1.0.6.6：工具栏只保留列表 / 待办等基础结构格式（去掉加粗 / 斜体 / 下划线）。
    // 存储层依旧是 Markdown 字符串，直接复用现有 `note` 列 —— **零 Room 迁移**；
    // 与笔记完全同源（载入 setMarkdown / 保存 toMarkdown），跨端契约不破坏。
    val noteRich = remember { RichTextState() }
    LaunchedEffect(Unit) {
        if (!initialNote.isNullOrBlank()) runCatching { noteRich.setMarkdown(initialNote) }
    }
    // v1.0.6.7（死命令 #5）：到期日拆为「日期(YYYY-MM-DD)」+「时间(HH:mm，可选)」。
    // dueDate 主体仍是日期串以兼容跨端与日历匹配；选了时间就追加 " HH:mm"。
    var due by remember { mutableStateOf(initialDue?.take(10)) }
    var dueTime by remember {
        mutableStateOf(initialDue?.let { if (it.length >= 16) it.substring(11, 16) else null })
    }
    var status by remember { mutableStateOf(initialStatus) }
    var parentId by remember { mutableStateOf(initialParentId) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var parentPickerOpen by remember { mutableStateOf(false) }
    // v1.0.5 QA：提交防重 —— 保存按钮在同一帧内被连点两次会重复落库，加一次性标志拦住
    var submitted by remember { mutableStateOf(false) }

    val parentLabel = parentId?.let { id -> parentOptions.firstOrNull { it.id == id }?.title } ?: "无（作为顶层任务）"

    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    if (submitted || title.isBlank()) return@TextButton
                    submitted = true
                    onSubmit(
                        title.trim(),
                        if (due != null && dueTime != null) "$due $dueTime" else due,
                        runCatching { noteRich.toMarkdown() }.getOrDefault("")
                            .trim().takeIf { it.isNotBlank() },
                        status, parentId
                    )
                },
                enabled = title.isNotBlank() && !submitted
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
        title = { Text(if (onDelete != null) "编辑任务" else "新增任务") },
        text = {
            // v1.0.4：内容可滚动 —— 父任务卡 + 状态 FlowRow + 日期 + 备注在长内容 / 大字号下不溢出。
            // 注意：外层 GlassDialog 已提供整卡纵向滚动，这里**禁止再套一层 verticalScroll**，
            // 否则同方向嵌套滚动会被 Compose 以 infinity 约束直接抛 IllegalStateException 闪退。
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassTextField(
                    value = title, onValueChange = { title = it },
                    label = "标题", singleLine = true, modifier = Modifier.fillMaxWidth()
                )

                // 父任务：决定这棵树长什么样。
                // 这里不用 ExposedDropdownMenuBox —— 它在 AlertDialog 内触摸区域会错位，
                // 点选项只关闭下拉、不回调（详见 CategoryPicker 的注释）。改用嵌套对话框。
                if (parentOptions.isNotEmpty() || parentId != null) {
                AppCard(
                    onClick = { parentPickerOpen = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "父任务",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(parentLabel, style = MaterialTheme.typography.bodyLarge)
                            }
                            Icon(Icons.Rounded.ArrowDropDown, contentDescription = "选择父任务")
                        }
                    }
                }

                // 状态
                Column {
                    Text("状态", style = MaterialTheme.typography.labelMedium)
                    /* v1.0.1 布局修正 + v1.0.4 二次修正：
                       v1.0.1 用 Row + weight(1f) 等宽单行解决了「已完成」被挤成两行的问题，
                       但 weight + softWrap=false + Clip 在大字号 / 窄屏下会直接把超宽文字
                       裁掉（用户看到「已完」缺字）。现在改为 FlowRow 自然宽度布局：
                       空间够时四个选项铺同一行，放不下时整体换到下一行——
                       每个 chip 永远按自身内容宽度渲染，任何字号下文字都完整显示。 */
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        STATUSES.forEach { (key, label) ->
                            FilterChip(
                                selected = status == key,
                                onClick = { status = key },
                                label = {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        softWrap = false,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            )
                        }
                    }
                }

                GlassTextField(
                    value = due ?: "", onValueChange = {}, readOnly = true,
                    label = "到期日（可选）",
                    trailingIcon = { IconButton({ showDatePicker = true }) { Icon(Icons.Rounded.DateRange, null) } },
                    modifier = Modifier.fillMaxWidth()
                )
                if (due != null) {
                    TextButton(onClick = { due = null; dueTime = null }) { Text("清除到期日") }
                }

                // v1.0.6.7（死命令 #5）：时间选择 —— 钟表盘（Analog Clock）视图，选了到期日后可再选具体时间
                if (due != null) {
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (dueTime != null) "时间 $dueTime · 点击改表盘" else "设置具体时间（钟表盘，可选）")
                    }
                }

                // 终极重构 · 工程二：备注 = 富文本（复用笔记编辑器），存 Markdown
                Text(
                    "备注",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                /* v1.0.6.6（死命令 #7）：任务备注**去掉加粗 / 斜体 / 下划线**。
                   备注是随手记的短文本，行内字重样式既用不上，又会把整段文字搞得花哨；
                   只保留「无序列表 / 有序列表 / 待办」三类**基础结构格式**。 */
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { noteRich.toggleUnorderedList() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Rounded.List, contentDescription = "无序列表", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { noteRich.toggleOrderedList() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Rounded.FormatListNumbered, contentDescription = "有序列表", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { toggleTaskTodo(noteRich) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Rounded.CheckBox, contentDescription = "待办", modifier = Modifier.size(18.dp))
                    }
                }
                BasicRichTextEditor(
                    state = noteRich,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    decorationBox = { inner ->
                        if (noteRich.annotatedString.text.isEmpty()) {
                            Text("备注…支持列表 / 待办", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        inner()
                    }
                )

                Text(
                    "分类：${CATEGORIES.firstOrNull { it.first == category }?.second ?: category}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )

    if (showDatePicker) {
        val initialMillis = due?.let { runCatching { DAY_FMT.parse(it.take(10))?.time }.getOrNull() }
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { due = millisToDayStr(it) }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton({ showDatePicker = false }) { Text("取消") } }
        ) { DatePicker(state) }
    }

    // v1.0.6.7（死命令 #5）：时间选择器 —— 自绘钟表盘（Analog Clock dial），
    // 拖动 / 点选表盘即可直观选时；确定后写回 dueTime(HH:mm)，与日期拼成 "YYYY-MM-DD HH:mm"。
    if (showTimePicker) {
        var tHour by remember { mutableIntStateOf(dueTime?.take(2)?.toIntOrNull() ?: 9) }
        var tMinute by remember { mutableIntStateOf(dueTime?.takeLast(2)?.toIntOrNull() ?: 0) }
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("选择时间") },
            text = {
                AnalogClockPicker(
                    hour = tHour,
                    minute = tMinute,
                    onChange = { h, m -> tHour = h; tMinute = m }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    dueTime = String.format(java.util.Locale.US, "%02d:%02d", tHour, tMinute)
                    showTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton({ showTimePicker = false }) { Text("取消") } }
        )
    }

    // 父任务选择：嵌套对话框（避免在 AlertDialog 内使用 Popup 类组件）
    if (parentPickerOpen) {
        AnimatedAlertDialog(
            onDismissRequest = { parentPickerOpen = false },
            confirmButton = { TextButton({ parentPickerOpen = false }) { Text("取消") } },
            title = { Text("选择父任务") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    item {
                        ListItem(
                            headlineContent = { Text("无（作为顶层任务）") },
                            modifier = Modifier.clickable {
                                parentId = null
                                parentPickerOpen = false
                            }
                        )
                    }
                    items(parentOptions, key = { it.id }) { p ->
                        ListItem(
                            headlineContent = { Text(p.title) },
                            modifier = Modifier.clickable {
                                parentId = p.id
                                parentPickerOpen = false
                            }
                        )
                    }
                }
            }
        )
    }
}

