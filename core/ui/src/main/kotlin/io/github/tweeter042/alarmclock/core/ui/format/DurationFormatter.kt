package io.github.tweeter042.alarmclock.core.ui.format

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import io.github.tweeter042.alarmclock.core.model.format.LocalizedFormat
import io.github.tweeter042.alarmclock.core.model.settings.NumeralStyle
import io.github.tweeter042.alarmclock.core.ui.R

/** Short units for the screen ("7 h 23 min"), long units for screen readers ("7 hours and 23 minutes"). */
enum class DurationStyle { SHORT, LONG }

/** How a duration is rounded to whole minutes (or seconds). */
enum class DurationRounding { UP, DOWN, NEAREST }

/**
 * A duration split into display units. Pure and unit tested.
 *
 * Minute precision ([ofMinutes]) is what the app shows for "rings in …": rounding up matches the
 * arithmetic a person does with the clock on screen (23:36 → 07:00 is "7 h 24 min").
 */
@Immutable
data class DurationParts(val days: Long, val hours: Int, val minutes: Int, val seconds: Int) {

    val totalSeconds: Long get() = ((days * 24 + hours) * 60 + minutes) * 60 + seconds

    val isZero: Boolean get() = totalSeconds == 0L

    companion object {
        private const val MINUTE = 60_000L
        private const val SECOND = 1_000L

        /** Whole minutes (seconds = 0), split into days, hours and minutes. Negative input counts as 0. */
        fun ofMinutes(millis: Long, rounding: DurationRounding): DurationParts {
            val minutes = round(millis.coerceAtLeast(0L), MINUTE, rounding)
            return DurationParts(days = minutes / (24 * 60), hours = (minutes / 60 % 24).toInt(), minutes = (minutes % 60).toInt(), seconds = 0)
        }

        /** Whole seconds split into hours (not days), minutes and seconds. */
        fun ofSeconds(millis: Long, rounding: DurationRounding): DurationParts {
            val seconds = round(millis.coerceAtLeast(0L), SECOND, rounding)
            return DurationParts(days = 0, hours = (seconds / 3600).toInt(), minutes = (seconds / 60 % 60).toInt(), seconds = (seconds % 60).toInt())
        }

        private fun round(value: Long, unit: Long, rounding: DurationRounding): Long = when (rounding) {
            DurationRounding.DOWN -> value / unit
            DurationRounding.UP -> (value + unit - 1) / unit
            DurationRounding.NEAREST -> (value + unit / 2) / unit
        }
    }
}

/**
 * Formats durations and relative times: "7 h 23 min", "in 7 h 23 min", "7 h 23 min ago",
 * "in less than a minute" (Thai: "7 ชม. 23 นาที", "อีก 7 ชม. 23 นาที", "7 ชม. 23 นาทีที่แล้ว",
 * "อีกไม่ถึง 1 นาที"), countdown digits ("05:00", "1:05:09") and timer lengths with seconds.
 *
 * From one day on, minutes are dropped ("in 1 day 3 h").
 */
@Stable
class DurationFormatter(
    private val strings: FormatStrings,
    private val numerals: NumeralStyle,
) {
    /** A length of time, rounded to the nearest minute: "7 h 24 min", "less than a minute", "0 min". */
    fun duration(millis: Long, style: DurationStyle = DurationStyle.SHORT, rounding: DurationRounding = DurationRounding.NEAREST): String {
        val parts = DurationParts.ofMinutes(millis, rounding)
        return when {
            !parts.isZero -> localize(minuteParts(parts, style))
            millis > 0 -> localize(strings.string(R.string.ui_duration_less_than_minute))
            else -> localize(unit(minutesRes(style), 0))
        }
    }

    /** A timer length to the second: "1 h 30 s", "1 min 30 s", "45 s" ("1 hour and 30 seconds"). */
    fun durationWithSeconds(millis: Long, style: DurationStyle = DurationStyle.SHORT): String {
        val p = DurationParts.ofSeconds(millis, DurationRounding.NEAREST)
        val parts = buildList {
            if (p.hours > 0) add(unit(hoursRes(style), p.hours))
            if (p.minutes > 0) add(unit(minutesRes(style), p.minutes))
            if (p.seconds > 0 || isEmpty()) add(unit(secondsRes(style), p.seconds))
        }
        return localize(join(parts, style))
    }

    /** Time left until something happens, rounded up: "in 7 h 24 min", "in less than a minute". */
    fun until(millis: Long, style: DurationStyle = DurationStyle.SHORT): String {
        val parts = DurationParts.ofMinutes(millis, DurationRounding.UP)
        return if (millis < MINUTE || parts.isZero) {
            localize(strings.string(R.string.ui_relative_in_less_than_minute))
        } else {
            localize(strings.string(R.string.ui_relative_in, minuteParts(parts, style)))
        }
    }

    /** Time since something happened, rounded down: "7 h 23 min ago", "just now". */
    fun since(millis: Long, style: DurationStyle = DurationStyle.SHORT): String {
        val parts = DurationParts.ofMinutes(millis, DurationRounding.DOWN)
        return if (parts.isZero) {
            localize(strings.string(R.string.ui_relative_just_now))
        } else {
            localize(strings.string(R.string.ui_relative_ago, minuteParts(parts, style)))
        }
    }

    /**
     * Countdown digits, rounded up to the second so a 5-minute timer starts at "05:00" and shows
     * "00:01" during its last second: "MM:SS" below an hour, "H:MM:SS" from one hour on.
     */
    fun countdown(millis: Long): String {
        val p = DurationParts.ofSeconds(millis, DurationRounding.UP)
        val mmss = "${two(p.minutes)}:${two(p.seconds)}"
        return localize(if (p.hours > 0) "${p.hours}:$mmss" else mmss)
    }

    private fun minuteParts(p: DurationParts, style: DurationStyle): String {
        val parts = buildList {
            if (p.days > 0) {
                add(unit(daysRes(style), p.days.toInt()))
                if (p.hours > 0) add(unit(hoursRes(style), p.hours))
            } else {
                if (p.hours > 0) add(unit(hoursRes(style), p.hours))
                if (p.minutes > 0) add(unit(minutesRes(style), p.minutes))
            }
        }
        return join(parts, style)
    }

    private fun join(parts: List<String>, style: DurationStyle): String = when {
        parts.size <= 1 -> parts.firstOrNull().orEmpty()
        style == DurationStyle.SHORT -> parts.joinToString(" ")
        else -> parts.dropLast(1).joinToString(strings.string(R.string.ui_duration_join)) +
            strings.string(R.string.ui_duration_join_last) + parts.last()
    }

    private fun unit(res: Int, value: Int): String = strings.plural(res, value, value)

    private fun daysRes(style: DurationStyle) = if (style == DurationStyle.SHORT) R.plurals.ui_unit_days_short else R.plurals.ui_unit_days_long
    private fun hoursRes(style: DurationStyle) = if (style == DurationStyle.SHORT) R.plurals.ui_unit_hours_short else R.plurals.ui_unit_hours_long
    private fun minutesRes(style: DurationStyle) = if (style == DurationStyle.SHORT) R.plurals.ui_unit_minutes_short else R.plurals.ui_unit_minutes_long
    private fun secondsRes(style: DurationStyle) = if (style == DurationStyle.SHORT) R.plurals.ui_unit_seconds_short else R.plurals.ui_unit_seconds_long

    private fun localize(text: String) = LocalizedFormat.digits(text, numerals)

    private fun two(value: Int) = value.toString().padStart(2, '0')

    private companion object {
        const val MINUTE = 60_000L
    }
}
