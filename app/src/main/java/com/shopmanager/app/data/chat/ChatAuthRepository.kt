package com.shopmanager.app.data.chat

import android.content.Context
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * الحسابات: إنشاء حساب مرة واحدة (بريد + كلمة مرور + اسم مستخدم فريد)، ثم تسجيل دخول
 * وتسجيل خروج بلا حد. الجلسة تُحفظ تلقائياً من Firebase Auth فلا يُطلب الدخول كل مرة.
 */
class ChatAuthRepository(private val context: Context) {

    private val auth: FirebaseAuth get() = ChatFirebase.auth(context)
    private val db get() = ChatFirebase.db(context)

    /** null = غير مسجّل. يُطلق فوراً بالحالة الحالية ثم مع كل تغيير. */
    fun accountFlow(): Flow<ChatAccount?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { a ->
            trySend(a.currentUser?.let { ChatAccount(it.uid, it.email.orEmpty(), it.displayName.orEmpty()) })
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    fun currentUid(): String? = auth.currentUser?.uid

    suspend fun signUp(rawName: String, email: String, password: String) = withTimeout(30_000L) {
        val name = cleanName(rawName)
        require(NAME_REGEX.matches(name)) { "invalid_name" }
        val lower = name.lowercase()

        val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user
            ?: error("no_user")
        try {
            // حجز اسم المستخدم + إنشاء ملفه بعملية ذرّية واحدة: إن كان الاسم محجوزاً تفشل كلها.
            db.runTransaction(Transaction.Function<Void?> { tx ->
                val nameRef = db.collection("usernames").document(lower)
                if (tx.get(nameRef).exists()) {
                    throw FirebaseFirestoreException("name_taken", FirebaseFirestoreException.Code.ALREADY_EXISTS)
                }
                tx.set(nameRef, mapOf("uid" to user.uid))
                tx.set(
                    db.collection("users").document(user.uid),
                    mapOf(
                        "name" to name,
                        "nameLower" to lower,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "lastSeen" to FieldValue.serverTimestamp()
                    )
                )
                null
            }).await()
        } catch (e: Exception) {
            // فشل حجز الاسم: نحذف الحساب الذي أُنشئ للتو حتى لا يبقى حساب بلا ملف.
            runCatching { user.delete().await() }
            throw e
        }
        runCatching { user.updateProfile(userProfileChangeRequest { displayName = name }).await() }
        Unit
    }

    suspend fun signIn(email: String, password: String) = withTimeout(30_000L) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
        Unit
    }

    suspend fun sendReset(email: String) = withTimeout(30_000L) {
        auth.sendPasswordResetEmail(email.trim()).await()
        Unit
    }

    fun signOut() = auth.signOut()
}

/** رسالة عربية واضحة لأي خطأ يخص الحسابات أو الشبكة. */
fun chatErrorMessage(e: Throwable): String {
    val msg = e.message.orEmpty()
    return when {
        e is IllegalArgumentException && msg == "invalid_name" ->
            "اسم المستخدم من 2 إلى 20 حرفاً (حروف وأرقام ومسافات)، ويبدأ بحرف أو رقم"
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.ALREADY_EXISTS ->
            "اسم المستخدم محجوز، اختر اسماً آخر"
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "الصلاحيات مرفوضة. تأكد من نشر قواعد Firestore الخاصة بمشروع الدردشة"
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.UNAVAILABLE ->
            "لا يوجد اتصال بالإنترنت"
        e is FirebaseAuthUserCollisionException -> "هذا البريد مسجّل مسبقاً، جرّب تسجيل الدخول"
        e is FirebaseAuthWeakPasswordException -> "كلمة المرور ضعيفة، استخدم 6 أحرف على الأقل"
        e is FirebaseAuthInvalidCredentialsException ->
            if ((e.errorCode) == "ERROR_INVALID_EMAIL") "صيغة البريد الإلكتروني غير صحيحة"
            else "البريد الإلكتروني أو كلمة المرور غير صحيحة"
        e is FirebaseAuthInvalidUserException -> "لا يوجد حساب بهذا البريد"
        e is FirebaseTooManyRequestsException -> "محاولات كثيرة، انتظر قليلاً ثم أعد المحاولة"
        e is FirebaseNetworkException -> "لا يوجد اتصال بالإنترنت"
        e is kotlinx.coroutines.TimeoutCancellationException -> "انتهت مهلة الاتصال، تحقق من الإنترنت وأعد المحاولة"
        e is FirebaseAuthException && e.errorCode == "ERROR_OPERATION_NOT_ALLOWED" ->
            "تسجيل البريد وكلمة المرور غير مفعّل في Firebase (Authentication ← Sign-in method)"
        e is FirebaseAuthException -> "تعذّر تنفيذ الطلب (${e.errorCode})"
        msg.contains("API key", ignoreCase = true) || msg.contains("blocked", ignoreCase = true) ->
            "مفتاح Firebase مقيّد أو غير صالح، راجع إعدادات المفتاح في Google Cloud"
        else -> "حدث خطأ غير متوقع، حاول مرة أخرى"
    }
}
