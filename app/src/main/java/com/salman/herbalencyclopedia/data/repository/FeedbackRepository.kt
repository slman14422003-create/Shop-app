package com.salman.herbalencyclopedia.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.salman.herbalencyclopedia.data.model.BlockedUser
import com.salman.herbalencyclopedia.data.model.Feedback
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.tasks.await

/**
 * Talks to the "feedback" Firestore collection: users report wrong/missing
 * info on a herb or blend here. Sending is open to everyone (see
 * firestore.rules — no login required), but only the admin account can
 * list/read this collection, which is what backs [observeFeedback] used by
 * the admin-only inbox in Settings.
 */
class FeedbackRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun submitFeedback(
        targetType: String,
        targetId: String,
        targetName: String,
        message: String,
        senderName: String?,
        senderUid: String?
    ) {
        val data: HashMap<String, Any?> = hashMapOf(
            "target_type" to targetType,
            "target_id" to targetId,
            "target_name" to targetName,
            "message" to message.trim(),
            "sender_name" to senderName?.trim()?.ifBlank { null },
            // هوية الجهاز المجهولة — راجع AuthRepository.ensureAnonymousUid
            // وتعليق Feedback.senderUid. firestore.rules يرفض أي مستند بلا
            // هذا الحقل (أو بقيمة لا تطابق request.auth.uid)، فهذا هو أساس
            // آلية الحظر بأكملها.
            "sender_uid" to senderUid,
            "created_at" to FieldValue.serverTimestamp()
        )
        db.collection("feedback").add(data).await()
    }

    /**
     * Live listener, admin-only per firestore.rules — newest feedback first.
     * كانت أي انقطاعة لحظية أو انتهاء صلاحية توكن يُنهي صندوق الوارد هذا
     * نهائياً بلا أي مؤشر للأدمن سوى شاشة فارغة، إلى أن يُعاد فتح الشاشة
     * يدوياً. نفس منطق إعادة المحاولة بتأخير تصاعدي المستخدم في
     * [HerbRepository.observeCollection] (1s ثم 2s ثم 4s... سقف 30s).
     */
    fun observeFeedback(): Flow<List<Feedback>> = callbackFlow {
        val registration = db.collection("feedback")
            .orderBy("created_at", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(snapshot.toObjects(Feedback::class.java))
                }
            }
        awaitClose { registration.remove() }
    }.retryWhen { _, attempt ->
        val delayMs = (1000L shl attempt.toInt().coerceAtMost(5)).coerceAtMost(30_000L)
        delay(delayMs)
        true
    }

    suspend fun deleteFeedback(id: String) {
        db.collection("feedback").document(id).delete().await()
    }

    // ---------------------------------------------------------------------
    // حظر مُرسِلي الملاحظات — انظر توثيق BlockedUser وقاعدة blocked_users في
    // firestore.rules. وجود مستند في "blocked_users" بمعرّف يساوي
    // Feedback.senderUid هو وحده ما يمنع ذلك الجهاز من إرسال ملاحظات جديدة؛
    // لا يحذف أو يُخفي أي ملاحظات سابقة (يبقى ذلك قراراً يدوياً منفصلاً
    // للأدمن عبر onDelete الحالي في AdminFeedbackScreen).
    // ---------------------------------------------------------------------

    suspend fun blockUser(uid: String, senderName: String?) {
        val data: HashMap<String, Any?> = hashMapOf(
            "sender_name" to senderName?.trim()?.ifBlank { null },
            "blocked_at" to FieldValue.serverTimestamp()
        )
        db.collection("blocked_users").document(uid).set(data).await()
    }

    suspend fun unblockUser(uid: String) {
        db.collection("blocked_users").document(uid).delete().await()
    }

    /** Live listener, admin-only per firestore.rules. */
    fun observeBlockedUsers(): Flow<List<BlockedUser>> = callbackFlow {
        val registration = db.collection("blocked_users")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(snapshot.toObjects(BlockedUser::class.java))
                }
            }
        awaitClose { registration.remove() }
    }.retryWhen { _, attempt ->
        val delayMs = (1000L shl attempt.toInt().coerceAtMost(5)).coerceAtMost(30_000L)
        delay(delayMs)
        true
    }
}
