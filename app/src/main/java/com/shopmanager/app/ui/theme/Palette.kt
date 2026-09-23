package com.shopmanager.app.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

/**
 * "لوحة الألوان" (Settings → المظهر): the accent color pair used across the
 * whole app — status bar / header gradient, buttons, selection states.
 * Independent from [AppThemeMode] (light/dark/system), which only controls
 * brightness, not hue. Twenty options, each an existing light/dark-tuned
 * pair from Color.kt so every palette reads correctly in both themes
 * exactly like the original Indigo/Violet pair did.
 */
enum class AppColorPalette(val label: String) {
    CLAUDE("كلود (افتراضي)"),
    INDIGO("نيلي"),
    EMERALD("زمردي"),
    OCEAN("أزرق محيطي"),
    SUNSET("غروب"),
    BERRY("توتي"),
    CRIMSON("قرمزي"),
    GOLDEN("ذهبي"),
    LIME("ليموني"),
    SKY("سماوي"),
    COBALT("كوبالت"),
    LAVENDER("خزامى"),
    ORCHID("أوركيدي"),
    SLATE("رمادي أنيق"),
    RUBY("ياقوتي"),
    PEACH("خوخي"),
    PLUM("برقوقي"),
    STEEL("فولاذي"),
    GRAPHITE("غرافيتي"),
    AMBER("كهرماني"),
    FOREST("أخضر غابي");
}

/**
 * "لوحة الألوان" (Settings → المظهر) top-level mode. Independent from
 * [AppThemeMode] (light/dark/system), which only controls brightness:
 *
 * - [MANUAL]: one of the 20 hand-tuned [AppColorPalette] hues below,
 *   picked from the swatch grid — the app's original behavior, and the
 *   default so nobody's look changes on update.
 * - [CLASSIC]: the "إيقاف لوحة الألوان" escape hatch — no accent hue at
 *   all, just true neutral grays on white (light) / near-black (dark), for
 *   anyone who wants the plain two-tone look and nothing more.
 * - [DYNAMIC]: "تلقائي من الخلفية" — Android 12+'s real Material You
 *   wallpaper-driven color engine ([dynamicLightColorScheme]/
 *   [dynamicDarkColorScheme]), reinstated as an explicit, opt-in mode
 *   (nobody is switched to it silently) rather than the old behavior this
 *   file's [MANUAL] doc used to describe fixing, where a hand-picked
 *   palette was silently overridden by the wallpaper on API 31+. Falls
 *   back to the currently-selected [AppColorPalette] on API < 31, where
 *   Android has no dynamic-color API at all — see [resolvedColorMode].
 */
enum class AppColorMode(val label: String) {
    MANUAL("لوحة ألوان مخصصة"),
    CLASSIC("أبيض وأسود كلاسيكي"),
    DYNAMIC("تلقائي من الخلفية"),
}

/** Whether [AppColorMode.DYNAMIC] can actually run on this device — Android's
 * dynamic-color APIs ([dynamicLightColorScheme]/[dynamicDarkColorScheme])
 * only exist from API 31 (Android 12) on. Checked once by both the theme
 * (to decide what to render) and Settings (to decide what to show/save). */
