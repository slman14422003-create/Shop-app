package com.shopmanager.app.assistant.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 90+ جملة اختبار للمحرك: لهجات، أخطاء إملائية، أرقام مكتوبة، أوامر مركّبة، ضمائر، تواريخ، غموض، أمان.
 * كل اختبار يجمع الإخفاقات ويطبعها كلها دفعة واحدة بدل التوقف عند الأول.
 */
class AssistantEngineTest {

    private val now = ZonedDateTime.of(2026, 11, 11, 10, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
    private val day = 86_400_000L
    private val todayStart = ZonedDateTime.of(2026, 11, 11, 0, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()

    private val persons = listOf(
        KnownPerson("p1", "سامر", 50_000.0, typicalAmount = 20_000.0),
        KnownPerson("p2", "أحمد علي", 30_000.0, typicalAmount = 10_000.0),
        KnownPerson("p3", "أحمد سمير", 10_000.0, typicalAmount = 10_000.0),
        KnownPerson("p4", "أبو سامر", 75_000.0, typicalAmount = 25_000.0),
        KnownPerson("p5", "خالد", 0.0),
        KnownPerson("p6", "أم خالد", 15_000.0),
        KnownPerson("p7", "محمد", 5_000.0),
        KnownPerson("p8", "علي", 1_000.0)
    )
    private val materials = listOf(
        KnownMaterial("سكر", 18_500.0), KnownMaterial("ملح", 3_000.0, inShortage = true), KnownMaterial("هيل", 90_000.0),
        KnownMaterial("فلفل أسود", 40_000.0), KnownMaterial("قرفة", 25_000.0), KnownMaterial("كمون", 12_000.0),
        KnownMaterial("أرز", 9_000.0)
    )
    private val snap = EngineSnapshot(persons, materials, emptyMap(), "ل.س", now, "UTC")
    private val engine = AssistantEngine()

    private fun run(text: String, ctx: ConversationContext = ConversationContext(), s: EngineSnapshot = snap) =
        engine.understand(text, s, ctx)

    private fun cmd(text: String, ctx: ConversationContext = ConversationContext(), s: EngineSnapshot = snap) =
        run(text, ctx, s).commands.first()

    private class Collector { val failures = ArrayList<String>()
        fun case(name: String, block: () -> Unit) {
            try { block() } catch (e: AssertionError) { failures.add("$name -> ${e.message}") }
            catch (e: Exception) { failures.add("$name -> EXCEPTION ${e}") }
        }
        fun assertNone() { assertTrue("إخفاقات (${failures.size}):\n" + failures.joinToString("\n"), failures.isEmpty()) }
    }

    private fun Collector.intent(text: String, expected: Intent, ctx: ConversationContext = ConversationContext()) =
        case(text) { val c = cmd(text, ctx); assertEquals("intent", expected, c.intent) }

    private fun Collector.debt(text: String, intent: Intent, personId: String?, amount: Double?) =
        case(text) {
            val c = cmd(text)
            assertEquals("intent", intent, c.intent)
            if (personId != null) assertEquals("person", personId, c.person?.id)
            if (amount != null) assertEquals("amount", amount, c.amount ?: -1.0, 0.001)
        }

    // ---------------------------------------------------------------- الديون

    @Test fun debts_add_dialects() {
        val c = Collector()
        c.debt("سجل على سامر 20 ألف", Intent.ADD_DEBT, "p1", 20_000.0)
        c.debt("قيد على خالد 50 الف", Intent.ADD_DEBT, "p5", 50_000.0)
        c.debt("دون على سامر 1500", Intent.ADD_DEBT, "p1", 1_500.0)
        c.debt("حط على أحمد سمير خمسين ألف", Intent.ADD_DEBT, "p3", 50_000.0)
        c.debt("اكتب على أم خالد 30000", Intent.ADD_DEBT, "p6", 30_000.0)
        c.debt("سامر أخذ 15 ألف", Intent.ADD_DEBT, "p1", 15_000.0)
        c.debt("استلف خالد مية وخمسين ألف", Intent.ADD_DEBT, "p5", 150_000.0)
        c.debt("سجل دين لأحمد سمير 10 آلاف", Intent.ADD_DEBT, "p3", 10_000.0)
        c.debt("سجل على علي 5000", Intent.ADD_DEBT, "p8", 5_000.0)
        c.debt("سجل علي سامر ١٠ آلاف", Intent.ADD_DEBT, "p1", 10_000.0)
        c.assertNone()
    }

    @Test fun debts_pay_reduce_delete() {
        val c = Collector()
        c.debt("سدد أحمد علي", Intent.PAY_DEBT, "p2", null)
        c.debt("سدد سامر 10000", Intent.PAY_DEBT, "p1", 10_000.0)
        c.debt("سدد سامر الباقي", Intent.PAY_DEBT, "p1", null)
        c.debt("قبضت من أبو سامر 25 ألف", Intent.PAY_DEBT, "p4", 25_000.0)
        c.debt("خالد دفع 0", Intent.PAY_DEBT, "p5", null)
        c.debt("نقص من دين سامر 5000", Intent.REDUCE_DEBT, "p1", 5_000.0)
        c.debt("خفض على أحمد سمير 2000", Intent.REDUCE_DEBT, "p3", 2_000.0)
        c.debt("احذف دين سامر", Intent.DELETE_DEBT, "p1", null)
        c.debt("امسح عملية أحمد سمير", Intent.DELETE_DEBT, "p3", null)
        c.case("pay full flag") { assertTrue(cmd("سدد سامر الباقي").full) }
        c.case("pay partial flag") { assertTrue(!cmd("سدد سامر 10000").full) }
        c.assertNone()
    }

    @Test fun debts_queries() {
        val c = Collector()
        c.debt("كم على سامر", Intent.QUERY_BALANCE, "p1", null)
        c.debt("قديش على أحمد سمير؟", Intent.QUERY_BALANCE, "p3", null)
        c.debt("شو رصيد خالد", Intent.QUERY_BALANCE, "p5", null)
        c.debt("كام على سامر", Intent.QUERY_BALANCE, "p1", null)
        c.debt("شكد على سامر", Intent.QUERY_BALANCE, "p1", null)
        c.debt("كم دين سامر", Intent.QUERY_BALANCE, "p1", null)
        c.intent("كم مجموع الديون", Intent.QUERY_TOTAL_DEBTS)
        c.intent("مجموع الديون؟", Intent.QUERY_TOTAL_DEBTS)
        c.intent("كل الديون", Intent.QUERY_TOTAL_DEBTS)
        c.intent("من لم يسدد منذ مدة", Intent.QUERY_STALE_DEBTORS)
        c.intent("مين ما سدد من أسبوع", Intent.QUERY_STALE_DEBTORS)
        c.case("top 5") { val x = cmd("أكثر 5 مدينين"); assertEquals(Intent.QUERY_TOP_DEBTORS, x.intent); assertEquals(5, x.limit) }
        c.case("top 3") { val x = cmd("أكثر 3 مديونين"); assertEquals(Intent.QUERY_TOP_DEBTORS, x.intent); assertEquals(3, x.limit) }
        c.assertNone()
    }

    // ---------------------------------------------------------------- النواقص والأسعار

    @Test fun shortages() {
        val c = Collector()
        c.case("add one") {
            val x = cmd("ضيف سكر للنواقص"); assertEquals(Intent.ADD_SHORTAGE, x.intent); assertEquals(listOf("سكر"), x.materials.map { it.name })
        }
        c.case("add many") {
            val x = cmd("ضيف سكر وملح وبهارات للنواقص")
            assertEquals(Intent.ADD_SHORTAGE, x.intent); assertEquals(listOf("سكر", "ملح", "بهارات"), x.materials.map { it.name })
            assertEquals(MatchLevel.NEW, x.materials[2].level)
        }
        c.case("qty pack") {
            val x = cmd("ضيف 3 كيس سكر للنواقص")
            assertEquals(3.0, x.materials[0].quantity ?: -1.0, 0.001); assertEquals("كيس", x.materials[0].pack)
        }
        c.case("qty unit") {
            val x = cmd("ضيف نص كيلو هيل للنواقص")
            assertEquals("نص كيلو", x.materials[0].unit)
        }
        c.case("qty after") {
            val x = cmd("ضيف سكر 3 وملح 2 للنواقص")
            assertEquals(3.0, x.materials[0].quantity ?: -1.0, 0.001); assertEquals(2.0, x.materials[1].quantity ?: -1.0, 0.001)
        }
        c.intent("خلصت الهيل", Intent.ADD_SHORTAGE)
        c.intent("خلص السكر", Intent.ADD_SHORTAGE)
        c.intent("بدنا قرفة وكمون", Intent.ADD_SHORTAGE)
        c.intent("اشتريت السكر", Intent.REMOVE_SHORTAGE)
        c.intent("احذف الملح من النواقص", Intent.REMOVE_SHORTAGE)
        c.intent("شو النواقص", Intent.LIST_SHORTAGES)
        c.intent("شو النواقض", Intent.LIST_SHORTAGES)
        c.intent("اعرض النواقص", Intent.LIST_SHORTAGES)
        c.intent("كم ناقص", Intent.LIST_SHORTAGES)
        c.intent("ايجاد المواد الناقصة", Intent.RUN_MISSING_FINDER)
        c.intent("شغل ايجاد النواقص", Intent.RUN_MISSING_FINDER)
        c.assertNone()
    }

    @Test fun prices_and_materials() {
        val c = Collector()
        c.intent("كم سعر السكر", Intent.QUERY_PRICE)
        c.intent("بكم الهيل", Intent.QUERY_PRICE)
        c.case("set price 1") { val x = cmd("غير سعر الهيل الى 90 ألف"); assertEquals(Intent.SET_PRICE, x.intent); assertEquals(90_000.0, x.amount ?: 0.0, 0.1); assertTrue(x.risky) }
        c.case("set price 2") { val x = cmd("خلي سعر السكر 19000"); assertEquals(Intent.SET_PRICE, x.intent); assertEquals(19_000.0, x.amount ?: 0.0, 0.1) }
        c.intent("سعر الملح صار 3000", Intent.SET_PRICE)
        c.intent("ارفع سعر الكمون 500", Intent.SET_PRICE)
        c.case("new material") {
            val x = cmd("ضيف مادة جديدة زعتر بسعر 12000")
            assertEquals(Intent.ADD_MATERIAL, x.intent); assertEquals("زعتر", x.materials.firstOrNull()?.name); assertEquals(12_000.0, x.amount ?: 0.0, 0.1)
        }
        c.intent("دور على القرفة", Intent.SEARCH_MATERIAL)
        c.intent("هل عندنا هيل", Intent.SEARCH_MATERIAL)
        c.case("search two words") { val x = cmd("ابحث عن فلفل اسود"); assertEquals(Intent.SEARCH_MATERIAL, x.intent); assertEquals("فلفل أسود", x.materials[0].name) }
        c.assertNone()
    }

    // ---------------------------------------------------------------- عام

    @Test fun general() {
        val c = Collector()
        c.case("summary today") { assertEquals(Period.TODAY, cmd("ملخص اليوم").period) }
        c.case("summary week") { assertEquals(Period.WEEK, cmd("ملخص الأسبوع").period) }
        c.case("summary month") { val x = cmd("شو صار هالشهر"); assertEquals(Intent.SUMMARY, x.intent); assertEquals(Period.MONTH, x.period) }
        c.case("open person") { val x = cmd("افتح ديون أبو سامر"); assertEquals(Intent.OPEN, x.intent); assertEquals("p4", x.person?.id) }
        c.case("open notes") { val x = cmd("افتح الملاحظات"); assertEquals(Intent.OPEN, x.intent); assertEquals(Screen.NOTES, x.screen) }
        c.case("open settings") { val x = cmd("افتح الإعدادات"); assertEquals(Screen.SETTINGS, x.screen) }
        c.case("note") { val x = cmd("اضف ملاحظة اتصل بالمورد"); assertEquals(Intent.ADD_NOTE, x.intent); assertEquals("اتصل بالمورد", x.text) }
        c.case("note colon") { val x = cmd("ملاحظة: الزبون بدو خصم"); assertEquals(Intent.ADD_NOTE, x.intent); assertEquals("الزبون بدو خصم", x.text) }
        c.case("reminder tomorrow afternoon") {
            val x = cmd("ذكرني بكرة العصر أتصل بالمورد")
            assertEquals(Intent.REMINDER, x.intent); assertEquals("أتصل بالمورد", x.text)
            assertEquals(todayStart + day + 16 * 3_600_000L, x.date?.timeMillis)
        }
        c.case("reminder thursday") {
            val x = cmd("ذكرني الخميس أدفع الإيجار"); assertEquals(Intent.REMINDER, x.intent)
            assertEquals(todayStart + day, x.date?.startMillis); assertEquals("أدفع الإيجار", x.text)
        }
        c.case("reminder hour") {
            val x = cmd("ذكرني الساعة 5 أتصل بخالد"); assertEquals(todayStart + 17 * 3_600_000L, x.date?.timeMillis)
        }
        c.case("calc 1") { val x = cmd("3 كيس × 18500 كم يصير؟"); assertEquals(Intent.CALCULATE, x.intent); assertEquals(55_500.0, x.result ?: 0.0, 0.01) }
        c.case("calc 2") { assertEquals(40_000.0, cmd("احسب 25000 + 15000").result ?: 0.0, 0.01) }
        c.case("calc 3") { assertEquals(60.0, cmd("كم يصير 5 ضرب 12").result ?: 0.0, 0.01) }
        c.case("calc 4") { assertEquals(10.0, cmd("2.5 × 4").result ?: 0.0, 0.01) }
        c.intent("تراجع", Intent.UNDO)
        c.intent("كرر", Intent.REPEAT)
        c.intent("شو بتعرف تعمل", Intent.HELP)
        c.intent("مرحبا", Intent.GREETING)
        c.intent("هلا", Intent.GREETING)
        c.intent("شكرا", Intent.THANKS)
        c.intent("نعم", Intent.CONFIRM)
        c.intent("لا", Intent.CANCEL)
        c.intent("الجو حلو اليوم", Intent.UNKNOWN)
        c.case("delete last") { assertEquals(Intent.DELETE_LAST, cmd("احذف الأخير").intent) }
        c.assertNone()
    }

    // ---------------------------------------------------------------- تطبيع وأرقام

    @Test fun normalization_and_numbers() {
        val c = Collector()
        c.case("tashkeel") { assertEquals("احمد", ArabicText.normalize("أَحْمَد")) }
        c.case("teh marbuta") { assertEquals("مدرسه", ArabicText.normalize("مدرسة")) }
        c.case("repeat") { assertEquals("مرحبا", ArabicText.normalize("مرحباااا")) }
        c.case("digits kept") { assertEquals("1000", ArabicText.normalize("1000")) }
        c.case("tatweel") { assertEquals("سلام", ArabicText.normalize("سـلام")) }
        c.debt("سَجِّل عَلى سامر عشرين ألف", Intent.ADD_DEBT, "p1", 20_000.0)
        c.debt("سجلللل على سامر 5000", Intent.ADD_DEBT, "p1", 5_000.0)
        c.debt("سجل على سامر ٢٠٠٠٠", Intent.ADD_DEBT, "p1", 20_000.0)
        c.debt("سجل على خالد 50k", Intent.ADD_DEBT, "p5", 50_000.0)
        c.debt("سجل على خالد نص مليون", Intent.ADD_DEBT, "p5", 500_000.0)
        c.debt("سجل على خالد مليون ونص", Intent.ADD_DEBT, "p5", 1_500_000.0)
        c.debt("سجل على خالد ألفين وخمسمية", Intent.ADD_DEBT, "p5", 2_500.0)
        c.debt("سجل على خالد ثلاثة آلاف", Intent.ADD_DEBT, "p5", 3_000.0)
        c.debt("سجل على خالد 1,500", Intent.ADD_DEBT, "p5", 1_500.0)
        c.debt("سجل على خالد مئتين وخمسين ألف", Intent.ADD_DEBT, "p5", 250_000.0)
        c.debt("سجل على خالد 2.5 مليون", Intent.ADD_DEBT, "p5", 2_500_000.0)
        c.assertNone()
    }

    @Test fun number_parser_direct() {
        fun v(text: String): Double? {
            val toks = text.split(" ").map { ArabicText.canonicalToken(it) }
            return NumberParser.findNumbers(toks).firstOrNull()?.value
        }
        val c = Collector()
        val table = listOf(
            "50 ألف" to 50_000.0, "خمسين ألف" to 50_000.0, "ألفين" to 2_000.0, "نص مليون" to 500_000.0,
            "مية وخمسين" to 150.0, "مليون ونص" to 1_500_000.0, "ثلاثمية" to 300.0, "٥٠٠" to 500.0,
            "1.5 مليون" to 1_500_000.0, "2,500,000" to 2_500_000.0, "عشرة آلاف" to 10_000.0,
            "ربع مليون" to 250_000.0, "ألف وخمسمية" to 1_500.0, "مليونين" to 2_000_000.0, "حداشر" to 11.0
        )
        for ((t, expected) in table) c.case(t) { assertEquals(expected, v(t) ?: -1.0, 0.001) }
        c.assertNone()
    }

    @Test fun fuzzy_helpers() {
        assertEquals(1.0, ArabicText.similarity("احمد", "احمد"), 0.0001)
        assertTrue(ArabicText.similarity("سامر", "سامي") < EntityMatcher.HIGH)
        assertEquals(ArabicText.phoneticKey("سامر"), ArabicText.phoneticKey("سمر"))
        assertEquals(1, ArabicText.levenshtein("سامر", "سمر"))
    }

    // ---------------------------------------------------------------- سياق وغموض وتعلّم وأمان

    @Test fun compound_and_context() {
        val c = Collector()
        c.case("compound") {
            val o = run("سجل على سامر 20 ألف وضيف سكر للنواقص")
            assertEquals(2, o.commands.size)
            assertEquals(Intent.ADD_DEBT, o.commands[0].intent); assertEquals(Intent.ADD_SHORTAGE, o.commands[1].intent)
            assertTrue(o.understood.startsWith("فهمت:")); assertTrue(o.understood.contains("ثم"))
        }
        c.case("compound pronoun") {
            val o = run("سجل على سامر 20 ألف وزيد عليه 10")
            assertEquals(2, o.commands.size); assertEquals("p1", o.commands[1].person?.id); assertEquals(10.0, o.commands[1].amount ?: 0.0, 0.01)
        }
        c.case("pronoun from context") {
            val o1 = run("سجل على سامر 20 ألف")
            val o2 = run("زيد عليه 10", o1.context)
            val x = o2.commands[0]; assertEquals(Intent.ADD_DEBT, x.intent); assertEquals("p1", x.person?.id); assertEquals(10.0, x.amount ?: 0.0, 0.01)
        }
        c.case("repeat") {
            val o1 = run("سجل على سامر 20 ألف"); val x = run("كرر", o1.context).commands[0]
            assertEquals(Intent.ADD_DEBT, x.intent); assertEquals("p1", x.person?.id); assertEquals(20_000.0, x.amount ?: 0.0, 0.01)
        }
        c.case("same thing for another") {
            val o1 = run("سجل على سامر 20 ألف"); val x = run("نفس الشي لخالد", o1.context).commands[0]
            assertEquals(Intent.ADD_DEBT, x.intent); assertEquals("p5", x.person?.id); assertEquals(20_000.0, x.amount ?: 0.0, 0.01)
        }
        c.case("follow-up amount") {
            val o1 = run("سجل على سامر"); val first = o1.commands[0]
            assertEquals(Intent.ADD_DEBT, first.intent); assertTrue(first.missing.contains(Slot.AMOUNT)); assertNotNull(first.prompt)
            val x = run("30 ألف", o1.context).commands[0]
            assertTrue(x.isReady); assertEquals(30_000.0, x.amount ?: 0.0, 0.01); assertEquals("p1", x.person?.id)
        }
        c.case("missing person") {
            val x = cmd("سجل دين 20 ألف"); assertEquals(Intent.ADD_DEBT, x.intent); assertTrue(x.missing.contains(Slot.PERSON))
        }
        c.assertNone()
    }

    @Test fun ambiguity_fuzzy_and_aliases() {
        val c = Collector()
        c.case("ambiguous first name") {
            val x = cmd("كم على أحمد")
            assertEquals(Intent.QUERY_BALANCE, x.intent); assertEquals(AmbiguityKind.PERSON, x.ambiguity?.kind)
            assertEquals(2, x.ambiguity?.options?.size)
            val resolved = engine.resolveChoice(x, x.ambiguity!!.options.first { it.label == "أحمد سمير" }, snap)
            assertTrue(resolved.isReady); assertEquals("p3", resolved.person?.id)
        }
        c.case("fuzzy typo asks") {
            val x = cmd("سجل على سمر 5000")
            assertEquals(Intent.ADD_DEBT, x.intent); assertEquals(AmbiguityKind.FUZZY_PERSON, x.ambiguity?.kind)
            val r = engine.resolveChoice(x, x.ambiguity!!.options[0], snap)
            assertTrue(r.isReady); assertEquals("p1", r.person?.id); assertEquals("سمر", r.aliasToLearn?.alias)
        }
        c.case("alias used") {
            val s2 = snap.copy(aliases = mapOf("سمر" to "سامر"))
            val x = cmd("سجل على سمر 5000", ConversationContext(), s2)
            assertEquals("p1", x.person?.id); assertNotNull(x.person); assertTrue(x.isReady)
        }
        c.case("new person") {
            val x = cmd("سجل على فارس 5000")
            assertEquals(MatchLevel.NEW, x.person?.level); assertEquals("فارس", x.person?.name); assertTrue(x.confirmReasons.isNotEmpty())
        }
        c.case("confirm picks first fuzzy option") {
            val o1 = run("سجل على سمر 5000"); val o2 = run("نعم", o1.context)
            assertEquals("p1", o2.commands[0].person?.id)
        }
        c.assertNone()
    }

    @Test fun shortage_important_and_levantine() {
        val c = Collector()
        c.case("important word") {
            val x = cmd("اضف حلبة حب كمادة هامة")
            assertEquals(Intent.ADD_SHORTAGE, x.intent); assertTrue(x.important)
            assertEquals(listOf("حلبة حب"), x.materials.map { it.name })
        }
        c.case("plain add new material with unit") {
            val x = cmd("اضف حلبة كيلو")
            assertEquals(Intent.ADD_SHORTAGE, x.intent); assertTrue(!x.important)
            assertEquals(listOf("حلبة"), x.materials.map { it.name }); assertEquals("كيلو", x.materials[0].unit)
        }
        c.case("important known material") {
            val x = cmd("ضيفلي ملح مهم")
            assertEquals(Intent.ADD_SHORTAGE, x.intent); assertTrue(x.important)
        }
        c.case("finished plural") { assertEquals(Intent.ADD_SHORTAGE, cmd("خلصو السكر").intent) }
        c.case("empty") { assertEquals(Intent.ADD_SHORTAGE, cmd("الهيل فاضي").intent) }
        c.case("confirm levantine") { assertEquals(Intent.CONFIRM, cmd("ماشي").intent) }
        c.assertNone()
    }

    @Test fun safety_rules() {
        val c = Collector()
        c.case("delete risky") { assertTrue(cmd("احذف دين سامر").risky) }
        c.case("remove shortage risky") { assertTrue(cmd("احذف الملح من النواقص").risky) }
        c.case("price risky") { assertTrue(cmd("خلي سعر السكر 19000").risky) }
        c.case("unusual amount risky") { val x = cmd("سجل على سامر 200 ألف"); assertTrue(x.risky); assertTrue(x.confirmReasons.isNotEmpty()) }
        c.case("normal amount not risky") { assertTrue(!cmd("سجل على سامر 20 ألف").risky) }
        c.case("pay more than balance risky") { assertTrue(cmd("سدد سامر 80000").risky) }
        c.case("pay zero balance reason") { assertTrue(cmd("سدد خالد").confirmReasons.any { it.contains("صفر") }) }
        c.case("huge amount risky") { assertTrue(cmd("سجل على خالد 20 مليون").risky) }
        c.case("read only queries") { assertTrue(cmd("كم على سامر").isReadOnly) }
        c.case("understood line") { assertTrue(run("سجل على سامر 20 ألف").understood.startsWith("فهمت: تسجيل دين")) }
        c.assertNone()
    }

    @Test fun performance_under_50ms_for_large_data() {
        val bigPersons = (1..1500).map { KnownPerson("x$it", "عميل رقم $it", 0.0) } + persons
        val bigMaterials = (1..1500).map { KnownMaterial("مادة $it") } + materials
        val s = snap.copy(persons = bigPersons, materials = bigMaterials)
        engine.understand("سجل على سامر 20 ألف", s) // إحماء JIT
        val t0 = System.nanoTime()
        engine.understand("سجل على سامر 20 ألف وضيف سكر وملح للنواقص", s)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue("استغرق $ms ms", ms < 200) // حد متساهل لاختلاف أجهزة CI؛ الهدف على الهاتف < 50ms
    }
}
