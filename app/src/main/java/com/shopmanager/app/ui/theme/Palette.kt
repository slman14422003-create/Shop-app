package com.shopmanager.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * "لوحة الألوان" (Settings → المظهر): the accent color pair used across the
 * whole app — status bar / header gradient, buttons, selection states.
 * Independent from [AppThemeMode] (light/dark/system), which only controls
 * brightness, not hue. Twenty options, each an existing light/dark-tuned
 * pair from Color.kt so every palette reads correctly in both themes
 * exactly like the original Indigo/Violet pair did.
 */
enum class AppColorPalette(val label: String) {
    INDIGO("نيلي (افتراضي)"),
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
 * - [GLASS]: "الجلاس الشفاف الكامل" — replaces the old wallpaper-driven
 *   dynamic-color mode entirely. Uses the same selected [AppColorPalette]
 *   hue pair as [MANUAL], but every surface a Card/Dialog/Sheet/Menu paints
 *   from is genuinely translucent (see [glassLightScheme]/[glassDarkScheme])
 *   instead of a solid fill, and [LocalGlassMode] switches on
 *   [com.shopmanager.app.ui.common.liquidGlassSurface]'s animated drift/
 *   sheen motion for the header, floating bottom nav, and every dialog —
 *   so the whole app, not just the header, reads as one continuous sheet
 *   of moving liquid glass. That extra motion is scoped to this mode only;
 *   [MANUAL]/[CLASSIC] keep their existing calm, static glass panels.
 * - [CLASSIC]: the "إيقاف لوحة الألوان" escape hatch — no accent hue at
 *   all, just true neutral grays on white (light) / near-black (dark), for
 *   anyone who wants the plain two-tone look and nothing more.
 */
enum class AppColorMode(val label: String) {
    GLASS("زجاج شفاف كامل"),
    MANUAL("لوحة ألوان مخصصة"),
    CLASSIC("أبيض وأسود كلاسيكي"),
}

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
 */
private fun blend(base: Color, tint: Color, amount: Float): Color = Color(
    red = base.red + (tint.red - base.red) * amount,
    green = base.green + (tint.green - base.green) * amount,
    blue = base.blue + (tint.blue - base.blue) * amount,
    alpha = 1f
)

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
private val ClassicGray = Color(0xFF49454E)

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
    background = LightBackground,
    onBackground = Color(0xFF1C1B1F),
    surface = LightSurface,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = Color(0xFF3A3A3D),
    outline = blend(LightOnSurfaceVariant, ClassicGray, 0.18f),
    outlineVariant = blend(LightSurfaceVariant, ClassicGray, 0.10f),
    inverseSurface = Color(0xFF2F2D33),
    inverseOnSurface = Color(0xFFF4EFF4),
    inversePrimary = Color(0xFFE2E1E5),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = blend(LightSurface, ClassicGray, 0.02f),
    surfaceContainer = blend(LightSurface, ClassicGray, 0.05f),
    surfaceContainerHigh = blend(LightSurface, ClassicGray, 0.08f),
    surfaceContainerHighest = blend(LightSurface, ClassicGray, 0.11f),
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
    background = DarkBackground,
    onBackground = Color(0xFFE7E2EA),
    surface = DarkSurface,
    onSurface = Color(0xFFE7E2EA),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = Color(0xFFE2E1E5),
    outline = blend(DarkOnSurfaceVariant, ClassicGray, 0.22f),
    outlineVariant = blend(DarkSurfaceVariant, ClassicGray, 0.14f),
    inverseSurface = Color(0xFFE7E2EA),
    inverseOnSurface = Color(0xFF2F2D33),
    inversePrimary = Color(0xFF454549),
    surfaceContainerLowest = blend(DarkBackground, Color.Black, 0.35f),
    surfaceContainerLow = blend(DarkSurface, ClassicGray, 0.04f),
    surfaceContainer = blend(DarkSurface, ClassicGray, 0.07f),
    surfaceContainerHigh = blend(DarkSurface, ClassicGray, 0.11f),
    surfaceContainerHighest = blend(DarkSurface, ClassicGray, 0.15f),
    error = Color(0xFFFF6B6B),
)

