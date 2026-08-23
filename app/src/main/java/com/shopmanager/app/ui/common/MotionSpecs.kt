package com.shopmanager.app.ui.common

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.PerformanceTier

/**
 * Single place that turns "تفضيل الأداء" (Settings → الأداء) into the
 * actual animation specs every small interactive motion in the app uses —
 * button-press scale, list-reorder, expand/collapse.
 *
 * BUG FIXED (تفضيل الأداء didn't reach most animations): only a few spots
 * — the nav-transition fade, the dashboard totals counter, the header
 * gradient — actually read [LocalPerformanceTier]. Every press-scale
 * spring, list-reorder spring, and expand/collapse tween was hardcoded
 * with its own fixed spec, so picking "منخفض" in Settings sped up almost
 * nothing you'd notice while actually tapping around, and picking "مرتفع"
 * didn't add anything beyond what STANDARD already did by default — it
 * just looked identical, which read as effects being "missing". Every
 * call site below now asks this object instead, so:
 *
 * - LOW: every spring settles at [Spring.StiffnessHigh] with no bounce —
 *   about as close to an instant snap as a spring animation gets — and
 *   every duration-based effect drops to a fraction of its normal length.
 *   This is deliberately snappier than STANDARD, not just "no animation",
 *   so the UI still feels alive rather than dead/static.
 * - STANDARD/HIGH: noticeably livelier than the old hardcoded specs —
 *   higher stiffness than Compose's spring() default (StiffnessMedium)
 *   paired with a touch of bounce, so a tap/reorder/expand visibly
 *   responds and settles quickly instead of feeling like it's catching up
 *   a beat behind the input.
 */
object MotionSpecs {

    @Composable
    private fun isLowTier(): Boolean = LocalPerformanceTier.current == PerformanceTier.LOW

    /** Button/row press scale-down feedback, and any other quick single-value
     * spring (color/dp highlight, etc.) that should track the same feel. */
    @Composable
    fun <T> quickSpring(): FiniteAnimationSpec<T> = if (isLowTier()) {
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
    } else {
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow * 3f)
    }

    /** Button/row press scale-down feedback. */
    @Composable
    fun pressSpring(): FiniteAnimationSpec<Float> = quickSpring()

    /** List-item reorder/insert/remove placement (LazyColumn animateItemPlacement). */
    @Composable
    fun reorderSpring(): FiniteAnimationSpec<IntOffset> = if (isLowTier()) {
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
    } else {
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow * 2f)
    }

    /** expandVertically/shrinkVertically size animation. */
    @Composable
    fun expandSpring(): FiniteAnimationSpec<IntSize> = if (isLowTier()) {
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
    } else {
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
    }

    /** Expand/collapse duration in ms for AnimatedVisibility-style effects. */
    @Composable
    fun expandMillis(): Int = if (isLowTier()) 90 else 220

    @Composable
    fun collapseMillis(): Int = if (isLowTier()) 70 else 180

    @Composable
    fun fadeMillis(): Int = if (isLowTier()) 60 else 150
}
