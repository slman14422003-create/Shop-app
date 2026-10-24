package com.salman.herbalencyclopedia.data.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

// ═══════════════════════════════════════════════════════════════════════
// نسخة "public" (المستخدم العادي) من AuthRepository.
//
// هذا الملف يحلّ محلّ app/src/full/.../data/repository/AuthRepository.kt
// عند بناء نكهة (flavor) "public" فقط — راجع flavorDimensions/productFlavors
// في app/build.gradle.kts. الهدف: لا قدرة على تسجيل دخول الأدمن إطلاقاً في
// هذه النسخة، حتى لو استُخرِج الـ APK وأُعيدت هندسته.
//
// بقيت نفس الواجهة العامة (نفس أسماء/تواقيع الدوال) المستخدمة من AppViewModel
// حتى يبقى AppViewModel وباقي الشاشات المشتركة (NavGraph, SettingsScreen...)
// كما هي بلا أي تعديل بين النكهتين:
//   - isAdmin: دائماً false هنا.
//   - login(): يرفض فوراً بلا أي اتصال بـ Firebase Auth (لا داعي حتى
//     لمحاولة التحقق من بيانات اعتماد لن تُستخدَم أصلاً).
//   - ensureAnonymousUid()/logout(): تبقيان فعّالتين كما هما — لا علاقة لهما
//     بالأدمن، بل يُستخدَمان في ميزة عادية (إرسال ملاحظات المستخدمين مع هوية
//     مجهولة لمنع إساءة الاستخدام، راجع AppViewModel.submitFeedback وتوثيق
//     ensureAnonymousUid الأصلي في نسخة full من هذا الملف).
// ═══════════════════════════════════════════════════════════════════════

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

    /** لا يوجد أدمن في هذه النسخة إطلاقاً. */
    val isAdmin: Boolean
        get() = false

    suspend fun login(email: String, password: String): AuthResult {
        return AuthResult(
            success = false,
            message = "تسجيل الدخول غير متاح في هذه النسخة من التطبيق."
        )
    }

    fun logout() {
        auth.signOut()
    }

    /** نفس سلوك نسخة full تماماً — ميزة عادية غير مرتبطة بالأدمن (راجع التوثيق أعلاه). */
    suspend fun ensureAnonymousUid(): String? {
        auth.currentUser?.uid?.let { return it }
        return try {
            auth.signInAnonymously().await().user?.uid
        } catch (e: Exception) {
            null
        }
    }
}