fun isDynamicColorAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** [AppColorMode.DYNAMIC] with no OS support to back it falls back to
 * [AppColorMode.MANUAL] — the same hand-tuned palette behavior every
 * Android version before 12 always had — instead of silently rendering
 * nothing or crashing. Every reader of [AppColorMode] (the theme, the
 * glass-mode switch, Settings' own selection UI) goes through this so
 * they can never disagree about which mode is actually in effect. */
fun AppColorMode.resolved(): AppColorMode =
    if (this == AppColorMode.DYNAMIC && !isDynamicColorAvailable()) AppColorMode.MANUAL else this

/** The resolved colors for one palette: the two brand-gradient colors (same
 * in every theme) plus the primary/secondary tones fed into the light and
 * dark Material color schemes. */
internal data class PaletteColors(
    val gradientStart: Color,
    val gradientEnd: Color,
    val primaryLight: Color,
    val primaryContainerLight: Color,
    val secondaryLight: Color,
    val secondaryContainerLight: Color,
    val primaryDark: Color,
    val onPrimaryDark: Color,
    val primaryContainerDark: Color,
    val secondaryDark: Color,
    val onSecondaryDark: Color,
    val secondaryContainerDark: Color,
)

internal fun paletteColorsFor(palette: AppColorPalette): PaletteColors = when (palette) {
    // NOTE: CLAUDE's actual ColorScheme is no longer built from these tones
    // via [lightSchemeFor]/[darkSchemeFor]'s generic hue-derived tone()
    // engine — [ShopManagerTheme] routes CLAUDE straight to
    // [claudeLightScheme]/[claudeDarkScheme] instead, which use the
    // reference design's literal color values. This entry is kept in sync
    // with those exact values anyway, since it still feeds the palette
    // swatch preview circle in Settings (see ColorPaletteSwatch).
    AppColorPalette.CLAUDE -> PaletteColors(
        gradientStart = ClaudeOrangeLight, gradientEnd = ClaudeOrangeLight,
        primaryLight = ClaudeOrangeLight, primaryContainerLight = ClaudeM3PrimaryContainerLight,
        secondaryLight = ClaudeSecondaryLight, secondaryContainerLight = ClaudeM3SecondaryContainerLight,
        primaryDark = ClaudeOrangeDark, onPrimaryDark = Color(0xFF2B140A), primaryContainerDark = ClaudeM3PrimaryContainerDark,
        secondaryDark = ClaudeSecondaryDark, onSecondaryDark = ClaudeBgDark1, secondaryContainerDark = ClaudeM3SecondaryContainerDark,
    )
    AppColorPalette.INDIGO -> PaletteColors(
        gradientStart = BrandGradientStart, gradientEnd = BrandGradientEnd,
        primaryLight = Indigo40, primaryContainerLight = Indigo80,
        secondaryLight = Violet40, secondaryContainerLight = Violet80,
        primaryDark = Indigo80, onPrimaryDark = Color(0xFF1A1650), primaryContainerDark = Indigo40,
        secondaryDark = Violet80, onSecondaryDark = Color(0xFF2B1466), secondaryContainerDark = Violet40,
    )
    AppColorPalette.EMERALD -> PaletteColors(
        gradientStart = Emerald40, gradientEnd = Teal40,
        primaryLight = Emerald40, primaryContainerLight = Emerald80,
        secondaryLight = Teal40, secondaryContainerLight = Teal80,
        primaryDark = Emerald80, onPrimaryDark = Color(0xFF00391F), primaryContainerDark = Emerald40,
        secondaryDark = Teal80, onSecondaryDark = Color(0xFF00332C), secondaryContainerDark = Teal40,
    )
    AppColorPalette.OCEAN -> PaletteColors(
        gradientStart = Ocean40, gradientEnd = Cyan40,
        primaryLight = Ocean40, primaryContainerLight = Ocean80,
        secondaryLight = Cyan40, secondaryContainerLight = Cyan80,
        primaryDark = Ocean80, onPrimaryDark = Color(0xFF002E6B), primaryContainerDark = Ocean40,
        secondaryDark = Cyan80, onSecondaryDark = Color(0xFF00363E), secondaryContainerDark = Cyan40,
    )
    AppColorPalette.SUNSET -> PaletteColors(
        gradientStart = Sunset40, gradientEnd = Rose40,
        primaryLight = Sunset40, primaryContainerLight = Sunset80,
        secondaryLight = Rose40, secondaryContainerLight = Rose80,
        primaryDark = Sunset80, onPrimaryDark = Color(0xFF4A2200), primaryContainerDark = Sunset40,
        secondaryDark = Rose80, onSecondaryDark = Color(0xFF4C0519), secondaryContainerDark = Rose40,
    )
    AppColorPalette.BERRY -> PaletteColors(
        gradientStart = Berry40, gradientEnd = Pink40,
        primaryLight = Berry40, primaryContainerLight = Berry80,
        secondaryLight = Pink40, secondaryContainerLight = Pink80,
        primaryDark = Berry80, onPrimaryDark = Color(0xFF4A0A57), primaryContainerDark = Berry40,
        secondaryDark = Pink80, onSecondaryDark = Color(0xFF4A0424), secondaryContainerDark = Pink40,
    )
    AppColorPalette.CRIMSON -> PaletteColors(
        gradientStart = Crimson40, gradientEnd = Amber40,
        primaryLight = Crimson40, primaryContainerLight = Crimson80,
        secondaryLight = Amber40, secondaryContainerLight = Amber80,
        primaryDark = Crimson80, onPrimaryDark = CrimsonOnDark, primaryContainerDark = Crimson40,
        secondaryDark = Amber80, onSecondaryDark = AmberOnDark, secondaryContainerDark = Amber40,
    )
    AppColorPalette.GOLDEN -> PaletteColors(
        gradientStart = Gold40, gradientEnd = Lime40,
        primaryLight = Gold40, primaryContainerLight = Gold80,
        secondaryLight = Lime40, secondaryContainerLight = Lime80,
        primaryDark = Gold80, onPrimaryDark = GoldOnDark, primaryContainerDark = Gold40,
        secondaryDark = Lime80, onSecondaryDark = LimeOnDark, secondaryContainerDark = Lime40,
    )
    AppColorPalette.LIME -> PaletteColors(
        gradientStart = Lime40, gradientEnd = Forest40,
        primaryLight = Lime40, primaryContainerLight = Lime80,
        secondaryLight = Forest40, secondaryContainerLight = Forest80,
        primaryDark = Lime80, onPrimaryDark = LimeOnDark, primaryContainerDark = Lime40,
        secondaryDark = Forest80, onSecondaryDark = ForestOnDark, secondaryContainerDark = Forest40,
    )
    AppColorPalette.FOREST -> PaletteColors(
        gradientStart = Forest40, gradientEnd = Sky40,
        primaryLight = Forest40, primaryContainerLight = Forest80,
        secondaryLight = Sky40, secondaryContainerLight = Sky80,
        primaryDark = Forest80, onPrimaryDark = ForestOnDark, primaryContainerDark = Forest40,
        secondaryDark = Sky80, onSecondaryDark = SkyOnDark, secondaryContainerDark = Sky40,
    )
    AppColorPalette.SKY -> PaletteColors(
        gradientStart = Sky40, gradientEnd = Cobalt40,
        primaryLight = Sky40, primaryContainerLight = Sky80,
        secondaryLight = Cobalt40, secondaryContainerLight = Cobalt80,
        primaryDark = Sky80, onPrimaryDark = SkyOnDark, primaryContainerDark = Sky40,
        secondaryDark = Cobalt80, onSecondaryDark = CobaltOnDark, secondaryContainerDark = Cobalt40,
    )
    AppColorPalette.COBALT -> PaletteColors(
        gradientStart = Cobalt40, gradientEnd = Lavender40,
        primaryLight = Cobalt40, primaryContainerLight = Cobalt80,
        secondaryLight = Lavender40, secondaryContainerLight = Lavender80,
        primaryDark = Cobalt80, onPrimaryDark = CobaltOnDark, primaryContainerDark = Cobalt40,
        secondaryDark = Lavender80, onSecondaryDark = LavenderOnDark, secondaryContainerDark = Lavender40,
    )
    AppColorPalette.LAVENDER -> PaletteColors(
        gradientStart = Lavender40, gradientEnd = Orchid40,
        primaryLight = Lavender40, primaryContainerLight = Lavender80,
        secondaryLight = Orchid40, secondaryContainerLight = Orchid80,
        primaryDark = Lavender80, onPrimaryDark = LavenderOnDark, primaryContainerDark = Lavender40,
        secondaryDark = Orchid80, onSecondaryDark = OrchidOnDark, secondaryContainerDark = Orchid40,
    )
    AppColorPalette.ORCHID -> PaletteColors(
        gradientStart = Orchid40, gradientEnd = Plum40,
        primaryLight = Orchid40, primaryContainerLight = Orchid80,
        secondaryLight = Plum40, secondaryContainerLight = Plum80,
        primaryDark = Orchid80, onPrimaryDark = OrchidOnDark, primaryContainerDark = Orchid40,
        secondaryDark = Plum80, onSecondaryDark = PlumOnDark, secondaryContainerDark = Plum40,
    )
    AppColorPalette.SLATE -> PaletteColors(
        gradientStart = Slate40, gradientEnd = Graphite40,
        primaryLight = Slate40, primaryContainerLight = Slate80,
        secondaryLight = Graphite40, secondaryContainerLight = Graphite80,
        primaryDark = Slate80, onPrimaryDark = SlateOnDark, primaryContainerDark = Slate40,
        secondaryDark = Graphite80, onSecondaryDark = GraphiteOnDark, secondaryContainerDark = Graphite40,
    )
    AppColorPalette.RUBY -> PaletteColors(
        gradientStart = Ruby40, gradientEnd = Crimson40,
        primaryLight = Ruby40, primaryContainerLight = Ruby80,
        secondaryLight = Crimson40, secondaryContainerLight = Crimson80,
        primaryDark = Ruby80, onPrimaryDark = RubyOnDark, primaryContainerDark = Ruby40,
        secondaryDark = Crimson80, onSecondaryDark = CrimsonOnDark, secondaryContainerDark = Crimson40,
    )
    AppColorPalette.PEACH -> PaletteColors(
        gradientStart = Peach40, gradientEnd = Gold40,
        primaryLight = Peach40, primaryContainerLight = Peach80,
        secondaryLight = Gold40, secondaryContainerLight = Gold80,
        primaryDark = Peach80, onPrimaryDark = PeachOnDark, primaryContainerDark = Peach40,
        secondaryDark = Gold80, onSecondaryDark = GoldOnDark, secondaryContainerDark = Gold40,
    )
    AppColorPalette.PLUM -> PaletteColors(
        gradientStart = Plum40, gradientEnd = Orchid40,
        primaryLight = Plum40, primaryContainerLight = Plum80,
        secondaryLight = Orchid40, secondaryContainerLight = Orchid80,
        primaryDark = Plum80, onPrimaryDark = PlumOnDark, primaryContainerDark = Plum40,
        secondaryDark = Orchid80, onSecondaryDark = OrchidOnDark, secondaryContainerDark = Orchid40,
    )
    AppColorPalette.STEEL -> PaletteColors(
        gradientStart = Steel40, gradientEnd = Sky40,
        primaryLight = Steel40, primaryContainerLight = Steel80,
        secondaryLight = Sky40, secondaryContainerLight = Sky80,
        primaryDark = Steel80, onPrimaryDark = SteelOnDark, primaryContainerDark = Steel40,
        secondaryDark = Sky80, onSecondaryDark = SkyOnDark, secondaryContainerDark = Sky40,
    )
    AppColorPalette.GRAPHITE -> PaletteColors(
        gradientStart = Graphite40, gradientEnd = Slate40,
        primaryLight = Graphite40, primaryContainerLight = Graphite80,
        secondaryLight = Slate40, secondaryContainerLight = Slate80,
        primaryDark = Graphite80, onPrimaryDark = GraphiteOnDark, primaryContainerDark = Graphite40,
        secondaryDark = Slate80, onSecondaryDark = SlateOnDark, secondaryContainerDark = Slate40,
    )
    AppColorPalette.AMBER -> PaletteColors(
        gradientStart = Amber40, gradientEnd = Gold40,
        primaryLight = Amber40, primaryContainerLight = Amber80,
        secondaryLight = Gold40, secondaryContainerLight = Gold80,
        primaryDark = Amber80, onPrimaryDark = AmberOnDark, primaryContainerDark = Amber40,
        secondaryDark = Gold80, onSecondaryDark = GoldOnDark, secondaryContainerDark = Gold40,
    )
}

/**
 * "لوحة الألوان" quality fix: [lightColorScheme]/[darkColorScheme] only had
 * primary/secondary/background/surface/surfaceVariant/error overridden per
 * palette — every other token Compose Material3 uses (surfaceTint, outline,
 * outlineVariant, tertiary, inverseSurface, and the whole
 * surfaceContainer/Low/High family that Card, Menu, Dialog, and
 * BottomSheet default backgrounds pull from) silently fell back to
 * [lightColorScheme]/[darkColorScheme]'s hardcoded *baseline Material
 * purple* defaults. That meant every card, dialog, and outline in the app
 * carried a faint baseline-purple tint no matter which of the 20 palettes
 * (Emerald, Crimson, Forest, ...) was actually selected — the palette
 * picker changed the header/buttons but the app's own neutral surfaces
 * quietly stayed purple-tinted underneath. Every token below is now
 * derived from that palette's own primary color instead, so choosing e.g.
 * Emerald gives an app that's tinted green throughout, not green buttons
 * on a purple-neutral shell.
 *
 * TONE ENGINE — HSV instead of a direct RGB mix: the old approach here
 * blended (lerp'd) each palette's raw RGB straight toward white/black,
 * which doesn't hold brightness steady — depending on the palette's own
 * hue, two "same amount" blends could land at visibly different perceived
 * brightness, occasionally leaving a surface and the text sitting on it
 * too close in lightness to read comfortably. [tone] instead converts the
 * palette's hue to HSV once and fixes saturation (S) and brightness (V)
 * *explicitly* for every single surface role, so:
 *
 * - the brightness gap between a surface and whatever sits on it is
 *   large and identical across all 20 palettes and both light/dark —
 *   never something that quietly varies by hue;
 * - the "elevated" surfaces (surfaceContainerHigh/Highest — raised cards,
 *   the glass bars) are given a deliberately *higher* saturation than
 *   resting surfaces so they read as lifted into the light and tinted
 *   with the palette's identity, not just a flat gray a shade lighter;
 * - turning saturation to zero for every role (see [neutralLightScheme]/
 *   [neutralDarkScheme]) is the entire "بدون تلوين" mode — the same
 *   brightness ladder, just hueless.
 */
private fun tone(hue: Float, saturation: Float, value: Float, alpha: Float = 1f): Color {
    val hsv = floatArrayOf(hue, saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f))
    return Color(AndroidColor.HSVToColor((alpha.coerceIn(0f, 1f) * 255f).roundToInt(), hsv))
}

