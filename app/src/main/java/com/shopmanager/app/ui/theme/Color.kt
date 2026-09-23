package com.shopmanager.app.ui.theme

import androidx.compose.ui.graphics.Color

// Claude palette — warm terracotta/rust accent + muted sand secondary,
// matching the Claude AI app's look (the new default palette; see
// AppColorPalette.CLAUDE in Palette.kt). Because darkSchemeFor/lightSchemeFor
// derive every background/surface tone from the selected palette's own hue
// (see [hueOf]/[tone] in Palette.kt), picking this as the default palette is
// what gives the *whole app* Claude's warm near-black dark mode and warm
// cream light mode automatically, not just its accent color.
//
// PRECISION FIX ("الوان التطبيق تحتاج تظبيط اكثر"): Claude40 is now
// Anthropic's actual brand coral (#CC785C, "Crail") instead of the earlier
// hand-picked approximation (#CC7A52) — a small shift toward slightly more
// pink/less orange, but it's the real value the rest of Claude's own UI
// uses, so every screen that reads off this palette (buttons, the drawer's
// selection pill, headers, the primary dialog action) now matches Claude's
// accent exactly rather than approximately.
val Claude80 = Color(0xFFF0C6AE)
val Claude40 = Color(0xFFCC785C)
val Sand80 = Color(0xFFE8D6C3)
val Sand40 = Color(0xFFA98763)

// ============================================================================
// Claude design-system tokens — pulled 1:1 from the reference res/ design
// (values/colors.xml + values-night/colors.xml): cream/terracotta light,
// near-black/coral dark, exactly matching the real Claude.ai app. These are
// what [claudeLightScheme]/[claudeDarkScheme] in Palette.kt build the CLAUDE
// palette's actual ColorScheme from — replacing the generic hue-derived
// tone() approximation with the reference design's literal values, so every
// card/dialog/button/settings-row (all of which already read off
// MaterialTheme.colorScheme) matches the reference pixel-for-pixel.
// ============================================================================

// Backgrounds — light: warm cream ladder; dark: near-black ladder.
val ClaudeBgLight1 = Color(0xFFF5F4EE)
val ClaudeBgLight2 = Color(0xFFF0EEE6)
val ClaudeBgLight3 = Color(0xFFEAE7DD)
val ClaudeBgDark1 = Color(0xFF131313)
val ClaudeBgDark2 = Color(0xFF181818)
val ClaudeBgDark3 = Color(0xFF1E1E1E)

// Brand accent (the flat terracotta used for primary buttons/selection).
val ClaudeOrangeLight = Color(0xFFC96442)
val ClaudeOrangeDark = Color(0xFFD97757)
val ClaudePrimaryDarkVariant = Color(0xFFAD5237) // primary_cyan_dark, light theme
val ClaudePrimaryDarkVariantOnDark = Color(0xFFEFAA8B) // primary_cyan_dark, dark theme

// Neutral secondary accent (settings icons, secondary chips).
val ClaudeSecondaryLight = Color(0xFF6A6968)
val ClaudeSecondaryDark = Color(0xFFB7B4AC)

// Card / dialog / settings-group fill — the literal "glass_fill_strong".
val ClaudeCardLight = Color(0xFFFFFFFF)
val ClaudeCardDark = Color(0xFF1F1F1F)
val ClaudeCardSoftLight = Color(0xFFFAF9F5)
val ClaudeCardSoftDark = Color(0xFF1A1A1A)

// Borders.
val ClaudeBorderLight = Color(0xFFE7E4DA)
val ClaudeBorderSoftLight = Color(0xFFEEEBE2)
val ClaudeBorderDark = Color(0xFF333333)
val ClaudeBorderSoftDark = Color(0xFF2A2A2A)

// Text.
val ClaudeTextPrimaryLight = Color(0xFF3D3929)
val ClaudeTextSecondaryLight = Color(0xFF78766D)
val ClaudeTextTertiaryLight = Color(0xFFA6A399)
val ClaudeTextPrimaryDark = Color(0xFFF2F2F2)
val ClaudeTextSecondaryDark = Color(0xFFA6A6A6)
val ClaudeTextTertiaryDark = Color(0xFF7C7C7C)

