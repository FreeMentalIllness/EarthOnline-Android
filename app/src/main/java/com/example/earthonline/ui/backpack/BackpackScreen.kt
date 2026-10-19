package com.example.earthonline.ui.backpack

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import java.io.File
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.earthonline.data.local.entity.BagCategoryEntity
import com.example.earthonline.data.local.entity.CollectionEntity
import com.example.earthonline.data.local.entity.ItemEntity
import com.example.earthonline.data.local.entity.SCOPE_COLLECTION
import com.example.earthonline.data.local.entity.SCOPE_ITEM
import com.example.earthonline.data.model.CollectionFileMeta
import com.example.earthonline.ui.components.AnimatedAlertDialog
import com.example.earthonline.ui.components.EmptyHint
import com.example.earthonline.ui.components.EmptyState
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.components.SettingsIconButton
import com.example.earthonline.ui.components.UiDimens
import com.example.earthonline.ui.theme.AmberPrimary
import kotlinx.serialization.json.Json

@Composable
fun BackpackRoute(
    startTab: Int = 0,
    moreActions: MoreMenuActions,
    vm: BackpackViewModel = hiltViewModel()
) {
    BackpackScreen(vm, moreActions, startTab)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackpackScreen(
    vm: BackpackViewModel,
    moreActions: MoreMenuActions,
    startTab: Int = 0
) {
    var tab by remember { mutableStateOf(startTab) }
    val items by vm.items.collectAsStateWithLifecycle()
    val collections by vm.collections.collectAsStateWithLifecycle()
    val itemCats by vm.itemCategories.collectAsStateWithLifecycle()
    val collectionCats by vm.collectionCategories.collectAsStateWithLifecycle()
    val catError by vm.categoryError.collectAsStateWithLifecycle()
    var showItem by remember { mutableStateOf(false) }
    var showCollection by remember { mutableStateOf(false) }
    var showCategoryManager by remember { mutableStateOf(false) }
    // v1.0.4：编辑已有条目（null = 未在编辑）。点卡片进入编辑，保存即生效。
    var editItem by remember { mutableStateOf<ItemEntity?>(null) }
    var editCollection by remember { mutableStateOf<CollectionEntity?>(null) }
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("背包") },
                actions = {
                    // v1.2.3：分类管理入口移到右上角，与筛选条解耦，避免「筛选」与「管理」入口重叠
                    IconButton(onClick = { showCategoryManager = true }) {
                        Icon(Icons.Filled.Tune, contentDescription = "分类管理")
                    }
                    SettingsIconButton(actions = moreActions)
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (tab == 0) showItem = true else showCollection = true },
                containerColor = AmberPrimary
            ) { Icon(Icons.Filled.Add, contentDescription = "新增") }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabToggle(selected = tab, onSelect = { tab = it })
            if (tab == 0) {
                ItemsPane(
                    items = items,
                    categories = itemCats,
                    vm = vm,
                    // 空状态里的「添加第一件物品」要能打开父级的新增对话框
                    onAddItem = { showItem = true },
                    // v1.0.4：点击物品卡片进入编辑
                    onEditItem = { editItem = it }
                )
            } else {
                CollectionsPane(
                    collections = collections,
                    categories = collectionCats,
                    vm = vm,
                    context = context,
                    onAddCollection = { showCollection = true },
                    onEditCollection = { editCollection = it }
                )
            }
        }
    }

    if (showItem) {
        ItemDialog(
            categories = itemCats,
            onCreateCategory = { vm.addCategory(SCOPE_ITEM, it) },
            onDismiss = { showItem = false },
            onSubmit = { n, d, c -> vm.addItem(n, "", d, c); showItem = false }
        )
    }
    // v1.0.4：编辑物品（复用新增对话框，预填当前值）
    editItem?.let { target ->
        ItemDialog(
            initial = target,
            categories = itemCats,
            onCreateCategory = { vm.addCategory(SCOPE_ITEM, it) },
            onDismiss = { editItem = null },
            onSubmit = { n, d, c -> vm.updateItem(target, n, "", d, c); editItem = null }
        )
    }
    if (showCollection) {
        CollectionDialog(
            categories = collectionCats,
            onCreateCategory = { vm.addCategory(SCOPE_COLLECTION, it) },
            onDismiss = { showCollection = false },
            onSubmit = { t, n, c, uri -> vm.addCollection(t, n, c, uri); showCollection = false }
        )
    }
    // v1.0.4：编辑收藏（附件保持原样，标题/备注/分类可改）
    editCollection?.let { target ->
        CollectionDialog(
            initial = target,
            categories = collectionCats,
            onCreateCategory = { vm.addCategory(SCOPE_COLLECTION, it) },
            onDismiss = { editCollection = null },
            onSubmit = { t, n, c, _ -> vm.updateCollection(target, t, n, c); editCollection = null }
        )
    }
    if (showCategoryManager) {
        val scope = if (tab == 0) SCOPE_ITEM else SCOPE_COLLECTION
        val cats = if (tab == 0) itemCats else collectionCats
        CategoryManagerDialog(
            title = if (tab == 0) "物品分类管理" else "收藏分类管理",
            categories = cats,
            error = catError,
            onDismiss = { showCategoryManager = false; vm.clearCategoryError() },
            onAdd = { vm.addCategory(scope, it) },
            onRename = vm::renameCategory,
            onDelete = vm::deleteCategory
        )
    }
}