/** Extracts the base hue (0–360°) driving every tonal surface for a given
 * palette, straight from that palette's own hand-tuned primary color — so
 * the neutral shell always tracks whichever accent is actually selected
 * without a second, separately-maintained hue table. */
private fun hueOf(color: Color): Float {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(color.toArgb(), hsv)
    return hsv[0]
}

/** The two header/gradient colors used in [AppColorMode.CLASSIC] — true
 * neutral grays instead of any hue, kept the same in light and dark for
 * the same reason [BrandGradientStart]/[BrandGradientEnd] are. */
val ClassicGradientStart = Color(0xFF5B5B5F)
val ClassicGradientEnd = Color(0xFF2D2D30)

/**
 * [AppColorMode.CLASSIC]'s color scheme: real grayscale, not Compose
 * Material3's own `lightColorScheme()`/`darkColorScheme()` defaults (those
 * bake in a baseline purple tint on primary/tertiary/surfaceTint — using
 * them as-is would silently reintroduce a hue after "إيقاف لوحة الألوان"
 * was supposed to remove it entirely). Built the same way [lightSchemeFor]/
 * [darkSchemeFor] build every other palette, just tinted toward gray
 * instead of toward a color.
 */
// Saturation forced to zero everywhere below — the hue argument to [tone]
// is therefore irrelevant and left at 0f. This *is* the README's "NONE"
// palette: identical brightness ladder to every colored scheme, just
// hueless, true neutral gray.
internal fun neutralLightScheme(): ColorScheme = lightColorScheme(
    primary = Color(0xFF3A3A3D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E1E5),
    onPrimaryContainer = Color(0xFF3A3A3D),
    secondary = Color(0xFF5C5C60),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7E6EA),
    onSecondaryContainer = Color(0xFF5C5C60),
    tertiary = Color(0xFF5C5C60),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE7E6EA),
    background = tone(0f, 0f, 0.985f),
    onBackground = Color(0xFF1C1B1F),
    surface = tone(0f, 0f, 0.995f),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = tone(0f, 0f, 0.95f),
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = Color(0xFF3A3A3D),
    outline = tone(0f, 0f, 0.55f),
    outlineVariant = tone(0f, 0f, 0.82f),
    inverseSurface = Color(0xFF2F2D33),
    inverseOnSurface = Color(0xFFF4EFF4),
    inversePrimary = Color(0xFFE2E1E5),
    surfaceContainerLowest = tone(0f, 0f, 1f),
    surfaceContainerLow = tone(0f, 0f, 0.98f),
    surfaceContainer = tone(0f, 0f, 0.965f),
    surfaceContainerHigh = tone(0f, 0f, 0.95f),
    surfaceContainerHighest = tone(0f, 0f, 0.935f),
    error = DangerRed,
)

