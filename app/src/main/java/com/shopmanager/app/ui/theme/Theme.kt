package com.shopmanager.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
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
/**
 * BUG FIXED ("الوضع المخصص لا يغيّر اللون"): [AppColorMode.MANUAL] ("مخصص"
 * in Settings) used to silently switch to `dynamicLightColorScheme`/
 * `dynamicDarkColorScheme` — Android 12+'s wallpaper-driven "Material You"
 * engine — the moment the device was on API 31+, completely ignoring
 * whichever of the 20 hand-tuned [AppColorPalette] swatches the person had
 * actually tapped. That's exactly why picking a swatch in "مخصص" appeared
 * to do nothing on most modern phones: the app was always painting itself
 * from the wallpaper instead, no matter which color was selected, and on
 * a dark/muted wallpaper that could land on near-black tones throughout
 * the app (buttons, the floating quick-add circle, etc.) with no way to
 * override it from Settings. "مخصص" now always uses the selected
 * [AppColorPalette] directly — the same fixed-palette behavior every
 * Android version before 12's dynamic color had (and the only behavior
 * this mode's own swatch grid ever implied it would have) — regardless of
 * OS version or wallpaper.
 */
/**
 * FEATURE ("الوضع الزجاجي ياخد اللون من الخلفية تلقائيا متل الوضع
 * التلقائي"): [AppColorMode.GLASS] used to always read its hue from
 * whichever [AppColorPalette] swatch was selected — the exact same source
 * [AppColorMode.MANUAL] uses. Real liquid glass has no color of its own,
 * though; it just reveals whatever is behind it. So on any device that can
 * actually read a color out of the wallpaper (API 31+, exactly
 * [AppColorMode.DYNAMIC]'s own requirement), GLASS's palette now comes from
 * [dynamicPaletteColors] instead of [paletteColorsFor] — the header,
 * buttons, dialogs, and every translucent surface [glassLightScheme]/
 * [glassDarkScheme] touch all shift to match the phone's actual wallpaper,
 * live, with zero manual picking. `colorPalette` stops mattering for GLASS
 * at that point (nothing is deleted — MANUAL still reads it normally, and
 * it's exactly what GLASS itself falls back to below API 31, where no
 * wallpaper-color API exists at all — see [isDynamicColorAvailable]).
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
    // DYNAMIC falls back to MANUAL's own palette behavior on API < 31,
    // where there is no wallpaper-driven color API to read from at all —
    // see [resolved]'s doc. Every branch below reads this, never the raw
    // [colorMode] parameter, so the two can never disagree.
    val effectiveColorMode = remember(colorMode) { colorMode.resolved() }
    // Whether *this* composition's palette is actually coming from the
    // wallpaper right now — true for DYNAMIC always, and for GLASS on any
    // device new enough to read one (see [dynamicPaletteColors]). Shared by
    // the palette pick below AND [LocalDynamicDarkMode] just below that, so
    // the two can never disagree about whether GLASS is currently wearing
    // wallpaper colors or hand-picked ones.
    val usingWallpaperColor = remember(effectiveColorMode) {
        effectiveColorMode == AppColorMode.DYNAMIC ||
            (effectiveColorMode == AppColorMode.GLASS && isDynamicColorAvailable())
    }

    val paletteColors = remember(colorPalette, effectiveColorMode, usingWallpaperColor, context) {
        if (effectiveColorMode == AppColorMode.GLASS && usingWallpaperColor) {
            dynamicPaletteColors(context)
        } else {
            paletteColorsFor(colorPalette)
        }
    }
    val colors = remember(effectiveColorMode, paletteColors, useDark, context) {
        when (effectiveColorMode) {
            AppColorMode.GLASS ->
                if (useDark) glassDarkScheme(paletteColors) else glassLightScheme(paletteColors)
            AppColorMode.CLASSIC ->
                if (useDark) neutralDarkScheme() else neutralLightScheme()
            AppColorMode.MANUAL ->
                if (useDark) darkSchemeFor(paletteColors) else lightSchemeFor(paletteColors)
            AppColorMode.DYNAMIC -> dynamicSchemeFor(context, useDark)
        }
    }

    // The header/status-bar gradient comes from the selected palette's own
    // gradient pair for every fixed-palette mode, or straight from the
    // resolved dynamic scheme itself for DYNAMIC (see
    // [dynamicGradientColors] — there's no palette to read a pair from in
    // that mode, the wallpaper picks the hue).
    val gradientColors = remember(effectiveColorMode, paletteColors, colors) {
        when (effectiveColorMode) {
            AppColorMode.GLASS -> glassGradientColors(paletteColors)
            AppColorMode.CLASSIC -> listOf(ClassicGradientStart, ClassicGradientEnd)
            AppColorMode.MANUAL -> listOf(paletteColors.gradientStart, paletteColors.gradientEnd)
            AppColorMode.DYNAMIC -> dynamicGradientColors(colors, useDark)
        }
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalBrandGradientColors provides gradientColors,
        // Scoped switch for the animated liquid-glass motion (drift/sheen)
        // and the extra translucency in liquidGlassSurface/GlassAlertDialog/
        // the glass buttons — on only for AppColorMode.GLASS, so MANUAL and
        // CLASSIC keep their existing calm, static *flat* panels (see
        // liquidGlassSurface's own `glassModeActive` branch) and never pick
        // up any glass translucency, matching stock Android's own solid
        // Material surfaces.
        LocalGlassMode provides (effectiveColorMode == AppColorMode.GLASS),
        // Read by GlassAlertDialog (see that local's own doc) so its brand-
        // tint blend can avoid the same pale tone-80 primary/tertiary roles
        // dynamicGradientColors already avoids for the header/nav. Keyed off
        // `usingWallpaperColor` (not a raw AppColorMode.DYNAMIC check) so
        // GLASS gets the identical fix the moment it's ALSO wearing
        // wallpaper-sourced colors — see that val's own doc just above.
        LocalDynamicDarkMode provides (usingWallpaperColor && useDark)
    ) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