// ------------------------------ 物品 ------------------------------

@Composable
private fun ItemsPane(
    items: List<ItemEntity>,
    categories: List<BagCategoryEntity>,
    vm: BackpackViewModel,
    onAddItem: () -> Unit,
    onEditItem: (ItemEntity) -> Unit
) {
    // 顶部模糊搜索（名称 / 描述 / 分类）
    var query by remember { mutableStateOf("") }
    // null=全部，""=未分类，其它=分类名
    var cat by remember { mutableStateOf<String?>(null) }
    val filtered = remember(items, query, cat) {
        items.filter { it ->
            val matchCat = when (cat) {
                null -> true
                "" -> it.category.isNullOrBlank()
                else -> it.category == cat
            }
            val matchQuery = query.isBlank() ||
                    it.name.contains(query, true) ||
                    (it.description ?: "").contains(query, true) ||
                    (it.category ?: "").contains(query, true)
            matchCat && matchQuery
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("搜索名称 / 描述 / 分类") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
                .padding(UiDimens.ListPad, UiDimens.ListPad, UiDimens.ListPad, 0.dp)
        )
        CategoryFilterRow(
            categories = categories,
            selected = cat,
            onSelect = { cat = it }
        )
        if (filtered.isEmpty()) {
            if (items.isEmpty()) {
                EmptyState(
                    emoji = "🎒",
                    title = "背包还是空的",
                    message = "把你拥有的、想留下的东西登记进来：一台相机、一本读到一半的书、一张还没用的券。" +
                        "以后翻背包就像翻自己的人生清单。",
                    actionLabel = "添加第一件物品",
                    onAction = onAddItem
                )
            } else {
                EmptyHint(if (query.isBlank()) "该分类下暂无物品" else "没有匹配的物品", emoji = "🔍")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = UiDimens.ListPad),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(
                    filtered,
                    key = { it.id },
                    contentType = { "itemCard" }
                ) { Box(Modifier.animateItem()) { ItemCard(it, vm::deleteItem, onEdit = { onEditItem(it) }) } }
            }
        }
    }
}

@Composable
private fun ItemCard(item: ItemEntity, onDelete: (String) -> Unit, onEdit: () -> Unit) {
    // v1.0.4：整卡可点进入编辑（删除按钮自身消费点击，不会误触）
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
        shape = RoundedCornerShape(UiDimens.ItemRadius)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.category?.takeIf { it.isNotBlank() }?.let {
                        AssistChip(onClick = {}, label = { Text(it) })
                    }
                }
                if (!item.description.isNullOrBlank()) {
                    // v1.0.3：故事卡展示 —— 带书签图标，读起来像一句纪念
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("📖", style = MaterialTheme.typography.labelSmall)
                        Text(
                            item.description!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
            IconButton(onClick = { onDelete(item.id) }) { Icon(Icons.Filled.Delete, contentDescription = "删除") }
        }
    }
}

