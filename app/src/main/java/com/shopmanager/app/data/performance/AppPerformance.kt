package com.shopmanager.app.data.performance

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.app.Activity
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.View
import android.view.Window
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
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
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.metrics.performance.JankStats
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.delay

/*
 * AppPerformance.kt — ملف الأداء والحركة الوحيد في التطبيق. كل ما يقرر "كم تأثيراً يتحمل هذا الجهاز"
 * وكيف تتحرك الواجهة موجود هنا، بالترتيب:
 *   1) [PerformanceTier] / [PerformanceMode] / [resolvePerformanceTier]: المستويان LOW/HIGH وكيف يُحسم المستوى الفعلي.
 *   2) [SystemMotion]: قيود الحركة من النظام (إزالة الحركة، توفير الطاقة).
 *   3) [DevicePerformance]: كشف قدرة الجهاز (RAM، الأنوية، heap، GPU) مع تخزين الحكم محلياً.
 *   4) [RefreshRatePolicy]: اختيار معدل تحديث الشاشة حسب المستوى.
 *   5) [MotionSpecs]: المصدر الوحيد لكل مدد/نوابض/انتقالات الحركة. لا يُكتب tween/spring/fadeIn خام خارجه.
 *   6) [collectDelayedPressedAsState] / [sheenSweep] / [glowBehind] / [listItemEntrance]: تأثيرات مبنية على المستوى.
 */

// ───────────────────────── 1) المستويات والتفضيل ─────────────────────────

enum class PerformanceTier {
    /** الاقتصادي: بلا أي تأثيرات؛ انتقالات تلاشٍ بسيطة فقط. */
    LOW,

    /** القوي: كل التأثيرات — انتقالات أغنى، لمعان تحميل، رموز متحركة (والضبابية للأجهزة الأقوى فقط عبر [LocalHeavyFx]). */
    HIGH
}

/**
 * "تفضيل الأداء" (الإعدادات ← الأداء): AUTO (يقرر التطبيق وحده)، HIGH (كل التأثيرات دائماً)، LOW (بلا تأثيرات).
 */
enum class PerformanceMode { AUTO, HIGH, LOW }

/** قيود الحركة القادمة من النظام نفسه، تُقرأ عند كل عودة للتطبيق. */
enum class SystemMotionLimit { NONE, POWER_SAVE, ANIMATIONS_OFF }

/**
 * المكان الوحيد الذي يجمع الكشف التلقائي وتفضيل المستخدم وقيود النظام في [PerformanceTier] النهائي.
 *
 * - "إزالة الحركة" في النظام: LOW دائماً (تتفوق على أي اختيار).
 * - AUTO: توفير الطاقة ← LOW، الحرارة ≥ MODERATE ← LOW، ضغط الذاكرة (onTrimMemory ≥ RUNNING_LOW) ← LOW،
 *   وإلا المستوى المكتشف.
 * - HIGH/LOW اليدوي: يُحترم كما هو.
 */
fun resolvePerformanceTier(
    detected: PerformanceTier,
    mode: PerformanceMode,
    limit: SystemMotionLimit = SystemMotionLimit.NONE,
    thermalStatus: Int = 0,
    memoryLow: Boolean = false
): PerformanceTier {
    if (limit == SystemMotionLimit.ANIMATIONS_OFF) return PerformanceTier.LOW
    return when (mode) {
        PerformanceMode.HIGH -> PerformanceTier.HIGH
        PerformanceMode.LOW -> PerformanceTier.LOW
        PerformanceMode.AUTO -> when {
            limit == SystemMotionLimit.POWER_SAVE -> PerformanceTier.LOW
            thermalStatus >= THERMAL_STATUS_MODERATE -> PerformanceTier.LOW
            memoryLow -> PerformanceTier.LOW
            else -> detected
        }
    }
}

