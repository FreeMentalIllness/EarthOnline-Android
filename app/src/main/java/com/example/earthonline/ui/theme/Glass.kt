package com.example.earthonline.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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

/**
 * v1.0.18：卡片背景不透明度（设置 → 外观滑块，DataStore 持久化）。
 * 仅作用于 [GlassCard]（全局统一卡片容器），速览瓦片/底栏等其它 GlassSurface 用途不受影响。
 * v1.0.19：取值放宽为 0.0 ~ 1.0 全域 ——
 *  - 1.0：卡片完全不透明（surface 纯色底，跳过壁纸模糊采样，纯色性能档）；
 *  - 0.0：卡片完全通透（仅剩高光描边轮廓）；
 *  - 默认 0.74（历史视觉基线）。
 */
val LocalCardOpacity = compositionLocalOf { 0.74f }

/** v1.0.19：卡片不透明度被视为「纯色不透明」的阈值（≥ 该值 = 100% 档） */
const val CARD_OPAQUE_THRESHOLD = 0.995f

/**
 * 液态玻璃特效全局开关（对应「设置 → 外观 → 液态玻璃特效」）。
 * - true（默认）：启用毛玻璃背景模糊 + 高光描边 + 半透明折射观感；
 * - false（性能模式）：[GlassSurface] 强制走 [GlassTier.FLAT] 分支 ——
 *   去掉所有高斯模糊 / RenderEffect / 折射缩放，降级为高对比度半透明纯色底板，
 *   保障低端机与旧机型绝对流畅。业务层无需感知，由框架在 [MainScaffold] 统一注入。
 */
