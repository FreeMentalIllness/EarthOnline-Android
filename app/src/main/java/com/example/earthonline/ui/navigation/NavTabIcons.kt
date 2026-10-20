package com.example.earthonline.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate

/**
 * v1.0.11：底部导航自绘动效图标（重绘版）。
 * 四个 Tab 各有「点击播放」的微动画（progress 由 0→1 驱动，由 NavTab 在点击时触发）：
 *  - HOME：房子 + 门，点击时门「打开」（门板宽度随 progress 张开）；
 *  - TASKS：圆角方框 + 勾，点击时勾沿轨迹被「画」出来；
 *  - BACKPACK：完整背包轮廓（箱体 + 顶提手 + 盖头 + 双扣带），点击时盖头微微掀起；
 *  - NOTES：一支倾斜的钢笔（笔身 + 笔尖 + 笔舌缝线），点击时钢笔绕笔尖轻微倾斜回弹。
 * progress = 1 为静态常驻形态，确保未点击时也清晰可辨。
 */
@Composable
fun AnimatedNavIcon(
    kind: NavIconKind,
    progress: Float,
    tint: Color,
    modifier: Modifier
) {
    Canvas(modifier) {
        val s = size.minDimension
        when (kind) {
            NavIconKind.HOME -> drawHome(progress, s, tint)
            NavIconKind.TASKS -> drawCheck(progress, s, tint)
            NavIconKind.BACKPACK -> drawBackpack(progress, s, tint)
            NavIconKind.NOTES -> drawFountainPen(progress, s, tint)
        }
    }
}

private fun DrawScope.stroke(width: Float) = Stroke(width = width, cap = StrokeCap.Round)

private fun DrawScope.drawHome(p: Float, s: Float, tint: Color) {
    val w = s * 0.09f
    val bodyTop = s * 0.42f
    val base = s * 0.86f
    val left = s * 0.16f
    val right = s * 0.84f
    val cx = s * 0.5f
    val roof = s * 0.18f
    // 屋顶 + 墙体
    drawPath(
        Path().apply {
            moveTo(left, bodyTop)
            lineTo(cx, roof)
            lineTo(right, bodyTop)
            lineTo(right, base)
            lineTo(left, base)
            close()
        },
        color = tint, style = stroke(w)
    )
    // 门：随 progress 张开（p=0 闭合不可见，p=1 全开）
    val doorW = (right - left) * 0.34f * p
    if (doorW > 1f) {
        val doorH = (base - bodyTop) * 0.72f
        val doorTop = bodyTop + (base - bodyTop - doorH) * 0.5f
        drawRect(tint, topLeft = Offset(left + (right - left) * 0.33f - doorW / 2f, doorTop), size = Size(doorW, doorH), style = stroke(w))
        // 门把手
        drawCircle(tint.copy(alpha = 0.9f), radius = w * 0.5f, center = Offset(left + (right - left) * 0.33f + doorW / 2f - w, doorTop + doorH * 0.5f))
    }
}

private fun DrawScope.drawCheck(p: Float, s: Float, tint: Color) {
    val w = s * 0.09f
    val r = s * 0.36f
    val c = s * 0.5f
    // 圆角方框
    drawRoundRect(tint, topLeft = Offset(c - r, c - r), size = Size(r * 2, r * 2), cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.16f), style = stroke(w))
    // 勾：两段折线，按 progress 抽取绘制长度
    val a = Offset(c - r * 0.5f, c + r * 0.02f)
    val b = Offset(c - r * 0.08f, c + r * 0.42f)
    val d = Offset(c + r * 0.52f, c - r * 0.42f)
    val l1 = a.distanceTo(b)
    val l2 = b.distanceTo(d)
    val drawn = (l1 + l2) * p.coerceIn(0f, 1f)
    val path = Path()
    path.moveTo(a.x, a.y)
    if (drawn <= l1) {
        path.lineTo(a.x + (b.x - a.x) * (drawn / l1), a.y + (b.y - a.y) * (drawn / l1))
    } else {
        path.lineTo(b.x, b.y)
        val r2 = drawn - l1
        path.lineTo(b.x + (d.x - b.x) * (r2 / l2), b.y + (d.y - b.y) * (r2 / l2))
    }
    drawPath(path, color = tint, style = stroke(w))
}

/**
 * 完整背包轮廓：圆角箱体 + 顶部提手弧 + 盖头（flap）+ 左右两条扣带。
 * 点击动画：盖头随 progress 向上掀起（正弦包络，中途最大、静止归位）。
 */
