package com.salman.herbalencyclopedia.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * مصدر واحد لكل مدد وأنماط الحركة (easing) المستخدمة بالتطبيق.
 * بما أن التطبيق مبني بالكامل على Jetpack Compose، فملفات res/anim
 * (XML) الخاصة بنظام Views القديم لا تُستخدم فعلياً هنا — Compose
 * يطبّق حركاته عبر AnimationSpec بالكود مباشرة. لذلك هذا الملف هو
 * البديل العملي: كل الشاشات تسحب مدد/منحنيات الحركة من هنا بدل ما
 * كل شاشة تخترع أرقامها الخاصة، فتصير الحركة متناسقة وسلسة بكل مكان.
 */
object AppMotion {
    /** منحنى ناعم بدايةً ونهايةً، أهدأ من الافتراضي — يُستخدم لمعظم الحركات. */
    val Smooth: Easing = CubicBezierEasing(0.33f, 0f, 0.13f, 1f)

    /** منحنى حريري بطيء الإقلاع/الهبوط، مناسب لانتقالات الشاشات الكبيرة. */
    val Silky: Easing = CubicBezierEasing(0.16f, 1f, 0.22f, 1f)

    const val Quick = 220
    const val Standard = 380
    const val Slow = 560

    fun <T> smooth(durationMillis: Int = Standard) =
        tween<T>(durationMillis = durationMillis, easing = Smooth)

    fun <T> silky(durationMillis: Int = Slow) =
        tween<T>(durationMillis = durationMillis, easing = Silky)

    /** نابض موحّد لكل الحركات "الحيّة" (ضغط زر...) — تصادم/ارتداد
     *  خفيف جداً وحركة أهدأ بدل النط الملحوظ، فيبقى الإحساس ناعم ومريح. */
    fun <T> bouncy() = spring<T>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)

    /**
     * نابض أسرع وأخفّ من [bouncy] خصّيصاً لشريط التنقّل السفلي: قبل هذا
     * كانت كل عناصر تبديل العنصر المختار (لون الخلفية، حجم الأيقونة،
     * اتساع الكبسولة، ظهور النص) تستخدم [bouncy] بصلابة منخفضة جداً
     * (StiffnessLow) فتستغرق نحو 700-900ms لتستقر، فيبدو التبديل بطيئاً
     * وغير متزامن (كل خاصية تصل لوضعها النهائي بتوقيت مختلف)، وتبقى
     * كلها تُعيد الرسم لمدة طويلة عند كل ضغطة — وهذا أصل مشكلة الأداء.
     * هذا النابض يستقر خلال ~150-200ms تقريباً بلا نطّة ملحوظة.
     */
    fun <T> snappy() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1200f)
}

/**
 * حركة "ضغطة زجاجية" موحّدة لكل عناصر التطبيق القابلة للنقر (أزرار،
 * بطاقات الأعشاب/التصنيفات...): تصغير خفيف فوري عند الضغط ثم عودة
 * نابضة عند تركه، بدل الاعتماد على الـ ripple فقط. يُستخدم عبر تمرير
 * نفس [interactionSource] المُمرَّر لـ Card/Surface/Button حتى تُطابق
 * حالة الضغط الفعلية للعنصر.
 */
@Composable
fun rememberPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f
): State<Float> {
    val isPressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = AppMotion.bouncy(),
        label = "pressScale"
    )
}

