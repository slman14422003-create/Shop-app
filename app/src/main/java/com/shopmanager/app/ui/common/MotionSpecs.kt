package com.shopmanager.app.ui.common

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.PerformanceTier
import com.shopmanager.app.data.performance.LocalRefreshRateHz

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
 * - LOW ("الوضع الاقتصادي"): بلا نوابض إطلاقاً — كل حركة تفاعلية (ضغط، ترتيب، توسيع) تقفز فوراً [snap]؛ سابقاً [Spring.StiffnessHigh] — settles in a couple
 *   of frames, as close to an instant snap as a spring gets — and every
 *   duration-based effect drops to a fraction of its normal length, so
 *   battery/CPU cost stays minimal without the UI going fully static.
 * - BALANCED ("المتوازن"): نوابض أصلب قليلاً ومدد أقصر ~15% — ناعمة لكن بعمل رسومي أقل.
 * - STANDARD/HIGH ("وضع الأداء العالي"): quick but perceptible, so a tap,
 *   reorder or expand still visibly responds — just without any bounce.
 */
object MotionSpecs {

    /** Claude.ai's own transition curve — quick to start, gently easing to
     * a dead stop with no overshoot. Used for every tween in this object. */
    val claudeEasing: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    @Composable
    private fun isLowTier(): Boolean = LocalPerformanceTier.current == PerformanceTier.LOW

    @Composable
    private fun isBalancedTier(): Boolean = LocalPerformanceTier.current == PerformanceTier.BALANCED

    /** المتوازن: نوابض أصلب قليلاً من القوي (تستقر بإطارات أقل = عمل رسومي أقل) لكنها
     * ما تزال ناعمة، بلا ارتداد. القوي يبقى بالأزمنة الأنعم الأصلية. */
    @Composable
    private fun tierStiffness(high: Float, balanced: Float): Float = when {
        isLowTier() -> Spring.StiffnessHigh
        isBalancedTier() -> balanced
        else -> high
    }

    /** مضروب مدد الـ tween: القوي 1، المتوازن ~0.85 (أقصر قليلاً)، والاقتصادي له قيم ثابتة خاصة. */
    @Composable
    fun tierDurationScale(): Float = if (isBalancedTier()) 0.85f * durationScale() else durationScale()

    /** شاشة 60Hz: نرفع صلابة النوابض ونقصّر المدد ~20% فتصل الحركة لهدفها بإطارات أقل
     * ويبدو التطبيق أسرع استجابة ("كأنه 90Hz")؛ على 90Hz+ تبقى الأزمنة الأنعم الأصلية. */
    @Composable
    private fun snap(): Float = if (LocalRefreshRateHz.current < 80f) 1.3f else 1f

    @Composable
    fun durationScale(): Float = if (LocalRefreshRateHz.current < 80f) 0.8f else 1f

