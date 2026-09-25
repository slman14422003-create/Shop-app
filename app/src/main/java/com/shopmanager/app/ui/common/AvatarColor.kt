package com.shopmanager.app.ui.common

import androidx.compose.ui.graphics.Color

// REDESIGN ("بدهم مظهر جميل غير هاد للرمادي"): avatars used to be a flat
// grayscale ladder — visually distinct per name, but completely toneless
// next to every other icon badge in the app (QuickActionButton, StatCard,
// SettingsSection all tint their circle with a real accent color, never
// gray). Rebuilt as a ladder of Claude's own warm accent tones instead —
// the exact same terracotta/gold/coral/sage hues already used for
// primary/warning/error/success elsewhere in the theme (see theme/Color.kt)
// — so a customer or material's circle now reads as "branded" rather than
// "disabled", while still being fully deterministic (same name → same
// color every time) and still built from a small closed set so any two
// names can land on the same tone without it looking arbitrary.
private val AvatarPalette = listOf(
    Color(0xFFD97757), // Claude orange (dark theme)
    Color(0xFFC96442), // Claude orange (light theme) — deeper rust
    Color(0xFFD3A461), // Claude gold (dark theme) — amber
    Color(0xFFB07A2E), // Claude gold (light theme) — ochre
    Color(0xFFE0916D), // Claude red (dark theme) — coral
    Color(0xFFBC4C34), // Claude red (light theme) — terracotta
    Color(0xFF8CB894), // Claude green (dark theme) — sage
    Color(0xFF5F8768), // Claude green (light theme) — moss
)

/** Deterministic color per name, so the same customer/material always gets the same color. */
fun avatarColorFor(name: String): Color {
    if (name.isBlank()) return AvatarPalette.first()
    val index = (name.sumOf { it.code } % AvatarPalette.size + AvatarPalette.size) % AvatarPalette.size
    return AvatarPalette[index]
}
