package com.shopmanager.app.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * "أزرار الزجاج السائل" (glass-mode text/filled buttons) — the text-button
 * counterpart to [GlassIconButton]/[liquidGlassSurface]'s circular icon
 * buttons, for the same job Material's [androidx.compose.material3.Button]/
 * [androidx.compose.material3.OutlinedButton] do, but styled to sit
 * directly on the brand gradient or another [liquidGlassSurface] panel
 * instead of on a plain card/dialog surface.
 *
 * IMPORTANT — where these belong (see the in-chat glass-mode roadmap):
 * only use these when the button's own background IS the brand gradient or
 * a glass panel (headers, PersonHeader-style summary panels, a future
 * frosted dialog). A plain Material [androidx.compose.material3.Button] on
 * an ordinary Card/Dialog/Scaffold surface is correct as-is — wrapping
 * every button in the app in glass regardless of what's behind it would
 * make filled/outlined buttons unreadable on light surfaces and is not
 * what "glass mode" means here.
 */
@Composable
fun GlassFilledButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(50),
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    icon: (@Composable () -> Unit)? = null
) {
    GlassButtonBase(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        contentPadding = contentPadding,
        background = Color.White.copy(alpha = if (enabled) 0.22f else 0.10f),
        borderAlpha = if (enabled) 0.45f else 0.20f,
        textColor = BrandOnGradient.copy(alpha = if (enabled) 1f else 0.5f),
        icon = icon,
        text = text
    )
}

/** Lower-emphasis sibling of [GlassFilledButton] — thinner fill, same rim,
 * for a secondary/"cancel"-style action next to a [GlassFilledButton] on
 * the same glass panel. */
@Composable
fun GlassOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(50),
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    icon: (@Composable () -> Unit)? = null
) {
    GlassButtonBase(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        contentPadding = contentPadding,
        background = Color.Transparent,
        borderAlpha = if (enabled) 0.40f else 0.18f,
        textColor = BrandOnGradient.copy(alpha = if (enabled) 0.92f else 0.5f),
        icon = icon,
        text = text
    )
}

@Composable
private fun GlassButtonBase(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    shape: Shape,
    contentPadding: PaddingValues,
    background: Color,
    borderAlpha: Float,
    textColor: Color,
    icon: (@Composable () -> Unit)?
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = MotionSpecs.pressSpring(),
        label = "glassButtonScale"
    )

    Row(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(background)
            .border(1.dp, BrandOnGradient.copy(alpha = borderAlpha), shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let {
            it()
            Spacer(Modifier.width(8.dp))
        }
        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.labelLarge) {
            Text(text, color = textColor, fontWeight = FontWeight.SemiBold)
        }
    }
}