// ------------------------------ 收藏 ------------------------------

@Composable
private fun CollectionsPane(
    collections: List<CollectionEntity>,
    categories: List<BagCategoryEntity>,
    vm: BackpackViewModel,
    context: Context,
    onAddCollection: () -> Unit,
    onEditCollection: (CollectionEntity) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf<String?>(null) }
    val filtered = remember(collections, query, cat) {
        collections.filter { c ->
            val matchCat = when (cat) {
                null -> true
                "" -> c.category.isNullOrBlank()
                else -> c.category == cat
            }
            val matchQuery = query.isBlank() ||
                    c.title.contains(query, true) || (c.note ?: "").contains(query, true)
            matchCat && matchQuery
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("搜索标题 / 备注") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
                .padding(UiDimens.ListPad, UiDimens.ListPad, UiDimens.ListPad, 0.dp)
        )
        CategoryFilterRow(
            categories = categories,
            selected = cat,
            onSelect = { cat = it }
        )
        if (filtered.isEmpty()) {
            if (collections.isEmpty()) {
                EmptyState(
                    emoji = "📚",
                    title = "收藏夹空着呢",
                    message = "值得回看的东西都往这儿放：一篇好文章、一段聊天记录、一张截图，还能附上原文件。",
                    actionLabel = "添加第一条收藏",
                    onAction = onAddCollection
                )
            } else {
                EmptyHint("没有匹配的收藏", emoji = "🔍")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = UiDimens.ListPad),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(
                    filtered,
                    key = { it.id },
                    contentType = { "collectionCard" }
                ) { Box(Modifier.animateItem()) { CollectionCard(it, vm, context, onEdit = { onEditCollection(it) }) } }
            }
        }
    }
}

