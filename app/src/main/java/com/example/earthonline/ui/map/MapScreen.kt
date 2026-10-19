package com.example.earthonline.ui.map

import android.Manifest
import android.content.Intent
import android.provider.Settings
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import com.example.earthonline.data.local.entity.LocationEntity
import com.example.earthonline.ui.components.AnimatedAlertDialog
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.components.SettingsIconButton
import com.example.earthonline.ui.theme.AmberPrimary
import com.example.earthonline.util.LocationHelper
import com.example.earthonline.util.LocateError
import com.example.earthonline.util.LocateResult
import com.example.earthonline.util.millisToDayStr
import com.example.earthonline.util.todayStr
import kotlinx.coroutines.launch

/**
 * 地图是否启用真实高德地图。
 * Key 已配置在 AndroidManifest.xml 的 com.amap.api.v2.apikey。
 * 若需临时降级为足迹列表（如排查地图黑屏），改为 false 即可。
 */
private const val MAP_ENABLED = true

/**
 * 持有 MapView / AMap 的引用。
 * 关键点：这是**普通对象**（非 Compose State），在 AndroidView 的 factory 里赋值不会触发
 * 组合期状态写入；后续在协程 / 点击事件里读取即可，彻底规避「组合期写 State」导致的崩溃。
 */
private class MapRef {
    /** 地图视图；创建失败时为 null（failed = true） */
    var view: MapView? = null
    /** MapView 原生组件是否初始化失败（普通字段，非 State，可安全在组合期写入） */
    var failed: Boolean = false
    val aMap: AMap? get() = view?.map
}