internal fun lightSchemeFor(p: PaletteColors): ColorScheme = lightColorScheme(
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
    background = LightBackground,
    onBackground = Color(0xFF1C1B1F),
    surface = LightSurface,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = p.primaryLight,
    outline = blend(LightOnSurfaceVariant, p.primaryLight, 0.18f),
    outlineVariant = blend(LightSurfaceVariant, p.primaryLight, 0.10f),
    inverseSurface = Color(0xFF2F2D33),
    inverseOnSurface = Color(0xFFF4EFF4),
    inversePrimary = p.primaryContainerLight,
    // BUG FIXED ("الألوان ينقصها شيء لتبدو زاهية وفخمة"): these blends used
    // to be very faint (2–11%), so every card/dialog/sheet in the app read
    // as close to flat neutral gray no matter which vivid palette was
    // selected — the accent hue only really showed up on buttons/headers,
    // never on the surfaces behind them. Raised so cards/sheets carry a
    // visibly richer tint of the selected color, closer to how iOS's own
    // "material" surfaces pick up a tint from whatever's behind/around them
    // instead of sitting as plain gray boxes.
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = blend(LightSurface, p.primaryLight, 0.04f),
    surfaceContainer = blend(LightSurface, p.primaryLight, 0.08f),
    surfaceContainerHigh = blend(LightSurface, p.primaryLight, 0.12f),
    surfaceContainerHighest = blend(LightSurface, p.primaryLight, 0.16f),
    error = DangerRed,
)

internal fun darkSchemeFor(p: PaletteColors): ColorScheme = darkColorScheme(
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
    background = DarkBackground,
    onBackground = Color(0xFFE7E2EA),
    surface = DarkSurface,
    onSurface = Color(0xFFE7E2EA),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = p.primaryDark,
    outline = blend(DarkOnSurfaceVariant, p.primaryDark, 0.22f),
    outlineVariant = blend(DarkSurfaceVariant, p.primaryDark, 0.14f),
    inverseSurface = Color(0xFFE7E2EA),
    inverseOnSurface = Color(0xFF2F2D33),
    inversePrimary = p.primaryContainerDark,
    surfaceContainerLowest = blend(DarkBackground, Color.Black, 0.35f),
    surfaceContainerLow = blend(DarkSurface, p.primaryDark, 0.07f),
    surfaceContainer = blend(DarkSurface, p.primaryDark, 0.11f),
    surfaceContainerHigh = blend(DarkSurface, p.primaryDark, 0.16f),
    surfaceContainerHighest = blend(DarkSurface, p.primaryDark, 0.22f),
    error = Color(0xFFFF6B6B),
)

/**
 * [AppColorMode.GLASS]'s color scheme: the same 20 hand-tuned palette hues
 * as [lightSchemeFor]/[darkSchemeFor], but every token a Card/Dialog/Sheet/
 * Menu/Button reads its background from carries real alpha < 1 instead of
 * a solid fill. Because Compose's `Modifier.background(Color)` respects a
 * Color's own alpha channel, this alone is what turns every *ordinary*
 * Material3 surface in the app — not just the hand-built
 * [com.shopmanager.app.ui.common.liquidGlassSurface] panels — into frosted
 * glass that lets whatever's behind it show through, without every screen
 * needing its own bespoke glass code. `primary`/`onPrimary` are kept
 * opaque so button/header text stays legible; only the *container* tones
 * (what a translucent panel's fill actually is) go see-through.
 */
internal fun glassLightScheme(p: PaletteColors): ColorScheme = lightColorScheme(
    primary = p.primaryLight,
    onPrimary = Color.White,
    primaryContainer = p.primaryContainerLight.copy(alpha = 0.55f),
    onPrimaryContainer = p.primaryLight,
    secondary = p.secondaryLight,
    onSecondary = Color.White,
    secondaryContainer = p.secondaryContainerLight.copy(alpha = 0.55f),
    onSecondaryContainer = p.secondaryLight,
    tertiary = p.secondaryLight,
    onTertiary = Color.White,
    tertiaryContainer = p.secondaryContainerLight.copy(alpha = 0.55f),
    background = LightBackground,
    onBackground = Color(0xFF1C1B1F),
    surface = LightSurface.copy(alpha = 0.55f),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = LightSurfaceVariant.copy(alpha = 0.50f),
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = p.primaryLight,
    outline = blend(LightOnSurfaceVariant, p.primaryLight, 0.18f).copy(alpha = 0.55f),
    outlineVariant = blend(LightSurfaceVariant, p.primaryLight, 0.10f).copy(alpha = 0.45f),
    inverseSurface = Color(0xFF2F2D33),
    inverseOnSurface = Color(0xFFF4EFF4),
    inversePrimary = p.primaryContainerLight,
    surfaceContainerLowest = Color.White.copy(alpha = 0.35f),
    surfaceContainerLow = blend(LightSurface, p.primaryLight, 0.04f).copy(alpha = 0.42f),
    surfaceContainer = blend(LightSurface, p.primaryLight, 0.08f).copy(alpha = 0.50f),
    surfaceContainerHigh = blend(LightSurface, p.primaryLight, 0.12f).copy(alpha = 0.58f),
    surfaceContainerHighest = blend(LightSurface, p.primaryLight, 0.16f).copy(alpha = 0.66f),
    error = DangerRed,
)

