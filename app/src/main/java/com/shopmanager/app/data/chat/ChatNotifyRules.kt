package com.shopmanager.app.data.chat

data class ChatNotifyInput(
    val chatId: String,
    val lastTimeMs: Long,
    val lastFrom: String,
    val myUnread: Int,
    val clearedMs: Long
)

data class ChatNotifyDecision(val advanceBaseline: Boolean, val notify: Boolean)

object ChatNotifyRules {

    const val NO_BASELINE = -1L

    fun decide(
        seenMs: Long,
        initial: Boolean,
        myUid: String,
        openChatId: String?,
        enabled: Boolean,
        doc: ChatNotifyInput
    ): ChatNotifyDecision {
        if (doc.lastTimeMs <= seenMs) return ChatNotifyDecision(false, false)
        if (seenMs == NO_BASELINE && initial) return ChatNotifyDecision(true, false)
        val notify = doc.lastFrom != myUid &&
            doc.myUnread > 0 &&
            !(doc.clearedMs > 0L && doc.lastTimeMs <= doc.clearedMs) &&
            openChatId != doc.chatId &&
            enabled
        return ChatNotifyDecision(true, notify)
    }

    fun body(type: String?, text: String): String = when (type) {
        "voice" -> "رسالة صوتية"
        "image" -> if (text.isBlank()) "📷 صورة" else "📷 ${text.trim()}"
        else -> text.ifBlank { "رسالة جديدة" }
    }

    fun summaryTitle(chats: Int, messages: Int): String = when {
        chats <= 1 -> "رسائل جديدة"
        else -> "$messages رسائل من $chats محادثات"
    }
}
