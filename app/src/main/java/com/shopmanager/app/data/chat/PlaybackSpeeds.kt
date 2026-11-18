package com.shopmanager.app.data.chat

object PlaybackSpeeds {

    val STEPS = listOf(1f, 1.25f, 1.5f, 2f)

    fun next(current: Float): Float {
        val i = STEPS.indexOfFirst { it > current + 0.001f }
        return if (i < 0) STEPS.first() else STEPS[i]
    }

    fun label(speed: Float): String =
        if (speed % 1f == 0f) "${speed.toInt()}×" else "${speed}×"
}