internal fun neutralDarkScheme(): ColorScheme = darkColorScheme(
    primary = Color(0xFFE2E1E5),
    onPrimary = Color(0xFF1C1B1F),
    primaryContainer = Color(0xFF454549),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFC7C6CA),
    onSecondary = Color(0xFF1C1B1F),
    secondaryContainer = Color(0xFF454549),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFFC7C6CA),
    onTertiary = Color(0xFF1C1B1F),
    tertiaryContainer = Color(0xFF454549),
    background = tone(0f, 0f, 0.09f),
    onBackground = Color(0xFFE7E2EA),
    surface = tone(0f, 0f, 0.12f),
    onSurface = Color(0xFFE7E2EA),
    surfaceVariant = tone(0f, 0f, 0.19f),
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = Color(0xFFE2E1E5),
    outline = tone(0f, 0f, 0.62f),
    outlineVariant = tone(0f, 0f, 0.32f),
    inverseSurface = Color(0xFFE7E2EA),
    inverseOnSurface = Color(0xFF2F2D33),
    inversePrimary = Color(0xFF454549),
    surfaceContainerLowest = tone(0f, 0f, 0.06f),
    surfaceContainerLow = tone(0f, 0f, 0.145f),
    surfaceContainer = tone(0f, 0f, 0.165f),
    surfaceContainerHigh = tone(0f, 0f, 0.20f),
    surfaceContainerHighest = tone(0f, 0f, 0.235f),
    error = Color(0xFFFF6B6B),
)

