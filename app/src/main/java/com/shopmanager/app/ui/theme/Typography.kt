package com.shopmanager.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Claude-app-inspired type scale: big screen titles ("Settings"-style) use a
 * serif display face — the same touch the Claude Android app uses to give
 * its headers a calmer, editorial feel instead of the tech-default sans —
 * while every smaller/body role stays on the platform sans so paragraphs,
 * labels, and buttons remain fast to read. Large headings keep a touch of
 * negative letter-spacing so they stay dense rather than loose; small labels
 * get slightly *positive* spacing so they stay legible at 11–12sp.
 */
private val ClaudeSerif = FontFamily.Serif

val AppTypography = Typography(
    headlineSmall = TextStyle(fontFamily = ClaudeSerif, fontWeight = FontWeight.Normal, fontSize = 25.sp, lineHeight = 31.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = ClaudeSerif, fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.1).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.2).sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = (-0.1).sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.2.sp),
)
