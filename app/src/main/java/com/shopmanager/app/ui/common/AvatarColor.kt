package com.shopmanager.app.ui.common

import androidx.compose.ui.graphics.Color

private val AvatarPalette = listOf(
    Color(0xFF6366F1), // indigo
    Color(0xFF8B5CF6), // violet
    Color(0xFFEC4899), // pink
    Color(0xFFF59E0B), // amber
    Color(0xFF10B981), // emerald
    Color(0xFF06B6D4), // cyan
    Color(0xFFEF4444), // red
    Color(0xFF14B8A6), // teal
)

/** Deterministic color per name, so the same customer/material always gets the same color. */
fun avatarColorFor(name: String): Color {
    if (name.isBlank()) return AvatarPalette.first()
    val index = (name.sumOf { it.code } % AvatarPalette.size + AvatarPalette.size) % AvatarPalette.size
    return AvatarPalette[index]
}
