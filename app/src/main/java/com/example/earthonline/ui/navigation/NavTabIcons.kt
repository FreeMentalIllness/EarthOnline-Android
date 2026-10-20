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

/**
 * v1.0.10：底部导航自绘动效图标。
 * 四个 Tab 各有「点击播放」的微动画（progress 由 0→1 驱动，由 NavTab 在点击时触发）：
 *  - HOME：房子 + 门，点击时门「打开」（门板宽度随 progress 张开）；
 *  - TASKS：圆角方框 + 勾，点击时勾沿轨迹被「画」出来；
 *  - BACKPACK：拉链，点击时链牙向两侧分开、滑块下滑；
 *  - NOTES：笔，点击时笔尖沿书写线移动、墨迹随之拉出。
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
            NavIconKind.BACKPACK -> drawZipper(progress, s, tint)
            NavIconKind.NOTES -> drawPen(progress, s, tint)
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

private fun DrawScope.drawZipper(p: Float, s: Float, tint: Color) {
    val w = s * 0.07f
    val top = s * 0.16f
    val bottom = s * 0.86f
    val split = s * 0.13f * p
    val lx = s * 0.5f - split
    val rx = s * 0.5f + split
    // 两侧链牙（在合拢时相接，点击分开）
    drawLine(tint, Offset(lx, top), Offset(lx, bottom), strokeWidth = w, cap = StrokeCap.Round)
    drawLine(tint, Offset(rx, top), Offset(rx, bottom), strokeWidth = w, cap = StrokeCap.Round)
    // 链牙小齿
    var y = top + s * 0.06f
    while (y < bottom) {
        drawLine(tint.copy(alpha = 0.7f), Offset(lx - s * 0.06f, y), Offset(lx, y), strokeWidth = w * 0.8f, cap = StrokeCap.Round)
        drawLine(tint.copy(alpha = 0.7f), Offset(rx, y), Offset(rx + s * 0.06f, y), strokeWidth = w * 0.8f, cap = StrokeCap.Round)
        y += s * 0.11f
    }
    // 滑块：随 progress 下滑
    val sy = top + (bottom - top) * p
    drawRoundRect(tint, topLeft = Offset(s * 0.5f - s * 0.12f, sy - s * 0.05f), size = Size(s * 0.24f, s * 0.1f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.04f), style = stroke(w))
}

private fun DrawScope.drawPen(p: Float, s: Float, tint: Color) {
    val w = s * 0.08f
    val lineY = s * 0.72f
    val x0 = s * 0.16f
    val x1 = s * 0.84f
    // 墨迹（随 progress 拉出）
    val inkX = x0 + (x1 - x0) * p.coerceIn(0f, 1f)
    drawLine(tint, Offset(x0, lineY), Offset(inkX, lineY), strokeWidth = w, cap = StrokeCap.Round)
    // 笔：斜放，笔尖落在墨迹末端
    val tipX = inkX
    val tipY = lineY
    val penAng = -0.9f
    val len = s * 0.32f
    val up = Offset(tipX + kotlin.math.cos(penAng) * len, tipY + kotlin.math.sin(penAng) * len)
    drawLine(tint, Offset(tipX, tipY), up, strokeWidth = w, cap = StrokeCap.Round)
    // 笔尖小三角
    drawCircle(tint, radius = w * 0.6f, center = Offset(tipX, tipY))
}

private fun Offset.distanceTo(o: Offset): Float {
    val dx = x - o.x
    val dy = y - o.y
    return kotlin.math.sqrt(dx * dx + dy * dy)
}
