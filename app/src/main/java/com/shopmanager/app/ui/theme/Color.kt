package com.shopmanager.app.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// Claude design-system tokens — pulled 1:1 from the reference res/ design
// (values/colors.xml + values-night/colors.xml): cream/terracotta light,
// near-black/coral dark, exactly matching the real Claude.ai app. These are
// what [claudeLightScheme]/[claudeDarkScheme] in Palette.kt build the app's
// single ColorScheme from, so every card/dialog/button/settings-row (all of
// which already read off MaterialTheme.colorScheme) matches the reference
// pixel-for-pixel.
//
// UNIFIED ON CLAUDE'S DESIGN ("ازل الماتيريال يو، ونسق كل الالوان بتصميم
// Claude AI" / "عدل التصميم بشكل جذري ليصبح متل كلود"): this file used to
// also carry a full 20-hue generic Material palette (Indigo/Emerald/Ocean/
// Sunset/Berry/Crimson/Amber/Gold/Lime/Forest/Sky/Cobalt/Lavender/Orchid/
// Slate/Ruby/Peach/Plum/Steel/Graphite) plus a separate Claude80/Claude40/
// Sand80/Sand40 pair and a generic Light*/Dark* surface ladder — leftovers
// from the old picker-driven color-mode system (removed from Palette.kt).
// None of it is reachable any more (the app only ever renders the tokens
// below), so it's removed here too instead of sitting as dead weight.
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
// ACCESSIBILITY FIX: the previous #78766D only reached ~3.9:1 against
// background_light (#F5F4EE) and ~4.1:1 against surfaceVariant (#F0EDE4) —
// both under the WCAG AA 4.5:1 minimum for normal-size text, and this token
// is what onSurfaceVariant reads for every secondary label/subtitle in the
// app. #6B6960 keeps the exact same warm, muted hue (imperceptible next to
// the old value) while clearing 4.5:1 in both contexts.
val ClaudeTextSecondaryLight = Color(0xFF6B6960)
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

// ============================================================================
// REDESIGN ("بدي تصميم جميل اجمل من هيك"): a small curated ladder of warm,
// muted hues — all pulled from the same terracotta/gold/sage family already
// used for brand + semantic accents elsewhere, never a generic rainbow — so
// avatar circles and per-item icon chips read as intentionally varied
// instead of every single one falling back to one flat gray. Fixed (not
// theme-dependent): callers like [avatarColorFor] are plain, non-Composable
// functions used from places with no access to the current color scheme
// (NotificationHelper), so each tone here is picked to sit comfortably on
// both the cream light background and the near-black dark one, with white
// avatar-initial text always staying comfortably legible on top.
// ============================================================================
val AvatarPalette = listOf(
    Color(0xFFC96442), // terracotta (brand)
    Color(0xFF5F8768), // sage green
    Color(0xFFB07A2E), // gold / amber
    Color(0xFF7E7BB0), // dusty lavender-slate
    Color(0xFFAD5237), // deep terracotta
    Color(0xFF6E8B8B), // muted teal
    Color(0xFF8B6F47), // warm clay
    Color(0xFFBC4C34), // clay red
)
