package com.example.earthonline.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.graphics.RenderEffect
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.earthonline.ui.components.UiDimens
import com.example.earthonline.ui.components.bouncyClickable
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
 *  - v1.0.6.6 默认 **1.0（100%）**：真机反馈默认半透在花壁纸上正文发虚，可读性优先；
 *    想透出壁纸色相的用户可在「设置 → 外观」下调到 70%~90%。
 */
val LocalCardOpacity = compositionLocalOf { 1f }

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

/**
 * v1.0.6.6（真机反馈批）：**取消列表滑动降级，液态玻璃常驻**。
 *
 * 真机反馈：滚动时玻璃突然"消失/变灰"的跳变比掉帧更刺眼。
 * 故撤销 v1.0.6.5 的 `TrackScrollBlur` 动态关模糊逻辑 —— 无论列表是否在滑动，
 * [GlassSurface] 都保持满模糊半径（[GLASS_BLUR_RADIUS_DP]）的毛玻璃渲染。
 *
 * 性能红线：**只降半径、绝不关模糊**。若真机出现卡顿，调小 [GLASS_BLUR_RADIUS_DP]
 * 即可（6dp → 4dp），模糊效果仍在，只是更细腻；禁止再走"置 0 关模糊"的降级路径。
 */
enum class GlassTier { FULL, BLUR, FLAT }

