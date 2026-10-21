package com.example.earthonline.ui.achievements

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.earthonline.data.local.entity.AchievementEntity
import com.example.earthonline.ui.theme.AmberPrimary
import com.example.earthonline.util.AchievementSound
import kotlinx.coroutines.delay

/** 单条提示停留时长（ms） */
private const val TOAST_DURATION = 2800L

/**
 * 全局成就解锁提示 —— 右下角弹出卡片 + 短音效（Steam / 我的世界那种手感）。
 *
 * 挂在 MainScaffold 里（不是在成就页里），这样在任务页完成第 10 个任务、
 * 或在地图页标记第一个足迹时，同样能看到解锁提示。
 *
 * 多个成就连续解锁时用队列逐条播放，不会互相覆盖：
 * 队列 -> 只渲染队首 -> 停留 2.8s -> 出队 -> 下一条进场。
 */
@Composable
fun AchievementUnlockHost(vm: AchievementViewModel) {
    val context = LocalContext.current
    // 用可观察列表当队列：add/removeAt 会驱动重组，天然按顺序播放
    val queue = remember { mutableStateListOf<AchievementEntity>() }

    LaunchedEffect(Unit) {
        // 预热 SoundPool：把 wav 的解码开销挪到「解锁之前」，
        // 否则第一次解锁时现解码，声音会比卡片晚半拍。
        AchievementSound.prepare(context)
        vm.unlockEvents.collect { ach ->
            queue.add(ach)
            AchievementSound.play(context)
        }
    }

    val current = queue.firstOrNull()
    // key 用成就 id：队首换人时重启计时器，实现「逐条停留」
    LaunchedEffect(current?.id) {
        if (current != null) {
            delay(TOAST_DURATION)
            if (queue.isNotEmpty()) queue.removeAt(0)
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        AnimatedVisibility(
            visible = current != null,
            enter = slideInVertically(
                animationSpec = tween(durationMillis = 320)
            ) { it } + fadeIn(tween(220)),
            exit = slideOutHorizontally(tween(260)) { it / 2 } + fadeOut(tween(200))
        ) {
            if (current != null) {
                // 底部留出导航栏 + 安全区，卡片浮在导航栏上方而不是被压住
                AchievementToastCard(
                    title = current.title,
                    desc = current.desc,
                    isEgg = current.category == ACH_CAT_EGG,
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 88.dp)
                )
            }
        }
    }
}

@Composable
private fun AchievementToastCard(
    title: String,
    desc: String,
    isEgg: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AmberPrimary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                // v1.0.6 封板：Emoji → 圆角矢量图标（彩蛋用「自动非凡」，普通用「奖杯」）
                Icon(
                    if (isEgg) Icons.Rounded.AutoAwesome else Icons.Rounded.EmojiEvents,
                    contentDescription = null,
                    tint = AmberPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(Modifier.padding(end = 6.dp)) {
                Text(
                    if (isEgg) "隐藏成就解锁" else "成就解锁",
                    style = MaterialTheme.typography.labelSmall,
                    color = AmberPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (desc.isNotBlank()) {
                    Text(
                        desc,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
