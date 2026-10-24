package com.salman.herbalencyclopedia.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

/**
 * مستند في مجموعة "blocked_users" (معرّفه الوثيقة = uid المجهول للمُرسِل،
 * انظر Feedback.senderUid). وجود المستند وحده هو ما يمنع صاحب هذا الـuid من
 * إرسال ملاحظات جديدة (يتحقق firestore.rules من عدم وجوده قبل قبول أي
 * "create" على مجموعة feedback) — القراءة/الكتابة هنا مقصورة على الأدمن.
 */
data class BlockedUser @JvmOverloads constructor(
    @DocumentId
    val uid: String = "",

    /** آخر اسم/رسالة عرفها الأدمن عن هذا المُرسِل وقت الحظر، لعرضها لاحقاً في قائمة المحظورين. */
    @get:PropertyName("sender_name")
    @set:PropertyName("sender_name")
    var senderName: String? = null,

    @get:PropertyName("blocked_at")
    @set:PropertyName("blocked_at")
    var blockedAt: Timestamp? = null
)
