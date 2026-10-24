@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.example.earthonline.ui.album

import com.example.earthonline.ui.components.bouncyPress

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoLibrary
import com.example.earthonline.ui.components.IconChip
import com.example.earthonline.ui.components.ModuleTints
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.earthonline.data.photos.MemoryPhoto
import com.example.earthonline.data.photos.PhotoAlbum
import com.example.earthonline.ui.components.AnimatedAlertDialog
import com.example.earthonline.ui.components.EmptyState
import com.example.earthonline.ui.components.GlassTextField
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.theme.AmberPrimary
import kotlinx.coroutines.launch
import java.io.File
import com.example.earthonline.ui.theme.GlassCard

/**
 * 记忆相册（v1.0.6 大改）：
 *  - 自定义相册分类（DataStore 扩展，零 Room 迁移）；
 *  - 「堆叠 + 3D 翻页」卡片组浏览，点按/左右翻看；
 *  - 详情页：补录「当年的话」、设为壁纸、唤起系统相册替换图像、归入/移出相册、删除；
 *  - 右下角批量导入（仍保留原图完整复制，画质红线）。
 */
@Composable
fun AlbumRoute(moreActions: MoreMenuActions, vm: AlbumViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val photos by vm.photos.collectAsStateWithLifecycle(initialValue = emptyList())
    val albums by vm.albums.collectAsStateWithLifecycle(initialValue = emptyList())
    val allPhotos by vm.allPhotos.collectAsStateWithLifecycle(initialValue = emptyList())
    val selectedAlbumId by vm.selectedAlbumId.collectAsStateWithLifecycle(initialValue = null)
    val importing by vm.importing.collectAsStateWithLifecycle(initialValue = false)

    // 批量选图（Photo Picker 多选，Android 13+ 原生；旧版本回落 GetMultipleContents）
    // v1.0.7：导入到「当前选中相册」—— 新建相册后点加号，照片直接进该相册
    val pickPhotos = rememberLauncherForAlbum { uris -> vm.import(uris, selectedAlbumId) }
    // 单张选图：详情页「替换图片」用 GetContent 取一张
    val replaceUri = remember { mutableStateOf<MemoryPhoto?>(null) }
    val pickReplace = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { replaceUri.value?.let { p -> vm.replacePhoto(p.id, it) } }
        replaceUri.value = null
    }

    var detailTarget by remember { mutableStateOf<MemoryPhoto?>(null) }
    var assignTarget by remember { mutableStateOf<MemoryPhoto?>(null) }
    var deckIndex by remember { mutableStateOf(0) }
    var showAlbumManage by remember { mutableStateOf(false) }
    var newAlbumName by remember { mutableStateOf("") }
    var manageAlbum by remember { mutableStateOf<PhotoAlbum?>(null) }
    var snackbarMsg by remember { mutableStateOf<String?>(null) }

    // v1.0.7：全览网格模式 + 批量管理
    var gridMode by remember { mutableStateOf(false) }
    var batchMode by remember { mutableStateOf(false) }
    val batchSel = remember { mutableStateOf(setOf<String>()) }
    var batchAssign by remember { mutableStateOf(false) }
    var batchDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(snackbarMsg) {
        snackbarMsg?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            snackbarMsg = null
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    // v1.0.6 封板：标题里的相机 Emoji → 圆角矢量图标芯片（与设置页同源）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconChip(
                            Icons.Rounded.PhotoLibrary,
                            chipColor = ModuleTints.Album,
                            chipSize = 30.dp,
                            iconSize = 18.dp
                        )
                        Text("记忆相册")
                    }
                },
                actions = {
                    // v1.0.7：全览网格 / 3D 翻页 视图切换
                    TextButton(onClick = {
                        gridMode = !gridMode
                        batchMode = false
                        batchSel.value = emptySet()
                    }) {
                        Text(if (gridMode) "翻页" else "网格", style = MaterialTheme.typography.labelMedium)
                    }
                    // v1.0.7：批量管理（仅网格态提供）
                    if (gridMode) {
                        TextButton(onClick = {
                            batchMode = !batchMode
                            batchSel.value = emptySet()
                        }) {
                            Text(
                                if (batchMode) "退出" else "管理",
                                style = MaterialTheme.typography.labelMedium,
                                color = AmberPrimary
                            )
                        }
                    }
                    IconButton(onClick = { showAlbumManage = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "管理相册")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { pickPhotos() },
                containerColor = AmberPrimary,
                shape = CircleShape,
                modifier = Modifier.bouncyPress()
            ) {
                if (importing) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                else Icon(Icons.Rounded.Add, contentDescription = "批量导入照片")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 相册分类选择条：全部 + 各相册（显示张数）+ 新建
            AlbumChipRow(
                albums = albums,
                allCount = allPhotos.size,
                selectedId = selectedAlbumId,
                onSelect = { vm.selectAlbum(it) },
                onCreate = { name ->
                    // v1.0.7：新建后立即选中该相册，随后点右下角 + 即把照片导入此相册
                    vm.createAlbum(name) { album ->
                        vm.selectAlbum(album.id)
                        snackbarMsg = "已创建并进入「${album.name}」，点右下角即可加图"
                    }
                }
            )

            if (photos.isEmpty()) {
                Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    if (allPhotos.isEmpty()) {
                        EmptyState(
                            icon = Icons.Rounded.PhotoLibrary,
                            title = "相册还是空的",
                            message = "点右下角批量导入老照片：自动按拍摄月份归档成相册墙，" +
                                "每一张都能补一句当年的话、设为壁纸、归入自定义相册。"
                        )
                    } else {
                        EmptyState(
                            icon = Icons.Rounded.FolderOpen,
                            title = "这个相册还没有照片",
                            message = "去别的相册看看，或在照片详情里把它归入这个相册。"
                        )
                    }
                }
            } else if (gridMode) {
                // v1.0.7：全览网格模式（含批量管理）
                PhotoGrid(
                    photos = photos,
                    batchMode = batchMode,
                    selectedIds = batchSel.value,
                    onToggleSelect = { id ->
                        batchSel.value =
                            if (id in batchSel.value) batchSel.value - id else batchSel.value + id
                    },
                    onOpen = { detailTarget = it },
                    modifier = Modifier.fillMaxSize().weight(1f)
                )
                if (batchMode) {
                    BatchActionBar(
                        selectedCount = batchSel.value.size,
                        totalCount = photos.size,
                        onSelectAll = {
                            batchSel.value =
                                if (batchSel.value.size == photos.size) emptySet()
                                else photos.map { it.id }.toSet()
                        },
                        onAssign = { batchAssign = true },
                        onDelete = { batchDeleteConfirm = true },
                        onExit = { batchMode = false; batchSel.value = emptySet() }
                    )
                }
            } else {
                // 堆叠 + 3D 翻页卡片组
                PhotoFlipDeck(
                    photos = photos,
                    index = deckIndex,
                    onIndexChange = { deckIndex = it.coerceIn(0, photos.lastIndex) },
                    onDetail = { detailTarget = it },
                    onAssign = { assignTarget = it },
                    modifier = Modifier.fillMaxSize().weight(1f)
                )
                // 底部缩略图条：点选直接跳到那张
                ThumbnailStrip(
                    photos = photos,
                    onPick = { deckIndex = it.coerceIn(0, photos.lastIndex) }
                )
            }
        }
    }

    // 详情页（补录当年的话 / 设为壁纸 / 替换图片 / 归入相册 / 删除）
    detailTarget?.let { photo ->
        AlbumDetailDialog(
            photo = photo,
            albums = albums,
            currentAlbumId = photo.albumId,
            onDismiss = { detailTarget = null },
            onSaveNote = { note -> vm.setNote(photo.id, note); detailTarget = null },
            onSetWallpaper = {
                vm.useAsWallpaper(photo.id) { ok -> snackbarMsg = if (ok) "已设为主页壁纸" else "设置失败" }
                detailTarget = null
            },
            onReplace = { replaceUri.value = photo; pickReplace.launch("image/*") },
            onAssignAlbum = { assignTarget = photo; detailTarget = null },
            onDelete = { vm.delete(photo.id); detailTarget = null }
        )
    }

    // 归入/移出相册
    assignTarget?.let { photo ->
        AssignAlbumDialog(
            albums = albums,
            currentAlbumId = photo.albumId,
            onDismiss = { assignTarget = null },
            onAssign = { albumId ->
                vm.setPhotoAlbum(photo.id, albumId)
                assignTarget = null
            }
        )
    }

    // 相册管理（新建 / 重命名 / 删除）
    if (showAlbumManage) {
        AlbumManageDialog(
            albums = albums,
            onDismiss = { showAlbumManage = false; manageAlbum = null },
            onCreate = { name ->
                vm.createAlbum(name) { album ->
                    vm.selectAlbum(album.id)
                    showAlbumManage = false
                    snackbarMsg = "已创建并进入「${album.name}」，点右下角即可加图"
                }
            },
            onRename = { id, name -> vm.renameAlbum(id, name) },
            onDelete = { id, name ->
                vm.deleteAlbum(id)
                snackbarMsg = "已删除相册「$name」（照片保留，回到未分类）"
            },
            onSelect = { vm.selectAlbum(it); showAlbumManage = false }
        )
    }

    // v1.0.7：批量归入相册
    if (batchAssign) {
        AssignAlbumDialog(
            albums = albums,
            currentAlbumId = null,
            onDismiss = { batchAssign = false },
            onAssign = { albumId ->
                val n = batchSel.value.size
                batchSel.value.forEach { vm.setPhotoAlbum(it, albumId) }
                batchSel.value = emptySet()
                batchAssign = false
                batchMode = false
                snackbarMsg = "已将 $n 张照片${if (albumId == null) "移到未分类" else "归入相册"}"
            }
        )
    }

    // v1.0.7：批量删除确认
    if (batchDeleteConfirm) {
        AnimatedAlertDialog(
            onDismissRequest = { batchDeleteConfirm = false },
            title = { Text("删除 ${batchSel.value.size} 张照片？") },
            text = { Text("照片原图会一并删除，此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    val n = batchSel.value.size
                    batchSel.value.forEach { vm.delete(it) }
                    batchSel.value = emptySet()
                    batchDeleteConfirm = false
                    batchMode = false
                    snackbarMsg = "已删除 $n 张照片"
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton({ batchDeleteConfirm = false }) { Text("取消") } }
        )
    }
}

