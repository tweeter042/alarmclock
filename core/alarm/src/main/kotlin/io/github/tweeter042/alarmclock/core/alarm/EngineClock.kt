package io.github.tweeter042.alarmclock.core.alarm

import java.time.ZoneId

/** Wall clock and time zone used by the engine (replaceable in tests). */
internal interface EngineClock {
    /** Epoch millis. */
    fun now(): Long

    /** The device time zone. Read on every use: it changes when the user travels. */
    fun zone(): ZoneId
}

internal object SystemEngineClock : EngineClock {
    override fun now(): Long = System.currentTimeMillis()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
