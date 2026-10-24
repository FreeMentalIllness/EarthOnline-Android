package com.example.earthonline.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.earthonline.ui.components.UiDimens
import com.example.earthonline.ui.components.bouncyClickable

/**
 * 全局液态玻璃（Liquid Glass）封装 —— 仅作**统一材质与描边**入口，业务层只调用 [GlassSurface]。
 *
 * 死命令（v1.0.6.x 彻底去壁纸化）：**卡片 / 对话框底板一律实心高不透明度纯色**
 * （surface 0.97 ~ 1.0，由 [LocalCardOpacity] 控制，默认 1.0 = 完全不透明），
 * 绝不从壁纸采样毛玻璃 —— 任何花壁纸上文字都绝对清晰。
 * 「壁纸氛围」只由 [MainScaffold] 的全屏背景层负责（一级页面间隙透出），与卡片层解耦。
 *
 * 降级矩阵（仅描述历史分层意图；卡片实际永远走实心纯色档，不再做 GPU 模糊）：
 *  - [GlassTier.FULL]：API 33+ 且非低内存；
 *  - [GlassTier.BLUR]：API 31/32 且非低内存；
 *  - [GlassTier.FLAT]：API < 31 或低内存。
 * 三档仅影响全屏壁纸层的模糊半径，不再影响卡片底板。
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

/* ============================================================
 * v1.0.6 vc62（顶级液态玻璃质感批）—— 深度参考开源项目 zhengfang-apk
 * （znjhahaha/zhengfang-apk，Kyant Backdrop 的 blur + lens + vibrancy 渲染栈）：
 *  - 折射（lens）：卡片边缘 refractionHeight 带内，按圆角矩形 SDF 法线把采样点向内推，
 *    背景在边缘被「弯折放大」，玻璃有厚度与透光感；
 *  - 色散（chromatic aberration）：R/G/B 三通道按法线方向做 ±uAberr 的错位采样，
 *    边缘出现棱镜式彩色镶边；
 *  - 顶部极细高光（Top-edge highlight）：DrawScope 手绘 1.5px 渐变高光线 +
 *    底部内阴影，模拟左上打光的玻璃倒角。
 * AGSL RuntimeShader 仅 API 33+ 可用；API 31/32 回落纯高斯模糊；低版本纯半透明。
 * ============================================================ */
private const val GLASS_LENS_AGSL = """
    uniform shader uContent;
    uniform float2 uSize;
    uniform float uRadius;
    uniform float uRefH;
    uniform float uRefAmt;
    uniform float uAberr;

    float sdRoundRect(float2 p, float2 hs, float r) {
        float2 q = abs(p) - hs + r;
        return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
    }

    half4 main(float2 fragCoord) {
        float2 c = uSize * 0.5;
        float d = sdRoundRect(fragCoord - c, c, uRadius);
        float e = 0.5;
        float2 g = float2(
            sdRoundRect(fragCoord - c + float2(e, 0.0), c, uRadius) - d,
            sdRoundRect(fragCoord - c + float2(0.0, e), c, uRadius) - d
        );
        float gl = length(g);
        g = gl > 0.0001 ? g / gl : float2(0.0);
        float t = clamp(1.0 + d / uRefH, 0.0, 1.0);
        float k = t * t * uRefAmt;
        float2 uv = fragCoord - g * k;
        half4 col = half4(0.0);
        col.r = uContent.eval(uv - g * uAberr * t).r;
        col.g = uContent.eval(uv).g;
        col.b = uContent.eval(uv + g * uAberr * t).b;
        col.a = 1.0;
        return col;
    }
"""

/** 折射带高度（边缘往内多少像素开始弯折） */
val GLASS_REFRACTION_HEIGHT_DP: Dp = 12.dp

/** 折射强度（边缘最大位移像素） */
val GLASS_REFRACTION_AMOUNT_DP: Dp = 12.dp