/**
 * 相册分类选择条：全部照片 + 各相册（张数）+ 新建。
 */
@Composable
private fun AlbumChipRow(
    albums: List<PhotoAlbum>,
    allCount: Int,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onCreate: (String) -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedId == null,
                onClick = { onSelect(null) },
                label = { Text("全部 $allCount") }
            )
        }
        items(albums, key = { it.id }) { album ->
            FilterChip(
                selected = selectedId == album.id,
                onClick = { onSelect(album.id) },
                label = { Text(album.name) },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
        item {
            AssistChip(
                onClick = { showCreate = true },
                label = { Text("＋ 新建相册") },
                leadingIcon = { Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp)) }
            )
        }
    }

    if (showCreate) {
        AnimatedAlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("新建相册") },
            text = {
                GlassTextField(
                    value = name, onValueChange = { name = it.take(24) },
                    label = "相册名称", singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = { onCreate(name.trim()); name = ""; showCreate = false }
                ) { Text("创建") }
            },
            dismissButton = { TextButton({ showCreate = false }) { Text("取消") } }
        )
    }
}

/**
 * 堆叠 + 3D 翻页卡片组。
 * 视觉：前方主图 + 身后 1~2 张缩小偏移的「牌堆」营造厚度；
 * 交互：点击主图开详情、左右箭头/点击空白处翻到上一张/下一张，
 * 翻页用 rotationY 0↔±90° 的 3D 旋转做「翻卡片」观感（cameraDistance 拉开景深）。
 */
