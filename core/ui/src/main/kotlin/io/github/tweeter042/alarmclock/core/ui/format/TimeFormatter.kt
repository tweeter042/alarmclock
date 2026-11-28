package io.github.tweeter042.alarmclock.core.ui.format

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import io.github.tweeter042.alarmclock.core.model.format.LocalizedFormat
import io.github.tweeter042.alarmclock.core.model.time.AlarmTime
import io.github.tweeter042.alarmclock.core.ui.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import io.github.tweeter042.alarmclock.core.designsystem.R as DsR

/** A time split for large displays: [digits] ("7:30" / "07:30") and the optional AM/PM [amPm]. */
@Immutable
data class TimeParts(val digits: String, val amPm: String?)

/**
 * Formats times of day and dates the way the user asked for in Settings › Language & region:
 * 12/24-hour clock, Arabic or Thai numerals (๐–๙), Gregorian or Buddhist year, in the app's
 * language. Pure: create it with [UiFormatters] (or `rememberUiFormatters()` in Compose).
 *
 * - 24 h: "07:30"; 12 h: "7:30 AM" / "7:30 ก่อนเที่ยง" (noon and midnight are 12).
 * - Short date "Wed, 25 Nov" / "พ. 25 พ.ย." (the year is added when it is not [today]'s year).
 * - Long date "Wednesday, 25 November 2026" / "วันพุธที่ 25 พฤศจิกายน 2569".
 */
@Stable
class TimeFormatter(
    private val strings: FormatStrings,
    val options: FormatOptions,
) {
    val use24h: Boolean get() = options.use24h

    /** Locale for month and weekday names. */
    val locale: Locale = strings.language.formattingLocale()

    /** This formatter with a forced 12- or 24-hour clock (e.g. a picker that overrides the setting). */
    fun with24h(use24h: Boolean): TimeFormatter =
        if (use24h == options.use24h) this else TimeFormatter(strings, options.copy(use24h = use24h))

    // --- Times --------------------------------------------------------------------------------

    /** "07:30" (24 h) or "7:30 AM" (12 h); [second] adds ":05". */
    fun time(hour: Int, minute: Int, second: Int? = null): String {
        val parts = parts(hour, minute, second)
        return if (parts.amPm == null) parts.digits else strings.string(R.string.ui_time_with_am_pm, parts.digits, parts.amPm)
    }

    fun time(time: LocalTime, showSeconds: Boolean = false): String =
        time(time.hour, time.minute, if (showSeconds) time.second else null)

    fun time(time: AlarmTime): String = time(time.hour, time.minute)

    /** Time of day of [epochMillis] in [zone]. */
    fun time(epochMillis: Long, zone: ZoneId, showSeconds: Boolean = false): String =
        time(Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime(), showSeconds)

    /** The time split into digits and AM/PM (null in 24-hour mode), numerals applied. */
    fun parts(hour: Int, minute: Int, second: Int? = null): TimeParts {
        require(hour in 0..23) { "hour out of range: $hour" }
        require(minute in 0..59) { "minute out of range: $minute" }
        val mm = two(minute)
        val ss = second?.let { ":" + two(it.coerceIn(0, 59)) } ?: ""
        return if (use24h) {
            TimeParts(localize("${two(hour)}:$mm$ss"), null)
        } else {
            TimeParts(localize("${hour12(hour)}:$mm$ss"), amPm(hour))
        }
    }

    /** "AM" / "PM" ("ก่อนเที่ยง" / "หลังเที่ยง") for a 0–23 [hour]. */
    fun amPm(hour: Int): String = strings.string(if (hour < 12) DsR.string.ds_am else DsR.string.ds_pm)

    /** Hour label as shown on a wheel: "07" (24 h) or "7" (12 h). */
    fun hourLabel(hour: Int): String = localize(if (use24h) two(hour) else hour12(hour).toString())

    /** Two-digit minute (or second) label: "05". */
    fun minuteLabel(minute: Int): String = localize(two(minute))

    // --- Dates --------------------------------------------------------------------------------

    /** "Wed, 25 Nov"; with the year when it differs from [today]'s (or always when [today] is null). */
    fun dateShort(date: LocalDate, today: LocalDate? = null): String =
        format(date, if (today != null && date.year == today.year) R.string.ui_pattern_date_short else R.string.ui_pattern_date_short_year)

    /** "Wednesday, 25 November 2026" / "วันพุธที่ 25 พฤศจิกายน 2569". */
    fun dateLong(date: LocalDate, withYear: Boolean = true): String =
        format(date, if (withYear) R.string.ui_pattern_date_long else R.string.ui_pattern_date_long_no_year)

    /** "25 Nov"; with the year when it differs from [today]'s (or always when [today] is null). */
    fun dayMonth(date: LocalDate, today: LocalDate? = null): String =
        format(date, if (today != null && date.year == today.year) R.string.ui_pattern_day_month else R.string.ui_pattern_day_month_year)

    /** "November 2026" / "พฤศจิกายน 2569". */
    fun monthYear(date: LocalDate): String = format(date, R.string.ui_pattern_month_year)

    /** A year in the user's style and numerals: 2026 → "2026" or "2569" (Buddhist). */
    fun year(gregorianYear: Int): String = localize(LocalizedFormat.year(gregorianYear, options.yearStyle).toString())

    /**
     * "Today", "Tomorrow", "Yesterday", the weekday name for the rest of the coming week
     * ("Wednesday"), otherwise [dateShort].
     */
    fun relativeDay(date: LocalDate, today: LocalDate): String {
        val days = ChronoUnit.DAYS.between(today, date)
        return when {
            days == 0L -> strings.string(R.string.ui_today)
            days == 1L -> strings.string(R.string.ui_tomorrow)
            days == -1L -> strings.string(R.string.ui_yesterday)
            days in 2..6 -> strings.string(dayFullRes(date.dayOfWeek.value))
            else -> dateShort(date, today)
        }
    }

    /** "Tomorrow at 7:00 AM" / "พรุ่งนี้ เวลา 07:00" for [epochMillis], relative to [nowMillis]. */
    fun dayAndTime(epochMillis: Long, zone: ZoneId, nowMillis: Long): String {
        val at = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return strings.string(R.string.ui_day_at_time, relativeDay(at.toLocalDate(), today), time(at.toLocalTime()))
    }

    // --- Numbers ------------------------------------------------------------------------------

    /** An integer with the user's numerals. */
    fun number(value: Long): String = localize(value.toString())

    fun number(value: Int): String = localize(value.toString())

    /** Converts ASCII digits in [text] to the user's numerals. */
    fun localize(text: String): String = LocalizedFormat.digits(text, options.numerals)

    private fun format(date: LocalDate, patternRes: Int): String =
        localize(DatePatterns.format(date, strings.string(patternRes), locale, options.yearStyle))

    private fun two(value: Int): String = value.toString().padStart(2, '0')

    private fun hour12(hour: Int): Int = (hour % 12).let { if (it == 0) 12 else it }
}

