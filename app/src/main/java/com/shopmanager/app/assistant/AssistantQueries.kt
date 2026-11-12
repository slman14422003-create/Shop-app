package com.shopmanager.app.assistant

import com.shopmanager.app.assistant.engine.ArabicText
import com.shopmanager.app.assistant.engine.Command
import com.shopmanager.app.assistant.engine.Intent
import com.shopmanager.app.assistant.engine.Period
import com.shopmanager.app.data.materials.PriceChange
import com.shopmanager.app.ui.common.Formatters

/** أجوبة أوامر القراءة فقط (تُحسب من لقطة البيانات في الذاكرة، بلا شبكة). */
object AssistantQueries {

    private const val DAY = 86_400_000L

    private fun money(v: Double, cur: String) = "${Formatters.number(v)} $cur"
    private fun norm(s: String) = ArabicText.normalize(s.trim())

    fun answer(
        cmd: Command,
        data: AssistantData,
        priceHistory: Map<String, List<PriceChange>>,
        now: Long
    ): AssistantCard.Answer? {
        val cur = data.currency
        return when (cmd.intent) {
            Intent.QUERY_BALANCE -> {
                val p = cmd.person ?: return null
                val person = data.persons.firstOrNull { it.id == p.id } ?: return null
                val ds = data.debts.filter { it.personId == person.id }
                val rows = ArrayList<CardField>()
                rows.add(CardField("الرصيد", money(ds.sumOf { it.amount }.takeIf { ds.isNotEmpty() } ?: person.amount, cur)))
                rows.add(CardField("عدد العمليات", Formatters.number(ds.size.toDouble())))
                ds.minByOrNull { it.createdAt }?.takeIf { it.createdAt > 0 }?.let {
                    rows.add(CardField("أقدم دين", "منذ ${daysAgo(it.createdAt, now)}"))
                }
                if (person.phone.isNotBlank()) rows.add(CardField("الهاتف", person.phone))
                AssistantCard.Answer("رصيد ${person.name}", rows)
            }
            Intent.QUERY_TOTAL_DEBTS -> {
                val withDebt = data.persons.filter { it.amount > 0 }
                AssistantCard.Answer(
                    "مجموع الديون",
                    listOf(
                        CardField("المجموع", money(withDebt.sumOf { it.amount }, cur)),
                        CardField("عدد الأشخاص", Formatters.number(withDebt.size.toDouble()))
                    )
                )
            }
            Intent.QUERY_TOP_DEBTORS -> {
                val n = (cmd.limit ?: 5).coerceIn(1, 20)
                val top = data.persons.filter { it.amount > 0 }.sortedByDescending { it.amount }.take(n)
                if (top.isEmpty()) AssistantCard.Answer("أكثر المدينين", emptyList(), "ما في ديون حالياً")
                else AssistantCard.Answer("أكثر $n مدينين", top.mapIndexed { i, p -> CardField("${i + 1}. ${p.name}", money(p.amount, cur)) })
            }
            Intent.QUERY_STALE_DEBTORS -> {
                val since = cmd.date?.startMillis ?: (now - 30 * DAY)
                val oldestByPerson = data.debts.filter { it.amount > 0 && it.createdAt > 0 }.groupBy { it.personId }
                    .mapValues { e -> e.value.minOf { it.createdAt } }
                val rows = data.persons.filter { it.amount > 0 }
                    .mapNotNull { p -> oldestByPerson[p.id]?.takeIf { it <= since }?.let { p to it } }
                    .sortedBy { it.second }.take(15)
                if (rows.isEmpty()) AssistantCard.Answer("متأخرون عن السداد", emptyList(), "ما في حدا متأخر خلال هالفترة")
                else AssistantCard.Answer("لم يسددوا منذ مدة", rows.map { (p, t) ->
                    CardField(p.name, "${money(p.amount, cur)} • منذ ${daysAgo(t, now)}")
                })
            }
            Intent.LIST_SHORTAGES -> {
                val list = data.materials
                if (list.isEmpty()) AssistantCard.Answer("النواقص", emptyList(), "ما في نواقص حالياً 👌")
                else AssistantCard.Answer(
                    "النواقص (${Formatters.number(list.size.toDouble())})",
                    list.take(25).map { m ->
                        val q = if (m.quantity > 0) "${Formatters.number(m.quantity)} ${m.unit}".trim() else ""
                        CardField((if (m.important) "★ " else "") + m.name, q)
                    },
                    if (list.size > 25) "و${list.size - 25} مواد أخرى" else null
                )
            }
            Intent.QUERY_PRICE -> {
                val ref = cmd.materials.firstOrNull() ?: return null
                val key = data.prices.keys.firstOrNull { norm(it) == norm(ref.name) }
                val price = key?.let { data.prices[it] }
                if (key == null || price == null) {
                    AssistantCard.Answer("سعر ${ref.name}", emptyList(), "ما عندي سعر لهالمادة بعد")
                } else {
                    val rows = ArrayList<CardField>()
                    rows.add(CardField("السعر", money(price, cur)))
                    priceHistory[key]?.maxByOrNull { it.at }?.let { ch ->
                        rows.add(CardField("آخر تحديث", "منذ ${daysAgo(ch.at, now)}"))
                        ch.oldPrice?.let { rows.add(CardField("السعر السابق", money(it, cur))) }
                    }
                    AssistantCard.Answer("سعر $key", rows)
                }
            }
            Intent.SEARCH_MATERIAL -> {
                val q = cmd.materials.firstOrNull()?.name ?: return null
                val nq = norm(q)
                val names = LinkedHashSet<String>()
                data.materials.forEach { if (norm(it.name).contains(nq)) names.add(it.name) }
                data.prices.keys.forEach { if (norm(it).contains(nq)) names.add(it) }
                data.catalog.forEach { if (norm(it.name).contains(nq)) names.add(it.name) }
                if (names.isEmpty()) AssistantCard.Answer("بحث: $q", emptyList(), "ما لقيت شي بهالاسم")
                else AssistantCard.Answer("نتائج \"$q\"", names.take(15).map { n ->
                    val inShort = data.materials.any { norm(it.name) == norm(n) }
                    val p = data.prices.entries.firstOrNull { norm(it.key) == norm(n) }?.value
                    CardField(n, listOfNotNull(if (inShort) "بالنواقص" else null, p?.let { money(it, cur) }).joinToString(" • "))
                })
            }
            Intent.SUMMARY -> summary(cmd.period ?: Period.TODAY, data, priceHistory, now)
            Intent.CALCULATE -> AssistantCard.Answer(
                "نتيجة الحساب",
                listOf(CardField(cmd.expression ?: "", Formatters.number(cmd.result ?: 0.0)))
            )
            else -> null
        }
    }

