package com.shopmanager.app.ui.common

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.LocalRefreshRateHz
import com.shopmanager.app.data.performance.PerformanceTier
import kotlinx.coroutines.delay

/*
 * منظومة الحركة والتأثيرات البصرية الموحّدة — ملف واحد:
 *   1) [MotionSpecs]: كل مدد/نوابض الحركة التفاعلية والتنقل حسب [PerformanceTier] ومعدل التحديث.
 *   2) [collectDelayedPressedAsState]: أثر الضغط المتأخر (لا يُرسم أثناء بداية التمرير).
 *   3) [sheenSweep] / [glowBehind]: تأثيرات للأداء القوي فقط.
 * كشف قدرة الجهاز نفسه في data/performance/DevicePerformance.kt.
 */

/**
 * المكان الوحيد الذي يحوّل "تفضيل الأداء" (الإعدادات ← الأداء) إلى مواصفات الحركة
 * الفعلية: ضغط الأزرار، ترتيب القوائم، التوسيع/الطي، الظهور المنبثق، التنقل.
 *
 * حركة بأسلوب Claude.ai: بلا ارتداد إطلاقاً — كل انتقال easing قصير ينتهي بسكون تام
 * ([claudeEasing])، وكل نابض هنا [Spring.DampingRatioNoBouncy] (باستثناء popInSpring
 * في القوي بارتداد خفيف جداً). المستوى يغيّر الصلابة والمدد فقط:
 *
 * - LOW (الاقتصادي): بلا نوابض — كل حركة تفاعلية tween قصير جداً (مضاعفات 25ms = إطار @40Hz).
 * - BALANCED (المتوازن): tween بثلاث مدد موحّدة فقط (120/180/240ms) وبمنحنى واحد،
 *   فتتزامن الحركات وتستقر معاً بإيقاع واحد وبعمل رسومي أقل.
 * - STANDARD (القوي): نوابض سريعة لكنها محسوسة، بلا ارتداد.
 */
object MotionSpecs {

    /** منحنى Claude.ai: بداية سريعة وتباطؤ ناعم حتى سكون تام بلا ارتداد. */
    val claudeEasing: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    // ───────────────────────── رموز الزمن الموحّدة (المتوازن) ─────────────────────────
    // كل حركة في المتوازن تأخذ واحدة فقط من ثلاث مدد: سريعة (تفاعل لحظي)، أساسية
    // (توسيع/تبديل)، بطيئة (دخول/انتقال).
    const val balancedFastMs = 120
    const val balancedBaseMs = 180
    const val balancedSlowMs = 240

    @Composable
    private fun isBalancedTier(): Boolean = LocalPerformanceTier.current == PerformanceTier.BALANCED

    /** شاشة 60Hz: نرفع صلابة النوابض ~30% ونقصّر المدد ~20% فتصل الحركة لهدفها بإطارات أقل
     * ويبدو التطبيق أسرع استجابة ("كأنه 90Hz")؛ على 80Hz+ تبقى الأزمنة الأنعم الأصلية. */
    @Composable
    private fun snap(): Float = if (LocalRefreshRateHz.current < 80f) 1.3f else 1f

    @Composable
    fun durationScale(): Float = if (LocalRefreshRateHz.current < 80f) 0.8f else 1f

    /** مضروب مدد الـ tween: القوي 1، المتوازن ~0.85 (أقصر قليلاً). */
    @Composable
    fun tierDurationScale(): Float = if (isBalancedTier()) 0.85f * durationScale() else durationScale()

    // ───────────────────────── الحركات التفاعلية الصغيرة ─────────────────────────

    /** حجم الانكماش عند الضغط: أخفّ في الأوضاع الأضعف، أوضح في القوي. */
    @Composable
    fun pressScale(): Float = when (LocalPerformanceTier.current) {
        PerformanceTier.LOW -> 0.95f
        PerformanceTier.BALANCED -> 0.94f
        PerformanceTier.STANDARD -> 0.91f
    }