/**
 * [AppColorPalette.CLAUDE]'s exact ColorScheme, built directly from the
 * reference Claude.ai design's literal color tokens (see the `Claude*`
 * constants in Color.kt, copied 1:1 from that design's
 * values/colors.xml + values-night/colors.xml) instead of
 * [lightSchemeFor]'s generic hue-derived [tone] approximation that every
 * other palette still uses. Every shared component that paints itself from
 * `MaterialTheme.colorScheme` — [com.shopmanager.app.ui.common.GlassCard],
 * [com.shopmanager.app.ui.common.GlassAlertDialog], settings rows, headers,
 * pill buttons — therefore matches the reference design exactly wherever
 * CLAUDE (the app's default palette) is selected, since `surfaceContainerHigh`
 * here is the reference's literal `glass_fill_strong` (the flat white/near-
 * black fill its bg_glass_card / bg_settings_group / bg_dialog_card
 * drawables all use), not a computed tint.
 */
internal fun claudeLightScheme(): ColorScheme = lightColorScheme(
    primary = ClaudeOrangeLight,
    onPrimary = Color.White,
    primaryContainer = ClaudeM3PrimaryContainerLight,
    onPrimaryContainer = ClaudeM3OnPrimaryContainerLight,
    secondary = ClaudeSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = ClaudeM3SecondaryContainerLight,
    onSecondaryContainer = ClaudeM3OnSecondaryContainerLight,
    tertiary = ClaudeAccentGoldLight,
    onTertiary = Color.White,
    tertiaryContainer = ClaudeM3TertiaryContainerLight,
    onTertiaryContainer = ClaudeM3OnTertiaryContainerLight,
    error = ClaudeAccentRedLight,
    onError = Color.White,
    errorContainer = ClaudeM3ErrorContainerLight,
    onErrorContainer = ClaudeM3OnErrorContainerLight,
    background = ClaudeBgLight1,
    onBackground = ClaudeTextPrimaryLight,
    surface = ClaudeBgLight1,
    onSurface = ClaudeTextPrimaryLight,
    surfaceVariant = ClaudeM3SurfaceVariantLight,
    onSurfaceVariant = ClaudeTextSecondaryLight,
    surfaceTint = ClaudeOrangeLight,
    outline = ClaudeTextTertiaryLight,
    outlineVariant = ClaudeBorderLight,
    inverseSurface = ClaudeCardDark,
    inverseOnSurface = ClaudeTextPrimaryDark,
    inversePrimary = ClaudePrimaryDarkVariantOnDark,
    // The exact "glass" ladder: cards/dialogs/settings-groups/headers all
    // read surfaceContainerHigh, which is the reference's literal
    // glass_fill_strong — pure white, with a hairline glass_border drawn
    // separately by each component's own .border() call.
    surfaceContainerLowest = ClaudeCardSoftLight,
    surfaceContainerLow = ClaudeBgLight2,
    surfaceContainer = ClaudeM3SurfaceContainerLight,
    surfaceContainerHigh = ClaudeCardLight,
    surfaceContainerHighest = ClaudeM3SurfaceContainerHighLight,
)

