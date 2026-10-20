package com.example.earthonline.ui.Root

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.earthonline.R
import com.example.earthonline.ui.theme.AmberPrimary
import kotlinx.coroutines.delay

/** 开屏最短展示时长：v1.0.7 由 900ms 提到 1400ms —— 真机反馈「动画丢失」，
 *  实际是动画太短、被当作闪一下忽略了。1.4s 让「图标放大 + 文字上浮 + 进度线」
 *  完整走完一个可辨认的节奏，仍不至于让人等得烦躁。 */
const val SPLASH_MIN_MS = 1400L

/**
 * v1.0.0：开屏底色 = **纯白**（浅色模式）。
 *
 * 必须与 `res/values/colors.xml` 的 `splash_background`、
 * `values(-v31)/themes.xml` 的 `windowSplashScreenBackground` 是同一个值。
 * 冷启动是三段接力：系统启动窗口 → 本开屏页 → 主界面。
 * 前两段只要有一处色值不一致，交接那一帧就会「换底色」，也就是用户看到的割裂感。
 *
 * v1.0.2+：增加深色变体（#1A1A1A，与 values-night 的 background_dark 同源），
 * 消除深色模式下「系统启动窗口深色 → Compose 开屏纯白」的白闪。
 * 系统层已由 values-night/themes.xml 同步覆盖，三段在各自模式下依然同色。
 */
val SplashBackground: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF1A1A1A) else Color.White

/** 开屏文字色：跟随开屏底色 —— 浅色底配深字，深色底配浅字（#F2F2F5），保证两种模式下都可读 */
private val SplashTextPrimary: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFF2F2F5) else Color(0xFF1E1A16)
private val SplashTextSecondary: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFA9A196) else Color(0xFF7A7268)

/**
 * v1.2.1：自绘开屏动画。
 *
 * 为什么不用系统 SplashScreen 的那张图收尾：系统启动图只能放「一张居中的图标 + 纯色底」，
 * 而且在 Android 12 以下干脆只有个色块；冷启动时那一下的观感，全靠这张图撑。
 * 所以这里在系统启动图之上再叠一层 Compose 开屏：
 *  ① 图标从 0.86 倍放大到原尺寸（带缓出），背后一层琥珀色光晕缓慢呼吸；
 *  ② 应用名与副标题依次上浮淡入（错开 80ms，形成节奏而不是一起「蹦」出来）；
 *  ③ 底部一条细琥珀线在 900ms 内匀速走到 46% 宽 —— 不是假进度条，
 *     它走完的时刻就是内容就绪的时刻（走完即淡出），看得见的等待比空转的白屏体面。
 *
 * v1.0.0 衔接修正（彻底消除冷启动割裂感）：
 *  - 底色 = 纯白，与系统启动窗口、主主题 windowBackground 三者同色；
 *  - 系统层**不放图标**（主题里用透明占位 splash_blank），所以这里不存在
 *    「系统图标 → Compose 图标」的位置 / 大小对齐问题，动画可以放开做；
 *  - 图标仍严格屏幕居中、文字用 offset 挂在下方 —— 居中是构图的需要，不再是为了对齐系统图标。
 *
 * 退出用 260ms 淡出：不做位移，避免和下一页的进场动画「对撞」。
 */
@Composable
fun SplashOverlay(contentReady: Boolean, onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(contentReady) {
        if (contentReady) {
            // 内容就绪后再停留 SPLASH_MIN_MS，让入场动画跑完；之后置 false 触发淡出
            delay(SPLASH_MIN_MS)
            visible = false
            onFinished()
        }
    }

    // 图标入场：0.86 -> 1，缓出
    var iconShown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { iconShown = true }
    val iconScale by animateFloatAsState(
        targetValue = if (iconShown) 1f else 0.86f,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "splashIcon"
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (iconShown) 1f else 0f,
        animationSpec = tween(durationMillis = 360),
        label = "splashIconAlpha"
    )

    // 光晕呼吸：1.0 <-> 1.08，3s 一轮。只动 scale，不动 alpha，几乎零成本
    val breath = rememberInfiniteTransition(label = "splashBreath")
    val halo by breath.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo"
    )

    // 底部细线：内容就绪后匀速走到位（900ms），走完即退
    val lineProgress by animateFloatAsState(
        targetValue = if (contentReady) 1f else 0.12f,
        animationSpec = tween(durationMillis = 900, easing = LinearEasing),
        label = "splashLine"
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(260))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SplashBackground)
        ) {
            /* ① 图标：单独居中，位置与系统启动图里的 splash_logo 重合。
               光晕 132dp、logo 84dp —— 与 drawable/splash_logo.xml 同款构图。 */
            Box(
                modifier = Modifier.align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(132.dp)
                        .scale(halo)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    AmberPrimary.copy(alpha = 0.16f),
                                    AmberPrimary.copy(alpha = 0.05f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Image(
                    painter = painterResource(R.drawable.ic_app_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(84.dp)
                        .scale(iconScale)
                        .graphicsLayer { alpha = iconAlpha }
                        .clip(RoundedCornerShape(22.dp))
                )
            }

            /* ② 文字：挂在图标下方固定偏移（42dp 半径 + 18dp 间距），
               不参与垂直居中 —— 否则会把图标顶离屏幕中心，与系统启动图对不齐。 */
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedVisibility(
                    visible = iconShown,
                    enter = fadeIn(tween(300, delayMillis = 140)) +
                        slideInVertically(tween(320, delayMillis = 140)) { it / 4 }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "地球Online",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            color = SplashTextPrimary
                        )
                        Text(
                            "把人生当成一场开放世界游戏",
                            style = MaterialTheme.typography.bodySmall,
                            color = SplashTextSecondary,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            // ③ 底部细线：46% 宽上限，居中
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 56.dp)
                    .align(Alignment.BottomCenter),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .width((140 * lineProgress).dp)
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(AmberPrimary)
                )
            }
        }
    }
}
