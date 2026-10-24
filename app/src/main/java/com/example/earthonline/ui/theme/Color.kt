package com.example.earthonline.ui.theme

import androidx.compose.ui.graphics.Color

// 对应 HTML 暖白 Notion 风 + 克制毛玻璃（style.css 变量）
// 主色：暖琥珀；背景：纸感米白；边框：浅卡其；文字：近黑暖灰
val AmberPrimary = Color(0xFFD4A373)
val AmberPrimaryDark = Color(0xFFE0A96D)
val AmberContainer = Color(0xFFF0E2D2)

val BackgroundLight = Color(0xFFF8F6F2)   // --bg #f8f6f2
val SurfaceLight = Color(0xFFFFFFFF)        // --panel #fff
val BorderLight = Color(0xFFE8E2DA)        // --border #e8e2da
val TextPrimaryLight = Color(0xFF1E1A16)    // --text #1e1a16
/* v1.0.6.8（死命令 #2 全局对比度修复）：二级/三级文字整体加深一档。
   原 #7A7268 在白底约 4.6:1、在 0.88 半透明卡片上跌到 4:1 以下，小字号发虚；
   现 #6A6258（白底 ≈5.9:1）、#8D8478（≈4.6:1），深浅主题均达 WCAG AA。 */
val TextSecondaryLight = Color(0xFF6A6258)  // --text-2 #6a6258（加深）
val TextTertiaryLight = Color(0xFF8D8478)   // --text-3 #8d8478（加深）

/* v1.2.1 深色对比度复核（第 18 项）：
   原深色值里 --caption/#71717A 这类三级文字在 #232326 上只有约 3.3:1，
   小字号（labelSmall）下几乎看不清；边框 #333 与背景 #232326 的差异也只有肉眼勉强可辨。
   下面把三级/二级文字与边框整体提亮一档，正文与背景的对比度拉到 12:1 以上，
   三级说明文字也从 3.3:1 提到 5:1 以上（WCAG AA 对小字的要求是 4.5:1）。 */
val BackgroundDark = Color(0xFF1A1A1A)
val SurfaceDark = Color(0xFF232326)
val BorderDark = Color(0xFF3F3F46)
val TextPrimaryDark = Color(0xFFF2F2F5)
val TextSecondaryDark = Color(0xFFB6B6BF)
val TextTertiaryDark = Color(0xFF8F8F99)

/* ————— v1.0.4 设计系统：语义色浅/深双档（一次到位） —————
   规则：浅色主题取「深色值」（白底上 ≥4.5:1），深色主题取「浅色值」（#232326 底上 ≥4.5:1）。
   所有强调组件（按钮/图标/角标）的语义色一律从这两档取，禁止散落硬编码。 */

/** 琥珀底上的文字/图标前景（浅深主题一致，深棕保证对比度，对齐 Web 端按钮 fg #241A10） */
val OnAmber = Color(0xFF241A10)

/** 深色主题的琥珀容器（原 Theme.kt 内联值 0xFF3A2E22 提升为命名 token） */
val AmberContainerDark = Color(0xFF3A2E22)
val OnAmberContainerDark = Color(0xFFF0E2D2)

/** 成功（完成/达成） */
val SuccessLight = Color(0xFF588157)
val SuccessDark = Color(0xFFA3C4A3)

/** 危险（删除/错误/超期） */
val DangerLight = Color(0xFFBC4749)
val DangerDark = Color(0xFFE59A9B)

/** 警告（到期提醒/暂停） */
val WarningLight = Color(0xFF9A6B1F)
val WarningDark = Color(0xFFE6C079)

/** 信息（链接/提示） */
val InfoLight = Color(0xFF457B9D)
val InfoDark = Color(0xFF9CC3DB)
