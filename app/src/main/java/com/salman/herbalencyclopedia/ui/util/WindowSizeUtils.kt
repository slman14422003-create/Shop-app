package com.salman.herbalencyclopedia.ui.util

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * محرك اكتشاف حجم الشاشة الحقيقي للتطبيق.
 *
 * قبل هذا الملف لم يكن هناك أي تمييز بين جوال وتابلت في كل الشاشات:
 * شبكة التصنيفات في [com.salman.herbalencyclopedia.ui.screens.home.HomeScreen]
 * كانت GridCells.Fixed(2) بشكل ثابت، وقوائم الأعشاب (كل الأعشاب/المفضلة/
 * التصنيف/البحث) كانت LazyColumn بعمود واحد دائماً. على تابلت أو نافذة
 * مكبّرة (شاشة مقسومة / طي) هذا يعني بطاقة واحدة تتمدد بعرض الشاشة كاملاً
 * أو شبكة بعمودين فقط تترك فراغاً هائلاً على الجانبين — نفس التخطيط
 * تماماً على جوال 360dp وتابلت 1280dp.
 *
 * الاعتماد هنا على LocalConfiguration.screenWidthDp/screenHeightDp بدل حجم
 * ثابت لمرة واحدة، لأن هذه القيم تُحدَّث تلقائياً مع كل تغيير حقيقي بالحجم
 * المتاح فعلياً للتطبيق (تدوير الجهاز، سحب حجم نافذة في وضع الشاشة
 * المقسومة، فتح/طي جهاز قابل للطي) — فيعيد remember(widthDp, heightDp)
 * حساب التصنيف فوراً بلا حاجة لإعادة تشغيل الشاشة، وبلا الاعتماد على
 * DisplayMetrics الثابتة عند الإقلاع فقط والتي لا تلتقط هذه التغييرات.
 * هذا ما يجعله "ديناميكياً ودقيقاً": يقرأ من نفس مصدر الحقيقة الذي
 * يستخدمه Compose نفسه لقياس الشاشة، لا رقماً مفترضاً مسبقاً.
 *
 * حدود التصنيف (600dp / 840dp) هي نفسها المعتمدة رسمياً من Material 3
 * لـ Window Size Classes (compact/medium/expanded)، فتُطابق سلوك تطبيقات
 * أندرويد الأخرى بدل عتبات مخترعة قد تُصنّف تابلت صغير كجوال أو العكس.
 */
enum class ScreenWidthClass { COMPACT, MEDIUM, EXPANDED }
enum class ScreenHeightClass { COMPACT, MEDIUM, EXPANDED }

/**
 * تصنيف كثافة البكسل (DPI) الرسمي المعتمد من Android نفسه — نفس الأسماء
 * ونفس العتبات التي تستخدمها لواحق موارد res/mipmap-*dpi بالمشروع
 * (ldpi/mdpi/hdpi/tvdpi/xhdpi/xxhdpi/xxxhdpi)، محسوبة هنا من
 * DisplayMetrics.densityDpi الحقيقي للجهاز بدل قيمة مفترضة. يسمح هذا لأي
 * جزء من الواجهة بالتكيّف مع الكثافة الفعلية عند الحاجة (مثلاً طلب حجم
 * صورة أدق على xxxhdpi، أو تفادي تفاصيل زخرفية باهظة على أجهزة قديمة
 * منخفضة الكثافة) دون تخمين، ودون إعادة اختراع القيم التي عرّفتها Android
 * أصلاً لكل دلاء res/mipmap-* — بل نقرأها من نفس المصدر الذي يستخدمه
 * النظام لاختيار مجلد الموارد المناسب لهذا الجهاز.
 */
enum class DensityBucket { LDPI, MDPI, HDPI, TVDPI, XHDPI, XXHDPI, XXXHDPI }

data class WindowSizeInfo(
    val widthDp: Int,
    val heightDp: Int,
    val widthClass: ScreenWidthClass,
    val heightClass: ScreenHeightClass,
    /** true من عرض 600dp فأكثر: تابلت، جوال أفقي كبير، أو نافذة مقسومة عريضة. */
    val isTablet: Boolean,
    val isLandscape: Boolean,
    /** true من MEDIUM فأعلى: شاشة تتّسع لشريط تنقّل جانبي بدل شريط عائم سفلي. */
    val useNavigationRail: Boolean,
    /** الحد الأدنى المقترح لعرض خلية الشبكة، يكبر قليلاً على الشاشات الواسعة. */
    val gridMinCellWidth: Dp,
    /** أقصى عرض مقترح لعمود محتوى نصي/قوائم كي لا يتمدد بلا حدود على تابلت عريض. */
    val contentMaxWidth: Dp,
    /** كثافة البكسل الخام (DisplayMetrics.densityDpi)، مثال: 160، 320، 480... */
    val densityDpi: Int,
    /** معامل الكثافة (1f = mdpi المرجعية، 3f = xxxhdpi...)، نفسه الذي يحوّل dp إلى بكسل فعلي. */
    val density: Float,
    /** تصنيف الكثافة المطابق لأسماء دلاء res/mipmap-*dpi بالمشروع. */
    val densityBucket: DensityBucket,
    /** مقياس تكبير الخط الذي يضبطه المستخدم من إعدادات النظام (إتاحة/سهولة الوصول). */
    val fontScale: Float
)

