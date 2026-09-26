package com.shopmanager.app.ui.common

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigation.NavHostController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * "اتجاه الخروج مو منسق متل اتجاه الدخول... بتصميم Claude.ai": one shared
 * controller for EVERY exit-a-screen action in the app — the real edge-
 * swipe gesture AND every screen's own tap-triggered back arrow both
 * animate through this SAME state, so the whole app has exactly one back
 * technique instead of the swipe gesture doing one thing and the button
 * doing another.
 *
 * BUG FIXED — this used to be a completely different motion *language*
 * from the forward push in MainActivity's NavHost: pushing a screen slides
 * it in from the right while the screen underneath slides a third of the
 * way left and scales to 94% (see `pushSlideSpec`/`pushScaleSpec` there),
 * but going back used to shrink the current screen in place and round its
 * corners off — a One UI-style "card lifting back" effect that shares no
 * axis, no distance, and no shape with how the screen arrived. Visually
 * that reads exactly as "the exit direction doesn't match the entrance
 * direction", because it doesn't: it's not even the same kind of motion.
 *
 * Rebuilt so going back is the EXACT reverse of coming forward, on the
 * same axis, same distances, same easing — i.e. literally rewind the push:
 *  - [oneUiPredictiveBack] (the screen being left) now slides out to the
 *    right the same [size.width] it slid in from — the mirror image of the
 *    push's enterTransition.
 *  - [OneUiBackBackdrop] (the screen being revealed) now slides in from a
 *    third of the way off to the left back to its resting position while
 *    scaling from 94% back up to 100% — the mirror image of the push's
 *    exitTransition.
 * Play both at once, driven by the same `progress` 0→1, and the two
 * screens trade places along the exact path they'd take forward, just
 * backward — which is what "coordinated" entrance/exit actually means.
 *
 * Because this owns 100% of the pop's visual motion, MainActivity's
 * NavHost sets `popEnterTransition`/`popExitTransition` to
 * `EnterTransition.None`/`ExitTransition.None` — running NavHost's own pop
 * animation *as well as* this one on top of it is exactly the kind of
 * double, out-of-sync motion that read as "تقطيع" (choppy) before.
 */
class OneUiBackController internal constructor(
    private val navController: NavHostController,
    internal val progressAnimatable: Animatable<Float, AnimationVector1D>,
    private val scope: CoroutineScope
) {
    /** 0f at rest, 1f at a fully committed or fully cancelled gesture. */
    val progress: Float get() = progressAnimatable.value

    /**
     * Plain tap on a screen's own back arrow (TopAppBar navigationIcon,
     * etc.) — the same reverse-the-push slide the real swipe gesture does,
     * just driven by a short timed animation instead of a live finger, so
     * every "leave this screen" action in the app looks and feels
     * identical.
     */
    fun triggerBack() {
        if (progressAnimatable.isRunning) return
        scope.launch {
            progressAnimatable.animateTo(1f, tween(300, easing = MotionSpecs.claudeEasing))
            // The screen landed on after going back must never show up
            // mid-slide itself: `progress` is one Animatable SHARED by the
            // whole NavHost's modifier (every screen reads the exact same
            // value), so it's reset to 0 BEFORE popping — the destination
            // screen's very first frame already reads 0, however long its
            // own composition takes, instead of briefly inheriting
            // whatever the just-finished slide left `progress` sitting at.
            progressAnimatable.snapTo(0f)
            navController.popBackStack()
        }
    }
}

@Composable
fun rememberOneUiBackController(navController: NavHostController): OneUiBackController {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    // Only intercept the gesture when there's actually somewhere in our
    // OWN graph to pop back to — at the pager root (nothing pushed yet)
    // the swipe should still exit the app via the system's normal
    // (untouched) predictive-back-to-home animation, not this one.
    val canGoBack by remember { derivedStateOf { navController.previousBackStackEntry != null } }

    PredictiveBackHandler(enabled = canGoBack) { events ->
        try {
            events.collect { event ->
                progress.snapTo(event.progress)
            }
            // Gesture committed (the flow finished without being
            // cancelled) — finish the slide the rest of the way, usually
            // already near 1f by the time the system commits it, then
            // reset BEFORE popping (not after — see triggerBack's doc
            // above for why the order matters: it's the exact same shared
            // `progress` value and the exact same bug otherwise).
            progress.animateTo(1f, tween(150, easing = MotionSpecs.claudeEasing))
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

    return remember(navController) { OneUiBackController(navController, progress, scope) }
}

/**
 * The screen being left, sliding back out to the right — the mirror image
 * of `enterTransition`'s `slideInHorizontally { fullWidth -> fullWidth }`
 * in MainActivity's NavHost. At progress 0 it sits at rest (translationX
 * 0); at progress 1 it has slid one full [size.width] off to the right,
 * exactly retracing the path it slid in on when it was first pushed. Reads
 * [controller]'s progress every frame inside the graphicsLayer draw phase
 * (not via recomposition), so this costs a single re-drawn layer per
 * frame — nothing measures or lays out again.
 */
fun Modifier.oneUiPredictiveBack(controller: OneUiBackController): Modifier =
    this.graphicsLayer {
        translationX = size.width * controller.progress
    }

/**
 * The screen being revealed, sliding back in from a third of the way off
 * to the left while scaling from 94% up to 100% — the mirror image of
 * `exitTransition`'s `slideOutHorizontally { fullWidth -> -fullWidth / 3 }
 * + scaleOut(targetScale = 0.94f)` in MainActivity's NavHost. At progress
 * 0 it sits exactly where the push left it (a third of [size.width] off to
 * the left, 94% scale) — precisely matching the resting look of whatever
 * is now underneath; at progress 1 it has eased all the way back to full
 * size and position, ready to become the foreground the instant the pop
 * completes.
 *
 * This is a synthetic surface-toned panel, not a literal render of the
 * previous screen's real content: doing that faithfully would mean
 * keeping two full NavHost destinations composed and measured at once (a
 * much larger restructure of every screen's navigation entry point),
 * which is out of proportion to what a visual pass on the back gesture
 * calls for. A plain themed panel reproduces the same sensation —
 * "the previous screen is sliding back into place behind this one" — at a
 * fraction of the cost: one extra Box, its transform read live inside a
 * single graphicsLayer (draw-phase only, like [oneUiPredictiveBack]
 * itself), so it never triggers a recomposition of its own as the gesture
 * moves.
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
                translationX = -(size.width / 3f) * (1f - p)
            }
            .background(backdropColor)
    )
}
