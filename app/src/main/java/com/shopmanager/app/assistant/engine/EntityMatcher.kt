package com.shopmanager.app.assistant.engine

import kotlin.math.abs
import kotlin.math.max

/** مدخل في فهرس المطابقة: الاسم الأصلي + صيغه المطبَّعة (الاسم الكامل والأسماء البديلة). */
internal class IndexEntry(val canonical: String, val id: String?, val forms: List<List<String>>, val aliasForms: List<List<String>>)

internal class SpanMatch(
    val start: Int,
    val end: Int,
    val rawSpan: String,
    val best: Candidate,
    val all: List<Candidate>,
    val viaAlias: Boolean
) {
    val level: MatchLevel get() = EntityMatcher.levelOf(best.score)
}

internal object EntityMatcher {
    const val HIGH = 0.90
    const val MEDIUM = 0.72
    private val KUNYA = setOf("ابو", "ام", "ابن", "بن", "بنت")

    fun levelOf(score: Double): MatchLevel = when {
        score >= 0.999 -> MatchLevel.EXACT
        score >= HIGH -> MatchLevel.HIGH
        score >= MEDIUM -> MatchLevel.MEDIUM
        else -> MatchLevel.NONE
    }

    fun tokensOf(name: String): List<String> =
        name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map { ArabicText.canonicalToken(it) }

    private fun key(tokens: List<String>): List<String> {
        val kept = tokens.filter { !ArabicText.isTitle(it) }.ifEmpty { tokens }
        return kept.map { Synonyms.canon(ArabicText.stripAl(it)) }
    }

    fun formOf(name: String): List<String> = key(tokensOf(name))

    /** تشابه متساهل للاقتراحات فقط: نسبة أجزاء النص القريبة من أجزاء الاسم. */
    fun looseScore(spanIn: List<String>, form: List<String>): Double {
        val span = key(spanIn)
        if (span.isEmpty() || form.isEmpty()) return 0.0
        var hit = 0
        for (t in span) if (form.any { f -> t == f || ArabicText.similarity(t, f) >= 0.75 }) hit++
        if (hit == 0) return 0.0
        return hit.toDouble() / span.size * 0.7
    }

    fun buildIndex(names: List<Pair<String, String?>>, aliases: Map<String, String>): List<IndexEntry> {
        val aliasByCanon = HashMap<String, MutableList<List<String>>>()
        for ((alias, canon) in aliases) {
            aliasByCanon.getOrPut(canon.trim()) { ArrayList() }.add(key(tokensOf(alias)))
        }
        return names.map { (name, id) ->
            IndexEntry(
                canonical = name,
                id = id,
                forms = listOf(key(tokensOf(name))),
                aliasForms = aliasByCanon[name.trim()] ?: emptyList()
            )
        }
    }

    /** تشابه بين نص كتبه المستخدم وصيغة اسم محفوظ (كلاهما قائمة توكنات بلا ألقاب). */
    fun scoreForm(spanIn: List<String>, form: List<String>): Double {
        val span = key(spanIn)
        if (span.isEmpty() || form.isEmpty()) return 0.0
        if (span == form) return 1.0
        if (span.size < form.size) {
            if (form.take(span.size) == span) return 0.93
            if (span.size == 1 && form.contains(span[0])) {
                return if (form[0] in KUNYA && form.size >= 2 && form[1] == span[0]) 0.74 else 0.80
            }
            if (span.size >= 2 && form.containsAll(span)) return 0.86
            return ArabicText.similarity(span.joinToString(" "), form.take(span.size).joinToString(" ")) * 0.95
        }
        if (span.size > form.size) {
            if (span.take(form.size) == form) return 0.82
            return ArabicText.similarity(span.take(form.size).joinToString(" "), form.joinToString(" ")) * 0.9
        }
        // نفس عدد الأجزاء: متوسط تشابه الأجزاء، مع عقوبة لجزء بعيد جداً.
        var sum = 0.0
        var worst = 1.0
        for (i in span.indices) {
            val s = ArabicText.similarity(span[i], form[i])
            sum += s
            if (s < worst) worst = s
        }
        var avg = sum / span.size
        if (worst < 0.6) avg *= 0.8
        return avg
    }