// Semantic accents (red/green/gold).
val ClaudeAccentRedLight = Color(0xFFBC4C34)
val ClaudeAccentRedDarkVariantLight = Color(0xFF96392A)
val ClaudeAccentGreenLight = Color(0xFF5F8768)
val ClaudeAccentGoldLight = Color(0xFFB07A2E)
val ClaudeAccentRedDark = Color(0xFFE0916D)
val ClaudeAccentRedDarkVariantDark = Color(0xFFEFAA8B)
val ClaudeAccentGreenDark = Color(0xFF8CB894)
val ClaudeAccentGoldDark = Color(0xFFD3A461)

// Material3 role fills, straight from the reference m3_* tokens.
val ClaudeM3PrimaryContainerLight = Color(0xFFF2D9CB)
val ClaudeM3OnPrimaryContainerLight = Color(0xFF5A2A15)
val ClaudeM3SecondaryContainerLight = Color(0xFFE7E5DC)
val ClaudeM3OnSecondaryContainerLight = Color(0xFF302E29)
val ClaudeM3TertiaryContainerLight = Color(0xFFF2E1C0)
val ClaudeM3OnTertiaryContainerLight = Color(0xFF452F0E)
val ClaudeM3SurfaceVariantLight = Color(0xFFF0EDE4)
val ClaudeM3ErrorContainerLight = Color(0xFFF5D6CC)
val ClaudeM3OnErrorContainerLight = Color(0xFF571C0E)
val ClaudeM3SurfaceContainerLight = Color(0xFFF2F0E8)
val ClaudeM3SurfaceContainerHighLight = Color(0xFFEAE7DD)

val ClaudeM3PrimaryContainerDark = Color(0xFF583020)
val ClaudeM3OnPrimaryContainerDark = Color(0xFFFADACB)
val ClaudeM3SecondaryContainerDark = Color(0xFF2E2E2E)
val ClaudeM3OnSecondaryContainerDark = Color(0xFFE4E4E4)
val ClaudeM3TertiaryContainerDark = Color(0xFF4A3820)
val ClaudeM3OnTertiaryContainerDark = Color(0xFFF6D9A9)
val ClaudeM3SurfaceVariantDark = Color(0xFF2B2B2B)
val ClaudeM3ErrorContainerDark = Color(0xFF5A2820)
val ClaudeM3OnErrorContainerDark = Color(0xFFFFDAD2)
val ClaudeM3SurfaceContainerDark = Color(0xFF1B1B1B)
val ClaudeM3SurfaceContainerHighDark = Color(0xFF2B2B2B)

// Brand (Indigo palette — kept as a secondary option)
val Indigo80 = Color(0xFFC7D2FE)
val Indigo40 = Color(0xFF4F46E5)
val Violet80 = Color(0xFFDDD6FE)
val Violet40 = Color(0xFF7C3AED)

// Brand gradient — used for headers, top bars, and the status bar across
// BOTH light and dark theme. Kept deliberately independent from
// colorScheme.primary/secondary: in the dark scheme those are intentionally
// pale tones (e.g. Indigo80/Violet80, meant for text/icon contrast on dark
// surfaces), so painting a full-width header/status bar with them looked
// like a jarring pale flash against an otherwise dark app. Each palette's
// gradient stays rich/deep in every theme, so the header is always legible
// with white text/icons and never clashes with dark mode. These two vals
// are the Indigo palette's gradient (also the default/fallback); the
// per-palette values live in Palette.kt and are what actually gets used at
// runtime once a color palette is selected in Settings.
val BrandGradientStart = Color(0xFF4F46E5)
val BrandGradientEnd = Color(0xFF7C3AED)

// Emerald palette
val Emerald80 = Color(0xFFA7F3D0)
val Emerald40 = Color(0xFF059669)
val Teal80 = Color(0xFF99F6E4)
val Teal40 = Color(0xFF0D9488)

