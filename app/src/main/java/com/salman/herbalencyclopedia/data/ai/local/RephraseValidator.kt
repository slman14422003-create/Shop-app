package com.salman.herbalencyclopedia.data.ai.local

import com.salman.herbalencyclopedia.data.ai.ArabicLexicon
import com.salman.herbalencyclopedia.data.search.GroundedPoint

object RephraseValidator {

    class Result(val ok: Boolean, val reason: String)

    private const val MIN_CHARS = 20
    private const val MIN_ARABIC_RATIO = 0.8
    private const val MAX_GROWTH = 2.2
    private const val MIN_ALLOWED_CHARS = 400
    private const val WARNING_COVERAGE = 0.6

    private val thinkBlock = Regex("(?s)<think>.*?</think>")
    private val digits = Regex("\\d+")

    private val genericWords: Set<String> by lazy {
        listOf(
            "يجب", "يمكن", "قد", "عند", "بعد", "قبل", "مع", "هذه", "هذا", "التي", "الذي", "كذلك", "ايضا", "بشكل",
            "تجنب", "تناول", "استخدام", "حذر", "تحذير", "انتبه", "استشر", "الطبيب"
        ).map { ArabicLexicon.stripAl(ArabicLexicon.normalizeText(it)) }.toSet()
    }

    fun stripThinking(raw: String): String {
        var text = thinkBlock.replace(raw, "")
        val open = text.indexOf("<think>")
        if (open >= 0) text = text.substring(0, open)
        return text.replace("</think>", "").trim()
    }

    private fun wordsOf(text: String): List<String> =
        ArabicLexicon.normalizeText(text).split(' ').filter { it.isNotBlank() }

    private fun forms(word: String): Set<String> {
        val out = HashSet<String>()
        val stripped = ArabicLexicon.stripAl(word)
        out += word
        out += stripped
        out.addAll(ArabicLexicon.formsOf(word))
        out.addAll(ArabicLexicon.formsOf(stripped))
        return out
    }

    private fun outputVocabulary(output: String): Set<String> {
        val vocab = HashSet<String>()
        for (w in wordsOf(output)) vocab.addAll(forms(w))
        return vocab
    }

    private fun containsWord(vocab: Set<String>, word: String): Boolean {
        val stripped = ArabicLexicon.stripAl(word)
        if (stripped in vocab || word in vocab) return true
        return ArabicLexicon.formsOf(stripped).any { it in vocab }
    }

    private fun herbTokens(name: String): List<String> =
        wordsOf(ArabicLexicon.arabicNameCore(name))
            .map { ArabicLexicon.stripAl(it) }
            .filter { it.length >= 3 }

    private fun arabicRatio(text: String): Double {
        var arabic = 0
        var letters = 0
        for (ch in text) {
            if (!ch.isLetter()) continue
            letters++
            if (ch in '\u0600'..'\u06FF') arabic++
        }
        if (letters == 0) return 0.0
        return arabic.toDouble() / letters
    }

    private fun hasForeignScript(text: String): Boolean = text.any {
        it in '\u4E00'..'\u9FFF' || it in '\u3040'..'\u30FF' || it in '\uAC00'..'\uD7AF' || it in '\u0400'..'\u04FF'
    }

    private fun digitsOf(text: String): Set<String> =
        digits.findAll(ArabicLexicon.normalizeText(text)).map { it.value.trimStart('0').ifEmpty { "0" } }.toSet()

    fun validate(rawOutput: String, points: List<GroundedPoint>): Result {
        if (points.isEmpty()) return Result(false, "no_points")
        val output = stripThinking(rawOutput)
        if (output.length < MIN_CHARS) return Result(false, "short")
        if (hasForeignScript(output)) return Result(false, "foreign_script")
        if (arabicRatio(output) < MIN_ARABIC_RATIO) return Result(false, "not_arabic")

        val inputLength = points.sumOf { it.text.length + it.herbName.length }
        val maxLength = maxOf(MIN_ALLOWED_CHARS, (inputLength * MAX_GROWTH).toInt())
        if (output.length > maxLength) return Result(false, "too_long")

        val inputNumbers = digitsOf(points.joinToString(" ") { it.text })
        val outputNumbers = digitsOf(output)
        if (outputNumbers.any { it !in inputNumbers }) return Result(false, "invented_number")

        val vocab = outputVocabulary(output)

        val herbNames = points.map { it.herbName }.distinct()
        for (name in herbNames) {
            val tokens = herbTokens(name)
            if (tokens.isEmpty()) continue
            if (!tokens.all { containsWord(vocab, it) }) return Result(false, "missing_herb")
        }

        val warningPoints = points.filter { it.field == "التحذيرات" || it.field == "الأضرار" }
        if (warningPoints.isNotEmpty()) {
            var satisfied = 0
            for (p in warningPoints) {
                val keys = wordsOf(p.text)
                    .map { ArabicLexicon.stripAl(it) }
                    .filter { it.length >= 4 && it !in genericWords }
                    .distinct()
                    .sortedByDescending { it.length }
                    .take(3)
                if (keys.isEmpty() || keys.any { containsWord(vocab, it) }) satisfied++
            }
            val required = maxOf(1, Math.ceil(warningPoints.size * WARNING_COVERAGE).toInt())
            if (satisfied < required) return Result(false, "missing_warning")
        }
        return Result(true, "ok")
    }
}