internal fun claudeDarkScheme(): ColorScheme = darkColorScheme(
    primary = ClaudeOrangeDark,
    onPrimary = Color(0xFF2B140A),
    primaryContainer = ClaudeM3PrimaryContainerDark,
    onPrimaryContainer = ClaudeM3OnPrimaryContainerDark,
    secondary = ClaudeSecondaryDark,
    onSecondary = ClaudeBgDark1,
    secondaryContainer = ClaudeM3SecondaryContainerDark,
    onSecondaryContainer = ClaudeM3OnSecondaryContainerDark,
    tertiary = ClaudeAccentGoldDark,
    onTertiary = Color(0xFF241A0D),
    tertiaryContainer = ClaudeM3TertiaryContainerDark,
    onTertiaryContainer = ClaudeM3OnTertiaryContainerDark,
    error = ClaudeAccentRedDark,
    onError = Color(0xFF35110A),
    errorContainer = ClaudeM3ErrorContainerDark,
    onErrorContainer = ClaudeM3OnErrorContainerDark,
    background = ClaudeBgDark1,
    onBackground = ClaudeTextPrimaryDark,
    surface = ClaudeBgDark1,
    onSurface = ClaudeTextPrimaryDark,
    surfaceVariant = ClaudeM3SurfaceVariantDark,
    onSurfaceVariant = ClaudeTextSecondaryDark,
    surfaceTint = ClaudeOrangeDark,
    outline = ClaudeTextTertiaryDark,
    outlineVariant = ClaudeBorderDark,
    inverseSurface = ClaudeCardLight,
    inverseOnSurface = ClaudeTextPrimaryLight,
    inversePrimary = ClaudePrimaryDarkVariant,
    surfaceContainerLowest = ClaudeBgDark1,
    surfaceContainerLow = ClaudeBgDark2,
    surfaceContainer = ClaudeM3SurfaceContainerDark,
    surfaceContainerHigh = ClaudeCardDark,
    surfaceContainerHighest = ClaudeM3SurfaceContainerHighDark,
)

internal fun lightSchemeFor(p: PaletteColors): ColorScheme {
    val hue = hueOf(p.primaryLight)
    return lightColorScheme(
    primary = p.primaryLight,
    onPrimary = Color.White,
    primaryContainer = p.primaryContainerLight,
    onPrimaryContainer = p.primaryLight,
    secondary = p.secondaryLight,
    onSecondary = Color.White,
    secondaryContainer = p.secondaryContainerLight,
    onSecondaryContainer = p.secondaryLight,
    tertiary = p.secondaryLight,
    onTertiary = Color.White,
    tertiaryContainer = p.secondaryContainerLight,
    // COLOR PRECISION FIX (see darkSchemeFor's matching note) — same
    // correction, light side: real Claude's light background is a quiet,
    // barely-warm off-white, not a noticeably tinted cream. Saturation cut
    // to roughly a third across the board; `background`/`surface` stay
    // close to the same near-white value since that part was already
    // reasonably close, but with far less chroma behind it.
    background = tone(hue, 0.018f, 0.99f),
    onBackground = Color(0xFF1C1B1F),
    surface = tone(hue, 0.008f, 0.997f),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = tone(hue, 0.035f, 0.965f),
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = p.primaryLight,
    outline = tone(hue, 0.08f, 0.55f),
    outlineVariant = tone(hue, 0.05f, 0.85f),
    inverseSurface = Color(0xFF2F2D33),
    inverseOnSurface = Color(0xFFF4EFF4),
    inversePrimary = p.primaryContainerLight,
    // Same corrected, much-lower saturation ladder as the dark scheme —
    // elevated surfaces stay close to neutral, not visibly richer/more
    // colored at each step up.
    surfaceContainerLowest = tone(hue, 0.005f, 1f),
    surfaceContainerLow = tone(hue, 0.02f, 0.985f),
    surfaceContainer = tone(hue, 0.03f, 0.975f),
    surfaceContainerHigh = tone(hue, 0.045f, 0.96f),
    surfaceContainerHighest = tone(hue, 0.06f, 0.945f),
    error = DangerRed,
    )
}