    private fun entryScore(span: List<String>, e: IndexEntry): Pair<Double, Boolean> {
        var best = 0.0
        for (f in e.forms) best = max(best, scoreForm(span, f))
        var viaAlias = false
        for (f in e.aliasForms) {
            val s = scoreForm(span, f)
            if (s >= 0.999 && best < 0.999) { best = 1.0; viaAlias = true }
        }
        return best to viaAlias
    }

    /** أفضل المرشحين لمقطع، مرتبين تنازلياً (بعد حذف الألقاب). */
    fun candidatesFor(span: List<String>, index: List<IndexEntry>, limit: Int = 4): Pair<List<Candidate>, Boolean> {
        val spanLen = key(span).joinToString(" ").length
        val out = ArrayList<Candidate>()
        var alias = false
        for (e in index) {
            val formLen = e.forms.firstOrNull()?.joinToString(" ")?.length ?: 0
            if (e.aliasForms.isEmpty() && abs(formLen - spanLen) > max(4, formLen / 2 + 2) && spanLen > 0 &&
                key(span).size == (e.forms.firstOrNull()?.size ?: 0)
            ) continue
            val (s, a) = entryScore(span, e)
            if (s >= MEDIUM) { out.add(Candidate(e.canonical, e.id, s)); if (a && s >= 0.999) alias = true }
        }
        out.sortByDescending { it.score }
        return out.take(limit) to alias
    }

    /** يقترح أول توكن بعد حذف حرف جر ملتصق (لسامر، بسامر). */
    private fun prefixVariants(tokens: List<String>): List<List<String>> {
        val first = tokens[0]
        val out = ArrayList<List<String>>()
        out.add(tokens)
        if (first.length > 3 && (first[0] == 'ل' || first[0] == 'ب' || first[0] == 'و' || first[0] == 'ف' || first[0] == 'ك')) {
            out.add(listOf(first.substring(1)) + tokens.drop(1))
            if (first.length > 4 && first[1] == 'ا' && first[2] == 'ل') out.add(listOf(first.substring(3)) + tokens.drop(1))
        }
        if (first.length > 4 && first.startsWith("لل")) out.add(listOf(first.substring(2)) + tokens.drop(1))
        return out
    }

    /**
     * يمرّ على كل المقاطع (1 إلى 3 توكنات) من التوكنات المتاحة ويختار أفضل مطابقات غير متداخلة.
     * [available] = false للتوكنات المحجوزة (أرقام، تواريخ) أو الكلمات المفتاحية.
     */
    fun findMatches(
        raw: List<String>,
        norm: List<String>,
        available: BooleanArray,
        index: List<IndexEntry>
    ): List<SpanMatch> {
        if (index.isEmpty()) return emptyList()
        val all = ArrayList<SpanMatch>()
        for (start in norm.indices) {
            if (!available[start]) continue
            var end = start
            while (end < norm.size && end - start < 3 && available[end]) {
                end++
                val tokens = norm.subList(start, end)
                var bestC: List<Candidate> = emptyList()
                var bestAlias = false
                for (variant in prefixVariants(tokens)) {
                    val (c, a) = candidatesFor(variant, index)
                    if (c.isNotEmpty() && (bestC.isEmpty() || c[0].score > bestC[0].score)) { bestC = c; bestAlias = a }
                }
                if (bestC.isNotEmpty()) {
                    all.add(SpanMatch(start, end, raw.subList(start, end).joinToString(" "), bestC[0], bestC, bestAlias))
                }
            }
        }
        // الأعلى درجة أولاً، ثم الأطول، ثم الأبكر؛ ونختار غير المتداخل.
        all.sortWith(compareByDescending<SpanMatch> { it.best.score }.thenByDescending { it.end - it.start }.thenBy { it.start })
        val taken = BooleanArray(norm.size)
        val picked = ArrayList<SpanMatch>()
        for (m in all) {
            var free = true
            for (i in m.start until m.end) if (taken[i]) { free = false; break }
            if (!free) continue
            for (i in m.start until m.end) taken[i] = true
            picked.add(m)
        }
        picked.sortBy { it.start }
        return picked
    }

    /** هل المطابقة غامضة (مرشحان مختلفان بدرجتين متقاربتين)؟ */
    fun isAmbiguous(m: SpanMatch): Boolean {
        if (m.all.size < 2) return false
        val a = m.all[0]; val b = m.all[1]
        if (a.score >= 0.999 && b.score < 0.999) return false
        return a.name != b.name && b.score >= MEDIUM && b.score >= a.score - 0.03
    }
}
