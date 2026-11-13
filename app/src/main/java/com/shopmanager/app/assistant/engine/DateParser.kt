package com.shopmanager.app.assistant.engine

import java.util.Calendar
import java.util.TimeZone

data class DateParse(val ref: DateRef, val consumed: Set<Int>)

/** تواريخ بالعامية: مبارح، اليوم، بكرة، الخميس، من أسبوع، آخر الشهر، + وقت اليوم (العصر، الساعة 5). */
object DateParser {

    private val WEEKDAYS = mapOf(
        "سبت" to Calendar.SATURDAY, "احد" to Calendar.SUNDAY,
        "اثنين" to Calendar.MONDAY, "اتنين" to Calendar.MONDAY, "تنين" to Calendar.MONDAY,
        "ثلاثا" to Calendar.TUESDAY, "تلاتا" to Calendar.TUESDAY,
        "اربعا" to Calendar.WEDNESDAY, "خميس" to Calendar.THURSDAY, "جمعه" to Calendar.FRIDAY
    )
    private val WEEKDAY_LABEL = mapOf(
        Calendar.SATURDAY to "السبت", Calendar.SUNDAY to "الأحد", Calendar.MONDAY to "الاثنين",
        Calendar.TUESDAY to "الثلاثاء", Calendar.WEDNESDAY to "الأربعاء",
        Calendar.THURSDAY to "الخميس", Calendar.FRIDAY to "الجمعة"
    )

    private val DAY_UNITS = mapOf(
        "يوم" to 1, "يومين" to 2, "ايام" to 1, "اسبوع" to 7, "اسبوعين" to 14, "اسابيع" to 7,
        "شهر" to 30, "شهرين" to 60, "شهور" to 30, "اشهر" to 30, "سنه" to 365, "سنتين" to 730, "سنوات" to 365
    )
    private val DUAL_UNITS = setOf("يومين", "اسبوعين", "شهرين", "سنتين")

    private val TIME_OF_DAY = mapOf(
        "صبح" to (9 to 0), "صباحا" to (9 to 0), "فجر" to (5 to 0), "ظهر" to (12 to 0), "ظهرا" to (12 to 0),
        "عصر" to (16 to 0), "مغرب" to (18 to 30), "مسا" to (19 to 0), "مساء" to (19 to 0), "عشا" to (20 to 0),
        "ليل" to (21 to 0), "ليله" to (21 to 0)
    )

    private fun cal(now: Long, tz: String): Calendar =
        Calendar.getInstance(TimeZone.getTimeZone(tz)).apply { timeInMillis = now }

    private fun startOfDay(c: Calendar): Long {
        val d = c.clone() as Calendar
        d.set(Calendar.HOUR_OF_DAY, 0); d.set(Calendar.MINUTE, 0); d.set(Calendar.SECOND, 0); d.set(Calendar.MILLISECOND, 0)
        return d.timeInMillis
    }

    private fun bare(t: String): String = ArabicText.stripAl(t)

