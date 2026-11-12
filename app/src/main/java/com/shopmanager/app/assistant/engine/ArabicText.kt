package com.shopmanager.app.assistant.engine

import kotlin.math.max
import kotlin.math.min

/** تطبيع النص العربي والمطابقة التقريبية. كله نقي بلا أندرويد. */
object ArabicText {

    /** حرف "على" كحرف جر يُحفظ بالألف المقصورة حتى لا يختلط مع الاسم "علي" بعد التطبيع. */
    const val PREP_ON = "على"

    private val SPELLING = mapOf(
        "نواقض" to "نواقص", "نواقس" to "نواقص", "نوقص" to "نواقص", "نواقصص" to "نواقص",
        "سجيل" to "سجل", "سجلل" to "سجل", "ليرا" to "ليره", "كام" to "كم",
        "بكره" to "بكره", "بكرا" to "بكره", "امبارح" to "مبارح", "البارحه" to "مبارح",
        "مبارحه" to "مبارح", "غدا" to "بكره", "ديوون" to "ديون", "الديوون" to "الديون",
        "دبن" to "دين", "ديين" to "دين", "الدبن" to "الدين", "اشتريه" to "اشتريت",
        "حزف" to "حذف", "احزف" to "احذف", "سيدد" to "سدد", "تسجيلل" to "تسجيل"
    )

    /** إزالة التشكيل والتطويل، توحيد الهمزات والتاء المربوطة والألف المقصورة، الأرقام الهندية، تقليل التكرار. */
    fun normalize(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            if (ch in '\u064B'..'\u065F' || ch == '\u0670' || ch == '\u0640' || ch == 'ء') continue
            val c: Char = when (ch) {
                'أ', 'إ', 'آ', 'ٱ' -> 'ا'
                'ى' -> 'ي'
                'ة' -> 'ه'
                'ؤ' -> 'و'
                'ئ' -> 'ي'
                'ک' -> 'ك'
                'ی' -> 'ي'
                in '٠'..'٩' -> '0' + (ch - '٠')
                in '۰'..'۹' -> '0' + (ch - '۰')
                else -> ch.lowercaseChar()
            }
            sb.append(c)
        }
        return collapseRepeats(sb.toString())
    }

    /** أي حرف عربي مكرر 3 مرات فأكثر يصير مرة واحدة ("مرحباااا" -> "مرحبا"). الأرقام لا تُمسّ. */
    private fun collapseRepeats(s: String): String {
        if (s.length < 3) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            var j = i
            while (j < s.length && s[j] == c) j++
            val run = j - i
            if (run >= 3 && c in '\u0621'..'\u064A') sb.append(c) else sb.append(s, i, j)
            i = j
        }
        return sb.toString()
    }

    /** تصحيح الأخطاء الإملائية الشائعة على كلمة مطبَّعة. */
    fun fixSpelling(token: String): String {
        SPELLING[token]?.let { return it }
        if (token.startsWith("ال") && token.length > 3) SPELLING[token.substring(2)]?.let { return "ال$it" }
        return token
    }

    /** الكلمة بعد التطبيع وتصحيح الإملاء. */
    fun canonicalToken(raw: String): String {
        if (raw == PREP_ON) return PREP_ON
        return fixSpelling(normalize(raw))
    }

    private val TITLES = setOf(
        "الحاج", "حاج", "الحاجه", "حاجه", "الاستاذ", "استاذ", "الاستاذه", "المعلم", "معلم", "الدكتور", "دكتور",
        "الدكتوره", "دكتوره", "الشيخ", "شيخ", "السيد", "سيد", "الست", "ست", "عم", "الخواجا", "خواجا", "المهندس", "مهندس"
    )

    fun isTitle(token: String): Boolean = token in TITLES

    /** الاسم بعد حذف الألقاب وأداة التعريف من كل جزء (يُستعمل للمقارنة فقط). */
    fun nameKey(tokens: List<String>): String {
        val kept = tokens.filter { !isTitle(it) }.ifEmpty { tokens }
        return kept.joinToString(" ") { stripAl(it) }
    }

    fun stripAl(token: String): String =
        if (token.length > 3 && token.startsWith("ال")) token.substring(2) else token

    // ---------------- مسافات ومقارنات ----------------

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    fun jaroWinkler(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val range = max(max(a.length, b.length) / 2 - 1, 0)
        val aMatch = BooleanArray(a.length)
        val bMatch = BooleanArray(b.length)
        var matches = 0
        for (i in a.indices) {
            val lo = max(0, i - range)
            val hi = min(b.length - 1, i + range)
            for (j in lo..hi) {
                if (!bMatch[j] && a[i] == b[j]) {
                    aMatch[i] = true; bMatch[j] = true; matches++
                    break
                }
            }
        }
        if (matches == 0) return 0.0
        var k = 0
        var transpositions = 0
        for (i in a.indices) {
            if (!aMatch[i]) continue
            while (!bMatch[k]) k++
            if (a[i] != b[k]) transpositions++
            k++
        }
        val m = matches.toDouble()
        val jaro = (m / a.length + m / b.length + (m - transpositions / 2.0) / m) / 3.0
        var prefix = 0
        while (prefix < min(4, min(a.length, b.length)) && a[prefix] == b[prefix]) prefix++
        return jaro + prefix * 0.1 * (1.0 - jaro)
    }

    /**
     * مفتاح صوتي عربي مبسّط: يدمج الأصوات المتقاربة بين اللهجات (ث/ص->س، ذ/ظ->ز، ط->ت، ض->د، ق->ك، ع->ا، ح->ه)
     * ويحذف حروف العلة غير الأولى. كلمتان بنفس المفتاح غالباً تُنطقان متقاربتين.
     */
    fun phoneticKey(s: String): String {
        val sb = StringBuilder()
        for ((idx, ch) in s.withIndex()) {
            if (ch == ' ') { sb.append(' '); continue }
            val m: Char = when (ch) {
                'ث', 'ص', 'ش' -> 'س'
                'ذ', 'ظ' -> 'ز'
                'ط' -> 'ت'
                'ض' -> 'د'
                'ق', 'غ' -> 'ك'
                'ع' -> 'ا'
                'ح', 'خ' -> 'ه'
                else -> ch
            }
            val isVowel = m == 'ا' || m == 'و' || m == 'ي' || m == 'ه'
            val atWordStart = idx == 0 || s[idx - 1] == ' '
            if (isVowel && !atWordStart) continue
            if (sb.isNotEmpty() && sb[sb.length - 1] == m) continue
            sb.append(m)
        }
        return sb.toString()
    }

    /** تشابه بين 0 و 1 لنصين مطبَّعين. */
    fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val maxLen = max(a.length, b.length)
        val levSim = 1.0 - levenshtein(a, b).toDouble() / maxLen
        val jw = jaroWinkler(a, b)
        var s = (levSim + jw) / 2.0
        // كلمة قصيرة جداً لا تُعتبر مطابقة عالية إلا بالتطابق التام.
        if (maxLen <= 3) s = min(s, 0.7)
        if (a.length >= 3 && b.length >= 3 && phoneticKey(a) == phoneticKey(b)) s = max(s, 0.8)
        return s.coerceIn(0.0, 1.0)
    }
}
