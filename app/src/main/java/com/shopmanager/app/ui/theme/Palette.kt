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

internal fun lightSchemeFor(p: PaletteColors): ColorScheme = lightColorScheme(
    primary = p.primaryLight,
    onPrimary = Color.White,
    primaryContainer = p.primaryContainerLight,
    onPrimaryContainer = p.primaryLight,
    secondary = p.secondaryLight,
    onSecondary = Color.White,
    secondaryContainer = p.secondaryContainerLight,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
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
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = Color(0xFFFF6B6B),
)

/** Carries the selected palette's brand-gradient colors down to
 * [com.shopmanager.app.ui.common.BrandGradient] without threading a
 * parameter through every screen that already calls
 * `BrandGradient.brush()`. Provided once near the root (see MainActivity),
 * defaults to the Indigo gradient so previews and anything outside the
 * provider still render correctly. */
val LocalBrandGradientColors = staticCompositionLocalOf { listOf(BrandGradientStart, BrandGradientEnd) }
