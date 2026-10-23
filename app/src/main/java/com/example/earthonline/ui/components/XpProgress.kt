package com.example.earthonline.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.earthonline.ui.design.EoRadius
import com.example.earthonline.ui.design.EoSpace
import com.example.earthonline.util.GamificationEngine
import com.example.earthonline.util.LevelInfo

/**
 * XP / 等级视觉组件（终极重构 · 工程三）。
 *
 * 1. [AnimatedXpBar]：等级进度条**平滑增长** —— 用低阻尼弹簧驱动 progress，
 *    获得 XP 时进度条是「弹」上去的，不是瞬间跳变；
 * 2. [XpGainBadge]：获得 XP 时飘出 `+N XP` 微动效（上浮 + 淡出），
 *    让「我做了一件事 → 我真的变强了」这件事看得见。
 *
 * 连击机制已废弃：不再有任何 combo 放大逻辑，成长只由真实累计行为决定。
 */

/**
 * 等级进度条（平滑增长动画）。
 * @param info 由 [GamificationEngine.levelInfo] 算出的等级快照
 */
@Composable
fun AnimatedXpBar(
    info: LevelInfo,
    modifier: Modifier = Modifier
) {
    // 低阻尼弹簧：进度变化时有轻微回弹的「生长感」，而非机械跳变
    val progress by animateFloatAsState(
        targetValue = info.progress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "xp_progress"
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Lv.${info.level} · ${info.title}",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = if (info.level >= GamificationEngine.MAX_LEVEL) {
                    "已满级 · ${info.totalXp} XP"
                } else {
                    "${info.xpIntoLevel} / ${info.xpForNext} XP"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = EoSpace.Sm)
                .height(8.dp)
                .clip(RoundedCornerShape(EoRadius.Pill))
        )
    }
}

/**
 * 飘出 `+N XP` 微动效。
 * @param amount 本次获得的 XP（<=0 时不显示）
 * @param trigger 变化即触发一次（传任务完成次数等单调递增的值）
 */
@Composable
fun XpGainBadge(
    amount: Int,
    trigger: Int,
    modifier: Modifier = Modifier
) {
    if (amount <= 0) return
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val scale = remember { Animatable(0.6f) }

    LaunchedEffect(trigger) {
        if (trigger <= 0) return@LaunchedEffect
        alpha.snapTo(0f)
        offsetY.snapTo(10f)
        scale.snapTo(0.6f)
        // 弹入
        scale.animateTo(1.15f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium))
        alpha.animateTo(1f, tween(120))
        scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
        // 上浮 + 淡出
        offsetY.animateTo(-34f, tween(700))
        alpha.animateTo(0f, tween(420))
    }

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Text(
            text = "+$amount XP",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.graphicsLayer {
                this.alpha = alpha.value
                this.translationY = offsetY.value
                this.scaleX = scale.value
                this.scaleY = scale.value
            }
        )
    }
}