val LocalLiquidGlass = compositionLocalOf { true }

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
    /** v1.0.18：着色不透明度覆写（卡片不透明度滑块），null = 用内置档位 */
    tintOverride: Float? = null,
    /** v1.0.18：可读性遮罩覆写（随不透明度滑块联动），null = 用内置 0.28 */
    scrimOverride: Float? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val deviceTier = rememberGlassTier()
    // v1.0.10：设置中的「液态玻璃开关」已彻底移除 —— 全局强制开启液态玻璃，
    // 不再提供关闭选项。低端机仍由 GlassTier 自动降级为纯色底板（零 GPU 开销），流畅度不受影响。
    val liquidGlass = true
    val tier = deviceTier
    val scheme = MaterialTheme.colorScheme
    val wallPath = LocalWallpaperPath.current
    // v1.0.19：卡片不透明度拉满（100% 纯色档）时跳过壁纸模糊采样 ——
    // 纯色底下采样结果完全被盖住，纯属 GPU 浪费；跳过后卡片走零采样纯色渲染。
    val cardOpaque = (tintOverride ?: 0f) >= CARD_OPAQUE_THRESHOLD
    val useBackdrop = tier != GlassTier.FLAT && !wallPath.isNullOrBlank() && !cardOpaque

    /* 着色层改用「上深下浅」的竖向渐变。
       - GPU 档（FULL/BLUR）：顶部略深、底部略透，顶部托住标题/正文，底部与背景柔和溶解；
       - FLAT 档（低端机）：高对比度半透明纯色底板，不透明度拉满，优先可读性、零 GPU 消费。
       注意：下方还有一层「独立可读性遮罩（scrim）」与模糊层解耦，进一步兜底任意壁纸下的对比度。 */
    val (tintTop, tintBottom) = when {
        tier == GlassTier.FLAT -> 0.94f to 0.94f
        // v1.0.19：100% 档 = 上下都拉满 1.0，卡片成为真正的 surface 纯色底
        tintOverride != null && tintOverride >= CARD_OPAQUE_THRESHOLD -> 1f to 1f
        // v1.0.18：卡片不透明度滑块覆写（上深下浅的梯度差收窄为 0.08）
        tintOverride != null -> tintOverride to (tintOverride - 0.08f).coerceAtLeast(0.30f)
        // v1.0.17：底部着色下限 0.62→0.66 —— 用户反馈可读性优先，Alpha 收敛进 60%~80% 区间上沿
        else -> 0.74f to 0.66f
    }
    val tintBrush = Brush.verticalGradient(
        colorStops = arrayOf(
            0.0f to scheme.surface.copy(alpha = tintTop),
            1.0f to scheme.surface.copy(alpha = tintBottom)
        )
    )

    Box(
        modifier = modifier
            // v1.0.20（死命令 3）：强制圆弧裁剪 —— 整个玻璃容器（背景、高光描边、
            // 可读性遮罩、内容层）都被限制在统一圆角内，内容再怎么溢出也不会出现
            // 方形直角，液态玻璃质感全局一致。
            .clip(shape)
            .then(
                if (onClick != null) Modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClick
                ) else Modifier
            )
    ) {
        /* ⚠️ 装饰层一律用 matchParentSize()，绝不能用 fillMaxSize()。
           fillMaxSize() 会向父级索取「最大可用高度」——当 GlassSurface 被放进
           Scaffold 的 bottomBar 这类只给 max 约束、不给 min 高度的槽位时，
           它会把整个 Box 撑成满屏高，进而把 innerPadding.bottom 顶到屏高，
           内容区被完全挤出可视范围（v1.0.7 首页全空白的根因）。
           matchParentSize() 只「跟随」最终尺寸、不参与测量，语义才正确。 */
        if (useBackdrop) {
            // 1) 模糊壁纸背景（GPU 档）。
            //    v1.0.7 性能关键：毛玻璃底图按 160px 小图采样 —— 模糊之后反正是一团色晕，
            //    小图的视觉结果与全尺寸图完全一致，但每帧的上传带宽 / 采样开销大幅下降；
            //    之前直接用 File 全尺寸解码，N 个玻璃卡片 = N 张全尺寸位图常驻显存，是卡顿主源之一。
            Box(
                Modifier
                    .matchParentSize()
                    .clip(shape)
                    .backdrop(tier)
                    .background(Color.Transparent)
            ) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(LocalContext.current)
                        .data(File(wallPath!!))
                        .size(160)
                        .crossfade(false)
                        .build(),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }
            // 2) 半透明着色层（上深下浅渐变，保证文字可读性 + 底缘溶解）
            Box(
                Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(tintBrush)
            )
        } else {
            // 降级档：纯色面板
            Box(
                Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(tintBrush)
            )
        }
        // 3) 独立可读性遮罩（GlassAccessibilityMode 的核心）：
        //    与模糊层解耦的纯色 surface 蒙层，无论壁纸多花、模糊多弱，都给文字一层稳定底色。
        //    - 液态玻璃开启（GPU 档）：补 0.24 的 surface 蒙层（v1.0.12 由 0.18 上调），
        //      把有效不透明度抬到 ~0.82，任意壁纸下正文对比度都在 WCAG AA 以上；
        //      同时仍保留壁纸的色相与模糊层次，不会退化成死灰面板。
        //    - FLAT 档（低端机 / 性能降级）：着色层已接近不透明，无需再叠蒙层（避免变成死灰面板）。
        // v1.0.17：同色系可读性遮罩 0.24→0.28 —— 任何壁纸下正文都保持稳定对比度
        // v1.0.18：随「卡片背景不透明度」滑块联动（滑得越透，遮罩越轻；默认 0.74 → 0.28）
        val scrimAlpha = when {
            tier == GlassTier.FLAT -> 0f
            scrimOverride != null -> scrimOverride
            else -> 0.28f
        }
        if (scrimAlpha > 0f) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(scheme.surface.copy(alpha = scrimAlpha))
            )
        }
        // 4) 顶部高光（液态玻璃标志性边缘光，叠在遮罩之上保证边缘光清晰）
        Box(
            Modifier
                .matchParentSize()
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
            /* v1.0.7：色散描边（dispersion edge）—— 白 → 琥珀 → 冷蓝的细渐变，
               模拟光线掠过玻璃棱镜时边缘的色散；比纯灰 outline 更有「液态」质感。
               渐变只画 1dp 描边，零额外 GPU 成本。 */
            val edgeBrush = Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.55f),
                    AmberPrimary.copy(alpha = 0.38f),
                    Color(0xFFBFD6FF).copy(alpha = 0.42f),
                    Color.White.copy(alpha = 0.22f)
                )
            )
            Box(
                Modifier
                    .matchParentSize()
                    .clip(shape)
                    .border(1.dp, edgeBrush, shape)
            )
        }
        // 4) 内容（永远锐利，不参与模糊）。
        // 这一层是唯一「决定」外层 Box 尺寸的层：不写尺寸修饰符 = 按内容自然测量，
        // 有固定 size/height 的调用点则由外层 modifier 决定，contentAlignment 照常生效。
        Box(Modifier, contentAlignment = contentAlignment, content = content)
    }
}

