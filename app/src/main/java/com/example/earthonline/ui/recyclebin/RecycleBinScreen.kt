package com.example.earthonline.ui.recyclebin

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Card
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.earthonline.data.repository.RecycleBinRepository.Entry
import com.example.earthonline.ui.components.EmptyState
import com.example.earthonline.ui.components.UiDimens
import com.example.earthonline.ui.components.AnimatedAlertDialog

/**
 * v1.0.5 回收站页：任务 / 灵感 / 物品 / 收藏 的软删行统一列表。
 * - 「恢复」回到原页面（任务连带恢复子任务）；
 * - 「彻底删除」不可逆（确认对话框）；条目保留 30 天后自动物理清理。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinRoute(
    onBack: () -> Unit,
    vm: RecycleBinViewModel = hiltViewModel()
) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<Entry?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("回收站") },
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
                .padding(horizontal = UiDimens.ListPad)
        ) {
            Text(
                "删除的任务、灵感、物品与收藏在这里保留 30 天，之后自动清除。内容仅保存在本机，不会同步到云端。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            if (entries.isEmpty()) {
                EmptyState(
                    emoji = "🗑️",
                    title = "回收站是空的",
                    message = "删除的内容会先到这里，30 天内都可以随时找回。"
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(entries, key = { it.kind + it.id }) { entry ->
                        RecycleCard(
                            entry = entry,
                            onRestore = { vm.restore(entry) },
                            onDelete = { confirmDelete = entry }
                        )
                    }
                }
            }
        }
    }

    confirmDelete?.let { entry ->
        AnimatedAlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("彻底删除？") },
            text = { Text("「${entry.title}」将被永久删除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteForever(entry)
                    confirmDelete = null
                }) { Text("彻底删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun kindLabel(kind: String): String = when (kind) {
    "task" -> "任务"
    "memo" -> "灵感"
    "item" -> "物品"
    else -> "收藏"
}

@Composable
private fun kindEmoji(kind: String): String = when (kind) {
    "task" -> "🎯"
    "memo" -> "💡"
    "item" -> "🎒"
    else -> "⭐"
}

@Composable
private fun RecycleCard(
    entry: Entry,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(UiDimens.ItemRadius)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(kindEmoji(entry.kind), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title.ifBlank { "（无标题）" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
                Text(
                    "${kindLabel(entry.kind)} · 删除于 ${entry.deletedAt.take(10)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRestore) {
                Icon(Icons.Rounded.Restore, contentDescription = "恢复", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.DeleteForever,
                    contentDescription = "彻底删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
