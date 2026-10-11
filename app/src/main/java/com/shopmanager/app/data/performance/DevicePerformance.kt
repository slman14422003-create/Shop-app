package com.shopmanager.app.data.performance

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.Display
import android.view.View
import android.view.Window
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.abs

/*
 * منظومة الأداء الموحّدة — ملف واحد لكل ما يقرر "كم تأثيراً يتحمل هذا الجهاز":
 *   1) [PerformanceTier] / [PerformanceMode] / [resolvePerformanceTier]: المستويات الثلاثة وكيف يُحسم المستوى الفعلي.
 *   2) [SystemMotion]: قيود الحركة القادمة من النظام (إزالة الحركة، توفير الطاقة).
 *   3) [DevicePerformance]: كشف قدرة الجهاز (RAM، الأنوية، الـ heap، الـ GPU) مع تخزين النتيجة محلياً.
 *   4) [RefreshRatePolicy]: اختيار معدل تحديث الشاشة حسب المستوى.
 * الحركات نفسها (المدد والنوابض) في ui/common/MotionSpecs.kt وتقرأ [LocalPerformanceTier] و[LocalRefreshRateHz].
 */

// ───────────────────────── 1) المستويات والتفضيل ─────────────────────────

enum class PerformanceTier {
    /** الاقتصادي: بلا أي تأثيرات؛ انتقالات تلاشٍ بسيطة فقط. */
    LOW,

    /** المتوازن: حركة ناعمة تُرسم كلها في مرحلة الرسم (transform/alpha فقط)
     * بلا ظلال ولا تكبير/تصغير للشاشة كاملة ولا حلقات لا نهائية — فتبقى سلسة
     * دون أن تستهلك المعالج الرسومي. */
    BALANCED,

    /** القوي (HIGH): كل التأثيرات — انتقالات أغنى، ظلال، لمعان تحميل، رموز متحركة. */
    STANDARD
}

/**
 * "تفضيل الأداء" (الإعدادات ← الأداء): يتيح تجاوز الكشف التلقائي.
 *
 * - AUTO (الافتراضي): المستوى الذي كشفه [DevicePerformance.detectTier].
 * - HIGH: كل التأثيرات دائماً، حتى لو صُنّف الجهاز LOW تلقائياً.
 * - BALANCED ("متوازن"): حركة ناعمة بلا ظلال ولا تكبير للشاشة كاملة ولا حلقات لا نهائية.
 * - LOW: بلا تأثيرات دائماً، حتى لو صُنّف الجهاز STANDARD (أسرع وأوفر للبطارية).
 */
enum class PerformanceMode { AUTO, HIGH, BALANCED, LOW }

/** قيود الحركة القادمة من النظام نفسه، تُقرأ عند كل عودة للتطبيق. */
enum class SystemMotionLimit { NONE, POWER_SAVE, ANIMATIONS_OFF }

/** المكان الوحيد الذي يجمع الكشف التلقائي وتفضيل المستخدم وقيود النظام في [PerformanceTier] النهائي. */
fun resolvePerformanceTier(
    detected: PerformanceTier,
    mode: PerformanceMode,
    limit: SystemMotionLimit = SystemMotionLimit.NONE
): PerformanceTier {
    // "إزالة الحركة" في إعدادات النظام (إتاحة الوصول / خيارات المطوّر): تُحترم دائماً وتتفوق على أي اختيار.
    if (limit == SystemMotionLimit.ANIMATIONS_OFF) return PerformanceTier.LOW
    val base = when (mode) {
        PerformanceMode.AUTO -> detected
        PerformanceMode.HIGH -> PerformanceTier.STANDARD
        PerformanceMode.BALANCED -> PerformanceTier.BALANCED
        PerformanceMode.LOW -> PerformanceTier.LOW
    }
    // توفير الطاقة: في الوضع التلقائي فقط ننزل من القوي إلى المتوازن (لا نغيّر اختيار المستخدم الصريح).
    return if (limit == SystemMotionLimit.POWER_SAVE && mode == PerformanceMode.AUTO && base == PerformanceTier.STANDARD)
        PerformanceTier.BALANCED else base
}

// ───────────────────────── 2) قيود الحركة من النظام ─────────────────────────

