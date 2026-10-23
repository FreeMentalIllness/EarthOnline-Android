package com.example.earthonline.ui.design

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 全局 Design Tokens（终极重构 · 工程一）。
 *
 * 存在意义：此前间距 / 圆角 / 高光散落在各业务页硬编码，改一处要改十几处。
 * 现在业务层一律引用本文件的 Token，禁止再写裸数字 ——
 * 这样「全站统一」才不是口号，而是编译期可约束的事实。
 *
 * 红线：卡片**绝不允许黑边 / 黑色 elevation**。Material 默认 Card 带 tonalElevation
 * 会在半透明玻璃上糊出一圈脏黑，本 App 全站卡片一律走高光描边（[eoSpecularBrush]），
 * 阴影只允许用暖色柔影（[eoWarmShadow]），从 Token 层就掐死黑色。
 */

/** 间距：8dp 基准栅格 */
object EoSpace {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 20.dp
    val Xxl = 24.dp
    val Huge = 32.dp
}

/** 圆角：四档收敛，杜绝每页各写各的 */
object EoRadius {
    /** 小控件 / Chip */
    val Chip = 10.dp
    /** 标准卡片（与 UiDimens.CardRadius 同值） */
    val Card = 16.dp
    /** 大容器 / 底部弹窗顶角 */
    val Sheet = 28.dp
    /** 胶囊（输入框 / 按钮）：半径大于半高即成胶囊，Compose 会自动收敛到半高 */
    val Pill = 50.dp
}

/** 描边宽度 */
object EoStroke {
    /** 发丝级高光边 */
    val Hair = 1.dp
    /** 强调边（选中态） */
    val Bold = 2.dp
}

/** 动效时长（ms）：与 ui/theme/Motion.kt 同源口径 */
object EoDuration {
    const val Fast = 160
    const val Normal = 260
    const val Slow = 380
    /** 底部弹窗 / 页面转场 */
    const val Transition = 300
}

/**
 * 无黑边高光：卡片顶缘的一道内高光 + 斜向镜面反光，
 * 这是「液态玻璃」与「灰色塑料板」的分界线。
 */
fun eoSpecularBrush(topAlpha: Float = 0.20f): Brush = Brush.verticalGradient(
    colorStops = arrayOf(
        0.0f to Color.White.copy(alpha = topAlpha),
        0.16f to Color.White.copy(alpha = topAlpha * 0.28f),
        0.42f to Color.Transparent
    )
)

/**
 * 暖色柔影 —— 卡片外投影**唯一**允许的实现。
 * 绝不使用 Color.Black 的硬阴影：在半透明玻璃底下会脏成黑圈。
 */
fun eoWarmShadow(alpha: Float = 0.10f): Color = Color(0xFF6B4A2A).copy(alpha = alpha)

/** 玻璃边缘色散描边（白 → 琥珀 → 冷蓝），与 GlassSurface 内部同一语言 */
fun eoEdgeBrush(): Brush = Brush.linearGradient(
    listOf(
        Color.White.copy(alpha = 0.58f),
        Color(0xFFE8A33C).copy(alpha = 0.40f),
        Color(0xFFBFD6FF).copy(alpha = 0.44f),
        Color.White.copy(alpha = 0.22f)
    )
)
