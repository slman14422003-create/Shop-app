package com.salman.herbalencyclopedia.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.salman.herbalencyclopedia.ui.theme.AppMotion
import com.salman.herbalencyclopedia.ui.theme.rememberSmoothMotionAllowed
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.salman.herbalencyclopedia.ui.theme.LocalPerformanceMode

/**
 * خلفية زخرفية هادئة تُوضع مرة واحدة خلف NavHost بالكامل (بدل خلفية مسطّحة
 * فارغة في كل شاشة على حدة): تدرّج أساسي بلوني الهوية الحاليين
 * (primary/tertiary من ColorScheme، فتتبدّل تلقائياً مع أي لوحة ألوان أو
 * الوضع الداكن)، فقاعتا ضوء كبيرتان مموَّهتان في الزوايا لإحساس عمق هادئ،
 * وورقتا زخرفة خافتتان جداً (نفس أيقونة Spa المستخدمة في شاشة البداية)
 * لهوية نباتية متسقة عبر التطبيق كله.
 *
 * كل الطبقات "الثقيلة" (تمويه RenderEffect + نبض التوهّج اللانهائي) تُقرأ
 * من [LocalPerformanceMode] وتُستبعد بالكامل في الوضع الاقتصادي — بلا أي
 * تكلفة رسم إضافية، تماماً بنفس مبدأ LiquidGlassSurface.
 *
 * ═══ إصلاح: التوهّج اللانهائي لا يراعي معدّل التحديث الفعلي ═══
 * نبض التوهّج كان مرتبطاً فقط بـ[LocalPerformanceMode] (اختيار المستخدم)،
 * بخلاف كل حركة متصلة أخرى بالتطبيق (staggeredEntrance، entranceFade،
 * فقاعة "سيمو يكتب…"، انتقالات التنقّل بين الشاشات) التي تحترم أيضاً
 * [rememberSmoothMotionAllowed] — أي تتوقف تلقائياً حين يكون معدّل التحديث
 * الفعلي الحالي أقل من 60Hz (شاشة LTPO خفّضت المعدل توفيراً للطاقة، أو
 * توفير الطاقة/التحكّم الحراري أرجعا الوضع الفعلي لاقتصادي عبر
 * rememberEffectivePerformanceMode) حتى لو كان اختيار المستخدم "أداء
 * عالٍ" لا يزال قائماً. خلفية طافية خلف كل شاشة بالتطبيق تقريباً كانت
 * تبقى الاستثناء الوحيد الذي يتجاهل هذا — نُوحّدها الآن مع بقية التطبيق.
 * لون التوهّج الثابت (0.16f) لم يتغيّر: هو نفس القيمة السابقة لوضع "بلا
 * حركة".
 *
 * ونفس مبدأ الحركات الأخرى بمنحنى [AppMotion.Smooth] الموحّد للتطبيق بدل
 * منحنى Compose الافتراضي الذي كان يُستخدم هنا فقط (tween(4200) بلا
 * easing صريح).
 *
 * كل شاشة يجب أن تجعل containerColor الخاص بـ Scaffold شفافاً
 * (Color.Transparent) كي تظهر هذه الخلفية من خلفها بدل حجبها.
 */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier) {
    val highQuality = LocalPerformanceMode.current.isHighQuality
    val smoothMotionAllowed = rememberSmoothMotionAllowed()
    val scheme = MaterialTheme.colorScheme
    val primary = scheme.primary
    val tertiary = scheme.tertiary

    val glowAlpha = if (smoothMotionAllowed) {
        val transition = rememberInfiniteTransition(label = "ambientGlow")
        val animated by transition.animateFloat(
            initialValue = 0.20f,
            targetValue = 0.32f,
            animationSpec = infiniteRepeatable(tween(4200, easing = AppMotion.Smooth), RepeatMode.Reverse),
            label = "ambientGlowAlpha"
        )
        animated
    } else 0.16f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        // فقاعة ضوء علوية-يمينية بلون Primary.
        BlurredBlob(
            color = primary,
            alpha = glowAlpha,
            size = 260.dp,
            highQuality = highQuality,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 90.dp, y = (-90).dp)
        )
        // فقاعة ضوء سفلية-يسارية بلون Tertiary لتوازن بصري بلونين متكاملين.
        // الإزاحة y الموجبة سابقاً (+120dp) كانت تدفع الفقاعة أسفل حافة
        // الشاشة الفعلية أكثر فأكثر رغم أنها أصلاً محاذاة لأسفل الشاشة
        // (BottomStart) — فتظهر غالباً مقصوصة تقريباً بالكامل، تاركة
        // المنطقة السفلية (حيث يطفو الشريط العائم الآن فوقها مباشرة) شبه
        // خالية من أي لون حي يستحق تمويهه خلف الزجاج. إزاحة سالبة ترفعها
        // لتظهر فعلياً في النصف السفلي المرئي من الشاشة.
        BlurredBlob(
            color = tertiary,
            alpha = glowAlpha * 0.85f,
            size = 300.dp,
            highQuality = highQuality,
            modifier = Modifier.align(Alignment.BottomStart).offset(x = (-80).dp, y = (-30).dp)
        )
        // فقاعة ثالثة صغيرة بلون Primary أسفل-يمين، تحديداً في المنطقة
        // التي يطفو فوقها الشريط العائم السفلي بمعظم الشاشات (منتصف/يمين
        // أسفل الشاشة) — لضمان وجود لون حي واضح خلف الشريط دائماً بدل
        // الاعتماد فقط على امتداد الفقاعتين الكبيرتين اللتين قد لا
        // تصلانه على كل أحجام الشاشات.
        BlurredBlob(
            color = primary,
            alpha = glowAlpha * 0.9f,
            size = 220.dp,
            highQuality = highQuality,
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = 40.dp, y = (-10).dp)
        )

        // تدرّج خافت جداً فوق الفقاعات ليوحّد السطح ولا يترك حواف واضحة.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, scheme.background.copy(alpha = 0.55f))
                    )
                )
        )

        // وريقتان زخرفيتان خافتتان جداً — نفس هوية شاشة البداية، بحضور
        // بصري شبه معدوم كي لا تُلهي عن المحتوى فوقها.
        // أُزيلت الورقة العلوية: كانت تظهر كقوس فاتح غريب أسفل الهيدر.
        Icon(
            imageVector = Icons.Filled.Spa,
            contentDescription = null,
            tint = scheme.onBackground.copy(alpha = 0.03f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 34.dp, y = 40.dp)
                .size(190.dp)
                .rotate(154f)
        )
    }
}

@Composable
private fun BlurredBlob(
    color: Color,
    alpha: Float,
    size: Dp,
    highQuality: Boolean,
    modifier: Modifier = Modifier
) {
    // إصلاح "المستطيلات الحادة": RenderEffect blur داخل graphicsLayer كان
    // يُقصّ عند حدود الطبقة (مربّع بحجم size) لأن الدائرة تملأها بالكامل،
    // فيظهر ضوء مقصوص بحواف مستطيلة على الشاشة. التدرّج الشعاعي ينتهي
    // بشفافية 0 قبل الحافة تماماً، فلا حواف أبداً، وهو أخفّ كلفة على
    // المعالج من التمويه الحقيقي ويعمل على كل إصدارات أندرويد.
    val peak = if (highQuality) alpha else alpha * 0.5f
    Box(
        modifier = modifier
            .size(size)
            .background(
                Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to color.copy(alpha = peak),
                        0.45f to color.copy(alpha = peak * 0.55f),
                        1.0f to Color.Transparent
                    )
                ),
                CircleShape
            )
    )
}
