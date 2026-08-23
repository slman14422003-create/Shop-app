package com.shopmanager.app.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle

/**
 * Animates a numeric value counting up (or down) to [targetValue] whenever
 * it changes, instead of the label just snapping to the new number. Small
 * touch, but it makes totals feel alive/responsive after every add or edit
 * instead of static text.
 */
@Composable
fun AnimatedCounterText(
    targetValue: Double,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    fontWeight: FontWeight = FontWeight.Bold
) {
    val animated by animateFloatAsState(
        targetValue = targetValue.toFloat(),
        animationSpec = tween(durationMillis = 600),
        label = "counter"
    )
    Text(format(animated.toDouble()), modifier = modifier, style = style, fontWeight = fontWeight)
}