/** 色散错位（RGB 三通道边缘错位像素） */
const val GLASS_ABERRATION_PX = 1.5f

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
 * @param solid v1.0.6.9（死命令 去壁纸化）：近不透明纯色模式 —— 跳过壁纸采样，
 *   遮罩 Alpha 固定 0.97。所有弹窗 / 对话框强制走此档：绝对不允许透出壁纸。
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
    /** v1.0.6.9（死命令）：true = 弹窗档，0.97 近不透明纯色、不采样壁纸 */
    solid: Boolean = false,
    /** vc62：折射 SDF 用的圆角半径（与 shape 视觉圆角一致即可；仅 API33+ 折射档消费） */
    fxRadius: Dp = 20.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    // v1.0.6.7（死命令 #1 断层修复）演进：卡片底板已彻底废弃「壁纸采样」——
    // 旧版多层叠加（模糊壁纸 + 渐变 tint + scrim + 顶部高光）且壁纸按每卡偏移，
    // 导致灰/白色块断层。现底板只由下方「单层高不透明度纯色遮罩 + 1dp 描边」构成，
    // 零壁纸采样、零断层，任意壁纸下文字都清晰。
    /* v1.0.6.10（彻底去壁纸化·死命令）：**彻底废弃卡片 / 弹窗内的壁纸采样**。
       任何卡片、对话框底板都不再从壁纸文件采样毛玻璃 —— 底层始终是一层
       极高不透明度的纯色（surface 0.97~1.0），绝对不允许花壁纸透过来破字。
       一级页面的「壁纸氛围」仅由 MainScaffold 的全屏背景层负责（页面间隙透出），
       卡片与弹窗一律实心，确保任意壁纸下文字绝对清晰。 */
    val wallPath = LocalWallpaperPath.current
    val liquid = LocalLiquidGlass.current
    // v1.0.6.11（本轮回滚死命令#1「去壁纸化」）：恢复全局毛玻璃 —— 卡片 / Tab 栏半透明，
    // 隐约透出底层壁纸、文字仍清晰。底层实时采样壁纸 + RenderEffect 高斯模糊（Crop 取景、
    // 不偏移，彻底杜绝色块断层），其上压一层半透明白色蒙版（Color.White.copy(alpha≈0.65)
    // 量级）+ 极轻可读性遮罩。弹窗(solid=0.97 近不透明) 与 无壁纸 / 性能模式 回落近不透明纯色。
    val useBackdrop = !solid && liquid && !wallPath.isNullOrBlank()
    val frostAlpha = if (solid) 0.97f else 0.66f
    // vc62：高光/折射共用的圆角像素半径（函数级，供 drawBehind 与 shader 共用）
    val fxRadiusPx = with(LocalDensity.current) { fxRadius.toPx() }

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
        if (useBackdrop) {
            // 1) 毛玻璃底层：采样壁纸 + 高斯模糊（GPU 档，API31+；低版本跳过模糊只透色相）
            //    vc62：API33+ 追加 AGSL lens 折射 + 色散（blur → RuntimeShader 链式 RenderEffect）
            val wDensity = LocalDensity.current
            val blurPx = with(wDensity) { GLASS_BLUR_RADIUS_DP.toPx() }
            val refHPx = with(wDensity) { GLASS_REFRACTION_HEIGHT_DP.toPx() }
            val refAmtPx = with(wDensity) { GLASS_REFRACTION_AMOUNT_DP.toPx() }
            // AGSL 折射 shader：API 33+ 才有 RuntimeShader；编译失败静默回落纯模糊
            val lensShader = if (Build.VERSION.SDK_INT >= 33) {
                runCatching { RuntimeShader(GLASS_LENS_AGSL) }.getOrNull()
            } else null
            Box(Modifier.matchParentSize().clip(shape)) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(File(wallPath))
                        .size(240)
                        .crossfade(false)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            renderEffect = if (Build.VERSION.SDK_INT >= 31) {
                                val blur = RenderEffect.createBlurEffect(
                                    blurPx, blurPx, android.graphics.Shader.TileMode.CLAMP
                                )
                                if (lensShader != null && size.width > 0f && size.height > 0f) {
                                    runCatching {
                                        lensShader.setFloatUniform("uSize", size.width, size.height)
                                        lensShader.setFloatUniform("uRadius", fxRadiusPx)
                                        lensShader.setFloatUniform("uRefH", refHPx)
                                        lensShader.setFloatUniform("uRefAmt", refAmtPx)
                                        lensShader.setFloatUniform("uAberr", GLASS_ABERRATION_PX)
                                        val lens = RenderEffect.createRuntimeShaderEffect(lensShader, "uContent")
                                        RenderEffect.createChainEffect(lens, blur).asComposeRenderEffect()
                                    }.getOrElse { blur.asComposeRenderEffect() }
                                } else blur.asComposeRenderEffect()
                            } else null
                        }
                )
            }
            // 2) 半透明白色蒙版（毛玻璃质感，隐约透出壁纸色相）
            Box(
                Modifier.matchParentSize().clip(shape)
                    .background(Color.White.copy(alpha = frostAlpha))
            )
            // 3) 极轻可读性遮罩（保证任意壁纸下正文清晰）
            Box(
                Modifier.matchParentSize().clip(shape)
                    .background(scheme.surface.copy(alpha = 0.10f))
            )
        } else {
            // 无壁纸 / 性能模式 / 弹窗档：近不透明纯色底板
            Box(
                Modifier.matchParentSize().clip(shape)
                    .background(scheme.surface.copy(alpha = frostAlpha))
            )
        }
        // vc62：顶部极细高光 —— DrawScope 手绘的玻璃倒角（左上打光，仅 1.5px 细线）。
        // vc63（真机反馈「灰砖」根治）：① 删除底部 20dp 内阴影 —— 其 verticalGradient 未映射到
        // 矩形（Brush 默认铺满整个绘制区），整条带几乎恒定 6% 黑，视觉上就是一块灰色砖头；
        // ② 顶部高光渐变显式传 startY/endY，锁定在 1.5px 细线内，保证「极细高光」真实生效。
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    val hi = 1.5.dp.toPx()
                    val inset = fxRadiusPx * 0.55f
                    // 顶边高光线：白 → 透明，左上角最亮（渐变严格限制在细线矩形内）
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.70f),
                                Color.White.copy(alpha = 0.28f),
                                Color.Transparent
                            ),
                            startX = inset,
                            endX = size.width - inset
                        ),
                        topLeft = Offset(inset, 0f),
                        size = Size(size.width - inset * 2f, hi)
                    )
                }
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
        scrimOverride = (0.28f * (userAlpha / 0.74f)).coerceIn(0.22f, 0.42f),
        // vc62：折射 SDF 圆角与卡片视觉圆角同源
        fxRadius = UiDimens.CardRadius
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