/**
 * حركة ظهور متتابعة (Staggered Entrance) لعناصر الشبكات/القوائم: كل
 * عنصر يتلاشى للظهور وينزلق للأعلى قليلاً، بتأخير يتناسب مع [index]
 * كي تظهر البطاقات الواحدة تلو الأخرى بدل ظهورها كلها دفعة واحدة —
 * وتشتغل *مرة واحدة فقط* عند دخول الشاشة (بلا أي تكرار لانهائي)، على
 * عكس تأثير اللمعان الزجاجي المستمر الذي لا يناسب بطاقات كثيرة معاً.
 *
 * ثغرة أداء كانت هنا: `remember(index)` يُنسى بالكامل كلما خرج العنصر من
 * نطاق الرسم في LazyColumn/LazyVerticalGrid وأُعيد تركيبه لاحقاً (وهو أمر
 * طبيعي ومتكرر جداً أثناء التمرير)، فتُعاد حركة الظهور من الصفر (delay
 * كامل + fade + slide) في كل مرة يمر فيها العنصر أمام الشاشة، على قوائم
 * قد تحوي عشرات/مئات العناصر — إعادة تشغيل حركات وإطارات رسم لا داعي لها
 * باستمرار أثناء التمرير، في كلا وضعي الأداء. rememberSaveable هنا يعتمد
 * على SaveableStateHolder الخاص بـ Lazy*، والمرتبط بمعامل `key` الذي توفّره
 * كل الشاشات فعلاً (`items(list, key = { it.id })`)، فتبقى حالة "ظهر
 * سابقاً" محفوظة لكل عنصر باسمه الحقيقي حتى لو تغيّر index لاحقاً (بعد
 * فرز/تصفية)، وتُشغَّل الحركة مرة واحدة فعلية فقط.
 *
 * قبل هذا التعديل كانت هذه الحركة تعمل بنفس التفصيل (تأخير متدرّج + تلاشي
 * + انزلاق) بغضّ النظر عن وضع الأداء المختار — أي أن الأجهزة الضعيفة في
 * الوضع الاقتصادي كانت تدفع نفس تكلفة animateFloatAsState المزدوجة (قيمتان
 * متحركتان لكل عنصر) ونفس سلسلة التأخيرات التراكمية عند التمرير السريع،
 * رغم أن هذا الوضع مخصّص أصلاً لتقليل عبء المعالج للحد الأقصى. الآن:
 *
 * - في [PerformanceMode.ECO]: العنصر يظهر فوراً بلا تأخير ولا حركتين
 *   متحركتين منفصلتين — تكلفة شبه معدومة، وهذا بالضبط ما يحتاجه جهاز ضعيف
 *   عند التمرير السريع بقائمة طويلة.
 * - في [PerformanceMode.HIGH_QUALITY]: تُضاف حركة تكبير خفيفة (scale) فوق
 *   التلاشي والانزلاق الأصليين، فيبدو الظهور أكثر "حيوية" على الأجهزة
 *   القوية القادرة على تحمّل حركة إضافية بلا أي تقطيع.
 *
 * إضافة أخيرة: الشرط لم يعد يعتمد فقط على اختيار المستخدم لوضع الأداء —
 * راجع [rememberSmoothMotionAllowed] في RefreshRate.kt: حتى لو اختار
 * "أداء عالٍ"، لو كانت الشاشة تعمل حالياً بمعدل تحديث منخفض فعلياً (أقل
 * من 60Hz)، تُعامَل كوضع اقتصادي هنا تحديداً، لأن حركة متصلة على شاشة
 * 20/40Hz تظهر متقطّعة بلا أي فائدة بصرية تُذكر.
 */
fun Modifier.staggeredEntrance(
    index: Int,
    stepMillis: Long = 45L,
    maxDelayMillis: Long = 360L
): Modifier = composed {
    val smoothAllowed = rememberSmoothMotionAllowed()
    var visible by rememberSaveable(index) { mutableStateOf(false) }
    LaunchedEffect(index, smoothAllowed) {
        if (!visible) {
            if (smoothAllowed) delay(minOf(index * stepMillis, maxDelayMillis))
            visible = true
        }
    }
    if (!smoothAllowed) {
        // بلا أي AnimationSpec متحرك: قيمة ثابتة فوراً، فلا يوجد إطار رسم
        // إضافي واحد يُعاد رسمه بسبب هذا المعدّل على الإطلاق.
        return@composed this.graphicsLayer { alpha = if (visible) 1f else 0f }
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = AppMotion.smooth<Float>(AppMotion.Standard),
        label = "entranceAlpha"
    )
    val slide by animateFloatAsState(
        targetValue = if (visible) 0f else 22f,
        animationSpec = AppMotion.smooth<Float>(AppMotion.Standard),
        label = "entranceSlide"
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.94f,
        animationSpec = AppMotion.smooth<Float>(AppMotion.Slow),
        label = "entranceScale"
    )
    this.graphicsLayer {
        this.alpha = alpha
        translationY = slide.dp.toPx()
        scaleX = scale
        scaleY = scale
    }
}

/**
 * حركة ظهور بسيطة (تلاشي + انزلاق خفيف من الأسفل) لعنصر واحد بارز بالشاشة
 * (بطاقة تسجيل الدخول، رأس شاشة...) بدل قائمة متكرّرة — نفس مبدأ الحساسية
 * لوضع الأداء *ومعدل التحديث الفعلي* معاً (راجع [rememberSmoothMotionAllowed]):
 * تظهر فوراً بلا حركة في الوضع الاقتصادي، أو لو كانت الشاشة تعمل حالياً
 * بمعدل تحديث أقل من 60Hz حتى في وضع الأداء العالي.
 */
fun Modifier.entranceFade(
    delayMillis: Long = 0L,
    slideFrom: androidx.compose.ui.unit.Dp = 18.dp
): Modifier = composed {
    val smoothAllowed = rememberSmoothMotionAllowed()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (smoothAllowed && delayMillis > 0) delay(delayMillis)
        visible = true
    }
    if (!smoothAllowed) {
        return@composed this.graphicsLayer { alpha = 1f }
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = AppMotion.smooth<Float>(AppMotion.Slow),
        label = "fadeAlpha"
    )
    val slide by animateFloatAsState(
        targetValue = if (visible) 0f else slideFrom.value,
        animationSpec = AppMotion.smooth<Float>(AppMotion.Slow),
        label = "fadeSlide"
    )
    this.graphicsLayer {
        this.alpha = alpha
        translationY = slide.dp.toPx()
    }
}
