package com.shopmanager.app.assistant

import com.shopmanager.app.assistant.engine.AmbiguityOption
import com.shopmanager.app.assistant.engine.Command

/** حالة الروبوت التعبيرية. */
enum class RobotMood { READY, THINKING, SUCCESS, CONFUSED }

data class CardField(val label: String, val value: String)

/** بطاقات غنية داخل فقاعة المساعد. */
sealed interface AssistantCard {
    /** بطاقة تأكيد: عنوان العملية + الحقول + تنفيذ/تعديل/إلغاء. */
    data class Confirm(
        val title: String,
        val fields: List<CardField>,
        val warnings: List<String>,
        val command: Command,
        val risky: Boolean
    ) : AssistantCard

    /** بطاقة نتيجة: "تم" + تفاصيل + زر تراجع (يبقى متاحاً لآخر 10 عمليات). */
    data class Result(
        val title: String,
        val details: String,
        val undoId: Long?,
        val success: Boolean = true
    ) : AssistantCard

    /** بطاقة جواب (سعر، رصيد، قائمة...). */
    data class Answer(val title: String, val rows: List<CardField>, val note: String? = null) : AssistantCard

    /** بطاقة توضيح عند الغموض. */
    data class Clarify(
        val question: String,
        val options: List<AmbiguityOption>,
        val command: Command
    ) : AssistantCard
}

/** زر اقتراح متابعة: إن كان [sendNow] يُرسل النص مباشرة، وإلا يُوضع في حقل الكتابة ليكمله المستخدم. */
data class AssistantChip(val label: String, val text: String = label, val sendNow: Boolean = true)

data class AssistantMessage(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    val time: Long,
    val card: AssistantCard? = null,
    /** اقتراحات متابعة تظهر تحت آخر رد فقط. */
    val chips: List<AssistantChip> = emptyList()
)

/** سطر في سجل "آخر ما نفّذه المساعد". */
data class ActionLogEntry(val id: Long, val text: String, val time: Long)

data class AssistantSettings(
    val proactiveSuggestions: Boolean = true,
    /** يطلب تأكيداً حتى للأوامر عالية الثقة. الحذف وتعديل السعر والمبالغ الشاذة تُؤكَّد دائماً. */
    val alwaysConfirm: Boolean = true,
    val robotAnimation: Boolean = true,
    /** الشاشة الرئيسية الجديدة (المساعد). إيقافها يعيد الرئيسية الكلاسيكية. */
    val newHome: Boolean = true
)
