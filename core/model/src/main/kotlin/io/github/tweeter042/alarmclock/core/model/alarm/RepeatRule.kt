@file:UseSerializers(LocalDateSerializer::class)

package io.github.tweeter042.alarmclock.core.model.alarm

import io.github.tweeter042.alarmclock.core.model.util.LocalDateSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.LocalDate

/** When an alarm repeats. See [AlarmScheduleCalculator] for the exact semantics. */
@Serializable
sealed interface RepeatRule {
    /**
     * Rings once. With [date] == null it rings at the next occurrence of the alarm time
     * (today if still ahead, otherwise tomorrow). With a [date] it rings only on that date.
     * One-time alarms are disabled automatically after they ring (dismiss or auto-dismiss).
     */
    @Serializable
    @SerialName("once")
    data class Once(val date: LocalDate? = null) : RepeatRule

    /** Rings on the given ISO days of week (Monday = 1 … Sunday = 7). Empty set behaves like [Once]. */
    @Serializable
    @SerialName("weekly")
    data class Weekly(val days: Set<Int>) : RepeatRule

    /**
     * Shift-work rotation. [pattern] is a repeating cycle of days starting at [anchorDate];
     * `true` = alarm rings that day. Example "4 on, 2 off": [true,true,true,true,false,false].
     */
    @Serializable
    @SerialName("rotation")
    data class Rotation(val anchorDate: LocalDate, val pattern: List<Boolean>) : RepeatRule

    /** Rings only on the listed dates. */
    @Serializable
    @SerialName("dates")
    data class SpecificDates(val dates: List<LocalDate>) : RepeatRule
}

val RepeatRule.isRepeating: Boolean
    get() = when (this) {
        is RepeatRule.Once -> false
        is RepeatRule.Weekly -> days.isNotEmpty()
        is RepeatRule.Rotation -> pattern.any { it }
        is RepeatRule.SpecificDates -> dates.size > 1
    }
