package com.shopmanager.app.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.PerformanceTier

/**
 * Flat, opaque panel surface used for every header/top-bar/dialog/card in
 * the app. This used to be a translucent "liquid glass" effect that only
 * rendered that way when Settings -> المظهر had "زجاج" selected; that mode
 * has been removed entirely, so this is now just a plain solid panel with
 * a drop shadow and an optional hairline border — the same look every
 * caller already fell back to outside glass mode, kept as the one and
 * only look now so nothing needed to change at any call site.
 *
 * PERF (low-end tier, "اصلاحات للاجهزة اللي فيها معالج رسوميات ضعيف"): a
 * drop shadow isn't free — `Modifier.shadow` asks the GPU to rasterize a
 * soft penumbra behind the shape on every frame it's on screen, and this
 * one modifier sits behind essentially every header/card/dialog/nav-bar in
 * the app (see FloatingBottomNav, GlassCard, every GlassAlertDialog...),
 * so on a weak/old GPU those shadows add up to real, measurable frame
 * time. Same trade-off BrandGradient already makes for its per-pixel
 * gradient shader: LOW-tier devices skip the shadow outright instead of
 * drawing it at a reduced elevation, since a flat panel with no shadow
 * reads as an intentional, cohesive "flatter" look rather than a
 * half-broken one — never a jarring mix of some panels shadowed and
 * others not.
 */
@Composable
fun Modifier.liquidGlassSurface(
    shape: Shape,
    baseBrush: Brush = BrandGradient.brush(),
    // "عائم" (floating): a soft drop shadow under the panel so it reads as
    // a distinct floating layer above the content behind it. 0.dp keeps a
    // flush look for callers that want the panel to sit flat against
    // whatever's behind it.
    elevation: Dp = 10.dp,
    // Kept for source compatibility with existing call sites; the
    // animated sheen/highlight/droplet effects these used to enable have
    // been removed, so these parameters no longer change anything.
    sheen: Boolean = false,
    // `topFlush = true` drops the shadow for a panel meant to sit directly
    // beneath another panel of the same kind, so the two read as one
    // continuous surface instead of two stacked slabs with a shadow line
    // between them.
    topFlush: Boolean = false,
    highlight: Boolean = false,
    animated: Boolean = false,
    baseAlpha: Float = 1f,
    // A hairline border color. `Color.White` (the default) means "no
    // border" for panels that sit on the app's own colored gradient; any
    // other color draws a subtle border, e.g. for a panel sitting on a
    // plain surface.
    rimColor: Color = Color.White
): Modifier {
    val isLowTier = LocalPerformanceTier.current == PerformanceTier.LOW
    return this
        .let {
            if (elevation > 0.dp && !topFlush && !isLowTier) {
                it.shadow(elevation, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.35f))
            } else it
        }
        .clip(shape)
        .background(baseBrush)
        .let {
            if (rimColor == Color.White) it
            else it.border(1.dp, rimColor.copy(alpha = 0.12f), shape)
        }
}

/**
 * Circular icon button used on top of the header gradient and other
 * colored panels. Previously switched to a translucent "glass" look in
 * glass mode; that mode is gone, so this is now always the plain,
 * solidly-tinted circular icon button every other color mode already used.
 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 40.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = MotionSpecs.pressSpring(),
        label = "glassIconButtonScale"
    )
    val restingFillAlpha = 0.22f
    val restingRimAlpha = 0f
    val fillAlpha by animateFloatAsState(
        targetValue = if (pressed) restingFillAlpha + 0.10f else restingFillAlpha,
        animationSpec = MotionSpecs.pressSpring(),
        label = "glassIconButtonFill"
    )
    val rimAlpha by animateFloatAsState(
        targetValue = if (pressed) restingRimAlpha + 0.14f else restingRimAlpha,
        animationSpec = MotionSpecs.pressSpring(),
        label = "glassIconButtonRim"
    )

    IconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = fillAlpha), Color.White.copy(alpha = fillAlpha))))
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color.White.copy(alpha = rimAlpha), Color.White.copy(alpha = rimAlpha))),
                CircleShape
            )
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/**
 * A soft glowing circle used as a purely decorative background accent
 * (behind the splash screen's logo orb, for example). Previously used a
 * real Gaussian blur to soften its edge; replaced here with a plain radial
 * gradient that fades to transparent, which reads as the same soft glow
 * with no blur pass and no OS-version/performance-tier branching needed.
 */
@Composable
fun LiquidGlassGlow(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    blurRadius: Dp = 30.dp
) {
    Box(
        modifier
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.55f), color.copy(alpha = 0f))
                )
            )
    )
}