object SystemMotion {
    fun read(c: Context): SystemMotionLimit {
        val off = runCatching {
            Settings.Global.getFloat(c.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) <= 0f
        }.getOrDefault(false)
        if (off) return SystemMotionLimit.ANIMATIONS_OFF
        val save = runCatching {
            (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode
        }.getOrDefault(false)
        return if (save) SystemMotionLimit.POWER_SAVE else SystemMotionLimit.NONE
    }
}

// ───────────────────────── 3) كشف قدرة الجهاز ─────────────────────────

/**
 * "وضع لكل هاتف": يُصنَّف الجهاز مرة واحدة عند أول تشغيل ويُحفظ الحكم محلياً (بلا
 * شبكة ولا قائمة موديلات). الإشارات المقروءة (أي واحدة منها تكفي للنزول إلى LOW):
 *
 * - إجمالي الـ RAM ([ActivityManager.MemoryInfo]) وعدد الأنوية وعلم النظام `isLowRamDevice`.
 * - الـ GPU: أقل من OpenGL ES 3.0 (`reqGlEsVersion`، بلا إنشاء سياق GL) = عتاد قديم/ضعيف.
 * - سقف الـ heap لكل تطبيق (`memoryClass`): تضبطه الشركات حسب فئة الجهاز، فيكشف أجهزة
 *   اقتصادية رقم RAM فيها يبدو "مقبولاً".
 * - أندرويد 9 (API 28) أو أقدم.
 *
 * الخطأ الإيجابي (جهاز جيد يأخذ واجهة أخف) أرخص بكثير من الخطأ السلبي (جهاز ضعيف يتقطع).
 * يُخزَّن رقم نسخة المنطق ([CURRENT_DETECTION_VERSION]) مع الحكم، فأي تحسين لاحق
 * في الكشف يُعيد قياس كل الأجهزة تلقائياً عند التشغيل التالي.
 */
object DevicePerformance {
    private const val PREFS = "shop_manager_device"
    private const val KEY_TIER = "performance_tier"
    private const val KEY_DETECTION_VERSION = "performance_tier_version"
    // ارفع هذا الرقم كلما تغيّر منطق [currentDeviceInfo].
    private const val CURRENT_DETECTION_VERSION = 4

    private const val LOW_RAM_THRESHOLD_MB = 3072L
    // OpenGL ES 3.0 = 0x30000 (النسخة الرئيسية في أعلى 16 بت).
    private const val MIN_GL_ES_VERSION = 0x30000
    // 96MB وما دون = فئة heap الأجهزة الضعيفة تاريخياً (المتوسط الحديث 192–256MB+).
    private const val LOW_MEMORY_CLASS_MB = 96

    // الجهاز المتوسط → BALANCED تلقائياً: رام ≤ ~6GB (النظام يُبلغ أقل قليلاً من
    // الاسمي) أو أقل من 8 أنوية أو سقف heap ≤ 160MB. القوي فقط (8GB+/8 أنوية)
    // يحصل على كل التأثيرات تلقائياً.
    private const val BALANCED_RAM_THRESHOLD_MB = 7000L
    private const val BALANCED_CORE_THRESHOLD = 7
    private const val BALANCED_MEMORY_CLASS_MB = 160

    /** الإشارات الخام التي يقيسها [detectTier]، تُعرض في الإعدادات ← الأداء ليرى المستخدم
     * سبب التصنيف. تُقاس دائماً من جديد (لا تقرأ التخزين المؤقت). */
    data class DeviceInfo(
        val totalRamMb: Long,
        val cores: Int,
        val osFlaggedLowRam: Boolean,
        val weakGpu: Boolean,
        val memoryClassMb: Int,
        val weakMemoryClass: Boolean,
        val tier: PerformanceTier
    )

    fun currentDeviceInfo(context: Context): DeviceInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)
        val totalRamMb = memoryInfo.totalMem / (1024 * 1024)
        val cores = Runtime.getRuntime().availableProcessors()
        val osFlaggedLowRam = activityManager?.isLowRamDevice == true
        val glEsVersion = activityManager?.deviceConfigurationInfo?.reqGlEsVersion ?: MIN_GL_ES_VERSION
        val weakGpu = glEsVersion in 1 until MIN_GL_ES_VERSION
        val memoryClassMb = activityManager?.memoryClass ?: (LOW_MEMORY_CLASS_MB + 1)
        val weakMemoryClass = memoryClassMb in 1..LOW_MEMORY_CLASS_MB
        val lowRam = totalRamMb in 1..LOW_RAM_THRESHOLD_MB
        val midRange = totalRamMb in 1..BALANCED_RAM_THRESHOLD_MB ||
            cores in 1..BALANCED_CORE_THRESHOLD ||
            memoryClassMb in 1..BALANCED_MEMORY_CLASS_MB
        // الأجهزة القديمة جداً: أندرويد 9 أو أقدم — معالجاتها الرسومية وأنظمتها لا تتحمل أي تأثيرات.
        val veryOld = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
        val tier = when {
            osFlaggedLowRam || weakGpu || weakMemoryClass || veryOld || lowRam -> PerformanceTier.LOW
            midRange -> PerformanceTier.BALANCED
            else -> PerformanceTier.STANDARD
        }
        return DeviceInfo(totalRamMb, cores, osFlaggedLowRam, weakGpu, memoryClassMb, weakMemoryClass, tier)
    }

    /** يقرأ التصنيف المخزَّن إن كان بنسخة المنطق الحالية، وإلا يقيس من جديد ويحفظ — فلا
     * يُعاد الاستعلام من ActivityManager في كل تشغيل بارد. */
    fun detectTier(context: Context): PerformanceTier {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_DETECTION_VERSION, -1) == CURRENT_DETECTION_VERSION) {
            prefs.getString(KEY_TIER, null)?.let { cached ->
                return runCatching { PerformanceTier.valueOf(cached) }.getOrDefault(PerformanceTier.BALANCED)
            }
        }
        // نفس منطق [currentDeviceInfo] تماماً، فلا ينفصل ما يُعرض في الإعدادات عن الحكم المخزَّن.
        val tier = currentDeviceInfo(context).tier
        prefs.edit()
            .putString(KEY_TIER, tier.name)
            .putInt(KEY_DETECTION_VERSION, CURRENT_DETECTION_VERSION)
            .apply()
        return tier
    }

    /** يمسح التصنيف المخزَّن ليُقاس من جديد عند الاستدعاء التالي لـ [detectTier]
     * (زر "إعادة فحص أداء الجهاز" في الإعدادات). */
    fun resetCachedTier(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_TIER)
            .remove(KEY_DETECTION_VERSION)
            .apply()
    }
}

