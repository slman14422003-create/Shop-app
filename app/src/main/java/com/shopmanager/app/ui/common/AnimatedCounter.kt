package com.shopmanager.app.ui.common

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
 * frame for 600ms. On a LOW-tier device that's ~36 extra recompositions
 * per total that changes, on top of everything else already redrawing.
 * Dropping the duration to 0 keeps one code path (no separate "static
 * text" branch to maintain) while making it settle in a single frame,
 * same as if there were no animation at all.
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
        animationSpec = tween(durationMillis = if (isLowTier) 0 else 600),
        label = "counter"
    )
    Text(format(animated.toDouble()), modifier = modifier, style = style, fontWeight = fontWeight)
}
