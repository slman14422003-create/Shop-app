package com.shopmanager.app.assistant.engine

/**
 * أوامر إدارة العملاء والقائمة الثابتة: إضافة عميل، تغيير اسم عميل/مادة، حذف عميل/مادة، حفظ رقم هاتف.
 * يُستدعى من AssistantEngine.parseSegment قبل التسجيل العام للنوايا، ويرجع null إن لم تنطبق القواعد.
 */
internal object ManagementParser {

    val INTENTS: Set<Intent> = setOf(
        Intent.ADD_PERSON, Intent.RENAME_PERSON, Intent.DELETE_PERSON, Intent.SET_PHONE,
        Intent.DELETE_MATERIAL, Intent.RENAME_MATERIAL
    )

    private val CHANGE_VERBS = setOf(
        "غير", "غيري", "غيرلي", "عدل", "عدلي", "عدللي", "بدل", "بدلي", "صحح", "صححلي", "سمي", "سميه", "سميها",
        "تغيير", "تعديل", "تبديل"
    )
    private val NAME_WORDS = setOf("اسم", "الاسم", "باسم", "اسمه", "اسمها")
    private val SEPARATORS = setOf("الي", "الى", "ل", "ليصير", "ب", "لاسم")
    private val PERSON_WORDS = setOf("عميل", "زبون", "زبونه", "عميله", "شخص")
    private val CATALOG_WORDS = setOf("كتالوج", "ثابته")
    private val PHONE_WORDS = setOf("رقم", "هاتف", "جوال", "موبايل", "تلفون", "تليفون", "نمره", "رقمه", "رقمها")
    private val NOISE = setOf(
        "من", "عن", "في", "بدي", "بدنا", "لازم", "ممكن", "رجاء", "يا", "هاد", "هذا", "هاي", "هيدا",
        "الي", "الى", "ل", "ب", "هو", "هي", "خلي", "خليه", "صير", "جديد", "جديده", "عند", "لو"
    )

    // ------------------------------------------------------------------ أدوات

    private fun matches(t: Tok, set: Set<String>): Boolean =
        Lexicon.variantsOf(t.norm).any { it in set }

    private fun has(toks: List<Tok>, set: Set<String>): Boolean = toks.any { matches(it, set) }

    private fun inFirst(toks: List<Tok>, set: Set<String>, n: Int = 3): Boolean =
        toks.take(n).any { matches(it, set) }

    private fun isDigits(s: String): Boolean = s.isNotEmpty() && s.all { it in '0'..'9' }

    /** يستخرج رقم هاتف (7–15 رقماً). بلا كلمة "رقم/هاتف" يُقبل فقط ما يبدأ بصفر أو +. */
    private fun extractPhone(toks: List<Tok>, phoneWord: Boolean): Pair<String, IntRange>? {
        var i = 0
        while (i < toks.size) {
            val plus = toks[i].norm == "+" && i + 1 < toks.size && isDigits(toks[i + 1].norm)
            val start = if (plus) i + 1 else i
            if (start < toks.size && isDigits(toks[start].norm)) {
                var j = start
                val sb = StringBuilder()
                if (plus) sb.append('+')
                while (j < toks.size) {
                    val u = toks[j].norm
                    if (isDigits(u)) { sb.append(u); j++ }
                    else if (u == "-" && j + 1 < toks.size && isDigits(toks[j + 1].norm)) j++
                    else break
                }
                val digitCount = sb.count { it in '0'..'9' }
                val explicit = plus || toks[start].norm.startsWith("0")
                if (digitCount in 7..15 && (phoneWord || explicit)) return sb.toString() to (i until j)
                i = j
            } else i++
        }
        return null
    }

    /** نص حر (جواب سؤال متابعة) إلى رقم هاتف. */
    private fun phoneFromText(text: String): String? {
        val sb = StringBuilder()
        for (ch in text) {
            when (ch) {
                in '0'..'9' -> sb.append(ch)
                in '٠'..'٩' -> sb.append('0' + (ch - '٠'))
                in '۰'..'۹' -> sb.append('0' + (ch - '۰'))
                else -> {}
            }
        }
        return sb.toString().takeIf { it.length in 7..15 }
    }

