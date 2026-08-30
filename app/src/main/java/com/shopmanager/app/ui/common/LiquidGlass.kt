package com.shopmanager.app.ui.common

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
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
import androidx.compose.ui.draw.shadow
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
    baseBrush: Brush = BrandGradient.brush(),
    // "عائم" (floating, One UI 8.5-style): a soft drop shadow under the
    // panel so it reads as a distinct floating glass layer above the
    // content behind it, instead of a flat bar glued to the screen edge.
    // 0.dp keeps the previous flush look for callers that still want it
    // (e.g. a bar that's meant to sit flat against another surface).
    elevation: Dp = 14.dp,
    // "زجاج سائل مذهل": an extra diagonal specular streak that sweeps
    // across the panel on a slow loop, like light catching a curved sheet
    // of glass — on top of the existing drift highlight, not replacing
    // it. Off by default so every existing header keeps today's exact
    // look; the main Dashboard header (the app's single most-seen surface)
    // opts in explicitly below. Degrades the same way as `drift` on LOW
    // tier: a single fixed streak position, no per-frame animation.
    sheen: Boolean = false
): Modifier {
    val isLowTier = LocalPerformanceTier.current == PerformanceTier.LOW

    // PERF (low-end tier): Modifier.shadow forces its own offscreen
    // graphicsLayer + a blur pass every frame it's on screen — on a weak
    // GPU/driver stack that's real, measurable frame time on every single
    // glass surface (header, floating nav, admin/person-detail panels),
    // stacking on top of everything else already competing for that
    // budget. Every other effect in this function already degrades for
    // LOW tier (drift, sheen); the shadow was the one still paid in full
    // regardless of tier. LOW tier now skips it entirely — the glass
    // panel itself (gradient + border) still reads clearly as its own
    // surface without the drop shadow.
    val effectiveElevation = if (isLowTier) 0.dp else elevation

    // BUG FIXED ("انميشن زجاج يلمع غير مرتب"): LinearEasing here meant the
    // highlight moved at constant speed and instantly reversed direction at
    // both ends every 7s — a mechanical back-and-forth "tick" rather than
    // anything reading as liquid. FastOutSlowInEasing decelerates into each
    // turnaround and accelerates back out, the same way real light drifting
    // across a curved surface would.
    val drift: Float = if (isLowTier) {
        0.28f
    } else {
        val transition = rememberInfiniteTransition(label = "liquidGlassDrift")
        val value by transition.animateFloat(
            initialValue = 0.08f,
            targetValue = 0.92f,
            animationSpec = infiniteRepeatable(tween(7000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "liquidGlassDriftValue"
        )
        value
    }

    // BUG FIXED ("غير مرتب وغير جميل"): this used to run a fast (3.2s),
    // constant-speed, non-stop sweep — restarting the instant it finished,
    // which reads as a restless flicker rather than a deliberate accent.
    // Real "light catching glass" is a single, occasional sweep: a pause,
    // then one smooth eased pass, then a longer pause before it repeats —
    // so it draws the eye once and then gets out of the way instead of
    // competing with `drift` for attention the whole time it's on screen.
    val sheenProgress: Float? = if (sheen && !isLowTier) {
        val transition = rememberInfiniteTransition(label = "liquidGlassSheen")
        val value by transition.animateFloat(
            initialValue = -0.35f,
            targetValue = 1.35f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 5200
                    -0.35f at 0
                    -0.35f at 1800 using FastOutSlowInEasing
                    1.35f at 4200 using FastOutSlowInEasing
                    1.35f at 5200
                },
                repeatMode = RepeatMode.Restart
            ),
            label = "liquidGlassSheenValue"
        )
        value
    } else null

    return this
        .let {
            if (effectiveElevation > 0.dp) it.shadow(effectiveElevation, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.35f))
            else it
        }
        .clip(shape)
        .background(baseBrush)
        // PERF: drawWithCache (not drawWithContent) so the Brush objects
        // below are only rebuilt when `drift`/`sheenProgress` or the
        // surface's `size` actually change — not on every recomposition. On
        // STANDARD/HIGH tier drift changes every animation frame anyway, so
        // this is a wash there; on LOW tier drift is a fixed constant (see
        // above), so this is where it actually pays off: a header sitting
        // behind a live Firestore-backed list (which can recompose often on
        // any data change, unrelated to the header itself) reallocates
        // these gradient Brushes on every one of those passes without this,
        // and zero extra times with it.
        .drawWithCache {
            val w = size.width
            val h = size.height
            val topHighlight = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0f)),
                center = Offset(w * drift, -h * 0.25f),
                radius = w * 0.75f
            )
            // BUG FIXED ("غير مرتب"): a second radial highlight ("glint")
            // used to drift here too, moving opposite `topHighlight`. Two
            // independently-moving translucent-white patches overlapping on
            // the same small panel is what read as messy/blotchy rather than
            // a single coherent sheet of glass — removed rather than tuned,
            // since `topHighlight` alone already carries the "light drifting
            // across glass" read that this was meant to reinforce.
            val topEdge = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0f)),
                startY = 0f,
                endY = h * 0.12f
            )
            // A narrow, angled band of light — three stops (transparent →
            // bright → transparent) offset diagonally by `progress` so it
            // reads as a single streak of light gliding across the panel,
            // the same way a phone screen's reflection moves across a
            // curved glass surface when it tilts. `null` (LOW tier / sheen
            // not requested) skips building this brush entirely.
            val sheenBrush = sheenProgress?.let { progress ->
                val center = w * progress
                val bandWidth = w * 0.22f
                Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Color.White.copy(alpha = 0f),
                        0.5f to Color.White.copy(alpha = 0.16f),
                        1f to Color.White.copy(alpha = 0f)
                    ),
                    start = Offset(center - bandWidth, 0f),
                    end = Offset(center + bandWidth, h)
                )
            }
            onDrawWithContent {
                // Content (text/icons) drawn first so every highlight below
                // is layered strictly on top of the surface itself — never
                // a blur pass over the content, so nothing ever turns
                // illegible.
                drawContent()
                drawRect(brush = topHighlight)
                drawRect(brush = topEdge)
                if (sheenBrush != null) drawRect(brush = sheenBrush)
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
