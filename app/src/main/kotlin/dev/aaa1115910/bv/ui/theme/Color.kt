package dev.aaa1115910.bv.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.darkColorScheme

/**
 * BV 的配色基底。
 *
 * 整体是一套「深空蓝黑 + 品牌粉 + 电子青」的暗色方案：
 * 背景不用纯黑而是带蓝调的墨色，在电视的大屏幕上更有层次也更不容易糊成一片；
 * 粉色保留 B 站的品牌识别，青色/紫色作为科技感的点缀，主要用在焦点光晕和渐变里。
 */
object BVColor {
    /** 最底层背景，带一点蓝调的墨色 */
    val Ink = Color(0xFF07090F)
    val InkElevated = Color(0xFF0D111A)

    /** 卡片、弹窗等浮起表面 */
    val Surface = Color(0xFF141926)
    val SurfaceVariant = Color(0xFF1D2432)
    val SurfaceHighlight = Color(0xFF283041)

    /** 品牌粉 */
    val Pink = Color(0xFFFB7299)
    val PinkBright = Color(0xFFFF8FB0)
    val PinkDeep = Color(0xFFD94E78)

    /** 电子青，科技感主要来源 */
    val Cyan = Color(0xFF23D3EE)
    val CyanDeep = Color(0xFF0EA5C6)

    /** 辅助紫，用在渐变的中间调 */
    val Violet = Color(0xFF7C6BFF)

    /** 文字 */
    val TextPrimary = Color(0xFFF2F4F8)
    val TextSecondary = Color(0xFFA9B2C3)
    val TextTertiary = Color(0xFF6C7689)

    val Error = Color(0xFFFF6B6B)

    /** 焦点描边与光晕 */
    val FocusRing = Color(0xFFFFFFFF)
    val FocusGlow = Color(0xFF23D3EE)
}

/** 焦点描边用的渐变：粉 → 紫 → 青，扫过卡片边缘时会有金属反光的感觉 */
val FocusRingBrush = Brush.linearGradient(
    colors = listOf(BVColor.Pink, BVColor.Violet, BVColor.Cyan)
)

/** 强调用的渐变，用于进度条、指示器等细长元素 */
val AccentBrush = Brush.horizontalGradient(
    colors = listOf(BVColor.Pink, BVColor.Violet, BVColor.Cyan)
)

/** 页面底色：从左上的墨色渐变到右下略亮的深蓝，避免整屏死黑 */
val AmbientBackgroundBrush = Brush.linearGradient(
    colors = listOf(
        BVColor.Ink,
        BVColor.InkElevated,
        Color(0xFF0B0E17)
    )
)

/** 卡片封面底部的遮罩，比单色半透明黑更自然 */
val CoverScrimBrush = Brush.verticalGradient(
    colors = listOf(
        Color.Transparent,
        Color.Black.copy(alpha = 0.35f),
        Color.Black.copy(alpha = 0.78f)
    )
)

val BVTvColorScheme = darkColorScheme(
    primary = BVColor.Pink,
    onPrimary = Color(0xFF2A0A15),
    primaryContainer = BVColor.PinkDeep,
    onPrimaryContainer = Color(0xFFFFE1EA),
    secondary = BVColor.Cyan,
    onSecondary = Color(0xFF00212A),
    secondaryContainer = BVColor.CyanDeep,
    onSecondaryContainer = Color(0xFFD6F7FF),
    tertiary = BVColor.Violet,
    onTertiary = Color(0xFF120C33),
    background = BVColor.Ink,
    onBackground = BVColor.TextPrimary,
    surface = BVColor.Surface,
    onSurface = BVColor.TextPrimary,
    surfaceVariant = BVColor.SurfaceVariant,
    onSurfaceVariant = BVColor.TextSecondary,
    inverseSurface = BVColor.SurfaceHighlight,
    inverseOnSurface = BVColor.TextPrimary,
    error = BVColor.Error,
    onError = Color(0xFF2A0A0A),
    border = BVColor.FocusRing,
    borderVariant = Color(0xFF33405A),
    scrim = Color(0xFF000000)
)

val BVCommonColorScheme = androidx.compose.material3.darkColorScheme(
    primary = BVColor.Pink,
    onPrimary = Color(0xFF2A0A15),
    primaryContainer = BVColor.PinkDeep,
    onPrimaryContainer = Color(0xFFFFE1EA),
    secondary = BVColor.Cyan,
    onSecondary = Color(0xFF00212A),
    secondaryContainer = BVColor.CyanDeep,
    onSecondaryContainer = Color(0xFFD6F7FF),
    tertiary = BVColor.Violet,
    onTertiary = Color(0xFF120C33),
    background = BVColor.Ink,
    onBackground = BVColor.TextPrimary,
    surface = BVColor.Surface,
    onSurface = BVColor.TextPrimary,
    surfaceVariant = BVColor.SurfaceVariant,
    onSurfaceVariant = BVColor.TextSecondary,
    surfaceContainer = BVColor.SurfaceVariant,
    surfaceContainerHigh = BVColor.SurfaceHighlight,
    inverseSurface = BVColor.SurfaceHighlight,
    inverseOnSurface = BVColor.TextPrimary,
    outline = Color(0xFF3A465E),
    outlineVariant = Color(0xFF262F41),
    error = BVColor.Error,
    onError = Color(0xFF2A0A0A),
    scrim = Color(0xFF000000)
)
