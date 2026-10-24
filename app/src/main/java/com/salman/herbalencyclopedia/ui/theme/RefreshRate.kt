package com.salman.herbalencyclopedia.ui.theme

import android.app.Activity
import android.os.Build
import android.view.Display
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import kotlin.math.abs

/**
 * قبل هذا الملف كان أندرويد يختار معدل تحديث الشاشة تلقائياً بمعزل تام عن
 * وضع الأداء الذي يختاره المستخدم من الإعدادات — ومعظم الأجهزة تختار
 * افتراضياً 60Hz حتى لو كانت شاشتها تدعم 90 أو 120Hz فعلياً، لأن النظام
 * لا يُفعّل المعدل الأعلى إلا إذا طلبه التطبيق صراحةً. النتيجة: من يملك
 * جهازاً حديثاً بشاشة 120Hz ويختار "أداء عالٍ" لم يكن يحصل عملياً على أي
 * سلاسة إضافية فوق الـ60Hz الافتراضية — كل حركات Compose كانت تُرسم بدقة
 * زمنية جيدة لكن بعدد إطارات أقل من إمكانية الشاشة الحقيقية.
 *
 * هذا الملف يغلق تلك الفجوة من طرفين:
 * 1) [applyPreferredRefreshRate] تطلب من النظام صراحةً أعلى وضع عرض متاح
 *    (Display.Mode) بنفس دقة الشاشة الحالية عند اختيار [PerformanceMode.HIGH_QUALITY]
 *    (استغلال كامل لإمكانية الجهاز: 90/120Hz إن توفرت)، وأدنى وضع متاح عند
 *    اختيار [PerformanceMode.ECO] (عادة 60Hz أو أقل) — تخفيف حقيقي لعبء
 *    المعالج/الرسوميات والبطارية على الأجهزة الضعيفة، بما يتجاوز مجرد
 *    إيقاف الحركات داخل Compose نفسها.
 * 2) [LocalRefreshRateTier] يجعل معدل التحديث الفعلي الحالي (مُقرَّباً لأقرب
 *    طبقة من 20/40/60/90/120) متاحاً لأي مكوّن Compose، كي تتكيّف الحركات
 *    المستمرة (fade/slide/scale) مع الواقع الفعلي للشاشة: شاشة تعمل حالياً
 *    عند 20 أو 40Hz فقط (سواء لأن الجهاز ضعيف أصلاً أو لأن النظام خفّض
 *    المعدل لتوفير الطاقة) لا يمكنها عرض حركة مستمرة بسلاسة مهما كانت مدة
 *    الحركة بالمللي ثانية صحيحة نظرياً — فتظهر متقطّعة (خطوات مرئية واضحة)
 *    بدل انسيابية. راجع rememberSmoothMotionAllowed() أسفل هذا الملف،
 *    وكيفية استخدامها في staggeredEntrance/entranceFade داخل Animations.kt.
 */

/** طبقات معدل التحديث التي يتكيّف معها التطبيق، من الأضعف للأقوى. أي معدل
 *  فعلي يُقرَّب (snap) لأقرب طبقة من هذه القائمة لأغراض اتخاذ القرار. */
val RefreshRateTiers = intArrayOf(20, 40, 60, 90, 120)

/** يقرّب معدل تحديث فعلي (بالهرتز) لأقرب طبقة من [RefreshRateTiers]. */
fun nearestRefreshRateTier(hz: Float): Int {
    if (hz <= 0f || hz.isNaN()) return 60
    return RefreshRateTiers.minByOrNull { abs(it - hz) } ?: 60
}

private fun Activity.currentDisplayOrNull(): Display? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display
    else @Suppress("DEPRECATION") windowManager.defaultDisplay
} catch (e: Exception) {
    null
}

/** معدل تحديث الشاشة الفعلي الحالي (بالهرتز) لهذا النشاط، أو 60 كقيمة
 *  افتراضية آمنة لو تعذّرت القراءة لأي سبب. */
fun Activity.currentRefreshRateHz(): Float = currentDisplayOrNull()?.refreshRate ?: 60f

/**
 * تطلب من النظام صراحةً أنسب وضع عرض (Display.Mode) حسب وضع الأداء
 * الحالي: أعلى معدل تحديث متاح بنفس دقة الشاشة في [PerformanceMode.HIGH_QUALITY]،
 * وأدناه في [PerformanceMode.ECO]. آمنة الاستدعاء المتكرر: لا تلمس نافذة
 * العرض إطلاقاً لو كان الوضع المطلوب مطابقاً للوضع الحالي أصلاً.
 */
fun applyPreferredRefreshRate(activity: Activity, mode: PerformanceMode) {
    val display = activity.currentDisplayOrNull() ?: return
    val current = try { display.mode } catch (e: Exception) { null } ?: return
    val supported = try { display.supportedModes } catch (e: Exception) { null }
        ?.takeIf { it.isNotEmpty() } ?: return

    // نفضّل أوضاعاً بنفس دقة الشاشة الحالية تحديداً (تفادي أي وضع بدقة
    // مختلفة قد يفرض تحجيماً/قصّاً غير مرغوب)، وإن لم يوجد أي وضع بنفس
    // الدقة (نادر) نكتفي بكل الأوضاع المتاحة كخيار احتياطي.
    val sameResolution = supported.filter {
        it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight
    }.ifEmpty { supported.toList() }

    val target = if (mode.isHighQuality) {
        sameResolution.maxByOrNull { it.refreshRate }
    } else {
        sameResolution.minByOrNull { it.refreshRate }
    } ?: return

    val window = activity.window
    val params = window.attributes
    if (params.preferredDisplayModeId != target.modeId) {
        params.preferredDisplayModeId = target.modeId
        window.attributes = params
    }
}

/** طبقة معدل تحديث الشاشة الفعلية الحالية (إحدى قيم [RefreshRateTiers])،
 *  تُزوَّد فعلياً من MainActivity بعد كل استدعاء لـ[applyPreferredRefreshRate]. */
val LocalRefreshRateTier = compositionLocalOf { 60 }

/**
 * هل يُسمح بتشغيل الحركات المستمرة "الكاملة" (تلاشي+انزلاق+تكبير) الآن؟
 * تتطلب كلا الشرطين معاً: المستخدم اختار [PerformanceMode.HIGH_QUALITY]
 * صراحةً من الإعدادات، *و* الشاشة تعمل فعلياً عند 60Hz فأعلى حالياً. هذا
 * الشرط الثاني هو الإضافة الجديدة: حتى لو اختار المستخدم "أداء عالٍ"، لو
 * كانت الشاشة نفسها (لأي سبب: جهاز LTPO خفّض المعدل توفيراً للطاقة، أو
 * جهاز رخيص أصلاً بشاشة 40Hz) تعمل حالياً بمعدل منخفض، فحركة متصلة بمدة
 * ثابتة ستظهر متقطّعة بلا فائدة — الأصح عندها التصرف كما في [PerformanceMode.ECO]
 * (ظهور فوري بلا حركة) بدل إهدار دورات معالج على حركة لن تُرى سلسة أصلاً.
 */
@Composable
fun rememberSmoothMotionAllowed(): Boolean =
    LocalPerformanceMode.current.isHighQuality && LocalRefreshRateTier.current >= 60
