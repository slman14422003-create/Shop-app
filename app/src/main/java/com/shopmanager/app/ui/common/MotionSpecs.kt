package com.shopmanager.app.ui.common

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
 * REPLACED WITH CLAUDE.AI-STYLE MOTION ("ازل جميع الانميشن واضف انميشن نمط
 * تطبيق كلاود ai"): every spec here used to be a bouncy spring
 * (DampingRatioMediumBouncy/LowBouncy) that visibly overshot and settled
 * back — a lively, "iOS 26" feel, but not what the reference Claude.ai
 * design actually does. Claude's own UI never overshoots: every transition
 * (a panel opening, a control responding to a tap) is a short,
 * critically-damped ease-out — it moves briskly and stops exactly where
 * it's going, with zero wobble. [claudeEasing] is that same curve (a
 * fast-start/gentle-stop "ease-out expo" shape) used everywhere a
 * duration-based tween is needed, and every spring below is now
 * [Spring.DampingRatioNoBouncy] — only the stiffness (how quickly it gets
 * there) still varies by call site and by performance tier:
 *
 * - LOW ("الوضع الاقتصادي"): [Spring.StiffnessHigh] — settles in a couple
 *   of frames, as close to an instant snap as a spring gets — and every
 *   duration-based effect drops to a fraction of its normal length, so
 *   battery/CPU cost stays minimal without the UI going fully static.
 * - STANDARD/HIGH ("وضع الأداء العالي"): quick but perceptible, so a tap,
 *   reorder or expand still visibly responds — just without any bounce.
 */
object MotionSpecs {

    /** Claude.ai's own transition curve — quick to start, gently easing to
     * a dead stop with no overshoot. Used for every tween in this object. */
    val claudeEasing: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    @Composable
    private fun isLowTier(): Boolean = LocalPerformanceTier.current == PerformanceTier.LOW

    /** Button/row press scale-down feedback, and any other quick single-value
     * spring (color/dp highlight, etc.) that should track the same feel. */
    @Composable
    fun <T> quickSpring(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = if (isLowTier()) Spring.StiffnessHigh else Spring.StiffnessMediumLow * 2.2f
    )

    /** Button/row press scale-down feedback. */
    @Composable
    fun pressSpring(): FiniteAnimationSpec<Float> = quickSpring()

    /** List-item reorder/insert/remove placement (LazyColumn animateItem's placementSpec). */
    @Composable
    fun reorderSpring(): FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = if (isLowTier()) Spring.StiffnessHigh else Spring.StiffnessMediumLow * 1.6f
    )

    /** expandVertically/shrinkVertically size animation. */
    @Composable
    fun expandSpring(): FiniteAnimationSpec<IntSize> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = if (isLowTier()) Spring.StiffnessHigh else Spring.StiffnessMediumLow
    )

    /**
     * تحسين أنيميشن: اختفاء الصف عند حذفه/تسديده (fadeOutSpec في animateItem).
     * يخفت لحظة بينما تنزلق الباقية إلى مكانها بنفس نابض الترتيب
     * [reorderSpring] — أقصر بكثير على الأجهزة الضعيفة (60ms) كبقية حركات
     * هذا الملف، وبمنحنى Claude نفسه [claudeEasing].
     */
    @Composable
    fun listItemFadeOut(): FiniteAnimationSpec<Float> =
        tween(durationMillis = fadeMillis(), easing = claudeEasing)

    @Composable
    fun expandMillis(): Int = if (isLowTier()) 90 else 200

    @Composable
    fun collapseMillis(): Int = if (isLowTier()) 70 else 160

    @Composable
    fun fadeMillis(): Int = if (isLowTier()) 60 else 140

    /**
     * "Pop in" for anything that appears on top of existing content
     * without pushing it (dialogs, one-off banners like the server-outage
     * restore prompt, snackbars). Previously a springy overshoot; now a
     * flat, no-bounce ease-out — it arrives and settles immediately,
     * matching how Claude's own modals/toasts appear with no wobble.
     */
    @Composable
    fun popInSpring(): FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = if (isLowTier()) Spring.StiffnessHigh else Spring.StiffnessMedium
    )

    /**
     * The floating bottom nav's sliding selection highlight (tab → tab).
     * A quick, no-bounce ease so the indicator moves to the tapped tab and
     * stops cleanly instead of wobbling past it.
     */
    @Composable
    fun tabIndicatorSpring(): FiniteAnimationSpec<androidx.compose.ui.unit.Dp> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = if (isLowTier()) Spring.StiffnessHigh else Spring.StiffnessMedium
    )

    /**
     * Smooth content fade+slide for anything that swaps in place (a
     * status line changing, a list of local backups loading in) — uses
     * [claudeEasing], the same curve every other transition in the app now
     * shares, so nothing reads as "off-brand" next to it.
     */
    @Composable
    fun contentTween(): FiniteAnimationSpec<Float> =
        tween(durationMillis = if (isLowTier()) 90 else 220, easing = claudeEasing)
}