@Composable
private fun PhotoFlipDeck(
    photos: List<MemoryPhoto>,
    index: Int,
    onIndexChange: (Int) -> Unit,
    onDetail: (MemoryPhoto) -> Unit,
    onAssign: (MemoryPhoto) -> Unit,
    modifier: Modifier = Modifier
) {
    if (photos.isEmpty()) return
    val safeIndex = index.coerceIn(0, photos.lastIndex)
    val scope = rememberCoroutineScope()
    val flip = remember { Animatable(0f) }
    var flipTarget by remember { mutableStateOf<Int?>(null) }
    var flipping by remember { mutableStateOf(false) }

    fun go(delta: Int) {
        if (photos.size <= 1) { onIndexChange(0); return }
        if (flipping) return
        val target = (safeIndex + delta).mod(photos.size)
        flipTarget = target
        flipping = true
        scope.launch {
            flip.snapTo(0f)
            flip.animateTo(1f, tween(380))
            onIndexChange(target)
            flip.snapTo(0f)
            flipTarget = null
            flipping = false
        }
    }

    val front = photos[safeIndex]
    val back = photos[flipTarget ?: safeIndex]

    Box(modifier.padding(16.dp), contentAlignment = Alignment.Center) {
        // 身后牌堆（厚度感）：取当前之后的第 1、2 张
        repeat(2) { depth ->
            val behindIdx = (safeIndex + depth + 1).mod(photos.size)
            val p = photos[behindIdx]
            val scale = 1f - (depth + 1) * 0.06f
            val offset = (depth + 1) * 14f
            FlipCard(
                photo = p,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale
                        translationX = offset.dp.toPx(); translationY = -offset.dp.toPx()
                        alpha = 0.5f - depth * 0.2f
                        rotationZ = if (depth == 0) -3f else 3f
                    },
                interactionSource = remember { MutableInteractionSource() },
                onClick = {}
            )
        }

        // 翻页中的「进入」牌（从背后翻到正面）
        if (flipping && flipTarget != null) {
            FlipCard(
                photo = back,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    rotationY = 90f * (1f - flip.value)
                    cameraDistance = 12f
                    alpha = flip.value
                },
                interactionSource = remember { MutableInteractionSource() },
                onClick = {}
            )
        }

        // 当前主图（翻页时旋转离场）
        FlipCard(
            photo = front,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                rotationY = -90f * flip.value
                cameraDistance = 12f
                alpha = 1f - flip.value
            },
            interactionSource = remember { MutableInteractionSource() },
            onClick = { onDetail(front) },
            onLongClick = { onAssign(front) }
        )

        // 左右翻页 arrows
        if (photos.size > 1) {
            Row(
                Modifier.fillMaxWidth().align(Alignment.Center),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { go(-1) }) {
                    Icon(Icons.Rounded.ChevronLeft, null, tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(36.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), CircleShape))
                }
                IconButton(onClick = { go(1) }) {
                    Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(36.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), CircleShape))
                }
            }
        }

        // 底部计数 + 提示
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
        ) {
            Text(
                "${safeIndex + 1} / ${photos.size} · 点按看详情，长按归入相册",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun FlipCard(
    photo: MemoryPhoto,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null
) {
    GlassCard(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = File(photo.path),
                contentDescription = photo.note.ifBlank { "照片" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (photo.note.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                ) {
                    Text(
                        photo.note,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

/** 底部缩略图条（快速跳转型预览，非翻页主交互） */
@Composable
private fun ThumbnailStrip(
    photos: List<MemoryPhoto>,
    onPick: (Int) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(photos, key = { it.id }) { p ->
            GlassCard(
                modifier = Modifier.size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onPick(photos.indexOf(p)) },
                shape = RoundedCornerShape(10.dp)
            ) {
                AsyncImage(
                    model = File(p.path),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/** 照片详情页：补录「当年的话」/ 设为壁纸 / 替换图片 / 归入相册 / 删除 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumDetailDialog(
    photo: MemoryPhoto,
    albums: List<PhotoAlbum>,
    currentAlbumId: String?,
    onDismiss: () -> Unit,
    onSaveNote: (String) -> Unit,
    onSetWallpaper: () -> Unit,
    onReplace: () -> Unit,
    onAssignAlbum: () -> Unit,
    onDelete: () -> Unit
) {
    var note by remember(photo.id) { mutableStateOf(photo.note) }
    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Rounded.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(photo.day)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AsyncImage(
                    model = File(photo.path),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp))
                )
                GlassTextField(
                    value = note, onValueChange = { note = it.take(120) },
                    label = "当年的话（可选）",
                    placeholder = "例如：大二那年和室友一起去的",
                    modifier = Modifier.fillMaxWidth(), maxLines = 3
                )
                val cur = albums.firstOrNull { it.id == currentAlbumId }
                Text(
                    "所在相册：${cur?.name ?: "未分类"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSaveNote(note) }) { Text("保存") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onSetWallpaper) { Text("设为壁纸") }
                TextButton(onClick = onReplace) { Text("替换图片") }
                TextButton(onClick = onAssignAlbum) { Text("归入相册") }
                TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
            }
        }
    )
}

/** 归入/移出相册选择 */
@Composable
private fun AssignAlbumDialog(
    albums: List<PhotoAlbum>,
    currentAlbumId: String?,
    onDismiss: () -> Unit,
    onAssign: (String?) -> Unit
) {
    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("归入相册") },
        text = {
            LazyRowOrColumn(albums, currentAlbumId, onAssign, onDismiss)
        },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss) { Text("取消") } }
    )
}

@Composable
private fun LazyRowOrColumn(
    albums: List<PhotoAlbum>,
    currentAlbumId: String?,
    onAssign: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    // 用 LazyColumn 容纳「未分类 + 各相册」，点选即归入
    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
        item {
            ListItem(
                headlineContent = { Text("未分类") },
                leadingContent = { Icon(Icons.Rounded.Close, null) },
                modifier = Modifier.clickable {
                    onAssign(null); onDismiss()
                }
            )
        }
        items(albums, key = { it.id }) { album ->
            ListItem(
                headlineContent = { Text(album.name) },
                leadingContent = {
                    if (album.id == currentAlbumId) Icon(Icons.Rounded.Check, null, tint = AmberPrimary)
                    else Icon(Icons.Rounded.Edit, null)
                },
                modifier = Modifier.clickable {
                    onAssign(album.id); onDismiss()
                }
            )
        }
    }
}

/** 相册管理：新建（已在上方可内联）/ 重命名 / 删除 / 快速进入 */
@Composable
private fun AlbumManageDialog(
    albums: List<PhotoAlbum>,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String, String) -> Unit,
    onSelect: (String?) -> Unit
) {
    var renameTarget by remember { mutableStateOf<PhotoAlbum?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<PhotoAlbum?>(null) }

    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text("相册管理")
            }
        },
        text = {
            if (albums.isEmpty()) {
                Text("还没有自定义相册。在相册页顶部点「＋ 新建相册」即可创建。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(albums, key = { it.id }) { album ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(album.name, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            TextButton(onClick = { onSelect(album.id) }) { Text("进入") }
                            TextButton(onClick = {
                                renameTarget = album; renameText = album.name
                            }) { Text("重命名") }
                            TextButton(onClick = { deleteTarget = album }) {
                                Text("删除", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(""); onDismiss() }) { Text("＋ 新建相册") }
        },
        dismissButton = { TextButton(onDismiss) { Text("关闭") } }
    )

    // 重命名
    renameTarget?.let { album ->
        AnimatedAlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名相册") },
            text = {
                GlassTextField(renameText, { renameText = it.take(24) },
                    label = "名称", singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(enabled = renameText.isNotBlank(), onClick = {
                    onRename(album.id, renameText.trim()); renameTarget = null
                }) { Text("确定") }
            },
            dismissButton = { TextButton({ renameTarget = null }) { Text("取消") } }
        )
    }

    // 删除确认
    deleteTarget?.let { album ->
        AnimatedAlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除相册「${album.name}」？") },
            text = { Text("照片不会丢失，会回到「未分类」。此操作可重新创建相册恢复归类。）") },
            confirmButton = {
                TextButton(onClick = { onDelete(album.id, album.name); deleteTarget = null }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton({ deleteTarget = null }) { Text("取消") } }
        )
    }
}

/** 批量选图启动器：Android 13+ 用 Photo Picker，旧版本回落系统多选 */
@Composable
private fun rememberLauncherForAlbum(onPicked: (List<android.net.Uri>) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> onPicked(uris) }
    return { launcher.launch("image/*") }
}

/**
 * v1.0.7：全览网格。自适应列数的方形缩略图墙；
 * batchMode 下点选切换选中态并显示勾角，非 batchMode 点按开详情。
 */
@Composable
private fun PhotoGrid(
    photos: List<MemoryPhoto>,
    batchMode: Boolean,
    selectedIds: Set<String>,
    onToggleSelect: (String) -> Unit,
    onOpen: (MemoryPhoto) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        gridItems(photos, key = { it.id }) { p ->
            val sel = p.id in selectedIds
            Box(
                Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { if (batchMode) onToggleSelect(p.id) else onOpen(p) }
            ) {
                AsyncImage(
                    model = File(p.path),
                    contentDescription = p.note.ifBlank { "照片" },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (batchMode) {
                    if (sel) Box(Modifier.fillMaxSize().background(AmberPrimary.copy(alpha = 0.28f)))
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = if (sel) AmberPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(22.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f), CircleShape)
                    )
                }
            }
        }
    }
}

/** v1.0.7：批量管理操作条（全选 / 归入相册 / 删除 / 退出） */
@Composable
private fun BatchActionBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onAssign: () -> Unit,
    onDelete: () -> Unit,
    onExit: () -> Unit
) {
    Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "已选 $selectedCount",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onSelectAll) {
                Text(if (selectedCount == totalCount && totalCount > 0) "取消" else "全选")
            }
            TextButton(onClick = onAssign, enabled = selectedCount > 0) { Text("归入相册") }
            TextButton(onClick = onDelete, enabled = selectedCount > 0) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
            TextButton(onClick = onExit) { Text("完成") }
        }
    }
}
