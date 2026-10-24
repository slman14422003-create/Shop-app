package com.salman.herbalencyclopedia.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.tasks.await

data class MaintenanceConfig(
    val enabled: Boolean = false,
    val message: String = ""
)

/**
 * أداة إدمن جديدة بطلب صريح: "تفعيل/تعطيل الصيانة" — علَم واحد بسيط بـ
 * Firestore يوقف نسخة public مؤقتاً برسالة للمستخدمين (مثلاً أثناء ترحيل
 * بيانات كبير أو صيانة السيرفر)، بدل الاضطرار لتوزيع تحديث كامل لذلك.
 *
 * مقصورة على نسخة public فقط بالتصميم — راجع MaintenanceGate بAppViewModel:
 * نسخة full (الأدمن نفسه) يجب أن تبقى قادرة على الدخول دوماً لتعطيل
 * الصيانة لاحقاً، فلا يجوز لهذا العلَم حجبها عن نفسها بالخطأ.
 */
class MaintenanceRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun doc() = db.collection("app_config").document("maintenance")

    /**
     * مستمع حي (Firestore snapshot listener) بدل قراءة لمرة واحدة: هذا هو ما
     * يجعل تفعيل/تعطيل الصيانة من لوحة الأدمن يطبَّق فوراً على كل نسخ public
     * المفتوحة حالياً — بلا الحاجة لإعادة تشغيل التطبيق — سواء بالتفعيل
     * (تُحجب فوراً) أو التعطيل (يُلغى الحجب فوراً وتعود شاشتها المعتادة).
     * أي انقطاعة لحظية بلا إنترنت لا تُغلق الصندوق نهائياً، بل تُعاد
     * المحاولة بتأخير تصاعدي (نفس منطق FeedbackRepository.observeFeedback)
     * إلى أن يعود الاتصال، وبانتظار ذلك تبقى آخر قيمة معروفة سارية بدل
     * حجب المستخدم بلا داعٍ لمجرد انقطاع مؤقت.
     */
    fun observe(): Flow<MaintenanceConfig> = callbackFlow {
        val registration = doc().addSnapshotListener { snap, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snap != null && snap.exists()) {
                trySend(
                    MaintenanceConfig(
                        enabled = snap.getBoolean("enabled") ?: false,
                        message = snap.getString("message")?.trim().orEmpty()
                    )
                )
            } else {
                trySend(MaintenanceConfig())
            }
        }
        awaitClose { registration.remove() }
    }.retryWhen { _, attempt ->
        val delayMs = (1000L shl attempt.toInt().coerceAtMost(5)).coerceAtMost(30_000L)
        delay(delayMs)
        true
    }

    suspend fun save(config: MaintenanceConfig) {
        doc().set(
            hashMapOf(
                "enabled" to config.enabled,
                "message" to config.message.trim(),
                "updated_at" to FieldValue.serverTimestamp()
            )
        ).await()
    }
}