private fun DrawScope.drawBackpack(p: Float, s: Float, tint: Color) {
    val w = s * 0.085f
    val lift = s * 0.06f * kotlin.math.sin(p * Math.PI).toFloat()
    val left = s * 0.20f
    val right = s * 0.80f
    val bodyTop = s * 0.30f
    val base = s * 0.88f
    // 箱体（圆角矩形）
    drawRoundRect(
        tint,
        topLeft = Offset(left, bodyTop),
        size = Size(right - left, base - bodyTop),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.14f),
        style = stroke(w)
    )
    // 顶部提手弧
    val handleL = s * 0.38f
    val handleR = s * 0.62f
    drawPath(
        Path().apply {
            moveTo(handleL, bodyTop)
            cubicTo(handleL, s * 0.12f, handleR, s * 0.12f, handleR, bodyTop)
        },
        color = tint, style = stroke(w)
    )
    // 盖头：横跨箱体上部的圆角横条，点击时向上掀起
    val flapTop = s * 0.30f - lift
    drawRoundRect(
        tint,
        topLeft = Offset(left - s * 0.02f, flapTop),
        size = Size((right - left) + s * 0.04f, s * 0.14f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.08f),
        style = stroke(w)
    )
    // 左右扣带（从盖头下沿垂到箱体中下部）
    val strapY0 = flapTop + s * 0.14f
    val strapY1 = s * 0.62f
    val bx1 = left + (right - left) * 0.28f
    val bx2 = left + (right - left) * 0.72f
    drawLine(tint, Offset(bx1, strapY0), Offset(bx1, strapY1), strokeWidth = w * 0.85f, cap = StrokeCap.Round)
    drawLine(tint, Offset(bx2, strapY0), Offset(bx2, strapY1), strokeWidth = w * 0.85f, cap = StrokeCap.Round)
    // 扣带小方扣
    drawRect(tint, topLeft = Offset(bx1 - w * 1.1f, strapY1 - w * 1.1f), size = Size(w * 2.2f, w * 2.2f), style = stroke(w * 0.75f))
    drawRect(tint, topLeft = Offset(bx2 - w * 1.1f, strapY1 - w * 1.1f), size = Size(w * 2.2f, w * 2.2f), style = stroke(w * 0.75f))
}

/**
 * 倾斜钢笔：笔身（斜置粗线）+ 笔环 + 笔尖（三角 + 中缝线）+ 书写基线。
 * 点击动画：整支笔绕笔尖轻微倾斜后回弹（rotate 由 progress 的正弦包络驱动）。
 */
private fun DrawScope.drawFountainPen(p: Float, s: Float, tint: Color) {
    val w = s * 0.085f
    val tilt = kotlin.math.sin(p * Math.PI).toFloat() * 14f
    val lineY = s * 0.78f
    val tipX = s * 0.36f
    val tipY = lineY
    rotate(degrees = tilt, pivot = Offset(tipX, tipY)) {
        // 笔身：从笔尖向右上方的粗斜线
        val ang = -0.82f
        val len = s * 0.52f
        val endX = tipX + kotlin.math.cos(ang) * len
        val endY = tipY + kotlin.math.sin(ang) * len
        drawLine(tint, Offset(tipX, tipY), Offset(endX, endY), strokeWidth = w * 1.9f, cap = StrokeCap.Round)
        // 笔环（装饰圈）
        val ringT = 0.72f
        val ringC = Offset(tipX + kotlin.math.cos(ang) * len * ringT, tipY + kotlin.math.sin(ang) * len * ringT)
        drawCircle(tint, radius = w * 1.15f, center = ringC)
        // 笔尖：小三角
        val nibL = Offset(tipX - s * 0.10f, tipY + s * 0.06f)
        val nibR = Offset(tipX + s * 0.02f, tipY + s * 0.12f)
        drawPath(
            Path().apply {
                moveTo(tipX, tipY)
                lineTo(nibL.x, nibL.y)
                lineTo(nibR.x, nibR.y)
                close()
            },
            color = tint, style = stroke(w * 0.9f)
        )
        // 笔尖中缝线
        drawLine(tint, Offset(tipX, tipY), Offset(tipX - s * 0.03f, tipY + s * 0.08f), strokeWidth = w * 0.6f, cap = StrokeCap.Round)
        // 书写基线（墨迹示意）
        drawLine(tint.copy(alpha = 0.55f), Offset(s * 0.14f, lineY), Offset(s * 0.86f, lineY), strokeWidth = w * 0.6f, cap = StrokeCap.Round)
    }
}

private fun Offset.distanceTo(o: Offset): Float {
    val dx = x - o.x
    val dy = y - o.y
    return kotlin.math.sqrt(dx * dx + dy * dy)
}
