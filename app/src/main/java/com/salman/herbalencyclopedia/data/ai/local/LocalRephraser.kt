package com.salman.herbalencyclopedia.data.ai.local

import com.salman.herbalencyclopedia.data.search.GroundedPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

object LocalRephraser {

    const val TIMEOUT_MS = 20_000L

    const val SYSTEM_PROMPT =
        "أعد صياغة النقاط التالية فقط بالعربية الواضحة والمختصرة. " +
            "لا تضف أي معلومة أو نصيحة غير موجودة فيها. إن لم تكفِ النقاط فقل ذلك."

    fun buildUserPrompt(question: String, points: List<GroundedPoint>): String = buildString {
        append("السؤال: ").append(question.trim()).append("\n\nالنقاط:\n")
        points.forEachIndexed { i, p ->
            append(i + 1).append(") ").append(p.herbName).append(" - ").append(p.field).append(": ")
                .append(p.text.trim()).append('\n')
        }
        append("\nأعد صياغة النقاط أعلاه فقط للإجابة عن السؤال في سطرين إلى ثلاثة.")
    }

    suspend fun rephrase(
        question: String,
        points: List<GroundedPoint>,
        onPartial: ((String) -> Unit)? = null
    ): String? {
        if (points.isEmpty()) return null
        val engine = LocalLlmManager.loadIfNeeded() ?: return null
        val spec = LocalLlmManager.selectedSpec()
        val buffer = StringBuilder()
        val finished = try {
            withTimeoutOrNull(TIMEOUT_MS) {
                engine.generate(SYSTEM_PROMPT, buildUserPrompt(question, points))
                    .collect { chunk ->
                        buffer.append(chunk)
                        if (onPartial != null) {
                            val partial = if (spec.stripThinking) RephraseValidator.stripThinking(buffer.toString()) else buffer.toString()
                            onPartial(partial)
                        }
                    }
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
        if (finished != true) {
            LocalLlmManager.release()
            return null
        }
        val text = RephraseValidator.stripThinking(buffer.toString())
        val verdict = RephraseValidator.validate(text, points)
        return if (verdict.ok) text else null
    }
}
