package com.cornming.slowjog.ui

import android.graphics.Typeface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** 三種介面風格，在「設定」選。 */
enum class AppTheme(val id: String, val nameZh: String, val descZh: String) {
    SUNRISE("sunrise", "晨光", "暖橘日出、圓潤可愛"),
    NIGHT("night", "夜跑錶", "深色底、大數字、運動感"),
    FOREST("forest", "森林清晨", "淡綠安靜、清爽簡潔");

    companion object {
        fun fromId(id: String?): AppTheme = values().firstOrNull { it.id == id } ?: SUNRISE
    }
}

/** 畫面用的顏色與數字字型；元件透過 [LocalPalette] 取用。 */
@Immutable
data class Palette(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    /** 主色的淡底（首頁主卡、選取底色）。 */
    val accentSoft: Color,
    val ink: Color,
    val inkSoft: Color,
    val line: Color,
    val accent: Color,
    val onAccent: Color,
    /** 強調用的文字顏色（在 bg 與 surface 上要夠清楚）。 */
    val accentText: Color,
    /** 暖身、超慢跑、肌力、伸展四個階段的顏色。 */
    val stages: List<Color>,
    /** 倒數大字的顏色。 */
    val countdown: Color,
    /** 大數字的字型。 */
    val numbers: FontFamily,
    /** 主卡的裝飾圓（太陽）顏色。 */
    val sun: Color,
)

val SunrisePalette = Palette(
    dark = false,
    bg = Color(0xFFFFF6EC),
    surface = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFFFE2C4),
    ink = Color(0xFF3A2A1F),
    inkSoft = Color(0xFF7A5E4A),
    line = Color(0xFFF0DCC8),
    accent = Color(0xFFD4521F),
    onAccent = Color(0xFFFFFFFF),
    accentText = Color(0xFFB8461A),
    stages = listOf(Color(0xFFFFC94D), Color(0xFFD4521F), Color(0xFF8FC8EB), Color(0xFF7FB77E)),
    countdown = Color(0xFFD4521F),
    numbers = FontFamily.SansSerif,
    sun = Color(0xFFFFC94D),
)

val NightPalette = Palette(
    dark = true,
    bg = Color(0xFF0F1214),
    surface = Color(0xFF1B2024),
    accentSoft = Color(0xFF1B2024),
    ink = Color(0xFFF2F5F3),
    inkSoft = Color(0xFFA7B0AC),
    line = Color(0xFF2C3338),
    accent = Color(0xFFC6F432),
    onAccent = Color(0xFF0F1214),
    accentText = Color(0xFFC6F432),
    stages = listOf(Color(0xFFF2F5F3), Color(0xFFC6F432), Color(0xFF4CC3FF), Color(0xFFFF9F5A)),
    countdown = Color(0xFFC6F432),
    numbers = FontFamily(androidx.compose.ui.text.font.Typeface(Typeface.create("sans-serif-condensed", Typeface.BOLD))),
    sun = Color(0xFF2C3338),
)

val ForestPalette = Palette(
    dark = false,
    bg = Color(0xFFF4F7F2),
    surface = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFDCEBDF),
    ink = Color(0xFF1F2D27),
    inkSoft = Color(0xFF55705F),
    line = Color(0xFFE3EAE4),
    accent = Color(0xFF246F53),
    onAccent = Color(0xFFFFFFFF),
    accentText = Color(0xFF246F53),
    stages = listOf(Color(0xFFE9B949), Color(0xFF2F8F6B), Color(0xFF5B8DB8), Color(0xFF9BC7A6)),
    countdown = Color(0xFF246F53),
    numbers = FontFamily.SansSerif,
    sun = Color(0xFFB9D6C0),
)

fun paletteOf(theme: AppTheme): Palette = when (theme) {
    AppTheme.SUNRISE -> SunrisePalette
    AppTheme.NIGHT -> NightPalette
    AppTheme.FOREST -> ForestPalette
}

val LocalPalette = staticCompositionLocalOf { SunrisePalette }

/** 依選擇的介面風格套用顏色；Material 元件（對話框、滑桿、開關）也跟著換色。 */
@Composable
fun SlowJogTheme(theme: AppTheme = AppTheme.SUNRISE, content: @Composable () -> Unit) {
    val p = paletteOf(theme)
    val scheme = if (p.dark) {
        darkColorScheme(
            primary = p.accent,
            onPrimary = p.onAccent,
            primaryContainer = p.surface,
            onPrimaryContainer = p.ink,
            secondary = p.accent,
            onSecondary = p.onAccent,
            secondaryContainer = p.surface,
            onSecondaryContainer = p.ink,
            background = p.bg,
            onBackground = p.ink,
            surface = p.surface,
            onSurface = p.ink,
            surfaceVariant = p.surface,
            onSurfaceVariant = p.inkSoft,
            surfaceContainer = p.surface,
            surfaceContainerHigh = p.surface,
            surfaceContainerHighest = p.surface,
            surfaceContainerLow = p.bg,
            outline = p.line,
            outlineVariant = p.line,
            error = Color(0xFFFF8A80),
        )
    } else {
        lightColorScheme(
            primary = p.accent,
            onPrimary = p.onAccent,
            primaryContainer = p.accentSoft,
            onPrimaryContainer = p.ink,
            secondary = p.accent,
            onSecondary = p.onAccent,
            secondaryContainer = p.accentSoft,
            onSecondaryContainer = p.ink,
            background = p.bg,
            onBackground = p.ink,
            surface = p.surface,
            onSurface = p.ink,
            surfaceVariant = p.accentSoft,
            onSurfaceVariant = p.inkSoft,
            surfaceContainer = p.surface,
            surfaceContainerHigh = p.surface,
            surfaceContainerHighest = p.surface,
            surfaceContainerLow = p.bg,
            outline = p.line,
            outlineVariant = p.line,
        )
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
