package com.shopmanager.app.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/**
 * The one circular action-button look used for every check / edit / delete
 * affordance in the app (person list, debt list, material list).
 *
 * BUG FIXED (design precision): the green "mark as paid" check button was
 * 36dp with no press feedback, while the red delete "×" button next to it
 * ([DeleteIconButton]) was 34dp with a scale-down press animation. Sitting
 * in the same row, that 2dp size mismatch and the missing animation on one
 * of the two is exactly the kind of inconsistency that reads as "not quite
 * right" even when you can't immediately say why — and inconsistent
 * per-button feedback is also part of what makes a UI feel less smooth to
 * use. Every circular action button (check, edit, delete) now shares this
 * single 36dp implementation, so they're pixel-identical in size, icon
 * padding, tint intensity, and press animation everywhere they appear.
 *
 * FEATURE ADDED ("تحسينات للتنقل واللمس"): every tap now fires a short
 * haptic tick right before [onClick] runs — a small tactile confirmation
 * that something actually happened (settling a debt, finishing a note,
 * deleting a row), the same way iOS gives a light tap-tic on most of its
 * own buttons. Compose only exposes [HapticFeedbackType.LongPress] and
 * [HapticFeedbackType.TextHandleMove] at this Compose UI version — despite
 * the name, [HapticFeedbackType.LongPress] fires immediately on call (it
 * maps to the platform's own HapticFeedbackConstants.LONG_PRESS, not an
 * actual long-press gesture), so it's reused here as the generic "tick"
 * instead of reaching for a raw platform HapticFeedbackConstants call.
 * Since [DeleteIconButton] is a thin wrapper around this composable, every
 * delete button in the app picks this up for free too.
 */
@Composable
fun ActionIconButton(
    icon: ImageVector,
    tint: Color,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = MotionSpecs.pressSpring(),
        label = "actionButtonScale"
    )
    val haptics = LocalHapticFeedback.current

    IconButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        interactionSource = interactionSource,
        modifier = modifier
            .size(36.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.12f))
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(18.dp))
    }
}
