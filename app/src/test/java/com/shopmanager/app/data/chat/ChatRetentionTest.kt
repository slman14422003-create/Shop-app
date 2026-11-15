package com.shopmanager.app.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRetentionTest {

    private val now = 1_800_000_000_000L
    private val day = 86_400_000L
    private val hour = 3_600_000L

    @Test fun cutoff() {
        assertEquals(now - 30 * day, ChatRetention.cutoffMs(now, 30))
        assertEquals(now - 90 * day, ChatRetention.cutoffMs(now, 90))
        assertEquals(0L, ChatRetention.cutoffMs(now, 0))
        assertEquals(0L, ChatRetention.cutoffMs(now, 29))
    }

    @Test fun shouldDelete() {
        val cutoff = ChatRetention.cutoffMs(now, 30)
        assertTrue(ChatRetention.shouldDelete(cutoff - 1, cutoff, false))
        assertFalse(ChatRetention.shouldDelete(cutoff, cutoff, false))
        assertFalse(ChatRetention.shouldDelete(cutoff - 1, cutoff, true))
        assertFalse(ChatRetention.shouldDelete(cutoff - 1, 0L, false))
        assertFalse(ChatRetention.shouldDelete(0L, cutoff, false))
    }

    @Test fun due() {
        assertTrue(ChatRetention.isDue(0L, now))
        assertFalse(ChatRetention.isDue(now - hour, now))
        assertTrue(ChatRetention.isDue(now - 13 * hour, now))
        assertTrue(ChatRetention.isDue(now + hour, now))
    }

    @Test fun optionsCycle() {
        assertEquals(30, ChatRetention.nextOption(0))
        assertEquals(0, ChatRetention.nextOption(180))
        assertEquals("متوقف", ChatRetention.label(0))
        assertEquals("أقدم من 90 يوماً", ChatRetention.label(90))
    }
}
