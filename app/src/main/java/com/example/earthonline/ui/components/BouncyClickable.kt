package com.example.earthonline.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 全局按压回弹反馈（死磕细节批）。
 *
 * 逻辑：按下时整块以极低阻尼弹簧缩到 0.98 倍，松手极速弹回 1.0 倍 —— 跟手、有「果冻感」。
 * 视觉缩放用 [pointerInput] 监听按压态驱动 [graphicsLayer]，点击语义与无障碍仍交给原生
 * [clickable]（indication=null 废弃默认 Ripple，符合全局统一玻璃质感）。
 *
 * 已接入 [com.example.earthonline.ui.theme.GlassSurface]，全站卡片 / 列表项 / 入口自动获得反馈；
 * 悬浮按钮等自带 onClick 的组件用 [bouncyPress] 仅吃视觉，不抢点击事件。
 */
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "bouncyScale"
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures(
                onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                }
            )
        }
        .clickable(
            enabled = enabled,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
}

/**
 * 仅视觉的按压回弹（不接管点击）：供 FloatingActionButton / IconButton 等自带 onClick 的组件使用，
 * 让它们也拥有与卡片一致的「按下去缩一缩、松手弹回来」手感，又不干扰自身点击逻辑。
 */
fun Modifier.bouncyPress(enabled: Boolean = true): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "bouncyPressScale"
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures(
                onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                }
            )
        }
}

/**
 * 列表滚动模糊降级追踪：在持有 [androidx.compose.foundation.lazy.LazyListState] 的页面里
 * 调一次即可（插入一行，不碰任何括号）。
 * 滑动进行中 → 把全局 [com.example.earthonline.ui.theme.glassScrollBlurRadius] 置 0，
 * 玻璃卡据此跳过壁纸模糊采样、降级为纯半透明底色（GPU 零高斯开销，保滚动 60FPS）；
 * 滑动停止 → 恢复满模糊。列表内 [com.example.earthonline.ui.theme.GlassCard] 自动读取，无需改造。
 */
@Composable
fun TrackScrollBlur(
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    androidx.compose.runtime.LaunchedEffect(listState.isScrollInProgress) {
        com.example.earthonline.ui.theme.glassScrollBlurRadius.floatValue =
            if (listState.isScrollInProgress) 0f
            else com.example.earthonline.ui.theme.GLASS_BLUR_RADIUS_DP.value
    }
}