/** Full weekday name resource ("Monday" / "วันจันทร์") for an ISO day (1 = Monday). */
internal fun dayFullRes(isoDay: Int): Int = when (isoDay) {
    1 -> DsR.string.ds_day_full_1
    2 -> DsR.string.ds_day_full_2
    3 -> DsR.string.ds_day_full_3
    4 -> DsR.string.ds_day_full_4
    5 -> DsR.string.ds_day_full_5
    6 -> DsR.string.ds_day_full_6
    else -> DsR.string.ds_day_full_7
}

/** Narrow weekday name resource ("M" / "จ") for an ISO day. */
internal fun dayNarrowRes(isoDay: Int): Int = when (isoDay) {
    1 -> DsR.string.ds_day_narrow_1
    2 -> DsR.string.ds_day_narrow_2
    3 -> DsR.string.ds_day_narrow_3
    4 -> DsR.string.ds_day_narrow_4
    5 -> DsR.string.ds_day_narrow_5
    6 -> DsR.string.ds_day_narrow_6
    else -> DsR.string.ds_day_narrow_7
}

/** Short weekday name resource ("Mon" / "จ.") for an ISO day. */
internal fun dayShortRes(isoDay: Int): Int = when (isoDay) {
    1 -> R.string.ui_day_short_1
    2 -> R.string.ui_day_short_2
    3 -> R.string.ui_day_short_3
    4 -> R.string.ui_day_short_4
    5 -> R.string.ui_day_short_5
    6 -> R.string.ui_day_short_6
    else -> R.string.ui_day_short_7
}