    /** Button/row press scale-down feedback, and any other quick single-value
     * spring (color/dp highlight, etc.) that should track the same feel. */
    @Composable
    fun <T> quickSpring(): FiniteAnimationSpec<T> = if (isLowTier()) snap<T>() else spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = tierStiffness(Spring.StiffnessMediumLow * 2.2f * snap(), Spring.StiffnessMedium * 1.6f * snap())
    )

    /** Button/row press scale-down feedback. */
    @Composable
    fun pressSpring(): FiniteAnimationSpec<Float> = quickSpring()

    /** List-item reorder/insert/remove placement (LazyColumn animateItem's placementSpec). */
    @Composable
    fun reorderSpring(): FiniteAnimationSpec<IntOffset> = if (isLowTier()) snap<IntOffset>() else spring<IntOffset>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = tierStiffness(Spring.StiffnessMediumLow * 1.6f * snap(), Spring.StiffnessMedium * 1.2f * snap())
    )

    /** expandVertically/shrinkVertically size animation. */
    @Composable
    fun expandSpring(): FiniteAnimationSpec<IntSize> = if (isLowTier()) snap<IntSize>() else spring<IntSize>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = tierStiffness(Spring.StiffnessMediumLow * snap(), Spring.StiffnessMedium * snap())
    )

    /**
     * تحسين أنيميشن: اختفاء الصف عند حذفه/تسديده (fadeOutSpec في animateItem).
     * يخفت لحظة بينما تنزلق الباقية إلى مكانها بنفس نابض الترتيب
     * [reorderSpring] — أقصر بكثير على الأجهزة الضعيفة (60ms) كبقية حركات
     * هذا الملف، وبمنحنى Claude نفسه [claudeEasing].
     */
    @Composable
    fun listItemFadeOut(): FiniteAnimationSpec<Float> =
        if (isLowTier()) snap<Float>() else tween<Float>(durationMillis = fadeMillis(), easing = claudeEasing)

    @Composable
    fun expandMillis(): Int = if (isLowTier()) 90 else (200 * tierDurationScale()).toInt()

    @Composable
    fun collapseMillis(): Int = if (isLowTier()) 70 else (160 * tierDurationScale()).toInt()

    @Composable
    fun fadeMillis(): Int = if (isLowTier()) 60 else (140 * tierDurationScale()).toInt()

    /**
     * "Pop in" for anything that appears on top of existing content
     * without pushing it (dialogs, one-off banners like the server-outage
     * restore prompt, snackbars). Previously a springy overshoot; now a
     * flat, no-bounce ease-out — it arrives and settles immediately,
     * matching how Claude's own modals/toasts appear with no wobble.
     */
    @Composable
    fun popInSpring(): FiniteAnimationSpec<Float> = if (isLowTier()) snap<Float>() else spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = tierStiffness(Spring.StiffnessMedium * snap(), Spring.StiffnessMedium * 1.3f * snap())
    )

    /**
     * The floating bottom nav's sliding selection highlight (tab → tab).
     * A quick, no-bounce ease so the indicator moves to the tapped tab and
     * stops cleanly instead of wobbling past it.
     */
    @Composable
    fun tabIndicatorSpring(): FiniteAnimationSpec<androidx.compose.ui.unit.Dp> = if (isLowTier()) snap<androidx.compose.ui.unit.Dp>() else spring<androidx.compose.ui.unit.Dp>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = tierStiffness(Spring.StiffnessMedium * snap(), Spring.StiffnessMedium * 1.3f * snap())
    )

    /**
     * Smooth content fade+slide for anything that swaps in place (a
     * status line changing, a list of local backups loading in) — uses
     * [claudeEasing], the same curve every other transition in the app now
     * shares, so nothing reads as "off-brand" next to it.
     */
    @Composable
    fun <T> contentTween(): FiniteAnimationSpec<T> =
        tween(durationMillis = if (isLowTier()) 90 else (220 * tierDurationScale()).toInt(), easing = claudeEasing)

    // ───────────────────────── التنقل بين الشاشات ─────────────────────────
    // قاعدة المستويات الثلاثة (طلب: "متوازن بحيث الانميشن ما تستهلك الجرافك"):
    //  - STANDARD (قوي): انزلاق + تصغير الشاشة المغطّاة (94%) — أغنى حركة.
    //  - BALANCED: انزلاق + تلاشٍ فقط، بدون scale للشاشة كاملة (تصغير شاشة
    //    كاملة بكل بطاقاتها كل إطار هو أغلى جزء رسومياً)، وبمدة أقصر.
    //  - LOW: تلاشٍ بسيط قصير جداً فقط (بلا انزلاق ولا scale).

    /** مدة انتقال دفع/سحب الشاشة (ms). */
    @Composable
    fun navMillis(): Int = when {
        isLowTier() -> 110
        isBalancedTier() -> (260 * durationScale()).toInt()
        else -> (300 * durationScale()).toInt()
    }

    /** مدة انتقال التبويبات (الـ pager) بالـ ms؛ 0 = بدون حركة (غير مستعمل حالياً: الاقتصادي يتلاشى). */
    @Composable
    fun pagerMillis(): Int = when {
        isLowTier() -> 0
        isBalancedTier() -> (240 * durationScale()).toInt()
        else -> (300 * durationScale()).toInt()
    }

    /** مدة الانتقال من السبلاش إلى التطبيق. */
    @Composable
    fun handoffMillis(): Int = when {
        isLowTier() -> 90
        isBalancedTier() -> 220
        else -> 360
    }
}
