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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Backpack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocalLibrary
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.earthonline.ui.components.UiDimens
import com.example.earthonline.ui.theme.AmberPrimary
import com.example.earthonline.ui.theme.GlassCard
import com.example.earthonline.ui.theme.GlassSurface
import com.example.earthonline.ui.theme.pressScaleClickable
import com.example.earthonline.ui.theme.pressScaleCombinedClickable
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
    // v1.0.8：单击收藏 = 直接打开/预览；无附件的收藏则弹预览卡。长按 = 进入编辑。
    var previewCollection by remember { mutableStateOf<CollectionEntity?>(null) }
    val context = LocalContext.current

    /** 单击收藏：有附件直接调起系统打开；无附件（纯文字/笔记）则弹预览卡 */
    fun openCollection(c: CollectionEntity) {
        val intent = vm.getOpenIntent(c)
        if (intent != null) {
            try { context.startActivity(intent) }
            catch (e: Exception) {
                Toast.makeText(context, "没有可打开此文件的应用", Toast.LENGTH_SHORT).show()
            }
        } else {
            previewCollection = c
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("背包") },
                // v1.0.11：Header 透明化，与主页一致透出壁纸，去掉顶部悬浮覆盖感
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    // v1.2.3：分类管理入口移到右上角，与筛选条解耦，避免「筛选」与「管理」入口重叠
                    IconButton(onClick = { showCategoryManager = true }) {
                        Icon(Icons.Rounded.Tune, contentDescription = "分类管理")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (tab == 0) showItem = true else showCollection = true },
                containerColor = AmberPrimary,
                // v1.0.6 封板：统一圆形，跟主页 AI 按钮一个形状语言
                shape = CircleShape
            ) { Icon(Icons.Rounded.Add, contentDescription = "新增") }
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
                    onOpenCollection = { openCollection(it) },
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
            // v1.0.8：编辑态内允许「替换文件」—— 选了新文件就替换，不选则沿用原附件
            onSubmit = { t, n, c, uri -> vm.replaceCollectionFile(target, t, n, c, uri); editCollection = null }
        )
    }
    // v1.0.8：单击无附件的收藏 → 预览卡（含打开文件 / 编辑入口）
    previewCollection?.let { target ->
        CollectionPreviewDialog(
            c = target,
            vm = vm,
            context = context,
            onDismiss = { previewCollection = null },
            onOpen = { previewCollection = null; openCollection(target) },
            onEdit = { previewCollection = null; editCollection = target }
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
    // v1.0.7：网格（带预览/大图）/ 列表（纯文字，一页更多）双模式
    var grid by rememberSaveable { mutableStateOf(false) }
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
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth()
                .padding(UiDimens.ListPad, UiDimens.ListPad, UiDimens.ListPad, 0.dp)
        )
        CategoryFilterRow(
            categories = categories,
            selected = cat,
            onSelect = { cat = it }
        )
        // v1.0.7：网格 / 列表视图切换
        ViewModeToggle(grid = grid, onToggle = { grid = !grid })
        if (filtered.isEmpty()) {
            if (items.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.Backpack,
                    title = "背包还是空的",
                    message = "把你拥有的、想留下的东西登记进来：一台相机、一本读到一半的书、一张还没用的券。" +
                        "以后翻背包就像翻自己的人生清单。",
                    actionLabel = "添加第一件物品",
                    onAction = onAddItem
                )
            } else {
                EmptyHint(if (query.isBlank()) "该分类下暂无物品" else "没有匹配的物品", icon = Icons.Rounded.Search)
            }
        } else if (grid) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(horizontal = UiDimens.ListPad),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                // v1.0.7：底部预留 88dp —— 右下角悬浮「+」不再遮挡列表最后一项
                contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
            ) {
                gridItems(
                    filtered,
                    key = { it.id },
                    contentType = { "itemTile" }
                ) { ItemGridTile(it, onEdit = { onEditItem(it) }, onDelete = { vm.deleteItem(it.id) }) }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = UiDimens.ListPad),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                // v1.0.7：底部预留 88dp —— 右下角悬浮「+」不再遮挡列表最后一项
                contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
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

