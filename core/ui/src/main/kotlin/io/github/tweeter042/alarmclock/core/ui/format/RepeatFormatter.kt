package io.github.tweeter042.alarmclock.core.ui.format

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import io.github.tweeter042.alarmclock.core.model.alarm.RepeatRule
import io.github.tweeter042.alarmclock.core.model.time.AlarmTime
import io.github.tweeter042.alarmclock.core.model.time.IsoDays
import io.github.tweeter042.alarmclock.core.ui.R
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * One-line summaries of a [RepeatRule], as shown under an alarm's time:
 *
 * | Rule | English | Thai |
 * |---|---|---|
 * | Once, no date | "Today" / "Tomorrow" (or "Once" without a time) | "วันนี้" / "พรุ่งนี้" ("ครั้งเดียว") |
 * | Once on a date | "Tomorrow", "Wed, 25 Nov", "Mon, 4 Jan 2027" | "พรุ่งนี้", "พ. 25 พ.ย.", "จ. 4 ม.ค. 2570" |
 * | Weekly | "Every day", "Weekdays", "Weekends", "Every Monday", "Every day except Sunday", "Mon, Wed, Fri" | "ทุกวัน", "วันทำงาน", "เสาร์–อาทิตย์", "ทุกวันจันทร์", "ทุกวัน ยกเว้นวันอาทิตย์", "จ. พ. ศ." |
 * | Rotation | "4 on / 2 off from 25 Nov", "3 of 7 days, repeating from 25 Nov" | "ทำ 4 หยุด 2 เริ่ม 25 พ.ย.", "ปลุก 3 จาก 7 วัน วนซ้ำตั้งแต่ 25 พ.ย." |
 * | Specific dates | "25 Nov, 2 Dec, 9 Dec", "25 Nov, 2 Dec and 3 more" | "25 พ.ย. 2 ธ.ค. 9 ธ.ค.", "25 พ.ย. 2 ธ.ค. และอีก 3 วัน" |
 *
 * Weekday lists follow the user's first day of the week. Dates in another year than [now]'s
 * show the year (in the chosen era and numerals).
 */
