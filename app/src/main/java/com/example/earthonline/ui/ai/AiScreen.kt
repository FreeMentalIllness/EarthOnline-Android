package com.example.earthonline.ui.ai

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.earthonline.ui.components.GlassTextField
import com.example.earthonline.ui.components.ModuleTints
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.settings.AiConfigDialog
import com.example.earthonline.ui.theme.GlassSurface
import kotlinx.coroutines.launch

@Composable
fun AiRoute(moreActions: MoreMenuActions, vm: AiViewModel = hiltViewModel()) =
    AiScreen(vm = vm, moreActions = moreActions)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiScreen(vm: AiViewModel, moreActions: MoreMenuActions) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val input by vm.input.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val aiConfigJson by vm.aiConfigFlow.collectAsStateWithLifecycle(initialValue = "")
    val localModelJson by vm.localModelJson.collectAsStateWithLifecycle(initialValue = "")
    var showConfig by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val listState = rememberLazyListState()
    // v1.0.6.14（死命令 #2 重组优化）：打字指示器显隐用 derivedStateOf 收敛——
    // 仅在 loading 或最后一条 assistant 消息空→非空跃迁时才触发 LazyColumn 内容重组，
    // 流式过程中每个 token 到达不再引发整屏级重新求值
    val showTyping by remember {
        derivedStateOf {
            val last = messages.lastOrNull()
            loading && last != null && last.role == "assistant" && last.content.isEmpty()
        }
    }

    // 退出对话页时中断后台推理协程：本地推理在 viewModelScope 内跑，页面销毁即取消，
    // 这里再显式 cancelGeneration() 兜底，杜绝「页面已销毁、协程仍在写 UI State」的越界闪退。
    DisposableEffect(Unit) {
        onDispose { vm.cancelGeneration() }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            runCatching { listState.animateScrollToItem(messages.lastIndex) }
        }
    }
    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            vm.clearError()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("系统") },
                actions = {
                    IconButton(onClick = { showConfig = true }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "AI 配置")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            // 液态玻璃胶囊输入条：与主应用毛玻璃风格统一
            GlassSurface(
                shape = RoundedCornerShape(24.dp),
                showBorder = true,
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlassTextField(
                        value = input,
                        onValueChange = { vm.onInputChange(it) },
                        modifier = Modifier.weight(1f),
                        placeholder = "说点什么…",
                        enabled = !loading,
                        minLines = 1,
                        maxLines = 4,
                        singleLine = false
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = {
                        // 发送前离线拦截：无网时直接给明确文案，不等请求失败
                        if (!com.example.earthonline.util.NetworkMonitor.isOnlineNow(context)) {
                            scope.launch { snackbar.showSnackbar("网络不可用，请检查网络连接") }
                        } else {
                            vm.send()
                        }
                    }, enabled = !loading) {
                        Icon(Icons.Rounded.Send, contentDescription = "发送")
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // v1.0.6：用稳定索引 key 代替 content.hashCode() —— 相同内容（或两次相同回复）
                // 曾触发 Compose「Key was already used」致命崩溃（AI 对话两次必崩的根因）。
                items(
                    count = messages.size,
                    key = { index -> "ai_msg_$index" },
                    contentType = { index -> messages[index].role }
                ) { index -> MessageBubble(messages[index]) }
                // 死命令 #1 状态联动：仅在「请求中且首个流式字符尚未上屏」时显示三点跳动；
                // 第一个字一到，最后一条 assistant 消息非空 → 指示器瞬间消失，替换为文字。
                if (showTyping) {
                    item(key = "ai_typing") { TypingIndicator() }
                }
            }
        }
    }

    if (showConfig) {
        AiConfigDialog(
            initial = vm.parseAiConfig(aiConfigJson),
            localModel = vm.parseLocalModel(localModelJson),
            onLoadedChange = { vm.setLocalModelLoaded(it) },
            onDismiss = { showConfig = false }
        ) { cfg, local ->
            vm.saveAiConfig(cfg)
            vm.saveLocalModel(local)
            showConfig = false
        }
    }
}

@Composable
private fun MessageBubble(msg: UiMessage) {
    val isUser = msg.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        GlassSurface(
            shape = RoundedCornerShape(16.dp),
            showBorder = true,
            // 死命令 #6 视觉融合：用户气泡提高玻璃不透明度区分阵营，材质仍是同一套液态玻璃
            tintOverride = if (isUser) 0.9f else null,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                msg.content,
                modifier = Modifier.padding(12.dp),
                fontWeight = if (isUser) FontWeight.Normal else FontWeight.Medium
            )
        }
    }
}

/**
 * 死命令 #1：三点跳动打字指示器（Typing Indicator）。
 * - 替换旧的 LinearProgressIndicator（气泡下方横穿全屏的进度条，丑且突兀）；
 * - `rememberInfiniteTransition` 驱动三枚圆点错峰上跳，极度轻量（仅 translationY）；
 * - 颜色用应用主题的 AI 暖色（ModuleTints.Ai · 星紫）；
 * - 状态联动由调用方控制：最后一个字流式上屏后本组件直接离开组合，动画零残留。
 */
@Composable
private fun TypingIndicator() {
    val tint = ModuleTints.Ai
    val transition = rememberInfiniteTransition(label = "typing")
    // 三枚圆点各自持 State（错峰 150ms），值在 graphicsLayer 块内延迟读取 ——
    // 动画每帧只重绘图层，不触发任何重组，极度轻量
    val d0 = transition.animateFloat(
        initialValue = 0f, targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(340, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(0)
        ),
        label = "dot0"
    )
    val d1 = transition.animateFloat(
        initialValue = 0f, targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(340, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(150)
        ),
        label = "dot1"
    )
    val d2 = transition.animateFloat(
        initialValue = 0f, targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(340, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(300)
        ),
        label = "dot2"
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        GlassSurface(
            shape = RoundedCornerShape(16.dp),
            showBorder = true
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val dots = listOf(d0, d1, d2)
                dots.forEachIndexed { i, state ->
                    Box(
                        Modifier
                            .size(7.dp)
                            .graphicsLayer { translationY = state.value }
                            .background(tint.copy(alpha = 0.95f - i * 0.18f), CircleShape)
                            .clip(CircleShape)
                    )
                }
            }
        }
    }
}
