package com.shopmanager.app.ui.common

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * REDESIGN ("بدي تصميم جميل اجمل من هيك"): card panel used for list
 * rows/forms (DebtRow, LinkedNoteRow, AddDebtCard, ...). Previously this
 * always forced `elevation = 0.dp` into [liquidGlassSurface] regardless of
 * the `elevation` parameter passed in here, so every card in the app sat
 * perfectly flush with zero shadow — reading as flat/paper-thin rather than
 * a distinct raised surface (visible in the reference screenshots: every
 * card blends into the page with no separation). The caller's `elevation`
 * is now actually used, and the fill is a very subtle top-to-bottom
 * gradient (surfaceContainerHigh → a hair lighter/darker) instead of one
 * flat color, so cards read with a touch of real depth instead of being a
 * plain color swatch — while staying well within the app's existing
 * restrained, low-shadow visual language.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    elevation: Dp = 8.dp,
    content: @Composable () -> Unit
) {
    val toneColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val liftedTone = MaterialTheme.colorScheme.surfaceContainerHighest

    Box(
        modifier
            .liquidGlassSurface(
                shape = shape,
                baseBrush = Brush.verticalGradient(listOf(liftedTone, toneColor)),
                elevation = elevation,
                highlight = false
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), shape)
    ) {
        content()
    }
}
