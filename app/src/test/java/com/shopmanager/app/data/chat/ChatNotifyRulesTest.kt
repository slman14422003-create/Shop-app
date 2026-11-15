package com.shopmanager.app.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatNotifyRulesTest {

    private val doc = ChatNotifyInput("c1", 1000L, "other", 1, 0L)

    private fun decide(
        seen: Long = 500L,
        initial: Boolean = false,
        open: String? = null,
        enabled: Boolean = true,
        d: ChatNotifyInput = doc
    ) = ChatNotifyRules.decide(seen, initial, "me", open, enabled, d)

    @Test fun normalNewMessageNotifies() {
        val r = decide()
        assertTrue(r.advanceBaseline)
        assertTrue(r.notify)
    }

    @Test fun firstSnapshotOnlySetsBaseline() {
        val r = decide(seen = ChatNotifyRules.NO_BASELINE, initial = true)
        assertTrue(r.advanceBaseline)
        assertFalse(r.notify)
    }

    @Test fun noBaselineButNotInitialNotifies() {
        assertTrue(decide(seen = ChatNotifyRules.NO_BASELINE, initial = false).notify)
    }

    @Test fun alreadySeenIsNeverRepeated() {
        val same = decide(seen = 1000L)
        assertFalse(same.advanceBaseline)
        assertFalse(same.notify)
        assertFalse(decide(seen = 2000L).notify)
    }

    @Test fun ownMessageSkipped() {
        val r = decide(d = doc.copy(lastFrom = "me"))
        assertTrue(r.advanceBaseline)
        assertFalse(r.notify)
    }

    @Test fun readOrClearedOrOpenOrDisabledSkipped() {
        assertFalse(decide(d = doc.copy(myUnread = 0)).notify)
        assertFalse(decide(d = doc.copy(clearedMs = 1000L)).notify)
        assertTrue(decide(d = doc.copy(clearedMs = 500L)).notify)
        assertFalse(decide(open = "c1").notify)
        assertTrue(decide(open = "c2").notify)
        val off = decide(enabled = false)
        assertTrue(off.advanceBaseline)
        assertFalse(off.notify)
    }

    @Test fun bodies() {
        assertEquals("رسالة صوتية", ChatNotifyRules.body("voice", "x"))
        assertEquals("📷 صورة", ChatNotifyRules.body("image", ""))
        assertEquals("📷 سعر", ChatNotifyRules.body("image", " سعر "))
        assertEquals("رسالة جديدة", ChatNotifyRules.body("text", ""))
        assertEquals("hi", ChatNotifyRules.body(null, "hi"))
    }

    @Test fun summaryTitles() {
        assertEquals("رسائل جديدة", ChatNotifyRules.summaryTitle(1, 3))
        assertEquals("7 رسائل من 3 محادثات", ChatNotifyRules.summaryTitle(3, 7))
    }
}
