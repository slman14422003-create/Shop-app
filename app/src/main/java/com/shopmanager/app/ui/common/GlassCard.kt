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
 * Flat, hairline-bordered card panel used for list rows/forms (DebtRow,
 * LinkedNoteRow, AddDebtCard, ...). Previously could render as translucent
 * "glass" when Settings -> المظهر had "زجاج" selected; that mode has been
 * removed, so this is now always the plain flat card every other color
 * mode already used.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    elevation: Dp = 10.dp,
    content: @Composable () -> Unit
) {
    val toneColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Box(
        modifier
            .liquidGlassSurface(
                shape = shape,
                baseBrush = Brush.linearGradient(listOf(toneColor, toneColor)),
                elevation = 0.dp,
                highlight = false
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), shape)
    ) {
        content()
    }
}
