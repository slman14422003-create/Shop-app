package com.shopmanager.app.assistant.engine

data class RecurringAlert(val name: String, val avgDays: Int, val daysSince: Int)

data class ChoiceLearn(val book: HabitBook, val alias: AliasLearn?)

class HabitBook(
    val usage: Map<String, Int> = emptyMap(),
    val units: Map<String, Map<String, Int>> = emptyMap(),
    val qty: Map<String, List<Double>> = emptyMap(),
    val pairs: Map<String, Map<String, Int>> = emptyMap(),
    val hours: Map<String, List<Int>> = emptyMap(),
    val choices: Map<String, Int> = emptyMap(),
    val shortageLog: Map<String, List<Long>> = emptyMap()
) {
    private fun copy(
        usage: Map<String, Int> = this.usage,
        units: Map<String, Map<String, Int>> = this.units,
        qty: Map<String, List<Double>> = this.qty,
        pairs: Map<String, Map<String, Int>> = this.pairs,
        hours: Map<String, List<Int>> = this.hours,
        choices: Map<String, Int> = this.choices,
        shortageLog: Map<String, List<Long>> = this.shortageLog
    ) = HabitBook(usage, units, qty, pairs, hours, choices, shortageLog)

    private fun key(name: String) = ArabicText.normalize(name.trim())

    fun record(cmd: Command, at: Long, zoneOffsetHours: Int = 3): HabitBook {
        if (cmd.intent == Intent.UNKNOWN) return this
        var b = this
        val hr = (((at / 3_600_000L) + zoneOffsetHours) % 24).toInt().let { if (it < 0) it + 24 else it }
        val h = (b.hours[cmd.intent.name] ?: List(24) { 0 }).toMutableList()
        h[hr] = h[hr] + 1
        b = b.copy(hours = b.hours + (cmd.intent.name to h))
        if (cmd.intent == Intent.ADD_SHORTAGE) {
            val names = cmd.materials.filter { it.level != MatchLevel.NEW && it.level != MatchLevel.NONE }.map { it.name }
            val u = b.usage.toMutableMap()
            val un = b.units.toMutableMap()
            val q = b.qty.toMutableMap()
            val lg = b.shortageLog.toMutableMap()
            for (m in cmd.materials) {
                if (m.level == MatchLevel.NEW || m.level == MatchLevel.NONE) continue
                val k = key(m.name)
                u[k] = (u[k] ?: 0) + 1
                if (!m.fromHabit && (m.unit != null || m.pack != null)) {
                    val uk = (m.unit ?: "") + "|" + (m.pack ?: "")
                    val mm = (un[k] ?: emptyMap()).toMutableMap()
                    mm[uk] = (mm[uk] ?: 0) + 1
                    un[k] = mm
                }
                if (!m.fromHabit && m.quantity != null) q[k] = ((q[k] ?: emptyList()) + m.quantity).takeLast(15)
                lg[k] = ((lg[k] ?: emptyList()) + at).takeLast(12)
            }
            val ps = b.pairs.toMutableMap()
            val keys = names.map { key(it) }.distinct()
            for (a in keys) for (c in keys) if (a != c) {
                val mm = (ps[a] ?: emptyMap()).toMutableMap()
                mm[c] = (mm[c] ?: 0) + 1
                ps[a] = mm
            }
            b = b.copy(usage = u, units = un, qty = q, pairs = ps, shortageLog = lg)
        }
        return b
    }

    fun recordChoice(raw: String, name: String): ChoiceLearn {
        val r = key(raw)
        val n = key(name)
        if (r.isEmpty() || r == n) return ChoiceLearn(this, null)
        val k = "$r|$n"
        val c = (choices[k] ?: 0) + 1
        val nb = copy(choices = choices + (k to c))
        return ChoiceLearn(nb, if (c >= 2) AliasLearn(raw.trim(), name) else null)
    }

    fun usualUnit(name: String): Pair<String?, String?>? {
        val m = units[key(name)] ?: return null
        val best = m.maxByOrNull { it.value } ?: return null
        if (best.value < 2) return null
        val parts = best.key.split('|')
        return (parts.getOrNull(0)?.ifEmpty { null }) to (parts.getOrNull(1)?.ifEmpty { null })
    }

    fun usualQty(name: String): Double? {
        val l = qty[key(name)] ?: return null
        if (l.size < 2) return null
        val s = l.sorted()
        return s[s.size / 2]
    }

    fun enrich(snapshot: EngineSnapshot): EngineSnapshot {
        val mats = snapshot.materials.map { m ->
            val u = usualUnit(m.name)
            m.copy(
                usage = usage[key(m.name)] ?: m.usage,
                usualUnit = u?.first ?: m.usualUnit,
                usualPack = u?.second ?: m.usualPack,
                usualQty = usualQty(m.name) ?: m.usualQty
            )
        }
        return snapshot.copy(materials = mats)
    }

    fun companions(name: String, exclude: Set<String> = emptySet(), limit: Int = 2): List<String> {
        val ex = exclude.map { key(it) }.toSet()
        return (pairs[key(name)] ?: emptyMap()).entries
            .filter { it.value >= 2 && it.key !in ex }
            .sortedByDescending { it.value }.take(limit).map { it.key }
    }

    fun recurring(now: Long, current: Set<String>): List<RecurringAlert> {
        val day = 86_400_000L
        val cur = current.map { key(it) }.toSet()
        val out = ArrayList<RecurringAlert>()
        for ((k, log) in shortageLog) {
            if (log.size < 3 || k in cur) continue
            val gaps = log.zipWithNext { a, b -> (b - a).coerceAtLeast(0) }.filter { it >= day / 2 }
            if (gaps.size < 2) continue
            val avg = gaps.average()
            val since = now - log.last()
            if (since >= avg * 0.8 && since <= avg * 1.6) {
                out.add(RecurringAlert(k, (avg / day).toInt().coerceAtLeast(1), (since / day).toInt()))
            }
        }
        return out.sortedByDescending { it.daysSince }
    }

    fun rankByUsage(names: List<String>): List<String> =
        names.sortedByDescending { usage[key(it)] ?: 0 }

    fun quickOrder(hour: Int, labels: List<Pair<String, Intent>>): List<Pair<String, Intent>> {
        fun w(i: Intent): Int {
            val h = hours[i.name] ?: return 0
            return h[hour] * 2 + h[(hour + 23) % 24] + h[(hour + 1) % 24]
        }
        return labels.withIndex().sortedWith(compareByDescending<IndexedValue<Pair<String, Intent>>> { w(it.value.second) }.thenBy { it.index }).map { it.value }
    }

    fun toText(): String {
        val sb = StringBuilder()
        fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')
        usage.forEach { (k, v) -> sb.append("U\t${clean(k)}\t$v\n") }
        units.forEach { (k, m) -> m.forEach { (u, c) -> sb.append("N\t${clean(k)}\t${clean(u)}\t$c\n") } }
        qty.forEach { (k, l) -> sb.append("Q\t${clean(k)}\t${l.joinToString(",")}\n") }
        pairs.forEach { (k, m) -> m.forEach { (o, c) -> sb.append("P\t${clean(k)}\t${clean(o)}\t$c\n") } }
        hours.forEach { (k, l) -> sb.append("H\t${clean(k)}\t${l.joinToString(",")}\n") }
        choices.forEach { (k, c) -> sb.append("C\t${clean(k)}\t$c\n") }
        shortageLog.forEach { (k, l) -> sb.append("S\t${clean(k)}\t${l.joinToString(",")}\n") }
        return sb.toString()
    }

    fun merge(other: HabitBook): HabitBook {
        fun <V> mergeMap(a: Map<String, V>, b: Map<String, V>, f: (V, V) -> V): Map<String, V> {
            val m = a.toMutableMap()
            for ((k, v) in b) m[k] = m[k]?.let { f(it, v) } ?: v
            return m
        }
        return HabitBook(
            mergeMap(usage, other.usage) { x, y -> x + y },
            mergeMap(units, other.units) { x, y -> mergeMap(x, y) { p, q -> p + q } },
            mergeMap(qty, other.qty) { x, y -> (x + y).takeLast(15) },
            mergeMap(pairs, other.pairs) { x, y -> mergeMap(x, y) { p, q -> p + q } },
            mergeMap(hours, other.hours) { x, y -> x.zip(y) { p, q -> p + q } },
            mergeMap(choices, other.choices) { x, y -> x + y },
            mergeMap(shortageLog, other.shortageLog) { x, y -> (x + y).distinct().sorted().takeLast(12) }
        )
    }

    companion object {
        fun fromText(text: String): HabitBook {
            val usage = HashMap<String, Int>()
            val units = HashMap<String, MutableMap<String, Int>>()
            val qty = HashMap<String, List<Double>>()
            val pairs = HashMap<String, MutableMap<String, Int>>()
            val hours = HashMap<String, List<Int>>()
            val choices = HashMap<String, Int>()
            val log = HashMap<String, List<Long>>()
            for (line in text.lines()) {
                val f = line.split('\t')
                if (f.size < 3) continue
                runCatching {
                    when (f[0]) {
                        "U" -> usage[f[1]] = f[2].toInt()
                        "N" -> units.getOrPut(f[1]) { HashMap() }[f[2]] = f[3].toInt()
                        "Q" -> qty[f[1]] = f[2].split(',').filter { it.isNotEmpty() }.map { it.toDouble() }
                        "P" -> pairs.getOrPut(f[1]) { HashMap() }[f[2]] = f[3].toInt()
                        "H" -> hours[f[1]] = f[2].split(',').map { it.toInt() }.let { if (it.size == 24) it else List(24) { 0 } }
                        "C" -> choices[f[1]] = f[2].toInt()
                        "S" -> log[f[1]] = f[2].split(',').filter { it.isNotEmpty() }.map { it.toLong() }
                    }
                }
            }
            return HabitBook(usage, units, qty, pairs, hours, choices, log)
        }
    }
}

