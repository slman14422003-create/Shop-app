package com.shopmanager.app.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSearchTest {

    private fun msg(id: String, text: String, type: MessageType = MessageType.TEXT) = ChatMessage(
        id = id, fromUid = "a", type = type, text = text, audioB64 = "", durationMs = 0L,
        wave = emptyList(), createdAtMs = 1L, expireAtMs = 0L, pending = false
    )

    @Test fun plainRange() {
        assertEquals(listOf(0..5), ChatSearch.ranges("السلام عليكم", "السلام"))
    }

    @Test fun diacriticsAreIgnoredAndMappedBack() {
        val r = ChatSearch.ranges("مَرْحَباً بك", "مرحبا")
        assertEquals(1, r.size)
        assertEquals(0, r[0].first)
        assertEquals(7, r[0].last)
    }

    @Test fun alefAndTaMarbutaFolding() {
        assertEquals(listOf(0..3), ChatSearch.ranges("أحمد", "احمد"))
        assertEquals(listOf(0..4), ChatSearch.ranges("مدرسة", "مدرسه"))
    }

    @Test fun arabicIndicDigits() {
        assertEquals(listOf(4..6), ChatSearch.ranges("سعر ٥٠٠", "500"))
    }

    @Test fun multipleAndCaseInsensitive() {
        assertEquals(listOf(0..1, 3..4), ChatSearch.ranges("Aa aa", "aA"))
    }

    @Test fun emptyQueryNoMatches() {
        assertTrue(ChatSearch.ranges("نص", "  ").isEmpty())
        assertTrue(ChatSearch.matchIds(listOf(msg("1", "نص")), "").isEmpty())
    }

    @Test fun matchIdsKeepOrderAndSkipVoice() {
        val list = listOf(
            msg("1", "سكر ناعم"),
            msg("2", "ملح"),
            msg("3", "", MessageType.VOICE),
            msg("4", "وصلت طلبية السكر", MessageType.IMAGE)
        )
        assertEquals(listOf("1", "4"), ChatSearch.matchIds(list, "سكر"))
    }

    @Test fun stepWraps() {
        assertEquals(2, ChatSearch.step(-1, 3, true))
        assertEquals(1, ChatSearch.step(2, 3, true))
        assertEquals(2, ChatSearch.step(0, 3, true))
        assertEquals(0, ChatSearch.step(2, 3, false))
        assertEquals(-1, ChatSearch.step(0, 0, true))
    }
}
