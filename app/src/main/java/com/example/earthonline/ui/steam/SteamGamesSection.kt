package com.example.earthonline.ui.steam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.earthonline.data.steam.SteamGame
import com.example.earthonline.ui.components.bouncyPress
import com.example.earthonline.ui.design.EODialog
import com.example.earthonline.ui.design.EOConfirmDialog
import com.example.earthonline.ui.design.EOEmptyState
import com.example.earthonline.ui.design.EOGlassCard
import com.example.earthonline.ui.design.EOInput
import com.example.earthonline.ui.design.EoRadius
import com.example.earthonline.ui.design.EoSpace

/**
 * v1.0.6.6（真机反馈 #8）：背包「收藏」页的**游戏库入口**。
 *
 * Steam 游戏数据不再内联展示在收藏列表里（会把收藏顶下去、语义也变杂），
 * 这里只留一张入口卡，点击跳 [SteamLibraryRoute] 独立二级页。
 */
@Composable
fun SteamLibraryEntry(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    EOGlassCard(
        modifier = modifier,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = EoSpace.Md, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.SportsEsports,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(EoSpace.Md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "我的游戏库",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "接入 Steam，把玩过的游戏当作收藏品展示",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = "进入游戏库",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Steam 游戏数据**独立二级页**（v1.0.6.6）。
 *
 * 从背包「收藏 → 我的游戏库」入口进入；顶部带返回栏，返回即弹栈回收藏页。
 * 内容整页可滚动，配置 / 刷新 / 断开都在本页完成，收藏页只保留入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteamLibraryRoute(onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("我的游戏库") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            SteamGamesSection(modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * Steam 游戏数据模块（终极重构 · 工程三）。
 *
 * 设计红线：
 *  1. **诚实原则** —— 未配置显示「未配置」引导、拉取失败显示真实原因，
 *     绝不用假数据填充，绝不把「拉取不到」渲染成「已就绪」；
 *  2. **业务页严禁硬编码样式** —— 全部走 EO 组件族与 Design Tokens；
 *  3. 破坏性操作（断开 Steam）走 [EOConfirmDialog] 二次确认。
 */
@Composable
fun SteamGamesSection(
    modifier: Modifier = Modifier,
    vm: SteamViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val configured by vm.configured.collectAsStateWithLifecycle()

    var showConfig by remember { mutableStateOf(false) }
    var showDisconnect by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("游戏数据", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (configured) "来自 Steam 游戏库" else "未接入 Steam",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { vm.refresh() },
                enabled = configured && !busy,
                modifier = Modifier.bouncyPress()
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = "刷新游戏数据")
            }
            IconButton(onClick = { showConfig = true }, modifier = Modifier.bouncyPress()) {
                Icon(Icons.Rounded.Settings, contentDescription = "配置 Steam")
            }
        }

        Spacer(Modifier.height(EoSpace.Md))

        when (val s = state) {
            SteamUiState.Unconfigured -> EOEmptyState(
                title = "尚未接入 Steam",
                message = "配置 Steam ID 与 Web API Key 后，你的游戏库会以收藏品的形式展示在这里。",
                icon = Icons.Rounded.Info,
                actionLabel = "去配置",
                onAction = { showConfig = true }
            )

            SteamUiState.Loading -> EOEmptyState(
                title = "正在拉取游戏库…",
                message = "正在向 Steam 请求你的游戏数据，请稍候。",
                icon = Icons.Rounded.Info
            )

            is SteamUiState.Error -> EOEmptyState(
                title = "拉取失败",
                message = s.reason,
                icon = Icons.Rounded.Info,
                actionLabel = "重试",
                onAction = { vm.refresh() }
            )

            is SteamUiState.Ready -> {
                if (s.games.isEmpty()) {
                    // 诚实：库里确实没有游戏，就如实说，不编造
                    EOEmptyState(
                        title = "游戏库是空的",
                        message = "这个 Steam 账号暂未拥有任何游戏，或隐私设置未公开游戏详情。",
                        icon = Icons.Rounded.Info,
                        actionLabel = "重新拉取",
                        onAction = { vm.refresh() }
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(EoSpace.Md)) {
                        Text(
                            text = "共 ${s.totalCount} 款 · 展示游玩时长最长的 ${s.games.size} 款",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        s.games.forEach { game -> SteamGameCard(game) }
                    }
                }
            }
        }
    }

    if (showConfig) {
        SteamConfigSheet(
            initialId = vm.steamId.value,
            initialKey = vm.steamApiKey.value,
            onSave = { id, key ->
                vm.saveConfig(id, key)
                showConfig = false
            },
            onDisconnect = {
                showConfig = false
                showDisconnect = true
            },
            onDismiss = { showConfig = false }
        )
    }

    if (showDisconnect) {
        EOConfirmDialog(
            title = "断开 Steam 接入",
            message = "将清除已保存的 Steam ID、API Key 与本地游戏数据缓存。这不会删除你的任何游戏或存档。",
            confirmLabel = "断开",
            destructive = true,
            onConfirm = { vm.disconnect() },
            onDismiss = { showDisconnect = false }
        )
    }
}

/** 单款游戏的卡片化展示（当作一种特殊的「收藏品」） */
@Composable
private fun SteamGameCard(game: SteamGame) {
    EOGlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(EoSpace.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = game.headerImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(108.dp)
                    .height(50.dp)
                    .clip(RoundedCornerShape(EoRadius.Chip))
            )
            Spacer(Modifier.width(EoSpace.Md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = game.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val hours = game.playtimeHours
                Text(
                    // 时长为 0 如实显示「未游玩」，不美化成别的说法
                    text = if (game.playtimeMinutes <= 0) "尚未游玩"
                    else "总时长 ${"%.1f".format(hours)} 小时",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (game.playtime2Weeks > 0) {
                    Text(
                        text = "最近两周 ${"%.1f".format(game.playtime2Weeks / 60f)} 小时",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/** Steam 配置面板（次级菜单统一走 EOBottomSheet） */
@Composable
private fun SteamConfigSheet(
    initialId: String,
    initialKey: String,
    onSave: (String, String) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    var id by remember { mutableStateOf(initialId) }
    var key by remember { mutableStateOf(initialKey) }

    EODialog(onDismissRequest = onDismiss, title = "配置 Steam") {
        Text(
            text = "填写 Steam 64 位 ID 与 Web API Key。数据只用于读取你的游戏库，不会做任何写操作。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = EoSpace.Lg)
        )
        EOInput(
            value = id,
            onValueChange = { id = it },
            placeholder = "Steam 64 位 ID",
            label = "Steam ID"
        )
        Spacer(Modifier.height(EoSpace.Md))
        EOInput(
            value = key,
            onValueChange = { key = it },
            placeholder = "Web API Key",
            label = "API Key"
        )
        Spacer(Modifier.height(EoSpace.Xl))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (initialId.isNotBlank() || initialKey.isNotBlank()) {
                TextButton(onClick = onDisconnect) { Text("断开接入") }
            } else {
                Spacer(Modifier.size(0.dp))
            }
            TextButton(
                onClick = { onSave(id.trim(), key.trim()) },
                modifier = Modifier.bouncyPress()
            ) { Text("保存并拉取") }
        }
    }
}
