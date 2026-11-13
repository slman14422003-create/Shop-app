package com.shopmanager.app.assistant.engine

internal object FillerWords {
    private val ALWAYS = setOf("يعني", "هلق", "هلا", "هلأ", "بليز", "please", "سمحت", "رجاء")
    private val NAMEABLE = setOf("معلم", "استاذ", "حبيبي", "خيو", "خيي", "اخي", "يا")
    private val FOLLOWED_BY_VERB = setOf("شو", "لو", "ايش")

    fun strip(toks: List<Tok>, known: Set<String>): List<Tok> {
        if (toks.size < 2) return toks
        val out = ArrayList<Tok>(toks.size)
        for ((i, t) in toks.withIndex()) {
            val w = t.norm
            val next = toks.getOrNull(i + 1)?.norm
            val drop = when {
                w == "هلا" -> i > 0 && toks.size > 2
                w in ALWAYS -> true
                w in NAMEABLE -> ArabicText.stripAl(w) !in known
                w in FOLLOWED_BY_VERB -> next != null && (next in Lexicon.COMMAND_VERBS || next == "سمحت")
                else -> false
            }
            if (!drop) out.add(t)
        }
        return if (out.isEmpty()) toks else out
    }
}

internal object SpellFixer {
    private val GLUED_PREFIXES = listOf("ضيف", "اضف", "سجل", "احذف", "حذف", "شيل", "كيلو", "كيس", "علبه", "نص")

    fun split(toks: List<Tok>, known: Set<String>): List<Tok> {
        val out = ArrayList<Tok>(toks.size)
        for (t in toks) {
            val w = t.norm
            var done = false
            if (w.length >= 3 && w[0] == 'و') {
                val rest = w.substring(1)
                if (rest in Lexicon.FRACTIONS || rest in Lexicon.UNIT_WORDS || rest in Lexicon.PACK_WORDS || rest in Lexicon.DUAL_PACK || NumberParser.findNumbers(listOf(rest)).isNotEmpty()) {
                    out.add(Tok(t.raw.take(1), "و"))
                    out.add(Tok(t.raw.drop(1), rest))
                    continue
                }
            }
            if (w.length >= 6 && w[0] !in '0'..'9' && w !in known) {
                for (p in GLUED_PREFIXES) {
                    if (w.length >= p.length + 3 && w.startsWith(p)) {
                        val rest = w.substring(p.length)
                        val key = ArabicText.stripAl(rest)
                        if (key in known || Synonyms.canon(key) != key || Synonyms.canon(key) in known) {
                            out.add(Tok(t.raw.take(p.length), p))
                            out.add(Tok(t.raw.drop(p.length), rest))
                            done = true
                            break
                        }
                    }
                }
            }
            if (!done) out.add(t)
        }
        return out
    }
}