@Composable
fun rememberWindowSizeInfo(): WindowSizeInfo {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val widthDp = configuration.screenWidthDp
    val heightDp = configuration.screenHeightDp
    val densityDpi = configuration.densityDpi
    return remember(widthDp, heightDp, densityDpi, density.density, configuration.fontScale) {
        val widthClass = when {
            widthDp < 600 -> ScreenWidthClass.COMPACT
            widthDp < 840 -> ScreenWidthClass.MEDIUM
            else -> ScreenWidthClass.EXPANDED
        }
        val heightClass = when {
            heightDp < 480 -> ScreenHeightClass.COMPACT
            heightDp < 900 -> ScreenHeightClass.MEDIUM
            else -> ScreenHeightClass.EXPANDED
        }
        // نفس عتبات android.util.DisplayMetrics (DENSITY_LOW=120،
        // DENSITY_MEDIUM=160، DENSITY_HIGH=240، DENSITY_TV=213،
        // DENSITY_XHIGH=320، DENSITY_XXHIGH=480، DENSITY_XXXHIGH=640)
        // لضمان تطابق تام مع الدلاء التي يختار النظام منها موارده فعلاً،
        // فلا يوجد احتمال لتصنيف جهاز بكثافة معيّنة ضمن دلو مختلف عمّا
        // يستخدمه النظام نفسه لموارد mipmap.
        val densityBucket = when {
            densityDpi <= 120 -> DensityBucket.LDPI
            densityDpi <= 160 -> DensityBucket.MDPI
            densityDpi <= 213 -> DensityBucket.TVDPI
            densityDpi <= 240 -> DensityBucket.HDPI
            densityDpi <= 320 -> DensityBucket.XHDPI
            densityDpi <= 480 -> DensityBucket.XXHDPI
            else -> DensityBucket.XXXHDPI
        }
        WindowSizeInfo(
            widthDp = widthDp,
            heightDp = heightDp,
            widthClass = widthClass,
            heightClass = heightClass,
            isTablet = widthDp >= 600,
            isLandscape = widthDp > heightDp,
            useNavigationRail = widthClass != ScreenWidthClass.COMPACT,
            // مقياس تكبير الخط (fontScale) يُضاف هنا فوق الحد الأدنى الأساسي:
            // بدونه، مستخدم رفع حجم الخط من إعدادات النظام (سهولة الوصول)
            // على تابلت بشبكة متعددة الأعمدة قد يجد نص العنوان/الفائدة
            // مقصوصاً (ellipsis مبكر) لأن عرض الخلية حُسب فقط من عرض
            // الشاشة بلا اعتبار لحجم الخط الفعلي المعروض داخلها.
            gridMinCellWidth = (when (widthClass) {
                ScreenWidthClass.COMPACT -> 152.dp
                ScreenWidthClass.MEDIUM -> 168.dp
                ScreenWidthClass.EXPANDED -> 180.dp
            }) * configuration.fontScale.coerceIn(1f, 1.5f),
            contentMaxWidth = when (widthClass) {
                ScreenWidthClass.COMPACT -> Dp.Unspecified
                ScreenWidthClass.MEDIUM -> 760.dp
                ScreenWidthClass.EXPANDED -> 960.dp
            },
            densityDpi = densityDpi,
            density = density.density,
            densityBucket = densityBucket,
            fontScale = configuration.fontScale
        )
    }
}

/**
 * يميّز نمط التنقّل الحالي بالجهاز: true لتنقّل الإيماءات (سحب من الحافة،
 * حاجز سفلي رفيع)، false لأزرار التنقل التقليدية الثلاثة (حاجز أثخن
 * بوضوح). القياس هنا حي عبر WindowInsets.navigationBars فيتحدّث تلقائياً
 * إن بدّل المستخدم نمط التنقّل من الإعدادات دون إغلاق التطبيق، بدل قراءة
 * ثابتة عند الإقلاع فقط.
 *
 * يُستخدم لضبط المسافة حول شريط التنقّل العائم [com.salman.herbalencyclopedia.ui.components.OneUiFloatingNavBar]
 * كي لا يبتعد كثيراً عن حافة الشاشة في وضع الإيماءات، أو يلتصق بأزرار
 * التنقل التقليدية في الوضع الآخر.
 */
@Composable
fun isGestureNavigation(): Boolean {
    val density = LocalDensity.current
    val bottomInsetPx = WindowInsets.navigationBars.getBottom(density)
    val bottomInsetDp = with(density) { bottomInsetPx.toDp() }
    return bottomInsetDp < 32.dp
}

/**
 * Wraps a single-column "reading" screen (herb/blend details, settings, help, terms,
 * privacy policy, admin tools/settings…) so its content is capped at
 * [WindowSizeInfo.contentMaxWidth] and centered, instead of stretching edge-to-edge.
 *
 * [rememberWindowSizeInfo] already computed a sensible [WindowSizeInfo.contentMaxWidth]
 * for exactly this purpose (960.dp on an EXPANDED window, 760.dp on MEDIUM, unconstrained
 * on COMPACT/phone), but nothing in the app actually applied it anywhere — grids used
 * `GridCells.Adaptive` (which self-adjusts its column count from available width, so they
 * were fine on their own), but every plain `LazyColumn`-based text/detail screen still just
 * did `Modifier.fillMaxWidth()` inside the Scaffold. On a tablet or a wide split-screen
 * window that meant paragraphs of herb benefits/warnings, or a settings/admin list, stretched
 * the full width of the screen — lines of Arabic text far too long to read comfortably, and
 * list rows with huge empty trailing space. This finally wires that unused field in.
 *
 * On COMPACT (phone) windows [WindowSizeInfo.contentMaxWidth] is `Dp.Unspecified`, so this
 * is a no-op there — phone layouts are completely unaffected.
 */
@Composable
fun ResponsiveScreenContent(
    windowInfo: WindowSizeInfo,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(
            modifier = if (windowInfo.contentMaxWidth == Dp.Unspecified) {
                Modifier.fillMaxSize()
            } else {
                Modifier.fillMaxSize().widthIn(max = windowInfo.contentMaxWidth)
            },
            content = content
        )
    }
}
