package com.shopmanager.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.PerformanceTier
import com.shopmanager.app.ui.theme.LocalBrandGradientColors

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
 *
 * PERF (low-end tier): a gradient shader still has to be evaluated per
 * pixel by the GPU on every draw, and cheap SoCs (the Mali/PowerVR parts
 * in entry-level phones) with older/thinner GPU driver stacks are exactly
 * where that shows up as extra frame time on every header. STANDARD-tier
 * devices keep the real gradient; LOW-tier devices get a flat SolidColor
 * of the same brand start color instead — same brand look, no per-pixel
 * shader.
 */
object BrandGradient {
    @Composable
    fun brush(): Brush {
        val colors = LocalBrandGradientColors.current
        val isLowTier = LocalPerformanceTier.current == PerformanceTier.LOW
        return remember(isLowTier, colors) {
            if (isLowTier) SolidColor(colors.first()) else Brush.verticalGradient(colors)
        }
    }

    @Composable
    fun horizontalBrush(): Brush {
        val colors = LocalBrandGradientColors.current
        val isLowTier = LocalPerformanceTier.current == PerformanceTier.LOW
        return remember(isLowTier, colors) {
            if (isLowTier) SolidColor(colors.first()) else Brush.horizontalGradient(colors)
        }
    }
}

val BrandOnGradient: Color = Color.White
