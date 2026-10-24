package com.salman.herbalencyclopedia.data.model

/**
 * عشبة "تعلّمها سيمو من الإنترنت" لأنها لم تكن في الموسوعة (مجموعة semo_web_herbs).
 *
 * - [aliasOf] غير فارغ ⇒ السجل مجرد "اسم بديل" لعشبة موجودة أصلاً في الموسوعة (يتعلّمه سيمو
 *   حين يتعرّف Gemini أن الاسم الذي سأل عنه المستخدم هو عشبة معروفة باسم آخر)، ولا يحمل محتوى.
 * - [approved] = false ⇒ لم يراجعها المطوّر بعد؛ تُعرَض دوماً بوسم "من الإنترنت — غير مؤكد".
 */
data class WebHerb(
    val id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val latinName: String = "",
    val benefits: String = "",
    val usage: String = "",
    val warnings: String = "",
    val harms: String = "",
    val notes: String = "",
    val aliasOf: String = "",
    val approved: Boolean = false,
    /** true = وُجدت عبر بحث Google حيّ؛ false = من معرفة النموذج بلا بحث (ثقة أقل). */
    val grounded: Boolean = true
) {
    val isAliasOnly: Boolean get() = aliasOf.isNotBlank()

    /** كل الأسماء التي تعرّف هذه العشبة (للمطابقة داخل السؤال). */
    val allNames: List<String> get() = (listOf(name) + aliases).filter { it.isNotBlank() }

    /**
     * تحويل لعشبة عادية يفهمها [com.salman.herbalencyclopedia.data.ai.HerbAssistant] دون أي تعديل:
     * الأسماء البديلة تُكتب بصيغة "اسم / بديل" (المقسَّمة بـNAME_ALT_SPLIT) والاسم اللاتيني بين قوسين.
     */
    fun toHerb(): Herb? {
        if (isAliasOnly) return null
        val displayName = buildString {
            append(allNames.joinToString(" / "))
            if (latinName.isNotBlank()) append(" ($latinName)")
        }
        val label = if (approved) "" else
            "⚠️ هذه المعلومات من الإنترنت" +
                (if (grounded) "" else " (من معرفة عامة بلا بحث مباشر)") +
                " ولم يراجعها المطوّر بعد — استشر مختصاً قبل الاعتماد عليها."
        return Herb(
            id = "web_$id",
            name = displayName,
            benefits = benefits,
            warnings = warnings,
            harms = harms,
            usage = usage,
            notes = listOf(label, notes).filter { it.isNotBlank() }.joinToString("\n")
        )
    }
}

/** سؤال لم يجد له سيمو جواباً (للمطوّر: ماذا ينقص الموسوعة؟). */
data class UnansweredQuestion(val id: String, val question: String, val count: Long)