object PersonalDictionary {
    fun export(aliases: Map<String, String>): String =
        aliases.entries.joinToString("\n") { it.key.replace('\t', ' ').replace('\n', ' ') + "\t" + it.value.replace('\t', ' ').replace('\n', ' ') }

    fun import(text: String): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        for (line in text.lines()) {
            val i = line.indexOf('\t')
            if (i <= 0) continue
            val a = ArabicText.normalize(line.substring(0, i).trim())
            val c = line.substring(i + 1).trim()
            if (a.isNotEmpty() && c.isNotEmpty() && a != ArabicText.normalize(c)) m[a] = c
        }
        return m
    }
}

object DuplicateGuard {
    private fun ago(ms: Long): String {
        val min = (ms / 60_000L).toInt().coerceAtLeast(1)
        return when {
            min < 60 -> if (min == 1) "قبل دقيقة" else if (min == 2) "قبل دقيقتين" else if (min < 11) "قبل $min دقائق" else "قبل $min دقيقة"
            min < 120 -> "قبل ساعة"
            min < 180 -> "قبل ساعتين"
            else -> "قبل ${min / 60} ساعات"
        }
    }

    fun check(kind: String, key: String, amount: Double, snapshot: EngineSnapshot, windowMs: Long = 6 * 3_600_000L): String? {
        val k = ArabicText.normalize(key)
        val hit = snapshot.recentOps
            .filter { it.kind == kind && ArabicText.normalize(it.key) == k && (kind == Intent.ADD_SHORTAGE.name || kotlin.math.abs(it.amount - amount) < 0.0001) }
            .filter { snapshot.nowMillis - it.at in 0..windowMs }
            .maxByOrNull { it.at } ?: return null
        return "أُضيفت ${ago(snapshot.nowMillis - hit.at)}، تضيفها مرة ثانية؟"
    }
}
