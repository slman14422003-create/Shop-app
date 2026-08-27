package com.shopmanager.app.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * number — animating this Text recomposes it on every animation frame for
 * its duration. On a LOW-tier device that's extra recompositions on top of
 * everything else already redrawing, so LOW settles in a single frame
 * (duration 0), same as if there were no animation at all.
 *
 * FIX (feel): STANDARD/HIGH previously used a flat/linear tween, which is
 * what a count-up looks like when it's "just" animating rather than
 * feeling designed — it starts and ends at the same constant speed with
 * no ease-out, so the final digits change at the same pace as the middle
 * ones and the stop reads as abrupt. FastOutSlowInEasing (Material's
 * standard easing curve) starts fast and settles gently instead, which is
 * what makes the same animation read as smooth rather than mechanical.
 *
 * BUG FIXED (cold-start "jitter"): [animate] lets a caller mark that its
 * data hasn't actually loaded yet (e.g. `!debtsState.isLoading`). While
 * that's false, any target this receives (typically the 0.0 loading
 * placeholder) is applied instantly with no animation. The very first time
 * it flips to true — the frame the real total first appears — is *also*
 * snapped instantly instead of counted up, since there was never a real
 * previous number on screen to count up *from*; animating that first
 * reveal is exactly what read as the totals visibly assembling/shaking
 * right when the app opens. Only changes *after* that first reveal (a
 * debt actually added or edited while the screen is open) get the
 * count-up animation — which is when it actually reads as "alive"
 * instead of as loading noise.
 */
@Composable
fun AnimatedCounterText(
    targetValue: Double,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    fontWeight: FontWeight = FontWeight.Bold,
    animate: Boolean = true
) {
    val isLowTier = LocalPerformanceTier.current == PerformanceTier.LOW
    val animatable = remember { Animatable(targetValue.toFloat()) }
    var hasRevealedOnce by remember { mutableStateOf(false) }

    LaunchedEffect(targetValue, animate) {
        if (!animate || !hasRevealedOnce) {
            animatable.snapTo(targetValue.toFloat())
            if (animate) hasRevealedOnce = true
        } else {
            animatable.animateTo(
                targetValue = targetValue.toFloat(),
                animationSpec = tween(
                    durationMillis = if (isLowTier) 0 else 500,
                    easing = FastOutSlowInEasing
                )
            )
        }
    }
    Text(format(animatable.value.toDouble()), modifier = modifier, style = style, fontWeight = fontWeight)
}
