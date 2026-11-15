package com.shopmanager.app.assistant.engine

/** نتيجة رقم/مبلغ: من توكن start (شاملاً) إلى end (غير شامل). */
data class NumberMatch(val start: Int, val end: Int, val value: Double)

/**
 * محلل أرقام ومبالغ بكل الصيغ: "50 ألف"، "خمسين ألف"، "ألفين"، "نص مليون"، "مية وخمسين"،
 * "مليون ونص"، "1,500"، "٥٠٠"، "50k". يعمل على توكنات مطبَّعة.
 */
object NumberParser {

    private val ONES = mapOf(
        "واحد" to 1.0, "اثنين" to 2.0, "اتنين" to 2.0, "اثنان" to 2.0, "اثنتين" to 2.0, "ثنتين" to 2.0,
        "اثنا" to 2.0, "اثني" to 2.0,
        "ثلاثه" to 3.0, "ثلاث" to 3.0, "تلاته" to 3.0, "تلات" to 3.0,
        "اربعه" to 4.0, "اربع" to 4.0, "خمسه" to 5.0, "خمس" to 5.0, "سته" to 6.0,
        "سبعه" to 7.0, "سبع" to 7.0, "ثمانيه" to 8.0, "ثماني" to 8.0, "ثمان" to 8.0,
        "تمانيه" to 8.0, "تماني" to 8.0, "تمان" to 8.0, "تسعه" to 9.0, "تسع" to 9.0,
        "عشره" to 10.0, "عشر" to 10.0,
        "حدعش" to 11.0, "احدعش" to 11.0, "اثنعش" to 12.0, "اطناش" to 12.0, "تلتعش" to 13.0, "تلاتعش" to 13.0, "ثلاثتعش" to 13.0, "اربعتعش" to 14.0, "خمستعش" to 15.0, "ستعش" to 16.0, "سبعتعش" to 17.0, "تمنتعش" to 18.0, "ثمنتعش" to 18.0, "تسعتعش" to 19.0, "تلتاشر" to 13.0, "حداشر" to 11.0, "احداشر" to 11.0, "اثناشر" to 12.0, "تناشر" to 12.0, "طناش" to 12.0,
        "تلتاشر" to 13.0, "ثلاثطعش" to 13.0, "تلاتطعش" to 13.0, "اربعتاشر" to 14.0, "اربعطعش" to 14.0,
        "خمستاشر" to 15.0, "خمسطعش" to 15.0, "ستاشر" to 16.0, "سطعش" to 16.0, "سبعتاشر" to 17.0,
        "سبعطعش" to 17.0, "تمنتاشر" to 18.0, "ثمنطعش" to 18.0, "تسعتاشر" to 19.0, "تسعطعش" to 19.0,
        "عشرين" to 20.0, "عشرون" to 20.0, "ثلاثين" to 30.0, "ثلاثون" to 30.0, "تلاتين" to 30.0,
        "اربعين" to 40.0, "اربعون" to 40.0, "خمسين" to 50.0, "خمسون" to 50.0,
        "ستين" to 60.0, "ستون" to 60.0, "سبعين" to 70.0, "سبعون" to 70.0,
        "ثمانين" to 80.0, "ثمانون" to 80.0, "تمانين" to 80.0, "تسعين" to 90.0, "تسعون" to 90.0
    )

    /** "ست" قد تكون لقب (الست فلانة) فلا تُعدّ رقماً إلا قبل مضاعف. */
    private const val AMBIGUOUS_SIX = "ست"

    private val HUNDRED_WORDS = setOf("ميه", "مايه", "ميت")
    private val HUNDREDS_EXACT = mapOf(
        "ميتين" to 200.0, "مايتين" to 200.0, "ميتان" to 200.0, "مايتان" to 200.0
    )
    private val HUNDRED_PREFIX = mapOf(
        "ثلاث" to 3.0, "ثلاثه" to 3.0, "تلات" to 3.0, "تلت" to 3.0, "ثلث" to 3.0,
        "اربع" to 4.0, "خمس" to 5.0, "ست" to 6.0, "سبع" to 7.0,
        "ثمن" to 8.0, "تمن" to 8.0, "ثمان" to 8.0, "تمان" to 8.0, "تسع" to 9.0
    )

    private val MULT = mapOf(
        "الف" to 1_000.0, "الاف" to 1_000.0, "تلاف" to 1_000.0, "مليون" to 1_000_000.0, "ملايين" to 1_000_000.0,
        "مليار" to 1_000_000_000.0, "مليارات" to 1_000_000_000.0
    )
    private val FIXED = mapOf(
        "الفين" to 2_000.0, "الفان" to 2_000.0, "مليونين" to 2_000_000.0, "مليونان" to 2_000_000.0
    )
    private val FRACTION = mapOf("نص" to 0.5, "نصف" to 0.5, "ربع" to 0.25)