@Composable
fun rememberGlassTier(): GlassTier {
    val context = LocalContext.current
    // v1.0.6.14（死命令 #2 防卡顿）：isLowRamDevice 之外加一道**总内存闸** ——
    // 总内存 < 4GB 的机型即便系统未标记 low-RAM，GPU/带宽也撑不住全屏高斯，
    // 一律强制 FLAT（半透明纯色底板，零 GPU 模糊开销，保证列表滚动 60FPS）
    val flatTier = remember {
        runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val info = android.app.ActivityManager.MemoryInfo()
            am?.getMemoryInfo(info)
            am?.isLowRamDevice == true || info.totalMem in 1 until 4_000_000_000L
        }.getOrDefault(false)
    }
    return when {
        Build.VERSION.SDK_INT >= 33 && !flatTier -> GlassTier.FULL
        Build.VERSION.SDK_INT >= 31 && !flatTier -> GlassTier.BLUR
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
    // v1.0.6.7（死命令 #1 断层修复）：精简为「单层毛玻璃底层 + 单层平滑半透明遮罩」。
    // 旧版多层叠加（模糊壁纸 + 渐变 tint + scrim + 顶部高光），且壁纸图按整屏 FillBounds +
    // 每卡位置偏移，导致灰/白相间的色块断层。现只保留：一层 RenderEffect 高斯模糊壁纸
    // （matchParentSize + Crop，不偏移、不拉伸）+ 一层 surface 半透明遮罩 + 1dp 描边；
    // 模糊半径加大到 14dp，色晕更平滑，彻底消除断层，同时平滑透出壁纸真实色彩。
    val useBackdrop = tier != GlassTier.FLAT && !wallPath.isNullOrBlank()
    // v1.0.6.8（死命令 #1 全局去壁纸化）：遮罩不透明度整体提到高 Alpha 档（0.72~0.88）。
    // 真机反馈：0.34~0.66 档在花壁纸上卡片仍能直接透出复杂底层图案，文字发虚、
    // 灰白相间断层感明显。现在底层仍是 14dp 高斯模糊壁纸（保留液态玻璃质感），
    // 但遮罩拉到 0.72 以上 —— 壁纸只作为「色相氛围」极淡透出，任何壁纸下文字都清晰。
    // 「卡片不透明度」滑块在 0.72~0.88 区间微调；FLAT 降级档用 0.93 高不透明纯色。
    val baseAlpha = tintOverride ?: LocalCardOpacity.current
    val maskAlpha = when {
        tier == GlassTier.FLAT -> 0.93f
        else -> (0.72f + 0.16f * baseAlpha).coerceIn(0.72f, 0.88f)
    }

    Box(
        modifier = modifier
            // v1.0.20（死命令 3）：强制圆弧裁剪 —— 整个玻璃容器（背景、高光描边、
            // 可读性遮罩、内容层）都被限制在统一圆角内，内容再怎么溢出也不会出现
            // 方形直角，液态玻璃质感全局一致。
            .clip(shape)
            .then(
                if (onClick != null) Modifier.bouncyClickable(onClick = onClick)
                else Modifier
            )
    ) {
        /* 装饰层一律用 matchParentSize()，绝不能用 fillMaxSize()。
           fillMaxSize() 会向父级索取「最大可用高度」——当 GlassSurface 被放进
           Scaffold 的 bottomBar 这类只给 max 约束、不给 min 高度的槽位时，
           它会把整个 Box 撑成满屏高，进而把 innerPadding.bottom 顶到屏高，
           内容区被完全挤出可视范围（v1.0.7 首页全空白的根因）。
           matchParentSize() 只「跟随」最终尺寸、不参与测量，语义才正确。 */
        // 1) 毛玻璃底层：实时采样壁纸 + RenderEffect 高斯模糊（GPU 档）。
        //    matchParentSize + Crop 按卡片自身取景，绝不整屏拉伸、绝不按位置偏移 → 无断层。
        if (useBackdrop) {
            val wDensity = LocalDensity.current
            val blurPx = with(wDensity) { GLASS_BLUR_RADIUS_DP.toPx() }
            Box(
                Modifier
                    .matchParentSize()
                    .clip(shape)
            ) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(LocalContext.current)
                        .data(File(wallPath!!))
                        .size(220)
                        .crossfade(false)
                        .build(),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            if (Build.VERSION.SDK_INT >= 31) {
                                renderEffect = RenderEffect.createBlurEffect(
                                    blurPx, blurPx, android.graphics.Shader.TileMode.CLAMP
                                ).asComposeRenderEffect()
                            }
                        }
                )
            }
        }
        // 2) 单层平滑半透明遮罩（可读性 + 与壁纸融合）；FLAT 档即高不透明纯色底板。
        Box(
            Modifier
                .matchParentSize()
                .clip(shape)
                .background(scheme.surface.copy(alpha = maskAlpha))
        )
        // 3) 色散描边（玻璃边缘光，1dp 细线，零额外成本）
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
    GlassSurface(
        modifier = modifier,
        shape = shape,
        showBorder = true,
        onClick = onClick,
        tintOverride = userAlpha,
        // v1.0.6.6（死命令 #3）：壁纸开启时始终保留一层可读性遮罩（下限 0.22），
        // 即便用户把卡片不透明度拉到 100%，也保证文字对比度稳定、同时透出壁纸色相。
        scrimOverride = (0.28f * (userAlpha / 0.74f)).coerceIn(0.22f, 0.42f)
    ) {
        // v1.0.6 封板：删掉内层 10% 黑色暗化层 —— 它正是「卡片发灰、和速览瓦片不是一种白」的真凶。
        // 可读性已由 GlassSurface 内部的 surface 蒙层（scrim）负责，无需再叠一层黑。
        content()
    }
}

/**
 * 全局标准列表卡（v1.0.6.3：对标背包页提取的统一卡片规范）。
 * - 圆角固定 [UiDimens.CardRadius]（16dp），与背包 / 速览 / 底栏同套圆弧；
 * - 着色与可读性遮罩走 [GlassCard] 同一渲染管线（受「卡片不透明度」滑块联动），
 *   绝不出现默认 Material Card 的黑色 elevation 阴影；
 * - 业务层（笔记 / 任务 / 背包）一律调用本组件，保证三端列表卡视觉完全一致。
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    GlassCard(
        modifier = modifier,
        shape = RoundedCornerShape(UiDimens.CardRadius),
        onClick = onClick,
        content = content
    )
}

/* 模糊半径：v1.0.6.7 由 6dp 提到 14dp —— 用户反馈 6dp 仍能看出壁纸细节、与半透明遮罩
   叠加后出现灰/白相间断层；加大半径让壁纸化成更平滑的色晕，从根上消除断层观感。
   FLAT 降级档不走这里，零 GPU 开销。 */
val GLASS_BLUR_RADIUS_DP: Dp = 14.dp

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