/** = PowerManager.THERMAL_STATUS_MODERATE (API 29) كثابت كي لا نحتاج فحص الإصدار عند المقارنة. */
const val THERMAL_STATUS_MODERATE = 2

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
    private const val CURRENT_DETECTION_VERSION = 6

    private const val LOW_RAM_THRESHOLD_MB = 3072L
    // OpenGL ES 3.0 = 0x30000 (النسخة الرئيسية في أعلى 16 بت).
    private const val MIN_GL_ES_VERSION = 0x30000
    // 96MB وما دون = فئة heap الأجهزة الضعيفة تاريخياً (المتوسط الحديث 192–256MB+).
    private const val LOW_MEMORY_CLASS_MB = 96

    // الضبابية (blur) تحتاج جهازاً قوياً حقاً: 6GB+ و8 أنوية+. النظام يُبلغ عن أقل قليلاً من
    // الرقم الاسمي (جهاز 6GB يُبلغ ~5.5–5.9GB)، فالعتبة 5500MB تعني "6GB اسمياً".
    private const val HEAVY_FX_RAM_MB = 5500L
    private const val HEAVY_FX_CORES = 8

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

    @Suppress("DEPRECATION")
    private fun maxDisplayHz(context: Context): Float = runCatching {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        val d = wm.defaultDisplay
        d.supportedModes.maxOfOrNull { it.refreshRate } ?: d.refreshRate
    }.getOrDefault(0f)

    private fun totalRamMb(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 0L
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.totalMem / (1024 * 1024)
    }

    /** true فقط عند RAM ≥ 6GB (اسمياً) و≥ 8 أنوية: شرط تفعيل الضبابية مع المستوى HIGH. */
    fun isHeavyFx(context: Context): Boolean =
        totalRamMb(context) >= HEAVY_FX_RAM_MB && Runtime.getRuntime().availableProcessors() >= HEAVY_FX_CORES

    /** سقف heap التطبيق بالـ MB (لتحجيم كاشات الصور). */
    fun memoryClassMb(context: Context): Int =
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.memoryClass ?: 128

    fun currentDeviceInfo(context: Context): DeviceInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val totalRamMb = totalRamMb(context)
        val cores = Runtime.getRuntime().availableProcessors()
        val osFlaggedLowRam = activityManager?.isLowRamDevice == true
        val glEsVersion = activityManager?.deviceConfigurationInfo?.reqGlEsVersion ?: MIN_GL_ES_VERSION
        val weakGpu = glEsVersion in 1 until MIN_GL_ES_VERSION
        val memoryClassMb = activityManager?.memoryClass ?: (LOW_MEMORY_CLASS_MB + 1)
        val weakMemoryClass = memoryClassMb in 1..LOW_MEMORY_CLASS_MB
        val lowRam = totalRamMb in 1..LOW_RAM_THRESHOLD_MB
        // الأجهزة القديمة جداً: أندرويد 9 أو أقدم.
        val veryOld = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
        // شاشة 60Hz أو أقل على جهاز قديم/محدود: ميزانية الإطار 16.6ms فقط.
        val slowDisplay = maxDisplayHz(context) in 1f..61f
        val oldOrLimited = Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q || totalRamMb in 1..4600L || cores in 1..6
        val tier = if (
            lowRam || osFlaggedLowRam || weakGpu || weakMemoryClass || veryOld || (slowDisplay && oldOrLimited)
        ) PerformanceTier.LOW else PerformanceTier.HIGH
        return DeviceInfo(totalRamMb, cores, osFlaggedLowRam, weakGpu, memoryClassMb, weakMemoryClass, tier)
    }

    /** يقرأ التصنيف المخزَّن إن كان بنسخة المنطق الحالية، وإلا يقيس من جديد ويحفظ. */
    fun detectTier(context: Context): PerformanceTier {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_DETECTION_VERSION, -1) == CURRENT_DETECTION_VERSION) {
            prefs.getString(KEY_TIER, null)?.let { cached ->
                return if (cached == "LOW") PerformanceTier.LOW else PerformanceTier.HIGH
            }
        }
        val tier = currentDeviceInfo(context).tier
        prefs.edit()
            .putString(KEY_TIER, tier.name)
            .putInt(KEY_DETECTION_VERSION, CURRENT_DETECTION_VERSION)
            .apply()
        return tier
    }

    /** ينزل بالتصنيف المخزَّن من HIGH إلى LOW حين يرصد [JankMonitor] تقطعاً فعلياً. يُعيد المستوى الجديد. */
    fun downgradeCachedTier(context: Context): PerformanceTier {
        val current = detectTier(context)
        if (current == PerformanceTier.LOW) return PerformanceTier.LOW
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TIER, PerformanceTier.LOW.name)
            .putInt(KEY_DETECTION_VERSION, CURRENT_DETECTION_VERSION)
            .apply()
        return PerformanceTier.LOW
    }

    /** يمسح التصنيف المخزَّن ليُقاس من جديد (زر "إعادة فحص أداء الجهاز"). */
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
 * - القوي (HIGH): أقرب وضع إلى 90Hz (ثابت وسلس وأوفر من 120Hz). إن لم يدعم
 *   الجهاز 90 يُختار أقرب وضع أعلى منه، وإن كانت الشاشة 60Hz فقط تبقى 60 وتتولّى
 *   [com.shopmanager.app.ui.common.MotionSpecs] جعل الحركة أسرع استجابة.
 * - الاقتصادي (LOW): أقرب وضع إلى 40Hz (تلميح إطارات 40 على أندرويد 15+)، وكل مدد الحركة
 *   مضاعفات 25ms = إطار واحد @40Hz فتتوزع الإطارات بالتساوي.
 * لا يتغير الـ resolution أبداً (نختار فقط من أوضاع بنفس الدقة) لتفادي وميض تبديل الوضع.
 */
