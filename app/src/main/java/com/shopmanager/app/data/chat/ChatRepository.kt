package com.shopmanager.app.data.chat

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import com.shopmanager.app.data.awaitQueued
import java.util.Date

private const val WRITE_TIMEOUT_MS = 20_000L

/** مدة بقاء الرسالة الصوتية، بعدها يحذفها التطبيق تلقائياً (deleteExpiredVoice) لتوفير المساحة. */
private const val VOICE_TTL_MS = 30L * 24 * 60 * 60 * 1000

const val MAX_TEXT_LENGTH = 2000

/**
 * كل عمليات الدردشة على مشروع Firebase المستقل (انظر [ChatFirebase]).
 *
 * البنية:
 *  users/{uid}                        الاسم + آخر ظهور
 *  usernames/{nameLower}              حجز الاسم الفريد -> uid
 *  chats/{uidA_uidB}                  (المعرّفان مرتّبان) الأعضاء + آخر رسالة + غير المقروء + القراءة + الكتابة
 *  chats/{id}/messages/{msgId}        نص أو صوت (Base64 مضغوط داخل المستند، بدون Storage)
 */
class ChatRepository(private val context: Context) {

    private val db get() = ChatFirebase.db(context)

    // ───────────── المستخدمون ─────────────

    suspend fun heartbeat(uid: String) {
        runCatching {
            db.collection("users").document(uid).update("lastSeen", FieldValue.serverTimestamp()).awaitQueued()
        }
    }

    suspend fun getUser(uid: String): ChatUser? = runCatching {
        withTimeout(15_000L) { db.collection("users").document(uid).get().await().toUser() }
    }.getOrNull()

    fun listenUser(uid: String): Flow<ChatUser?> = callbackFlow {
        val reg = db.collection("users").document(uid)
            .addSnapshotListener(ChatFirebase.executor) { snap, err ->
                if (err != null) return@addSnapshotListener
                trySend(snap?.toUser())
            }
        awaitClose { reg.remove() }
    }

    /** بحث ببداية الاسم (بدون فهارس مركّبة). الاستعلام الفارغ يعيد أول 100 مستخدم حسب الاسم. */
    suspend fun searchUsers(query: String, exceptUid: String): List<ChatUser> = withTimeout(20_000L) {
        val q = cleanName(query).lowercase()
        val base = db.collection("users").orderBy("nameLower")
        val snap = if (q.isEmpty()) base.limit(100).get().await()
        else base.startAt(q).endAt(q + "\uf8ff").limit(100).get().await()
        snap.documents.mapNotNull { it.toUser() }.filter { it.uid != exceptUid }
    }

    // ───────────── المحادثات ─────────────

