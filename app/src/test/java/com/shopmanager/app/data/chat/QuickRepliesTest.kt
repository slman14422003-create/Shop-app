package com.shopmanager.app.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickRepliesTest {

    @Test fun defaultsWhenNothingStored() {
        assertEquals(QuickReplies.DEFAULTS, QuickReplies.decode(null))
        assertTrue(QuickReplies.DEFAULTS.containsAll(listOf("تمام", "أرسل لك السعر", "وصلت الطلبية")))
    }

    @Test fun emptyStoredMeansNoReplies() {
        assertTrue(QuickReplies.decode("").isEmpty())
    }

    @Test fun roundTrip() {
        val list = listOf("تمام", "بعد ساعة")
        assertEquals(list, QuickReplies.decode(QuickReplies.encode(list)))
    }

    @Test fun cleanTrimsDedupesAndFlattensLines() {
        assertEquals(listOf("تمام", "a b"), QuickReplies.clean(listOf("  تمام  ", "تمام", "", "a\nb")))
    }

    @Test fun limits() {
        val many = (1..20).map { "رد $it" }
        assertEquals(QuickReplies.MAX_COUNT, QuickReplies.clean(many).size)
        assertEquals(QuickReplies.MAX_LEN, QuickReplies.clean(listOf("x".repeat(200)))[0].length)
    }

    @Test fun addUpdateRemove() {
        val base = listOf("أ", "ب")
        assertEquals(listOf("أ", "ب", "ج"), QuickReplies.add(base, "ج"))
        assertEquals(listOf("أ", "د"), QuickReplies.update(base, 1, "د"))
        assertEquals(base, QuickReplies.update(base, 5, "د"))
        assertEquals(listOf("ب"), QuickReplies.remove(base, 0))
        assertEquals(base, QuickReplies.remove(base, 9))
    }
}