internal fun darkSchemeFor(p: PaletteColors): ColorScheme {
    val hue = hueOf(p.primaryDark)
    return darkColorScheme(
    primary = p.primaryDark,
    onPrimary = p.onPrimaryDark,
    primaryContainer = p.primaryContainerDark,
    onPrimaryContainer = Color.White,
    secondary = p.secondaryDark,
    onSecondary = p.onSecondaryDark,
    secondaryContainer = p.secondaryContainerDark,
    onSecondaryContainer = Color.White,
    tertiary = p.secondaryDark,
    onTertiary = p.onSecondaryDark,
    tertiaryContainer = p.secondaryContainerDark,
    // COLOR PRECISION FIX ("الوان التطبيق مانها بتشبة كلود ابدأ" — with
    // real Claude-app screenshots as the reference this time): the real
    // Claude app's dark background is essentially true black with only a
    // whisper of warmth — nowhere near as saturated/orange as this used to
    // be. A previous pass ("الألوان ينقصها شيء لتبدو زاهية وفخمة") pushed
    // saturation UP for a richer look; the actual reference screenshots
    // show the opposite is true, so this corrects course: saturation cut
    // to roughly a third of what it was, and `background` itself darkened
    // toward true near-black (V 0.10 → 0.055) instead of a lighter warm
    // charcoal. The palette's own primary/secondary (the coral accent
    // itself, used for the logo mark, avatar, and buttons) are untouched —
    // only the neutral shell around it changes.
    background = tone(hue, 0.05f, 0.055f),
    onBackground = Color(0xFFE7E2EA),
    surface = tone(hue, 0.045f, 0.075f),
    onSurface = Color(0xFFE7E2EA),
    surfaceVariant = tone(hue, 0.055f, 0.135f),
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = p.primaryDark,
    outline = tone(hue, 0.09f, 0.55f),
    outlineVariant = tone(hue, 0.06f, 0.28f),
    inverseSurface = Color(0xFFE7E2EA),
    inverseOnSurface = Color(0xFF2F2D33),
    inversePrimary = p.primaryContainerDark,
    // Same corrected, much-lower saturation ladder — real Claude's raised
    // cards/dialogs/sheets stay close to neutral gray as they lift off the
    // near-black background, not a noticeably warmer/richer tint at each
    // step.
    surfaceContainerLowest = tone(hue, 0.045f, 0.045f),
    surfaceContainerLow = tone(hue, 0.05f, 0.095f),
    surfaceContainer = tone(hue, 0.05f, 0.115f),
    surfaceContainerHigh = tone(hue, 0.055f, 0.15f),
    surfaceContainerHighest = tone(hue, 0.06f, 0.185f),
    error = Color(0xFFFF6B6B),
    )
}

/**
 * [AppColorMode.DYNAMIC]'s color scheme: Android's own wallpaper-derived
 * Material You palette, completely untouched — no hue/tone overrides on
 * top of it like every other mode above, since the entire point of this
 * mode is that the colors come from the person's actual wallpaper rather
 * than any hand-tuned table in this file. Callers must guard with
 * [isDynamicColorAvailable] first; this throws on API < 31 exactly like
 * the underlying platform call does.
 */
internal fun dynamicSchemeFor(context: Context, useDark: Boolean): ColorScheme =
    if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

