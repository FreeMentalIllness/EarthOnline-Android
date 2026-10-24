package com.example.earthonline.ui.notes

import com.example.earthonline.ui.components.AnimatedAlertDialog

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.earthonline.data.local.entity.MemoEntity
import com.example.earthonline.ui.components.EmptyHint
import com.example.earthonline.ui.components.GlassDialog
import com.example.earthonline.ui.components.GlassTextField
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.components.UiDimens
import com.example.earthonline.ui.components.TrackScrollBlur
import androidx.compose.foundation.lazy.rememberLazyListState
import com.example.earthonline.ui.theme.AppCard
import com.example.earthonline.ui.theme.AmberPrimary
import kotlinx.coroutines.launch

/**
 * 笔记主页（v1.0.6.9 重构：分类（文件夹）层级）。
 *
 * 形态：**先创建分类（文件夹），再在分类下创建笔记** —— 彻底取消平铺一锅粥。
 *  - 顶部常驻「新建笔记」入口（落入「未分类」）与「新建分类」入口；
 *  - 每个分类是一张可展开/收起的卡片：头部 = 图标 + 名称 + 篇数 + 管理（重命名/删除），
 *    展开 = 该分类下的笔记流 + 「＋ 新建笔记」（新笔记直接归入此分类）；
 *  - 没有归属的笔记收在「未分类」段，同样可展开、可新建；
 *  - 笔记卡片的「归类」按钮可把笔记移动到任意分类（或移出到未分类）；
 *  - 搜索 / 标签筛选激活时临时展示平铺结果（筛选态不分组，避免空分类干扰）。
 *
 * 存储：分类与归属走 DataStore JSON（见 [NotesViewModel]），笔记本体仍落 Room memos 表 ——
 * 底层数据与跨端同步契约零改动，纯粹是 UI / 交互层重构。
 */
