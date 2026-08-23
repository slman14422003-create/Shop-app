package com.shopmanager.app.ui.theme

import androidx.compose.ui.graphics.Color

// Brand
val Indigo80 = Color(0xFFC7D2FE)
val Indigo40 = Color(0xFF4F46E5)
val Violet80 = Color(0xFFDDD6FE)
val Violet40 = Color(0xFF7C3AED)

// Brand gradient — used for headers, top bars, and the status bar across
// BOTH light and dark theme. Kept deliberately independent from
// colorScheme.primary/secondary: in the dark scheme those are intentionally
// pale tones (Indigo80/Violet80, meant for text/icon contrast on dark
// surfaces), so painting a full-width header/status bar with them looked
// like a jarring pale-purple flash against an otherwise dark app. This pair
// stays a rich, deep indigo→violet in every theme, so the header is always
// legible with white text/icons and never clashes with dark mode.
val BrandGradientStart = Color(0xFF4F46E5)
val BrandGradientEnd = Color(0xFF7C3AED)

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
