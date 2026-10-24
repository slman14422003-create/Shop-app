package com.salman.herbalencyclopedia

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * دفاع إضافي (طبقة ثانية) ضد نسخة مُعدَّلة من التطبيق يُعاد توقيعها بمفتاح
 * غير مفتاح التوقيع الرسمي - وهو بالضبط ما تُنتجه دورة "فكّ بـapktool، تعديل
 * الكود/الموارد، إعادة توقيع، إعادة تجميع" التي طُلب التصدي لها. لا توجد أي
 * طريقة لمنع apktool من فكّ تجميع APK أو عرض/تعديل نسخة محلية منه بالكامل -
 * هذا قيد فيزيائي في أي تطبيق يعمل على جهاز المستخدم، وليس خللاً يمكن
 * "إصلاحه" بالكامل. ما يمكن فعله واقعياً هو رفع كلفة الاستغلال وقطع أي فائدة
 * منه:
 *   ١) R8/ProGuard (مُفعَّل أصلاً في build.gradle.kts) يُعمّي أسماء الأصناف/
 *      الدوال في كل حزمة الإصدار، فتصبح قراءة الكود المُفكَّك أصعب بكثير من
 *      كود Kotlin الأصلي.
 *   ٢) Firebase App Check بمزوّد Play Integrity (مُفعَّل أصلاً - انظر
 *      FirebaseSecurity.kt وapp/src/release/.../FirebaseSecurityProvider.kt)
 *      يرفض من طرف الخادم (Firestore) أي طلب من نسخة لم تُثبت أنها مُوقَّعة
 *      بالشهادة الرسمية ومُثبَّتة عبر قناة موثوقة (Play أساساً) - وهذا يحمي
 *      البيانات على الخادم بصرف النظر عمّا يفعله أي شخص بنسخته المحلية.
 *   ٣) هذا الملف: فحص محلي إضافي يقارن شهادة توقيع الحزمة الحالية فعلياً على
 *      الجهاز بشهادة الإصدار الرسمي المتوقّعة (EXPECTED_SIGNATURE_SHA256 -
 *      تُضبط في app/build.gradle.kts من keystore.properties أو متغيّر بيئة
 *      CI، ولا تُكتب بشكل صريح داخل الكود المصدري). يُستخدم كطبقة دفاع محلية
 *      إضافية (خصوصاً لو أُعيد تحزيم التطبيق وتوزيعه خارج Play حيث قد لا
 *      يكتمل تحقّق Play Integrity بنفس القوة) - انظر استخدامه في
 *      AuthRepository.login(): نسخة مُعاد توقيعها لا يمكنها إطلاقاً الدخول
 *      إلى لوحة الإدارة حتى لو عرف المهاجم بيانات اعتماد صحيحة.
 *
 * ملاحظة: هذا الفحص يبقى معطَّلاً بالكامل (isTampered() تُعيد false دائماً)
 * ما لم تُضبَط EXPECTED_SIGNATURE_SHA256 فعلياً بقيمة حقيقية - فلا يُقفَل
 * أي بناء محلي/تجريبي بالخطأ قبل أن يُهيّئ المطوّر القيمة الصحيحة.
 */
internal object SecurityUtils {

    /**
     * true إذا كانت شهادة توقيع الحزمة المُثبَّتة فعلياً لا تطابق شهادة
     * الإصدار الرسمي المتوقَّعة - أي أن هذه نسخة أُعيد توقيعها بمفتاح آخر
     * (نموذجي بعد تعديل عبر apktool أو أي أداة فكّ/إعادة تجميع مشابهة).
     */
    fun isTampered(context: Context): Boolean {
        val expected = BuildConfig.EXPECTED_SIGNATURE_SHA256
        if (expected.isBlank()) return false // الفحص معطَّل حتى يُضبط المطوّر القيمة الحقيقية.
        val actual = currentSignatureSha256(context) ?: return true // تعذّر القراءة يُعامَل كمشبوه أيضاً.
        return !actual.equals(expected, ignoreCase = true)
    }

    private fun currentSignatureSha256(context: Context): String? = try {
        val pm = context.packageManager
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
            val signingInfo = info.signingInfo
            if (signingInfo == null) return null
            if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners else signingInfo.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            info.signatures
        }
        val signature = signatures?.firstOrNull() ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
        digest.joinToString("") { "%02X".format(it) }
    } catch (_: Exception) {
        null
    }
}
