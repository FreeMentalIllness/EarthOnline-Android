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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
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
    // v1.0.6.6（死命令 #3）：**废除「100% 不透明就跳过壁纸模糊」的旧逻辑**。
    // 旧逻辑让默认不透明度的卡片退化为纯色面板，完全不透出壁纸 —— 正是用户反馈的
    // 「背景固定半透明色、没有折射透光」根因。现在只要设备支持 GPU 模糊（tier != FLAT）
    // 且注入了壁纸路径，卡片一律实时采样并模糊壁纸，透出真实色彩、随滑动产生光影变化。
    val useBackdrop = tier != GlassTier.FLAT && !wallPath.isNullOrBlank()

    /* 着色层改用「上深下浅」的竖向渐变。
       - GPU 档（FULL/BLUR）：顶部略深、底部略透，顶部托住标题/正文，底部与背景柔和溶解；
       - FLAT 档（低端机）：高对比度半透明纯色底板，不透明度拉满，优先可读性、零 GPU 消费。
       注意：下方还有一层「独立可读性遮罩（scrim）」与模糊层解耦，进一步兜底任意壁纸下的对比度。 */
    // 着色层：壁纸开启时压低不透明度让壁纸透出（用户滑块仍可在范围内调节），
    // 纯色降级档（FLAT）保持高不透明、可读性优先。
    val backdropCap = if (useBackdrop) 0.66f else 1f
    val (tintTop, tintBottom) = when {
        tier == GlassTier.FLAT -> 0.94f to 0.94f
        tintOverride != null -> tintOverride.coerceAtMost(backdropCap) to
            (tintOverride - 0.10f).coerceAtLeast(0.20f).coerceAtMost(backdropCap)
        // 无覆写（弹窗 / 按钮等）默认档：压低到 0.60/0.50，让壁纸透出又不失可读性
        else -> 0.60f to 0.50f
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
                if (onClick != null) Modifier.bouncyClickable(onClick = onClick)
                else Modifier
            )
    ) {
        /* ⚠️ 装饰层一律用 matchParentSize()，绝不能用 fillMaxSize()。
           fillMaxSize() 会向父级索取「最大可用高度」——当 GlassSurface 被放进
           Scaffold 的 bottomBar 这类只给 max 约束、不给 min 高度的槽位时，
           它会把整个 Box 撑成满屏高，进而把 innerPadding.bottom 顶到屏高，
           内容区被完全挤出可视范围（v1.0.7 首页全空白的根因）。
           matchParentSize() 只「跟随」最终尺寸、不参与测量，语义才正确。 */
        if (useBackdrop) {
            // 1) 实时采样壁纸 + 位置感知模糊（GPU 档，API 31+）。
            //    v1.0.6.6（死命令 #3）：废弃「固定半透明纯色」—— 卡片按自身在窗口中的位置对齐
            //    背后同一张壁纸，配合 RenderEffect 高斯模糊，真正透出下方壁纸的真实色彩；
            //    列表滚动时卡片位置改变 → 采样区域随之移动 → 折射光影随滑动而变化。
            //    采样图放大到整屏尺寸并反向偏移卡片窗口坐标，使壁纸在卡片范围内「对位」显示。
            val configuration = LocalConfiguration.current
            val wDensity = LocalDensity.current
            val screenW = configuration.screenWidthDp.dp
            val screenH = configuration.screenHeightDp.dp
            val blurPx = with(wDensity) { GLASS_BLUR_RADIUS_DP.toPx() }
            val bgOffset = remember { mutableStateOf(Offset.Zero) }
            Box(
                Modifier
                    .matchParentSize()
                    .clip(shape)
                    .onGloballyPositioned { coords: LayoutCoordinates ->
                        // localToWindow(Offset.Zero) == positionInWindow()：取卡片左上角在窗口中的坐标
                        bgOffset.value = coords.localToWindow(Offset.Zero)
                    }
            ) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(LocalContext.current)
                        .data(File(wallPath!!))
                        .size(420)
                        .crossfade(false)
                        .build(),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
                    modifier = Modifier
                        .size(screenW, screenH)
                        .graphicsLayer {
                            translationX = -bgOffset.value.x
                            translationY = -bgOffset.value.y
                            // Android 12+：原生 RenderEffect 高斯模糊（真正的折射观感）
                            // 平台 android.graphics.RenderEffect 需经 asComposeRenderEffect() 转为 Compose 类型
                            if (Build.VERSION.SDK_INT >= 31) {
                                renderEffect = RenderEffect.createBlurEffect(
                                    blurPx, blurPx, android.graphics.Shader.TileMode.CLAMP
                                ).asComposeRenderEffect()
                            }
                        }
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

/* 模糊半径：18dp(v1.0.6) → 10dp(v1.0.7) → 6dp(v1.0.6.7)。
   低端降级档（FLAT 纯色）完全不走这里，零 GPU 开销。
   v1.0.6.6（死命令 #3）：现直接用于 RenderEffect.createBlurEffect 的高斯模糊半径，
   配合位置感知壁纸采样，实现真正随滑动变化的动态毛玻璃。 */
val GLASS_BLUR_RADIUS_DP: Dp = 6.dp

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
