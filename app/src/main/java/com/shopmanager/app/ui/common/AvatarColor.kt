package com.shopmanager.app.ui.common

import androidx.compose.ui.graphics.Color

// NEUTRALIZED ("الالوان متل لقطة الشاشة... بكل التطبيق" — back to the
// neutral black/white/gray look, not a colorful accent ladder): this used
// to cycle each customer/material's circle through Claude's warm accent
// hues (terracotta/gold/coral/sage) so every name got a different color.
// The real Claude app doesn't do that — its circles are one flat neutral
// tone regardless of name — and against this app's now-neutral primary
// (see Palette.kt) a rainbow of per-name accents was the one place still
// visibly "colorful" next to Claude's own monochrome chrome. Same function
// signature (still deterministic per name, still called the same way at
// every call site, including the non-Composable NotificationHelper) — only
// the returned color changed, to one fixed neutral gray instead of a
// per-name ladder.
private val AvatarNeutral = Color(0xFF8A8778)

/** Deterministic color per name — now a single flat neutral tone (same for
 * every name), matching Claude's own avatar circles instead of a per-name
 * accent ladder. */
fun avatarColorFor(name: String): Color = AvatarNeutral
