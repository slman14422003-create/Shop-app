package com.salman.herbalencyclopedia.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.salman.herbalencyclopedia.data.ai.HerbAssistant
import com.salman.herbalencyclopedia.data.model.UnansweredQuestion
import com.salman.herbalencyclopedia.data.model.WebHerb
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * معرفة سيمو المكتسبة من الإنترنت — منفصلة عمداً عن "semo_learned" (أسئلة/أجوبة):
 *  - semo_web_herbs: أعشاب جديدة وأسماء بديلة، تُنشأ من أي جهاز بحالة غير معتمدة،
 *    ويعتمدها/يحذفها المطوّر فقط (انظر firestore.rules).
 *  - semo_unanswered: عدّاد للأسئلة التي لم يعرف سيمو جوابها، للمطوّر فقط.
 * Firestore يحتفظ بنسخة محلية دائمة، فتبقى الأعشاب المتعلَّمة متاحة بلا إنترنت.
 */
class SemoWebHerbRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val herbs get() = db.collection("semo_web_herbs")
    private val unanswered get() = db.collection("semo_unanswered")

    private companion object { const val LIMIT = 500L }

    fun observeAll(): Flow<List<WebHerb>> = callbackFlow {
        val registration = herbs.limit(LIMIT).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            val list = snapshot.documents.mapNotNull { doc ->
                val name = doc.getString("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                @Suppress("UNCHECKED_CAST")
                val aliases = (doc.get("aliases") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                WebHerb(
                    id = doc.id,
                    name = name,
                    aliases = aliases,
                    latinName = doc.getString("latin_name") ?: "",
                    benefits = doc.getString("benefits") ?: "",
                    usage = doc.getString("usage") ?: "",
                    warnings = doc.getString("warnings") ?: "",
                    harms = doc.getString("harms") ?: "",
                    notes = doc.getString("notes") ?: "",
                    aliasOf = doc.getString("alias_of") ?: "",
                    approved = doc.getBoolean("approved") ?: false,
                    grounded = doc.getBoolean("grounded") ?: true
                )
            }
            trySend(list)
        }
        awaitClose { registration.remove() }
    }

    /** إنشاء فقط: إن وُجد السجل مسبقاً ترفض القواعد التعديل ويبقى الأصلي كما هو. */
    suspend fun contribute(w: WebHerb) {
        if (w.id.isBlank() || w.name.isBlank()) return
        runCatching {
            herbs.document(w.id).set(
                hashMapOf(
                    "name" to w.name.take(200),
                    "aliases" to w.aliases.map { it.take(100) }.take(10),
                    "latin_name" to w.latinName.take(200),
                    "benefits" to w.benefits.take(5000),
                    "usage" to w.usage.take(5000),
                    "warnings" to w.warnings.take(5000),
                    "harms" to w.harms.take(5000),
                    "notes" to w.notes.take(3000),
                    "alias_of" to w.aliasOf.take(200),
                    "approved" to false,
                    "grounded" to w.grounded,
                    "created_at" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    /** يزيد عدّاد سؤال لم يُجَب (create أو +1 عبر merge، وكلاهما مسموح بالقواعد). */
    suspend fun logUnanswered(question: String) {
        val q = question.trim().take(500)
        if (q.isEmpty()) return
        runCatching {
            unanswered.document(HerbAssistant.learningKey(q)).set(
                hashMapOf(
                    "question" to q,
                    "count" to FieldValue.increment(1),
                    "updated_at" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
        }
    }

    // ── للمطوّر فقط (القواعد ترفضها لغيره) ──────────────────────────────
    suspend fun approve(id: String) {
        runCatching { herbs.document(id).update("approved", true).await() }
    }

    suspend fun delete(id: String) {
        runCatching { herbs.document(id).delete().await() }
    }

    fun observeUnanswered(): Flow<List<UnansweredQuestion>> = callbackFlow {
        val registration = unanswered.limit(200).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            trySend(
                snapshot.documents.mapNotNull { doc ->
                    val q = doc.getString("question") ?: return@mapNotNull null
                    UnansweredQuestion(doc.id, q, doc.getLong("count") ?: 1L)
                }.sortedByDescending { it.count }
            )
        }
        awaitClose { registration.remove() }
    }

    suspend fun deleteUnanswered(id: String) {
        runCatching { unanswered.document(id).delete().await() }
    }
}
