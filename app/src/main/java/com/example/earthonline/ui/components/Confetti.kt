package com.example.earthonline.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity

/**
 * v1.0.4：撒花彩纸粒子（公开组件）。
 * 纯 Canvas + 随机种子实现，零第三方依赖、零表结构改动。
 * 覆盖层只画不摸：Canvas 不消费触摸事件，不影响下方内容的任何交互。
 *
 * @param burstKey 一次庆祝的标识（非 null 即触发一次迸发；变化即重放）
 *
 * 终极重构 · 工程三：原 `combo` 参数（连击放大粒子）已**废弃移除** ——
 * 撒花强度恒定，不再奖励「短时间内连点」，成长只由真实累计行为决定。
 */
private data class ConfettiParticle(
    val vx: Float,      // 水平初速（单位时间占屏宽比例，正 = 向右）
    val vy: Float,      // 垂直初速（负 = 向上抛）
    val g: Float,       // 重力加速度（单位时间占屏高比例）
    val radius: Float,  // 基准半径（dp，绘制时按屏幕密度换算）
    val color: Color,
    val seed: Float     // 相位扰动，让粒子下落节奏错开
)

@Composable
fun ConfettiBurst(burstKey: String?) {
    if (burstKey == null) return
    var progress by remember(burstKey) { mutableStateOf(0f) }
    LaunchedEffect(burstKey) {
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(durationMillis = 900, easing = LinearEasing)
        ) { v, _ -> progress = v }
    }
    // 以 burstKey 为随机种子：同一次庆祝粒子轨迹稳定，重组/连击不跳变
    val particles = remember(burstKey) {
        val rnd = kotlin.random.Random(burstKey.hashCode())
        val palette = listOf(
            Color(0xFFD4A373), // 暖琥珀
            Color(0xFFE0A96D), // 亮琥珀
            Color(0xFF588157), // 叶绿
            Color(0xFFBC4749), // 陶红
            Color(0xFFE6C079)  // 麦金
        )
        List(30) {
            ConfettiParticle(
                vx = rnd.nextFloat() * 1.2f - 0.6f,
                vy = -(0.30f + rnd.nextFloat() * 0.45f),
                g = 1.1f + rnd.nextFloat() * 0.5f,
                radius = 3f + rnd.nextFloat() * 4f,
                color = palette[rnd.nextInt(palette.size)],
                seed = rnd.nextFloat() * 0.1f
            )
        }
    }
    val density = LocalDensity.current
    Canvas(Modifier.fillMaxSize()) {
        val t = progress * 0.9f + 0.001f
        val cx = size.width / 2f
        val cy = size.height * 0.30f
        // 连击机制已废弃：粒子尺寸恒定（原为随 combo 放大 12%，封顶 1.4x）
        val scale = 1f
        particles.forEach { p ->
            val x = cx + p.vx * t * size.width * 0.5f
            val y = cy + (p.vy * (t + p.seed) + 0.5f * p.g * t * t) * size.height
            drawCircle(
                color = p.color,
                radius = p.radius * density.density * scale,
                center = Offset(x, y),
                alpha = (1f - progress).coerceIn(0f, 1f)
            )
        }
    }
}
