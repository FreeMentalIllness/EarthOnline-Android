package com.example.earthonline.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.earthonline.ui.theme.AmberPrimary

/**
 * 全局 UI 基线常量。
 * 所有页面统一取这里的半径 / 间距，避免各页各写一份硬编码导致「细节不统一」。
 */
object UiDimens {
    /** 大卡片（整块分区）圆角 */
    val CardRadius: Dp = 16.dp
    /** 小卡片（列表项）圆角 */
    val ItemRadius: Dp = 12.dp
    /** 整页内容左右留白 */
    val ScreenPad: Dp = 16.dp
    /** 列表左右留白（有 TabRow 的页面用 12） */
    val ListPad: Dp = 12.dp
    /** 卡片内边距 */
    val CardPad: Dp = 14.dp
    /** 同级元素间距 */
    val Gap: Dp = 12.dp
    /** v1.2.0：主页「全局概览」四卡的固定高度（保证四张卡严格等高） */
    val OverviewCardHeight: Dp = 104.dp
}

/**
 * v1.0.19：模块语义色板（全局图标翻新）。
 * 对标设置页「彩色圆角图标芯片」的设计语言 —— 圆角小方块背景 + 现代矢量线条图标，
 * 按模块区分色相（任务蓝 / 背包黄 / 笔记绿…），统一线性风格，告别单调纯色。
 * 所有色值固定不随主题翻转（芯片背景走 14% 透明度，深浅色下对比度均达标）。
 */
object ModuleTints {
    val Tasks = Color(0xFF5B8DEF)        // 任务 · 蓝
    val Backpack = Color(0xFFF5A623)     // 背包 · 琥珀黄
    val Notes = Color(0xFF58B87C)        // 笔记 · 绿
    val Album = Color(0xFFA879F0)        // 相册 · 紫
    val Map = Color(0xFF3FBFAD)          // 地图 · 青
    val Accounting = Color(0xFFF08A5D)   // 记账 · 珊瑚橙
    val Data = Color(0xFF6C7BFF)         // 数据 · 靛蓝
    val Achievements = Color(0xFFF5B940) // 成就 · 金
    val Ai = Color(0xFF9B7BEF)           // AI · 星紫
    val System = Color(0xFF90A4AE)       // 系统 · 灰蓝
    val Feed = Color(0xFF5B8DEF)         // 最近动态 · 蓝
}

/**
 * v1.0.19：图标芯片 —— 圆角小方块背景 + 居中矢量图标（对标设置页图标语言）。
 * @param chipColor 模块语义色（见 [ModuleTints]），同时决定背景（14% 透明度）与前景
 * @param chipSize 方块边长（速览瓦片 38dp / 分区标题 26dp）
 * @param iconSize 图标边长（约为方块的 55%）
 */
@Composable
fun IconChip(
    icon: ImageVector,
    chipColor: Color,
    modifier: Modifier = Modifier,
    chipSize: Dp = 38.dp,
    iconSize: Dp = 21.dp,
    contentDescription: String? = null
) {
    Box(
        modifier = modifier
            .size(chipSize)
            .clip(RoundedCornerShape(chipSize * 0.32f))
            .background(chipColor.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = chipColor,
            modifier = Modifier.size(iconSize)
        )
    }
}

/** 分区标题：图标芯片 + 标题 + 右侧可选操作，全页统一。
 *  v1.0.17：新增 [icon] 参数 —— 传入矢量图标时优先渲染 Icons.Rounded 圆角矢量，
 *  不再依赖 emoji 字符（全局图标现代化）。
 *  v1.0.19：图标升级为 [IconChip] 彩色圆角芯片（[chipColor] 指定模块色，默认品牌琥珀），
 *  与设置页图标语言完全统一。 */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    chipColor: Color = AmberPrimary,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) {
            IconChip(
                icon,
                chipColor = chipColor,
                chipSize = 26.dp,
                iconSize = 15.dp
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

/** 统一的空状态占位 */
@Composable
fun EmptyHint(
    text: String,
    modifier: Modifier = Modifier,
    emoji: String = "",
    icon: ImageVector? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                if (emoji.isBlank()) text else "$emoji $text",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * v1.2.1：空状态引导。
 *
 * 空列表只写一行「暂无数据」是最省事的做法，但也是最浪费的：
 * 用户第一次进任务页，看到的是一堵白墙，不知道该按哪里、做了会得到什么。
 * 这里统一给出「大 emoji（插画位）+ 一句标题 + 一句人话解释 + 一个直达按钮」，
 * 让空状态承担「教用户怎么用」的职责。
 *
 * @param emoji 视觉主体。将来若换成真插画，改这一处即可（见 EmptyArt）
 * @param actionLabel / onAction 成对出现；给 null 则只展示文案（如「该分类下暂无物品」）
 */
@Composable
fun EmptyState(
    emoji: String = "",
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 插画位：圆形柔底 + 大图标（v1.0.17 起优先矢量 Icons.Rounded，emoji 仅作回退）
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(AmberPrimary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = AmberPrimary,
                    modifier = Modifier.size(40.dp)
                )
            } else if (emoji.isNotBlank()) {
                Text(emoji, style = MaterialTheme.typography.displayMedium)
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(2.dp))
            FilledTonalButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** 带进度数值的进度条（current/goal） */
@Composable
fun MeterBar(
    current: Int,
    goal: Int,
    modifier: Modifier = Modifier,
    showText: Boolean = true,
    text: String? = null
) {
    val g = goal.coerceAtLeast(1)
    val p = (current.coerceAtLeast(0).toFloat() / g).coerceIn(0f, 1f)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LinearProgressIndicator(
            progress = { p },
            modifier = Modifier.weight(1f),
            color = AmberPrimary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        if (showText) {
            Text(
                text ?: "$current/$g",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 弹窗进场动画：从 0.94 倍缩放 + 全透明渐入到原尺寸。
 *
 * 直接给 AlertDialog 套 graphicsLayer 而不是另起一个 Dialog：
 * 这样不用改任何调用点的结构，也不会破坏 AlertDialog 自带的返回键 / 外部点击关闭语义。
 * 动画只跑一次（visible 置 true 后不再变），不会在内容重组时反复触发。
 */
fun Modifier.dialogEnter(durationMs: Int = 240): Modifier = composed {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.94f,
        animationSpec = tween(durationMillis = durationMs),
        label = "dialogScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = durationMs),
        label = "dialogAlpha"
    )
    this.graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha)
}

/**
 * 带进场动画的 AlertDialog —— 签名与 Material3 的 AlertDialog 对齐（常用参数子集），
 * 因此全项目的 `AlertDialog(...)` 可以原地替换成 `AnimatedAlertDialog(...)`。
 *
 * 为什么不逐个弹窗手写动画：18 处调用点，逐处加一遍必然漏，且以后新增弹窗又会漏。
 * 收敛到一个包装函数，新增弹窗只要沿用它就是有动画的。
 */
@Composable
fun AnimatedAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    confirmButton: @Composable () -> Unit = {},
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: androidx.compose.ui.graphics.Shape = AlertDialogDefaults.shape,
    containerColor: androidx.compose.ui.graphics.Color = AlertDialogDefaults.containerColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties()
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier.dialogEnter(),
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = containerColor,
        tonalElevation = tonalElevation,
        properties = properties
    )
}

/** 小标签（分类 / 状态等），全页统一外观 */
@Composable
fun TagChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = AmberPrimary.copy(alpha = 0.12f),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
