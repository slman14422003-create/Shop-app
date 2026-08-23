package com.shopmanager.app.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.shopmanager.app.ui.theme.DangerRed

/**
 * A small, clearly-marked circular "×" delete affordance shown next to a
 * list row. Used instead of swipe-to-delete: with swipe navigation between
 * top-level screens (see the HorizontalPager in MainActivity), a horizontal
 * swipe-to-delete gesture on each row would fight the page-swipe gesture for
 * the same drag axis. A tap target is unambiguous, works the same everywhere,
 * and never gets tangled up with page swiping.
 */
@Composable
fun DeleteIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, contentDescription: String = "حذف") {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.5f),
        label = "deleteButtonScale"
    )

    IconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .size(34.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(DangerRed.copy(alpha = 0.12f))
    ) {
        Icon(
            Icons.Default.Close,
            contentDescription = contentDescription,
            tint = DangerRed,
            modifier = Modifier.size(18.dp)
        )
    }
}
