package io.github.tweeter042.alarmclock.core.model.time

import kotlinx.serialization.Serializable
import java.time.LocalTime

/** A wall-clock time of day (no date, no zone). */
@Serializable
data class AlarmTime(val hour: Int, val minute: Int) : Comparable<AlarmTime> {
    init {
        require(hour in 0..23) { "hour out of range: $hour" }
        require(minute in 0..59) { "minute out of range: $minute" }
    }

    val minuteOfDay: Int get() = hour * 60 + minute

    fun toLocalTime(): LocalTime = LocalTime.of(hour, minute)

    /** Adds (or subtracts) minutes, wrapping around midnight. */
    fun plusMinutes(minutes: Int): AlarmTime = fromMinuteOfDay(minuteOfDay + minutes)

    override fun compareTo(other: AlarmTime): Int = minuteOfDay.compareTo(other.minuteOfDay)

    companion object {
        fun of(time: LocalTime): AlarmTime = AlarmTime(time.hour, time.minute)
        fun fromMinuteOfDay(m: Int): AlarmTime {
            val wrapped = Math.floorMod(m, 24 * 60)
            return AlarmTime(wrapped / 60, wrapped % 60)
        }
    }
}

/** ISO day-of-week numbers used across the model: Monday = 1 … Sunday = 7. */
object IsoDays {
    const val MONDAY = 1
    const val TUESDAY = 2
    const val WEDNESDAY = 3
    const val THURSDAY = 4
    const val FRIDAY = 5
    const val SATURDAY = 6
    const val SUNDAY = 7
    val ALL: Set<Int> = (1..7).toSet()
    val WEEKDAYS: Set<Int> = (1..5).toSet()
    val WEEKEND: Set<Int> = setOf(6, 7)
}
