package com.shopmanager.app.data.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSpeedsTest {

    @Test fun cycle() {
        assertEquals(1.25f, PlaybackSpeeds.next(1f), 0.0001f)
        assertEquals(1.5f, PlaybackSpeeds.next(1.25f), 0.0001f)
        assertEquals(2f, PlaybackSpeeds.next(1.5f), 0.0001f)
        assertEquals(1f, PlaybackSpeeds.next(2f), 0.0001f)
        assertEquals(1f, PlaybackSpeeds.next(0.75f), 0.0001f)
    }

    @Test fun labels() {
        assertEquals("1×", PlaybackSpeeds.label(1f))
        assertEquals("1.25×", PlaybackSpeeds.label(1.25f))
        assertEquals("2×", PlaybackSpeeds.label(2f))
    }
}
