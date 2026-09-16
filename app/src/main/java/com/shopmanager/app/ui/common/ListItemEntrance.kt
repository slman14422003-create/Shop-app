package com.shopmanager.app.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.PerformanceTier
import kotlinx.coroutines.delay

/**
 * "ما احس انو في انيميشن" (on capable/"strong" devices the UI feels
 * static): every existing motion in this app is a *reaction* to input —
 * a press, a swipe, a value changing — but a list of rows just appearing
 * fully-formed the instant its data loads has no motion of its own at
 * all, which is exactly what reads as "there's no animation here" even on
 * a phone that could easily afford one. This is the missing "arrival"
 * motion: each row fades in and rises a short distance into place, so a
 * freshly-loaded/refreshed list visibly settles into view row by row
 * instead of popping in as one flat block.
 *
 * STAGGER: `index` offsets each row's start by a small, capped delay so
 * the motion reads as a single wave sweeping down the list rather than
 * every row moving in lockstep — capped at 8 rows' worth of delay so a
 * long list's *visible* rows all finish settling quickly instead of the
 * 40th row waiting a full second for its turn.
 *
 * PERF (LOW tier / this app's central performance switch — see
 * [com.shopmanager.app.data.performance.DevicePerformance]): returns
 * `this` completely unchanged on LOW tier — no [Animatable], no
 * [LaunchedEffect], no extra composable state at all, not even one that
 * finishes instantly. That's deliberate: unlike this app's other motion
 * (which degrades to a near-instant snap on LOW — see [MotionSpecs]),
 * *this* effect fires once per row every time a list is scrolled/loaded,
 * so on a weak device even a "free" near-zero-duration animation still
 * means one extra coroutine + Animatable allocation per row composed.
 * Skipping it outright is what keeps a long list's first render exactly
 * as cheap on LOW tier as it was before this existed.
 */
@Composable
fun Modifier.listItemEntrance(index: Int): Modifier {
    val isLowTier = LocalPerformanceTier.current == PerformanceTier.LOW
    if (isLowTier) return this

    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        val staggerMs = (index.coerceAtMost(8)) * 28L
        if (staggerMs > 0) delay(staggerMs)
        progress.animateTo(1f, animationSpec = tween(360, easing = FastOutSlowInEasing))
    }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 16.dp.toPx()
    }
}
