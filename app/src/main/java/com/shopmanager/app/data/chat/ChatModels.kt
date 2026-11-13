package com.shopmanager.app.data.chat

enum class MessageType(val storage: String) {
    TEXT("text"), VOICE("voice");

    companion object {
        fun from(s: String?) = if (s == "voice") VOICE else TEXT
    }
}

/** حساب المستخدم الحالي (من Firebase Auth). */
data class ChatAccount(val uid: String, val email: String, val displayName: String)

/** مستخدم مسجّل في الدردشة. */
data class ChatUser(val uid: String, val name: String, val lastSeenMs: Long) {
    /** متصل = آخر نبضة خلال دقيقتين (يرسل التطبيق نبضة كل 45 ثانية وهو مفتوح). */
    fun isOnline(nowMs: Long = System.currentTimeMillis()): Boolean =
        lastSeenMs > 0 && nowMs - lastSeenMs < ONLINE_WINDOW_MS

    companion object {
        const val ONLINE_WINDOW_MS = 120_000L
    }
}

/** صف في قائمة المحادثات. */
data class ChatSummary(
    val chatId: String,
    val otherUid: String,
    val otherName: String,
    val lastText: String,
    val lastType: MessageType,
    val lastFromMe: Boolean,
    val lastTimeMs: Long,
    val unread: Int,
    val clearedMs: Long = 0L
)

data class ReplyRef(val id: String, val fromUid: String, val type: MessageType, val text: String)

data class ChatMessage(
    val id: String,
    val fromUid: String,
    val type: MessageType,
    val text: String,
    val audioB64: String,
    val durationMs: Long,
    val wave: List<Int>,
    val createdAtMs: Long,
    val expireAtMs: Long,
    /** true = الكتابة لم تصل للخادم بعد (ساعة صغيرة). */
    val pending: Boolean,
    val reply: ReplyRef? = null,
    val edited: Boolean = false,
    val reactions: Map<String, String> = emptyMap()
) {
    val isExpiredVoice: Boolean
        get() = type == MessageType.VOICE && expireAtMs > 0 && System.currentTimeMillis() > expireAtMs
}

/** بيانات مستند المحادثة التي تهم الواجهة: متى قرأ الطرف الآخر، وهل يكتب الآن. */
data class ChatMeta(
    /** آخر وقت (بتوقيت الخادم) فتح فيه الطرف الآخر المحادثة. */
    val otherReadMs: Long = 0L,
    /** قيمة مؤشر "يكتب الآن" للطرف الآخر (تتغير مع كل نبضة كتابة)، 0 = لا يكتب. */
    val otherTypingStamp: Long = 0L,
    val myReadMs: Long = 0L,
    val myClearedMs: Long = 0L
)

fun chatIdFor(a: String, b: String): String = if (a < b) "${a}_$b" else "${b}_$a"

val NAME_REGEX = Regex("^[\\p{L}\\p{N}][\\p{L}\\p{N} _]{1,19}$")

fun cleanName(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")