/**
 * 卡片级玻璃容器（全站列表卡 / 概览卡的唯一材质）：毛玻璃 + 独立暗化遮罩。
 * 红线：壁纸可能过亮过花，正文必须有稳定对比度 —— v1.0.12 起内层暗化从 5% 提到 10%，
 * 配合外层 scrim（0.24 surface）把卡片内文字区压到稳定的中低亮度，
 * 既保住「背景半透明、透出壁纸色相」的质感，又保证正文清晰可读。
 * 业务里替代 Material Card 使用；内容按自然测量，调用方自带 padding。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    // v1.0.18：接入「卡片背景不透明度」滑块 —— tint 直读用户值，
    // scrim 按比例联动（默认 0.74 → 0.28，滑到 0.40 → 0.15 更通透，滑到 0.95 → 0.36 更稳）。
    // v1.0.19：范围放宽 0~1 —— 100% 档纯色不透明（scrim 归零），0% 档完全通透（含内层暗化一起归零）；
    // 整卡视觉参数与速览瓦片完全同源（同一 GlassSurface 渲染管线），绝无默认 Card 的黑色 elevation。
    // v1.0.20：圆角全局统一 16dp（UiDimens.CardRadius 同值），且 GlassSurface 内部强制
    // clip(shape) —— 背景与内容都锁进圆弧，任何调用点都不可能再出现方形直角。
    val userAlpha = LocalCardOpacity.current
    val opaque = userAlpha >= CARD_OPAQUE_THRESHOLD
    GlassSurface(
        modifier = modifier,
        shape = shape,
        showBorder = true,
        onClick = onClick,
        tintOverride = userAlpha,
        scrimOverride = if (opaque) 0f else (0.28f * (userAlpha / 0.74f)).coerceIn(0f, 0.4f)
    ) {
        // v1.0.6 封板：删掉内层 10% 黑色暗化层 —— 它正是「卡片发灰、和速览瓦片不是一种白」的真凶。
        // 可读性已由 GlassSurface 内部的 surface 蒙层（scrim）负责，无需再叠一层黑。
        content()
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

/* v1.0.7：模糊半径 18dp -> 10dp（真机反馈明显卡顿）。
   Modifier.blur 的开销随半径非线性增长，且每个 GlassSurface 每帧都要跑一次；
   10dp 已足够「磨砂」，18dp 属于过度消费 GPU。低端降级（FLAT 纯色档）不受影响。 */
private val BLUR_DP: Dp = 10.dp

/** 在主框架注入全局壁纸路径，供各处 [GlassSurface] 就地采样毛玻璃背景。 */
@Composable
fun ProvideWallpaper(wallpaperPath: String?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalWallpaperPath provides wallpaperPath, content = content)
}

/** 在框架层注入液态玻璃开关：关闭时所有 [GlassSurface] 自动降级为纯色底板（性能模式）。 */
@Composable
fun ProvideLiquidGlass(enabled: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLiquidGlass provides enabled, content = content)
}