internal fun glassDarkScheme(p: PaletteColors): ColorScheme = darkColorScheme(
    primary = p.primaryDark,
    onPrimary = p.onPrimaryDark,
    primaryContainer = p.primaryContainerDark.copy(alpha = 0.50f),
    onPrimaryContainer = Color.White,
    secondary = p.secondaryDark,
    onSecondary = p.onSecondaryDark,
    secondaryContainer = p.secondaryContainerDark.copy(alpha = 0.50f),
    onSecondaryContainer = Color.White,
    tertiary = p.secondaryDark,
    onTertiary = p.onSecondaryDark,
    tertiaryContainer = p.secondaryContainerDark.copy(alpha = 0.50f),
    background = DarkBackground,
    onBackground = Color(0xFFE7E2EA),
    surface = DarkSurface.copy(alpha = 0.45f),
    onSurface = Color(0xFFE7E2EA),
    surfaceVariant = DarkSurfaceVariant.copy(alpha = 0.42f),
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = p.primaryDark,
    outline = blend(DarkOnSurfaceVariant, p.primaryDark, 0.22f).copy(alpha = 0.55f),
    outlineVariant = blend(DarkSurfaceVariant, p.primaryDark, 0.14f).copy(alpha = 0.45f),
    inverseSurface = Color(0xFFE7E2EA),
    inverseOnSurface = Color(0xFF2F2D33),
    inversePrimary = p.primaryContainerDark,
    surfaceContainerLowest = blend(DarkBackground, Color.Black, 0.35f).copy(alpha = 0.35f),
    surfaceContainerLow = blend(DarkSurface, p.primaryDark, 0.07f).copy(alpha = 0.40f),
    surfaceContainer = blend(DarkSurface, p.primaryDark, 0.11f).copy(alpha = 0.48f),
    surfaceContainerHigh = blend(DarkSurface, p.primaryDark, 0.16f).copy(alpha = 0.56f),
    surfaceContainerHighest = blend(DarkSurface, p.primaryDark, 0.22f).copy(alpha = 0.64f),
    error = Color(0xFFFF6B6B),
)

/** The header/gradient colors for [AppColorMode.GLASS] — the selected
 * palette's own gradient pair, softened toward translucent so the header
 * itself reads as a pane of glass rather than a solid banner. Never fully
 * invisible: [com.shopmanager.app.ui.common.liquidGlassSurface]'s own
 * layered highlight/rim (switched on for this mode via [LocalGlassMode])
 * keeps the panel readable on top of whatever scrolls near it. */
internal fun glassGradientColors(p: PaletteColors): List<Color> = listOf(
    p.gradientStart.copy(alpha = 0.60f),
    p.gradientEnd.copy(alpha = 0.60f)
)

/** True only when [AppColorMode.GLASS] is the active color mode. Read by
 * [com.shopmanager.app.ui.common.liquidGlassSurface],
 * [com.shopmanager.app.ui.common.GlassAlertDialog], and the glass buttons
 * in [com.shopmanager.app.ui.common.GlassButton]/
 * [com.shopmanager.app.ui.common.LiquidGlass] to switch on their animated
 * drift/sheen motion and push their translucency further — scoped to this
 * mode alone so [AppColorMode.MANUAL]/[AppColorMode.CLASSIC]'s existing
 * calm, static glass panels never change. */
val LocalGlassMode = staticCompositionLocalOf { false }

/** Carries the selected palette's brand-gradient colors down to
 * [com.shopmanager.app.ui.common.BrandGradient] without threading a
 * parameter through every screen that already calls
 * `BrandGradient.brush()`. Provided once near the root (see MainActivity),
 * defaults to the Indigo gradient so previews and anything outside the
 * provider still render correctly. */
val LocalBrandGradientColors = staticCompositionLocalOf { listOf(BrandGradientStart, BrandGradientEnd) }