// Ocean palette
val Ocean80 = Color(0xFFBFDBFE)
val Ocean40 = Color(0xFF2563EB)
val Cyan80 = Color(0xFFA5F3FC)
val Cyan40 = Color(0xFF0891B2)

// Sunset palette
val Sunset80 = Color(0xFFFED7AA)
val Sunset40 = Color(0xFFEA580C)
val Rose80 = Color(0xFFFECDD3)
val Rose40 = Color(0xFFE11D48)

// Berry palette
val Berry80 = Color(0xFFF5D0FE)
val Berry40 = Color(0xFFC026D3)
val Pink80 = Color(0xFFFBCFE8)
val Pink40 = Color(0xFFDB2777)

// --- Extended palette (15 additional hue pairs → 20 palettes total) ---

// Crimson
val Crimson80 = Color(0xFFFECACA)
val Crimson40 = Color(0xFFDC2626)
val CrimsonOnDark = Color(0xFF4A0404)

// Amber
val Amber80 = Color(0xFFFDE68A)
val Amber40 = Color(0xFFD97706)
val AmberOnDark = Color(0xFF4A2E00)

// Gold
val Gold80 = Color(0xFFFEF08A)
val Gold40 = Color(0xFFCA8A04)
val GoldOnDark = Color(0xFF3E2A00)

// Lime
val Lime80 = Color(0xFFD9F99D)
val Lime40 = Color(0xFF65A30D)
val LimeOnDark = Color(0xFF253C00)

// Forest
val Forest80 = Color(0xFFBBF7D0)
val Forest40 = Color(0xFF15803D)
val ForestOnDark = Color(0xFF052E12)

// Sky
val Sky80 = Color(0xFFBAE6FD)
val Sky40 = Color(0xFF0284C7)
val SkyOnDark = Color(0xFF012A4A)

// Cobalt
val Cobalt80 = Color(0xFF93C5FD)
val Cobalt40 = Color(0xFF1D4ED8)
val CobaltOnDark = Color(0xFF0B1F66)

// Lavender
val Lavender80 = Color(0xFFE9D5FF)
val Lavender40 = Color(0xFF8B5CF6)
val LavenderOnDark = Color(0xFF2E1065)

// Orchid
val Orchid80 = Color(0xFFF0ABFC)
val Orchid40 = Color(0xFFA21CAF)
val OrchidOnDark = Color(0xFF3B0764)

// Slate
val Slate80 = Color(0xFFCBD5E1)
val Slate40 = Color(0xFF475569)
val SlateOnDark = Color(0xFF1E293B)

// Ruby
val Ruby80 = Color(0xFFFDA4AF)
val Ruby40 = Color(0xFFBE123C)
val RubyOnDark = Color(0xFF4C0519)

// Peach
val Peach80 = Color(0xFFFDBA74)
val Peach40 = Color(0xFFF97316)
val PeachOnDark = Color(0xFF431407)

// Plum
val Plum80 = Color(0xFFEAB8F7)
val Plum40 = Color(0xFF86198F)
val PlumOnDark = Color(0xFF4A044E)

// Steel
val Steel80 = Color(0xFF7DD3FC)
val Steel40 = Color(0xFF0E7490)
val SteelOnDark = Color(0xFF042F2E)

// Graphite
val Graphite80 = Color(0xFF94A3B8)
val Graphite40 = Color(0xFF334155)
val GraphiteOnDark = Color(0xFF0F172A)

// Semantic
val SuccessGreen = Color(0xFF16A34A)
val WarningAmber = Color(0xFFF59E0B)
val DangerRed = Color(0xFFDC2626)
val InfoBlue = Color(0xFF2563EB)

// Light scheme
val LightBackground = Color(0xFFFAFAFC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF1F0FA)
val LightOnSurfaceVariant = Color(0xFF5B5876)

// Dark scheme
val DarkBackground = Color(0xFF121218)
val DarkSurface = Color(0xFF1B1B24)
val DarkSurfaceVariant = Color(0xFF2A2A38)
val DarkOnSurfaceVariant = Color(0xFFC7C5D6)