@Composable
fun MapRoute(moreActions: MoreMenuActions, vm: MapViewModel = hiltViewModel()) =
    MapScreen(vm = vm, moreActions = moreActions)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(vm: MapViewModel, moreActions: MoreMenuActions) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val locations by vm.locations.collectAsStateWithLifecycle(initialValue = emptyList())

    // v1.0.7：顶部地点搜索栏 —— 本地足迹筛选（名称 / 备注 / 标签 / 日期），不依赖高德 Key
    var query by remember { mutableStateOf("") }
    var showList by remember { mutableStateOf(false) }
    val filteredLocations = remember(locations, query) {
        if (query.isBlank()) locations else locations.filter { loc ->
            loc.name.contains(query, ignoreCase = true) ||
                (loc.note ?: "").contains(query, ignoreCase = true) ||
                loc.date.contains(query, ignoreCase = true) ||
                (loc.tagsJson ?: "").contains(query, ignoreCase = true)
        }
    }

    // 普通对象持有 MapView，避免组合期写 State（地图闪退根因之一）
    val mapRef = remember { MapRef() }
    // mapReady 在「视图真正 attach 到窗口」后设置（不在组合期），可安全写入 State
    var mapReady by remember { mutableStateOf(false) }
    // 高德 SDK / 原生 so 加载失败时退化为列表视图，避免硬崩
    var mapFailed by remember { mutableStateOf(false) }

    var currentLatLng by remember { mutableStateOf<LatLng?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var pendingLatLng by remember { mutableStateOf<LatLng?>(null) }
    /** 是否正在定位（UI 显示进度，避免「点了没反应」） */
    var locating by remember { mutableStateOf(false) }

    fun moveCameraTo(ll: LatLng, zoom: Float = 15f) {
        mapRef.aMap?.let { runCatching { it.moveCamera(CameraUpdateFactory.newLatLngZoom(ll, zoom)) } }
    }

    /**
     * 主动定位（调用前请自行确保已授权）。每一步都有明确反馈：
     * 旧实现只读 getLastKnownLocation（多数真机为 null）且失败静默，是「点定位没反应」的根因。
     * @param silent true=静默模式（进入地图自动定位），失败不弹提示，避免打扰
     */
    fun locate(silent: Boolean) {
        if (locating) return
        locating = true
        if (!silent) scope.launch { snackbar.showSnackbar("正在定位…") }
        LocationHelper.requestCurrentLocation(context) { result ->
            locating = false
            when (result) {
                is LocateResult.Success -> {
                    val ll = LatLng(result.lat, result.lng)
                    currentLatLng = ll
                    mapRef.aMap?.let { runCatching { it.moveCamera(CameraUpdateFactory.newLatLngZoom(ll, 15f)) } }
                    if (!silent) scope.launch { snackbar.showSnackbar("已定位到当前位置") }
                }
                is LocateResult.Failure -> scope.launch {
                    when (result.error) {
                        LocateError.NO_PERMISSION -> snackbar.showSnackbar("没有定位权限，请授予后再试")
                        LocateError.DISABLED -> snackbar.showSnackbar(
                            message = "系统定位已关闭，请开启后重试",
                            actionLabel = "去开启"
                        ).let {
                            if (it == SnackbarResult.ActionPerformed) {
                                runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }
                        }
                        LocateError.TIMEOUT -> snackbar.showSnackbar("暂时定位不到，请到室外或打开 WiFi 后重试")
                        LocateError.UNAVAILABLE -> snackbar.showSnackbar("设备不支持定位")
                    }
                }
            }
        }
    }

    // 授权回调：拿到权限立即定位；被拒则明确告知去哪里开启
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) locate(false)
        else scope.launch { snackbar.showSnackbar("未授予定位权限，可在系统设置中开启") }
    }

    // 进入地图且已授权时，静默定位一次（失败不打扰用户）；mapReady 之后才触发，避免空指针
    var autoLocateDone by remember { mutableStateOf(false) }
    LaunchedEffect(mapReady) {
        if (!autoLocateDone && mapReady && LocationHelper.hasPermission(context)) {
            autoLocateDone = true
            locate(true)
        }
    }

    // 统一重绘：足迹 marker + 当前位置 marker（任一项变化都重画，避免互相覆盖）
    LaunchedEffect(locations, mapReady, currentLatLng) {
        if (!mapReady) return@LaunchedEffect
        val map = mapRef.aMap ?: return@LaunchedEffect
        runCatching {
            map.clear()
            locations.forEach { loc ->
                if (loc.lat != 0.0 || loc.lng != 0.0) {
                    map.addMarker(MarkerOptions().position(LatLng(loc.lat, loc.lng)).title(loc.name).snippet(loc.note ?: ""))
                }
            }
            currentLatLng?.let {
                map.addMarker(MarkerOptions().position(it).title("我的位置").snippet("当前定位"))
            }
        }
    }

    // v1.0.2 深度修复：绑定 Activity 生命周期。
    // ON_RESUME -> mapView.onResume()，ON_PAUSE -> mapView.onPause()，
    // onDispose（离开页面/销毁）-> mapView.onDestroy()。三者缺一都可能崩溃或泄漏 GL 资源。
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            val mv = mapRef.view ?: return@LifecycleEventObserver
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> runCatching { mv.onResume() }
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> runCatching { mv.onPause() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapRef.view?.let { mv ->
                runCatching { mv.onPause() }
                runCatching { mv.onDestroy() }
            }
            mapRef.view = null
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("足迹地图") },
                actions = {
                    // v1.0.7：打开「路径点记录页」（本地足迹列表，支持搜索筛选）
                    IconButton(onClick = { showList = true }) {
                        Icon(Icons.Filled.List, contentDescription = "足迹列表")
                    }
                    SettingsIconButton(actions = moreActions)
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(
                    onClick = {
                        if (LocationHelper.hasPermission(context)) locate(false)
                        else permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                ) {
                    if (locating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        Icon(Icons.Filled.MyLocation, contentDescription = "定位到我的位置")
                    }
                }
                FloatingActionButton(onClick = {
                    pendingLatLng = mapRef.aMap?.cameraPosition?.target ?: currentLatLng
                    showAdd = true
                }) { Icon(Icons.Filled.Add, null) }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (MAP_ENABLED && !mapFailed) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        // 隐私合规：必须在创建 MapView 之前同意隐私政策（否则新版 SDK 会抛异常）。
                        // Application.onCreate 已初始化过，这里再做一次幂等兜底。
                        // 参数必须 (true, true)：(false, false) 会触发 errorCode 555571 白屏。
                        runCatching { MapsInitializer.updatePrivacyAgree(ctx, true) }
                        runCatching { MapsInitializer.updatePrivacyShow(ctx, true, true) }
                        // 创建 MapView；失败标记写入普通对象 MapRef（严禁在组合期写 Compose State！）
                        val mv: MapView? = try {
                            MapView(ctx).apply { onCreate(null) }
                        } catch (e: Throwable) {
                            mapRef.failed = true
                            null
                        }
                        mapRef.view = mv
                        mv?.let { m ->
                            runCatching { m.map.uiSettings.isMyLocationButtonEnabled = false }
                            runCatching {
                                m.map.setOnMapLongClickListener { latLng ->
                                    pendingLatLng = latLng
                                    showAdd = true
                                }
                            }
                        }
                        // 实际展示的视图（成功=MapView，失败=空占位），二者必须是同一个实例
                        val shown: View = mv ?: android.widget.FrameLayout(ctx)
                        // mapReady / mapFailed 状态写入全部延迟到 attach 回调（非组合期，安全）
                        shown.addOnAttachStateChangeListener(
                            object : View.OnAttachStateChangeListener {
                                override fun onViewAttachedToWindow(v: View) {
                                    if (mapRef.failed) {
                                        mapFailed = true
                                    } else {
                                        // attach 时 Activity 多半已处于 Resumed 态（ON_RESUME 不会再触发），
                                        // 必须在这里补一次 onResume；后续 pause/resume 由生命周期观察者接管。
                                        runCatching { mapRef.view?.onResume() }
                                        mapReady = true
                                    }
                                }
                                override fun onViewDetachedFromWindow(v: View) { /* onPause 由生命周期观察者处理 */ }
                            }
                        )
                        shown
                    }
                    // 生命周期（onPause/onDestroy）由上方 DisposableEffect 的
                    // LifecycleEventObserver / onDispose 统一处理，此处不再重复释放。
                )
            }
            if (mapFailed) {
                Column(
                    Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = AmberPrimary.copy(alpha = 0.12f))
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("地图组件加载失败", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "高德地图原生组件未能初始化（可能是设备缺少相应图形库）。已自动切换为足迹列表，不影响其它功能。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    LocationList(filteredLocations, vm::deleteLocation)
                }
            }

            // v1.0.7：顶部地点搜索栏（悬浮于地图之上，本地筛选足迹，不依赖高德 Key）
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Surface(
                    tonalElevation = 6.dp,
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("搜索足迹名称 / 备注 / 标签 / 日期") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "清空")
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                // 有词时就地展开筛选结果（本地足迹列表）；地图已降级为列表时主区已展示，避免重复
                if (query.isNotBlank() && !mapFailed) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        tonalElevation = 6.dp,
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(8.dp)) {
                            LocationList(filteredLocations, vm::deleteLocation)
                        }
                    }
                }
            }
        }
    }

    // v1.0.7：路径点记录页 —— 独立的足迹列表（本地搜索筛选，不依赖高德 Key）
    if (showList) {
        ModalBottomSheet(onDismissRequest = { showList = false }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "路径点记录（${filteredLocations.size}）",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (query.isNotBlank()) {
                    Text(
                        "搜索「$query」的筛选结果",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LocationList(filteredLocations, vm::deleteLocation)
            }
        }
    }

    if (showAdd) {
        AddLocationDialog(
            initialDate = todayStr(),
            onDismiss = { showAdd = false; pendingLatLng = null },
            onConfirm = { name, date, note, tags ->
                val ll = pendingLatLng ?: currentLatLng
                if (ll != null) {
                    vm.addLocation(name, ll.latitude, ll.longitude, date, note, tags)
                } else {
                    scope.launch { snackbar.showSnackbar("尚无坐标：请先点击定位获取位置") }
                }
                showAdd = false
                pendingLatLng = null
            }
        )
    }
}

