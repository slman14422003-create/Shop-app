package com.shopmanager.app.ui.common

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * card lifting and pulling back, with nothing sliding in from off-screen.
 * That's exactly what [OneUiBackController.progress]/[oneUiPredictiveBack]
 * reproduce, driven live by the real system gesture through
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
            navController.popBackStack()
            progressAnimatable.snapTo(0f)
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
            // actually pop, then reset for whatever screen is now on top.
            progress.animateTo(1f, tween(90, easing = MotionSpecs.claudeEasing))
            navController.popBackStack()
            progress.snapTo(0f)
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
