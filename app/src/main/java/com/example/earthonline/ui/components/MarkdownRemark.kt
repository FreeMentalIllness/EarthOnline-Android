package com.example.earthonline.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.earthonline.ui.theme.AmberPrimary
import com.example.earthonline.ui.theme.OnAmber

/**
 * 轻量 Markdown 备注渲染：把任务备注里的 Markdown 待办行（`- [ ]` / `- [x]`）
 * 渲染为标准、美观的 Compose 复选框（圆角 + 品牌琥珀色），其余行按普通文本展示。
 *
 * 设计取舍：不引入完整 Markdown 解析库（重且慢），只做「待办行 → 复选框」这一件事，
 * 足以覆盖用户在任务详情里写清单的核心场景；勾选可回写（[onToggle] 拿到重排后的全文）。
 *
 * @param note 原始备注（Markdown 文本）
 * @param onToggle 点击复选框时回调，参数为「切换该待办勾选态后」的完整新文本；
 *                不传则为纯展示（视觉可点但不变更数据）。
 */
@Composable
fun MarkdownRemark(
    note: String,
    modifier: Modifier = Modifier,
    onToggle: ((String) -> Unit)? = null
) {
    val todoRe = remember { Regex("^\\s*-\\s*\\[([ xX])\\]\\s*(.*)$") }
    val lines = note.lines()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        lines.forEachIndexed { index, line ->
            val m = todoRe.find(line)
            if (m != null) {
                val checked = m.groupValues[1].equals("x", ignoreCase = true)
                val text = m.groupValues[2]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { newChecked ->
                            if (onToggle != null) {
                                val rebuilt = lines.toMutableList().apply {
                                    this[index] = "- [${if (newChecked) "x" else " "}] $text"
                                }
                                onToggle(rebuilt.joinToString("\n"))
                            }
                        },
                        // 圆角 + 品牌琥珀色，贴合液态玻璃质感
                        colors = CheckboxDefaults.colors(
                            checkedColor = AmberPrimary,
                            checkmarkColor = OnAmber,
                            uncheckedColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    androidx.compose.material3.Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (checked) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        textDecoration = if (checked) TextDecoration.LineThrough else null
                    )
                }
            } else if (line.isNotBlank()) {
                androidx.compose.material3.Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}
