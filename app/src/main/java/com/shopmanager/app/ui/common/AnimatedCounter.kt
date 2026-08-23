package com.shopmanager.app.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.PerformanceTier

/**
 * Animates a numeric value counting up (or down) to [targetValue] whenever
 * it changes, instead of the label just snapping to the new number. Small
 * touch, but it makes totals feel alive/responsive after every add or edit
 * instead of static text.
 *
 * PERF (low-end tier): the count-up itself is what's expensive, not the
 * number — animateFloatAsState recomposes this Text on every animation
 * frame for its duration. On a LOW-tier device that's extra recompositions
 * on top of everything else already redrawing, so LOW settles in a single
 * frame (duration 0), same as if there were no animation at all.
 *
 * FIX (feel): STANDARD/HIGH previously used a flat/linear tween, which is
 * what a count-up looks like when it's "just" animating rather than
 * feeling designed — it starts and ends at the same constant speed with
 * no ease-out, so the final digits change at the same pace as the middle
 * ones and the stop reads as abrupt. FastOutSlowInEasing (Material's
 * standard easing curve) starts fast and settles gently instead, which is
 * what makes the same animation read as smooth rather than mechanical.
 */
@Composable
fun AnimatedCounterText(
    targetValue: Double,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    fontWeight: FontWeight = FontWeight.Bold
) {
    val isLowTier = LocalPerformanceTier.current == PerformanceTier.LOW
    val animated by animateFloatAsState(
        targetValue = targetValue.toFloat(),
        animationSpec = tween(
            durationMillis = if (isLowTier) 0 else 500,
            easing = FastOutSlowInEasing
        ),
        label = "counter"
    )
    Text(format(animated.toDouble()), modifier = modifier, style = style, fontWeight = fontWeight)
}
