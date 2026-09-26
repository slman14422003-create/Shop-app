package com.shopmanager.app.ui.common

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * "تقنية الرجوع بالتطبيق كله... بدي رجوع تنبؤي متل One UI 8.5": one shared
 * controller for EVERY exit-a-screen action in the app — the real edge-
 * swipe gesture AND every screen's own tap-triggered back arrow both
 * animate through this SAME state, so the whole app has exactly one back
 * technique instead of the swipe gesture doing one thing and the button
 * doing another.
 *
 * One UI's own predictive back (Settings, Gallery, ...) doesn't slide one
 * screen out from under another the way the old iOS-style push/pop here
 * did — the CURRENT screen shrinks in place, its corners round off, and
 * it eases a few dp toward whichever edge the gesture came from, like a
 * card lifting and pulling back.
 *
 * UPDATED FOR ONE UI 8.5 ("بدي ياه متل ال one ui 8.5 بكل التطبيق"): One UI
 * 8.5 added "Back Swipe Preview" — the gesture no longer just shrinks the
 * current screen over a blank void, the screen you're returning to visibly
 * surfaces from behind as you drag, so it reads as peeling back a layer
 * rather than the current page shrinking into nothing. [OneUiBackBackdrop]
 * below reproduces that same "something is surfacing from behind" cue as a
 * lightweight synthetic panel layered underneath the shrinking foreground
 * (see its own doc for why it's a synthetic panel and not the literal
 * previous screen). [OneUiBackController.progress]/[oneUiPredictiveBack]
 * still drive the foreground shrink/round/shift exactly as before, and
 * both are driven live by the real system gesture through
 * [PredictiveBackHandler] (so it tracks the finger 1:1, frame by frame,
 * instead of a fixed-duration animation guessing at where the finger is),
 * or by [OneUiBackController.triggerBack] for a plain tap on a back arrow.
 *
 * Because this owns 100% of the pop's visual motion now, MainActivity's
 * NavHost sets `popEnterTransition`/`popExitTransition` to
 * `EnterTransition.None`/`ExitTransition.None` — running NavHost's own pop
 * animation *as well as* this one on top of it is exactly the kind of
 * double, out-of-sync motion that read as "تقطيع" (choppy) before.
 */
class OneUiBackController internal constructor(
    private val navController: NavHostController,
    internal val progressAnimatable: Animatable<Float, AnimationVector1D>,
    private val edgeState: MutableIntState,
    private val scope: CoroutineScope
) {
    /** 0f at rest, 1f at a fully committed or fully cancelled gesture. */
    val progress: Float get() = progressAnimatable.value

    /** Which screen edge the live gesture started from (irrelevant, and left at its last value, for a tap-triggered [triggerBack]). */
    val swipeEdge: Int get() = edgeState.intValue

    /**
     * Plain tap on a screen's own back arrow (TopAppBar navigationIcon,
     * etc.) — the same shrink-round-and-pop the real swipe gesture does,
     * just driven by a short timed animation instead of a live finger, so
     * every "leave this screen" action in the app looks and feels
     * identical.
     */
    fun triggerBack() {
        if (progressAnimatable.isRunning) return
        scope.launch {
            progressAnimatable.animateTo(1f, tween(180, easing = MotionSpecs.claudeEasing))
            // BUG FIXED ("الرجوع بكل الشاشات ترجع بشكل مو حلو ابدا" — the
            // screen landed on after going back would briefly show up
            // shrunk/rounded itself, sitting over the OneUiBackBackdrop
            // panel, before snapping to its normal size): this used to
            // reset progress to 0 AFTER popBackStack(). `progress` is one
            // Animatable SHARED by the whole NavHost's modifier (every
            // screen reads the exact same value) — so for however long it
            // takes the destination screen to actually finish composing
            // and laying out after the pop (a screen with its own data
            // collection/LazyColumn measuring is never truly zero-cost,
            // unlike a trivial composable), it was reading `progress`
            // still sitting at 1 from the shrink that just finished, and
            // rendered shrunk/rounded itself the moment it appeared.
            // Resetting to 0 BEFORE popping instead means the destination
            // screen's very first frame already reads 0, however long its
            // own composition takes — there's no window left where it can
            // read anything else.
            progressAnimatable.snapTo(0f)
            navController.popBackStack()
        }
    }
}