    fun listenMyChats(me: String): Flow<List<ChatSummary>> = callbackFlow {
        val reg = db.collection("chats").whereArrayContains("members", me)
            .addSnapshotListener(ChatFirebase.executor) { snap, err ->
                if (err != null) {
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { it.toSummary(me) }
                    // الترتيب هنا (لا في الاستعلام) حتى لا نحتاج فهرساً مركّباً في Firestore.
                    ?.sortedByDescending { it.lastTimeMs } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun listenMeta(chatId: String, me: String, other: String): Flow<ChatMeta> = callbackFlow {
        val reg = db.collection("chats").document(chatId)
            .addSnapshotListener(ChatFirebase.executor) { snap, err ->
                if (err != null) return@addSnapshotListener
                if (snap == null || !snap.exists()) {
                    trySend(ChatMeta())
                    return@addSnapshotListener
                }
                val reads = snap.get("reads") as? Map<*, *>
                val typing = snap.get("typing") as? Map<*, *>
                fun ms(v: Any?): Long = (v as? Timestamp)?.let { it.seconds * 1000 + it.nanoseconds / 1_000_000 } ?: 0L
                trySend(ChatMeta(otherReadMs = ms(reads?.get(other)), otherTypingStamp = ms(typing?.get(other)), myReadMs = ms(reads?.get(me))))
            }
        awaitClose { reg.remove() }
    }

    fun listenMessages(chatId: String, limit: Long): Flow<List<ChatMessage>> = callbackFlow {
        val reg = db.collection("chats").document(chatId).collection("messages")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            // INCLUDE: لنعرف لحظة وصول الرسالة للخادم (تبديل الساعة الصغيرة إلى ✓).
            .addSnapshotListener(ChatFirebase.executor, MetadataChanges.INCLUDE) { snap, err ->
                if (err != null) {
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { it.toMessage() }?.asReversed() ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun sendText(me: String, myName: String, other: String, otherName: String, text: String) {
        val clean = text.trim().take(MAX_TEXT_LENGTH)
        if (clean.isEmpty()) return
        send(me, myName, other, otherName, MessageType.TEXT, clean, null, 0L, emptyList())
    }

    suspend fun sendVoice(
        me: String, myName: String, other: String, otherName: String,
        audioB64: String, durationMs: Long, wave: List<Int>
    ) {
        send(me, myName, other, otherName, MessageType.VOICE, "", audioB64, durationMs, wave)
    }

    private suspend fun send(
        me: String, myName: String, other: String, otherName: String,
        type: MessageType, text: String, audioB64: String?, durationMs: Long, wave: List<Int>
    ) = withTimeout(WRITE_TIMEOUT_MS) {
        val chatId = chatIdFor(me, other)
        val chatRef = db.collection("chats").document(chatId)
        val msgRef = chatRef.collection("messages").document()

        val msg = hashMapOf<String, Any>(
            "from" to me,
            "type" to type.storage,
            "text" to text,
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (type == MessageType.VOICE && audioB64 != null) {
            msg["audio"] = audioB64
            msg["durationMs"] = durationMs
            msg["wave"] = wave
            // الصوت يُحذف بعد 30 يوماً: التطبيق ينظّف المنتهي عند فتح المحادثة (بدون TTL ولا خدمات إضافية).
            msg["expireAt"] = Timestamp(Date(System.currentTimeMillis() + VOICE_TTL_MS))
        }

        val chat = mapOf(
            "members" to listOf(me, other).sorted(),
            "names" to mapOf(me to myName, other to otherName),
            "lastText" to if (type == MessageType.VOICE) "" else text.take(120),
            "lastType" to type.storage,
            "lastFrom" to me,
            "lastTime" to FieldValue.serverTimestamp(),
            "unread" to mapOf(other to FieldValue.increment(1), me to 0),
            "typing" to mapOf(me to FieldValue.delete())
        )

        val batch = db.batch()
        batch.set(chatRef, chat, SetOptions.merge())
        batch.set(msgRef, msg)
        batch.commit().awaitQueued()
        Unit
    }

    /** تصفير غير المقروء + تسجيل وقت قراءتي (يراه الطرف الآخر كـ ✓✓). */
    suspend fun markRead(chatId: String, me: String) {
        runCatching {
            db.collection("chats").document(chatId)
                .update("reads.$me", FieldValue.serverTimestamp(), "unread.$me", 0).awaitQueued()
        }
    }

    /** حذف الرسائل الصوتية التي انتهت مدتها (30 يوماً). تُستدعى عند فتح المحادثة، وفشلها لا يضر. */
    suspend fun deleteExpiredVoice(chatId: String, expired: List<ChatMessage>) {
        if (expired.isEmpty()) return
        runCatching {
            val batch = db.batch()
            val col = db.collection("chats").document(chatId).collection("messages")
            expired.take(400).forEach { batch.delete(col.document(it.id)) }
            batch.commit().awaitQueued()
        }
    }

    suspend fun setTyping(chatId: String, me: String, typing: Boolean) {
        runCatching {
            db.collection("chats").document(chatId)
                .update("typing.$me", if (typing) FieldValue.serverTimestamp() else FieldValue.delete()).awaitQueued()
        }
    }
}

private fun Timestamp?.toMs(): Long = this?.let { it.seconds * 1000 + it.nanoseconds / 1_000_000 } ?: 0L

private fun DocumentSnapshot.toUser(): ChatUser? {
    if (!exists()) return null
    val name = getString("name") ?: return null
    return ChatUser(uid = id, name = name, lastSeenMs = getTimestamp("lastSeen", ServerTimestampBehavior.ESTIMATE).toMs())
}

private fun DocumentSnapshot.toSummary(me: String): ChatSummary? {
    val members = (get("members") as? List<*>)?.filterIsInstance<String>() ?: return null
    val other = members.firstOrNull { it != me } ?: return null
    val names = get("names") as? Map<*, *>
    val unread = ((get("unread") as? Map<*, *>)?.get(me) as? Number)?.toInt() ?: 0
    return ChatSummary(
        chatId = id,
        otherUid = other,
        otherName = (names?.get(other) as? String).orEmpty().ifBlank { "مستخدم" },
        lastText = getString("lastText").orEmpty(),
        lastType = MessageType.from(getString("lastType")),
        lastFromMe = getString("lastFrom") == me,
        lastTimeMs = getTimestamp("lastTime", ServerTimestampBehavior.ESTIMATE).toMs(),
        unread = unread
    )
}

private fun DocumentSnapshot.toMessage(): ChatMessage? {
    val from = getString("from") ?: return null
    val wave = (get("wave") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
    return ChatMessage(
        id = id,
        fromUid = from,
        type = MessageType.from(getString("type")),
        text = getString("text").orEmpty(),
        audioB64 = getString("audio").orEmpty(),
        durationMs = getLong("durationMs") ?: 0L,
        wave = wave,
        createdAtMs = getTimestamp("createdAt", ServerTimestampBehavior.ESTIMATE).toMs(),
        expireAtMs = getTimestamp("expireAt").toMs(),
        pending = metadata.hasPendingWrites()
    )
}
