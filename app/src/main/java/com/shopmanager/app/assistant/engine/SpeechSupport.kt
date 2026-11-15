package com.shopmanager.app.assistant.engine

data class WeakWord(val index: Int, val text: String, val alternatives: List<String>)

data class RankedSpeech(
    val text: String,
    val candidates: List<String>,
    val weak: List<WeakWord>
)

object SpeechRanker {

    private fun knownTokens(snapshot: EngineSnapshot): Set<String> {
        val s = HashSet<String>()
        for (p in snapshot.persons) EntityMatcher.tokensOf(p.name).forEach { s.add(ArabicText.stripAl(it)) }
        for (m in snapshot.materials) EntityMatcher.tokensOf(m.name).forEach { s.add(ArabicText.stripAl(it)) }
        for (a in snapshot.aliases.keys) EntityMatcher.tokensOf(a).forEach { s.add(ArabicText.stripAl(it)) }
        return s
    }

    private fun words(text: String): List<String> =
        text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    private fun isCommonWord(t: String): Boolean =
        Lexicon.isStop(t) || NumberParser.findNumbers(listOf(t)).isNotEmpty() || t[0] in '0'..'9'

    private fun bestKnown(t: String, known: Set<String>): Pair<String, Double>? {
        val key = ArabicText.stripAl(t)
        if (key in known) return key to 1.0
        val canon = Synonyms.canon(key)
        if (canon in known) return canon to 0.95
        val pk = ArabicText.phoneticKey(key)
        var best: String? = null
        var bs = 0.0
        for (k in known) {
            if (kotlin.math.abs(k.length - key.length) > 2) continue
            var s = ArabicText.similarity(key, k)
            if (pk.isNotEmpty() && pk == ArabicText.phoneticKey(k) && key.length >= 3) s = maxOf(s, 0.88)
            if (s > bs) { bs = s; best = k }
        }
        return if (best != null && bs >= 0.78) best to bs else null
    }

    private fun scoreText(text: String, conf: Float, known: Set<String>): Double {
        val ws = words(text).map { ArabicText.canonicalToken(it) }.filter { it.isNotEmpty() }
        if (ws.isEmpty()) return 0.0
        var total = 0.0
        for (w in ws) {
            total += when {
                isCommonWord(w) -> 0.8
                else -> bestKnown(w, known)?.second?.let { if (it >= 1.0) 1.6 else it * 0.9 } ?: 0.0
            }
        }
        return total / ws.size + conf * 0.3
    }

    fun rank(result: SpeechResult, snapshot: EngineSnapshot): RankedSpeech {
        val known = knownTokens(snapshot)
        val pool = LinkedHashMap<String, Float>()
        pool[result.text] = 0.4f
        result.alternatives.forEach { if (it.text.isNotBlank() && it.text !in pool) pool[it.text] = it.confidence }
        val scored = pool.entries.mapIndexed { i, e -> Triple(e.key, scoreText(e.key, e.value.coerceIn(0f, 1f), known) + if (i == 0) 0.05 else 0.0, i) }
            .sortedWith(compareByDescending<Triple<String, Double, Int>> { it.second }.thenBy { it.third })
        val best = scored.firstOrNull()?.first ?: result.text
        val fixed = phoneticFix(best, known)
        val weak = weakWords(fixed, result, known)
        return RankedSpeech(fixed, scored.map { it.first }, weak)
    }

    private fun phoneticFix(text: String, known: Set<String>): String {
        if (known.isEmpty()) return text
        val ws = words(text)
        val out = ArrayList<String>(ws.size)
        for (w in ws) {
            val c = ArabicText.canonicalToken(w)
            if (c.length < 3 || isCommonWord(c) || ArabicText.stripAl(c) in known) { out.add(w); continue }
            val b = bestKnown(c, known)
            if (b != null && b.second >= 0.85 && b.second < 1.0) out.add(b.first) else out.add(w)
        }
        return out.joinToString(" ")
    }

    private fun weakWords(text: String, result: SpeechResult, known: Set<String>): List<WeakWord> {
        val ws = words(text)
        val conf = HashMap<String, Float>()
        result.words.forEach { conf[ArabicText.canonicalToken(it.text)] = it.confidence }
        val out = ArrayList<WeakWord>()
        for ((i, w) in ws.withIndex()) {
            val c = ArabicText.canonicalToken(w)
            if (c.length < 2 || isCommonWord(c)) continue
            val inKnown = ArabicText.stripAl(c) in known
            val low = (conf[c] ?: 1f) < 0.7f
            val unknown = !inKnown && Synonyms.canon(ArabicText.stripAl(c)) !in known
            if (low || (unknown && known.isNotEmpty())) {
                out.add(WeakWord(i, w, alternativesFor(c, known)))
            }
        }
        return out
    }

    fun alternativesFor(token: String, known: Set<String>, limit: Int = 3): List<String> {
        val key = ArabicText.stripAl(token)
        return known.map { it to ArabicText.similarity(key, it) }
            .filter { it.second >= 0.5 && it.first != key }
            .sortedByDescending { it.second }.take(limit).map { it.first }
    }

    fun alternativesFor(token: String, snapshot: EngineSnapshot, limit: Int = 3): List<String> =
        alternativesFor(ArabicText.canonicalToken(token), knownTokens(snapshot), limit)
}

sealed interface VoiceChoiceResult {
    data class Option(val index: Int) : VoiceChoiceResult
    data object Confirm : VoiceChoiceResult
    data object Cancel : VoiceChoiceResult
    data object Undo : VoiceChoiceResult
    data object None : VoiceChoiceResult
}

