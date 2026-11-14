package com.shopmanager.app.data.chat

object ChatRetention {

    val OPTIONS_DAYS = listOf(0, 30, 90, 180)
    const val MIN_DAYS = 30
    private const val DAY_MS = 86_400_000L
    private const val CHECK_INTERVAL_MS = 12L * 60 * 60 * 1000

    fun cutoffMs(nowMs: Long, days: Int): Long =
        if (days < MIN_DAYS) 0L else nowMs - days * DAY_MS

    fun isDue(lastRunMs: Long, nowMs: Long): Boolean =
        lastRunMs <= 0L || nowMs - lastRunMs >= CHECK_INTERVAL_MS || nowMs < lastRunMs

    fun shouldDelete(createdAtMs: Long, cutoffMs: Long, protectedMessage: Boolean): Boolean =
        cutoffMs > 0L && createdAtMs > 0L && createdAtMs < cutoffMs && !protectedMessage

    fun label(days: Int): String = if (days < MIN_DAYS) "متوقف" else "أقدم من $days يوماً"

    fun nextOption(days: Int): Int {
        val i = OPTIONS_DAYS.indexOf(days)
        return OPTIONS_DAYS[(i + 1).mod(OPTIONS_DAYS.size)]
    }
}