object RefreshRatePolicy {
    private const val HIGH_TARGET_HZ = 90f
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
        return if (tier == PerformanceTier.HIGH) {
            val fast = modes.filter { it.refreshRate >= HIGH_MIN_HZ }
            if (fast.isEmpty()) {
                modes.maxByOrNull { it.refreshRate }
            } else {
                fast.minWithOrNull(
                    compareBy<Display.Mode>({ abs(it.refreshRate - HIGH_TARGET_HZ) }, { -it.refreshRate })
                )
            }
        } else {
            modes.minWithOrNull(compareBy<Display.Mode>({ abs(it.refreshRate - LOW_TARGET_HZ) }, { it.refreshRate }))
        }
    }
}

// ───────────────────────── 4.5) رصد التقطع بـ JankStats (androidx.metrics) ─────────────────────────

/**
 * JankStats واحد لكل نافذة يُغذّي أي عدد من "المجمِّعات" (بدء التشغيل، التمرير). التتبّع يعمل فقط
 * ما دام هناك مجمِّع نشط، ويتوقف كلياً بعدها. [JankStats] يرصد الإطارات البطيئة فعلياً
 * (FrameMetrics على API 24+) بلا Choreographer يدوي، ولا يعمل على الخيط الرئيسي.
 */
object JankMonitor {
    private class Collector(
        val warmupFrames: Int,
        val batchFrames: Int,
        val jankRatio: Float,
        val deadlineMs: Long,
        val onJank: () -> Unit
    ) {
        var seen = 0
        var total = 0
        var jank = 0
        @Volatile var done = false
    }

    private val main = Handler(Looper.getMainLooper())
    private val executor by lazy { Executors.newSingleThreadExecutor { r -> Thread(r, "jank-stats").apply { isDaemon = true } } }
    private val collectors = CopyOnWriteArrayList<Collector>()
    private var stats: JankStats? = null
    private var statsWindow: Window? = null

    @Synchronized
    private fun ensure(window: Window): JankStats? {
        if (stats != null && statsWindow === window) return stats
        stats?.isTrackingEnabled = false
        statsWindow = window
        stats = runCatching {
            JankStats.createAndTrack(window, executor) { frame ->
                val now = SystemClock.elapsedRealtime()
                val jank = frame.isJank
                for (c in collectors) {
                    if (c.done) continue
                    if (c.deadlineMs in 1 until now) { finish(c, fire = false); continue }
                    c.seen++
                    if (c.seen <= c.warmupFrames) continue
                    c.total++
                    if (jank) c.jank++
                    if (c.total >= c.batchFrames) {
                        val bad = c.jank.toFloat() / c.total > c.jankRatio
                        c.total = 0
                        c.jank = 0
                        if (bad) finish(c, fire = true)
                    }
                }
            }
        }.getOrNull()
        stats?.isTrackingEnabled = collectors.isNotEmpty()
        return stats
    }

    private fun finish(c: Collector, fire: Boolean) {
        if (c.done) return
        c.done = true
        collectors.remove(c)
        main.post {
            if (fire) c.onJank()
            syncTracking()
        }
    }

    @Synchronized
    private fun syncTracking() {
        stats?.isTrackingEnabled = collectors.isNotEmpty()
    }

