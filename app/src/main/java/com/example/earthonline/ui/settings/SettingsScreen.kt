package com.example.earthonline.ui.settings

import com.example.earthonline.ui.components.AnimatedAlertDialog

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.earthonline.ui.components.MoreMenuActions
import com.example.earthonline.ui.components.SettingsIconButton
import com.example.earthonline.ui.navigation.Screen
import kotlinx.coroutines.launch

@Composable
fun SettingsRoute(moreActions: MoreMenuActions, vm: SettingsViewModel = hiltViewModel()) =
    SettingsScreen(vm = vm, moreActions = moreActions)

/* v1.0.9：分组入口图标芯片配色（对标参考设置页的彩色圆角方块图标） */
private val ChipPurple = Color(0xFF7C6BF2)
private val ChipTeal = Color(0xFF00A884)
private val ChipBlue = Color(0xFF2F7CF6)
private val ChipPink = Color(0xFFF4536E)
private val ChipAmber = Color(0xFFF59E0B)
private val ChipRed = Color(0xFFEF4444)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, moreActions: MoreMenuActions) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // v1.0.11：隐私政策 / 清除所有数据 两个弹窗状态（入口统一提到设置首页）
    var showPrivacy by remember { mutableStateOf(false) }
    var showDanger by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
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

            /* ==================== v1.0.12：设置目录重组（去重 + 按功能归类） ====================
               ① 去重：「通用」二级页里的「回收站」「AI 配置」、「数据与备份」二级页里的
                  「清空数据」「隐私政策」全部删除 —— 入口只在首页出现一次，不再两处并存。
               ② 归类：数据类操作（备份 / 回收站 / 清除数据）统一进「数据管理」，路径清晰；
                  隐私政策归入「关于」（它是说明性内容，不是数据操作）。 */
            item {
                SettingsSectionLabel("外观与体验")
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                    shape = RoundedCornerShape(18.dp),
                    tonalElevation = 1.dp
                ) {
                    Column {
                        SettingsGroupRow(
                            icon = Icons.Filled.Palette,
                            chip = ChipPurple,
                            label = "外观",
                            hint = "主题 · 壁纸 · 模糊度"
                        ) { moreActions.onNavigate(Screen.SettingsAppearance.route) }
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 66.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsGroupRow(
                            icon = Icons.Filled.Build,
                            chip = ChipTeal,
                            label = "通用",
                            hint = "提醒 · 通知 · 音效"
                        ) { moreActions.onNavigate(Screen.SettingsGeneral.route) }
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 66.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsGroupRow(
                            icon = Icons.Filled.SmartToy,
                            chip = ChipPurple,
                            label = "AI 配置",
                            hint = "接口地址 · 密钥 · 模型"
                        ) { moreActions.onNavigate(Screen.Ai.route) }
                    }
                }
            }
            item {
                SettingsSectionLabel("数据管理")
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                    shape = RoundedCornerShape(18.dp),
                    tonalElevation = 1.dp
                ) {
                    Column {
                        SettingsGroupRow(
                            icon = Icons.Filled.Backup,
                            chip = ChipBlue,
                            label = "数据与备份",
                            hint = "WebDAV 同步 · 本地自动备份"
                        ) { moreActions.onNavigate(Screen.SettingsBackupSync.route) }
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 66.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsGroupRow(
                            icon = Icons.Filled.DeleteOutline,
                            chip = ChipTeal,
                            label = "回收站",
                            hint = "已删除内容保留 30 天，可恢复"
                        ) { moreActions.onNavigate(Screen.RecycleBin.route) }
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 66.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsGroupRow(
                            icon = Icons.Filled.DeleteForever,
                            chip = ChipRed,
                            label = "清除所有数据",
                            hint = "清空本机用户数据，不可撤销"
                        ) { showDanger = true }
                    }
                }
            }
            item {
                SettingsSectionLabel("关于")
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                    shape = RoundedCornerShape(18.dp),
                    tonalElevation = 1.dp
                ) {
                    Column {
                        SettingsGroupRow(
                            icon = Icons.Filled.Info,
                            chip = ChipPink,
                            label = "关于",
                            hint = "版本 · 团队 · 赞助 · 更新"
                        ) { moreActions.onNavigate(Screen.SettingsAbout.route) }
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 66.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsGroupRow(
                            icon = Icons.Filled.Lock,
                            chip = ChipAmber,
                            label = "隐私政策",
                            hint = "数据收集 · 权限用途 · 第三方组件"
                        ) { showPrivacy = true }
                    }
                }
            }
        }
    }

    if (showPrivacy) {
        PrivacyPolicyDialog(onDismiss = { showPrivacy = false })
    }
    if (showDanger) {
        AnimatedAlertDialog(
            onDismissRequest = { showDanger = false },
            title = { Text("清除所有数据？") },
            text = {
                Text(
                    "将清空本机全部任务 / 背包 / 笔记 / 收藏 / 相册 / 成就等用户数据，" +
                        "操作不可撤销（主题、壁纸等应用设置会保留）。\n\n" +
                        "建议先在「数据与备份」导出一份 JSON 备份。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDanger = false
                    scope.launch {
                        val msg = vm.clearAllData(deleteCloud = false)
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                }) { Text("清除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDanger = false }) { Text("取消") }
            }
        )
    }
}

/**
 * v1.0.11：隐私政策弹窗（从「数据与备份」二级页提升到设置首页直达）。
 */
@Composable
private fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("隐私政策") },
        text = {
            Column {
                AppInfo.privacyPolicy.forEach { (title, body) ->
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                    )
                    Text(
                        body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("我知道了") }
        }
    )
}

/**
 * v1.0.10：分组标题（对标参考图二的组名样式）。
 */
@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.Bold,
            shadow = Shadow(
                color = Color.Black.copy(alpha = 0.35f),
                offset = Offset(0f, 1f),
                blurRadius = 3f
            )
        ),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)
    )
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
