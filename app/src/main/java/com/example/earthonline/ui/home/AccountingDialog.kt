package com.example.earthonline.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.earthonline.ui.components.AnimatedAlertDialog
import com.example.earthonline.ui.theme.AmberPrimary
import com.example.earthonline.util.openUrlInBrowser

private const val URL_123PAN =
    "https://1838010616.share.123pan.cn/123pan/ssAgTd-5GIT3?nottoken=1"
private const val URL_GITHUB =
    "https://github.com/LumiDesk/verifin/releases"

/**
 * 记账下载对话框（标准 AlertDialog）：两个下载渠道，点击后用系统浏览器打开链接。
 * 原生端不内置记账功能，也不在应用内下载 APK。
 */
@Composable
fun AccountingDownloadDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "记账",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "记账由独立 App「Verifin」提供，选择一个下载渠道，将在浏览器中打开。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                DownloadOption(
                    title = "123云盘下载",
                    subtitle = "base.apk · 55.82MB"
                ) { openUrlInBrowser(context, URL_123PAN) }
                DownloadOption(
                    title = "GitHub Releases 下载",
                    subtitle = "verifin 最新版 APK"
                ) { openUrlInBrowser(context, URL_GITHUB) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun DownloadOption(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(AmberPrimary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ListAlt,
                contentDescription = null,
                tint = AmberPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "›",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
