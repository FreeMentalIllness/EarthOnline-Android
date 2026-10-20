package com.example.earthonline.ui.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.components.SettingsIconButton
import com.example.earthonline.ui.settings.AiConfigDialog
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
    var showConfig by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
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
                title = { Text("系统") },
                actions = {
                    IconButton(onClick = { showConfig = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "AI 配置")
                    }
                    SettingsIconButton(actions = moreActions)
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                /* v1.2.1：键盘弹起时把输入框顶上去。
                   顺序有讲究：imePadding() 必须在 navigationBarsPadding() 之后 ——
                   先让开系统手势条，再让开键盘，否则键盘压上来时输入框仍被手势条盖住一截。
                   前提是 Activity 设了 adjustResize（见 AndroidManifest）。 */
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { vm.onInputChange(it) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("说点什么…") },
                        enabled = !loading,
                        minLines = 1,
                        maxLines = 4
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = {
                        // 发送前离线拦截：无网时直接给明确文案，不等请求失败
                        if (!com.example.earthonline.util.NetworkMonitor.isOnlineNow(context)) {
                            scope.launch { snackbar.showSnackbar("网络不可用，请检查网络连接") }
                        } else {
                            vm.send()
                        }
                    }, enabled = !loading) {
                        Icon(Icons.Filled.Send, contentDescription = "发送")
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
                items(
                    messages,
                    // 气泡只有两种样式（我 / AI），contentType 让复用命中率最高
                    key = { it.content.hashCode().toString() + it.role },
                    contentType = { it.role }
                ) { msg -> MessageBubble(msg) }
                if (loading) {
                    item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                }
            }
        }
    }

    if (showConfig) {
        AiConfigDialog(
            initial = vm.parseAiConfig(aiConfigJson),
            onDismiss = { showConfig = false }
        ) { vm.saveAiConfig(it); showConfig = false }
    }
}

@Composable
private fun MessageBubble(msg: UiMessage) {
    val isUser = msg.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            tonalElevation = 1.dp,
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                msg.content,
                modifier = Modifier.padding(10.dp),
                fontWeight = if (isUser) FontWeight.Normal else FontWeight.Medium
            )
        }
    }
}
