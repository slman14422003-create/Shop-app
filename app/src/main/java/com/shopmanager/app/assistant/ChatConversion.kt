package com.shopmanager.app.assistant

import com.shopmanager.app.assistant.engine.AmbiguityKind
import com.shopmanager.app.assistant.engine.AssistantEngine
import com.shopmanager.app.assistant.engine.Command
import com.shopmanager.app.assistant.engine.EngineSnapshot
import com.shopmanager.app.assistant.engine.Intent
import com.shopmanager.app.assistant.engine.MatchLevel
import com.shopmanager.app.assistant.engine.Slot
import com.shopmanager.app.data.chat.ConversionPlan
import com.shopmanager.app.data.chat.ConversionResult
import com.shopmanager.app.data.chat.ConvertKind
import com.shopmanager.app.data.chat.ConvertOption
import com.shopmanager.app.data.chat.MessageConverter

object ChatConversion {

    fun shortageText(text: String) = "ضيف ${text.trim()} للنواقص"

    fun debtText(text: String) = "سجل على ${text.trim()}"

    private fun known(level: MatchLevel) = level == MatchLevel.EXACT || level == MatchLevel.HIGH

    fun plan(engine: AssistantEngine, snapshot: EngineSnapshot, kind: ConvertKind, text: String): ConversionPlan {
        val clean = text.trim()
        if (clean.isEmpty()) return ConversionPlan.Missing(kind, "الرسالة فاضية")
        return when (kind) {
            ConvertKind.NOTE -> classifyNote(clean)
            ConvertKind.SHORTAGE -> classifyShortage(engine.understand(shortageText(clean), snapshot).commands.firstOrNull(), clean)
            ConvertKind.DEBT -> classifyDebt(engine.understand(debtText(clean), snapshot).commands.firstOrNull(), clean)
        }
    }

    fun classifyNote(text: String): ConversionPlan =
        ConversionPlan.Ready(
            ConvertKind.NOTE,
            "ملاحظة هامة: ${text.take(80)}",
            Command(Intent.ADD_NOTE, text = text, important = true, understood = "ملاحظة")
        )

    fun classifyShortage(cmd: Command?, text: String): ConversionPlan {
        val kind = ConvertKind.SHORTAGE
        if (cmd == null || cmd.intent != Intent.ADD_SHORTAGE || cmd.materials.isEmpty()) {
            return ConversionPlan.Missing(kind, "ما لقيت أسماء مواد بالرسالة")
        }
        val amb = cmd.ambiguity
        if (amb != null && amb.options.isNotEmpty() &&
            (amb.kind == AmbiguityKind.FUZZY_MATERIAL || amb.kind == AmbiguityKind.MATERIAL)
        ) {
            val options = amb.options.map { o ->
                val idx = o.value.substringBefore('|').toIntOrNull()
                val raw = idx?.let { cmd.materials.getOrNull(it)?.rawSpan }.orEmpty()
                val replaced = if (raw.isNotEmpty() && text.contains(raw)) text.replace(raw, o.label) else o.label
                ConvertOption(o.label, replaced)
            }.distinctBy { it.label }
            return ConversionPlan.Suggest(kind, amb.question, options)
        }
        if (cmd.missing.contains(Slot.MATERIAL) || cmd.materials.any { !known(it.level) }) {
            val bad = cmd.materials.firstOrNull { !known(it.level) }?.rawSpan
            return ConversionPlan.Missing(kind, if (bad.isNullOrBlank()) "ما لقيت" else "ما لقيت \"$bad\" بين المواد")
        }
        return ConversionPlan.Ready(kind, "إضافة للنواقص: " + cmd.materials.joinToString("، ") { it.name }, cmd)
    }

    fun classifyDebt(cmd: Command?, text: String): ConversionPlan {
        val kind = ConvertKind.DEBT
        if (cmd == null || cmd.intent != Intent.ADD_DEBT) return ConversionPlan.Missing(kind, "ما فهمت اسم العميل والمبلغ")
        val amb = cmd.ambiguity
        if (amb != null && amb.options.isNotEmpty() &&
            (amb.kind == AmbiguityKind.FUZZY_PERSON || amb.kind == AmbiguityKind.PERSON)
        ) {
            val raw = cmd.person?.rawSpan.orEmpty()
            val options = amb.options.map { o ->
                val label = o.label
                val replaced = if (raw.isNotEmpty() && text.contains(raw)) text.replace(raw, label) else "$label ${text}"
                ConvertOption(label, replaced)
            }.distinctBy { it.label }
            return ConversionPlan.Suggest(kind, amb.question, options)
        }
        val p = cmd.person
        if (p == null || p.id == null || !known(p.level)) {
            val n = p?.rawSpan?.takeIf { it.isNotBlank() }
            return ConversionPlan.Missing(kind, if (n == null) "ما لقيت العميل" else "ما لقيت \"$n\" بين العملاء")
        }
        if (cmd.missing.contains(Slot.AMOUNT) || (cmd.amount ?: 0.0) <= 0.0) {
            return ConversionPlan.Missing(kind, "ما لقيت مبلغ بالرسالة")
        }
        val reasons = if (cmd.confirmReasons.isEmpty()) "" else "\n" + cmd.confirmReasons.joinToString("\n")
        return ConversionPlan.Ready(kind, cmd.understood.removePrefix("فهمت: ").ifBlank { "تسجيل دين على ${p.name}" } + reasons, cmd)
    }
}

class AssistantMessageConverter(private val vm: AssistantViewModel) : MessageConverter {
    override suspend fun plan(kind: ConvertKind, text: String): ConversionPlan = vm.planChatConversion(kind, text)
    override suspend fun apply(plan: ConversionPlan.Ready): ConversionResult = vm.applyChatConversion(plan)
}