// ───────────────────────── 4) معدل تحديث الشاشة ─────────────────────────

/**
 * سياسة معدل التحديث حسب وضع الأداء:
 * - القوي (STANDARD): أقرب وضع إلى 90Hz (ثابت وسلس وأوفر من 120Hz). إن لم يدعم
 *   الجهاز 90 يُختار أقرب وضع أعلى منه، وإن كانت الشاشة 60Hz فقط تبقى 60 وتتولّى
 *   [com.shopmanager.app.ui.common.MotionSpecs] جعل الحركة أسرع استجابة.
 * - المتوازن (BALANCED): أقرب وضع إلى 60Hz لتوفير الطاقة وثبات توقيت الإطارات.
 * - الاقتصادي (LOW): أقرب وضع إلى 40Hz (تلميح إطارات 40 على أندرويد 15+)، وكل مدد الحركة
 *   مضاعفات 25ms = إطار واحد @40Hz فتتوزع الإطارات بالتساوي.
 * لا يتغير الـ resolution أبداً (نختار فقط من أوضاع بنفس الدقة) لتفادي وميض تبديل الوضع.
 */
object RefreshRatePolicy {
    private const val HIGH_TARGET_HZ = 90f
    private const val BALANCED_TARGET_HZ = 60f
    // الاقتصادي: 40Hz — إطارات أقل = حمل أقل على معالج الرسوميات القديم. إن لم يوفّر
    // العرض وضعاً قريباً من 40 يُختار أقرب وضع (غالباً 60) ويُرسَل تلميح إطارات 40 للنظام.
    private const val LOW_TARGET_HZ = 40f
    private const val HIGH_MIN_HZ = 85f

    fun apply(window: Window, display: Display?, tier: PerformanceTier) {
        if (display == null) return
        val current = display.mode ?: return
        val modes = display.supportedModes.filter {
            it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight
        }
        val target = choose(modes, tier) ?: return
        val attrs = window.attributes
        if (attrs.preferredDisplayModeId == target.modeId && attrs.preferredRefreshRate == target.refreshRate) return
        window.attributes = attrs.apply {
            preferredDisplayModeId = target.modeId
            preferredRefreshRate = target.refreshRate
        }
    }

    /** تلميح معدل الإطارات للنظام (أندرويد 15+): الاقتصادي يطلب 40، غيره يرفع التلميح. */
    fun applyFrameRateHint(window: Window, tier: PerformanceTier) {
        if (Build.VERSION.SDK_INT < 35) return
        runCatching {
            window.decorView.requestedFrameRate =
                if (tier == PerformanceTier.LOW) LOW_TARGET_HZ else View.REQUESTED_FRAME_RATE_CATEGORY_NO_PREFERENCE
        }
    }

    private fun choose(modes: List<Display.Mode>, tier: PerformanceTier): Display.Mode? {
        if (modes.isEmpty()) return null
        return if (tier == PerformanceTier.STANDARD) {
            val fast = modes.filter { it.refreshRate >= HIGH_MIN_HZ }
            if (fast.isEmpty()) {
                modes.maxByOrNull { it.refreshRate }
            } else {
                fast.minWithOrNull(
                    compareBy<Display.Mode>({ abs(it.refreshRate - HIGH_TARGET_HZ) }, { -it.refreshRate })
                )
            }
        } else {
            val target = if (tier == PerformanceTier.LOW) LOW_TARGET_HZ else BALANCED_TARGET_HZ
            modes.minWithOrNull(compareBy<Display.Mode>({ abs(it.refreshRate - target) }, { it.refreshRate }))
        }
    }
}

// ───────────────────────── 5) توفير القيم لشجرة Compose ─────────────────────────

/** يُوفَّر مرة واحدة قرب جذر الشجرة (MainActivity)؛ الافتراضي STANDARD كي تعرض المعاينات
 * وأي شيء خارج الموفّر الواجهة الكاملة. */
val LocalPerformanceTier = staticCompositionLocalOf { PerformanceTier.STANDARD }

/** معدل تحديث الشاشة الحالي (Hz) كما يراه التطبيق؛ تقرؤه حركات التطبيق لتضبط سرعتها. */
val LocalRefreshRateHz = staticCompositionLocalOf { 60f }
