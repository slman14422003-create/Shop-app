package com.salman.herbalencyclopedia.data.ai

object SemoReplyPolicy {

    const val DOCTOR_NOTICE =
        "⚕️ تنبيه: هذه معلومات عامة من الموسوعة وليست بديلاً عن استشارة الطبيب، " +
            "خصوصاً مع الحمل أو الرضاعة أو الأطفال أو الأمراض المزمنة أو تناول أدوية."

    const val NOT_ENOUGH_INFO =
        "لم أجد في الموسوعة معلومات كافية للإجابة عن هذا السؤال، ولا أريد التخمين. " +
            "جرّب ذكر اسم العشبة أو صياغة السؤال بشكل أوضح."

    const val MAX_SHORT_LINES = 3
    const val MAX_SHORT_CHARS = 360

    fun appendNotice(text: String): String {
        if (text.contains(DOCTOR_NOTICE)) return text
        return text.trimEnd() + "\n\n" + DOCTOR_NOTICE
    }

    fun stripNotice(text: String): String {
        if (!text.contains(DOCTOR_NOTICE)) return text
        return text.replace(DOCTOR_NOTICE, "").trimEnd()
    }

    class Shaped(val shortText: String, val fullText: String?)

    fun shape(text: String): Shaped {
        val hadNotice = text.contains(DOCTOR_NOTICE)
        val body = stripNotice(text).trim()
        val shortBody = shorten(body)
        val needsMore = shortBody.length < body.length
        val shortFinal = if (hadNotice) appendNotice(shortBody) else shortBody
        val fullFinal = if (hadNotice) appendNotice(body) else body
        return Shaped(shortFinal, if (needsMore) fullFinal else null)
    }

    fun shorten(body: String): String {
        val lines = body.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return body
        val picked = ArrayList<String>()
        var chars = 0
        for (line in lines) {
            if (picked.size >= MAX_SHORT_LINES) break
            if (picked.isNotEmpty() && chars + line.length > MAX_SHORT_CHARS) break
            picked += line
            chars += line.length
        }
        if (picked.size == 1 && picked[0].length > MAX_SHORT_CHARS) {
            picked[0] = cutAtSentence(picked[0], MAX_SHORT_CHARS)
        }
        return picked.joinToString("\n")
    }

    private fun cutAtSentence(text: String, max: Int): String {
        val window = text.substring(0, max.coerceAtMost(text.length))
        var cut = -1
        for (i in window.indices.reversed()) {
            val ch = window[i]
            if (ch == '.' || ch == '!' || ch == '؟' || ch == '?' || ch == '؛') {
                cut = i
                break
            }
        }
        if (cut >= max / 3) return window.substring(0, cut + 1)
        val space = window.lastIndexOf(' ')
        val base = if (space > max / 2) window.substring(0, space) else window
        return base.trimEnd() + "…"
    }

    fun sourceLabel(field: String): String = when (field) {
        "الفوائد" -> "فوائد"
        "التحذيرات" -> "تحذيرات"
        "الاستخدام" -> "استخدام"
        "الأضرار" -> "أضرار"
        "ملاحظات" -> "ملاحظات"
        else -> field
    }
}
