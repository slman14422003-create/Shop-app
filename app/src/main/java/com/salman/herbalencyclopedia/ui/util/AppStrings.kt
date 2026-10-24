package com.salman.herbalencyclopedia.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.salman.herbalencyclopedia.data.translate.TranslationRepository

/**
 * قاموس ثابت لأكثر عبارات واجهة التطبيق (الأزرار، التبويبات، العناوين
 * الأساسية) تكراراً. التراجم هنا يدوية ومضبوطة بعناية بدل تمريرها دوماً
 * على ترجمة جوجل، لأنها عبارات قصيرة متكررة تظهر في كل شاشة تقريباً
 * وتستحق دقة أعلى وسرعة فورية بلا أي طلب شبكة. أي نص عربي غير موجود هنا
 * يُترجم تلقائياً وحيّاً عبر [TranslationRepository] (واجهة ترجمة جوجل
 * المجانية) ويُخزَّن مؤقتاً على القرص بعد أول ترجمة — راجع [tr].
 */
private val staticDictionary: Map<String, String> = mapOf(
    "موسوعة الأعشاب" to "Herbal Encyclopedia",
    "دليلك الطبيعي للمعرفة بالأعشاب" to "Your natural guide to herbal knowledge",
    "جاري تحميل البيانات..." to "Loading data...",
    "تعذّر تحميل البيانات" to "Couldn't load data",
    "جاري الاتصال بالخادم..." to "Connecting to server...",
    "جاري تحميل الأعشاب والتصنيفات..." to "Loading herbs and categories...",
    "جاري حفظ البيانات..." to "Saving data...",
    "اكتمل التحميل" to "Download complete",
    "تحقق من الإنترنت، وسنعيد المحاولة تلقائيًا" to "Check your internet, we will retry automatically",
    "إعادة المحاولة" to "Retry",
    // التنقل الرئيسي
    "الرئيسية" to "Home",
    "بحث" to "Search",
    "بحث..." to "Search...",
    "سيمو" to "Semo",
    "الخلطات" to "Blends",
    "كل الأعشاب" to "All Herbs",
    "الأعشاب" to "Herbs",
    "المفضلة" to "Favorites",
    "الإعدادات" to "Settings",
    "المساعدة" to "Help",
    "الدعم الفني" to "Support",
    "سياسة الخصوصية" to "Privacy Policy",
    "الشروط والأحكام" to "Terms & Conditions",

    // أفعال/أزرار شائعة
    "رجوع" to "Back",
    "حفظ" to "Save",
    "إلغاء" to "Cancel",
    "مسح" to "Clear",
    "تعديل" to "Edit",
    "حذف" to "Delete",
    "إضافة" to "Add",
    "إغلاق المعاينة" to "Close preview",

    // الرئيسية
    "موسوعة الأعشاب الطبية" to "Herbal Encyclopedia",
    // تحية الشريط العلوي — راجع greetingForNow في HomeScreen.kt لسبب وجود
    // عدة صيغ لكل فترة يومية بدل جملتين ثابتتين فقط.
    "أهلاً بك، صباح الخير" to "Welcome, good morning",
    "صباح الخير، يومك مليء بالنشاط" to "Good morning, have an energetic day",
    "صباح النور، أهلاً بعودتك" to "Good morning, welcome back",
    "أهلاً بك، بداية موفّقة ليومك" to "Welcome, here's to a great start to your day",
    "أهلاً بك، نهارك سعيد" to "Welcome, have a great day",
    "طاب نهارك، أهلاً بعودتك" to "Good day, welcome back",
    "أهلاً بك، وقت رائع لاستكشاف الأعشاب" to "Welcome, a great time to explore herbs",
    "نهارك مليء بالصحة والعافية" to "Wishing you a healthy, wonderful day",
    "أهلاً بك، مساء الخير" to "Welcome, good evening",
    "مساء النور، أهلاً بعودتك" to "Good evening, welcome back",
    "أهلاً بك، أمسية طيبة" to "Welcome, have a pleasant evening",
    "مساء الخير، وقت هادئ لتصفّح الموسوعة" to "Good evening, a calm time to browse the encyclopedia",
    "أهلاً بك، ليلة سعيدة" to "Welcome, good night",
    "طابت ليلتك، أهلاً بعودتك" to "Good night, welcome back",
    "أهلاً بك، سهرة هادئة معك" to "Welcome, wishing you a calm evening",
    "ليلتك طيبة، أهلاً بك من جديد" to "Good night, welcome back again",
    "لوحة التحكم" to "Admin Panel",

    // شاشة تفاصيل العشبة
    "الفوائد" to "Benefits",
    "طريقة الاستخدام" to "How to Use",
    "التحذيرات" to "Warnings",
    "الأضرار المحتملة" to "Possible Harms",
    "ملاحظات إضافية" to "Additional Notes",
    "الإبلاغ عن خطأ بالمعلومات" to "Report incorrect information",

    // شاشة إضافة/تعديل العشبة (المطوّر)
    "التصنيف" to "Category",
    "اسم العشبة" to "Herb Name",
    "صورة العشبة" to "Herb Image",
    "اختيار صورة" to "Choose Image",
    "جاري ضغط الصورة..." to "Compressing image...",
    "جاري تحميل بيانات الموسوعة..." to "Loading encyclopedia data...",
    "جاري تحميل الأعشاب..." to "Loading herbs...",
    "بدون تصنيف" to "Uncategorized",

    // الإعدادات
    "المظهر" to "Appearance",
    "الأداء" to "Performance",
    "التحديثات" to "Updates",
    "الحساب" to "Account",
    "اللغة" to "Language",
    "لغة التطبيق" to "App Language",
    "يترجم محتوى التطبيق تلقائياً عبر ترجمة جوجل" to "Automatically translates app content via Google Translate",
    "العربية" to "Arabic",
    "الإنجليزية" to "English"
)

/**
 * يترجم نصاً عربياً ثابتاً إلى الإنجليزية عند اختيار المستخدم للغة
 * الإنجليزية من الإعدادات، وإلا يعيده كما هو دون أي معالجة. يبحث أولاً في
 * القاموس اليدوي أعلاه (فوري وبلا شبكة)، فإن لم يجد النص هناك يستخدم
 * ترجمة جوجل المجانية حيّاً مع تخزين مؤقت محلي دائم (راجع
 * [TranslationRepository]) — يظهر النص العربي الأصلي للحظة قصيرة ريثما
 * تصل الترجمة أول مرة فقط، ثم يتحدّث تلقائياً ويُصبح فورياً في كل مرة تالية.
 */
@Composable
fun tr(arabic: String): String {
    val language = LocalAppLanguage.current
    if (language == AppLanguage.ARABIC || arabic.isBlank()) return arabic
    staticDictionary[arabic]?.let { return it }

    val context = LocalContext.current
    val repository = remember(context) { TranslationRepository.shared(context) }
    val translated by produceState(initialValue = arabic, arabic, language) {
        value = repository.translate(arabic, targetLang = "en", sourceLang = "ar")
    }
    return translated
}