    private fun start(
        window: Window,
        warmupFrames: Int,
        batchFrames: Int,
        jankRatio: Float,
        windowMs: Long,
        onJank: () -> Unit
    ): () -> Unit {
        val s = ensure(window) ?: return {}
        val deadline = if (windowMs > 0) SystemClock.elapsedRealtime() + windowMs else 0L
        val c = Collector(warmupFrames, batchFrames, jankRatio, deadline, onJank)
        collectors.add(c)
        s.isTrackingEnabled = true
        return {
            c.done = true
            collectors.remove(c)
            syncTracking()
        }
    }

    /** أول ~25 ثانية بعد الجاهزية: إن تجاوز التقطع 30% من 150 إطاراً بعد 45 إطار إحماء ← [onSustainedJank] مرة واحدة. */
    fun startStartupGuard(window: Window, onSustainedJank: () -> Unit): () -> Unit =
        start(window, warmupFrames = 45, batchFrames = 150, jankRatio = 0.30f, windowMs = 25_000L, onJank = onSustainedJank)

    /** أثناء التمرير فقط: أول 6 إطارات تُتجاهل، و25% من 90 إطاراً بطيئاً ← [onJank]. */
    fun startScrollWatch(window: Window, onJank: () -> Unit): () -> Unit =
        start(window, warmupFrames = 6, batchFrames = 90, jankRatio = 0.25f, windowMs = 0L, onJank = onJank)
}

/** يُستدعى من MainActivity: ينزل بالمستوى حين يرصد [JankMonitor] تقطعاً (وضع «تلقائي» فقط). */
val LocalScrollJankReporter = staticCompositionLocalOf<(() -> Unit)?> { null }

private tailrec fun Context.findWindow(): Window? = when (this) {
    is Activity -> window
    is ContextWrapper -> baseContext.findWindow()
    else -> null
}

/** ضعه داخل أي شاشة فيها قائمة طويلة: يقيس التمرير الفعلي ولا يكلّف شيئاً والقائمة ساكنة. */
@Composable
fun ScrollJankWatch(state: androidx.compose.foundation.lazy.LazyListState) {
    val reporter = LocalScrollJankReporter.current ?: return
    val tier = LocalPerformanceTier.current
    if (tier == PerformanceTier.LOW) return
    val window = LocalView.current.context.findWindow() ?: return
    androidx.compose.runtime.LaunchedEffect(state, tier, window) {
        var stop: (() -> Unit)? = null
        try {
            androidx.compose.runtime.snapshotFlow { state.isScrollInProgress }.collect { scrolling ->
                stop?.invoke()
                stop = if (scrolling) JankMonitor.startScrollWatch(window) { reporter() } else null
            }
        } finally {
            stop?.invoke()
        }
    }
}

// ───────────────────────── 5) توفير القيم لشجرة Compose ─────────────────────────

/** المستوى الفعلي الحالي لغير الـ Composable (مفكّكات الصور، العمال…). يكتبه MainActivity فقط. */
object PerformanceRuntime {
    @Volatile var tier: PerformanceTier = PerformanceTier.HIGH
    val thumbnailConfig: android.graphics.Bitmap.Config
        get() = if (tier == PerformanceTier.LOW) android.graphics.Bitmap.Config.RGB_565 else android.graphics.Bitmap.Config.ARGB_8888
}

/** يُوفَّر مرة واحدة قرب جذر الشجرة (MainActivity)؛ الافتراضي HIGH كي تعرض المعاينات
 * وأي شيء خارج الموفّر الواجهة الكاملة. */
val LocalPerformanceTier = staticCompositionLocalOf { PerformanceTier.HIGH }

/** true فقط على جهاز RAM ≥ 6GB و≥ 8 أنوية ([DevicePerformance.isHeavyFx]). الضبابية تتطلب HIGH + هذا. */
val LocalHeavyFx = staticCompositionLocalOf { false }

/** معدل تحديث الشاشة الحالي (Hz) كما يراه التطبيق؛ تقرؤه حركات التطبيق لتضبط سرعتها. */
val LocalRefreshRateHz = staticCompositionLocalOf { 60f }

/**
 * الحركات اللانهائية (شعارات، هيكل تحميل، سبلاش، مؤشرات دوّارة) تعمل فقط حين يكون المستوى HIGH
 * والـ lifecycle ≥ STARTED؛ غير ذلك تبقى ساكنة فلا تُهدر إطارات في الخلفية ولا على الأجهزة الضعيفة.
 */