@Composable
fun NotesRoute(
    moreActions: MoreMenuActions,
    onEdit: (id: String?, folderId: String?) -> Unit
) {
    val vm = hiltViewModel<NotesViewModel>()
    NotesScreen(vm = vm, moreActions = moreActions, onEdit = onEdit)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    vm: NotesViewModel,
    moreActions: MoreMenuActions,
    onEdit: (id: String?, folderId: String?) -> Unit
) {
    val scope = rememberCoroutineScope()
    val notes by vm.notes.collectAsStateWithLifecycle(initialValue = emptyList())
    val query by vm.query.collectAsStateWithLifecycle(initialValue = "")
    val allTags by vm.allTags.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeTag by vm.tag.collectAsStateWithLifecycle(initialValue = NotesViewModel.ALL_TAGS)
    val folders by vm.folders.collectAsStateWithLifecycle(initialValue = emptyList())
    val folderMap by vm.folderMap.collectAsStateWithLifecycle(initialValue = emptyMap())

    var searchOpen by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<String?>(null) }
    // v1.0.6.9：分类相关弹窗状态
    var showCreateFolder by remember { mutableStateOf(false) }
    var manageFolder by remember { mutableStateOf<NoteFolder?>(null) }
    var confirmDeleteFolder by remember { mutableStateOf<NoteFolder?>(null) }
    var moveTarget by remember { mutableStateOf<MemoEntity?>(null) }
    // 展开态：新分类自动展开，用户可随时收起
    var expanded by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(folders) {
        expanded = expanded + folders.map { it.id }
    }
    val folderIds = remember(folders) { folders.map { it.id }.toSet() }
    val filtering = query.isNotBlank() || activeTag != NotesViewModel.ALL_TAGS
    val unfiled = remember(notes, folderMap, folderIds) {
        notes.filter { val fid = folderMap[it.id]; fid.isNullOrBlank() || fid !in folderIds }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
                GlassTextField(
                    value = query,
                    onValueChange = vm::setQuery,
                    placeholder = "搜正文或标签",
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

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

            val listState = rememberLazyListState()
            TrackScrollBlur(listState)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 顶部两个常驻入口：新建笔记（落未分类）/ 新建分类
                item(key = "new_note") {
                    NewNoteEntry(onClick = { onEdit(null, null) })
                }
                item(key = "new_folder") {
                    NewFolderEntry(onClick = { showCreateFolder = true })
                }

                if (filtering) {
                    // 筛选态：平铺结果，不分组（避免空分类干扰筛选）
                    item(key = "filter_hint") {
                        Text(
                            "「${query.ifBlank { "#$activeTag" }}」的筛选结果（${notes.size}）",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 2.dp, top = 2.dp)
                        )
                    }
                    if (notes.isEmpty()) {
                        item(key = "filter_empty") {
                            EmptyHint("没有匹配的笔记", icon = Icons.Rounded.Search)
                        }
                    } else {
                        items(notes, key = { it.id }) { m ->
                            NoteCard(
                                text = m.text,
                                markdown = m.markdown ?: m.text,
                                tags = NotesViewModel.tagsOf(m),
                                time = formatNoteTime(m.createdAt),
                                onClick = { onEdit(m.id, null) },
                                onDelete = { toDelete = m.id },
                                onMove = { moveTarget = m }
                            )
                        }
                    }
                } else {
                    if (notes.isEmpty() && folders.isEmpty()) {
                        item(key = "empty_all") {
                            Column(
                                Modifier.fillMaxWidth().padding(top = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.EditNote,
                                    contentDescription = null,
                                    tint = AmberPrimary,
                                    modifier = Modifier.size(56.dp)
                                )
                                Text(
                                    "先建一个分类吧",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "把笔记分门别类：工作、生活、灵感……\n建好分类后，在分类里写笔记。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // —— 分类卡片（可展开/收起） ——
                    folders.forEach { f ->
                        val fNotes = notes.filter { folderMap[it.id] == f.id }
                        val open = f.id in expanded
                        item(key = "folder-${f.id}") {
                            FolderHeaderCard(
                                folder = f,
                                count = fNotes.size,
                                expanded = open,
                                onToggle = {
                                    expanded = if (open) expanded - f.id else expanded + f.id
                                },
                                onManage = { manageFolder = f }
                            )
                        }
                        if (open) {
                            if (fNotes.isEmpty()) {
                                item(key = "folder-empty-${f.id}") {
                                    EmptyHint("这个分类还是空的，写第一篇吧", icon = Icons.Rounded.EditNote)
                                }
                            } else {
                                items(fNotes, key = { it.id }) { m ->
                                    NoteCard(
                                        text = m.text,
                                        markdown = m.markdown ?: m.text,
                                        tags = NotesViewModel.tagsOf(m),
                                        time = formatNoteTime(m.createdAt),
                                        onClick = { onEdit(m.id, null) },
                                        onDelete = { toDelete = m.id },
                                        onMove = { moveTarget = m }
                                    )
                                }
                            }
                            item(key = "folder-new-${f.id}") {
                                NewNoteInFolderRow(folderName = f.name) { onEdit(null, f.id) }
                            }
                        }
                    }

                    // —— 未分类段（有笔记才显示；一篇都没有时不占位） ——
                    if (unfiled.isNotEmpty()) {
                        val open = " unfiled" in expanded
                        item(key = "unfiled") {
                            FolderHeaderCard(
                                folder = null,
                                name = "未分类",
                                count = unfiled.size,
                                expanded = open,
                                onToggle = {
                                    expanded = if (open) expanded - " unfiled" else expanded + " unfiled"
                                },
                                onManage = null
                            )
                        }
                        if (open) {
                            items(unfiled, key = { it.id }) { m ->
                                NoteCard(
                                    text = m.text,
                                    markdown = m.markdown ?: m.text,
                                    tags = NotesViewModel.tagsOf(m),
                                    time = formatNoteTime(m.createdAt),
                                    onClick = { onEdit(m.id, null) },
                                    onDelete = { toDelete = m.id },
                                    onMove = { moveTarget = m }
                                )
                            }
                            item(key = "unfiled-new") {
                                NewNoteInFolderRow(folderName = "未分类") { onEdit(null, null) }
                            }
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { id ->
        AnimatedAlertDialog(
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

    // —— 新建分类 ——
    if (showCreateFolder) {
        var name by remember { mutableStateOf("") }
        GlassDialog(onDismissRequest = { showCreateFolder = false }) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("新建分类", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                GlassTextField(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    label = "分类名称",
                    placeholder = "例如：工作 / 生活 / 灵感",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (name.isNotBlank()) { vm.createFolder(name); showCreateFolder = false }
                    }),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { showCreateFolder = false },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("取消", maxLines = 1) }
                    Button(
                        onClick = { vm.createFolder(name); showCreateFolder = false },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.weight(1.25f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AmberPrimary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("创建", fontWeight = FontWeight.SemiBold, maxLines = 1) }
                }
            }
        }
    }

    // —— 分类管理（重命名 / 删除） ——
    manageFolder?.let { f ->
        var name by remember(f.id) { mutableStateOf(f.name) }
        GlassDialog(onDismissRequest = { manageFolder = null }) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("管理分类", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                GlassTextField(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    label = "分类名称",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "删除分类不会删除笔记，旗下笔记将移回「未分类」。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { confirmDeleteFolder = f; manageFolder = null },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("删除分类", maxLines = 1) }
                    Button(
                        onClick = { vm.renameFolder(f.id, name); manageFolder = null },
                        enabled = name.isNotBlank() && name != f.name,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AmberPrimary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("保存改名", fontWeight = FontWeight.SemiBold, maxLines = 1) }
                }
            }
        }
    }

    confirmDeleteFolder?.let { f ->
        AnimatedAlertDialog(
            onDismissRequest = { confirmDeleteFolder = null },
            title = { Text("删除分类「${f.name}」？") },
            text = { Text("旗下笔记将移回「未分类」，笔记本身不会丢失。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteFolder(f.id)
                    confirmDeleteFolder = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteFolder = null }) { Text("取消") } }
        )
    }

    // —— 移动笔记到分类 ——
    moveTarget?.let { m ->
        GlassDialog(onDismissRequest = { moveTarget = null }) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("移动到分类", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    m.text.lineSequence().firstOrNull { it.isNotBlank() }?.take(30).orEmpty()
                        .ifBlank { "无标题笔记" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                folders.forEach { f ->
                    val selected = folderMap[m.id] == f.id
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                vm.assignFolder(m.id, f.id)
                                moveTarget = null
                            }
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            tint = if (selected) AmberPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            f.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        if (selected) {
                            Text("当前", style = MaterialTheme.typography.labelSmall, color = AmberPrimary)
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            vm.assignFolder(m.id, null)
                            moveTarget = null
                        }
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Text("移到「未分类」", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/**
 * v1.0.6.9：分类头部卡 —— 图标 + 名称 + 篇数 + 展开箭头 + 管理入口。
 * [folder] 为空表示「未分类」段（无管理入口）。
 */
@Composable
private fun FolderHeaderCard(
    folder: NoteFolder?,
    name: String = folder?.name.orEmpty(),
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onManage: (() -> Unit)? = null
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggle
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(AmberPrimary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    tint = AmberPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "$count 篇笔记",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onManage != null) {
                IconButton(onClick = onManage, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = "管理分类",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).rotate(if (expanded) 90f else 0f)
            )
        }
    }
}

/** v1.0.6.9：分类内的「＋ 新建笔记」行（新笔记直接归入该分类） */
@Composable
private fun NewNoteInFolderRow(folderName: String, onClick: () -> Unit) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AmberPrimary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    tint = AmberPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                "在「$folderName」里写一篇",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * v1.0.6.9：新建笔记入口卡（落入「未分类」）。
 * 点击直接进入完整编辑页 —— 标题输入 + 格式化工具栏 + 正文区一步到位，
 * 不再在列表页内联输入（内联框没有工具栏，格式根本用不了）。
 */
@Composable
private fun NewNoteEntry(onClick: () -> Unit) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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

/** v1.0.6.9：新建分类入口卡（「先建分类，再建笔记」的第一入口） */
@Composable
private fun NewFolderEntry(onClick: () -> Unit) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(AmberPrimary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    tint = AmberPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "新建分类",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "先建文件夹，再把笔记归进去",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "＋",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AmberPrimary
            )
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
    onDelete: () -> Unit,
    /** v1.0.6.9：移动到其它分类 */
    onMove: () -> Unit = {}
) {
    val plainLines = markdownToPlain(markdown).lines().filter { it.isNotBlank() }
    val title = plainLines.firstOrNull()?.take(60).orEmpty().ifBlank {
        text.lineSequence().firstOrNull { it.isNotBlank() }?.take(60).orEmpty()
    }.ifBlank { "无标题笔记" }
    val preview = plainLines.drop(1).joinToString(" ").take(180)

    AppCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
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
                // v1.0.6.9：移动到分类（文件夹层级的新操作）
                IconButton(onClick = onMove, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.FolderOpen,
                        contentDescription = "移动到分类",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
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
