package com.example.earthonline.ui.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.earthonline.ui.components.GlassTextField
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
        containerColor = Color.Transparent,
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
                if (loading) {
                    item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                }
            }
        }
    }

    if (showConfig) {
        AiConfigDialog(
            initial = vm.parseAiConfig(aiConfigJson),
            localModel = vm.parseLocalModel(localModelJson),
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