@Composable
fun rememberInfiniteAnimationsAllowed(): Boolean {
    val tier = LocalPerformanceTier.current
    val state by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    return tier == PerformanceTier.HIGH && state.isAtLeast(Lifecycle.State.STARTED)
}

// ───────────────────────── 6) الحركة الموحّدة ─────────────────────────

/**
 * المكان الوحيد الذي يحوّل "تفضيل الأداء" (الإعدادات ← الأداء) إلى مواصفات الحركة
 * الفعلية: ضغط الأزرار، ترتيب القوائم، التوسيع/الطي، الظهور المنبثق، التنقل.
 *
 * حركة بأسلوب Claude.ai: بلا ارتداد إطلاقاً — كل انتقال easing قصير ينتهي بسكون تام
 * ([claudeEasing])، وكل نابض هنا [Spring.DampingRatioNoBouncy] (باستثناء popInSpring
 * في القوي بارتداد خفيف جداً). المستوى يغيّر الصلابة والمدد فقط:
 *
 * - LOW (الاقتصادي): بلا نوابض — كل حركة تفاعلية tween قصير جداً (مضاعفات 25ms = إطار @40Hz).
 * - HIGH (القوي): نوابض سريعة لكنها محسوسة، بلا ارتداد.
 */
object MotionSpecs {

    /** منحنى Claude.ai: بداية سريعة وتباطؤ ناعم حتى سكون تام بلا ارتداد. */
    val claudeEasing: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** منحنى انتقال الشاشات بأسلوب iOS (UINavigationController). */
    val navEasing: Easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

    @Composable
    private fun isHigh(): Boolean = LocalPerformanceTier.current == PerformanceTier.HIGH

    /** شاشة 60Hz: نرفع صلابة النوابض ~30% ونقصّر المدد ~20% فتصل الحركة لهدفها بإطارات أقل. */
    @Composable
    private fun snap(): Float = if (LocalRefreshRateHz.current < 80f) 1.3f else 1f

    @Composable
    fun durationScale(): Float = if (LocalRefreshRateHz.current < 80f) 0.8f else 1f

    // ───────────────────────── الحركات التفاعلية الصغيرة ─────────────────────────

    /** حجم الانكماش عند الضغط. */
    @Composable
    fun pressScale(): Float = if (isHigh()) 0.91f else 0.95f

