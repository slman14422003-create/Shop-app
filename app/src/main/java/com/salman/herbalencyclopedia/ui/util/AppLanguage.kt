package com.salman.herbalencyclopedia.ui.util

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.compositionLocalOf
import java.util.Locale

/**
 * لغات واجهة التطبيق المدعومة. إضافة لغة جديدة مستقبلاً تحتاج فقط توسيع
 * هذا التعداد (وإضافة ترجماتها إلى القاموس الثابت في AppStrings.kt إن
 * رُغب بترجمة فورية بلا شبكة لعباراتها الأكثر تكراراً).
 */
enum class AppLanguage(val code: String, val nativeName: String) {
    ARABIC("ar", "العربية"),
    ENGLISH("en", "English");

    companion object {
        fun fromCode(code: String?): AppLanguage = entries.firstOrNull { it.code == code } ?: ARABIC
    }
}

/**
 * تُوفَّر من MainActivity (بعد قراءة تفضيل المستخدم المحفوظ عبر
 * PreferencesRepository.appLanguage) ليقرأها أي Composable في الشجرة عبر
 * [tr] لمعرفة اللغة الحالية دون تمريرها يدوياً كمعامل في كل شاشة.
 */
val LocalAppLanguage = compositionLocalOf { AppLanguage.ARABIC }

/**
 * يغلّف [context] بإعدادات Configuration/Locale للغة [languageCode]، لضمان
 * أن كل موارد Android (اتجاه التخطيط الافتراضي عبر android:supportsRtl،
 * وأي مورد @string لاحق مثل values-en/strings.xml) تتبع اللغة التي اختارها
 * المستخدم من داخل التطبيق، بدل لغة نظام الجهاز نفسه — تماماً كما تفعل
 * تطبيقات كثيرة توفّر تبديل لغة داخلي مستقل عن إعدادات النظام.
 */
object LocaleManager {
    fun wrapContext(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        val localeList = LocaleList(locale)
        LocaleList.setDefault(localeList)
        config.setLocales(localeList)
        return context.createConfigurationContext(config)
    }
}