    /** يملأ حقل نص ناقص (اسم جديد أو رقم) من جواب المستخدم. null = لا ينطبق. */
    fun applyText(cmd: Command, text: String): Command? = when (cmd.intent) {
        Intent.RENAME_PERSON, Intent.RENAME_MATERIAL ->
            text.trim().takeIf { it.isNotEmpty() }?.let { cmd.copy(newName = it) }
        Intent.SET_PHONE -> phoneFromText(text)?.let { cmd.copy(phone = it) }
        else -> null
    }

    private fun span(toks: List<Tok>, skip: Set<Int>, vararg removal: Set<String>): List<Tok> {
        val out = ArrayList<Tok>()
        for ((i, t) in toks.withIndex()) {
            if (i in skip) continue
            if (removal.any { matches(t, it) }) continue
            out.add(t)
        }
        return out
    }

    private fun personRef(span: List<Tok>, index: List<IndexEntry>): PersonRef? {
        if (span.isEmpty()) return null
        val raw = span.joinToString(" ") { it.raw }
        val (c, alias) = EntityMatcher.candidatesFor(span.map { it.norm }, index)
        if (c.isEmpty()) return PersonRef(raw, null, raw, 0.0, MatchLevel.NONE)
        val best = c[0]
        val level = EntityMatcher.levelOf(best.score)
        val ambiguous = c.size >= 2 && c[1].name != best.name && c[1].score >= best.score - 0.03
        val cands = if (ambiguous || level == MatchLevel.MEDIUM) c else emptyList()
        return PersonRef(raw, best.id, best.name, best.score, level, cands, alias)
    }

    private fun materialRef(span: List<Tok>, index: List<IndexEntry>): MaterialRef? {
        if (span.isEmpty()) return null
        val raw = span.joinToString(" ") { it.raw }
        val (c, alias) = EntityMatcher.candidatesFor(span.map { it.norm }, index)
        if (c.isEmpty()) return MaterialRef(rawSpan = raw, name = raw, score = 0.0, level = MatchLevel.NEW)
        val best = c[0]
        val level = EntityMatcher.levelOf(best.score)
        val ambiguous = c.size >= 2 && c[1].name != best.name && c[1].score >= best.score - 0.03
        val cands = if (ambiguous || level == MatchLevel.MEDIUM) c else emptyList()
        return MaterialRef(
            rawSpan = raw, name = best.name, score = best.score, level = level,
            candidates = cands, viaAlias = alias
        )
    }

    private fun intentAmbiguity(rawText: String, a: Intent, labelA: String, b: Intent, labelB: String): Command {
        val q = "تقصد: $labelA أم $labelB؟"
        val options = listOf(AmbiguityOption(labelA, a.name), AmbiguityOption(labelB, b.name))
        return Command(
            Intent.UNKNOWN, 6.0, rawText,
            ambiguity = Ambiguity(AmbiguityKind.INTENT, q, options), prompt = q
        )
    }

    // ------------------------------------------------------------------ نقطة الدخول

