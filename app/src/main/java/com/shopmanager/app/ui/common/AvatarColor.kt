package com.shopmanager.app.ui.common

import androidx.compose.ui.graphics.Color

// "شيل الألوان، خليه بس ليلي/نهاري": avatars used to cycle through 8
// saturated brand colors (indigo/violet/pink/amber/emerald/cyan/red/teal) —
// exactly the kind of per-element color the rest of the redesign removed
// from cards/headers/buttons, just still showing up here. Now a ladder of
// true grayscale tones instead: still gives each customer/material a
// visually distinct, deterministic circle (darker vs. lighter), just no hue
// at all, consistent with the rest of the neutral black/white/gray design.
private val AvatarPalette = listOf(
    Color(0xFF6B6B70),
    Color(0xFF5A5A5F),
    Color(0xFF7D7D82),
    Color(0xFF4D4D52),
    Color(0xFF8E8E93),
    Color(0xFF56565B),
    Color(0xFF75757A),
    Color(0xFF636368),
)

/** Deterministic color per name, so the same customer/material always gets the same color. */
fun avatarColorFor(name: String): Color {
    if (name.isBlank()) return AvatarPalette.first()
    val index = (name.sumOf { it.code } % AvatarPalette.size + AvatarPalette.size) % AvatarPalette.size
    return AvatarPalette[index]
}
