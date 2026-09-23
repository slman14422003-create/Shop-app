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
@Composable
fun ShopManagerTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    colorMode: AppColorMode = AppColorMode.MANUAL,
    colorPalette: AppColorPalette = AppColorPalette.CLAUDE,
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
    // wallpaper right now — true only for DYNAMIC. Shared by the palette
    // pick below AND [LocalDynamicDarkMode] just below that, so the two can
    // never disagree.
    val usingWallpaperColor = remember(effectiveColorMode) {
        effectiveColorMode == AppColorMode.DYNAMIC
    }

    val paletteColors = remember(colorPalette, effectiveColorMode, context) {
        paletteColorsFor(colorPalette)
    }
    // FIXED ("صلح الألوان بالوضع الليلي والنهاري ونسق الـUI بشكل كلود"): a
    // previous pass forced every theme to the true grayscale CLASSIC scheme
    // regardless of [colorMode]/[colorPalette] — so the palette picker in
    // Settings, the CLAUDE default, and DYNAMIC wallpaper color all silently
    // did nothing, in both light and dark. Restored to actually honor
    // [effectiveColorMode]: MANUAL paints the selected [AppColorPalette]
    // (CLAUDE by default — see SettingsRepository.colorPalette — which is
    // what gives the app Claude's warm terracotta/cream identity end to end,
    // see the Claude80/Claude40 doc in Color.kt), CLASSIC keeps the
    // intentional hueless black/white escape hatch, and DYNAMIC reads the
    // wallpaper exactly as [effectiveColorMode]/[usingWallpaperColor] above
    // were already computed to support.
    val colors = remember(useDark, effectiveColorMode, colorPalette, paletteColors, context) {
        when (effectiveColorMode) {
            AppColorMode.CLASSIC -> if (useDark) neutralDarkScheme() else neutralLightScheme()
            AppColorMode.DYNAMIC -> dynamicSchemeFor(context, useDark)
            // CLAUDE (the app's default) uses the reference Claude.ai design's
            // exact color tokens instead of the generic hue-derived tone()
            // approximation every other hand-tuned palette still uses — see
            // claudeLightScheme()/claudeDarkScheme() in Palette.kt.
            AppColorMode.MANUAL -> if (colorPalette == AppColorPalette.CLAUDE) {
                if (useDark) claudeDarkScheme() else claudeLightScheme()
            } else {
                if (useDark) darkSchemeFor(paletteColors) else lightSchemeFor(paletteColors)
            }
        }
    }

    // "مش شبه هيدر كلود، متل بلوك لون مختلف عن الخلفية": headers used to be
    // tinted from a fixed mid-gray brand constant, noticeably lighter than
    // the near-black dark background (or just a same-toned floating gray
    // bar in light mode) — reading as a separate colored block instead of
    // part of the page, unlike Claude's own flush, headerless top bars.
    // Now every header pulls the exact same [surfaceContainerHigh] tone
    // every ordinary card already uses, so it blends into the page as a
    // subtle "slightly raised" surface instead of standing out.
    val gradientColors = remember(colors) {
        listOf(colors.surfaceContainerHigh, colors.surfaceContainerHigh)
    }
    val semanticColors = remember(colorPalette, useDark) { semanticColorsFor(colorPalette, useDark) }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalBrandGradientColors provides gradientColors,
        // Read by GlassAlertDialog so its brand-tint blend can avoid the
        // same pale tone-80 primary/tertiary roles dynamicGradientColors
        // already avoids for the header/nav.
        LocalDynamicDarkMode provides (usingWallpaperColor && useDark),
        // Status-icon colors (success/warning/danger/info) — see
        // [SemanticColors]'s doc: exact reference accents under CLAUDE,
        // unchanged flat constants for every other palette.
        LocalSemanticColors provides semanticColors
    ) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

