package com.shopmanager.app.assistant.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

object RobotListenMath {

    const val RING_COUNT_RICH = 3
    const val RING_COUNT_LIGHT = 2
    const val BAR_COUNT = 5

    fun normalizeLevel(raw: Float): Float = (raw * 3f).coerceIn(0f, 1f)

    fun smooth(current: Float, target: Float, factor: Float = 0.45f): Float =
        current + (target - current) * factor.coerceIn(0f, 1f)

    fun ringProgress(wave: Float, index: Int, count: Int): Float {
        val p = (wave + index.toFloat() / count.coerceAtLeast(1)) % 1f
        return if (p < 0f) p + 1f else p
    }

    fun ringInflate(progress: Float): Float = 0.02f + 0.15f * progress.coerceIn(0f, 1f)

    fun ringAlpha(progress: Float, level: Float): Float =
        ((1f - progress.coerceIn(0f, 1f)) * (0.22f + 0.5f * level.coerceIn(0f, 1f))).coerceIn(0f, 1f)

    fun barHeight(index: Int, wave: Float, level: Float): Float {
        val l = level.coerceIn(0f, 1f).coerceAtLeast(0.22f)
        val s = abs(sin(wave * 2f * PI.toFloat() * 2f + index * 0.9f))
        return 0.012f + 0.05f * l * (0.35f + 0.65f * s)
    }

    fun earArcAlpha(wave: Float, index: Int, level: Float): Float {
        val s = 0.5f + 0.5f * sin(wave * 2f * PI.toFloat() * 2f - index * 1.6f)
        return ((0.35f + 0.5f * level.coerceIn(0f, 1f)) * s).coerceIn(0f, 1f)
    }
}
