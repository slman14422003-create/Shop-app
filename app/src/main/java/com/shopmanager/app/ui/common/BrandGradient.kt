package com.shopmanager.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.shopmanager.app.ui.theme.BrandGradientEnd
import com.shopmanager.app.ui.theme.BrandGradientStart

/**
 * The single brand gradient used everywhere a "header" appears: dashboard
 * header, top app bars, person-detail header. One definition means the
 * whole app reads as one cohesive product instead of each screen picking
 * its own tint, and it stays deep enough in both themes to always pair with
 * white text/icons (see the status bar fix in MainActivity).
 *
 * PERF: the brush is `remember`-ed instead of being rebuilt every
 * recomposition. Every screen that has a live Firestore listener
 * recomposes often (any list update anywhere), and reallocating a
 * `Brush.verticalGradient` on each of those passes was pure waste — this
 * was one of a few small contributors to the sluggish screen-to-screen
 * feel reported after the last redesign.
 */
object BrandGradient {
    val colors = listOf(BrandGradientStart, BrandGradientEnd)

    @Composable
    fun brush(): Brush = remember { Brush.verticalGradient(colors) }

    @Composable
    fun horizontalBrush(): Brush = remember { Brush.horizontalGradient(colors) }
}

val BrandOnGradient: Color = Color.White
