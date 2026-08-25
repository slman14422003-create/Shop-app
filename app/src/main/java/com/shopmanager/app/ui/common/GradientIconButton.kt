package com.shopmanager.app.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * The circular action-button used for icons that sit directly on top of the
 * brand gradient header (settings on the dashboard, share on the materials
 * screen, etc). Deliberately a fully opaque solid-white circle with a
 * tinted glyph rather than a translucent/frosted "glass" chip — that's the
 * flat, high-contrast look modern iOS nav bars use for their circular
 * buttons, and it reads as crisp instead of the older blurred-glass pill
 * this app used to lean on. Shares the same press-scale feedback as
 * [ActionIconButton] so every circular tap target in the app feels the same
 * regardless of what it's sitting on.
 */
@Composable
fun GradientIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = MotionSpecs.pressSpring(),
        label = "gradientIconButtonScale"
    )

    IconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .size(40.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Color.White)
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}
