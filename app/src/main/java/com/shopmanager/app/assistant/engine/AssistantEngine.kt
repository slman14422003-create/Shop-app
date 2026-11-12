package com.shopmanager.app.assistant.engine

import java.util.EnumMap
import kotlin.math.max

internal class Tok(val raw: String, val norm: String)

private class Qty(val value: Double?, val unit: String?, val pack: String?)

private class Analysis(
    val toks: List<Tok>,
    val numbers: List<NumberMatch>,
    val money: List<NumberMatch>,
    val amountNums: List<NumberMatch>,
    val dateFuture: DateParse?,
    val datePast: DateParse?,
    val consumed: Set<Int>,
    val personMatches: List<SpanMatch>,
    val materialMatches: List<SpanMatch>,
    val free: List<IntRange>,
    val freeNoPerson: List<IntRange>,
    val pronoun: Boolean,
    val available: BooleanArray
) {
    val raw: List<String> = toks.map { it.raw }
    val norm: List<String> = toks.map { it.norm }
}

/**
 * محرك الفهم المحلي للمساعد: قواعد ونقاط بلا أي نموذج لغوي. يأخذ جملة المستخدم + لقطة بيانات التطبيق + سياق المحادثة
 * ويرجع أوامر منظّمة (النية + الكيانات + الثقة + الحقول الناقصة). لا يلمس أندرويد ولا Firestore.
 */
class AssistantEngine(private val fallback: NluFallback? = null) {

    // ------------------------------------------------------------------ واجهة عامة

    fun understand(
        input: String,
        snapshot: EngineSnapshot,
        context: ConversationContext = ConversationContext()
    ): EngineOutput {
        val toks = correctTokens(tokenize(input), snapshot)
        if (toks.isEmpty()) return output(listOf(Command(Intent.UNKNOWN, rawText = input)), context, snapshot)
        val idx = Indexes(snapshot)
        val segments = splitSegments(toks)

        var ctx = context
        val commands = ArrayList<Command>()
        var hint: Intent? = null
        for (seg in segments) {
            val cmd = parseSegment(seg, snapshot, idx, ctx, hint, null, null)
            commands.add(cmd)
            if (cmd.intent == Intent.ADD_DEBT || cmd.intent == Intent.ADD_SHORTAGE) hint = cmd.intent
            ctx = updateContext(ctx, cmd)
        }

        val pend = context.pending
        if (pend != null && commands.size == 1) {
            val only = commands[0]
            if (only.intent == Intent.CANCEL) return output(commands, context.copy(pending = null), snapshot)
            if (only.intent == Intent.CONFIRM && pend.ambiguity != null && pend.ambiguity.options.isNotEmpty()) {
                val resolved = resolveChoice(pend, pend.ambiguity.options[0], snapshot, context)
                return output(listOf(resolved), context.copy(pending = null), snapshot)
            }
            val strong = only.score >= 6.5 && only.intent != Intent.ADD_DEBT && only.intent != Intent.UNKNOWN
            if (!strong) {
                val cont = continuePending(pend, toks, snapshot, idx, context)
                if (cont != null) return output(listOf(cont), context, snapshot)
            }
        }

        if (commands.all { it.intent == Intent.UNKNOWN } && fallback != null) {
            val fb = fallback.interpret(input, snapshot, context)
            if (fb != null) return fb
        }
        return output(commands, ctx, snapshot)
    }

    /** يطبّق اختيار المستخدم من بطاقة التوضيح ويرجع الأمر المحدّث. */
    fun resolveChoice(
        command: Command,
        option: AmbiguityOption,
        snapshot: EngineSnapshot,
        context: ConversationContext = ConversationContext()
    ): Command {
        val amb = command.ambiguity ?: return command
        return when (amb.kind) {
            AmbiguityKind.PERSON, AmbiguityKind.FUZZY_PERSON -> {
                val (id, name) = splitOption(option.value)
                val raw = command.person?.rawSpan ?: name
                val learn = if (amb.kind == AmbiguityKind.FUZZY_PERSON &&
                    ArabicText.normalize(raw) != ArabicText.normalize(name)
                ) AliasLearn(raw, name) else null
                validate(
                    command.copy(
                        person = PersonRef(raw, id.ifBlank { null }, name, 1.0, MatchLevel.EXACT),
                        ambiguity = null, aliasToLearn = learn ?: command.aliasToLearn
                    ), snapshot
                )
            }
            AmbiguityKind.MATERIAL, AmbiguityKind.FUZZY_MATERIAL -> {
                val (idxStr, name) = splitOption(option.value)
                val i = idxStr.toIntOrNull() ?: 0
                val list = command.materials.toMutableList()
                if (i in list.indices) {
                    val old = list[i]
                    list[i] = old.copy(name = name, score = 1.0, level = MatchLevel.EXACT, candidates = emptyList())
                    val learn = if (amb.kind == AmbiguityKind.FUZZY_MATERIAL &&
                        ArabicText.normalize(old.rawSpan) != ArabicText.normalize(name)
                    ) AliasLearn(old.rawSpan, name) else null
                    validate(command.copy(materials = list, ambiguity = null, aliasToLearn = learn ?: command.aliasToLearn), snapshot)
                } else command
            }
            AmbiguityKind.INTENT -> {
                val forced = runCatching { Intent.valueOf(option.value) }.getOrNull() ?: return command
                val toks = correctTokens(tokenize(command.rawText), snapshot)
                parseSegment(toks, snapshot, Indexes(snapshot), context, null, forced, null)
            }
        }
    }

    private fun splitOption(v: String): Pair<String, String> {
        val i = v.indexOf('|')
        return if (i < 0) "" to v else v.substring(0, i) to v.substring(i + 1)
    }

    // ------------------------------------------------------------------ تقطيع

    private class Indexes(snapshot: EngineSnapshot) {
        val persons = EntityMatcher.buildIndex(snapshot.persons.map { it.name to it.id }, snapshot.aliases)
        val materials = EntityMatcher.buildIndex(snapshot.materials.map { it.name to null }, snapshot.aliases)
    }