@Stable
class RepeatFormatter(
    private val strings: FormatStrings,
    private val time: TimeFormatter,
    private val days: DayNames,
) {
    /**
     * @param alarmTime the alarm's time; lets "once" alarms say "Today" or "Tomorrow" and skips
     *   today's date in a date list once the time has passed.
     * @param now the reference time (also decides which dates show a year).
     */
    fun summary(rule: RepeatRule, alarmTime: AlarmTime? = null, now: ZonedDateTime = ZonedDateTime.now()): String = when (rule) {
        is RepeatRule.Once -> once(rule.date, alarmTime, now)
        is RepeatRule.Weekly -> if (rule.days.none { it in 1..7 }) once(null, alarmTime, now) else weekly(rule.days)
        is RepeatRule.Rotation -> rotation(rule, now.toLocalDate())
        is RepeatRule.SpecificDates -> dates(rule.dates, alarmTime, now)
    }

    /** Summary of a weekly rule (ISO days). An empty set gives "Once". */
    fun weekly(isoDays: Set<Int>): String {
        val selected = isoDays.filter { it in 1..7 }.toSet()
        return when {
            selected.isEmpty() -> strings.string(R.string.ui_repeat_once)
            selected.size == 7 -> strings.string(R.string.ui_repeat_every_day)
            selected == IsoDays.WEEKDAYS -> strings.string(R.string.ui_repeat_weekdays)
            selected == IsoDays.WEEKEND -> strings.string(R.string.ui_repeat_weekends)
            selected.size == 1 -> strings.string(R.string.ui_repeat_every_weekday, days.long(selected.first()))
            selected.size == 6 -> strings.string(R.string.ui_repeat_every_day_except, days.long((IsoDays.ALL - selected).first()))
            else -> days.inWeekOrder(selected).joinToString(separator()) { days.short(it) }
        }
    }

    private fun once(date: LocalDate?, alarmTime: AlarmTime?, now: ZonedDateTime): String {
        val today = now.toLocalDate()
        if (date == null) {
            if (alarmTime == null) return strings.string(R.string.ui_repeat_once)
            val ringsToday = alarmTime.toLocalTime().isAfter(now.toLocalTime())
            return strings.string(if (ringsToday) R.string.ui_today else R.string.ui_tomorrow)
        }
        return dateLabel(date, today)
    }

    private fun rotation(rule: RepeatRule.Rotation, today: LocalDate): String {
        val shape = RotationShape.of(rule.pattern)
        return when (shape) {
            RotationShape.Never -> strings.string(R.string.ui_repeat_no_days)
            RotationShape.Always -> strings.string(R.string.ui_repeat_every_day)
            is RotationShape.Block -> strings.string(
                R.string.ui_repeat_rotation,
                shape.on,
                shape.off,
                time.dayMonth(rule.anchorDate.plusDays(shape.startIndex.toLong()), today),
            )
            is RotationShape.Irregular -> strings.string(
                R.string.ui_repeat_rotation_custom,
                shape.on,
                shape.length,
                time.dayMonth(rule.anchorDate, today),
            )
        }.let(time::localize)
    }

    private fun dates(dates: List<LocalDate>, alarmTime: AlarmTime?, now: ZonedDateTime): String {
        if (dates.isEmpty()) return strings.string(R.string.ui_repeat_no_dates)
        val today = now.toLocalDate()
        val todayPassed = alarmTime != null && !alarmTime.toLocalTime().isAfter(now.toLocalTime())
        val upcoming = dates.distinct().sorted().filter { it.isAfter(today) || (it == today && !todayPassed) }
        return when {
            upcoming.isEmpty() -> strings.string(R.string.ui_repeat_no_upcoming_dates)
            upcoming.size == 1 -> dateLabel(upcoming.first(), today)
            upcoming.size <= MAX_LISTED_DATES -> upcoming.joinToString(separator()) { time.dayMonth(it, today) }
            else -> {
                val shown = upcoming.take(MAX_LISTED_DATES - 1).joinToString(separator()) { time.dayMonth(it, today) }
                val more = upcoming.size - (MAX_LISTED_DATES - 1)
                time.localize(strings.plural(R.plurals.ui_repeat_dates_more, more, shown, more))
            }
        }
    }

    /** "Today" / "Tomorrow" / short date (with the year when it is not this year). */
    private fun dateLabel(date: LocalDate, today: LocalDate): String = when (date) {
        today -> strings.string(R.string.ui_today)
        today.plusDays(1) -> strings.string(R.string.ui_tomorrow)
        else -> time.dateShort(date, today)
    }

    private fun separator() = strings.string(R.string.ui_list_separator)

    private companion object {
        const val MAX_LISTED_DATES = 3
    }
}

/** The shape of a rotation pattern, for its summary. Pure and unit tested. */
@Immutable
sealed interface RotationShape {
    /** No ring day at all (empty pattern or all off). */
    data object Never : RotationShape

    /** Every day is a ring day. */
    data object Always : RotationShape

    /**
     * One block of [on] ring days and [off] days off per cycle (cyclically), the block starting
     * at [startIndex] of the pattern: "4 on / 2 off".
     */
    data class Block(val on: Int, val off: Int, val startIndex: Int) : RotationShape

    /** Any other pattern: [on] ring days in a cycle of [length] days. */
    data class Irregular(val on: Int, val length: Int) : RotationShape

    companion object {
        fun of(pattern: List<Boolean>): RotationShape {
            val n = pattern.size
            val on = pattern.count { it }
            if (on == 0) return Never
            if (on == n) return Always
            // Starts of "on" runs, treating the pattern as a cycle.
            val starts = (0 until n).filter { pattern[it] && !pattern[(it - 1 + n) % n] }
            return if (starts.size == 1) Block(on = on, off = n - on, startIndex = starts.first()) else Irregular(on = on, length = n)
        }
    }
}