object VoiceChoice {
    private val ORDINALS = mapOf(
        "اولي" to 0, "اول" to 0, "الاول" to 0, "الاولي" to 0, "واحد" to 0, "1" to 0, "هاي" to -1,
        "تانيه" to 1, "ثانيه" to 1, "تاني" to 1, "ثاني" to 1, "التانيه" to 1, "التاني" to 1, "الثانيه" to 1, "الثاني" to 1, "اثنين" to 1, "اتنين" to 1, "2" to 1,
        "تالته" to 2, "ثالثه" to 2, "تالت" to 2, "ثالث" to 2, "التالته" to 2, "التالت" to 2, "الثالثه" to 2, "الثالث" to 2, "ثلاثه" to 2, "تلاته" to 2, "3" to 2,
        "رابعه" to 3, "رابع" to 3, "الرابعه" to 3, "الرابع" to 3, "اربعه" to 3, "4" to 3
    )
    private val CONFIRM = setOf("هي", "هاي", "هيك", "هاد", "هيدا", "نفذ", "نفذها", "نفذو", "ايوه", "اه", "اي", "ايوا", "تمام", "نعم", "ماشي", "اكيد", "صح")
    private val CANCEL = setOf("لا", "لأ", "لاء", "الغي", "الغاء", "بلاش", "كنسل")
    private val UNDO = setOf("تراجع", "ارجع", "رجع", "رجعها", "تراجعي")

    fun parse(text: String): VoiceChoiceResult {
        val ws = text.trim().split(Regex("\\s+")).map { ArabicText.canonicalToken(it) }.filter { it.isNotEmpty() && it != "ال" && it != "و" }
        if (ws.isEmpty() || ws.size > 3) return VoiceChoiceResult.None
        for (w in ws) if (w in UNDO) return VoiceChoiceResult.Undo
        val first = ws[0]
        if (first in CANCEL && ws.size == 1) return VoiceChoiceResult.Cancel
        if (first == "لا" || first == "لأ") return VoiceChoiceResult.None
        for (w in ws) {
            val o = ORDINALS[w]
            if (o != null && o >= 0) return VoiceChoiceResult.Option(o)
        }
        if (ws.all { it in CONFIRM }) return VoiceChoiceResult.Confirm
        if (ws.size == 2 && ws[0] in CONFIRM && ws[1] in CONFIRM) return VoiceChoiceResult.Confirm
        return VoiceChoiceResult.None
    }
}

object Vocabulary {
    private val COMMAND_WORDS = listOf(
        "ضيف", "سجل", "سدد", "احذف", "شيل", "نقص", "كم", "قديش", "شو", "ناقص", "نواقص", "دين", "ديون", "على", "سعر", "خلص",
        "اشتريت", "وصل", "جبنا", "كيلو", "نص", "ربع", "كيس", "علبة", "كرتونة", "هام", "مهم", "تراجع", "نفذ", "لا", "الاولى", "التانية",
        "التالتة", "الباقي", "كمان", "ثم", "اليوم", "مبارح", "بكرة", "ملخص", "اكثر", "مديونين", "عندنا"
    )
    private val NUMBER_WORDS = listOf(
        "واحد", "اتنين", "تلاتة", "اربعة", "خمسة", "ستة", "سبعة", "تمانية", "تسعة", "عشرة", "احداشر", "اثناشر", "تلتاشر", "اربعتاشر",
        "خمستاشر", "ستاشر", "سبعتاشر", "تمنتاشر", "تسعتاشر", "عشرين", "تلاتين", "اربعين", "خمسين", "ستين", "سبعين", "تمانين", "تسعين",
        "ميه", "ميتين", "تلتميه", "اربعميه", "خمسميه", "ستميه", "سبعميه", "تمنميه", "تسعميه", "الف", "الفين", "تلات الاف", "مليون", "مليونين"
    )

    fun build(snapshot: EngineSnapshot, maxWords: Int = 700): VocabularyList {
        val words = LinkedHashSet<String>()
        val phrases = LinkedHashSet<String>()
        val persons = snapshot.persons.sortedByDescending { it.lastDebtAt }
        for (p in persons) {
            phrases.add(p.name)
            EntityMatcher.tokensOf(p.name).forEach { words.add(it) }
        }
        val mats = snapshot.materials.sortedByDescending { it.usage }
        for (m in mats) {
            phrases.add(m.name)
            EntityMatcher.tokensOf(m.name).forEach { words.add(it) }
        }
        for (a in snapshot.aliases.keys) {
            phrases.add(a)
        }
        words.addAll(COMMAND_WORDS)
        words.addAll(NUMBER_WORDS)
        return VocabularyList(words.take(maxWords), phrases.take(maxWords))
    }
}

object ListMode {
    private val SPLIT = Regex("\\s*[،,؛;\\n]+\\s*|\\s+و(?=\\S)")

    fun items(text: String): List<String> {
        val parts = text.trim().split(Regex("\\s*[،,؛;\\n]+\\s*")).map { it.trim() }.filter { it.isNotEmpty() }
        return parts.flatMap { p ->
            val seg = p.split(Regex("\\s+و\\s+|\\s+و(?=[^\\s])")).map { it.trim() }
            if (seg.size > 1 && seg.all { it.split(' ').size <= 3 }) seg else listOf(p)
        }.filter { it.isNotEmpty() }
    }

    fun asCommandText(text: String): String {
        val its = items(text)
        return "ضيف " + its.joinToString(" ، ")
    }
}
