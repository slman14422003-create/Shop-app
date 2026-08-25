package com.shopmanager.app.ui.theme

import androidx.compose.ui.graphics.Color

// Brand (Indigo palette — the default)
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