/* ===================== 原有：足迹列表 / 添加对话框 ===================== */

@Composable
private fun LocationList(locations: List<LocationEntity>, onDelete: (LocationEntity) -> Unit) {
    if (locations.isEmpty()) {
        Text("还没有足迹，点击右下角 + 添加。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(
            locations,
            key = { it.id },
            contentType = { "locationRow" }
        ) { loc ->
            Card(Modifier.fillMaxWidth().animateItem()) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Place, null, tint = AmberPrimary)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(loc.name.ifBlank { "未命名足迹" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        if (loc.date.isNotBlank()) {
                            Text(loc.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!loc.note.isNullOrBlank()) {
                            Text(loc.note!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        }
                        Text("%.4f, %.4f".format(loc.lat, loc.lng), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onDelete(loc) }) { Icon(Icons.Filled.Delete, null) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddLocationDialog(
    initialDate: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, date: String, note: String?, tags: List<String>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(initialDate) }
    var note by remember { mutableStateOf("") }
    var tagsText by remember { mutableStateOf("") }
    var showDate by remember { mutableStateOf(false) }

    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加足迹") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(60) }, label = { Text("名称") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(date, {}, readOnly = true, label = { Text("日期") },
                    trailingIcon = { IconButton({ showDate = true }) { Icon(Icons.Filled.DateRange, null) } },
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(tagsText, { tagsText = it }, label = { Text("标签（逗号分隔）") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val tags = tagsText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    onConfirm(name.ifBlank { "未命名足迹" }, date, note.ifBlank { null }, tags)
                },
                enabled = name.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } }
    )

    if (showDate) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = {
                state.selectedDateMillis?.let { date = millisToDayStr(it) }
                showDate = false
            }) { Text("确定") } },
            dismissButton = { TextButton({ showDate = false }) { Text("取消") } }
        ) { DatePicker(state) }
    }
}
