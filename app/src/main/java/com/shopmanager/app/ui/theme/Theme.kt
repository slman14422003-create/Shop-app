package com.shopmanager.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * iOS-style continuous, generously-rounded corners (closer to SwiftUI's
 * default card/sheet/button radii than Material's usual tighter defaults)
 * so cards, dialogs, buttons and sheets across the whole app read as soft
 * and "squircle"-like instead of sharply cut. Bumped up from the previous
 * 6/10/14/20/28 scale for a noticeably friendlier, more premium feel while
 * keeping the same five-step Material shape system every screen already
 * pulls from via MaterialTheme.shapes, so this single change cascades app-wide.
 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

enum class AppThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun rememberIsDarkTheme(themeMode: AppThemeMode): Boolean {
    val systemDark = isSystemInDarkTheme()
    return when (themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
}

/**
 * BUG FIXED (RTL layout): every string in this app is hardcoded Arabic —
 * there's no strings.xml localization, no values-ar folder, nothing that
 * makes layout direction depend on the device's language setting. Compose
 * only switches Row/Column/padding("start"/"end")/alignment to RTL when the
 * *system* locale itself is Arabic (or another RTL language). A very common
 * case on an economy phone: the device's system language is left on English
 * (or any LTR language) while the person just uses this Arabic app. Before
 * this fix, on exactly that setup, Arabic *text* still rendered
 * right-to-left (that's Unicode bidi inside each Text, unrelated to layout
 * direction) but every Row, icon, and start/end padding stayed mirrored to
 * LTR — avatars, action buttons, and chevrons landing on the wrong side of
 * the row relative to the text. That's the "خطأ بتنسيق الشاشة" — a layout
 * direction bug, not a text-direction one, and it wouldn't show up on a
 * device whose Android language is already set to Arabic, which is why it
 * could look fine on some phones and broken on others. This app has no LTR
 * content anywhere, so layout direction is now forced to RTL unconditionally
 * instead of following the system locale.
 */
@Composable
fun ShopManagerTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    colorMode: AppColorMode = AppColorMode.MANUAL,
    colorPalette: AppColorPalette = AppColorPalette.INDIGO,
    content: @Composable () -> Unit
) {
    val useDark = rememberIsDarkTheme(themeMode)

    val paletteColors = remember(colorPalette) { paletteColorsFor(colorPalette) }
    val colors = remember(colorMode, paletteColors, useDark) {
        when (colorMode) {
            AppColorMode.GLASS ->
                if (useDark) glassDarkScheme(paletteColors) else glassLightScheme(paletteColors)
            AppColorMode.CLASSIC ->
                if (useDark) neutralDarkScheme() else neutralLightScheme()
            AppColorMode.MANUAL ->
                if (useDark) darkSchemeFor(paletteColors) else lightSchemeFor(paletteColors)
        }
    }

    val gradientColors = remember(colorMode, paletteColors) {
        when (colorMode) {
            AppColorMode.GLASS -> glassGradientColors(paletteColors)
            AppColorMode.CLASSIC -> listOf(ClassicGradientStart, ClassicGradientEnd)
            AppColorMode.MANUAL -> listOf(paletteColors.gradientStart, paletteColors.gradientEnd)
        }
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalBrandGradientColors provides gradientColors,
        // Scoped switch for the animated liquid-glass motion (drift/sheen)
        // and the extra translucency in liquidGlassSurface/GlassAlertDialog/
        // the glass buttons — on only for AppColorMode.GLASS, so MANUAL and
        // CLASSIC keep their existing calm, static glass panels untouched.
        LocalGlassMode provides (colorMode == AppColorMode.GLASS)
    ) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

