package com.example.earthonline.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.earthonline.BuildConfig
import com.example.earthonline.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.earthonline.util.DownloadPhase
import androidx.navigation.NavHostController
import com.example.earthonline.data.network.UpdateRepository
import com.example.earthonline.reminder.ReminderScheduler
import com.example.earthonline.util.IMAGE_TYPES_HINT
import com.example.earthonline.util.ImageStore
import com.example.earthonline.util.openUrlInBrowser
import com.example.earthonline.util.rememberImagePicker
import com.example.earthonline.ui.components.AnimatedAlertDialog
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.components.CropShape
import com.example.earthonline.ui.components.ImageCropperDialog
import com.example.earthonline.ui.navigation.Screen
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt
import kotlin.text.Charsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ============================================================
 * v1.2.3：集中设置页拆出的四个二级页。
 * 每个二级页都是独立路由（见 Screen.kt），顶部统一带返回导航栏，
 * 点返回由 NavHost 弹栈回到「设置」主页（见 MainScaffold 的返回键处理）。
 * ============================================================ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceRoute(
    navController: NavHostController,
    moreActions: MoreMenuActions,
    vm: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val theme by vm.theme.collectAsStateWithLifecycle(initialValue = "system")
    val wallpaper by vm.wallpaper.collectAsStateWithLifecycle(initialValue = "")
    val wallpaperAlpha by vm.wallpaperAlpha.collectAsStateWithLifecycle(initialValue = 0.35f)
    // v1.0.4：字号档位设置已移除 —— 应用内不再额外缩放文字，直接跟随系统字体大小，
    // 由系统无障碍设置统一控制（对应需求「删除字号大小设置，自动适配」）。
    // v1.0.3：壁纸从记忆相册随机轮换
    val wallpaperRotate by vm.wallpaperRotate.collectAsStateWithLifecycle(initialValue = false)
    val memoryPhotosJson by vm.memoryPhotosJson.collectAsStateWithLifecycle(initialValue = "")
    val albumCount = remember(memoryPhotosJson) {
        Regex("\"path\"").findAll(memoryPhotosJson).count()
    }
    val wallpaperAlbum by vm.wallpaperAlbum.collectAsStateWithLifecycle(initialValue = "")
    val albums by vm.albums.collectAsStateWithLifecycle(initialValue = emptyList())

    /* v1.2.4：壁纸导入改为「先选图 → 裁剪框缩放/移动（铺满屏幕）→ 确认」，
       裁剪输出无损 PNG（见 ui/components/ImageCropper.kt）。 */
    var showWallpaperCrop by remember { mutableStateOf(false) }
    var wallpaperCropUri by remember { mutableStateOf<Uri?>(null) }
    val pickWallpaper = rememberImagePicker(onPicked = { uri ->
        wallpaperCropUri = uri
        showWallpaperCrop = true
    })

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { SettingsTopBar("外观") { navController.popBackStack() } },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("主题", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeChip("跟随系统", "system", theme) { vm.setTheme("system") }
                        ThemeChip("浅色", "light", theme) { vm.setTheme("light") }
                        ThemeChip("深色", "dark", theme) { vm.setTheme("dark") }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("壁纸", style = MaterialTheme.typography.labelMedium)
                            Text(
                                if (wallpaper.isBlank()) "未设置" else "已设置（点击更换）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "支持 $IMAGE_TYPES_HINT，原图完整保存不压缩；显示时按屏幕尺寸自动采样。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(onClick = { pickWallpaper() }) { Text("选择图片") }
                    }
                    if (wallpaper.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                vm.setWallpaper("")
                                scope.launch { snackbar.showSnackbar("已重置为默认背景") }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("重置为默认") }
                    }
                    if (wallpaper.isNotBlank()) {
                        var alphaDraft by remember(wallpaper) { mutableFloatStateOf(wallpaperAlpha) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("壁纸不透明度", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                            Text(
                                "${(alphaDraft * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Slider(
                            value = alphaDraft,
                            onValueChange = {
                                alphaDraft = it
                                vm.setWallpaperAlpha(it)
                            },
                            valueRange = 0.05f..0.9f
                        )
                    }
                    // v1.0.3：动态主页背景 —— 从记忆相册随机轮换（每天一张，确定性挑选）
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("从记忆相册轮换壁纸", style = MaterialTheme.typography.labelMedium)
                            Text(
                                if (albumCount == 0) "先在数据页「记忆相册」导入照片"
                                else "每天自动从 ${albumCount} 张照片里换一张",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = wallpaperRotate && albumCount > 0,
                            onCheckedChange = {
                                if (albumCount > 0) vm.setWallpaperRotate(it)
                            },
                            enabled = albumCount > 0
                        )
                    }
                    // v1.0.6：轮换来源相册（空 = 全部照片，无数量限制）
                    if (wallpaperRotate && albums.isNotEmpty()) {
                        var albumMenu by remember { mutableStateOf(false) }
                        val albumName = albums.firstOrNull { it.id == wallpaperAlbum }?.name ?: "全部照片"
                        ExposedDropdownMenuBox(
                            expanded = albumMenu,
                            onExpandedChange = { albumMenu = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = albumName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("轮换来源相册") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = albumMenu) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = albumMenu,
                                onDismissRequest = { albumMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("全部照片") },
                                    onClick = { vm.setWallpaperAlbum(""); albumMenu = false }
                                )
                                albums.forEach { a ->
                                    DropdownMenuItem(
                                        text = { Text(a.name) },
                                        onClick = { vm.setWallpaperAlbum(a.id); albumMenu = false }
                                    )
                                }
                            }
                        }
                    }
                    // v1.0.4：字号档位设置已移除。文字大小完全跟随系统字体缩放（无障碍），
                    // 应用内布局全部自适应（FlowRow / 横向滚动 / 权重布局），不再需要档位干预。
                }
            }
        }
    }

    if (showWallpaperCrop && wallpaperCropUri != null) {
        ImageCropperDialog(
            uri = wallpaperCropUri!!,
            shape = CropShape.Rect,
            outputDir = ImageStore.wallpaperDir(context),
            prefix = "wallpaper",
            onConfirm = { file ->
                vm.setWallpaper(file.absolutePath)
                ImageStore.clearDirExcept(ImageStore.wallpaperDir(context), file)
                showWallpaperCrop = false
                wallpaperCropUri = null
                scope.launch { snackbar.showSnackbar("壁纸已设置（裁剪无损 · ${ImageStore.prettySize(file.length())}）") }
            },
            onDismiss = { showWallpaperCrop = false; wallpaperCropUri = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralRoute(
    navController: NavHostController,
    moreActions: MoreMenuActions,
    vm: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val notify by vm.notify.collectAsStateWithLifecycle(initialValue = false)
    val achSound by vm.achSound.collectAsStateWithLifecycle(initialValue = true)
    // v1.0.3：通知栏快捷记录开关
    val quickNotif by vm.quickNotif.collectAsStateWithLifecycle(initialValue = false)
    val aiConfigJson by vm.aiConfigJson.collectAsStateWithLifecycle(initialValue = "")

    var showAiConfig by remember { mutableStateOf(false) }

    // 记录权限请求的发起方（到期提醒 / 快捷记录），回调里据此分别处理
    var pendingQuickNotif by remember { mutableStateOf(false) }

    val postNotifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (pendingQuickNotif) {
                vm.setQuickNotif(true)
            } else {
                vm.setNotify(true)
                ReminderScheduler.schedule(context)
            }
            pendingQuickNotif = false
        } else {
            scope.launch { snackbar.showSnackbar("未授予通知权限，无法开启该功能") }
        }
    }

    fun onQuickNotifToggle(want: Boolean) {
        if (want) {
            if (Build.VERSION.SDK_INT >= 33) {
                when (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)) {
                    PackageManager.PERMISSION_GRANTED -> vm.setQuickNotif(true)
                    else -> { pendingQuickNotif = true; postNotifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
                }
            } else {
                vm.setQuickNotif(true)
            }
        } else {
            vm.setQuickNotif(false)
        }
    }

    fun onNotifyToggle(want: Boolean) {
        if (want) {
            if (Build.VERSION.SDK_INT >= 33) {
                when (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)) {
                    PackageManager.PERMISSION_GRANTED -> {
                        vm.setNotify(true)
                        ReminderScheduler.schedule(context)
                    }
                    else -> postNotifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                vm.setNotify(true)
                ReminderScheduler.schedule(context)
            }
        } else {
            vm.setNotify(false)
            ReminderScheduler.cancel(context)
        }
    }

    if (showAiConfig) {
        AiConfigDialog(
            initial = vm.parseAiConfig(aiConfigJson),
            onDismiss = { showAiConfig = false }
        ) { vm.saveAiConfig(it); showAiConfig = false }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { SettingsTopBar("通用") { navController.popBackStack() } },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("到期提醒通知", style = MaterialTheme.typography.labelMedium)
                            Text(
                                "待办到期当天推送提醒",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OnOffSwitch(checked = notify, onCheckedChange = { onNotifyToggle(it) })
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    // v1.0.3：通知栏常驻「快捷记录」按钮
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("通知栏快捷记录", style = MaterialTheme.typography.labelMedium)
                            Text(
                                "在通知栏常驻一个「记录这一刻」入口，点一下就能写日志",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OnOffSwitch(checked = quickNotif, onCheckedChange = { onQuickNotifToggle(it) })
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("解锁音效", style = MaterialTheme.typography.labelMedium)
                            Text(
                                "解锁成就时右下角弹卡片并播放一声轻响",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OnOffSwitch(checked = achSound, onCheckedChange = { vm.setAchSound(it) })
                    }
                }
            }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("数据管理", style = MaterialTheme.typography.labelMedium)
                    SettingsNavItem(
                        icon = Icons.Filled.DeleteOutline,
                        label = "回收站",
                        hint = "已删除内容保留 30 天，可随时恢复",
                        onClick = { moreActions.onNavigate(Screen.RecycleBin.route) }
                    )
                }
            }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AI 对话", style = MaterialTheme.typography.labelMedium)
                    val cfg = vm.parseAiConfig(aiConfigJson)
                    Text(
                        "当前：${if (cfg.baseUrl.isBlank()) "未配置" else cfg.baseUrl}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { showAiConfig = true }, modifier = Modifier.fillMaxWidth()) { Text("配置 AI 接口") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSyncRoute(
    navController: NavHostController,
    moreActions: MoreMenuActions,
    vm: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val webdavConfigJson by vm.webdavConfigJson.collectAsStateWithLifecycle(initialValue = "")

    var showDavConfig by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var davTesting by remember { mutableStateOf(false) }
    var davSyncing by remember { mutableStateOf(false) }
    var davPulling by remember { mutableStateOf(false) }
    // v1.0.5：清空数据确认框与「同时删除云端备份」勾选态（默认不勾）
    var showClearData by remember { mutableStateOf(false) }
    var clearDelCloud by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }

    val autoSync by vm.autoSync.collectAsStateWithLifecycle(initialValue = true)
    val lastSyncAt by vm.lastSyncAt.collectAsStateWithLifecycle(initialValue = "")

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val text = vm.exportJson()
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(text.toByteArray(Charsets.UTF_8))
                }
                snackbar.showSnackbar("已导出备份")
            } catch (e: Exception) {
                snackbar.showSnackbar("导出失败：${e.message}")
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                vm.importJson(text)
                snackbar.showSnackbar("已导入备份")
            } catch (e: Exception) {
                snackbar.showSnackbar("导入失败：${e.message}")
            }
        }
    }
    // v1.0.5：CSV / Markdown 文本导入（任务表 / 日记）
    val textImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                snackbar.showSnackbar(vm.importTextFile(text))
            } catch (e: Exception) {
                snackbar.showSnackbar("导入失败：${e.message}")
            }
        }
    }

    if (showDavConfig) {
        WebDavConfigDialog(
            initial = vm.parseWebdavConfig(webdavConfigJson),
            onDismiss = { showDavConfig = false }
        ) { vm.saveWebDavConfig(it); showDavConfig = false }
    }
    if (showPrivacy) {
        AnimatedAlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text("隐私政策") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "最后更新：${AppInfo.buildDate} · v${AppInfo.version}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AppInfo.privacyPolicy.forEach { (title, body) ->
                        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(
                            body,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("我已阅读") } }
        )
    }
    // v1.0.5：清空数据 —— 醒目二次确认（红色警示 + 勾选删云端，默认不勾）
    if (showClearData) {
        AnimatedAlertDialog(
            onDismissRequest = { if (!clearing) showClearData = false },
            title = { Text("确认清空数据", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "将清空本机全部用户数据（任务、日志、物品、成就、收藏、足迹、个人资料等），并恢复为全新种子数据。此操作无法撤销，建议先「导出备份」。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "主题、壁纸、WebDAV 与 AI 配置等应用设置会保留；清空后会暂停一次启动时的云端自动拉取，防止旧备份被同步回来。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = clearDelCloud,
                            onCheckedChange = { clearDelCloud = it },
                            enabled = !clearing
                        )
                        Text(
                            "同时删除云端备份（默认不勾）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !clearing,
                    onClick = {
                        clearing = true
                        scope.launch {
                            val msg = vm.clearAllData(clearDelCloud)
                            clearing = false
                            showClearData = false
                            snackbar.showSnackbar(msg)
                            // 与 Web 端一致：清空后回主页（数据归零 + 空态引导）
                            if (!navController.popBackStack(Screen.Home.route, false)) {
                                navController.navigate(Screen.Home.route) { launchSingleTop = true }
                            }
                        }
                    }
                ) { Text("确认清空", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(enabled = !clearing, onClick = { showClearData = false }) { Text("取消") }
            }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { SettingsTopBar("数据与备份") { navController.popBackStack() } },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("WebDAV 配置", style = MaterialTheme.typography.labelMedium)
                    val cfg = vm.parseWebdavConfig(webdavConfigJson)
                    Text(
                        "当前：${if (cfg.url.isBlank()) "未配置" else cfg.url}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { showDavConfig = true }, modifier = Modifier.fillMaxWidth()) { Text("配置 WebDAV 服务器") }
                    Text(
                        "填写服务器信息后，在下方手动同步，或在「外观/通用」页设置自动同步。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("手动同步", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    val cfg = vm.parseWebdavConfig(webdavConfigJson)
                    Text(
                        "当前：${if (cfg.url.isBlank()) "未配置" else cfg.url}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                if (!com.example.earthonline.util.NetworkMonitor.isOnlineNow(context)) {
                                    scope.launch { snackbar.showSnackbar("网络不可用，请检查网络连接") }
                                } else {
                                    davTesting = true
                                    scope.launch { val r = vm.testDav(cfg); davTesting = false; snackbar.showSnackbar(r) }
                                }
                            },
                            enabled = !davTesting,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (davTesting) CircularProgressIndicator(Modifier.size(18.dp)) else Text("测试连接")
                        }
                        OutlinedButton(
                            onClick = {
                                if (!com.example.earthonline.util.NetworkMonitor.isOnlineNow(context)) {
                                    scope.launch { snackbar.showSnackbar("网络不可用，请检查网络连接") }
                                } else {
                                    davSyncing = true
                                    scope.launch { val r = vm.syncToDav(cfg); davSyncing = false; snackbar.showSnackbar(r) }
                                }
                            },
                            enabled = !davSyncing,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (davSyncing) CircularProgressIndicator(Modifier.size(18.dp)) else Text("推送到云端")
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            if (!com.example.earthonline.util.NetworkMonitor.isOnlineNow(context)) {
                                scope.launch { snackbar.showSnackbar("网络不可用，请检查网络连接") }
                            } else {
                                davPulling = true
                                scope.launch { val r = vm.syncFromDav(cfg); davPulling = false; snackbar.showSnackbar(r) }
                            }
                        },
                        enabled = !davPulling,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (davPulling) CircularProgressIndicator(Modifier.size(18.dp)) else Text("从云端拉取")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("自动同步（启动拉取 / 切后台推送）", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        OnOffSwitch(checked = autoSync, onCheckedChange = { vm.setAutoSync(it) })
                    }
                    Text(
                        if (lastSyncAt.isBlank()) "尚未同步过"
                        else "上次同步：${lastSyncAt.take(19).replace('T', ' ')}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                val fmt = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("本地自动备份", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "每次改动后自动在本机留一份快照，只保留最近 3 份（卸载应用会一并删除，长期留档请用「导出备份」）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val ok = vm.autoBackupNow()
                                snackbar.showSnackbar(if (ok) "已保存一份本地快照" else "备份失败")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("立即备份一份") }
                    val snapshots by produceState(initialValue = emptyList<com.example.earthonline.data.backup.AutoBackupManager.Snapshot>()) {
                        value = vm.autoSnapshots()
                    }
                    if (snapshots.isEmpty()) {
                        Text(
                            "还没有自动快照",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        snapshots.forEach { s ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(fmt.format(Date(s.timeMillis)), style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "${s.bytes / 1024} KB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = {
                                    scope.launch {
                                        val ok = vm.restoreAutoSnapshot(s)
                                        snackbar.showSnackbar(if (ok) "已从该快照恢复（按主键合并）" else "恢复失败")
                                    }
                                }) { Text("恢复") }
                            }
                        }
                    }
                }
            }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("导出 / 导入备份", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { exportLauncher.launch("earth-online-backup.json") }, modifier = Modifier.weight(1f)) { Text("导出备份") }
                        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }, modifier = Modifier.weight(1f)) { Text("导入备份") }
                    }
                    Text(
                        "导入采用按主键合并：同一条数据以备份为准，本地新增的部分保留（与网页端一致）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    // v1.0.5：CSV / Markdown 文本导入（任务表 / 日记，增量合并去重）
                    OutlinedButton(
                        onClick = { textImportLauncher.launch(arrayOf("text/*", "text/csv", "text/markdown", "*/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("从 CSV / Markdown 导入") }
                    Text(
                        "任务 CSV 需含 title 列（可选 status、dueDate、note）；日志 CSV 需含 text 列（可选 type、createdAt）；" +
                            "Markdown 日记用「# 2026-01-01」标题分日，每行一条。重复导入自动去重。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // v1.0.5：危险区 —— 清空数据（醒目确认 + 可选删云端 + 防同步拉回，与 Web/Windows 三端一致）
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                )
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "危险区",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "清空本机全部用户数据并恢复为全新种子数据。主题、壁纸、WebDAV 与 AI 配置等应用设置会保留。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { showClearData = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("🗑️ 清空数据", color = MaterialTheme.colorScheme.error) }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val msg = vm.resyncToCloud()
                                snackbar.showSnackbar(msg)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("☁️ 重新同步（上传当前数据到云端）") }
                    Text(
                        "「重新同步」会把当前（清空后的）数据上传云端覆盖旧备份，用于恢复多设备同步。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("隐私政策", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "本应用不收集任何数据，所有内容只存在这台设备上。完整说明可随时查阅。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { showPrivacy = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("查看隐私政策") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutRoute(
    navController: NavHostController,
    moreActions: MoreMenuActions,
    vm: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var showSponsor by remember { mutableStateOf(false) }
    var showChangelog by remember { mutableStateOf(false) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateMsg by remember { mutableStateOf<String?>(null) }
    var updateAvailable by remember { mutableStateOf<UpdateCheck?>(null) }

    // v1.0.5：下载与安装抽到应用级 ApkInstaller（与启动更新弹窗共用）；
    // 「安装未知应用」权限（Android 8.0+）的跳转与回执仍留在 UI 层（ActivityResult 必须在 Composable 注册）
    val installer = vm.installer
    var pendingApkUrl by remember { mutableStateOf<String?>(null) }

    // ApkInstaller 的用户消息（开始下载 / 下载失败 / 无法启动安装）→ Snackbar，消费即清空
    val installerEvent by installer.events.collectAsStateWithLifecycle()
    LaunchedEffect(installerEvent) {
        installerEvent?.let {
            snackbar.showSnackbar(it)
            installer.consumeEvent()
        }
    }

    val installPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        val canInstall = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
        val url = pendingApkUrl
        pendingApkUrl = null
        if (canInstall && url != null) installer.download(url)
        else scope.launch { snackbar.showSnackbar("未授权「安装未知应用」，已改为打开 Release 页面") }
    }

    fun beginInstallFlow(apkUrl: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            pendingApkUrl = apkUrl
            installPermLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.fromParts("package", context.packageName, null)
                )
            )
        } else {
            installer.download(apkUrl)
        }
    }

    if (showSponsor) {
        AnimatedAlertDialog(
            onDismissRequest = { showSponsor = false },
            title = { Text("❤️ 赞助开发者") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "地球Online 完全免费、无广告、无服务器成本。如果它陪你走过了一段路，" +
                            "欢迎扫码请开发者喝一杯 ☕（金额随意）。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Image(
                        painter = painterResource(R.drawable.sponsor_qr),
                        contentDescription = "赞助收款码",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Text(
                        "长按图片保存到相册，再用微信「扫一扫 · 相册」识别。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showSponsor = false }) { Text("知道啦") } }
        )
    }

    if (showChangelog) {
        AnimatedAlertDialog(
            onDismissRequest = { showChangelog = false },
            title = { Text("📝 更新说明") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    AppInfo.changelog.forEach { (ver, date, items) ->
                        Text(
                            "v$ver · $date",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        items.forEach { item ->
                            Text(
                                "· $item",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showChangelog = false }) { Text("知道了") } }
        )
    }

    updateMsg?.let { msg ->
        AnimatedAlertDialog(
            onDismissRequest = { updateMsg = null },
            title = { Text("检查更新") },
            text = { Text(msg, style = MaterialTheme.typography.bodySmall) },
            confirmButton = { TextButton(onClick = { updateMsg = null }) { Text("好的") } }
        )
    }

    updateAvailable?.let { up ->
        val isApk = up.url.endsWith(".apk", ignoreCase = true)
        AnimatedAlertDialog(
            onDismissRequest = { updateAvailable = null },
            title = { Text("发现新版本 v${up.latest}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "当前版本 v${AppInfo.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (up.notes.isNotBlank()) {
                        Text(
                            up.notes,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 180.dp)
                        )
                    }
                    Text(
                        if (isApk) "下载将通过系统下载器进行，完成后自动提示安装；Android 8.0 及以上首次安装需授予「安装未知应用」权限。覆盖安装不会丢失本地数据。"
                        else "未找到 APK 直链，将打开 Release 页面手动下载。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    updateAvailable = null
                    if (isApk) {
                        beginInstallFlow(up.url)
                    } else if (!openUrlInBrowser(context, up.url, noBrowserMessage = "未找到可用的浏览器，请手动访问 Release 页面")) {
                        scope.launch { snackbar.showSnackbar("未找到可用的浏览器") }
                    }
                }) { Text(if (isApk) "下载并安装" else "前往更新") }
            },
            dismissButton = { TextButton(onClick = { updateAvailable = null }) { Text("稍后") } }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { SettingsTopBar("关于") { navController.popBackStack() } },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // v1.0.6：应用内更新的下载进度卡（多线路自动回退，实时百分比 / 已下载字节 / 当前线路）
            val dlProgress by installer.progress.collectAsStateWithLifecycle()
            dlProgress?.let { p ->
                if (p.phase != DownloadPhase.Idle) {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    when (p.phase) {
                                        DownloadPhase.Downloading -> "⬇️ 正在下载更新"
                                        DownloadPhase.Retrying -> "🔁 正在切换下载线路"
                                        DownloadPhase.Success -> "✅ 下载完成"
                                        else -> "⚠️ 下载失败"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { installer.clearProgress() }) { Text("收起") }
                            }
                            if (p.phase == DownloadPhase.Downloading) {
                                if (p.percent >= 0) {
                                    LinearProgressIndicator(
                                        progress = p.percent / 100f,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        "${p.percent}% · ${formatBytes(p.receivedBytes)} / ${formatBytes(p.totalBytes)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    Text(
                                        "已下载 ${formatBytes(p.receivedBytes)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (p.totalAttempts > 1) {
                                Text(
                                    "线路 ${p.attempt} / ${p.totalAttempts}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            p.message?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Image(
                            painter = painterResource(R.drawable.ic_app_logo),
                            contentDescription = null,
                            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                        )
                        Column(Modifier.weight(1f)) {
                            Text("${AppInfo.name} v${AppInfo.version}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(AppInfo.slogan, style = MaterialTheme.typography.bodySmall)
                            Text(
                                "构建于 ${AppInfo.buildDate} · ${AppInfo.license} 开源许可",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text("开发人员", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppInfo.developers.forEach { PersonChip(it) }
                    }
                    Text("赞助者", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppInfo.sponsors.forEach { PersonChip(it) }
                    }
                    Text(
                        AppInfo.offlineNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showSponsor = true }, modifier = Modifier.weight(1f)) { Text("❤️ 赞助我") }
                        OutlinedButton(onClick = { showChangelog = true }, modifier = Modifier.weight(1f)) { Text("📝 更新说明") }
                    }
                    OutlinedButton(
                        onClick = {
                            if (checkingUpdate) return@OutlinedButton
                            checkingUpdate = true
                            scope.launch {
                                vm.checkUpdate()
                                    .onSuccess { c ->
                                        if (c.hasUpdate) updateAvailable = c
                                        else updateMsg = "已是最新版本 v${AppInfo.version}"
                                    }
                                    .onFailure { e ->
                                        updateMsg = "检查失败：${e.message ?: "无法连接更新服务器"}"
                                    }
                                checkingUpdate = false
                            }
                        },
                        enabled = !checkingUpdate,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (checkingUpdate) {
                            CircularProgressIndicator(Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("检查更新")
                    }
                    // v1.0.7：分享人生卡片入口已去重 —— 统一收敛到「个人资料页」唯一入口
                    // （com.example.earthonline.util.ShareCardRenderer），此处不再重复提供生成入口。
                }
            }
        }
    }
}

/* ===================== 二级页共用：返回导航栏 + 列表项 ===================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
        }
    )
}

@Composable
fun SettingsNavItem(
    icon: ImageVector,
    label: String,
    hint: String,
    chip: Color = Color(0xFFF59E0B),
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            /* v1.0.9：彩色圆角图标芯片（对标参考设置页），38dp / 圆角 12dp */
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(chip.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = chip, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (hint.isNotBlank()) {
                    Text(
                        hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun OnOffSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
        Text(
            text = if (checked) "开" else "关",
            style = MaterialTheme.typography.labelMedium,
            color = if (checked) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ThemeChip(label: String, value: String, current: String, onClick: () -> Unit) {
    FilterChip(selected = current == value, onClick = onClick, label = { Text(label) })
}

@Composable
fun PersonChip(p: Person) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(p.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            if (p.tag.isNotBlank()) {
                Text(
                    p.tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 字节数转人类可读（下载进度用）；未知大小（负数 / 0）返回 "--" */
private fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "--"
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