    private fun summary(period: Period, data: AssistantData, history: Map<String, List<PriceChange>>, now: Long): AssistantCard.Answer {
        val span = when (period) { Period.TODAY -> 1L; Period.WEEK -> 7L; Period.MONTH -> 30L }
        val since = now - span * DAY
        val title = when (period) { Period.TODAY -> "ملخص اليوم"; Period.WEEK -> "ملخص الأسبوع"; Period.MONTH -> "ملخص الشهر" }
        val cur = data.currency
        val newDebts = data.debts.filter { it.createdAt >= since }
        val newShortages = data.materials.filter { it.updatedAt >= since }
        val priceChanges = history.values.sumOf { l -> l.count { it.at >= since } }
        val totalDebts = data.persons.filter { it.amount > 0 }.sumOf { it.amount }
        return AssistantCard.Answer(
            title,
            listOf(
                CardField("ديون جديدة", "${Formatters.number(newDebts.size.toDouble())} • ${money(newDebts.sumOf { it.amount }, cur)}"),
                CardField("نواقص أُضيفت", Formatters.number(newShortages.size.toDouble())),
                CardField("أسعار تغيّرت", Formatters.number(priceChanges.toDouble())),
                CardField("إجمالي الديون الآن", money(totalDebts, cur))
            )
        )
    }