    private fun digitsToAscii(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            when (ch) {
                in '٠'..'٩' -> sb.append('0' + (ch - '٠'))
                in '۰'..'۹' -> sb.append('0' + (ch - '۰'))
                '٫' -> sb.append('.')
                '٬' -> sb.append(',')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    internal fun tokenize(input: String): List<Tok> {
        var s = digitsToAscii(input.trim())
        s = s.replace(Regex("([×÷*+=])"), " $1 ")
        s = s.replace(Regex("(?<=\\d)\\s*[xX]\\s*(?=\\d)"), " × ")
        s = s.replace(Regex("(?<=\\d)\\s*-\\s*(?=\\d)"), " - ")
        s = s.replace(Regex("(?<=\\d)\\s*/\\s*(?=\\d)"), " / ")
        s = s.replace("،", " , ").replace("؛", " , ")
        s = s.replace(Regex("(?<!\\d),|,(?!\\d)"), " , ")
        s = s.replace(Regex("[؟?!:\"'()\\[\\]{}…]"), " ")
        s = s.replace(Regex("(?<!\\d)\\.|\\.(?!\\d)"), " ")
        return s.split(Regex("\\s+")).filter { it.isNotEmpty() }.map { Tok(it, ArabicText.canonicalToken(it)) }
    }

    private val verbPool: List<String> by lazy { Lexicon.COMMAND_VERBS.filter { it.length >= 4 } }
    private val qtyPool: List<String> by lazy {
        (Lexicon.UNIT_WORDS.keys + Lexicon.PACK_WORDS.keys + Lexicon.IMPORTANT_WORDS).filter { it.length >= 4 }
    }

    private fun nearest(t: String, pool: List<String>): String? {
        var best: String? = null
        var bd = 2
        for (w in pool) {
            if (kotlin.math.abs(w.length - t.length) > 1) continue
            val d = ArabicText.levenshtein(t, w)
            if (d < bd) { bd = d; best = w }
        }
        return best
    }

    /** يصحح خربطة حرف واحد في أفعال الأوامر ووحدات الكمية وكلمات الأهمية، دون المساس بأسماء العملاء والمواد المعروفة. */
    private fun correctTokens(toks: List<Tok>, snapshot: EngineSnapshot): List<Tok> {
        if (toks.size < 2) return toks
        val known = HashSet<String>()
        for (p in snapshot.persons) EntityMatcher.tokensOf(p.name).forEach { known.add(ArabicText.stripAl(it)) }
        for (m in snapshot.materials) EntityMatcher.tokensOf(m.name).forEach { known.add(ArabicText.stripAl(it)) }
        for (a in snapshot.aliases.keys) EntityMatcher.tokensOf(a).forEach { known.add(ArabicText.stripAl(it)) }
        val out = ArrayList<Tok>(toks.size)
        for ((i, t) in toks.withIndex()) {
            val w = t.norm
            val skip = w.length < 4 || w[0] in '0'..'9' || Lexicon.isStop(w) || ArabicText.stripAl(w) in known || NumberParser.findNumbers(listOf(w)).isNotEmpty()
            if (skip) { out.add(t); continue }
            val verbPos = i == 0 || toks[i - 1].norm in conj
            var fixed: String? = null
            if (verbPos) fixed = nearest(w, verbPool)
            if (fixed == null) fixed = nearest(w, qtyPool)
            out.add(if (fixed != null) Tok(t.raw, fixed) else t)
        }
        return out
    }

    private val conj = setOf("و", "ثم", "بعدين", "وبعدين", "كمان", "وكمان", ",")

    private fun splitSegments(toks: List<Tok>): List<List<Tok>> {
        val out = ArrayList<List<Tok>>()
        var cur = ArrayList<Tok>()
        for (t in toks) {
            var tok = t
            var boundary = false
            if (cur.isNotEmpty()) {
                val prev = cur.last()
                if (t.norm.length > 2 && t.norm[0] == 'و' && t.norm.substring(1) in Lexicon.COMMAND_VERBS) {
                    boundary = true
                    tok = Tok(t.raw.drop(1), t.norm.substring(1))
                } else if (prev.norm in conj && t.norm in Lexicon.COMMAND_VERBS) {
                    boundary = true
                }
            }
            if (boundary) {
                while (cur.isNotEmpty() && cur.last().norm in conj) cur.removeAt(cur.size - 1)
                if (cur.isNotEmpty()) out.add(cur)
                cur = ArrayList()
            }
            cur.add(tok)
        }
        if (cur.isNotEmpty()) out.add(cur)
        return out
    }

    // ------------------------------------------------------------------ تحليل مقطع

    private fun analyze(toks: List<Tok>, snapshot: EngineSnapshot, idx: Indexes): Analysis {
        val norm = toks.map { it.norm }
        val raw = toks.map { it.raw }
        val numbers = NumberParser.findNumbers(norm)
        val dateFuture = DateParser.parse(norm, numbers, snapshot.nowMillis, snapshot.timeZoneId, true)
        val datePast = DateParser.parse(norm, numbers, snapshot.nowMillis, snapshot.timeZoneId, false)
        val consumed: Set<Int> = (datePast?.consumed ?: emptySet()) + (dateFuture?.consumed ?: emptySet())

        val money = numbers.filter { n -> (n.start until n.end).none { it in consumed } }
        val amountNums = money.filter { n ->
            val prev = if (n.start > 0) norm[n.start - 1] else ""
            val next = if (n.end < norm.size) norm[n.end] else ""
            !(isUnitish(prev) || isUnitish(next))
        }

        val n = norm.size
        val numMask = BooleanArray(n)
        for (m in numbers) for (i in m.start until m.end) numMask[i] = true
        val available = BooleanArray(n) { i ->
            !numMask[i] && i !in consumed && norm[i] != "," && !isStopAt(norm, i)
        }
        val personMatches = EntityMatcher.findMatches(raw, norm, available, idx.persons)
            .filter { it.best.score >= EntityMatcher.MEDIUM }
        val materialMatches = EntityMatcher.findMatches(raw, norm, available, idx.materials)
            .filter { it.best.score >= EntityMatcher.MEDIUM }

        val matched = BooleanArray(n)
        for (m in personMatches + materialMatches) for (i in m.start until m.end) matched[i] = true
        val free = ArrayList<IntRange>()
        var i = 0
        while (i < n) {
            if (available[i] && !matched[i]) {
                var j = i
                while (j + 1 < n && available[j + 1] && !matched[j + 1] && j + 1 - i < 3) j++
                free.add(i..j)
                i = j + 1
            } else i++
        }
        val matchedMat = BooleanArray(n)
        for (m in materialMatches) for (k in m.start until m.end) matchedMat[k] = true
        val freeNoPerson = ArrayList<IntRange>()
        var q = 0
        while (q < n) {
            if (available[q] && !matchedMat[q]) {
                var j = q
                while (j + 1 < n && available[j + 1] && !matchedMat[j + 1] && j + 1 - q < 3) j++
                freeNoPerson.add(q..j)
                q = j + 1
            } else q++
        }
        val pronoun = norm.any { it in Lexicon.PRONOUNS }
        return Analysis(toks, numbers, money, amountNums, dateFuture, datePast, consumed, personMatches, materialMatches, free, freeNoPerson, pronoun, available)
    }

    private fun isUnitish(t: String): Boolean =
        t in Lexicon.UNIT_WORDS || t in Lexicon.PACK_WORDS || t in Lexicon.DUAL_PACK || t in Lexicon.GRAM_WORDS ||
            t in Lexicon.FRACTIONS

    private val prepOnContext: Set<String> by lazy {
        Lexicon.RECORD_VERBS + Lexicon.QUESTION + Lexicon.DEBT_WORDS + setOf("كتب", "كم", "حط")
    }

    /** "علي": حرف جر بعد فعل التسجيل/السؤال، وإلا فهو اسم. */
    private fun isPrepOnAt(norm: List<String>, i: Int): Boolean {
        if (norm[i] == ArabicText.PREP_ON) return true
        if (norm[i] == "علي") return i > 0 && norm[i - 1] in prepOnContext
        return false
    }

    private fun isStopAt(norm: List<String>, i: Int): Boolean {
        val t = norm[i]
        if (t == "علي") return isPrepOnAt(norm, i)
        if (t in Lexicon.AMBIGUOUS_NAMES && t != "علي") {
            val verbPos = i == 0 || norm[i - 1] in conj
            return verbPos
        }
        return Lexicon.isStop(t)
    }

    // ------------------------------------------------------------------ النوايا

    private class Scored(val intent: Intent, val score: Double)

    private fun scoreIntents(an: Analysis, ctx: ConversationContext, hint: Intent?): List<Scored> {
        val norm = an.norm
        val n = norm.size
        val vs: List<List<String>> = norm.map { Lexicon.variantsOf(it) }
        fun hasAt(i: Int, set: Set<String>) = vs[i].any { it in set }
        fun hasW(set: Set<String>) = (0 until n).any { hasAt(it, set) }
        fun verbPos(i: Int) = i == 0 || norm[i - 1] in conj || (i == 1 && norm[0] in setOf("بدي", "بدنا", "لازم", "ممكن"))
        fun verbHit(set: Set<String>): Boolean = (0 until n).any { i ->
            hasAt(i, set) && (norm[i] !in Lexicon.AMBIGUOUS_NAMES || verbPos(i))
        }
        fun seq(a: Set<String>, b: Set<String>, gap: Int = 3): Boolean {
            for (i in 0 until n) if (hasAt(i, a)) for (j in i + 1..minOf(i + gap, n - 1)) if (hasAt(j, b)) return true
            return false
        }

        val s = EnumMap<Intent, Double>(Intent::class.java)
        fun add(i: Intent, w: Double) { s[i] = (s[i] ?: 0.0) + w }

        // ردود فورية قصيرة
        if (n <= 3 && norm.all { it in Lexicon.CONFIRM_WORDS }) return listOf(Scored(Intent.CONFIRM, 6.0))
        if (n <= 3 && norm.all { it in Lexicon.CANCEL_WORDS }) return listOf(Scored(Intent.CANCEL, 6.0))

        // ملاحظة وتذكير: ما بعدهما نص حر فلا نحلله
        if (verbHit(Lexicon.REMIND_VERBS)) return listOf(Scored(Intent.REMINDER, 10.0))
        val noteIdx = (0 until n).firstOrNull { hasAt(it, Lexicon.NOTE_WORDS) }
        if (noteIdx != null && noteIdx <= 3 && !hasW(Lexicon.OPEN_VERBS)) return listOf(Scored(Intent.ADD_NOTE, 10.0))

        val recordV = verbHit(Lexicon.RECORD_VERBS)
        val payV = verbHit(Lexicon.PAY_VERBS)
        val reduceV = verbHit(Lexicon.REDUCE_VERBS)
        val deleteV = verbHit(Lexicon.DELETE_VERBS)
        val openV = hasW(Lexicon.OPEN_VERBS)
        val qWord = hasW(Lexicon.QUESTION)
        val prepOn = (0 until n).any { isPrepOnAt(norm, it) }
        val debtW = hasW(Lexicon.DEBT_WORDS)
        val shortageW = hasW(Lexicon.SHORTAGE_WORDS)
        val priceW = hasW(Lexicon.PRICE_WORDS)
        val personPresent = an.personMatches.isNotEmpty() || (an.pronoun && ctx.lastPerson != null)
        val materialPresent = an.materialMatches.isNotEmpty()
        val freeName = an.free.isNotEmpty()
        val materialOrFree = materialPresent || freeName
        val amountAny = an.money.isNotEmpty()
        val amountNum = an.amountNums.isNotEmpty()
        val shortCue = hasW(Lexicon.FINISHED_STRONG) && !(hasW(setOf("خلص")) && personPresent)
        val importantW = hasW(Lexicon.IMPORTANT_WORDS)
        val adderV = hasW(setOf("ضيف", "ضيفلي", "ضيفلنا", "ضيفو", "ضيفوا", "اضف", "اضيف", "اضيفلي", "اضيفلنا", "ضيفها", "ضيفه", "زودلي", "زود", "تضيف", "بضيف", "اضافه"))
        val plainAdd = adderV && !prepOn && !debtW && !personPresent && !amountNum

        // حاسبة
        val calc = Calculator.evaluate(norm, an.numbers, hasW(Lexicon.CALC_WORDS))
        if (calc != null && (hasW(Lexicon.CALC_WORDS) || norm.any { it in setOf("×", "*", "x", "÷", "/", "+", "ضرب", "قسمه", "زائد") })) {
            return listOf(Scored(Intent.CALCULATE, 9.0))
        }

        // ---- الديون
        if (recordV) {
            add(Intent.ADD_DEBT, 2.0)
            if (prepOn || debtW) add(Intent.ADD_DEBT, 2.0)
        }
        if (debtW && !qWord && !openV) add(Intent.ADD_DEBT, 2.0)
        if (hasW(Lexicon.TOOK_VERBS)) add(Intent.ADD_DEBT, 4.0)
        if (an.pronoun && amountAny && recordV) add(Intent.ADD_DEBT, 2.0)
        if (personPresent && amountNum) add(Intent.ADD_DEBT, 1.5)
        if (amountNum) add(Intent.ADD_DEBT, 0.5)
        if (!personPresent && !debtW && !freeName && !an.pronoun) add(Intent.ADD_DEBT, -1.5)
        if (hint == Intent.ADD_DEBT && recordV && !shortageW) add(Intent.ADD_DEBT, 2.0)

        if (payV) {
            add(Intent.PAY_DEBT, 3.5)
            if (personPresent) add(Intent.PAY_DEBT, 1.0)
            if (debtW) add(Intent.PAY_DEBT, 0.5)
            if (hasW(setOf("خلص")) && !personPresent) { add(Intent.PAY_DEBT, -3.0) }
            if (hasW(Lexicon.NEG)) add(Intent.PAY_DEBT, -3.0)
        }
        if (hasW(Lexicon.FULL_WORDS) && payV) add(Intent.PAY_DEBT, 1.0)

        if (reduceV && (personPresent || amountNum || freeName)) {
            add(Intent.REDUCE_DEBT, 3.5)
            if (personPresent && amountNum) add(Intent.REDUCE_DEBT, 1.5)
            if (debtW) add(Intent.REDUCE_DEBT, 1.0)
            if (priceW) add(Intent.REDUCE_DEBT, -4.0)
        }

        if (deleteV) {
            add(Intent.DELETE_DEBT, 2.5)
            if (debtW || hasW(setOf("عمليه", "حركه"))) add(Intent.DELETE_DEBT, 2.5)
            if (personPresent) add(Intent.DELETE_DEBT, 1.5)
            if (hasW(Lexicon.LAST_WORDS)) add(Intent.DELETE_LAST, 7.0)
        }

        if (qWord && (prepOn || hasW(Lexicon.BALANCE_WORDS) || debtW)) {
            var qb = 3.0
            if (hasW(setOf("كم", "قديش", "بكم", "شكد", "اشكد"))) qb += 1.0
            if (personPresent) qb += 1.5
            if (!personPresent && !freeName) qb *= 0.3
            add(Intent.QUERY_BALANCE, qb)
        }
        if (hasW(setOf("رصيد")) && (personPresent || freeName)) add(Intent.QUERY_BALANCE, 4.5)

        if (debtW && (hasW(Lexicon.TOTAL_WORDS) || qWord) && !personPresent && !freeName) {
            add(Intent.QUERY_TOTAL_DEBTS, 4.5)
            if (hasW(setOf("مجموع", "اجمالي", "اجمال", "مجمل"))) add(Intent.QUERY_TOTAL_DEBTS, 1.0)
        }
        if (hasW(setOf("مجموع", "اجمالي")) && !personPresent && hasW(Lexicon.DEBT_WORDS)) add(Intent.QUERY_TOTAL_DEBTS, 1.0)

        if (hasW(Lexicon.TOP_WORDS) && (hasW(Lexicon.DEBTORS) || debtW)) add(Intent.QUERY_TOP_DEBTORS, 5.5)

        if (hasW(Lexicon.NEG) && hasW(Lexicon.PAID_WORDS)) {
            add(Intent.QUERY_STALE_DEBTORS, 3.5)
            if (hasW(Lexicon.STALE_WORDS)) add(Intent.QUERY_STALE_DEBTORS, 1.5)
            if (an.datePast != null && an.datePast.ref.endMillis != null) add(Intent.QUERY_STALE_DEBTORS, 1.5)
            if (!personPresent && !freeName) add(Intent.QUERY_STALE_DEBTORS, 1.0)
        }
        if (hasW(setOf("متاخر", "متاخرين", "متاخره", "متأخر"))) add(Intent.QUERY_STALE_DEBTORS, 3.0)

        // ---- النواقص
        if (shortageW) add(Intent.ADD_SHORTAGE, 3.0)
        if (recordV && materialOrFree) add(Intent.ADD_SHORTAGE, 1.5)
        if (recordV && materialPresent && !personPresent && !debtW) add(Intent.ADD_SHORTAGE, 2.5)
        if (shortCue && materialOrFree) add(Intent.ADD_SHORTAGE, 3.5)
        if (hasW(Lexicon.NEED_WEAK) && materialOrFree && !debtW && !personPresent) add(Intent.ADD_SHORTAGE, 3.5)
        if (hasW(setOf("للنواقص", "بالنواقص", "للقائمه"))) add(Intent.ADD_SHORTAGE, 2.0)
        if (hasW(setOf("خلص")) && !personPresent && materialOrFree && payV) add(Intent.ADD_SHORTAGE, 3.5)
        if (shortageW && !materialOrFree && !recordV) add(Intent.ADD_SHORTAGE, -2.5)
        if (hint == Intent.ADD_SHORTAGE && recordV && materialOrFree) add(Intent.ADD_SHORTAGE, 2.5)
        if (plainAdd && materialOrFree) { add(Intent.ADD_SHORTAGE, 2.5); add(Intent.ADD_DEBT, -1.5) }
        // أمر إضافة بلا مبلغ ولا "على" ولا كلمة دين = مادة ناقصة، حتى لو شبه اسمها اسم عميل.
        val qtyCue = (0 until n).any { isUnitish(norm[it]) && norm[it] !in Lexicon.FRACTIONS }
        val addCmd = recordV || adderV
        val tookV = hasW(Lexicon.TOOK_VERBS)
        val catalogOrMat = hasW(setOf("ثابته", "كتالوج")) || hasW(Lexicon.MATERIAL_WORDS)
        if (addCmd && !prepOn && !debtW && !amountNum && !tookV && !an.pronoun && !catalogOrMat) {
            val shortageCue = qtyCue || importantW
            if (materialOrFree && (!personPresent || shortageCue)) { add(Intent.ADD_SHORTAGE, 3.0); add(Intent.ADD_DEBT, -2.5) }
            else if (personPresent && shortageCue) { add(Intent.ADD_SHORTAGE, 4.0); add(Intent.ADD_DEBT, -3.0) }
        }
        if (addCmd && qtyCue && !prepOn && !debtW && !amountNum && !catalogOrMat) add(Intent.ADD_SHORTAGE, 1.5)
        if (importantW && materialOrFree && (!personPresent || !prepOn) && !debtW && (recordV || adderV || shortCue || hasW(Lexicon.NEED_WEAK))) add(Intent.ADD_SHORTAGE, 3.0)

        if (deleteV && materialOrFree) {
            add(Intent.REMOVE_SHORTAGE, 2.5)
            if (shortageW) add(Intent.REMOVE_SHORTAGE, 2.0)
            if (materialPresent) add(Intent.REMOVE_SHORTAGE, 1.5)
            if (personPresent) add(Intent.REMOVE_SHORTAGE, -1.5)
        }
        if (hasW(Lexicon.BOUGHT_VERBS) && materialOrFree) add(Intent.REMOVE_SHORTAGE, 4.0)

        if (shortageW) {
            add(Intent.LIST_SHORTAGES, 2.5)
            if (qWord || hasW(setOf("اعرض", "عرض", "وريني", "ورجيني", "شوف", "اظهر", "قائمه", "كل", "وين"))) add(Intent.LIST_SHORTAGES, 2.0)
            if (n <= 2) add(Intent.LIST_SHORTAGES, 2.0)
            if (recordV || deleteV || shortCue || hasW(Lexicon.BOUGHT_VERBS) || materialPresent) add(Intent.LIST_SHORTAGES, -3.0)
        }

        // ---- الأسعار والمواد
        if (priceW && materialOrFree) add(Intent.QUERY_PRICE, 3.5)
        if (priceW) add(Intent.QUERY_PRICE, 1.0)
        if (priceW && qWord) add(Intent.QUERY_PRICE, 1.5)
        if (hasW(setOf("بكم", "بقديش", "بكام", "يسوي")) && materialOrFree) add(Intent.QUERY_PRICE, 4.5)
        if (amountAny && hasW(Lexicon.PRICE_SET_CUES)) add(Intent.QUERY_PRICE, -3.0)
        if (priceW && amountAny && !qWord) add(Intent.QUERY_PRICE, -2.0)

        if (priceW) {
            add(Intent.SET_PRICE, 1.5)
            if (amountAny) add(Intent.SET_PRICE, 1.5)
            if (hasW(Lexicon.PRICE_SET_CUES)) add(Intent.SET_PRICE, 3.0)
            if (materialOrFree) add(Intent.SET_PRICE, 1.0)
            if (amountAny && !qWord) add(Intent.SET_PRICE, 2.0)
        }
        if (hasW(setOf("=")) && materialOrFree && amountAny) add(Intent.SET_PRICE, 4.0)

        if (hasW(Lexicon.MATERIAL_WORDS) && hasW(Lexicon.NEW_WORDS)) add(Intent.ADD_MATERIAL, 4.5)
        if (hasW(Lexicon.MATERIAL_WORDS) && recordV) add(Intent.ADD_MATERIAL, 2.5)
        if (importantW && hasW(Lexicon.MATERIAL_WORDS) && !hasW(Lexicon.NEW_WORDS)) add(Intent.ADD_MATERIAL, -3.0)
        if (hasW(Lexicon.NEW_WORDS) && recordV && materialOrFree) add(Intent.ADD_MATERIAL, 2.0)
        // "ضيف مادة X" أو "ضيف X للقائمة الثابتة": إضافة للقائمة الثابتة لا للنواقص.
        val catalogCue = hasW(setOf("ثابته", "كتالوج"))
        if (catalogCue && (recordV || adderV)) { add(Intent.ADD_MATERIAL, 5.0); add(Intent.ADD_SHORTAGE, -5.0) }
        if (hasW(Lexicon.MATERIAL_WORDS) && (recordV || adderV) && !shortageW && !importantW) add(Intent.ADD_MATERIAL, 3.0)

        if (hasW(Lexicon.SEARCH_VERBS)) {
            add(Intent.SEARCH_MATERIAL, 4.0)
            if (materialOrFree) add(Intent.SEARCH_MATERIAL, 1.0)
        }
        if ((qWord || hasW(setOf("في"))) && hasW(Lexicon.AVAILABLE) && materialOrFree) add(Intent.SEARCH_MATERIAL, 4.5)

        if (hasW(Lexicon.FINDER_WORDS) && hasW(Lexicon.MISSING_WORDS)) add(Intent.RUN_MISSING_FINDER, 7.0)
        if (hasW(setOf("ايجاد", "اكتشاف")) && hasW(setOf("مواد"))) add(Intent.RUN_MISSING_FINDER, 4.0)

        // ---- عام
        if (hasW(setOf("ملخص", "تقرير", "خلاصه", "احصائيات", "احصائيه", "جرد", "حصاد", "تلخيص"))) {
            add(Intent.SUMMARY, 5.0)
            if (DateParser.detectPeriod(norm) != null) add(Intent.SUMMARY, 1.0)
        }
        if (hasW(setOf("صار", "صاير")) && qWord && !priceW) add(Intent.SUMMARY, 4.5)

        if (openV) {
            add(Intent.OPEN, 4.5)
            val screenWord = norm.any { v -> Lexicon.variantsOf(v).any { it in Lexicon.SCREEN_WORDS } }
            if (screenWord || personPresent || materialPresent) add(Intent.OPEN, 1.5)
        }

        if (hasW(Lexicon.UNDO_WORDS)) {
            add(Intent.UNDO, 5.0)
            if (n <= 3) add(Intent.UNDO, 2.0)
        }
        if (hasW(Lexicon.REPEAT_WORDS) || seq(setOf("مره"), setOf("ثانيه")) || (hasW(setOf("نفس")) && hasW(setOf("شي", "الشي", "شيء")))) {
            add(Intent.REPEAT, 5.0)
        }
        if (qWord && hasW(Lexicon.HELP_VERBS)) add(Intent.HELP, 6.0)
        if (hasW(Lexicon.HELP_WORDS)) add(Intent.HELP, 5.0)
        if (n <= 4 && hasW(Lexicon.GREETINGS)) add(Intent.GREETING, 4.5)
        if (n <= 4 && hasW(Lexicon.THANKS)) add(Intent.THANKS, 4.5)

        return s.entries.map { Scored(it.key, it.value) }.sortedByDescending { it.score }
    }

    // ------------------------------------------------------------------ بناء الأمر

    private fun parseSegment(
        toks: List<Tok>,
        snapshot: EngineSnapshot,
        idx: Indexes,
        ctx: ConversationContext,
        hint: Intent?,
        force: Intent?,
        base: Command?
    ): Command {
        val rawText = toks.joinToString(" ") { it.raw }
        ManagementParser.parse(toks, rawText, snapshot, ctx, force)?.let { return validate(it, snapshot) }
        val an = analyze(toks, snapshot, idx)
        val scored = scoreIntents(an, ctx, hint)
        val best = scored.firstOrNull()
        var intent: Intent
        var score: Double
        if (force != null) {
            intent = force; score = 10.0
        } else if (best == null || best.score < MIN_SCORE) {
            return Command(Intent.UNKNOWN, best?.score ?: 0.0, rawText, understood = "")
        } else {
            intent = best.intent; score = best.score
            val second = scored.getOrNull(1)
            if (second != null && second.score >= MIN_SCORE + 0.5 && second.score >= best.score * 0.85 &&
                best.score - second.score < 1.0 && best.score < 9.0
            ) {
                val options = listOf(best.intent, second.intent).map { AmbiguityOption(intentLabel(it), it.name) }
                return Command(
                    Intent.UNKNOWN, best.score, rawText,
                    ambiguity = Ambiguity(AmbiguityKind.INTENT, "تقصد: ${options[0].label} أم ${options[1].label}؟", options),
                    prompt = "تقصد: ${options[0].label} أم ${options[1].label}؟"
                )
            }
        }
        if (intent == Intent.REPEAT) {
            val last = ctx.lastCommand ?: return Command(Intent.REPEAT, score, rawText, missing = emptyList(), prompt = "ما في شي أكرره بعد.")
            if (last.intent in ManagementParser.INTENTS) return validate(last.copy(rawText = rawText, score = score), snapshot)
            intent = last.intent
            return build(intent, score, rawText, an, snapshot, ctx, last)
        }
        return build(intent, score, rawText, an, snapshot, ctx, base)
    }

    private fun intentLabel(i: Intent): String = when (i) {
        Intent.ADD_DEBT -> "تسجيل دين"
        Intent.PAY_DEBT -> "تسديد دين"
        Intent.REDUCE_DEBT -> "تخفيض دين"
        Intent.DELETE_DEBT -> "حذف دين"
        Intent.QUERY_BALANCE -> "السؤال عن رصيد"
        Intent.ADD_SHORTAGE -> "إضافة للنواقص"
        Intent.REMOVE_SHORTAGE -> "حذف من النواقص"
        Intent.LIST_SHORTAGES -> "عرض النواقص"
        Intent.QUERY_PRICE -> "السؤال عن سعر"
        Intent.SET_PRICE -> "تعديل السعر"
        Intent.ADD_MATERIAL -> "إضافة مادة جديدة"
        Intent.SEARCH_MATERIAL -> "البحث عن مادة"
        Intent.OPEN -> "فتح شاشة"
        Intent.ADD_PERSON -> "إضافة عميل"
        Intent.RENAME_PERSON -> "تغيير اسم عميل"
        Intent.DELETE_PERSON -> "حذف عميل"
        Intent.SET_PHONE -> "حفظ رقم هاتف"
        Intent.DELETE_MATERIAL -> "حذف مادة"
        Intent.RENAME_MATERIAL -> "تغيير اسم مادة"
        else -> i.name
    }

    private fun build(
        intent: Intent, score: Double, rawText: String, an: Analysis,
        snapshot: EngineSnapshot, ctx: ConversationContext, base: Command?
    ): Command {
        var cmd = Command(intent, score, rawText)
        val needsPerson = intent in setOf(Intent.ADD_DEBT, Intent.PAY_DEBT, Intent.REDUCE_DEBT, Intent.DELETE_DEBT, Intent.QUERY_BALANCE)
        val allowNewPerson = intent == Intent.ADD_DEBT

        if (needsPerson || intent == Intent.OPEN) {
            val p = resolvePerson(an, ctx, allowNewPerson, base?.person)
            cmd = cmd.copy(person = p)
        }
        val needsMaterial = intent in setOf(
            Intent.ADD_SHORTAGE, Intent.REMOVE_SHORTAGE, Intent.QUERY_PRICE, Intent.SET_PRICE,
            Intent.ADD_MATERIAL, Intent.SEARCH_MATERIAL
        ) || (intent == Intent.OPEN && cmd.person == null)
        if (needsMaterial) {
            val multi = intent == Intent.ADD_SHORTAGE || intent == Intent.REMOVE_SHORTAGE
            val noPrepOn = (an.norm.indices).none { isPrepOnAt(an.norm, it) }
            var items = materialItems(an, multi, ignorePersons = noPrepOn && (intent == Intent.ADD_SHORTAGE || intent == Intent.REMOVE_SHORTAGE || intent == Intent.ADD_MATERIAL))
            if (!multi && items.size > 1) items = listOf(items[0])
            if (items.isEmpty() && base != null) items = base.materials
            if (intent == Intent.OPEN && items.none { it.level != MatchLevel.NEW }) items = emptyList()
            cmd = cmd.copy(materials = items)
        }
        if (intent == Intent.ADD_SHORTAGE) {
            val imp = an.norm.any { v -> Lexicon.variantsOf(v).any { it in Lexicon.IMPORTANT_WORDS } }
            cmd = cmd.copy(important = imp || base?.important == true)
        }

        val amountNeeded = intent in setOf(Intent.ADD_DEBT, Intent.REDUCE_DEBT, Intent.SET_PRICE, Intent.PAY_DEBT, Intent.DELETE_DEBT, Intent.ADD_MATERIAL)
        if (amountNeeded) {
            val pool = if (intent == Intent.SET_PRICE) an.money else an.amountNums.ifEmpty { an.money }
            val value = when {
                pool.isEmpty() -> null
                intent == Intent.SET_PRICE -> pool.last().value
                else -> pool.first().value
            }
            cmd = cmd.copy(amount = value ?: base?.takeIf { it.intent == intent }?.amount)
        }
        if (intent == Intent.PAY_DEBT) {
            val fullWord = an.norm.any { v -> Lexicon.variantsOf(v).any { it in Lexicon.FULL_WORDS } }
            cmd = cmd.copy(full = fullWord || cmd.amount == null)
            if (fullWord) cmd = cmd.copy(amount = null)
        }
        if (intent == Intent.QUERY_TOP_DEBTORS) {
            val lim = an.numbers.firstOrNull { it.value in 1.0..100.0 }?.value?.toInt() ?: 5
            cmd = cmd.copy(limit = lim)
        }
        if (intent == Intent.SUMMARY) {
            cmd = cmd.copy(period = DateParser.detectPeriod(an.norm) ?: Period.TODAY)
        }
        if (intent == Intent.OPEN) {
            var screen: Screen? = null
            for (t in an.norm) for (v in Lexicon.variantsOf(t)) { Lexicon.SCREEN_WORDS[v]?.let { screen = it } }
            cmd = cmd.copy(screen = screen)
        }
        if (intent == Intent.ADD_DEBT || intent == Intent.PAY_DEBT || intent == Intent.QUERY_STALE_DEBTORS) {
            cmd = cmd.copy(date = an.datePast?.ref)
        }
        if (intent == Intent.QUERY_STALE_DEBTORS) cmd = cmd.copy(person = null)
        if (intent == Intent.CALCULATE) {
            val c = Calculator.evaluate(an.norm, an.numbers, true)
            if (c != null) cmd = cmd.copy(expression = c.expression, result = c.value)
        }
        if (intent == Intent.ADD_NOTE || intent == Intent.REMINDER) {
            val consumed = HashSet<Int>()
            var skipDone = false
            val sb = ArrayList<String>()
            val dp = if (intent == Intent.REMINDER) an.dateFuture else null
            if (dp != null) consumed.addAll(dp.consumed)
            for (i in an.norm.indices) {
                val t = an.norm[i]
                if (i in consumed) continue
                val v = Lexicon.variantsOf(t)
                val isTrigger = v.any { it in Lexicon.REMIND_VERBS || it in Lexicon.NOTE_WORDS || it in Lexicon.RECORD_VERBS && !skipDone }
                if (isTrigger && sb.isEmpty()) { if (v.any { it in Lexicon.NOTE_WORDS || it in Lexicon.REMIND_VERBS }) skipDone = true; continue }
                if (sb.isEmpty() && (t == "ب" || t == "بـ" || t == "ان" || t == "انو" || t == "بأن" || t == "هي" || t == "ل")) continue
                sb.add(an.raw[i])
            }
            val text = sb.joinToString(" ").trim().trim(',').trim()
            cmd = cmd.copy(text = text.ifBlank { base?.text }, date = dp?.ref)
        }
        return validate(cmd, snapshot)
    }

    // ------------------------------------------------------------------ الأشخاص

    private fun cleanNewName(span: List<String>): String {
        val words = span.toMutableList()
        if (words.isNotEmpty()) {
            val f = words[0]
            if (f.length >= 5 && f[0] == 'ل' && ArabicText.canonicalToken(f).length >= 5) words[0] = f.substring(1)
            else if (f.length == 4 && f[0] == 'ل' && ArabicText.canonicalToken(f.substring(1)) in setOf("ابو", "ام")) words[0] = f.substring(1)
        }
        return words.joinToString(" ")
    }

    private fun resolvePerson(an: Analysis, ctx: ConversationContext, allowNew: Boolean, fallback: PersonRef?): PersonRef? {
        val m = an.personMatches.maxWithOrNull(compareBy<SpanMatch> { it.best.score }.thenByDescending { -it.start })
        if (m != null) {
            var cands = m.all.map { Candidate(it.name, it.id, it.score) }
            val level = m.level
            val ambiguous = EntityMatcher.isAmbiguous(m)
            if (!ambiguous && level != MatchLevel.MEDIUM) cands = emptyList()
            return PersonRef(m.rawSpan, m.best.id, m.best.name, m.best.score, level, cands, m.viaAlias)
        }
        if (an.pronoun && ctx.lastPerson != null) {
            val lp = ctx.lastPerson
            return PersonRef(lp.rawSpan, lp.id, lp.name, 1.0, MatchLevel.EXACT)
        }
        val run = an.free.firstOrNull()
        if (run != null) {
            val span = an.raw.subList(run.first, run.last + 1)
            val name = cleanNewName(span)
            return PersonRef(span.joinToString(" "), null, name, 0.0, if (allowNew) MatchLevel.NEW else MatchLevel.NONE)
        }
        return fallback
    }

    // ------------------------------------------------------------------ المواد

    private fun isQtyTok(i: Int, an: Analysis): Boolean {
        val t = an.norm[i]
        if (an.numbers.any { i >= it.start && i < it.end }) return true
        return isUnitish(t)
    }

    private fun readGroup(an: Analysis, used: BooleanArray, from: Int, step: Int): List<Int> {
        val out = ArrayList<Int>()
        var k = from
        while (k in an.norm.indices && !used[k] && isQtyTok(k, an)) {
            out.add(k); k += step
        }
        if (step < 0) out.reverse()
        return out
    }

    private fun parseQty(an: Analysis, group: List<Int>): Qty? {
        if (group.isEmpty()) return null
        var value: Double? = null
        var unit: String? = null
        var pack: String? = null
        var frac: String? = null
        var gramsValue: Double? = null
        var i = 0
        while (i < group.size) {
            val k = group[i]
            val t = an.norm[k]
            val nm = an.numbers.firstOrNull { it.start == k }
            when {
                nm != null -> { value = nm.value; while (i < group.size && group[i] < nm.end) i++; continue }
                t in Lexicon.FRACTIONS -> frac = if (t == "ربع") "ربع" else "نص"
                t in Lexicon.UNIT_WORDS -> {
                    val u = Lexicon.UNIT_WORDS.getValue(t)
                    unit = if (frac != null) "$frac $u" else u
                    if (frac != null) value = 1.0
                    frac = null
                }
                t in Lexicon.DUAL_PACK -> {
                    val d = Lexicon.DUAL_PACK.getValue(t)
                    value = d.first
                    if (d.second == "كيلو") unit = "كيلو" else pack = d.second
                }
                t in Lexicon.PACK_WORDS -> pack = Lexicon.PACK_WORDS.getValue(t)
                t in Lexicon.GRAM_WORDS -> gramsValue = value
            }
            i++
        }
        if (gramsValue != null) {
            when (gramsValue.toInt()) {
                250 -> { unit = "ربع كيلو"; value = 1.0 }
                500 -> { unit = "نص كيلو"; value = 1.0 }
                1000 -> { unit = "كيلو"; value = 1.0 }
            }
        }
        if (value == null && (unit != null || pack != null)) value = 1.0
        if (value == null && unit == null && pack == null) return null
        return Qty(value, unit, pack)
    }

    private class Span(val start: Int, val end: Int, val name: String, val score: Double, val level: MatchLevel, val cands: List<Candidate>, val alias: Boolean, val raw: String)

    private fun materialItems(an: Analysis, allowFree: Boolean, ignorePersons: Boolean = false): List<MaterialRef> {
        val spans = ArrayList<Span>()
        for (m in an.materialMatches) {
            val amb = EntityMatcher.isAmbiguous(m)
            val level = m.level
            val cands = if (amb || level == MatchLevel.MEDIUM) m.all.map { Candidate(it.name, it.id, it.score) } else emptyList()
            spans.add(Span(m.start, m.end, m.best.name, m.best.score, level, cands, m.viaAlias, m.rawSpan))
        }
        if (allowFree || spans.isEmpty()) {
            for (run in (if (ignorePersons) an.freeNoPerson else an.free)) {
                // نقسم على واو العطف الملتصقة حتى تنفصل مواد القائمة ("سكر وملح وبهارات").
                var s = run.first
                for (k in run.first..run.last) {
                    val t = an.norm[k]
                    val startsWaw = k > run.first && t.length > 3 && t[0] == 'و'
                    if (startsWaw) {
                        spans.add(freeSpan(an, s, k))
                        s = k
                    }
                }
                spans.add(freeSpan(an, s, run.last + 1))
            }
        }
        spans.sortBy { it.start }
        val used = BooleanArray(an.norm.size)
        val items = ArrayList<MaterialRef>()
        for (sp in spans) {
            for (k in sp.start until sp.end) used[k] = true
        }
        val claimed = BooleanArray(an.norm.size)
        for (sp in spans) {
            val back = readGroup(an, claimed.also { for (k in sp.start until sp.end) it[k] = true }, sp.start - 1, -1)
            var group = back
            if (group.isEmpty() || parseQty(an, group) == null) group = readGroup(an, claimed, sp.end, 1)
            for (k in group) claimed[k] = true
            val q = parseQty(an, group)
            items.add(
                MaterialRef(
                    rawSpan = sp.raw, name = sp.name, score = sp.score, level = sp.level,
                    quantity = q?.value, unit = q?.unit, pack = q?.pack, candidates = sp.cands, viaAlias = sp.alias
                )
            )
        }
        return items
    }

    private fun freeSpan(an: Analysis, start: Int, end: Int): Span {
        val words = an.raw.subList(start, end).toMutableList()
        if (words.isNotEmpty() && an.norm[start].length > 3 && an.norm[start][0] == 'و') {
            words[0] = words[0].substring(1)
        }
        val name = words.joinToString(" ")
        return Span(start, end, name, 0.0, MatchLevel.NEW, emptyList(), false, name)
    }

    // ------------------------------------------------------------------ التحقق والمخاطر والوصف

    private fun validate(cmdIn: Command, snapshot: EngineSnapshot): Command {
        var cmd = cmdIn
        val missing = ArrayList<Slot>()
        var ambiguity: Ambiguity? = null
        var prompt: String? = null
        val reasons = ArrayList<String>()
        var risky = false
        val cur = snapshot.currency
        val intent = cmd.intent

        val person = cmd.person
        val needsPerson = intent in setOf(
            Intent.ADD_DEBT, Intent.PAY_DEBT, Intent.REDUCE_DEBT, Intent.DELETE_DEBT, Intent.QUERY_BALANCE,
            Intent.ADD_PERSON, Intent.RENAME_PERSON, Intent.DELETE_PERSON, Intent.SET_PHONE
        )
        if (needsPerson) {
            if (person == null) {
                missing.add(Slot.PERSON)
                prompt = when (intent) {
                    Intent.ADD_DEBT -> "على مين بدك أسجّل الدين؟"
                    Intent.ADD_PERSON -> "شو اسم العميل الجديد؟"
                    else -> "عن أي عميل تقصد؟"
                }
            } else if (person.level == MatchLevel.NONE) {
                missing.add(Slot.PERSON); prompt = "ما لقيت \"${person.rawSpan}\" بين العملاء. اكتب الاسم مرة ثانية."
            } else if (person.candidates.size >= 2 && person.candidates[1].name != person.candidates[0].name &&
                person.candidates[1].score >= person.candidates[0].score - 0.03
            ) {
                val opts = person.candidates.take(4).map { AmbiguityOption(it.name, "${it.id ?: ""}|${it.name}") }
                prompt = "أي واحد تقصد؟"
                ambiguity = Ambiguity(AmbiguityKind.PERSON, "أي \"${person.rawSpan}\" تقصد؟", opts)
            } else if (person.level == MatchLevel.MEDIUM) {
                val opts = person.candidates.take(3).map { AmbiguityOption(it.name, "${it.id ?: ""}|${it.name}") }
                prompt = "تقصد ${person.name}؟"
                ambiguity = Ambiguity(AmbiguityKind.FUZZY_PERSON, "تقصد ${person.name}؟", opts)
            }
        }

        val needsMaterial = intent in setOf(
            Intent.ADD_SHORTAGE, Intent.REMOVE_SHORTAGE, Intent.QUERY_PRICE, Intent.SET_PRICE,
            Intent.ADD_MATERIAL, Intent.SEARCH_MATERIAL, Intent.DELETE_MATERIAL, Intent.RENAME_MATERIAL
        )
        if (needsMaterial) {
            if ((intent == Intent.DELETE_MATERIAL || intent == Intent.RENAME_MATERIAL) && cmd.materials.isNotEmpty()) {
                val first = cmd.materials[0]
                if (first.level == MatchLevel.NEW || first.level == MatchLevel.NONE) {
                    missing.add(Slot.MATERIAL)
                    if (prompt == null) prompt = "ما لقيت \"${first.rawSpan}\" بالقائمة. اكتب اسم المادة مرة ثانية."
                }
            }
            if (cmd.materials.isEmpty()) {
                missing.add(Slot.MATERIAL); if (prompt == null) prompt = "أي مادة؟"
            } else if (ambiguity == null) {
                for ((i, m) in cmd.materials.withIndex()) {
                    if (m.candidates.size >= 2 && m.candidates[1].name != m.candidates[0].name &&
                        m.candidates[1].score >= m.candidates[0].score - 0.03
                    ) {
                        val opts = m.candidates.take(4).map { AmbiguityOption(it.name, "$i|${it.name}") }
                        prompt = "أي مادة تقصد؟"
                        ambiguity = Ambiguity(AmbiguityKind.MATERIAL, "أي \"${m.rawSpan}\" تقصد؟", opts)
                        break
                    } else if (m.level == MatchLevel.MEDIUM) {
                        val opts = m.candidates.take(3).map { AmbiguityOption(it.name, "$i|${it.name}") }
                        prompt = "تقصد ${m.name}؟"
                        ambiguity = Ambiguity(AmbiguityKind.FUZZY_MATERIAL, "تقصد ${m.name}؟", opts)
                        break
                    }
                }
            }
        }

        val needsAmount = intent == Intent.ADD_DEBT || intent == Intent.REDUCE_DEBT || intent == Intent.SET_PRICE
        if (needsAmount && (cmd.amount == null || cmd.amount!! <= 0.0)) {
            missing.add(Slot.AMOUNT); if (prompt == null) prompt = if (intent == Intent.SET_PRICE) "كم السعر الجديد؟" else "كم المبلغ؟"
        }
        if ((intent == Intent.RENAME_PERSON || intent == Intent.RENAME_MATERIAL) && cmd.newName.isNullOrBlank()) {
            missing.add(Slot.TEXT); if (prompt == null) prompt = "شو الاسم الجديد؟"
        }
        if (intent == Intent.SET_PHONE && cmd.phone.isNullOrBlank()) {
            missing.add(Slot.TEXT); if (prompt == null) prompt = "شو رقم الهاتف؟"
        }
        if (intent == Intent.ADD_NOTE && cmd.text.isNullOrBlank()) { missing.add(Slot.TEXT); if (prompt == null) prompt = "شو نص الملاحظة؟" }
        if (intent == Intent.REMINDER) {
            if (cmd.text.isNullOrBlank()) { missing.add(Slot.TEXT); if (prompt == null) prompt = "شو بدك أذكّرك فيه؟" }
            if (cmd.date == null) { missing.add(Slot.DATE); if (prompt == null) prompt = "إمتى بدك التذكير؟" }
        }
        if (intent == Intent.OPEN && cmd.screen == null && cmd.person == null && cmd.materials.isEmpty()) {
            missing.add(Slot.TEXT); if (prompt == null) prompt = "شو بدك أفتح؟"
        }
        if (intent == Intent.CALCULATE && cmd.expression == null) { missing.add(Slot.TEXT); prompt = "ما فهمت العملية الحسابية." }

        // أمان
        val known = person?.let { p -> snapshot.persons.firstOrNull { it.id == p.id || ArabicText.normalize(it.name) == ArabicText.normalize(p.name) } }
        when (intent) {
            Intent.DELETE_DEBT, Intent.DELETE_LAST, Intent.REMOVE_SHORTAGE -> { risky = true; reasons.add("عملية حذف") }
            Intent.SET_PRICE -> { risky = true; reasons.add("تعديل سعر") }
            Intent.DELETE_PERSON -> {
                risky = true; reasons.add("حذف عميل مع كل ديونه")
                if (known != null && known.balance > 0.0001) reasons.add("عليه دين ${Calculator.format(known.balance)} $cur سيُحذف معه")
            }
            Intent.DELETE_MATERIAL -> { risky = true; reasons.add("حذف مادة من القائمة الثابتة") }
            else -> {}
        }
        if (person?.level == MatchLevel.NEW) reasons.add("عميل جديد سيُنشأ")
        val amt = cmd.amount
        if (amt != null && intent in setOf(Intent.ADD_DEBT, Intent.PAY_DEBT, Intent.REDUCE_DEBT)) {
            if (known != null && known.typicalAmount > 0.0 && amt > known.typicalAmount * 5.0) {
                risky = true; reasons.add("المبلغ أكبر بكثير من المعتاد لهذا الشخص (المعتاد ≈ ${Calculator.format(known.typicalAmount)} $cur)")
            } else if (amt >= 10_000_000.0) {
                risky = true; reasons.add("مبلغ كبير جداً")
            }
            if (known != null && intent != Intent.ADD_DEBT && amt > known.balance + 0.0001) {
                risky = true; reasons.add("المبلغ أكبر من رصيد ${known.name} (${Calculator.format(known.balance)} $cur)")
            }
        }
        if (intent == Intent.PAY_DEBT && known != null && known.balance <= 0.0001) reasons.add("رصيده صفر أصلاً")

        cmd = cmd.copy(
            missing = missing, ambiguity = ambiguity, prompt = prompt, confirmReasons = reasons, risky = risky
        )
        return cmd.copy(understood = describe(cmd, cur))
    }

    private fun describe(c: Command, cur: String): String {
        val name = c.person?.name ?: ""
        val amt = c.amount?.let { "${Calculator.format(it)} $cur" } ?: ""
        val mats = c.materials.joinToString("، ") { m ->
            val q = when {
                m.quantity == null -> ""
                m.unit != null && m.quantity == 1.0 -> " (${m.unit})"
                else -> " (${Calculator.format(m.quantity)}${m.unit?.let { " $it" } ?: ""}${m.pack?.let { " $it" } ?: ""})"
            }
            m.name + q
        }
        return when (c.intent) {
            Intent.ADD_DEBT -> "تسجيل دين ${amt.ifBlank { "..." }} على ${name.ifBlank { "..." }}" + if (c.person?.level == MatchLevel.NEW) " (عميل جديد)" else ""
            Intent.PAY_DEBT -> if (c.amount != null && !c.full) "تسديد $amt من دين $name" else "تسديد كامل لدين $name"
            Intent.REDUCE_DEBT -> "تخفيض دين $name بمقدار ${amt.ifBlank { "..." }}"
            Intent.DELETE_DEBT -> "حذف دين $name" + (if (c.amount != null) " بمبلغ $amt" else "")
            Intent.QUERY_BALANCE -> "السؤال عن رصيد $name"
            Intent.QUERY_TOTAL_DEBTS -> "السؤال عن مجموع الديون"
            Intent.QUERY_TOP_DEBTORS -> "أكثر ${c.limit ?: 5} مدينين"
            Intent.QUERY_STALE_DEBTORS -> "المدينون الذين لم يسددوا منذ مدة" + (c.date?.let { " (${it.label})" } ?: "")
            Intent.ADD_SHORTAGE -> "إضافة للنواقص: $mats" + if (c.important) " (هامة)" else ""
            Intent.REMOVE_SHORTAGE -> "حذف من النواقص: $mats"
            Intent.LIST_SHORTAGES -> "عرض قائمة النواقص"
            Intent.QUERY_PRICE -> "السؤال عن سعر $mats"
            Intent.SET_PRICE -> "تعديل سعر $mats إلى ${amt.ifBlank { "..." }}"
            Intent.ADD_MATERIAL -> "إضافة مادة جديدة: $mats"
            Intent.SEARCH_MATERIAL -> "البحث عن $mats"
            Intent.RUN_MISSING_FINDER -> "تشغيل إيجاد المواد الناقصة"
            Intent.SUMMARY -> "ملخص " + when (c.period) { Period.WEEK -> "الأسبوع"; Period.MONTH -> "الشهر"; else -> "اليوم" }
            Intent.OPEN -> when {
                c.person != null -> "فتح ملف $name"
                c.materials.isNotEmpty() -> "فتح المادة $mats"
                else -> "فتح " + screenLabel(c.screen)
            }
            Intent.ADD_NOTE -> "إضافة ملاحظة: ${c.text ?: "..."}"
            Intent.REMINDER -> "تذكير ${c.date?.label ?: ""}: ${c.text ?: "..."}".replace("  ", " ")
            Intent.CALCULATE -> "حساب ${c.expression ?: ""}"
            Intent.UNDO -> "التراجع عن آخر عملية"
            Intent.DELETE_LAST -> "حذف آخر عملية"
            Intent.REPEAT -> "تكرار آخر أمر"
            Intent.HELP -> "عرض ما أستطيع فعله"
            Intent.GREETING -> "تحية"
            Intent.THANKS -> "شكر"
            Intent.CONFIRM -> "تأكيد"
            Intent.CANCEL -> "إلغاء"
            Intent.ADD_PERSON -> "إضافة عميل جديد: ${name.ifBlank { "..." }}" + (c.phone?.let { " ($it)" } ?: "")
            Intent.RENAME_PERSON -> "تغيير اسم العميل ${name.ifBlank { "..." }} إلى ${c.newName ?: "..."}"
            Intent.DELETE_PERSON -> "حذف العميل ${name.ifBlank { "..." }} مع كل ديونه"
            Intent.SET_PHONE -> "حفظ رقم ${name.ifBlank { "..." }}: ${c.phone ?: "..."}"
            Intent.DELETE_MATERIAL -> "حذف مادة من القائمة الثابتة: ${mats.ifBlank { "..." }}"
            Intent.RENAME_MATERIAL -> "تغيير اسم المادة ${mats.ifBlank { "..." }} إلى ${c.newName ?: "..."}"
            Intent.UNKNOWN -> ""
        }
    }

    private fun screenLabel(s: Screen?): String = when (s) {
        Screen.DEBTS -> "شاشة الديون"
        Screen.MATERIALS -> "شاشة المواد"
        Screen.NOTES -> "الملاحظات"
        Screen.SETTINGS -> "الإعدادات"
        Screen.ACTIVITY -> "سجل النشاط"
        Screen.MISSING_FINDER -> "إيجاد المواد الناقصة"
        Screen.CATALOG -> "كتالوج المواد"
        Screen.HOME -> "الرئيسية"
        Screen.ASSISTANT -> "المحادثة"
        null -> "الشاشة"
    }

    // ------------------------------------------------------------------ أسئلة المتابعة

    private fun continuePending(pend: Command, toks: List<Tok>, snapshot: EngineSnapshot, idx: Indexes, ctx: ConversationContext): Command? {
        val amb = pend.ambiguity
        if (amb != null) {
            val spanTokens = EntityMatcher.tokensOf(toks.joinToString(" ") { it.raw })
            var best: AmbiguityOption? = null
            var bestScore = 0.0
            for (o in amb.options) {
                val s = EntityMatcher.scoreForm(spanTokens, EntityMatcher.tokensOf(o.label))
                if (s > bestScore) { bestScore = s; best = o }
            }
            if (best != null && bestScore >= EntityMatcher.MEDIUM) return resolveChoice(pend, best, snapshot, ctx)
            return null
        }
        if (pend.missing.isEmpty()) return null
        val an = analyze(toks, snapshot, idx)
        var cmd = pend
        var filled = false
        val cleanBase = pend.copy(missing = emptyList(), prompt = null)
        for (slot in pend.missing) {
            when (slot) {
                Slot.AMOUNT -> {
                    val n = (an.amountNums.ifEmpty { an.money }).firstOrNull()
                    if (n != null) { cmd = cmd.copy(amount = n.value); filled = true }
                }
                Slot.PERSON -> {
                    val p = resolvePerson(an, ctx, pend.intent == Intent.ADD_DEBT || pend.intent == Intent.ADD_PERSON, null)
                    if (p != null) { cmd = cmd.copy(person = p); filled = true }
                }
                Slot.MATERIAL -> {
                    val items = materialItems(an, true)
                    if (items.isNotEmpty()) { cmd = cmd.copy(materials = items); filled = true }
                }
                Slot.TEXT -> {
                    val t = toks.joinToString(" ") { it.raw }.trim()
                    if (pend.intent in ManagementParser.INTENTS) {
                        val upd = ManagementParser.applyText(cmd, t)
                        if (upd != null) { cmd = upd; filled = true }
                    } else if (t.isNotEmpty()) { cmd = cmd.copy(text = t); filled = true }
                }
                Slot.DATE -> {
                    val d = an.dateFuture
                    if (d != null) { cmd = cmd.copy(date = d.ref); filled = true }
                }
            }
        }
        if (!filled) return null
        return validate(cmd.copy(missing = cleanBase.missing), snapshot)
    }

    // ------------------------------------------------------------------ السياق والإخراج

    private fun updateContext(ctx: ConversationContext, cmd: Command): ConversationContext {
        var c = ctx
        if (cmd.person != null && cmd.person.level != MatchLevel.NONE) c = c.copy(lastPerson = cmd.person)
        if (cmd.materials.isNotEmpty()) c = c.copy(lastMaterial = cmd.materials.last().name)
        if (cmd.amount != null) c = c.copy(lastAmount = cmd.amount)
        if (cmd.isReady && !cmd.isReadOnly) c = c.copy(lastCommand = cmd)
        return c
    }

    private fun output(commands: List<Command>, ctx: ConversationContext, snapshot: EngineSnapshot): EngineOutput {
        var c = ctx
        val needing = commands.firstOrNull { !it.isReady && it.intent != Intent.UNKNOWN || it.ambiguity != null }
        c = c.copy(pending = needing)
        val lines = commands.filter { it.intent != Intent.UNKNOWN && it.understood.isNotBlank() }.map { it.understood }
        val understood = if (lines.isEmpty()) "" else "فهمت: " + lines.joinToString(" ثم ")
        return EngineOutput(commands, c, understood)
    }

    companion object {
        const val MIN_SCORE = 3.0
    }
}
