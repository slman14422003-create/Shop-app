package com.shopmanager.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
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

/** True only on Android 12+ (API 31), where [dynamicLightColorScheme]/
 * [dynamicDarkColorScheme] actually exist and read the device wallpaper —
 * gates whether [AppColorMode.DYNAMIC] can be offered/honored at all. */
@Composable
fun isDynamicColorSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

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
    val context = LocalContext.current
    val dynamicSupported = isDynamicColorSupported()

    // Falls back to MANUAL's own selected palette if DYNAMIC is somehow
    // stored on a device that doesn't support it (e.g. after restoring a
    // backup taken on a newer phone onto an older one running < Android 12).
    val effectiveMode = if (colorMode == AppColorMode.DYNAMIC && !dynamicSupported) {
        AppColorMode.MANUAL
    } else colorMode

    val paletteColors = remember(colorPalette) { paletteColorsFor(colorPalette) }
    val colors = remember(effectiveMode, paletteColors, useDark, context) {
        when (effectiveMode) {
            AppColorMode.DYNAMIC ->
                if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            AppColorMode.CLASSIC ->
                if (useDark) neutralDarkScheme() else neutralLightScheme()
            AppColorMode.MANUAL ->
                if (useDark) darkSchemeFor(paletteColors) else lightSchemeFor(paletteColors)
        }
    }

    // The header/status-bar gradient (LocalBrandGradientColors) has no
    // single "gradientStart/End" pair to read off a system dynamic scheme,
    // so it's built straight from that scheme's own primary/tertiary tones
    // instead — same idea as every manual palette's own two-color pair,
    // just sourced from the wallpaper instead of a hand-picked hue.
    val gradientColors = remember(effectiveMode, colors, paletteColors) {
        when (effectiveMode) {
            AppColorMode.DYNAMIC -> listOf(colors.primary, colors.tertiary)
            AppColorMode.CLASSIC -> listOf(ClassicGradientStart, ClassicGradientEnd)
            AppColorMode.MANUAL -> listOf(paletteColors.gradientStart, paletteColors.gradientEnd)
        }
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalBrandGradientColors provides gradientColors
    ) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

