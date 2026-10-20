package com.example.earthonline.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.components.SettingsIconButton
import com.example.earthonline.ui.navigation.Screen

@Composable
fun SettingsRoute(moreActions: MoreMenuActions, vm: SettingsViewModel = hiltViewModel()) =
    SettingsScreen(vm = vm, moreActions = moreActions)

/* v1.0.9：分组入口图标芯片配色（对标参考设置页的彩色圆角方块图标） */
private val ChipPurple = Color(0xFF7C6BF2)
private val ChipTeal = Color(0xFF00A884)
private val ChipBlue = Color(0xFF2F7CF6)
private val ChipPink = Color(0xFFF4536E)

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

            /* ==================== v1.0.9：对标参考图的单卡分组 ====================
               四个入口合并进一张半透明圆角卡片，行间细分隔线；
               每行 = 彩色圆角图标芯片 + 标题/副标题 + 右箭头，间距与参考图一致。 */
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(18.dp),
                    tonalElevation = 1.dp
                ) {
                    Column {
                        SettingsGroupRow(
                            icon = Icons.Filled.Palette,
                            chip = ChipPurple,
                            label = "外观",
                            hint = "主题 · 壁纸 · 液态玻璃"
                        ) { moreActions.onNavigate(Screen.SettingsAppearance.route) }
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 66.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsGroupRow(
                            icon = Icons.Filled.Build,
                            chip = ChipTeal,
                            label = "通用",
                            hint = "提醒 · 音效 · AI 对话"
                        ) { moreActions.onNavigate(Screen.SettingsGeneral.route) }
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 66.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsGroupRow(
                            icon = Icons.Filled.Backup,
                            chip = ChipBlue,
                            label = "数据与备份",
                            hint = "WebDAV 同步 · 本地自动备份 · 隐私政策"
                        ) { moreActions.onNavigate(Screen.SettingsBackupSync.route) }
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 66.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsGroupRow(
                            icon = Icons.Filled.Info,
                            chip = ChipPink,
                            label = "关于",
                            hint = "版本 · 团队 · 赞助 · 更新"
                        ) { moreActions.onNavigate(Screen.SettingsAbout.route) }
                    }
                }
            }
        }
    }
}

/**
 * v1.0.9：分组卡内单行 —— 彩色圆角图标芯片（38dp / 圆角 12dp / 图标 20dp）
 * + 标题加粗 + 副标题 + 右箭头，排版对标参考设置页。
 */
@Composable
private fun SettingsGroupRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    chip: Color,
    label: String,
    hint: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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
