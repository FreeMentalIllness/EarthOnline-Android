package com.example.earthonline.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.components.SettingsIconButton
import com.example.earthonline.ui.navigation.Screen

@Composable
fun SettingsRoute(moreActions: MoreMenuActions, vm: SettingsViewModel = hiltViewModel()) =
    SettingsScreen(vm = vm, moreActions = moreActions)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, moreActions: MoreMenuActions) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                actions = { SettingsIconButton(actions = moreActions, visible = false) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "所有设置项均在下方分组中，点击进入对应的二级页面。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            /* ==================== 分组入口：点击进入独立二级页（无折叠面板） ==================== */
            item {
                SettingsNavItem(
                    icon = Icons.Filled.Palette,
                    label = "外观",
                    hint = "主题 · 壁纸"
                ) { moreActions.onNavigate(Screen.SettingsAppearance.route) }
            }
            item {
                SettingsNavItem(
                    icon = Icons.Filled.Build,
                    label = "通用",
                    hint = "提醒 · 音效 · AI 对话"
                ) { moreActions.onNavigate(Screen.SettingsGeneral.route) }
            }
            item {
                SettingsNavItem(
                    icon = Icons.Filled.Backup,
                    label = "数据与备份",
                    hint = "WebDAV 同步 · 本地自动备份 · 隐私政策"
                ) { moreActions.onNavigate(Screen.SettingsBackupSync.route) }
            }
            item {
                SettingsNavItem(
                    icon = Icons.Filled.Info,
                    label = "关于",
                    hint = "版本 · 团队 · 赞助 · 更新"
                ) { moreActions.onNavigate(Screen.SettingsAbout.route) }
            }
        }
    }
}
