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
 * v1.0.6.6（真机反馈批）：**已废弃** —— 列表滑动模糊降级被撤销，液态玻璃改为常驻。
 *
 * 真机反馈：滚动时玻璃忽有忽无的跳变比掉帧更刺眼。全局模糊半径不再随滚动置 0，
 * 因此本追踪器已无实际作用。保留空实现仅为兼容尚未清理的调用点（调用即无操作），
 * 新代码不要引用；后续会连同调用点一起删除。
 */
@Deprecated("v1.0.6.6 起液态玻璃常驻，滚动不再降级模糊；本调用已无操作")
@Composable
fun TrackScrollBlur(
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    // 无操作：模糊半径恒定，玻璃常驻
}
