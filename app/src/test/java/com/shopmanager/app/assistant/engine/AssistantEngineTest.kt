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
            assertEquals(MatchLevel.NEW, x.materials[2].level); assertTrue(!x.isReady)
        }
        c.case("synonym match") {
            val s2 = snap.copy(materials = materials + KnownMaterial("نسكافيه جولد"))
            val x = cmd("اضف كيلو نسكافية", ConversationContext(), s2)
            assertEquals(Intent.ADD_SHORTAGE, x.intent); assertTrue(x.isReady)
            assertEquals("نسكافيه جولد", x.materials[0].name)
        }
        c.case("unknown material not added") {
            val x = cmd("اضف كيلو كافيار")
            assertEquals(Intent.ADD_SHORTAGE, x.intent); assertTrue(!x.isReady)
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
            assertEquals(MatchLevel.NEW, x.person?.level); assertEquals("فارس", x.person?.name); assertTrue(!x.isReady)
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

    @Test fun add_shortage_with_unit_and_important_even_with_similar_person_name() {
        val s = snap.copy(persons = persons + KnownPerson("p9", "كريم", 0.0))
        val c = Collector()
        val texts = listOf(
            "اضف كريمة محلية كيلو هام", "اضف كريمة محلاية كيلو هامه", "سجل كريمة محلاية كيلو هام",
            "ضيف كمون ناعم كيلو هام", "اضف كمون ناعم كيلو هام", "اظف كمون ناعم كيلو هام",
            "ضيف كمون ناعم كيلو هام", "اضف كمون ناعم كلو هام", "اضف كمون ناعم كيلوو هام"
        )
        for (t in texts) c.case(t) {
            val cm = cmd(t, s = s)
            assertEquals("intent", Intent.ADD_SHORTAGE, cm.intent)
            assertTrue("important", cm.important)
            assertTrue("material", cm.materials.isNotEmpty())
        }
        c.case("name kept whole") { assertEquals("كريمة محلية", cmd("اضف كريمة محلية كيلو هام", s = s).materials[0].name) }
        c.case("debt still works") { assertEquals(Intent.ADD_DEBT, cmd("سجل على سامر 20 ألف", s = s).intent) }
        c.assertNone()
    }

    private val rich = EngineSnapshot(
        persons = listOf(
            KnownPerson("p1", "سامر", 50_000.0, typicalAmount = 20_000.0),
            KnownPerson("p2", "أحمد علي", 30_000.0, debtAmounts = listOf(10_000.0, 12_000.0)),
            KnownPerson("p3", "أحمد سمير", 80_000.0, debtAmounts = listOf(75_000.0))
        ),
        materials = listOf(
            KnownMaterial("سكر", 18_500.0), KnownMaterial("ملح", 3_000.0, inShortage = true),
            KnownMaterial("ملح خشن", 3_500.0), KnownMaterial("ملح ناعم", 3_200.0),
            KnownMaterial("هيل", 90_000.0, usualUnit = "نص كيلو", usualQty = 1.0),
            KnownMaterial("بيبسي", 5_000.0), KnownMaterial("نسكافيه", 40_000.0), KnownMaterial("قهوة", 30_000.0)
        ),
        aliases = emptyMap(), currency = "ل.س", nowMillis = now, timeZoneId = "UTC", version = 7L,
        recentOps = listOf(RecentOp("ADD_SHORTAGE", "سكر", 0.0, now - 3_600_000L))
    )

    @Test fun voice_fillers_glued_and_cleaned_text() {
        val c = Collector()
        c.case("fillers removed") {
            val o = run("يا معلم لو سمحت ضيف سكر هلق", s = rich)
            assertEquals(Intent.ADD_SHORTAGE, o.commands[0].intent)
            assertEquals("سكر", o.commands[0].materials[0].name)
            assertTrue(!o.cleanedText.contains("معلم") && !o.cleanedText.contains("سمحت"))
        }
        c.case("glued verb") { assertEquals("سكر", cmd("ضيفسكر", s = rich).materials[0].name) }
        c.case("filler shu before verb") { assertEquals(Intent.ADD_SHORTAGE, cmd("شو ضيف هيل", s = rich).intent) }
        c.assertNone()
    }

    @Test fun foreign_names_and_shami_numbers() {
        val c = Collector()
        c.case("pepsi latin") { assertEquals("بيبسي", cmd("ضيف Pepsi", s = rich).materials[0].name) }
        c.case("nescafe latin") { assertEquals("نسكافيه", cmd("ضيف Nescafe", s = rich).materials[0].name) }
        c.case("ألفين وخمسميه") { assertEquals(2_500.0, cmd("سجل على سامر ألفين وخمسميه", s = rich).amount ?: 0.0, 0.001) }
        c.case("تلاتة تلاف") { assertEquals(3_000.0, cmd("سجل على سامر تلاتة تلاف", s = rich).amount ?: 0.0, 0.001) }
        c.case("اربعتعش الف") { assertEquals(14_000.0, cmd("سجل على سامر اربعتعش الف", s = rich).amount ?: 0.0, 0.001) }
        c.case("ميتين الف") { assertEquals(200_000.0, cmd("سجل على سامر ميتين الف", s = rich).amount ?: 0.0, 0.001) }
        c.case("مليون ونص") { assertEquals(1_500_000.0, cmd("سجل على سامر مليون ونص", s = rich).amount ?: 0.0, 0.001) }
        c.case("نص مليون") { assertEquals(500_000.0, cmd("سجل على سامر نص مليون", s = rich).amount ?: 0.0, 0.001) }
        c.case("qaf as hamza") { assertTrue(ArabicText.similarity("قهوه", "اهوه") >= 0.8) }
        c.case("thaa as seen") { assertTrue(ArabicText.similarity("ثوم", "سوم") >= 0.8) }
        c.case("dhal as zay") { assertTrue(ArabicText.similarity("ذره", "زره") >= 0.8) }
        c.case("dad as dal") { assertTrue(ArabicText.similarity("ضبس", "دبس") >= 0.8) }
        c.assertNone()
    }

    @Test fun speech_ranker_and_weak_words() {
        val c = Collector()
        c.case("rerank by known names") {
            val r = SpeechResult("سجل على ثامر 5000", listOf(SpeechAlt("سجل على سامر 5000", 0.4f), SpeechAlt("سجل على شامر 5000", 0.3f)))
            val ranked = SpeechRanker.rank(r, rich)
            assertTrue(ranked.text, ranked.text.contains("سامر"))
        }
        c.case("phonetic fix") {
            val r = SpeechResult("ضيف اهوه", emptyList())
            assertTrue(SpeechRanker.rank(r, rich).text.contains("قهوة") || SpeechRanker.rank(r, rich).text.contains("قهوه"))
        }
        c.case("weak words listed") {
            val r = SpeechResult("ضيف زنجفيل", emptyList(), listOf(SpeechWord("ضيف", 0.95f), SpeechWord("زنجفيل", 0.3f)))
            assertTrue(SpeechRanker.rank(r, rich).weak.any { it.text == "زنجفيل" })
        }
        c.case("weak alternatives") {
            val alts = SpeechRanker.alternativesFor("سكار", rich)
            assertTrue(alts.contains("سكر"))
        }
        c.assertNone()
    }

    @Test fun voice_choice_words() {
        val c = Collector()
        c.case("first") { assertEquals(VoiceChoiceResult.Option(0), VoiceChoice.parse("الأولى")) }
        c.case("second") { assertEquals(VoiceChoiceResult.Option(1), VoiceChoice.parse("التانية")) }
        c.case("third") { assertEquals(VoiceChoiceResult.Option(2), VoiceChoice.parse("التالتة")) }
        c.case("this") { assertEquals(VoiceChoiceResult.Confirm, VoiceChoice.parse("هي")) }
        c.case("execute") { assertEquals(VoiceChoiceResult.Confirm, VoiceChoice.parse("نفّذ")) }
        c.case("no") { assertEquals(VoiceChoiceResult.Cancel, VoiceChoice.parse("لا")) }
        c.case("undo") { assertEquals(VoiceChoiceResult.Undo, VoiceChoice.parse("تراجع")) }
        c.case("sentence is none") { assertEquals(VoiceChoiceResult.None, VoiceChoice.parse("ضيف سكر وملح")) }
        c.case("choice resolves pending") {
            val o = run("كم على أحمد", s = rich)
            val r = run("التانية", o.context, rich).commands[0]
            assertTrue(r.isReady); assertTrue(r.fromChoice)
            assertEquals(o.commands[0].ambiguity!!.options[1].label, r.person?.name)
        }
        c.assertNone()
    }

    @Test fun list_mode_vocabulary_and_options() {
        val c = Collector()
        c.case("list mode items") {
            val o = engine.understand("سكر، هيل، ملح", rich, ConversationContext(), EngineOptions(listMode = true))
            val x = o.commands[0]
            assertEquals(Intent.ADD_SHORTAGE, x.intent); assertTrue(x.listMode)
            assertEquals(listOf("سكر", "هيل", "ملح"), x.materials.map { it.name })
        }
        c.case("list split") { assertEquals(listOf("سكر", "هيل", "ملح"), ListMode.items("سكر، هيل و ملح")) }
        c.case("vocabulary") {
            val v = Vocabulary.build(rich)
            assertTrue(v.words.contains("سامر") && v.words.contains("هيل") && v.words.contains("ضيف") && v.words.contains("خمسين"))
            assertTrue(v.toGrammarJson().contains("[unk]"))
        }
        c.case("prewarm and cache") {
            engine.prewarm(rich)
            val a = cmd("ضيف سكر", s = rich); val b = cmd("ضيف سكر", s = rich)
            assertEquals(a.materials[0].name, b.materials[0].name)
        }
        c.assertNone()
    }

    @Test fun understanding_quantities_corrections_queries() {
        val c = Collector()
        c.case("multi quantities") {
            val x = cmd("ضيف كيلو سكر ونص كيلو هيل وكيسين ملح", s = rich)
            assertEquals(3, x.materials.size)
            assertEquals("كيلو", x.materials[0].unit)
            assertEquals("نص كيلو", x.materials[1].unit)
            assertEquals(2.0, x.materials[2].quantity ?: 0.0, 0.001)
        }
        c.case("update existing shortage") {
            val x = cmd("ضيف تلاتة كيس ملح", s = rich)
            assertTrue(x.materials[0].inShortage); assertTrue(x.understood.contains("تحديث"))
        }
        c.case("correction") {
            val last = Command(Intent.ADD_SHORTAGE, 9.0, "ضيف ملح", materials = listOf(MaterialRef("ملح", "ملح", 1.0, MatchLevel.EXACT, 1.0, "كيلو")), understood = "x")
            val x = cmd("لا مو هيك، قصدي الخشن", ConversationContext(lastCommand = last), rich)
            assertTrue(x.isCorrection); assertEquals("ملح خشن", x.materials[0].name); assertEquals("كيلو", x.materials[0].unit)
        }
        c.case("bought deletes") { assertEquals(Intent.REMOVE_SHORTAGE, cmd("اشتريت السكر", s = rich).intent) }
        c.case("arrived deletes") { assertEquals(Intent.REMOVE_SHORTAGE, cmd("وصل الهيل", s = rich).intent) }
        c.case("compound debt shortage") {
            val o = run("سجل على سامر 5000 وضيف سكر", s = rich)
            assertEquals(listOf(Intent.ADD_DEBT, Intent.ADD_SHORTAGE), o.commands.map { it.intent })
        }
        c.case("remaining query") { assertEquals(Intent.QUERY_BALANCE, cmd("كم باقي على سامر", s = rich).intent) }
        c.case("shortage today") {
            val x = cmd("شو ناقص اليوم", s = rich)
            assertEquals(Intent.LIST_SHORTAGES, x.intent); assertEquals(Period.TODAY, x.period)
        }
        c.case("top debtors") {
            val x = cmd("أكثر 5 مديونين", s = rich)
            assertEquals(Intent.QUERY_TOP_DEBTORS, x.intent); assertEquals(5, x.limit)
        }
        c.case("pronoun material") {
            val x = cmd("ضيف كمان كيلو منها", ConversationContext(lastMaterial = "هيل"), rich)
            assertEquals(Intent.ADD_SHORTAGE, x.intent); assertEquals("هيل", x.materials[0].name); assertEquals("كيلو", x.materials[0].unit)
        }
        c.case("pronoun person pay rest") {
            val x = cmd("سدد له الباقي", ConversationContext(lastPerson = PersonRef("سامر", "p1", "سامر", 1.0, MatchLevel.EXACT)), rich)
            assertEquals(Intent.PAY_DEBT, x.intent); assertEquals("p1", x.person?.id); assertTrue(x.full)
        }
        c.case("question availability") { assertEquals(Intent.SEARCH_MATERIAL, cmd("عندنا هيل؟", s = rich).intent) }
        c.case("finished means shortage") { assertEquals(Intent.ADD_SHORTAGE, cmd("خلص الهيل", s = rich).intent) }
        c.case("bought means remove") { assertEquals(Intent.REMOVE_SHORTAGE, cmd("جبنا هيل", s = rich).intent) }
        c.assertNone()
    }

    @Test fun habits_and_context_rules() {
        val c = Collector()
        c.case("usual unit filled") {
            val x = cmd("ضيف هيل", s = rich)
            assertEquals("نص كيلو", x.materials[0].unit); assertTrue(x.materials[0].fromHabit)
        }
        c.case("explicit beats habit") {
            val x = cmd("ضيف كيلو هيل", s = rich)
            assertEquals("كيلو", x.materials[0].unit); assertTrue(!x.materials[0].fromHabit)
        }
        c.case("disambiguate by amount") {
            val x = cmd("سجل على أحمد 75000", s = rich)
            assertEquals("p3", x.person?.id); assertTrue(x.ambiguity == null)
        }
        c.case("still ambiguous without hint") { assertTrue(cmd("كم على أحمد", s = rich).ambiguity != null) }
        c.case("large quantity risky") { assertTrue(cmd("ضيف 2000 كيس سكر", s = rich).risky) }
        c.case("price jump risky") { assertTrue(cmd("خلي سعر هيل 900000", s = rich).risky) }
        c.case("duplicate warning") {
            val x = cmd("ضيف سكر", s = rich)
            assertTrue(x.confirmReasons.joinToString().contains("قبل ساعة"))
        }
        c.case("no duplicate warning after window") {
            val x = cmd("ضيف سكر", s = rich.copy(recentOps = listOf(RecentOp("ADD_SHORTAGE", "سكر", 0.0, now - 30 * 3_600_000L))))
            assertTrue(!x.confirmReasons.joinToString().contains("قبل"))
        }
        c.case("shortage only from existing") {
            val x = cmd("ضيف زعفران", s = rich)
            assertTrue(!x.isReady)
        }
        c.case("debt only for existing") {
            val x = cmd("سجل على فارس 5000", s = rich)
            assertTrue(!x.isReady)
        }
        c.assertNone()
    }

    @Test fun habit_book_learning_and_export() {
        val c = Collector()
        val add = { name: String, unit: String?, qty: Double -> Command(Intent.ADD_SHORTAGE, 9.0, "", materials = listOf(MaterialRef(name, name, 1.0, MatchLevel.EXACT, qty, unit))) }
        c.case("usual unit after two") {
            var b = HabitBook()
            b = b.record(add("سكر", "كيلو", 2.0), now)
            assertEquals(null, b.usualUnit("سكر"))
            b = b.record(add("سكر", "كيلو", 2.0), now + 1)
            assertEquals("كيلو", b.usualUnit("سكر")?.first)
            assertEquals(2.0, b.usualQty("سكر") ?: 0.0, 0.001)
        }
        c.case("alias after same choice twice") {
            val b1 = HabitBook().recordChoice("سمر", "سامر")
            assertEquals(null, b1.alias)
            val b2 = b1.book.recordChoice("سمر", "سامر")
            assertEquals("سامر", b2.alias?.canonical)
        }
        c.case("companions") {
            val two = Command(Intent.ADD_SHORTAGE, 9.0, "", materials = listOf(MaterialRef("سكر", "سكر", 1.0, MatchLevel.EXACT), MaterialRef("شاي", "شاي", 1.0, MatchLevel.EXACT)))
            val b = HabitBook().record(two, now).record(two, now + 1)
            assertEquals(listOf("شاي"), b.companions("سكر"))
            assertEquals(emptyList<String>(), b.companions("سكر", setOf("شاي")))
        }
        c.case("recurring") {
            val day = 86_400_000L
            var b = HabitBook()
            for (i in 0 until 4) b = b.record(add("سكر", null, 1.0), now - (40 - i * 10) * day)
            val r = b.recurring(now, emptySet())
            assertTrue(r.isNotEmpty() && r[0].name == "سكر")
            assertTrue(b.recurring(now, setOf("سكر")).isEmpty())
        }
        c.case("quick order by hour") {
            val noonDebt = Command(Intent.ADD_DEBT, 9.0, "")
            var b = HabitBook()
            for (i in 0 until 3) b = b.record(noonDebt, 12 * 3_600_000L - 3 * 3_600_000L + i * 86_400_000L, 3)
            val ordered = b.quickOrder(12, listOf("نواقص" to Intent.LIST_SHORTAGES, "دين" to Intent.ADD_DEBT))
            assertEquals(Intent.ADD_DEBT, ordered[0].second)
        }
        c.case("export import roundtrip") {
            val b = HabitBook().record(add("سكر", "كيلو", 2.0), now).record(add("سكر", "كيلو", 2.0), now + 1)
            val back = HabitBook.fromText(b.toText())
            assertEquals(b.toText(), back.toText())
            val merged = b.merge(back)
            assertEquals(4, merged.usage["سكر"])
        }
        c.case("dictionary export import") {
            val txt = PersonalDictionary.export(mapOf("سمر" to "سامر", "بيبسي" to "Pepsi"))
            val m = PersonalDictionary.import(txt)
            assertEquals("سامر", m["سمر"])
            assertEquals(2, m.size)
        }
        c.case("enrich snapshot") {
            val b = HabitBook().record(add("سكر", "كيلو", 2.0), now).record(add("سكر", "كيلو", 2.0), now + 1)
            val e = b.enrich(rich)
            assertEquals("كيلو", e.materials.first { it.name == "سكر" }.usualUnit)
            assertEquals(2, e.materials.first { it.name == "سكر" }.usage)
        }
        c.assertNone()
    }
}