    /** ضغط الأزرار/الصفوف، وأي حركة قيمة واحدة سريعة (لون/dp) يجب أن تتبع الإحساس نفسه. */
    @Composable
    fun <T> quickSpring(): FiniteAnimationSpec<T> = when (LocalPerformanceTier.current) {
        PerformanceTier.LOW -> tween<T>(100, easing = claudeEasing)
        PerformanceTier.BALANCED -> tween<T>(balancedFastMs, easing = claudeEasing)
        PerformanceTier.STANDARD -> spring<T>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow * 2.2f * snap()
        )
    }

    /** انكماش الضغط. */
    @Composable
    fun pressSpring(): FiniteAnimationSpec<Float> = quickSpring()

    /** ترتيب/إدراج/حذف عناصر القوائم (placementSpec في animateItem). */
    @Composable
    fun reorderSpring(): FiniteAnimationSpec<IntOffset> = when (LocalPerformanceTier.current) {
        PerformanceTier.LOW -> tween<IntOffset>(150, easing = claudeEasing)
        PerformanceTier.BALANCED -> tween<IntOffset>(balancedSlowMs, easing = claudeEasing)
        PerformanceTier.STANDARD -> spring<IntOffset>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow * 1.6f * snap()
        )
    }

    /** حركة حجم expandVertically/shrinkVertically. */
    @Composable
    fun expandSpring(): FiniteAnimationSpec<IntSize> = when (LocalPerformanceTier.current) {
        PerformanceTier.LOW -> tween<IntSize>(125, easing = claudeEasing)
        PerformanceTier.BALANCED -> tween<IntSize>(balancedBaseMs, easing = claudeEasing)
        PerformanceTier.STANDARD -> spring<IntSize>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow * snap()
        )
    }

    /** اختفاء الصف عند حذفه/تسديده (fadeOutSpec في animateItem). */
    @Composable
    fun listItemFadeOut(): FiniteAnimationSpec<Float> =
        tween<Float>(durationMillis = fadeMillis(), easing = claudeEasing)

    @Composable
    fun expandMillis(): Int = when (LocalPerformanceTier.current) {
        PerformanceTier.LOW -> 125
        PerformanceTier.BALANCED -> balancedBaseMs
        PerformanceTier.STANDARD -> (200 * durationScale()).toInt()
    }

    @Composable
    fun fadeMillis(): Int = when (LocalPerformanceTier.current) {
        PerformanceTier.LOW -> 75
        PerformanceTier.BALANCED -> balancedFastMs
        PerformanceTier.STANDARD -> (140 * durationScale()).toInt()
    }

    /**
     * "Pop in" لكل ما يظهر فوق المحتوى (حوارات، أزرار عائمة، لافتات).
     * القوي: نابض بارتداد خفيف جداً (0.72) فيبدو العنصر "حياً"؛ المتوازن: tween بالمدة
     * الأساسية؛ المنخفض: tween قصير 125ms.
     */
    @Composable
    fun popInSpring(): FiniteAnimationSpec<Float> = when (LocalPerformanceTier.current) {
        PerformanceTier.LOW -> tween<Float>(125, easing = claudeEasing)
        PerformanceTier.BALANCED -> tween<Float>(balancedBaseMs, easing = claudeEasing)
        PerformanceTier.STANDARD -> spring<Float>(
            dampingRatio = 0.72f,
            stiffness = Spring.StiffnessMedium * snap()
        )
    }

    /** تبديل المحتوى في مكانه (تلاشٍ + انزلاق). */
    @Composable
    fun <T> contentTween(): FiniteAnimationSpec<T> = tween(
        durationMillis = when (LocalPerformanceTier.current) {
            PerformanceTier.LOW -> 100
            PerformanceTier.BALANCED -> balancedBaseMs
            PerformanceTier.STANDARD -> (220 * durationScale()).toInt()
        },
        easing = claudeEasing
    )

    // ───────────────────────── التنقل والدخول ─────────────────────────
    // قاعدة المستويات الثلاثة:
    //  - STANDARD (قوي): انزلاق + تصغير الشاشة المغطّاة (94%) — أغنى حركة.
    //  - BALANCED: انزلاق + تلاشٍ فقط، بدون scale للشاشة كاملة (تصغير شاشة
    //    كاملة بكل بطاقاتها كل إطار هو أغلى جزء رسومياً)، وبمدة أقصر.
    //  - LOW: تلاشٍ بسيط قصير جداً فقط (بلا انزلاق ولا scale).

    /** مدة انتقال دفع/سحب الشاشة (ms). */
    @Composable
    fun navMillis(): Int = when (LocalPerformanceTier.current) {
        PerformanceTier.LOW -> 150
        PerformanceTier.BALANCED -> balancedSlowMs
        PerformanceTier.STANDARD -> (300 * durationScale()).toInt()
    }

    /** مدة انتقال التبويبات (الـ pager) بالـ ms. */
    @Composable
    fun pagerMillis(): Int = when (LocalPerformanceTier.current) {
        PerformanceTier.LOW -> 175
        PerformanceTier.BALANCED -> balancedSlowMs
        PerformanceTier.STANDARD -> (320 * durationScale()).toInt()
    }

    /** مدة ظهور صف القائمة عند أول عرض (انظر [listItemEntrance]). */
    @Composable
    fun entranceMillis(): Int = when (LocalPerformanceTier.current) {
        PerformanceTier.LOW -> 150
        PerformanceTier.BALANCED -> balancedSlowMs
        PerformanceTier.STANDARD -> (320 * durationScale()).toInt()
    }
}

