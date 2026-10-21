package com.example.earthonline.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.earthonline.data.local.datastore.SettingsDataStore
import com.example.earthonline.data.local.entity.ActivityEntity
import com.example.earthonline.data.repository.ActivityRepository
import com.example.earthonline.ui.components.AnimatedAlertDialog
import com.example.earthonline.ui.components.EmptyHint
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.theme.AmberPrimary
import com.example.earthonline.util.nowIso
import com.example.earthonline.util.uid
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 「全部动态」页 UI 状态：完整动态列表 + 类型筛选 + 首页条数设置回显。
 * 独立 ViewModel（不共用 HomeViewModel）：此页只需 activities 流，
 * 复用 HomeViewModel 会让 8 个聚合流在返回栈上双份运行。
 */
data class AllActsUiState(
    val acts: List<ActivityEntity> = emptyList(),
    val feedLimit: Int = 3
)

@HiltViewModel
class AllActivitiesViewModel @Inject constructor(
    private val activityRepo: ActivityRepository,
    private val settingsDs: SettingsDataStore
) : ViewModel() {

    private val actsFlow = activityRepo.observeRecent(200)

    val state: StateFlow<AllActsUiState> =
        combine(actsFlow, settingsDs.homeFeedLimit) { acts, limit ->
            AllActsUiState(acts = acts, feedLimit = limit)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AllActsUiState())

    /** 设置首页「最近动态」显示条数（0 = 不限制） */
    fun setFeedLimit(n: Int) {
        viewModelScope.launch { settingsDs.setHomeFeedLimit(n) }
    }

    /** 添加一条自定义动态 */
    fun addCustomActivity(title: String) {
        val t = title.trim()
        if (t.isBlank()) return
        viewModelScope.launch {
            activityRepo.add(
                ActivityEntity(id = uid("act"), time = nowIso(), kind = "custom", title = t.take(60))
            )
        }
    }

    /** 删除一条动态（自定义动态可删；系统自动生成的动态删除后会随行为再次产生） */
    fun deleteActivity(id: String) {
        viewModelScope.launch { activityRepo.delete(id) }
    }
}

/**
 * 全部动态列表页：首页「最近动态」右上角「全部」进入。
 * - 完整动态列表（类型筛选与首页共用一份 DataStore 状态）
 * - 顶部可设置首页显示条数（3/5/10/20/不限）
 * - 「+ 自定义」手动添加一条动态事件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllActivitiesRoute(
    onBack: () -> Unit,
    moreActions: MoreMenuActions,
    vm: AllActivitiesViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // 类型筛选与首页共用一份 DataStore 状态；本页展示全部（custom 始终可见）
    val filtered = state.acts

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("全部动态（${filtered.size}）") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "添加自定义动态", tint = AmberPrimary)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // 首页显示条数设置
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "首页「最近动态」显示条数",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(3 to "3 条", 5 to "5 条", 10 to "10 条", 20 to "20 条", 0 to "不限").forEach { (n, label) ->
                            FilterChip(
                                selected = state.feedLimit == n,
                                onClick = { vm.setFeedLimit(n) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                EmptyHint("暂无动态，点右上角 + 添加一条自定义动态吧", emoji = "⚡")
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filtered, key = { it.id }, contentType = { "actRow" }) { act ->
                        val (icon, verb) = when (act.kind) {
                            "ach" -> "🏆" to "解锁成就"
                            "task" -> "📋" to "完成任务"
                            "item" -> "🎒" to "获得物品"
                            "custom" -> "⭐" to "自定义事件"
                            else -> "💭" to "记录"
                        }
                        Card(Modifier.fillMaxWidth().animateItem()) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.width(64.dp)) {
                                    Text(act.time.take(10), style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(act.time.drop(11).take(5), style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(icon)
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        act.title.ifBlank { "(未命名动态)" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(verb, style = MaterialTheme.typography.labelSmall,
                                        color = if (act.kind == "custom") AmberPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = {
                                    vm.deleteActivity(act.id)
                                    scope.launch { snackbar.showSnackbar("已删除该条动态") }
                                }, modifier = Modifier.size(30.dp)) {
                                    Icon(Icons.Filled.Delete, contentDescription = "删除",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddActivityDialog(
            onDismiss = { showAdd = false },
            onConfirm = { title ->
                vm.addCustomActivity(title)
                showAdd = false
                scope.launch { snackbar.showSnackbar("已添加自定义动态") }
            }
        )
    }
}

@Composable
private fun AddActivityDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⭐ 添加自定义动态") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    title, { title = it.take(60) },
                    label = { Text("事件内容") },
                    placeholder = { Text("例如：开始一段新旅程") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "自定义动态会同时出现在主页「最近动态」与时间轴附近的动态流中。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title.trim()) }, enabled = title.isNotBlank()) { Text("添加") }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } }
    )
}
