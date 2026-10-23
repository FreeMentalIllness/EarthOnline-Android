package com.example.earthonline.ui.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.earthonline.ui.components.GlassDialog
import com.example.earthonline.ui.components.bouncyPress
import com.example.earthonline.ui.theme.GlassCard

/**
 * 全局统一组件（终极重构 · 工程一）。
 *
 * 红线：**业务页面严禁硬编码样式**，所有卡片 / 输入框 / 弹窗 / 菜单一律引用这里的组件。
 * 组件内部统一走 Design Tokens（[EoSpace] / [EoRadius]）与玻璃材质，
 * 改视觉只需改这一层，业务零感知。
 */

/**
 * 全局玻璃卡片。全站列表卡 / 概览卡的唯一材质入口。
 * 无黑边、无黑色 elevation —— 边缘是高光描边，投影只走暖色柔影。
 */
@Composable
fun EOGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(EoRadius.Card),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    GlassCard(
        modifier = modifier,
        shape = shape,
        onClick = onClick,
        content = content
    )
}

/**
 * 胶囊输入框（全局唯一输入控件）。
 * 圆角走 [EoRadius.Pill]，容器透明（透出玻璃底），绝无默认方形输入框。
 */
@Composable
fun EOInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    label: String? = null,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        textStyle = MaterialTheme.typography.bodyLarge,
        placeholder = placeholder?.let {
            { Text(it, style = MaterialTheme.typography.bodyMedium) }
        },
        label = label?.let {
            { Text(it, style = MaterialTheme.typography.bodyMedium) }
        },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else maxLines.coerceAtLeast(1),
        shape = RoundedCornerShape(EoRadius.Pill)
    )
}

/**
 * **全局居中固定弹窗**：所有次级菜单 / 选择器 / 表单弹层的唯一载体。
 *
 * v1.0.6.6（真机反馈批）：废弃 ModalBottomSheet 底部抽屉 —— 改走 [GlassDialog] 居中固定尺寸，
 * 单手可达、与全站玻璃质感同源。名称保留 `EOBottomSheet` 仅为历史调用点兼容，
 * 新代码一律使用 [EODialog]。
 */
@Composable
fun EODialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier
    ) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = EoSpace.Lg)
            )
        }
        content()
    }
}

/** 历史别名（原底部抽屉组件），内部已改为 [EODialog] 居中玻璃弹窗。 */
@Composable
fun EOBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) = EODialog(
    onDismissRequest = onDismissRequest,
    modifier = modifier,
    title = title,
    content = content
)

/**
 * **破坏性操作确认弹窗**（红线组件）。
 *
 * 红线：所有删除 / 清空数据 / 卸载模型等不可逆操作，**必须**强制调用本组件二次确认，
 * 业务页严禁直接执行删除。视觉上走 [EODialog] 居中玻璃弹窗，
 * 确认按钮用 error 色 + 破坏性图标，让用户在点下去之前就知道后果。
 */
@Composable
fun EOConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = "确认删除",
    dismissLabel: String = "取消",
    /** 破坏性=true 时用 error 配色与警示图标 */
    destructive: Boolean = true
) {
    EODialog(onDismissRequest = onDismiss, title = title) {
        if (destructive) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = EoSpace.Md)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(EoSpace.Sm))
                Text(
                    text = "此操作不可撤销",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = EoSpace.Xl)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EoSpace.Md, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
            androidx.compose.material3.Button(
                onClick = {
                    onDismiss()
                    onConfirm()
                },
                colors = if (destructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
                modifier = Modifier.bouncyPress()
            ) { Text(confirmLabel) }
        }
    }
}

/**
 * 诚实空状态（工程二 · 诚实原则）。
 *
 * 红线：**没有数据就显示优雅诚实的空状态**，绝不用占位假数据填充，
 * 更绝不允许把「未配置」渲染成「已就绪」。
 * 需要引导时传 [actionLabel] + [onAction]（例如「去配置 AI」）。
 */
@Composable
fun EOEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Box(modifier = modifier.fillMaxWidth().padding(EoSpace.Xl)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(EoSpace.Sm)
        ) {
            if (icon != null) {
                Surface(
                    shape = RoundedCornerShape(EoRadius.Chip),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Box(Modifier.padding(EoSpace.Md)) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
                    }
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(EoSpace.Sm))
                TextButton(onClick = onAction, modifier = Modifier.bouncyPress()) {
                    Text(actionLabel)
                }
            }
        }
    }
}
