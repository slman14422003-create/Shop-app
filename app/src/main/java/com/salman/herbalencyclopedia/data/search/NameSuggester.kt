package com.salman.herbalencyclopedia.data.search

import com.salman.herbalencyclopedia.data.ai.ArabicLexicon
import com.salman.herbalencyclopedia.data.ai.HerbAssistant
import com.salman.herbalencyclopedia.data.model.Herb

object NameSuggester {

    private val stopWords: Set<String> by lazy {
        listOf(
            "ما", "ماذا", "هل", "هي", "هو", "ماهي", "ماهو", "فوائد", "فوايد", "فايده", "فائده", "اضرار", "ضرر",
            "استخدام", "استعمال", "طريقه", "كيف", "عن", "في", "من", "على", "الى", "مع", "ايش", "شو", "وش",
            "تحذيرات", "تحذير", "خطر", "اثار", "جانبيه", "حامل", "للحامل", "لل", "عشبه", "نبات", "اعشاب",
            "تفيد", "يفيد", "ينفع", "بتنفع", "علاج", "لعلاج", "ابي", "ابغى", "اريد", "طيب", "وش", "قارن", "بين",
            "امان", "آمنه", "امنه", "اطفال", "للاطفال", "طفل"
        ).map { ArabicLexicon.stripAl(ArabicLexicon.normalizeText(it)) }.toSet()
    }

    private class Entry(val herb: Herb, val tokens: List<String>)

    private fun fold(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            when (ch) {
                'ث' -> sb.append('ت')
                'ذ' -> sb.append('د')
                'ظ' -> sb.append('ض')
                'ق' -> sb.append('ك')
                'ؤ' -> sb.append('و')
                'ئ' -> sb.append('ي')
                'ء' -> Unit
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun queryTokens(question: String): List<String> {
        val dialect = HerbAssistant.dialectNormalize(question)
        return dialect.split(' ')
            .map { ArabicLexicon.stripAl(it) }
            .filter { it.length >= 3 && it !in stopWords }
            .distinct()
    }

    private fun entryFor(herb: Herb): Entry {
        val core = ArabicLexicon.normalizeText(ArabicLexicon.arabicNameCore(herb.name))
        val tokens = core.split(' ').map { ArabicLexicon.stripAl(it) }.filter { it.length >= 3 }
        return Entry(herb, tokens)
    }

    private fun tokenDistance(q: String, n: String): Int {
        if (q == n) return 0
        val fq = fold(q)
        val fn = fold(n)
        if (fq == fn) return 1
        if (n.startsWith(q) && q.length >= 3) return 1
        val max = if (fq.length <= 5 || fn.length <= 5) 1 else 2
        if (kotlin.math.abs(fq.length - fn.length) > max) return Int.MAX_VALUE
        val d = ArabicLexicon.editDistanceAtMost(fq, fn, max)
        return if (d <= max) d + 1 else Int.MAX_VALUE
    }

    fun suggest(question: String, herbs: List<Herb>, limit: Int = 3): List<String> {
        if (herbs.isEmpty()) return emptyList()
        val qTokens = queryTokens(question)
        if (qTokens.isEmpty()) return emptyList()
        val scored = ArrayList<Pair<Herb, Int>>()
        for (herb in herbs) {
            val entry = entryFor(herb)
            if (entry.tokens.isEmpty()) continue
            var best = Int.MAX_VALUE
            for (q in qTokens) {
                for (n in entry.tokens) {
                    val d = tokenDistance(q, n)
                    if (d < best) best = d
                }
            }
            if (best != Int.MAX_VALUE) scored += herb to best
        }
        return scored
            .sortedWith(compareBy({ it.second }, { it.first.name.length }))
            .map { it.first.name }
            .distinct()
            .take(limit)
    }
}
