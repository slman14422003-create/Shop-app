package com.salman.herbalencyclopedia.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

/** عنصر واحد من سجل الأعطال — يُقرأ فقط من طرف الأدمن (راجع firestore.rules). */
data class CrashLog(
    val id: String,
    val message: String,
    val stackTrace: String,
    val versionName: String,
    val versionCode: Int,
    val flavor: String,
    val deviceModel: String,
    val androidVersion: String,
    val createdAt: com.google.firebase.Timestamp?
)

/**
 * أداة إدمن جديدة بطلب صريح من المستخدم: "ميزات مفيدة للمطوّر" — سجل أعطال
 * عن بعد. تُستدعى [report] من مُعترِض استثناءات غير مُلتقَطة عام
 * (Thread.setDefaultUncaughtExceptionHandler، راجع HerbalApp.onCreate)،
 * فتُرسَل تفاصيل العطل (رسالة الاستثناء + أول أسطر من الـ stack trace،
 * بلا أي بيانات شخصية عن المستخدم) إلى Firestore قبل إعادة تمرير الاستثناء
 * للمعترِض الافتراضي (الذي يُنهي العملية كالمعتاد — هذا ليس تعطيلاً لتصادم
 * التطبيق، فقط تسجيلاً له أولاً).
 *
 * الكتابة هنا "أفضل جهد" فقط بمهلة قصيرة جداً (انظر [report]) لأن التطبيق
 * على وشك الانهيار فعلياً عند استدعائها؛ أي تأخير أو فشل شبكة يجب ألا يمنع
 * الانهيار الطبيعي من إكمال مساره أو يجمّد العملية أكثر مما ينبغي.
 */
class CrashLogRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun collection() = db.collection("crash_logs")

    /** أفضل جهد تماماً — أي فشل (بلا إنترنت وقت التصادم مثلاً) يُتجاهل بصمت. */
    fun report(
        throwable: Throwable,
        versionName: String,
        versionCode: Int,
        flavor: String,
        deviceModel: String,
        androidVersion: String
    ) {
        runCatching {
            val trace = throwable.stackTraceToString().lineSequence().take(25).joinToString("\n")
            collection().add(
                hashMapOf(
                    "message" to (throwable.message ?: throwable.toString()).take(500),
                    "stack_trace" to trace,
                    "version_name" to versionName,
                    "version_code" to versionCode,
                    "flavor" to flavor,
                    "device_model" to deviceModel,
                    "android_version" to androidVersion,
                    "created_at" to FieldValue.serverTimestamp()
                )
            )
            // لا await() هنا عمداً: نافذة الفرصة قبل إنهاء العملية قصيرة جداً
            // (نحن داخل معترِض استثناء غير مُلتقَط)، فانتظار تأكيد الكتابة قد
            // لا يكتمل أبداً؛ يكفي أن يبدأ الطلب فعلياً، وFirestore يحاول
            // إرساله حتى لو انتهت العملية بعد سطر أو سطرين من هذا الاستدعاء.
        }
    }

    /** آخر [limit] عطل مُسجَّل — للأدمن فقط (راجع firestore.rules). */
    suspend fun recentCrashes(limit: Int = 30): List<CrashLog> {
        val snapshot = collection()
            .orderBy("created_at", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .get()
            .await()
        return snapshot.documents.map { doc ->
            CrashLog(
                id = doc.id,
                message = doc.getString("message") ?: "",
                stackTrace = doc.getString("stack_trace") ?: "",
                versionName = doc.getString("version_name") ?: "",
                versionCode = (doc.getLong("version_code") ?: 0L).toInt(),
                flavor = doc.getString("flavor") ?: "",
                deviceModel = doc.getString("device_model") ?: "",
                androidVersion = doc.getString("android_version") ?: "",
                createdAt = doc.getTimestamp("created_at")
            )
        }
    }

    /** يمسح كل السجلات — زر "مسح السجل" بلوحة الإدمن. */
    suspend fun clearAll() {
        val snapshot = collection().get().await()
        snapshot.documents.forEach { it.reference.delete() }
    }
}
