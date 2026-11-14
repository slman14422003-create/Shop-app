package com.shopmanager.app.assistant

import com.shopmanager.app.assistant.engine.AssistantEngine
import com.shopmanager.app.assistant.engine.Command
import com.shopmanager.app.assistant.engine.EngineSnapshot
import com.shopmanager.app.assistant.engine.Intent
import com.shopmanager.app.assistant.engine.KnownMaterial
import com.shopmanager.app.assistant.engine.KnownPerson
import com.shopmanager.app.data.chat.ConversionPlan
import com.shopmanager.app.data.chat.ConvertKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ChatConversionTest {

    private val now = ZonedDateTime.of(2026, 11, 11, 10, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
    private val persons = listOf(
        KnownPerson("p1", "سامر", 50_000.0, typicalAmount = 20_000.0),
        KnownPerson("p2", "أحمد علي", 30_000.0, typicalAmount = 10_000.0),
        KnownPerson("p3", "أحمد سمير", 10_000.0, typicalAmount = 10_000.0),
        KnownPerson("p5", "خالد", 0.0)
    )
    private val materials = listOf(
        KnownMaterial("سكر", 18_500.0), KnownMaterial("ملح", 3_000.0, inShortage = true), KnownMaterial("هيل", 90_000.0)
    )
    private val snap = EngineSnapshot(persons, materials, emptyMap(), "ل.س", now, "UTC")
    private val engine = AssistantEngine()

    private fun plan(kind: ConvertKind, text: String) = ChatConversion.plan(engine, snap, kind, text)

    @Test fun noteIsAlwaysReadyAndImportant() {
        val p = ChatConversion.classifyNote("اتصل بالمورد بكرة")
        assertTrue(p is ConversionPlan.Ready)
        val cmd = (p as ConversionPlan.Ready).handle as Command
        assertEquals(Intent.ADD_NOTE, cmd.intent)
        assertTrue(cmd.important)
        assertEquals("اتصل بالمورد بكرة", cmd.text)
    }

    @Test fun emptyTextIsMissing() {
        assertTrue(plan(ConvertKind.DEBT, "   ") is ConversionPlan.Missing)
    }

    @Test fun nullCommandIsMissing() {
        assertTrue(ChatConversion.classifyDebt(null, "x") is ConversionPlan.Missing)
        assertTrue(ChatConversion.classifyShortage(null, "x") is ConversionPlan.Missing)
    }

    @Test fun existingCustomerWithAmountIsReady() {
        val p = plan(ConvertKind.DEBT, "سامر 20 ألف")
        assertTrue(p is ConversionPlan.Ready)
        val cmd = (p as ConversionPlan.Ready).handle as Command
        assertEquals("p1", cmd.person?.id)
        assertEquals(20_000.0, cmd.amount ?: 0.0, 0.001)
    }

    @Test fun unknownCustomerIsNeverReady() {
        assertTrue(plan(ConvertKind.DEBT, "فارس 5000") !is ConversionPlan.Ready)
    }

    @Test fun closeSpellingOffersSuggestions() {
        val p = plan(ConvertKind.DEBT, "سمر 5000")
        assertTrue(p is ConversionPlan.Suggest)
        assertTrue((p as ConversionPlan.Suggest).options.any { it.label == "سامر" })
    }

    @Test fun existingMaterialIsReady() {
        val p = plan(ConvertKind.SHORTAGE, "سكر")
        assertTrue(p is ConversionPlan.Ready)
        assertTrue((p as ConversionPlan.Ready).summary.contains("سكر"))
    }

    @Test fun unknownMaterialIsNeverReady() {
        assertTrue(plan(ConvertKind.SHORTAGE, "كافيار") !is ConversionPlan.Ready)
    }
}
