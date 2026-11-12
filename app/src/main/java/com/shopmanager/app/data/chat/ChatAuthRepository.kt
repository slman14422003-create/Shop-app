package com.shopmanager.app.data.chat

import android.app.Activity
import android.content.Context
import android.provider.Settings
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.UUID

private const val DEVICE_TAKEN = "device_taken"
private const val ACCOUNT_OTHER_DEVICE = "account_other_device"
private const val NAME_TAKEN = "name_taken"
private const val BROKEN_ANDROID_ID = "9774d56d682e549c"

class ChatAuthRepository(private val context: Context) {

    private val auth: FirebaseAuth get() = ChatFirebase.auth(context)
    private val db get() = ChatFirebase.db(context)

    fun accountFlow(): Flow<ChatAccount?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { a ->
            trySend(a.currentUser?.let { ChatAccount(it.uid, it.email.orEmpty(), it.displayName.orEmpty()) })
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    fun currentUid(): String? = auth.currentUser?.uid

    suspend fun signInWithGoogle(activity: Activity) = withTimeout(120_000L) {
        val provider = OAuthProvider.newBuilder("google.com")
            .setScopes(listOf("email", "profile"))
            .addCustomParameter("prompt", "select_account")
            .build()
        val task = auth.pendingAuthResult ?: auth.startActivityForSignInWithProvider(activity, provider)
        val user = task.await().user ?: error("no_user")
        try {
            bindDevice(user.uid, user.displayName, user.email)
        } catch (e: Exception) {
            auth.signOut()
            throw e
        }
        Unit
    }

    private fun deviceId(): String {
        val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        if (!id.isNullOrBlank() && id != BROKEN_ANDROID_ID) return id
        val prefs = context.getSharedPreferences("chat_device", Context.MODE_PRIVATE)
        return prefs.getString("id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("id", it).apply()
        }
    }

    private fun baseName(displayName: String?, email: String?): String {
        val raw = displayName?.takeIf { it.isNotBlank() } ?: email?.substringBefore('@').orEmpty()
        val cleaned = cleanName(raw.filter { it.isLetterOrDigit() || it == ' ' || it == '_' }).take(20).trim()
        return if (NAME_REGEX.matches(cleaned)) cleaned else "user" + (1000..9999).random()
    }

    private suspend fun bindDevice(uid: String, displayName: String?, email: String?) {
        val device = deviceId()
        val base = baseName(displayName, email)
        var lastError: Exception? = null
        repeat(4) { attempt ->
            val name = if (attempt == 0) base else base.take(15).trim() + (100..9999).random()
            try {
                runBind(uid, device, name)
                return
            } catch (e: FirebaseFirestoreException) {
                if (e.code == FirebaseFirestoreException.Code.ALREADY_EXISTS && e.message.orEmpty().contains(NAME_TAKEN)) {
                    lastError = e
                } else {
                    throw e
                }
            }
        }
        throw lastError ?: error("bind_failed")
    }

    private suspend fun runBind(uid: String, device: String, name: String) {
        val lower = name.lowercase()
        db.runTransaction(Transaction.Function<Void?> { tx ->
            val deviceRef = db.collection("devices").document(device)
            val userRef = db.collection("users").document(uid)
            val nameRef = db.collection("usernames").document(lower)
            val deviceSnap = tx.get(deviceRef)
            val userSnap = tx.get(userRef)
            val nameSnap = if (userSnap.exists()) null else tx.get(nameRef)

            if (deviceSnap.exists() && deviceSnap.getString("uid") != uid) {
                throw FirebaseFirestoreException(DEVICE_TAKEN, FirebaseFirestoreException.Code.FAILED_PRECONDITION)
            }
            val boundDevice = userSnap.getString("deviceId").orEmpty()
            if (userSnap.exists() && boundDevice.isNotEmpty() && boundDevice != device) {
                throw FirebaseFirestoreException(ACCOUNT_OTHER_DEVICE, FirebaseFirestoreException.Code.FAILED_PRECONDITION)
            }
            if (nameSnap != null && nameSnap.exists()) {
                throw FirebaseFirestoreException(NAME_TAKEN, FirebaseFirestoreException.Code.ALREADY_EXISTS)
            }

            if (!deviceSnap.exists()) {
                tx.set(deviceRef, mapOf("uid" to uid, "createdAt" to FieldValue.serverTimestamp()))
            }
            if (!userSnap.exists()) {
                tx.set(nameRef, mapOf("uid" to uid))
                tx.set(
                    userRef,
                    mapOf(
                        "name" to name,
                        "nameLower" to lower,
                        "deviceId" to device,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "lastSeen" to FieldValue.serverTimestamp()
                    )
                )
            } else if (boundDevice.isEmpty()) {
                tx.update(userRef, "deviceId", device)
            }
            null
        }).await()
    }
}

fun chatErrorMessage(e: Throwable): String {
    val msg = e.message.orEmpty()
    return when {
        e is FirebaseFirestoreException && msg.contains(DEVICE_TAKEN) ->
            "هذا الجهاز مرتبط بحساب آخر، ولا يمكن استخدام أكثر من حساب على الجهاز"
        e is FirebaseFirestoreException && msg.contains(ACCOUNT_OTHER_DEVICE) ->
            "هذا الحساب مرتبط بجهاز آخر، ولا يمكن الدخول به من هذا الجهاز"
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.ALREADY_EXISTS ->
            "تعذّر إنشاء اسم المستخدم، حاول مرة أخرى"
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "الصلاحيات مرفوضة. تأكد من نشر قواعد Firestore الخاصة بمشروع الدردشة"
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.UNAVAILABLE ->
            "لا يوجد اتصال بالإنترنت"
        e is FirebaseTooManyRequestsException -> "محاولات كثيرة، انتظر قليلاً ثم أعد المحاولة"
        e is FirebaseNetworkException -> "لا يوجد اتصال بالإنترنت"
        e is kotlinx.coroutines.TimeoutCancellationException -> "انتهت مهلة الاتصال، تحقق من الإنترنت وأعد المحاولة"
        e is FirebaseAuthException && e.errorCode == "ERROR_WEB_CONTEXT_CANCELED" -> "تم إلغاء تسجيل الدخول"
        e is FirebaseAuthException && e.errorCode == "ERROR_OPERATION_NOT_ALLOWED" ->
            "تسجيل الدخول بـ Google غير مفعّل في Firebase (Authentication ← Sign-in method)"
        e is FirebaseAuthException -> "تعذّر تسجيل الدخول (${e.errorCode})"
        msg.contains("API key", ignoreCase = true) || msg.contains("blocked", ignoreCase = true) ->
            "مفتاح Firebase مقيّد أو غير صالح، راجع إعدادات المفتاح في Google Cloud"
        else -> "حدث خطأ غير متوقع، حاول مرة أخرى"
    }
}