    private fun daysAgo(time: Long, now: Long): String {
        val d = ((now - time) / DAY).toInt().coerceAtLeast(0)
        return when {
            d == 0 -> "اليوم"
            d == 1 -> "يوم"
            d == 2 -> "يومين"
            d < 11 -> "$d أيام"
            d < 30 -> "$d يوم"
            d < 60 -> "شهر"
            d < 365 -> "${d / 30} أشهر"
            else -> "${d / 365} سنة"
        }
    }

    /** نص "ما يستطيع المساعد فعله" مصنّفاً، للورقة السفلية وحالة الشاشة الفارغة. */
    val capabilityGroups: List<Pair<String, List<String>>> = listOf(
        "الديون" to listOf(
            "سجل على سامر 20 ألف", "سدد أحمد 5000", "سدد سامر الباقي", "نقص من دين خالد 3000",
            "كم على سامر؟", "مجموع الديون", "أكثر 5 مدينين", "من لم يسدد منذ مدة", "احذف دين سامر"
        ),
        "النواقص" to listOf(
            "ضيف سكر وملح للنواقص", "ضيف 3 كيس رز للنواقص", "خلصت الهيل", "اشتريت السكر", "شو النواقص", "إيجاد المواد الناقصة"
        ),
        "الأسعار" to listOf(
            "كم سعر السكر؟", "خلي سعر الهيل 90 ألف", "ضيف مادة جديدة زعتر بسعر 12000", "دور على القرفة"
        ),
        "العملاء والقائمة الثابتة" to listOf(
            "ضيف عميل جديد أبو خالد", "غير اسم سامر الى سامر الحلبي", "رقم سامر 0933123456", "احذف العميل خالد",
            "غير اسم مادة سكر الى سكر ناعم", "احذف مادة قرفة من القائمة الثابتة"
        ),
        "ملخصات وعام" to listOf(
            "ملخص اليوم", "ملخص الأسبوع", "افتح ديون أبو سامر", "اضف ملاحظة اتصل بالمورد",
            "ذكرني بكرة العصر أتصل بالمورد", "3 كيس × 18500 كم يصير؟", "تراجع", "كرر"
        )
    )

    /**
     * اقتراحات من البيانات الفعلية فقط (أشخاص عليهم دين، مواد في النواقص، مواد مسعّرة) بدل أمثلة ثابتة.
     * المجموعة التي لا بيانات لها لا تظهر.
     */
    fun dynamicGroups(d: AssistantData): List<Pair<String, List<String>>> {
        val groups = mutableListOf<Pair<String, List<String>>>()

        val debtors = d.persons.filter { it.amount > 0 }.sortedByDescending { it.amount }
        if (debtors.isNotEmpty()) {
            val l = mutableListOf<String>()
            debtors.take(2).forEach { l += "كم على ${it.name}؟" }
            debtors.getOrNull(2)?.let { l += "افتح ديون ${it.name}" }
            debtors.take(2).forEach { l += "سدد ${it.name} الباقي" }
            l += "مجموع الديون"
            if (debtors.size > 1) l += "أكثر 5 مدينين"
            groups += "الديون" to l.distinct()
        }

        val shortages = d.materials.sortedByDescending { it.updatedAt }
        if (shortages.isNotEmpty()) {
            val l = mutableListOf("شو النواقص")
            shortages.take(3).forEach { l += "اشتريت ${it.name}" }
            l += "إيجاد المواد الناقصة"
            groups += "النواقص" to l.distinct()
        }

        val priced = d.prices.keys.filter { it.isNotBlank() }.take(4)
        val priceNames = priced.ifEmpty { d.catalog.map { it.name }.filter { it.isNotBlank() }.take(4) }
        if (priceNames.isNotEmpty()) {
            groups += "الأسعار" to priceNames.map { n -> "كم سعر $n؟" }
        }

        if (debtors.isNotEmpty() || shortages.isNotEmpty()) {
            groups += "ملخصات وعام" to listOf("ملخص اليوم", "ملخص الأسبوع")
        }
        return groups
    }
}