    private val DIGITS = Regex("^\\d+(?:[.,]\\d+)*$")
    private val DIGITS_SUFFIX = Regex("^(\\d+(?:\\.\\d+)?)(الف|الاف|مليون|k|ك|m)$")

    private class Tk(val kind: Char, val value: Double)

    fun isMagnitudeWord(tok: String): Boolean = tok in MULT || tok in FIXED

    private fun parseDigits(tok: String): Double? {
        if (!DIGITS.matches(tok)) return null
        val hasDot = tok.contains('.')
        val hasComma = tok.contains(',')
        val s = when {
            hasDot && hasComma -> tok.replace(",", "")
            hasComma -> if (Regex("^\\d{1,3}(,\\d{3})+$").matches(tok)) tok.replace(",", "") else tok.replace(',', '.')
            hasDot -> if (Regex("^\\d{1,3}(\\.\\d{3})+$").matches(tok)) tok.replace(".", "") else tok
            else -> tok
        }
        return s.toDoubleOrNull()
    }

    private fun hundredsOf(tok: String): Double? {
        HUNDREDS_EXACT[tok]?.let { return it }
        for (suffix in listOf("مايه", "ميه", "ميت")) {
            if (tok.length > suffix.length && tok.endsWith(suffix)) {
                HUNDRED_PREFIX[tok.removeSuffix(suffix)]?.let { return it * 100.0 }
            }
        }
        return null
    }

    private fun classify(tok: String, next: String?): Tk? {
        parseDigits(tok)?.let { return Tk('D', it) }
        DIGITS_SUFFIX.matchEntire(tok)?.let { m ->
            val base = m.groupValues[1].toDoubleOrNull() ?: return null
            val mult = when (m.groupValues[2]) { "الف", "الاف", "k", "ك" -> 1_000.0; else -> 1_000_000.0 }
            return Tk('F', base * mult)
        }
        if (tok == "احد") return if (next == "عشر") Tk('U', 1.0) else null
        if (tok == AMBIGUOUS_SIX) return if (next != null && isMagnitudeWord(next)) Tk('U', 6.0) else null
        ONES[tok]?.let { return Tk('U', it) }
        if (tok in HUNDRED_WORDS) return Tk('h', 100.0)
        hundredsOf(tok)?.let { return Tk('H', it) }
        FIXED[tok]?.let { return Tk('F', it) }
        MULT[tok]?.let { return Tk('M', it) }
        FRACTION[tok]?.let { return Tk('X', it) }
        return null
    }

    /** يحاول تصنيف التوكن، وإن بدأ بواو العطف الملتصقة ("وخمسين") يصنّف ما بعدها. */
    private fun classifyWithWaw(tok: String, next: String?): Tk? {
        classify(tok, next)?.let { return it }
        if (tok.length > 2 && tok[0] == 'و') return classify(tok.substring(1), next)
        return null
    }

    fun findNumbers(tokens: List<String>): List<NumberMatch> {
        val out = ArrayList<NumberMatch>()
        var i = 0
        while (i < tokens.size) {
            val r = parsePhrase(tokens, i)
            if (r != null) { out.add(r); i = r.end } else i++
        }
        return out
    }

    private fun parsePhrase(t: List<String>, start: Int): NumberMatch? {
        var i = start
        var total = 0.0
        var cur = 0.0
        var curSet = false
        var lastMag = 0.0
        var consumed = false
        var end = start
        while (i < t.size) {
            val tok = t[i]
            val next = if (i + 1 < t.size) t[i + 1] else null
            if (tok == "و") {
                // واو عطف مستقلة: تُقبل فقط إن تبعها رقم.
                if (consumed && next != null && classifyWithWaw(next, t.getOrNull(i + 2)) != null) { i++; continue }
                break
            }
            val c = classifyWithWaw(tok, next) ?: break
            when (c.kind) {
                'D' -> {
                    if (curSet) break
                    cur = c.value; curSet = true
                }
                'U' -> { cur += c.value; curSet = true }
                'H' -> { cur += c.value; curSet = true }
                'h' -> {
                    if (curSet && cur in 1.0..9.0) cur *= 100.0 else cur += 100.0
                    curSet = true
                }
                'M' -> {
                    val base = if (curSet) cur else 1.0
                    total += base * c.value
                    lastMag = c.value
                    cur = 0.0; curSet = false
                }
                'F' -> {
                    if (curSet) break
                    total += c.value
                    lastMag = if (c.value >= 1_000_000.0) 1_000_000.0 else 1_000.0
                }
                'X' -> {
                    val nextIsMag = next != null && isMagnitudeWord(next)
                    if (!curSet && lastMag > 0.0 && total > 0.0) {
                        total += lastMag * c.value
                    } else if (!curSet && nextIsMag) {
                        cur = c.value; curSet = true
                    } else break
                }
            }
            consumed = true
            end = i + 1
            i++
        }
        if (!consumed) return null
        val value = total + cur
        if (value <= 0.0 && !curSet) return null
        return NumberMatch(start, end, value)
    }
}
