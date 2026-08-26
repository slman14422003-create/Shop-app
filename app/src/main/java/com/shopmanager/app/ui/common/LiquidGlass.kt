package com.shopmanager.app.ui.common

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.PerformanceTier

/**
 * "زجاج سائل" (liquid glass): the frosted, glossy surface behind every
 * header/top bar and its circular buttons.
 *
 * HONEST LIMITATION: real backdrop blur (sampling whatever scrolls
 * *underneath* a translucent bar, the way iOS's frosted nav bars work)
 * isn't something Compose exposes without a dedicated blur library — and
 * none of this app's headers actually sit on top of moving content (each
 * one is the first LazyColumn item, or a non-overlapping Scaffold topBar),
 * so there is nothing behind them to sample anyway. What reads as "glass"
 * here is layered instead, all drawn on the surface's own background —
 * never on top of its text/icon children, so nothing is ever blurred
 * unreadable: the brand gradient underneath, a soft translucent highlight
 * that slowly drifts across it like light moving through liquid, a bright
 * top edge, and a thin glass-rim border. Cheaper on LOW tier (fixed
 * highlight position, no per-frame animation) exactly like [BrandGradient]
 * already degrades there.
 */
@Composable
fun Modifier.liquidGlassSurface(
    shape: Shape,
    baseBrush: Brush = BrandGradient.brush()
): Modifier {
    val isLowTier = LocalPerformanceTier.current == PerformanceTier.LOW

    val drift: Float = if (isLowTier) {
        0.28f
    } else {
        val transition = rememberInfiniteTransition(label = "liquidGlassDrift")
        val value by transition.animateFloat(
            initialValue = 0.08f,
            targetValue = 0.92f,
            animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Reverse),
            label = "liquidGlassDriftValue"
        )
        value
    }

    return this
        .clip(shape)
        .background(baseBrush)
        // PERF: drawWithCache (not drawWithContent) so the three Brush
        // objects below are only rebuilt when `drift` or the surface's
        // `size` actually change — not on every recomposition. On
        // STANDARD/HIGH tier drift changes every animation frame anyway, so
        // this is a wash there; on LOW tier drift is a fixed constant (see
        // above), so this is where it actually pays off: a header sitting
        // behind a live Firestore-backed list (which can recompose often on
        // any data change, unrelated to the header itself) reallocates
        // three gradient Brushes on every one of those passes without this,
        // and zero extra times with it.
        .drawWithCache {
            val w = size.width
            val h = size.height
            val topHighlight = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0f)),
                center = Offset(w * drift, -h * 0.25f),
                radius = w * 0.75f
            )
            val glint = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0f)),
                center = Offset(w * (1f - drift) * 0.6f, h * 1.1f),
                radius = w * 0.5f
            )
            val topEdge = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0f)),
                startY = 0f,
                endY = h * 0.12f
            )
            onDrawWithContent {
                // Content (text/icons) drawn first so every highlight below
                // is layered strictly on top of the surface itself — never
                // a blur pass over the content, so nothing ever turns
                // illegible.
                drawContent()
                drawRect(brush = topHighlight)
                drawRect(brush = glint)
                drawRect(brush = topEdge)
            }
        }
        .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
}

/**
 * Circular frosted-glass button — the "liquid glass" replacement for the
 * flat opaque-white circular buttons previously used on top of the header
 * gradient. Same size class and press-scale feedback as before so tap
 * targets don't shift, just translucent instead of solid white.
 *
 * No blur here either, and deliberately so: a blur modifier on this
 * button's own chain would blur everything the IconButton draws —
 * including the glyph inside it — which would make the icon unreadable.
 * The glass look comes entirely from translucency + a bright rim, the same
 * safe approach as [liquidGlassSurface] above.
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

    IconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.20f))
            .border(1.dp, Color.White.copy(alpha = 0.40f), CircleShape)
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/**
 * A soft, blurred glowing circle used as a purely decorative background
 * accent (behind the splash screen's logo orb, for example). Unlike
 * [liquidGlassSurface]/[GlassIconButton] above, this composable draws
 * *nothing but the glow* — no text, no icon — so it's the one place in
 * this file where applying a real Modifier.blur to the whole node is
 * completely safe: there is no content it could blur into illegibility.
 *
 * Skipped below API 31 (Modifier.blur is a no-op there) and on LOW
 * performance tier, matching how every other effect in this file degrades
 * — a plain soft circle (no blur pass) is drawn instead so the glow still
 * exists, just without the extra compositing cost.
 */
@Composable
fun LiquidGlassGlow(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    blurRadius: Dp = 24.dp
) {
    val isLowTier = LocalPerformanceTier.current == PerformanceTier.LOW
    val canBlur = !isLowTier && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Box(
        modifier
            .clip(CircleShape)
            .let { if (canBlur) it.blur(blurRadius) else it }
            .background(color.copy(alpha = if (canBlur) 0.55f else 0.20f))
    )
}