/** The header/gradient colors for [AppColorMode.DYNAMIC] — derived from
 * the resolved dynamic [ColorScheme] itself instead of a fixed
 * [PaletteColors] pair, since there's no selected palette to read a
 * gradient from in this mode: the wallpaper picks the hue, so the header
 * gradient has to follow whatever that scheme actually contains.
 *
 * FLAT, NOT A GRADIENT ("مو تدرج فاتح وغامق بالوضع التلقائي، بدي لون واحد
 * خلص — لون الخلفية بس"): returns the *same* single color twice instead
 * of a `primaryContainer`→`tertiaryContainer`/`primary`→`tertiary` pair.
 * [BrandGradient.brush]/[BrandGradient.horizontalBrush] still build a
 * `Brush.verticalGradient`/`horizontalGradient` from this list either way,
 * but two identical stops paint as one flat color with no light/dark
 * blend — exactly the plain "one color, that's it" look asked for here.
 * MANUAL/CLASSIC's own [ShopManagerTheme] `gradientColors` now do the same
 * (repeat their single `gradientStart`/`ClassicGradientStart` tone) instead
 * of keeping their real two-hue pair, so all three modes read as one flat
 * header color, not just this one.
 *
 * TONE ("فاتح لدرجة تزعج" — a bright, washed-out pastel flash on an
 * otherwise near-black app): per Material 3's tonal-palette spec, a
 * *dark* scheme's `primary` is deliberately tone-80 — pale, high-chroma —
 * the right tone for *text/icon* color on a dark surface, not a filled
 * banner. `primaryContainer` sits at tone-30 for a dynamic dark scheme
 * instead: the same "rich, medium-dark, still reads the wallpaper's hue"
 * register [BrandGradientStart]/[BrandGradientEnd]'s own pair already
 * uses for every hand-tuned palette. Light mode keeps plain `primary` —
 * already the correctly-rich tone-40 there, exactly like every other
 * mode. */
internal fun dynamicGradientColors(scheme: ColorScheme, useDark: Boolean): List<Color> {
    val color = if (useDark) scheme.primaryContainer else scheme.primary
    return listOf(color, color)
}

/** Carries the selected palette's brand-gradient colors down to
 * [com.shopmanager.app.ui.common.BrandGradient] without threading a
 * parameter through every screen that already calls
 * `BrandGradient.brush()`. Provided once near the root (see MainActivity),
 * defaults to the Indigo gradient so previews and anything outside the
 * provider still render correctly. */
val LocalBrandGradientColors = staticCompositionLocalOf { listOf(BrandGradientStart, BrandGradientEnd) }

/** True only when the palette actually in effect right now is coming
 * straight from the phone's wallpaper *and* the theme is currently dark —
 * [AppColorMode.DYNAMIC] only (see [ShopManagerTheme]'s
 * `usingWallpaperColor`). Read by
 * [com.shopmanager.app.ui.common.GlassAlertDialog] to decide which color
 * role to blend its fill toward — see the "TONE" note on
 * [dynamicGradientColors] for why a dynamic *dark* scheme's
 * `primary`/`tertiary` (pale tone-80, meant for text/icons on dark, not a
 * large tinted panel) are the wrong pick there too. GlassAlertDialog swaps
 * to `primaryContainer`/`tertiaryContainer` (tone-30, rich) only when this
 * is true; a hand-tuned palette (MANUAL/CLASSIC) keeps blending toward its
 * own already-tuned `primary`/`tertiary` exactly as before. */
val LocalDynamicDarkMode = staticCompositionLocalOf { false }

/**
 * Status-icon/badge colors (success ✓, warning ⚠, danger ✕, info) used
 * across DeleteIconButton, SwipeToDeleteRow, MaterialsScreen, NotesScreen,
 * PersonDetailScreen and SettingsScreen. These used to be flat, theme-blind
 * constants (SuccessGreen/WarningAmber/DangerRed/InfoBlue in Color.kt) —
 * fine for the 20 generic hue palettes, but wrong for CLAUDE specifically:
 * a request to make the app match the reference Claude.ai design "fully"
 * means these status colors too, not just backgrounds/cards/text. Under
 * CLAUDE this now resolves to the reference design's own light/dark
 * accent_green/accent_gold/accent_red tokens (accent_red mirrors
 * colorScheme.error exactly, since [claudeLightScheme]/[claudeDarkScheme]
 * already set `error` from the same token); "info" has no blue in the
 * reference design at all, so it falls back to the same warm neutral
 * secondary accent (accent_violet) the design uses for secondary chips
 * instead of introducing an off-palette blue. Every other palette keeps
 * the original flat constants unchanged.
 */
data class SemanticColors(
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
)

internal fun semanticColorsFor(palette: AppColorPalette, useDark: Boolean): SemanticColors =
    if (palette == AppColorPalette.CLAUDE) {
        if (useDark) SemanticColors(
            success = ClaudeAccentGreenDark,
            warning = ClaudeAccentGoldDark,
            danger = ClaudeAccentRedDark,
            info = ClaudeSecondaryDark,
        ) else SemanticColors(
            success = ClaudeAccentGreenLight,
            warning = ClaudeAccentGoldLight,
            danger = ClaudeAccentRedLight,
            info = ClaudeSecondaryLight,
        )
    } else {
        SemanticColors(success = SuccessGreen, warning = WarningAmber, danger = DangerRed, info = InfoBlue)
    }

/** Provided once near the root (see [ShopManagerTheme]); defaults to the
 * original flat constants so anything outside the provider (previews)
 * still renders correctly. */
val LocalSemanticColors = staticCompositionLocalOf {
    SemanticColors(success = SuccessGreen, warning = WarningAmber, danger = DangerRed, info = InfoBlue)
}
