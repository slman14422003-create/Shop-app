package com.shopmanager.app.ui.common

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.PerformanceTier
import com.shopmanager.app.ui.theme.LocalGlassMode
import kotlin.math.roundToInt

/**
 * "زجاج سائل" (liquid glass): the frosted, glossy surface behind every
 * header/top bar and its circular buttons.
 *
 * iOS 26 LIQUID GLASS UPGRADE ("البلور... تحسين وعمق ووضوح"): the panel's
 * soft light patches (the drifting highlight + the corner "droplet"
 * glint) used to be flat, un-blurred radial gradients — cheap and safe,
 * but a hard-edged color patch reads as a sticker painted on top of the
 * glass, not as light actually diffusing *through* a translucent,
 * refractive material. On API 31+ (RenderEffect-backed blur) and
 * STANDARD/HIGH performance tier, those same shapes are now recorded into
 * an offscreen [androidx.compose.ui.graphics.layer.GraphicsLayer] and put
 * through a genuine Gaussian blur ([BlurEffect] — the exact same real
 * blur [LiquidGlassGlow] already used for its standalone decorative orb,
 * now folded into every glass panel's own resting look) before being
 * composited. Paired with a new soft inner shadow hugging the panel's
 * *bottom* inside edge (opposite the existing bright top edge), this is
 * what actually reads as a panel with real thickness — light gathering
 * unevenly through a solid slab of glass — instead of a flat gradient
 * rectangle with a color patch drawn on it.
 *
 * That blur is applied to abstract decorative shapes ONLY, composited in
 * its own separate draw pass — [drawContent] (this surface's real
 * text/icons) always runs first, in its own always-sharp pass, so there
 * is zero risk of the blur ever touching anything the person needs to
 * read. Below API 31 / on LOW tier, the exact same flat, un-blurred
 * gradients from before are drawn instead (see `canRealBlur` below) — no
 * regression there, same graceful degradation as every other effect in
 * this file.
 *
 * STILL AN HONEST LIMITATION: this is a real blur of this panel's own
 * decorative light, not a true "backdrop filter" that samples whatever
 * scrolls *underneath* a translucent bar (the way iOS's frosted nav bars
 * work) — Compose has no first-party API for sampling sibling content
 * that way. Most headers in this app don't actually sit on top of moving
 * content anyway (each is the first LazyColumn item, or a non-overlapping
 * Scaffold topBar), so there's nothing behind them to sample regardless.
 * [FloatingBottomNav] is the one surface that genuinely does float over
 * live scrolling content — it gets the same richer blurred-core treatment
 * as everything else here, just without a literal blurred view of the
 * list rows immediately behind it.
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
    elevation: Dp = 10.dp,
    // "زجاج سائل مذهل": an extra diagonal specular streak that sweeps
    // across the panel on a slow loop, like light catching a curved sheet
    // of glass — on top of the existing drift highlight, not replacing
    // it. Off by default so every existing header keeps today's exact
    // look; the main Dashboard header (the app's single most-seen surface)
    // opts in explicitly below. Degrades the same way as `drift` on LOW
    // tier: a single fixed streak position, no per-frame animation.
    sheen: Boolean = false,
    // BUG FIXED ("في خط مو حلو فوق بالديون" — an ugly seam line across the
    // top of the person-detail screen): PersonDetailScreen used to stack
    // *two independent* liquidGlassSurface panels directly on top of one
    // another — the TopAppBar, then PersonHeader (the "إجمالي الديون"
    // summary) as the very next element. Every screen *other* than that
    // one only ever has a single glass panel for its whole header (see
    // DebtsScreen/MaterialsScreen/DashboardScreen — the header rounds off
    // with `bottomStart`/`bottomEnd` and whatever's below it is a plain,
    // non-glass surface), so this never showed up anywhere else. Two
    // independent panels touching is what actually drew the line — not
    // one effect but three stacking at the exact same seam: (1) this
    // function's own drop shadow (`elevation`, 14.dp by default) reads as
    // a dark line cast by the second panel onto the first, (2) the second
    // panel's own `topEdge` bright highlight — meant to read as "light
    // hitting the top of a pane of glass" — draws a *second*, brighter
    // line at that exact seam since, as far as that panel knows, its top
    // edge IS the top of the glass, and (3) the glass-rim border draws a
    // full-perimeter line, including straight across that same seam.
    // `topFlush = true` is for exactly this "continuation panel sitting
    // directly beneath another glass panel" case: it drops the shadow
    // entirely (nothing should be floating above what's already there),
    // skips the topEdge highlight (this panel's top is not a real top,
    // don't draw one), and skips the rim border (no perimeter line
    // between two panels meant to read as one surface) — so the two
    // panels blend into what looks like a single continuous sheet of
    // glass instead of two stacked slabs with a hard line between them.
    topFlush: Boolean = false,
    // BUG FIXED ("اللمعه ما بدي ياها" — don't want the shine/glare):
    // `topHighlight`, `topEdge`, and `dropletGlint` below were all drawn
    // unconditionally — no caller could turn the glare off, only tune
    // `sheen`/`animated` (the *moving* streak). [GlassAlertDialog] needs
    // exactly that: a flat, true-transparent glass panel with none of
    // this surface's usual gloss. `false` here skips all three static
    // highlight layers; the base gradient + rim border still render, so
    // it still reads as a distinct glass panel — just without any glare.
    highlight: Boolean = true,
    animated: Boolean = false,
    // طلب "تعميم ستايل الزجاج": every header/bottom-nav call used to paint
    // `baseBrush` fully opaque (`.background(baseBrush)`, alpha always 1f) —
    // fine for [GlassAlertDialog] since it bakes its own translucency into
    // the Color stops it hands in as `baseBrush`, but every *other* caller
    // (headers, FloatingBottomNav, GlassIconButton's panel siblings) never
    // had a way to be genuinely see-through at all, only "glare on/off".
    // `baseAlpha` (1f = old behavior, unchanged for every existing caller
    // that doesn't pass it) multiplies on top of `baseBrush`'s own colors —
    // applied to the background fill only (see the `drawBehind` below,
    // which runs *before* `drawContent()`), never to the text/icon
    // children this surface hosts, so turning it down can't wash out
    // readability the way a whole-node `Modifier.alpha` would.
    baseAlpha: Float = 1f
): Modifier {
    val isLowTier = LocalPerformanceTier.current == PerformanceTier.LOW

    // "وضع الجلاس الشفاف الكامل" (AppColorMode.GLASS): the mode where this
    // surface's fill pushes further toward see-through and its drift
    // highlight is free to animate, regardless of the caller's `animated`
    // value — every other color mode (MANUAL/CLASSIC) keeps exactly the
    // calm, static look it already had, since `glassModeActive` is false
    // there. `sheen` is deliberately left to the caller alone (never
    // force-enabled by glass mode): the diagonal streak is a strong,
    // deliberate glare/glint effect, not a general "glass mode" trait —
    // forcing it on every header/bottom-nav panel by default is exactly
    // what read as "لمعة" (unwanted shine) once the bright orbs from
    // `highlight` combined with it. Only surfaces that explicitly opt in
    // (`sheen = true`) get the streak; the rest just get plain, animation-
    // free transparency.
    val glassModeActive = LocalGlassMode.current
    val effectiveAnimated = (animated || glassModeActive) && !isLowTier
    val effectiveSheen = sheen && effectiveAnimated
    val effectiveBaseAlpha = if (glassModeActive) (baseAlpha * 0.78f).coerceIn(0f, 1f) else baseAlpha

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
    //
    // iOS 26: also trimmed from 14.dp to a lighter, native-looking float —
    // real iOS bars sit close to the content with a soft, shallow shadow,
    // never a heavy floating-card drop shadow.
    val effectiveElevation = if (isLowTier || topFlush) 0.dp else elevation

    // iOS 26 LIQUID GLASS UPGRADE: real Gaussian blur is only worth paying
    // for where it's actually visible (`highlight == false` panels, like
    // GlassAlertDialog's flat mode, draw no light patches at all) and only
    // where the hardware/tier can afford it — RenderEffect-backed blur
    // needs API 31 and is skipped on LOW tier exactly like every other
    // per-frame compositing cost in this file (see [LiquidGlassGlow]'s own
    // identical gate). `rememberGraphicsLayer()` is cheap to hold even when
    // unused, but only actually requested when it'll be drawn into below.
    val canRealBlur = highlight && !isLowTier && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val frostedCoreLayer = if (canRealBlur) rememberGraphicsLayer() else null

    // Fixed, calm highlight position when `animated` is false (the new
    // default for every top bar/bottom nav) — still gives the surface a
    // single soft light source like glass catching light from one angle,
    // just without anything looping.
    val drift: Float = if (!effectiveAnimated) {
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

    // BUG FIXED ("الانميشن مو سلسة" — jerky sheen): the streak used to
    // pause dead-still at each end for a beat (`-0.35f at 1800`/`1.35f at
    // 5200`) before suddenly rushing across in the middle of the cycle —
    // a stop-start motion that read as jerky rather than a smooth glide.
    // A single continuous ease across the whole duration (no plateaus)
    // reads as one smooth glide instead — slower and gentler too (5200ms
    // → 6400ms), matching a calmer "liquid glass" feel over an obvious
    // sweep.
    val sheenProgress: Float? = if (effectiveSheen) {
        val transition = rememberInfiniteTransition(label = "liquidGlassSheen")
        val value by transition.animateFloat(
            initialValue = -0.35f,
            targetValue = 1.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(6400, easing = FastOutSlowInEasing),
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
        // BUG FIXED/طلب: was `.background(baseBrush)`, which always painted
        // at full alpha no matter what `baseAlpha` says — a plain
        // `Modifier.background` has no alpha parameter of its own, and
        // wrapping the *whole* node in `Modifier.alpha(baseAlpha)` would
        // have faded the header's text/icons along with it. `drawBehind`
        // draws only this rect, strictly before this node's own children
        // are drawn (never after, never wrapping them), so `alpha` here
        // dims just the glass fill — content on top stays fully legible.
        .drawBehind { drawRect(brush = baseBrush, alpha = effectiveBaseAlpha) }
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
            val frostedCoreSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1))
            // Only built when the real-blur path (below, in onDrawWithContent)
            // is unavailable — the flat, un-blurred fallback exactly as
            // before, so API<31/LOW-tier devices see no change at all.
            val topHighlight = if (highlight && frostedCoreLayer == null) Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0f)),
                center = Offset(w * drift, -h * 0.25f),
                radius = w * 0.75f
            ) else null
            // BUG FIXED ("غير مرتب"): a second radial highlight ("glint")
            // used to drift here too, moving opposite `topHighlight`. Two
            // independently-moving translucent-white patches overlapping on
            // the same small panel is what read as messy/blotchy rather than
            // a single coherent sheet of glass — removed rather than tuned,
            // since `topHighlight` alone already carries the "light drifting
            // across glass" read that this was meant to reinforce.
            val topEdge = if (topFlush || !highlight) null else Brush.verticalGradient(
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
            // BUG FIXED ("بدون لمعة" — no glare): lowered from 0.16 to
            // 0.09 alpha at its brightest point — a faint glide instead of
            // a visible bright streak, since this now also has to coexist
            // with `highlight`'s absence on every surface that uses it.
            val sheenBrush = sheenProgress?.let { progress ->
                val center = w * progress
                val bandWidth = w * 0.22f
                Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Color.White.copy(alpha = 0f),
                        0.5f to Color.White.copy(alpha = 0.09f),
                        1f to Color.White.copy(alpha = 0f)
                    ),
                    start = Offset(center - bandWidth, 0f),
                    end = Offset(center + bandWidth, h)
                )
            }
            // BUG FIXED ("بدي ياه بلوز زجاجي... متل نقطة ماء وبلور" — this
            // panel needs a real "water droplet" glass read, and the same
            // treatment on the top/bottom bars too): `topHighlight` alone is
            // a soft, wide drift with no bright core, so on its own it reads
            // as a faint color wash, not glass catching light. A genuine
            // optical blur/refraction pass isn't something Compose exposes
            // for content drawn elsewhere on screen, but a tight,
            // bright-cored radial highlight fixed in one corner — the way
            // light collects at the curved center of a bead of water and
            // fades sharply outward — is a real, well-established way to
            // fake exactly that "droplet" read cheaply. It's added here, in
            // the one function every glass surface in the app already goes
            // through ([DashboardScreen]'s header, [FloatingBottomNav], and
            // [GlassAlertDialog]), so all three automatically pick up the
            // same "نفس المبدأ" droplet glint without editing each of them
            // separately — and it's unconditional (not gated behind
            // `sheen`/`animated`), so it's part of this surface's resting
            // look everywhere, not an extra opt-in effect.
            val dropletGlint = if (highlight && frostedCoreLayer == null) Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.50f),
                    Color.White.copy(alpha = 0.16f),
                    Color.White.copy(alpha = 0f)
                ),
                center = Offset(w * 0.22f, h * 0.14f),
                radius = w * 0.30f
            ) else null
            // NEW (thickness/depth): a soft, dark gradient hugging just the
            // *inside* bottom edge — the mirror of `topEdge`'s bright rim
            // above. Real glass/acrylic isn't lit evenly all over: the top
            // catches ambient light, the underside falls into its own soft
            // shadow. Pairing a dark bottom edge with the existing bright
            // top edge is what reads as a panel with real thickness rather
            // than a flat, evenly-lit rectangle.
            val innerBaseShadow = if (highlight && !topFlush) Brush.verticalGradient(
                colors = listOf(Color.Black.copy(alpha = 0f), Color.Black.copy(alpha = 0.10f)),
                startY = h * 0.80f,
                endY = h
            ) else null
            onDrawWithContent {
                // Content (text/icons) drawn first so every highlight below
                // is layered strictly on top of the surface itself — never
                // a blur pass over the content, so nothing ever turns
                // illegible.
                drawContent()
                val layer = frostedCoreLayer
                if (layer != null) {
                    // Record just the two abstract light-patch shapes (never
                    // real content — that already finished drawing above, in
                    // its own separate, always-sharp pass) into an offscreen
                    // layer, blur that layer for real, then composite it —
                    // see the function doc for why this is safe.
                    layer.record(this, layoutDirection, frostedCoreSize) {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0f)),
                                center = Offset(w * drift, -h * 0.20f),
                                radius = w * 0.65f
                            )
                        )
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.62f),
                                    Color.White.copy(alpha = 0.20f),
                                    Color.White.copy(alpha = 0f)
                                ),
                                center = Offset(w * 0.22f, h * 0.14f),
                                radius = w * 0.28f
                            )
                        )
                    }
                    // "رقّي شكل الزجاج لأحدث مظهر": 28f → 34f — a slightly
                    // heavier real Gaussian blur on the frosted core so the
                    // drifting highlight/droplet glint reads as light
                    // diffusing through a thicker slab of glass rather than
                    // a tighter, more contained soft patch. Still the same
                    // safe two-pass approach (content drawn sharp first,
                    // this blur only ever touches the decorative shapes).
                    layer.renderEffect = BlurEffect(34f, 34f, TileMode.Decal)
                    drawLayer(layer)
                } else {
                    if (topHighlight != null) drawRect(brush = topHighlight)
                    if (dropletGlint != null) drawRect(brush = dropletGlint)
                }
                if (topEdge != null) drawRect(brush = topEdge)
                if (innerBaseShadow != null) drawRect(brush = innerBaseShadow)
                if (sheenBrush != null) drawRect(brush = sheenBrush)
            }
        }
        .let {
            // Slightly brighter glass rim (0.22 → 0.30) so the edge reads as
            // a distinct rim of light catching the border of the glass/
            // droplet, matching the stronger `dropletGlint` highlight above.
            if (topFlush) it else it.border(1.dp, Color.White.copy(alpha = 0.30f), shape)
        }
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
    // AppColorMode.GLASS pushes every glass element further toward
    // see-through; every other mode keeps the existing 0.16/0.30 values.
    val glassModeActive = LocalGlassMode.current
    val restingFillAlpha = if (glassModeActive) 0.10f else 0.16f
    val restingRimAlpha = if (glassModeActive) 0.38f else 0.30f
    // "رقّي التفاعل عند الضغط": a brief brighten on press — both the fill
    // and rim animate a touch lighter, on the same spring as the scale —
    // so tapping the button reads as light momentarily catching the glass,
    // not just a bare size change. Settles back to the resting values the
    // instant the press ends.
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
            // طلب "تعميم ستايل الزجاج": pushed a bit more see-through
            // (0.20 → 0.16), same direction as the header/bottom-nav
            // panels' new `baseAlpha`, and the rim brought down to the
            // same 0.30 every other glass edge in the app uses (was a
            // brighter 0.40, which read as a heavier ring than the panel
            // border it sits on).
            .background(Color.White.copy(alpha = fillAlpha))
            .border(1.dp, Color.White.copy(alpha = rimAlpha), CircleShape)
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