@Composable
private fun CollectionCard(c: CollectionEntity, vm: BackpackViewModel, context: Context, onEdit: () -> Unit) {
    val meta = remember(c.fileMetaJson) {
        c.fileMetaJson?.let { runCatching { Json.decodeFromString(CollectionFileMeta.serializer(), it) }.getOrNull() }
    }
    // v1.0.6：收藏封面预览 —— 图片直接出缩略图，文本文件自动读取前几行排版，无预览能力的类型给图标兜底。
    val file = remember(c.fileUri) { c.fileUri?.let { File(context.filesDir, it) } }
    val previewText = remember(c.id) {
        file?.takeIf { it.exists() && (meta?.mime?.startsWith("text/") == true || it.extension in setOf("txt", "md", "json", "csv", "log")) }
            ?.runCatching { readLines().take(3).joinToString("\n") }?.getOrNull()
    }
    // v1.0.4：整卡可点进入编辑（删除 / 打开按钮各自消费点击，不会误触）
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
        shape = RoundedCornerShape(UiDimens.ItemRadius)
    ) {
        Column(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (file != null) {
                Box(
                    Modifier.fillMaxWidth().height(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    when {
                        meta?.mime?.startsWith("image/") == true -> {
                            AsyncImage(
                                model = file,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        previewText != null -> {
                            Text(
                                previewText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp),
                                maxLines = 5,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        else -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Description, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(c.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.deleteCollection(c.id) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除")
                }
            }
            c.category?.takeIf { it.isNotBlank() }?.let {
                AssistChip(onClick = {}, label = { Text(it) })
            }
            if (!c.note.isNullOrBlank()) {
                Text(c.note!!, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (meta != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Description, null, Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(meta.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(formatSize(meta.size), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = {
                        val intent = vm.getOpenIntent(c)
                        if (intent != null) {
                            try { context.startActivity(intent) }
                            catch (e: Exception) {
                                Toast.makeText(context, "没有可打开此文件的应用", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "文件不存在", Toast.LENGTH_SHORT).show()
                        }
                    }) { Icon(Icons.Filled.OpenInNew, contentDescription = "打开") }
                }
            }
        }
    }
}

// ------------------------------ 对话框 ------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemDialog(
    categories: List<BagCategoryEntity>,
    onCreateCategory: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (name: String, desc: String?, category: String?) -> Unit,
    initial: ItemEntity? = null
) {
    // v1.0.4：initial 非空 = 编辑模式，预填当前值
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var desc by remember { mutableStateOf(initial?.description ?: "") }
    var category by remember { mutableStateOf(initial?.category) }
    // v1.0.5 QA：提交防重（连点保存不再重复落库）
    var submitted by remember { mutableStateOf(false) }
    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    if (submitted || name.isBlank()) return@TextButton
                    submitted = true
                    onSubmit(name.trim(), desc.takeIf { it.isNotBlank() }, category)
                },
                enabled = name.isNotBlank() && !submitted
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
        title = { Text(if (initial == null) "新增物品" else "编辑物品") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                // v1.0.6：取消「实物 / 虚拟」分类，物品不再做类型二分
                // v1.0.3：物品故事卡 —— 让背包更像人生纪念品而不是冷数据
                OutlinedTextField(value = desc, onValueChange = { desc = it },
                    label = { Text("来源 / 故事") },
                    placeholder = { Text("它是怎么来到你身边的？") },
                    modifier = Modifier.fillMaxWidth())
                CategoryPicker(
                    categories = categories,
                    selected = category,
                    onSelect = { category = it },
                    onCreate = onCreateCategory
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionDialog(
    categories: List<BagCategoryEntity>,
    onCreateCategory: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (title: String, note: String?, category: String?, fileUri: Uri?) -> Unit,
    initial: CollectionEntity? = null
) {
    // v1.0.4：initial 非空 = 编辑模式（附件只读展示，不提供替换）
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var category by remember { mutableStateOf(initial?.category) }
    var fileUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    // v1.0.5 QA：提交防重（连点保存不再重复落库）
    var submitted by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            fileUri = it
            fileName = fileNameFromUri(context, it)
        }
    }
    val initialMeta = remember(initial?.fileMetaJson) {
        initial?.fileMetaJson?.let {
            runCatching { Json.decodeFromString(CollectionFileMeta.serializer(), it) }.getOrNull()
        }
    }
    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    if (submitted || title.isBlank()) return@TextButton
                    submitted = true
                    onSubmit(title.trim(), note.takeIf { it.isNotBlank() }, category, fileUri)
                },
                enabled = title.isNotBlank() && !submitted
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
        title = { Text(if (initial == null) "新增收藏" else "编辑收藏") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("标题") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("备注") },
                    modifier = Modifier.fillMaxWidth())
                CategoryPicker(
                    categories = categories,
                    selected = category,
                    onSelect = { category = it },
                    onCreate = onCreateCategory
                )
                if (initial == null) {
                    OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.AttachFile, null)
                        Spacer(Modifier.width(8.dp))
                        Text(fileName ?: "选择文件附件（可选）")
                    }
                } else {
                    // 编辑模式：附件不可替换（替换涉及旧文件清理，误操作风险高），只读展示
                    Text(
                        if (initialMeta != null) "附件：${initialMeta.name}（保持不变）" else "未附带文件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}

private fun fileNameFromUri(context: Context, uri: Uri): String? {
    val c = context.contentResolver.query(uri, null, null, null, null)
    c?.use {
        val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (it.moveToFirst() && idx >= 0) return it.getString(idx)
    }
    return null
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

/**
 * v1.2.3：物品 / 收藏 切换由 Material 顶部 Tab 改为「胶囊分段控件」。
 * 原来两个 Tab 之间有一条贯穿的指示线，视觉上把页面「切」成两半；
 * 现在用同一颗药丸里两段等分的按铃，选中段填主题暖色，整体是一个连续控件，
 * 切换时不再有「分割感」。
 */
@Composable
private fun TabToggle(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = UiDimens.ListPad, vertical = 8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
            TabToggleButton("物品", selected = selected == 0) { onSelect(0) }
            TabToggleButton("收藏", selected = selected == 1) { onSelect(1) }
        }
    }
}

@Composable
private fun RowScope.TabToggleButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.weight(1f).height(38.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