/** v1.0.7：背包网格模式下的物品磁贴（大图标 + 名称 + 分类，一眼扫更多） */
@Composable
private fun ItemGridTile(item: ItemEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().pressScaleClickable(onClick = onEdit),
        shape = RoundedCornerShape(UiDimens.CardRadius)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "删除",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Box(
                Modifier.fillMaxWidth().height(64.dp),
                contentAlignment = Alignment.Center
            ) {
                // v1.0.17：📦 emoji → Inventory2 圆角矢量
                Icon(
                    Icons.Rounded.Inventory2,
                    contentDescription = null,
                    tint = AmberPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                item.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            item.category?.takeIf { it.isNotBlank() }?.let {
                AssistChip(
                    onClick = {},
                    label = { Text(it, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    }
}

/** v1.0.7：网格 / 列表视图切换按钮（物品与收藏共用） */
@Composable
private fun ViewModeToggle(grid: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(
            onClick = onToggle,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            // v1.0.17：☰/⊞ 字形 → ViewList / GridView 圆角矢量
            Icon(
                if (grid) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(if (grid) "列表模式" else "网格模式", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ItemCard(item: ItemEntity, onDelete: (String) -> Unit, onEdit: () -> Unit) {
    // v1.0.4：整卡可点进入编辑（删除按钮自身消费点击，不会误触）
    // v1.0.7：按压缩放反馈（94% -> 100% spring 弹回）
    GlassCard(
        modifier = Modifier.fillMaxWidth().pressScaleClickable(onClick = onEdit),
        shape = RoundedCornerShape(UiDimens.CardRadius)
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
                        // v1.0.17：📖 emoji → MenuBook 圆角矢量
                        Icon(
                            Icons.Rounded.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
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
            IconButton(onClick = { onDelete(item.id) }) { Icon(Icons.Rounded.Delete, contentDescription = "删除") }
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
    onOpenCollection: (CollectionEntity) -> Unit,
    onEditCollection: (CollectionEntity) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf<String?>(null) }
    // v1.0.7：网格（带封面预览）/ 列表（纯文字）双模式
    var grid by rememberSaveable { mutableStateOf(false) }
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
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true,
            // v1.0.19（死命令 2）：收藏搜索框圆角与「物品」页统一为两端半圆胶囊形
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth()
                .padding(UiDimens.ListPad, UiDimens.ListPad, UiDimens.ListPad, 0.dp)
        )
        CategoryFilterRow(
            categories = categories,
            selected = cat,
            onSelect = { cat = it }
        )
        ViewModeToggle(grid = grid, onToggle = { grid = !grid })
        if (filtered.isEmpty()) {
            if (collections.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.LocalLibrary,
                    title = "收藏夹空着呢",
                    message = "值得回看的东西都往这儿放：一篇好文章、一段聊天记录、一张截图，还能附上原文件。",
                    actionLabel = "添加第一条收藏",
                    onAction = onAddCollection
                )
            } else {
                EmptyHint("没有匹配的收藏", icon = Icons.Rounded.Search)
            }
        } else if (grid) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(horizontal = UiDimens.ListPad),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                // v1.0.7：底部预留 88dp —— 右下角悬浮「+」不再遮挡列表最后一项
                contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
            ) {
                gridItems(
                    filtered,
                    key = { it.id },
                    contentType = { "collectionTile" }
                ) { CollectionGridTile(it, vm, context, onOpen = { onOpenCollection(it) }, onEdit = { onEditCollection(it) }) }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = UiDimens.ListPad),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                // v1.0.7：底部预留 88dp —— 右下角悬浮「+」不再遮挡列表最后一项
                contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
            ) {
                items(
                    filtered,
                    key = { it.id },
                    contentType = { "collectionCard" }
                ) { Box(Modifier.animateItem()) { CollectionCard(it, vm, context, onOpen = { onOpenCollection(it) }, onEdit = { onEditCollection(it) }) } }
            }
        }
    }
}

/**
 * v1.0.7：收藏网格磁贴 —— 顶部封面预览（PDF 首页 / 音频内嵌封面 / 图片缩略图），
 * 无封面能力时留白显示默认图标，绝不报错。
 */
@Composable
private fun CollectionGridTile(
    c: CollectionEntity,
    vm: BackpackViewModel,
    context: Context,
    onOpen: () -> Unit,
    onEdit: () -> Unit
) {
    val meta = remember(c.fileMetaJson) {
        c.fileMetaJson?.let { runCatching { Json.decodeFromString(CollectionFileMeta.serializer(), it) }.getOrNull() }
    }
    val file = remember(c.fileUri) { c.fileUri?.let { File(context.filesDir, it) } }
    val coverBitmap by produceState<android.graphics.Bitmap?>(initialValue = null, c.id, file?.absolutePath, meta?.mime) {
        value = withContext(Dispatchers.IO) { extractCover(context, file, meta?.mime) }
    }
    val coverImage = remember(coverBitmap) { coverBitmap?.asImageBitmap() }
    // v1.0.8：单击打开/预览，长按进入编辑
    GlassCard(
        modifier = Modifier.fillMaxWidth().pressScaleCombinedClickable(onClick = onOpen, onLongClick = onEdit),
        shape = RoundedCornerShape(UiDimens.CardRadius)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier.fillMaxWidth().height(96.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                when {
                    meta?.mime?.startsWith("image/") == true && file != null -> {
                        AsyncImage(
                            model = file,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    coverImage != null -> {
                        Image(
                            bitmap = coverImage,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Description,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Text(
                c.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            meta?.let {
                Text(
                    formatSize(it.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * v1.0.7：智能封面提取。
 *  - PDF：渲染第一页为位图（白底）；
 *  - 音频：提取内嵌封面（ID3 / APIC）；
 *  - 其它类型或提取失败：返回 null，调用方用默认图标兜底，绝不抛异常。
 */
private fun extractCover(context: Context, file: File?, mime: String?): android.graphics.Bitmap? {
    if (file == null || !file.exists()) return null
    val isPdf = mime?.endsWith("pdf", ignoreCase = true) == true || file.extension.equals("pdf", true)
    val isAudio = mime?.startsWith("audio/") == true ||
            file.extension.lowercase() in setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav")
    return runCatching {
        when {
            isPdf -> {
                android.os.ParcelFileDescriptor
                    .open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                    .use { pfd ->
                        android.graphics.pdf.PdfRenderer(pfd).use { renderer ->
                            if (renderer.pageCount <= 0) return@use null
                            renderer.openPage(0).use { page ->
                                val pw = page.width.coerceAtLeast(1)
                                val ph = page.height.coerceAtLeast(1)
                                val w = 600.coerceAtMost(pw)
                                val h = (w.toFloat() * ph / pw).toInt().coerceAtLeast(1)
                                val bmp = android.graphics.Bitmap.createBitmap(
                                    w, h, android.graphics.Bitmap.Config.ARGB_8888
                                )
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                page.render(
                                    bmp,
                                    null,
                                    null,
                                    android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                                )
                                bmp
                            }
                        }
                    }
            }
            isAudio -> {
                val mmr = android.media.MediaMetadataRetriever()
                try {
                    mmr.setDataSource(file.absolutePath)
                    val art = mmr.embeddedPicture
                    if (art != null) {
                        android.graphics.BitmapFactory.decodeByteArray(art, 0, art.size)
                    } else null
                } finally {
                    runCatching { mmr.release() }
                }
            }
            else -> null
        }
    }.getOrNull()
}

@Composable
private fun CollectionCard(c: CollectionEntity, vm: BackpackViewModel, context: Context, onOpen: () -> Unit, onEdit: () -> Unit) {
    val meta = remember(c.fileMetaJson) {
        c.fileMetaJson?.let { runCatching { Json.decodeFromString(CollectionFileMeta.serializer(), it) }.getOrNull() }
    }
    // v1.0.6：收藏封面预览 —— 图片直接出缩略图，文本文件自动读取前几行排版，无预览能力的类型给图标兜底。
    // v1.0.7：PDF 渲染首页、音频提取内嵌封面（extractCover），后台线程跑，失败一律兜底图标。
    val file = remember(c.fileUri) { c.fileUri?.let { File(context.filesDir, it) } }
    val previewText = remember(c.id) {
        file?.takeIf { it.exists() && (meta?.mime?.startsWith("text/") == true || it.extension in setOf("txt", "md", "json", "csv", "log")) }
            ?.runCatching { readLines().take(3).joinToString("\n") }?.getOrNull()
    }
    val coverBitmap by produceState<android.graphics.Bitmap?>(initialValue = null, c.id, file?.absolutePath, meta?.mime) {
        value = withContext(Dispatchers.IO) { extractCover(context, file, meta?.mime) }
    }
    val coverImage = remember(coverBitmap) { coverBitmap?.asImageBitmap() }
    // v1.0.8：单击整卡 = 打开/预览，长按 = 进入编辑；删除按钮各自消费点击不会误触
    GlassCard(
        modifier = Modifier.fillMaxWidth().pressScaleCombinedClickable(onClick = onOpen, onLongClick = onEdit),
        shape = RoundedCornerShape(UiDimens.CardRadius)
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
                        coverImage != null -> {
                            Image(
                                bitmap = coverImage,
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
                                Icon(Icons.Rounded.Description, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(c.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.deleteCollection(c.id) }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "删除")
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
                // 附件信息（文件名 + 大小）；整卡单击即打开，无需单独的打开按钮
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.Description, null, Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(meta.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(formatSize(meta.size), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                // 新增与编辑都允许选择 / 替换文件附件：选了新文件即替换，不选则保留原附件。
                // v1.0.8：编辑态必须提供「上传/替换文件」入口。
                OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.AttachFile, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            fileName != null -> "替换附件：$fileName"
                            initialMeta != null -> "替换文件附件（当前：${initialMeta.name}）"
                            else -> "选择文件附件（可选）"
                        }
                    )
                }
                if (initialMeta != null && fileName == null) {
                    Text(
                        "留空则保留当前附件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}

/**
 * v1.0.8：单击「无附件」的收藏时弹出的预览卡 —— 展示封面/备注/附件信息，
 * 并提供「打开文件」（有附件时）与「编辑」入口。单击有附件的收藏则直接调起系统打开，不弹此卡。
 */
@Composable
private fun CollectionPreviewDialog(
    c: CollectionEntity,
    vm: BackpackViewModel,
    context: Context,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onEdit: () -> Unit
) {
    val meta = remember(c.fileMetaJson) {
        c.fileMetaJson?.let { runCatching { Json.decodeFromString(CollectionFileMeta.serializer(), it) }.getOrNull() }
    }
    val file = remember(c.fileUri) { c.fileUri?.let { File(context.filesDir, it) } }
    val coverBitmap by produceState<android.graphics.Bitmap?>(initialValue = null, c.id, file?.absolutePath, meta?.mime) {
        value = withContext(Dispatchers.IO) { extractCover(context, file, meta?.mime) }
    }
    val coverImage = remember(coverBitmap) { coverBitmap?.asImageBitmap() }
    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onEdit) { Text("编辑") } },
        dismissButton = { TextButton(onDismiss) { Text("关闭") } },
        title = { Text(c.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (file != null) {
                    Box(
                        Modifier.fillMaxWidth().height(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        when {
                            meta?.mime?.startsWith("image/") == true -> AsyncImage(
                                model = file, contentDescription = null,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                            )
                            coverImage != null -> Image(
                                bitmap = coverImage, contentDescription = null,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                            )
                            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Description, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (!c.note.isNullOrBlank()) {
                    Text(c.note!!, style = MaterialTheme.typography.bodyMedium)
                }
                meta?.let {
                    Text(
                        "附件：${it.name} · ${formatSize(it.size)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (file != null) {
                    OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.OpenInNew, null)
                        Spacer(Modifier.width(8.dp))
                        Text("打开文件")
                    }
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
    // v1.0.11：切换 Tab 改用液态玻璃材质（与主页卡片统一），选中态仍用主色胶囊
    GlassSurface(
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