/**
 * أثر الضغط لا يظهر إلا إن بقي الإصبع 60ms، فبداية تمرير فوق بطاقة لا تُشغّل حركة
 * ضغط ثم تلغيها (عمل رسومي مهدور في أسوأ لحظة). النقرة السريعة جداً تُظهر الأثر ~90ms
 * كي يُرى. بلا أي مؤقّت إضافي ما دام لا ضغط.
 */
@Composable
fun InteractionSource.collectDelayedPressedAsState(delayMs: Long = 60L): State<Boolean> {
    val raw = collectIsPressedAsState()
    val shown = remember { mutableStateOf(false) }
    val started = remember { mutableStateOf(false) }
    LaunchedEffect(raw.value) {
        if (raw.value) {
            started.value = true
            delay(delayMs)
            shown.value = true
        } else {
            if (started.value && !shown.value) {
                shown.value = true
                delay(90L)
            }
            shown.value = false
            started.value = false
        }
    }
    return shown
}

// ───────────────────────── تأثيرات بصرية للأداء القوي فقط ─────────────────────────
// في المتوازن والمنخفض تُعيد `this` كما هي بلا أي مؤقّت ولا رسم إضافي. كلها تُرسم في
// مرحلة الرسم (draw) ولا تُعيد التركيب.

/**
 * لمعة ضوء ناعمة تمسح العنصر قطرياً كل ~6 ثوانٍ (تتحرك في أول 40% من الدورة وتسكن
 * بقيتها). الحالة الساكنة لا تُبطل الرسم (derivedStateOf يثبت على -1) فلا كلفة بين اللمعات.
 */
@Composable
fun Modifier.sheenSweep(): Modifier {
    if (LocalPerformanceTier.current != PerformanceTier.STANDARD) return this
    val t = rememberInfiniteTransition(label = "sheenSweep")
    val phase = t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)),
        label = "sheenPhase"
    )
    val sweep = remember { derivedStateOf { if (phase.value < 0.4f) phase.value / 0.4f else -1f } }
    val tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    return this.drawWithContent {
        drawContent()
        val f = sweep.value
        if (f >= 0f) {
            val band = size.width * 0.55f
            val start = -band + (size.width + band) * f
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, tint, Color.Transparent),
                    start = Offset(start, 0f),
                    end = Offset(start + band, size.height * 0.5f)
                )
            )
        }
    }
}

/** هالة لونية ناعمة خلف الزر العائم (تتجاوز حدوده قليلاً) — ثابتة بلا حركة. */
@Composable
fun Modifier.glowBehind(color: Color): Modifier {
    if (LocalPerformanceTier.current != PerformanceTier.STANDARD) return this
    return this.drawBehind {
        val r = size.minDimension * 0.85f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.32f), Color.Transparent),
                center = center,
                radius = r
            ),
            radius = r,
            center = center
        )
    }
}
