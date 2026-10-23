package com.example.earthonline.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.earthonline.ui.theme.AmberPrimary

/**
 * 全站标准输入框（v1.0.6.5 全局 UI 统一死命令）。
 *
 * 统一视觉规范：胶囊圆角（RoundedCornerShape(50)）+ 半透明玻璃底 + 细高光描边 +
 * 透明指示线（去掉 Material 默认下划线），光标/主题色与全局一致。
 * 所有页面的文本输入（主页搜索、笔记标题/标签、AI 对话、地图搜索、设置项、
 * 各类对话框表单）一律使用本组件，彻底消除风格杂乱。
 */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 4,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge
) {
    val shape = RoundedCornerShape(50)
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.border(
            width = 1.dp,
            color = if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
            shape = shape
        ),
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = if (label == null) null else {
            val l = label
            { Text(l) }
        },
        placeholder = if (placeholder == null) null else {
            val p = placeholder
            { Text(p, style = MaterialTheme.typography.bodyMedium) }
        },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        isError = isError,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = shape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
            disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.30f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            cursorColor = AmberPrimary,
            focusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}