    fun parse(tokens: List<String>, numbers: List<NumberMatch>, nowMillis: Long, tz: String, preferFuture: Boolean): DateParse? {
        val now = cal(nowMillis, tz)
        val today = startOfDay(now)
        val consumed = HashSet<Int>()
        var dayStart: Long? = null
        var dayEnd: Long? = null
        var label = ""

        fun day(offset: Int, text: String) {
            val c = now.clone() as Calendar
            c.add(Calendar.DAY_OF_YEAR, offset)
            dayStart = startOfDay(c); label = text
        }

        var i = 0
        while (i < tokens.size && dayStart == null) {
            val t = tokens[i]
            val b = bare(t)
            when {
                t == "بعد" && i + 1 < tokens.size && (tokens[i + 1] == "بكره" || tokens[i + 1] == "غد") -> {
                    day(2, "بعد بكرة"); consumed.add(i); consumed.add(i + 1)
                }
                t == "بكره" -> { day(1, "بكرة"); consumed.add(i) }
                t == "اليوم" || t == "هاليوم" -> { day(0, "اليوم"); consumed.add(i) }
                t == "مبارح" -> { day(-1, "مبارح"); consumed.add(i) }
                t == "الليله" || t == "الليلة" -> { day(0, "الليلة"); consumed.add(i) }
                (t == "من" || t == "منذ" || t == "قبل") && sinceAt(tokens, numbers, i) != null -> {
                    val (days, upTo) = sinceAt(tokens, numbers, i)!!
                    val c = now.clone() as Calendar
                    c.add(Calendar.DAY_OF_YEAR, -days)
                    dayStart = startOfDay(c); dayEnd = today + 86_400_000L
                    label = "منذ " + describeDays(days)
                    for (k in i..upTo) consumed.add(k)
                }
                (t == "اخر" || t == "نهايه") && i + 1 < tokens.size && bare(tokens[i + 1]) == "شهر" -> {
                    val c = now.clone() as Calendar
                    c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH))
                    dayStart = startOfDay(c); label = "آخر الشهر"
                    consumed.add(i); consumed.add(i + 1)
                }
                (t.startsWith("ال") && WEEKDAYS.containsKey(b)) || (i > 0 && tokens[i - 1] == "يوم" && WEEKDAYS.containsKey(b)) -> {
                    val target = WEEKDAYS.getValue(b)
                    val cur = now.get(Calendar.DAY_OF_WEEK)
                    var diff = if (preferFuture) (target - cur + 7) % 7 else -((cur - target + 7) % 7)
                    if (diff == 0) diff = if (preferFuture) 7 else -7
                    day(diff, WEEKDAY_LABEL.getValue(target)); consumed.add(i)
                    if (i > 0 && tokens[i - 1] == "يوم") consumed.add(i - 1)
                }
            }
            i++
        }

        // وقت داخل اليوم
        var hour = -1
        var minute = 0
        var timeLabel = ""
        for (k in tokens.indices) {
            val b = bare(tokens[k])
            val tod = TIME_OF_DAY[b]
            if (tod != null && !consumed.contains(k)) {
                hour = tod.first; minute = tod.second; timeLabel = tokens[k]; consumed.add(k)
            }
        }
        for (k in tokens.indices) {
            if (bare(tokens[k]) == "ساعه" && k + 1 < tokens.size) {
                val n = numbers.firstOrNull { it.start == k + 1 }
                if (n != null && n.value in 1.0..12.0) {
                    var h = n.value.toInt()
                    val pm = tokens.any { val x = bare(it); x == "عصر" || x == "مساء" || x == "مسا" || x == "عشا" || x == "ليل" || x == "ظهر" }
                    val am = tokens.any { val x = bare(it); x == "صبح" || x == "صباحا" || x == "فجر" }
                    h = when {
                        pm && h < 12 -> h + 12
                        am -> h
                        h in 1..6 -> h + 12
                        else -> h
                    }
                    hour = h; minute = 0
                    var end = n.end
                    if (end < tokens.size && (tokens[end] == "ونص" || tokens[end] == "والنص")) { minute = 30; consumed.add(end); end++ }
                    timeLabel = "الساعة ${n.value.toInt()}"
                    consumed.add(k)
                    for (q in n.start until n.end) consumed.add(q)
                }
            }
        }

        if (dayStart == null && hour < 0) return null
        var start = dayStart ?: today
        var finalLabel = label
        var timeMillis: Long? = null
        if (hour >= 0) {
            val c = now.clone() as Calendar
            c.timeInMillis = start
            c.set(Calendar.HOUR_OF_DAY, hour); c.set(Calendar.MINUTE, minute)
            timeMillis = c.timeInMillis
            if (dayStart == null && preferFuture && timeMillis!! <= nowMillis) {
                c.add(Calendar.DAY_OF_YEAR, 1)
                timeMillis = c.timeInMillis
                start = startOfDay(c)
                finalLabel = "بكرة"
            } else if (dayStart == null) {
                finalLabel = "اليوم"
            }
            finalLabel = (finalLabel + " " + timeLabel).trim()
        }
        return DateParse(DateRef(start, dayEnd, finalLabel, timeMillis), consumed)
    }

    /** "من أسبوع" / "من 3 أيام" / "منذ شهرين": يرجع (عدد الأيام، آخر توكن مستهلك). */
    private fun sinceAt(tokens: List<String>, numbers: List<NumberMatch>, i: Int): Pair<Int, Int>? {
        if (i + 1 >= tokens.size) return null
        val num = numbers.firstOrNull { it.start == i + 1 }
        if (num != null && num.end < tokens.size) {
            val unit = DAY_UNITS[tokens[num.end]] ?: return null
            return Pair((num.value * unit).toInt().coerceAtLeast(1), num.end)
        }
        val u = tokens[i + 1]
        val unit = DAY_UNITS[u] ?: return null
        val n = if (u in DUAL_UNITS) 1 else 1
        return Pair(n * unit, i + 1)
    }

    private fun describeDays(days: Int): String = when {
        days == 1 -> "يوم"
        days == 2 -> "يومين"
        days < 7 -> "$days أيام"
        days == 7 -> "أسبوع"
        days == 14 -> "أسبوعين"
        days % 7 == 0 && days < 30 -> "${days / 7} أسابيع"
        days == 30 -> "شهر"
        days == 60 -> "شهرين"
        days % 30 == 0 && days < 360 -> "${days / 30} أشهر"
        else -> "$days يوم"
    }

    /** فترة الملخص من كلمات مثل "اليوم" و"هالأسبوع" و"هذا الشهر". */
    fun detectPeriod(tokens: List<String>): Period? {
        for ((i, t) in tokens.withIndex()) {
            val b = bare(t)
            if (b == "اسبوع" || b == "هالاسبوع" || t == "هالاسبوع" || t == "الاسبوعي") return Period.WEEK
            if (b == "شهر" || t == "هالشهر" || t == "الشهري") return Period.MONTH
            if (t == "اليوم" || t == "هاليوم" || t == "اليومي") return Period.TODAY
            if (b == "يوم" && i > 0 && tokens[i - 1] == "هذا") return Period.TODAY
        }
        return null
    }
}