@Composable
fun rememberOneUiBackController(navController: NavHostController): OneUiBackController {
    val progress = remember { Animatable(0f) }
    val edgeState = remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    val scope = rememberCoroutineScope()
    // Only intercept the gesture when there's actually somewhere in our
    // OWN graph to pop back to — at the pager root (nothing pushed yet)
    // the swipe should still exit the app via the system's normal
    // (untouched) predictive-back-to-home animation, not this one.
    val canGoBack by remember { derivedStateOf { navController.previousBackStackEntry != null } }

    PredictiveBackHandler(enabled = canGoBack) { events ->
        try {
            events.collect { event ->
                edgeState.intValue = event.swipeEdge
                progress.snapTo(event.progress)
            }
            // Gesture committed (the flow finished without being
            // cancelled) — finish the shrink the rest of the way, usually
            // already near 1f by the time the system commits it, then
            // reset BEFORE popping (not after — see triggerBack's doc
            // above for why the order matters: it's the exact same shared
            // `progress` value and the exact same bug otherwise).
            progress.animateTo(1f, tween(90, easing = MotionSpecs.claudeEasing))
            progress.snapTo(0f)
            navController.popBackStack()
        } catch (cancellation: CancellationException) {
            // Gesture abandoned mid-swipe — ease back to fully settled
            // instead of snapping, so letting go still feels intentional
            // rather than glitching back into place.
            progress.animateTo(
                0f,
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
            )
        }
    }

    return remember(navController) { OneUiBackController(navController, progress, edgeState, scope) }
}

/**
 * Applies the actual One UI-style transform: shrink slightly, round the
 * corners in as progress increases, ease a few dp toward the edge the
 * gesture started from. Reads [controller]'s progress/edge every frame
 * inside the graphicsLayer draw phase (not via recomposition), so this
 * costs a single re-drawn layer per frame — nothing measures or lays out
 * again.
 */
fun Modifier.oneUiPredictiveBack(controller: OneUiBackController): Modifier =
    this.graphicsLayer {
        val p = controller.progress
        val scale = 1f - 0.12f * p
        scaleX = scale
        scaleY = scale
        val maxShift = 8.dp.toPx()
        translationX = if (controller.swipeEdge == BackEventCompat.EDGE_RIGHT) -maxShift * p else maxShift * p
        val corner = 28.dp.toPx() * p
        shape = RoundedCornerShape(corner)
        clip = p > 0.001f
    }

/**
 * The One UI 8.5 "Back Swipe Preview" layer: sits directly BEHIND whatever
 * carries [oneUiPredictiveBack] (the foreground/current screen) and reads
 * as the destination screen surfacing into view as the gesture progresses
 * — scaling up from a touch smaller, fading in, and easing in from the
 * opposite edge to the foreground's shift, so the two layers visually
 * separate (one receding, one arriving) instead of moving together.
 *
 * This is a synthetic surface-toned panel, not a literal render of the
 * previous screen's real content: doing that faithfully would mean
 * keeping two full NavHost destinations composed and measured at once
 * (a much larger restructure of every screen's navigation entry point),
 * which is out of proportion to what a visual pass on the back gesture
 * calls for. A plain themed panel reproduces the exact same *sensation*
 * — "another layer of the app is right behind this one" — at a fraction
 * of the cost: one extra Box, its transform read live inside a single
 * graphicsLayer (draw-phase only, like [oneUiPredictiveBack] itself), so
 * it never triggers a recomposition of its own as the gesture moves.
 *
 * Usage: place as the first child of the same Box that hosts the
 * [oneUiPredictiveBack]-modified content, so it paints underneath it:
 * ```
 * Box(Modifier.fillMaxSize()) {
 *     OneUiBackBackdrop(oneUiBack)
 *     NavHost(modifier = Modifier.fillMaxSize().oneUiPredictiveBack(oneUiBack), ...)
 * }
 * ```
 */
@Composable
fun OneUiBackBackdrop(controller: OneUiBackController, modifier: Modifier = Modifier) {
    val backdropColor = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = controller.progress
                val scale = 0.94f + 0.06f * p
                scaleX = scale
                scaleY = scale
                alpha = p
                val maxShift = 8.dp.toPx()
                // Opposite direction to the foreground's shift (see
                // oneUiPredictiveBack above) — the two layers separate as
                // they move instead of travelling together, which is what
                // actually sells "one surfacing from behind the other"
                // rather than "the same image at two sizes".
                translationX = if (controller.swipeEdge == BackEventCompat.EDGE_RIGHT) maxShift * (1f - p) else -maxShift * (1f - p)
                val corner = 28.dp.toPx() * p
                shape = RoundedCornerShape(corner)
                clip = true
            }
            .background(backdropColor)
    )
}