    fun parse(
        toks: List<Tok>,
        rawText: String,
        snapshot: EngineSnapshot,
        ctx: ConversationContext,
        force: Intent?
    ): Command? {
        if (toks.isEmpty()) return null
        if (force != null && force !in INTENTS) return null
        fun want(i: Intent) = force == null || force == i

        val persons by lazy { EntityMatcher.buildIndex(snapshot.persons.map { it.name to it.id }, snapshot.aliases) }
        val materials by lazy { EntityMatcher.buildIndex(snapshot.materials.map { it.name to null }, snapshot.aliases) }

        val deleteV = inFirst(toks, Lexicon.DELETE_VERBS)
        val changeV = inFirst(toks, CHANGE_VERBS)
        val addV = inFirst(toks, Lexicon.RECORD_VERBS)
        val personW = has(toks, PERSON_WORDS)
        val materialW = has(toks, Lexicon.MATERIAL_WORDS)
        val catalogW = has(toks, CATALOG_WORDS)
        val debtW = has(toks, Lexicon.DEBT_WORDS)
        val shortageW = has(toks, Lexicon.SHORTAGE_WORDS)
        val nameW = has(toks, NAME_WORDS)
        val phoneW = has(toks, PHONE_WORDS)

        // ---- تغيير اسم عميل أو مادة
        if ((want(Intent.RENAME_PERSON) || want(Intent.RENAME_MATERIAL)) && changeV && nameW) {
            val nameIdx = toks.indexOfFirst { matches(it, NAME_WORDS) }
            val sepIdx = toks.indices.firstOrNull { it > nameIdx && toks[it].norm in SEPARATORS }
            val leftEnd = sepIdx ?: toks.size
            val left = span(
                toks.subList(nameIdx + 1, leftEnd), emptySet(),
                PERSON_WORDS, Lexicon.MATERIAL_WORDS, CATALOG_WORDS, NOISE
            )
            val newName = if (sepIdx == null) null else {
                val right = toks.subList(sepIdx + 1, toks.size).dropWhile { matches(it, NAME_WORDS) }
                right.joinToString(" ") { it.raw }.trim().takeIf { it.isNotEmpty() }
            }
            val p = personRef(left, persons)
            val m = materialRef(left, materials)
            val pBest = p?.score ?: 0.0
            val mBest = if (m != null && m.level != MatchLevel.NEW) m.score else 0.0
            val intent: Intent = when {
                force != null -> force
                personW && !materialW && !catalogW -> Intent.RENAME_PERSON
                (materialW || catalogW) && !personW -> Intent.RENAME_MATERIAL
                pBest >= EntityMatcher.MEDIUM && pBest > mBest + 0.03 -> Intent.RENAME_PERSON
                mBest >= EntityMatcher.MEDIUM && mBest > pBest + 0.03 -> Intent.RENAME_MATERIAL
                else -> return intentAmbiguity(rawText, Intent.RENAME_PERSON, "تغيير اسم عميل", Intent.RENAME_MATERIAL, "تغيير اسم مادة")
            }
            return if (intent == Intent.RENAME_PERSON) {
                Command(intent, 9.0, rawText, person = p, newName = newName)
            } else {
                Command(intent, 9.0, rawText, materials = listOfNotNull(m), newName = newName)
            }
        }

        // ---- حذف عميل (مع ديونه). "احذف دين سامر" يبقى لحذف الدين.
        if (want(Intent.DELETE_PERSON) && deleteV && personW && !debtW && !materialW) {
            val left = span(toks, emptySet(), Lexicon.DELETE_VERBS, PERSON_WORDS, NOISE)
            return Command(Intent.DELETE_PERSON, 9.0, rawText, person = personRef(left, persons))
        }

        // ---- حذف مادة من القائمة الثابتة. "احذف مادة سكر من النواقص" يبقى لحذف النواقص.
        if (want(Intent.DELETE_MATERIAL) && deleteV && (materialW || catalogW) &&
            !personW && !debtW && (catalogW || !shortageW)
        ) {
            val left = span(
                toks, emptySet(),
                Lexicon.DELETE_VERBS, Lexicon.MATERIAL_WORDS, CATALOG_WORDS, Lexicon.SHORTAGE_WORDS, NOISE
            )
            return Command(Intent.DELETE_MATERIAL, 9.0, rawText, materials = listOfNotNull(materialRef(left, materials)))
        }

        // ---- حفظ رقم هاتف لعميل موجود
        val phone = extractPhone(toks, phoneW)
        if (want(Intent.SET_PHONE) && phone != null && phoneW && !(addV && personW)) {
            val skip = phone.second.toSet()
            val left = span(toks, skip, Lexicon.RECORD_VERBS, CHANGE_VERBS, PHONE_WORDS, PERSON_WORDS, NOISE)
            var p = personRef(left, persons)
            if (p == null) {
                val lp = ctx.lastPerson
                if (lp != null && lp.id != null) p = PersonRef(lp.rawSpan, lp.id, lp.name, 1.0, MatchLevel.EXACT)
            }
            return Command(Intent.SET_PHONE, 9.0, rawText, person = p, phone = phone.first)
        }

        // ---- إضافة عميل جديد بلا دين. وجود مبلغ يعني تسجيل دين فيُترك للمحرك العام.
        if (want(Intent.ADD_PERSON) && addV && personW && !debtW) {
            val skip = phone?.second?.toSet() ?: emptySet()
            val numbers = NumberParser.findNumbers(toks.map { it.norm })
            val hasAmount = numbers.any { n -> (n.start until n.end).any { it !in skip } }
            if (!hasAmount) {
                val left = span(
                    toks, skip,
                    Lexicon.RECORD_VERBS, PERSON_WORDS, PHONE_WORDS, NAME_WORDS, NOISE
                )
                val raw = left.joinToString(" ") { it.raw }.trim()
                val p = if (raw.isEmpty()) null else PersonRef(raw, null, raw, 0.0, MatchLevel.NEW)
                return Command(Intent.ADD_PERSON, 9.0, rawText, person = p, phone = phone?.first)
            }
        }

        return null
    }
}