    @Composable
    fun <T> quickSpring(): FiniteAnimationSpec<T> =
        if (isHigh()) spring<T>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow * 2.2f * snap()
        ) else tween<T>(100, easing = claudeEasing)

    @Composable
    fun reorderSpring(): FiniteAnimationSpec<IntOffset> =
        if (isHigh()) spring<IntOffset>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow * 1.6f * snap()
        ) else tween<IntOffset>(150, easing = claudeEasing)

    @Composable
    fun expandSpring(): FiniteAnimationSpec<IntSize> =
        if (isHigh()) spring<IntSize>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow * snap()
        ) else tween<IntSize>(125, easing = claudeEasing)

    @Composable
    fun listItemFadeOut(): FiniteAnimationSpec<Float> =
        tween<Float>(durationMillis = fadeMillis(), easing = claudeEasing)

    @Composable
    fun fadeMillis(): Int = if (isHigh()) (140 * durationScale()).toInt() else 75

    /** "Pop in" لكل ما يظهر فوق المحتوى (حوارات، أزرار عائمة، لافتات). */
    @Composable
    fun popInSpring(): FiniteAnimationSpec<Float> =
        if (isHigh()) spring<Float>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium * snap())
        else tween<Float>(125, easing = claudeEasing)

    @Composable
    fun <T> contentTween(): FiniteAnimationSpec<T> = tween(
        durationMillis = if (isHigh()) (220 * durationScale()).toInt() else 100,
        easing = claudeEasing
    )

    // ───────────────────────── التنقل والدخول ─────────────────────────
    //  - HIGH: انزلاق + تصغير الشاشة المغطّاة (94%).
    //  - LOW: تلاشٍ بسيط قصير فقط (بلا انزلاق ولا scale).

    @Composable
    fun navMillis(): Int = if (isHigh()) 380 else 160

    @Composable
    fun pagerMillis(): Int = if (isHigh()) 380 else 160

    @Composable
    fun entranceMillis(): Int = if (isHigh()) (320 * durationScale()).toInt() else 150

    // ───────────────────────── انتقالات جاهزة ─────────────────────────

    @Composable
    fun popEnter(): EnterTransition = fadeIn(popInSpring()) + scaleIn(popInSpring(), initialScale = 0.6f)

    @Composable
    fun popExit(): ExitTransition = fadeOut(popInSpring()) + scaleOut(popInSpring(), targetScale = 0.6f)

    @Composable
    fun expandEnter(): EnterTransition = fadeIn(contentTween()) + expandVertically(expandSpring())

    @Composable
    fun expandExit(): ExitTransition = fadeOut(contentTween()) + shrinkVertically(expandSpring())

    /** تسليم الشاشة الكاملة (splash ← التطبيق، القفل ← التطبيق). */
    fun handoffTransition(tier: PerformanceTier): ContentTransform = when (tier) {
        PerformanceTier.LOW ->
            fadeIn(tween(90, easing = claudeEasing)) togetherWith fadeOut(tween(90, easing = claudeEasing))
        PerformanceTier.HIGH ->
            (fadeIn(tween(360, easing = claudeEasing)) +
                scaleIn(tween(360, easing = claudeEasing), initialScale = 0.96f)) togetherWith
                (fadeOut(tween(260, easing = claudeEasing)) +
                    scaleOut(tween(260, easing = claudeEasing), targetScale = 1.04f))
    }

    // ───────────────────────── unified motion vocabulary ─────────────────────────
    // Every screen should pick from these names instead of hand-written tween(...) values.
    const val fastMs = 120
    const val baseMs = 180
    const val slowMs = 240
    const val emphasizedMs = 360
    const val breatheMs = 1100
    const val cardStaggerMs = 30L
    const val cardStaggerCount = 6

    /** Standard easing for small, utility motion. */
    val standardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Emphasized easing for entrances and transitions (same curve as [claudeEasing]). */
    val emphasizedEasing: Easing = claudeEasing

    /** True when the system animation scale is set to 0 (remove animations). */
    @Composable
    fun reduceMotion(): Boolean {
        val ctx = LocalContext.current
        return remember {
            runCatching {
                Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }.getOrDefault(false)
        }
    }

    @Composable
    fun <T> enterSpec(): FiniteAnimationSpec<T> = contentTween()

    @Composable
    fun <T> exitSpec(): FiniteAnimationSpec<T> = tween(
        durationMillis = if (isHigh()) (160 * durationScale()).toInt() else 75,
        easing = standardEasing
    )

    @Composable
    fun itemPlacement(): FiniteAnimationSpec<IntOffset> = reorderSpring()

    @Composable
    fun <T> pressSpec(): FiniteAnimationSpec<T> = quickSpring()

    @Composable
    fun expandSpec(): FiniteAnimationSpec<IntSize> = expandSpring()

    /** Dialog / sheet appearance and disappearance. */
    @Composable
    fun dialogEnter(): EnterTransition = fadeIn(enterSpec()) + scaleIn(enterSpec(), initialScale = 0.92f)

    @Composable
    fun dialogExit(): ExitTransition = fadeOut(exitSpec()) + scaleOut(exitSpec(), targetScale = 0.96f)

    // ───────────────────────── دورات الحركة اللانهائية (تُفعَّل حسب المستوى فقط) ─────────────────────────
    const val shimmerMs = 1300      // لمعان هيكل التحميل (القوي)
    const val pulseMs = 900         // نبض هيكل التحميل (المتوازن)
    const val spinnerMs = 1000      // دورة المؤشر الدوّار
    const val floatMs = 1800        // طفو الأيقونة في حالة الانتظار
    const val arrowPulseMs = 650    // نبض سهم شاشة التحديث الإجباري
    const val sheenCycleMs = 6000   // دورة لمعة sheenSweep
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
    if (LocalPerformanceTier.current != PerformanceTier.HIGH || MotionSpecs.reduceMotion()) return this
    val t = rememberInfiniteTransition(label = "sheenSweep")
    val phase = t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(MotionSpecs.sheenCycleMs, easing = LinearEasing)),
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
    if (LocalPerformanceTier.current != PerformanceTier.HIGH) return this
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

// ───────────────────────── 7) دخول عناصر القوائم ─────────────────────────

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
/** الصفوف التي فهرسها أكبر من هذا لا تُحرَّك أبداً: هي تدخل بالتمرير لا بتحميل القائمة. */
private const val MAX_ANIMATED_INDEX = 12

