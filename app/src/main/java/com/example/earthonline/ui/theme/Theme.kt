package com.example.earthonline.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/*
  注意：Material3 的 Card / FilterChip / 各种 Container 实际取的是
  surfaceContainer* / surfaceTint / *Container 这些"新角色"，
  只覆盖 primary/background/surface 会落回默认的紫色基线调色板
  （现象：卡片发淡紫）。这里把整套暖白配色补齐。
*/
private val LightColorScheme = lightColorScheme(
    primary = AmberPrimary,
    onPrimary = OnAmber,
    primaryContainer = AmberContainer,
    onPrimaryContainer = TextPrimaryLight,

    secondary = AmberPrimary,
    onSecondary = OnAmber,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = TextPrimaryLight,

    tertiary = AmberPrimary,
    onTertiary = OnAmber,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = TextPrimaryLight,

    // v1.0.4：语义错误色接入设计系统（DangerLight，白底 ≥4.5:1）
    error = DangerLight,
    onError = Color.White,
    errorContainer = Color(0xFFF5DFDD),
    onErrorContainer = TextPrimaryLight,

    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    /* v1.0.6 全局视觉统一：卡片 / 容器一律带 0.88 透明度，透出壁纸。
       background 保持不透明（它是壁纸之上的「蒙层」，负责压住壁纸亮度），
       只有 surface 家族透 —— 这样卡片浮在壁纸上、页面底色仍然稳。 */
    surface = SurfaceLight.copy(alpha = 0.88f),
    onSurface = TextPrimaryLight,
    surfaceVariant = BackgroundLight.copy(alpha = 0.86f),
    onSurfaceVariant = TextSecondaryLight,

    // 卡片 / 容器层级（Card 默认取 surfaceContainerLow）
    surfaceContainerLowest = Color(0xFFFFFFFF).copy(alpha = 0.86f),
    surfaceContainerLow = Color(0xFFFFFFFF).copy(alpha = 0.88f),
    surfaceContainer = Color(0xFFF8F6F2).copy(alpha = 0.88f),
    surfaceContainerHigh = Color(0xFFF1EDE7).copy(alpha = 0.88f),
    surfaceContainerHighest = Color(0xFFEAE4DC).copy(alpha = 0.88f),
    surfaceBright = Color(0xFFFFFFFF).copy(alpha = 0.86f),
    surfaceDim = Color(0xFFEFEBE5).copy(alpha = 0.88f),
    surfaceTint = AmberPrimary,

    outline = BorderLight,
    outlineVariant = BorderLight,

    inverseSurface = TextPrimaryLight,
    inverseOnSurface = Color(0xFFFFFFFF),
    inversePrimary = AmberPrimaryDark
)

private val DarkColorScheme = darkColorScheme(
    primary = AmberPrimaryDark,
    onPrimary = OnAmber,
    primaryContainer = AmberContainerDark,
    onPrimaryContainer = OnAmberContainerDark,

    secondary = AmberPrimaryDark,
    onSecondary = OnAmber,
    secondaryContainer = AmberContainerDark,
    onSecondaryContainer = OnAmberContainerDark,

    tertiary = AmberPrimaryDark,
    onTertiary = OnAmber,
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = OnAmberContainerDark,

    // v1.0.4：语义错误色接入设计系统（DangerDark，#232326 底 ≥4.5:1）
    error = DangerDark,
    onError = Color(0xFF3B1717),
    errorContainer = Color(0xFF4A2624),
    onErrorContainer = Color(0xFFF5DFDD),

    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    /* 深色下壁纸透出量刻意压到 0.80（比浅色更克制）：亮色壁纸在近黑卡片背后
       透太多会破坏夜间一致性、也伤可读性；这个量透出「有壁纸」就够了。 */
    surface = SurfaceDark.copy(alpha = 0.80f),
    onSurface = TextPrimaryDark,
    surfaceVariant = BackgroundDark.copy(alpha = 0.78f),
    onSurfaceVariant = TextSecondaryDark,

    surfaceContainerLowest = Color(0xFF1A1A1A).copy(alpha = 0.78f),
    surfaceContainerLow = Color(0xFF232326).copy(alpha = 0.80f),
    surfaceContainer = Color(0xFF28282B).copy(alpha = 0.80f),
    surfaceContainerHigh = Color(0xFF2E2E32).copy(alpha = 0.80f),
    surfaceContainerHighest = Color(0xFF35353A).copy(alpha = 0.80f),
    surfaceBright = Color(0xFF35353A).copy(alpha = 0.80f),
    surfaceDim = Color(0xFF1A1A1A).copy(alpha = 0.78f),
    surfaceTint = AmberPrimaryDark,

    outline = BorderDark,
    outlineVariant = BorderDark,

    inverseSurface = TextPrimaryDark,
    inverseOnSurface = Color(0xFF1A1A1A),
    inversePrimary = AmberPrimary
)

/**
 * 全局主题。darkTheme 由 SettingsDataStore 的 theme 偏好决定（light/dark/system），
 * 对应 HTML 的 [data-theme="dark"] 切换；其余沿用 Material3 默认排版形状。
 */
@Composable
fun EarthOnlineTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** v1.2.0：字号档位 key（std / lg / xl），由设置页写入 DataStore */
    fontScaleKey: String = "std",
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = scaledTypography(fontScaleOf(fontScaleKey)),
        /* 对话框圆角统一为 16dp，与 Web 端 .modal（--radius-card=16px）及卡片圆角一致 */
        shapes = MaterialTheme.shapes.copy(extraLarge = RoundedCornerShape(16.dp)),
        content = content
    )
}
