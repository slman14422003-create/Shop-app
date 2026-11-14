package com.shopmanager.app.assistant.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RobotListenMathTest {

    @Test
    fun levelIsNormalizedAndClamped() {
        assertEquals(0f, RobotListenMath.normalizeLevel(-1f), 0f)
        assertEquals(1f, RobotListenMath.normalizeLevel(5f), 0f)
        assertEquals(0.3f, RobotListenMath.normalizeLevel(0.1f), 0.0001f)
    }

    @Test
    fun smoothMovesTowardTargetWithoutOvershoot() {
        val next = RobotListenMath.smooth(0f, 1f)
        assertTrue(next > 0f && next < 1f)
        assertEquals(1f, RobotListenMath.smooth(1f, 1f), 0f)
    }

    @Test
    fun ringProgressStaysInUnitRangeAndStaggers() {
        for (i in 0 until 3) {
            val p = RobotListenMath.ringProgress(0.9f, i, 3)
            assertTrue(p >= 0f && p < 1f)
        }
        val a = RobotListenMath.ringProgress(0.2f, 0, 3)
        val b = RobotListenMath.ringProgress(0.2f, 1, 3)
        assertTrue(a != b)
    }

    @Test
    fun ringFadesOutAsItExpands() {
        val start = RobotListenMath.ringAlpha(0.05f, 0.5f)
        val end = RobotListenMath.ringAlpha(0.95f, 0.5f)
        assertTrue(start > end)
        assertTrue(RobotListenMath.ringInflate(1f) > RobotListenMath.ringInflate(0f))
    }

    @Test
    fun louderVoiceMakesTallerBarsAndIdleStillMoves() {
        val quiet = RobotListenMath.barHeight(2, 0.13f, 0f)
        val loud = RobotListenMath.barHeight(2, 0.13f, 1f)
        assertTrue(loud > quiet)
        assertTrue(quiet > 0.012f)
    }

    @Test
    fun earArcAlphaIsBounded() {
        for (i in 0..1) {
            val a = RobotListenMath.earArcAlpha(0.37f, i, 0.8f)
            assertTrue(a >= 0f && a <= 1f)
        }
    }
}
