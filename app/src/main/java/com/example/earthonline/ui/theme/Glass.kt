package com.example.earthonline.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File

/**
 * 全局液态玻璃（Liquid Glass）封装。
 *
 * 设计原则（对应需求红线）：
 *  - 全端视觉统一：业务层只调用 [GlassSurface]，不感知降级；
 *  - 流畅度优先：低端机 / Android 12 及以下自动降级为半透明纯色 + 高光，零 GPU 模糊；
 *  - 绝不崩溃：所有 RenderEffect 调用均门控于 SDK 版本，低版本 / 异常一律回退纯色玻璃。
 *
 * 降级矩阵：
 *  - [GlassTier.FULL]：API 33+ 且非低内存 → 透镜微放大（折射观感）+ 背景模糊 + 高光；
 *  - [GlassTier.BLUR]：API 31/32 且非低内存 → 背景模糊 + 高光；
 *  - [GlassTier.FLAT]：API < 31 或低内存 → 半透明纯色 + 顶部高光边，零卡顿。
 *
 * 背景来源：通过 [LocalWallpaperPath] 从主框架注入当前壁纸路径，
 * 玻璃在支持 GPU 效果的层级会就地模糊壁纸（毛玻璃观感）；
 * 无壁纸或降级档时退化为半透明纯色面板。
 */
val LocalWallpaperPath = compositionLocalOf<String?> { null }

enum class GlassTier { FULL, BLUR, FLAT }

@Composable
fun rememberGlassTier(): GlassTier {
    val context = LocalContext.current
    val lowRam = remember {
        runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.isLowRamDevice == true
        }.getOrDefault(false)
    }
    return when {
        Build.VERSION.SDK_INT >= 33 && !lowRam -> GlassTier.FULL
        Build.VERSION.SDK_INT >= 31 && !lowRam -> GlassTier.BLUR
        else -> GlassTier.FLAT
    }
}

/**
 * 液态玻璃容器：背景（模糊壁纸 / 纯色）+ 顶部高光 + 内容层。
 * @param onClick 非空时整块可点（替代 Button 的玻璃态）。
 * @param shape 圆角形状（默认 20dp）。
 * @param showBorder 是否描边（玻璃边缘高光）。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    onClick: (() -> Unit)? = null,
    contentAlignment: Alignment = Alignment.Center,
    showBorder: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val tier = rememberGlassTier()
    val scheme = MaterialTheme.colorScheme
    val wallPath = LocalWallpaperPath.current
    val useBackdrop = tier != GlassTier.FLAT && !wallPath.isNullOrBlank()

    val tintAlpha = if (tier == GlassTier.FLAT) 0.82f else 0.42f

    Box(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            ) else Modifier
        )
    ) {
        if (useBackdrop) {
            // 1) 模糊壁纸背景（GPU 档）
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .backdrop(tier)
                    .background(Color.Transparent)
            ) {
                AsyncImage(
                    model = File(wallPath!!),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // 2) 半透明着色层（保证文字可读性）
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(scheme.surface.copy(alpha = tintAlpha))
            )
        } else {
            // 降级档：纯色面板
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(scheme.surface.copy(alpha = tintAlpha))
            )
        }
        // 3) 顶部高光（液态玻璃标志性边缘光）
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.White.copy(alpha = 0.20f),
                            0.18f to Color.White.copy(alpha = 0.05f),
                            0.4f to Color.Transparent
                        )
                    )
                )
        )
        if (showBorder) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .border(1.dp, scheme.outlineVariant.copy(alpha = 0.5f), shape)
            )
        }
        // 4) 内容（永远锐利，不参与模糊）
        Box(Modifier.fillMaxSize(), contentAlignment = contentAlignment, content = content)
    }
}

/**
 * 背景模糊 / 折射修饰符。所有 GPU 调用均门控于 SDK 版本，低版本返回原修饰符（纯色面板照常）。
 * FULL 档额外做轻微放大，模拟透镜折射观感（安全、零 RenderEffect 类型摩擦）。
 */
private fun Modifier.backdrop(tier: GlassTier): Modifier {
    val blurred = if (Build.VERSION.SDK_INT >= 31) this.blur(BLUR_DP) else this
    return if (tier == GlassTier.FULL) {
        this.graphicsLayer { scaleX = 1.06f; scaleY = 1.06f }.blur(BLUR_DP)
    } else blurred
}

private val BLUR_DP: Dp = 14.dp

/** 在主框架注入全局壁纸路径，供各处 [GlassSurface] 就地采样毛玻璃背景。 */
@Composable
fun ProvideWallpaper(wallpaperPath: String?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalWallpaperPath provides wallpaperPath, content = content)
}
