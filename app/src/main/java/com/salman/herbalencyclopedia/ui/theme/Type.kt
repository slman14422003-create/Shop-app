package com.salman.herbalencyclopedia.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.salman.herbalencyclopedia.R

/**
 * خط "Tajawal" العربي عبر Google Fonts (Downloadable Fonts): يُحمَّل من
 * جهاز المستخدم وقت التشغيل عبر خدمات Google Play، فلا يحتاج ملف خط
 * داخل المشروع — لكنه يحتاج اتصال إنترنت أول مرة على جهاز المستخدم فقط
 * (يُخزَّن محلياً بعدها). Tajawal أنسب للطابع "الطبي/النباتي" الهادئ
 * للتطبيق من الخط الافتراضي. شهادات الموفّر في res/values/font_certs.xml.
 */
private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val tajawal = GoogleFont("Tajawal")

/**
 * FontFamily كاملة بكل الأوزان المستخدمة بالتطبيق. إن فشل التحميل (بلا
 * إنترنت أو تعذّر التحقق من الموفّر) يتراجع Compose تلقائياً لخط النظام
 * الافتراضي بلا أي كسر بصري.
 */
val TajawalFontFamily = FontFamily(
    Font(googleFont = tajawal, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = tajawal, fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = tajawal, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = tajawal, fontProvider = googleFontProvider, weight = FontWeight.Bold)
)

val HerbalTypography = Typography(
    headlineLarge = TextStyle(fontFamily = TajawalFontFamily, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = TajawalFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = TajawalFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = TajawalFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = TajawalFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = TajawalFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontFamily = TajawalFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
)


/**
 * قبل هذا التعديل كان حجم النص يقدّم 3 مراحل فقط (عادي/كبير/أكبر) بحد
 * أقصى 1.30x — غير كافٍ لمن يحتاج تكبيراً أوضح لصعوبة في الرؤية. الآن 5
 * مراحل تدريجية تصل حتى 1.55x، بخطوات أصغر بين المراحل الأولى (لتغيير
 * لطيف قابل للملاحظة) وخطوات أوسع قرب النهاية (لمن يحتاج تكبيراً حقيقياً).
 */
val FontScaleFactors = listOf(1f, 1.12f, 1.24f, 1.38f, 1.55f)

fun Typography.scaled(level: Int): Typography {
    val factor = FontScaleFactors[level.coerceIn(0, FontScaleFactors.lastIndex)]
    fun TextStyle.s() = copy(fontSize = fontSize * factor, lineHeight = lineHeight * factor)
    return copy(headlineLarge=headlineLarge.s(), headlineMedium=headlineMedium.s(), titleLarge=titleLarge.s(), titleMedium=titleMedium.s(), bodyLarge=bodyLarge.s(), bodyMedium=bodyMedium.s(), labelLarge=labelLarge.s())
}
