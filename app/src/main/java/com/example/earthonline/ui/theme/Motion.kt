package com.example.earthonline.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.IntSize

/**
 * v1.0.7：全局动效规格（对标参考项目的「连续动画反馈」）。
 * 三条铁律：
 *  ① 弹性优先 —— 展开/收起/选中态用 spring，绝不用 linear/tween 的「生硬跳变」；
 *  ② 按压必回馈 —— 可点元素按下即缩到 94%，抬手弹回，配合按压光圈/背景色过渡；
 *  ③ 时长克制 —— spring 的 stiffness 统一压在中档，保证低端机上不拖泥带水。
 */

/** 展开/收起/导航选中的弹性规格：低刚度 + 中等阻尼，回弹一下但不晃 */
val GlassSpring: SpringSpec<Float> = spring(
    dampingRatio = 0.72f,
    stiffness = Spring.StiffnessMediumLow
)

/**
 * [GlassSpring] 的 IntSize 版本 —— animateContentSize / expandVertically 的
 * animationSpec 要的是 FiniteAnimationSpec<IntSize>，Float 版塞不进去。
 * 数值与 [GlassSpring] 完全一致，保证不同属性的动画观感统一。
 */
val GlassSpringIntSize: SpringSpec<IntSize> = spring(
    dampingRatio = 0.72f,
    stiffness = Spring.StiffnessMediumLow
)

/** 按压回弹：高刚度，按下立刻缩、抬手立刻回，不拖泥带水 */
val PressSpring: SpringSpec<Float> = spring(
    dampingRatio = 0.55f,
    stiffness = Spring.StiffnessHigh
)

/**
 * 按压缩放 + 点击二合一修饰符：按下缩 [pressedScale]，抬手 spring 弹回。
 * 自带 ripple-free 的 interactionSource，业务若自绘按压高亮（光圈/背景色）
 * 应把同一个 interactionSource 传进来复用，避免两套状态互相打架。
 */
@Composable
fun Modifier.pressScaleClickable(
    onClick: () -> Unit,
    pressedScale: Float = 0.94f,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = PressSpring,
        label = "pressScale"
    )
    return this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

/** 只拿按压缩放（不接管点击）—— 给自带点击逻辑的元素（如 detectTapGestures）用 */
@Composable
fun Modifier.pressScale(
    pressed: Boolean,
    pressedScale: Float = 0.94f
): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = PressSpring,
        label = "pressScaleOnly"
    )
    return this.scale(scale)
}
