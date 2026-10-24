package com.salman.herbalencyclopedia.data.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.salman.herbalencyclopedia.HerbalApp
import com.salman.herbalencyclopedia.SecurityUtils
import kotlinx.coroutines.tasks.await

data class AuthResult(
    val success: Boolean,
    val isAdmin: Boolean = false,
    val message: String? = null
)

class AuthRepository(
    private val appContext: Context,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    val currentUser get() = auth.currentUser

    /** The only account that may access the administration session. */
    val isAdmin: Boolean
        get() = auth.currentUser?.uid == HerbalApp.ADMIN_UID

    suspend fun login(email: String, password: String): AuthResult {
        // طبقة الدفاع المحلية الإضافية (انظر توثيق SecurityUtils.kt الكامل):
        // نسخة أُعيد توقيعها بمفتاح غير رسمي لا تصل حتى إلى محاولة تسجيل
        // الدخول - يُرفض الطلب محلياً قبل أي اتصال بـFirebase Auth أصلاً،
        // بصرف النظر عن صحة بيانات الاعتماد المُدخَلة.
        if (SecurityUtils.isTampered(appContext)) {
            return AuthResult(
                success = false,
                message = "تعذّر تسجيل الدخول من هذه النسخة من التطبيق."
            )
        }
        return try {
            val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
            val user = result.user
            if (user?.uid != HerbalApp.ADMIN_UID) {
                // A valid Firebase account is not enough: this app is admin-only.
                auth.signOut()
                AuthResult(
                    success = false,
                    message = "هذا الحساب غير مخوّل بالدخول إلى لوحة الإدارة."
                )
            } else {
                AuthResult(success = true, isAdmin = true)
            }
        } catch (e: Exception) {
            // Never expose Firebase's raw exception text; it can reveal account
            // state, backend details, or implementation-specific information.
            AuthResult(
                success = false,
                message = "تعذّر تسجيل الدخول. تحقق من بيانات المسؤول وحاول مرة أخرى."
            )
        }
    }

    fun logout() {
        auth.signOut()
    }

    /**
     * يضمن وجود جلسة Firestore Auth مجهولة (Anonymous Auth) قبل أي عملية
     * يحتاج فيها التطبيق لهوية ثابتة للجهاز — حالياً فقط عند إرسال ملاحظة
     * (انظر submitFeedback في AppViewModel). بلا هذه الهوية لا توجد أي طريقة
     * لتمييز "من أرسل هذه الملاحظة" لاحقاً، وبالتالي لا طريقة لحظر مُرسِل
     * مسيء عن الإرسال مجدداً (راجع قاعدة blocked_users في firestore.rules).
     *
     * إن كان هناك بالفعل مستخدم مسجَّل دخوله (المسؤول أو جلسة مجهولة سابقة)
     * تُستخدَم هويته كما هي بلا استبدال. تُعيد null فقط عند فشل الاتصال، وعندها
     * تُرسَل الملاحظة بلا "sender_uid" (يرفضها Firestore حسب القاعدة الجديدة،
     * فيظهر للمُرسِل خطأ اتصال بدل فشل صامت).
     */
    suspend fun ensureAnonymousUid(): String? {
        auth.currentUser?.uid?.let { return it }
        return try {
            auth.signInAnonymously().await().user?.uid
        } catch (e: Exception) {
            null
        }
    }
}