/**
 * PERF + FEEL (تحسين): كان الصف يعيد تشغيل حركة الدخول (Animatable + coroutine)
 * **في كل مرة** يدخل فيها التركيب — أي كلما مرّرتَ القائمة لأعلى ثم لأسفل كانت
 * الصفوف تعود شفافة وتصعد من جديد، وهذا عمل زائد على المعالج أثناء التمرير
 * تحديداً (أسوأ لحظة) ويبدو كوميضاً مزعجاً. الآن تُعرض الحركة **مرة واحدة فقط
 * لكل صف** (تُحفظ بـ rememberSaveable مربوطة بمفتاح الصف في LazyColumn، فتبقى
 * حتى لو خرج الصف من الشاشة وعاد أو دُوِّرت الشاشة)، ولا تُحرَّك الصفوف البعيدة
 * أصلاً. الصف الذي لا يتحرك لا يُنشئ أي coroutine.
 */
@Composable
fun Modifier.listItemEntrance(index: Int): Modifier {
    val tier = LocalPerformanceTier.current
    // حدود الحركة حسب الوضع:
    //  - LOW: أول 4 صفوف فقط وتلاشٍ (alpha) فقط بمدة 150ms (6 إطارات @40Hz) — بلا إزاحة ولا تكبير.
    //  - HIGH: أول 12 صفاً، تلاشٍ + صعود 22dp + تكبير 0.94→1 (حركة غنية).
    val maxIndex = if (tier == PerformanceTier.LOW) 3 else MAX_ANIMATED_INDEX
    val slowDisplay = LocalRefreshRateHz.current < 61f && tier != PerformanceTier.HIGH
    val reduce = MotionSpecs.reduceMotion() || slowDisplay
    val played = rememberSaveable { mutableStateOf(false) }
    // تُحسب مرة واحدة عند دخول هذا الصف التركيب (لا تتغير بعدها) — وهذا ما
    // يجعل الشرط أدناه ثابتاً ولا يقطع الحركة حين نكتب played = true.
    val shouldAnimate = remember { !reduce && !played.value && index <= maxIndex }
    val progress = remember { Animatable(if (shouldAnimate) 0f else 1f) }
    // تعلّمناها من تطبيق الملاحظات (Motion.enter: LAYER_TYPE_NONE عند انتهاء الحركة): graphicsLayer
    // يُبقي RenderNode/طبقة لكل صف حتى بعد انتهاء حركته. هنا يُزال المُعدِّل كلياً بعد الانتهاء،
    // فالصفوف الساكنة (وهي معظم القائمة) تعود بلا أي طبقة رسومية إضافية.
    var finished by remember { mutableStateOf(!shouldAnimate) }
    val durationMs = MotionSpecs.entranceMillis()
    val staggerStepMs = if (tier == PerformanceTier.LOW) 25L else 30L

    if (shouldAnimate) {
        LaunchedEffect(Unit) {
            played.value = true
            val staggerMs = index.coerceAtMost(8) * staggerStepMs
            if (staggerMs > 0) delay(staggerMs)
            progress.animateTo(1f, animationSpec = tween(durationMs, easing = MotionSpecs.claudeEasing))
            finished = true
        }
    }
    if (finished) return this
    return this.graphicsLayer {
        // القراءة هنا داخل كتلة graphicsLayer (مرحلة الرسم) — لا إعادة تركيب.
        val p = progress.value
        alpha = p
        // LOW: بلا طبقة خارج الشاشة (offscreen buffer) لتلاشي الصف — يُطبَّق الشفاف على كل رسمة.
        if (tier != PerformanceTier.HIGH) {
            compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
        }
        if (tier == PerformanceTier.HIGH) {
            translationY = (1f - p) * 22.dp.toPx()
            val sc = 0.94f + 0.06f * p
            scaleX = sc
            scaleY = sc
        }
    }
}

/**
 * بديل animateContentSize يعيد قياس التخطيط في كل إطار، وهو من أغلى الحركات على الأجهزة
 * القديمة؛ في المستوى LOW يتغير الحجم فوراً بلا حركة، وفي غيره يعمل بمواصفات [MotionSpecs].
 */
@Composable
fun Modifier.tierAnimateContentSize(): Modifier =
    if (LocalPerformanceTier.current == PerformanceTier.LOW || MotionSpecs.reduceMotion()) this
    else this.animateContentSize(MotionSpecs.expandSpring())
