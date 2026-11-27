package io.github.tweeter042.alarmclock.core.model

import io.github.tweeter042.alarmclock.core.model.alarm.Alarm
import io.github.tweeter042.alarmclock.core.model.alarm.AlarmScheduleCalculator
import io.github.tweeter042.alarmclock.core.model.alarm.RepeatRule
import io.github.tweeter042.alarmclock.core.model.alarm.SnoozeConfig
import io.github.tweeter042.alarmclock.core.model.alarm.SnoozeMode
import io.github.tweeter042.alarmclock.core.model.alarm.SnoozePolicy
import io.github.tweeter042.alarmclock.core.model.format.LocalizedFormat
import io.github.tweeter042.alarmclock.core.model.settings.NumeralStyle
import io.github.tweeter042.alarmclock.core.model.settings.YearStyle
import io.github.tweeter042.alarmclock.core.model.time.AlarmTime
import io.github.tweeter042.alarmclock.core.model.time.IsoDays
import io.github.tweeter042.alarmclock.core.model.util.ModelJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class AlarmScheduleCalculatorTest {
    private val bangkok = ZoneId.of("Asia/Bangkok")
    private val berlin = ZoneId.of("Europe/Berlin")

    // Friday 2026-11-27 08:00 Bangkok
    private val now = ZonedDateTime.of(2026, 11, 27, 8, 0, 0, 0, bangkok)

    private fun next(alarm: Alarm, after: ZonedDateTime = now, holidays: Set<LocalDate> = emptySet()) =
        AlarmScheduleCalculator.nextOccurrence(alarm, after, isHoliday = { it in holidays })

    @Test fun onceLaterTodayRingsToday() {
        val r = next(Alarm(time = AlarmTime(9, 30)))
        assertEquals(ZonedDateTime.of(2026, 11, 27, 9, 30, 0, 0, bangkok), r)
    }

    @Test fun oncePastTimeRingsTomorrow() {
        val r = next(Alarm(time = AlarmTime(7, 0)))
        assertEquals(LocalDate.of(2026, 11, 28), r!!.toLocalDate())
    }

    @Test fun exactlyNowIsNotAnOccurrence() {
        val r = next(Alarm(time = AlarmTime(8, 0)))
        assertEquals(LocalDate.of(2026, 11, 28), r!!.toLocalDate())
    }

    @Test fun onceWithPastDateIsNull() {
        assertNull(next(Alarm(repeat = RepeatRule.Once(LocalDate.of(2026, 11, 1)))))
    }

    @Test fun disabledOrGroupDisabledIsNull() {
        assertNull(next(Alarm(enabled = false)))
        assertNull(AlarmScheduleCalculator.nextOccurrence(Alarm(), now, groupEnabled = false))
    }

    @Test fun weekdaysSkipWeekend() {
        // Friday 08:00 now, 07:00 weekday alarm -> next is Monday 30 Nov
        val r = next(Alarm(time = AlarmTime(7, 0), repeat = RepeatRule.Weekly(IsoDays.WEEKDAYS)))
        assertEquals(LocalDate.of(2026, 11, 30), r!!.toLocalDate())
    }

    @Test fun holidaySkipped() {
        val alarm = Alarm(time = AlarmTime(7, 0), repeat = RepeatRule.Weekly(IsoDays.WEEKDAYS), skipHolidays = true)
        val r = next(alarm, holidays = setOf(LocalDate.of(2026, 11, 30)))
        assertEquals(LocalDate.of(2026, 12, 1), r!!.toLocalDate())
        // Not skipped when skipHolidays is false
        assertEquals(LocalDate.of(2026, 11, 30), next(alarm.copy(skipHolidays = false), holidays = setOf(LocalDate.of(2026, 11, 30)))!!.toLocalDate())
    }

    @Test fun skipNextSkipsExactlyOneOccurrence() {
        val alarm = Alarm(time = AlarmTime(9, 0), repeat = RepeatRule.Weekly(IsoDays.ALL))
        val first = next(alarm)!!
        val skipped = alarm.copy(skippedOccurrenceAt = first.toInstant().toEpochMilli())
        assertEquals(first.plusDays(1), next(skipped))
        assertEquals(first, AlarmScheduleCalculator.nextOccurrence(skipped, now, ignoreSkip = true))
    }

    @Test fun rotationFourOnTwoOff() {
        // anchor Wed 25 Nov = day 0 (on), 26 on, 27 on, 28 on, 29 off, 30 off, 1 Dec on
        val alarm = Alarm(
            time = AlarmTime(6, 0),
            repeat = RepeatRule.Rotation(LocalDate.of(2026, 11, 25), listOf(true, true, true, true, false, false)),
        )
        val occ = AlarmScheduleCalculator.upcoming(alarm, now, 3)
        assertEquals(listOf(LocalDate.of(2026, 11, 28), LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 2)), occ.map { it.toLocalDate() })
    }

    @Test fun rotationAnchorInFutureWorksBackwards() {
        val rule = RepeatRule.Rotation(LocalDate.of(2026, 12, 10), listOf(true, false))
        assertTrue(AlarmScheduleCalculator.matchesRule(rule, LocalDate.of(2026, 12, 8)))
        assertFalse(AlarmScheduleCalculator.matchesRule(rule, LocalDate.of(2026, 12, 9)))
    }

    @Test fun specificDatesSortedAndFiltered() {
        val alarm = Alarm(
            time = AlarmTime(10, 0),
            repeat = RepeatRule.SpecificDates(listOf(LocalDate.of(2026, 12, 24), LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 27))),
        )
        assertEquals(LocalDate.of(2026, 11, 27), next(alarm)!!.toLocalDate())
        assertEquals(LocalDate.of(2026, 12, 24), next(alarm, after = now.withHour(11))!!.toLocalDate())
    }

    @Test fun dstGapMovesForward() {
        // Europe/Berlin springs forward on 2027-03-28 at 02:00 -> 03:00
        val after = ZonedDateTime.of(2027, 3, 27, 12, 0, 0, 0, berlin)
        val r = AlarmScheduleCalculator.nextOccurrence(Alarm(time = AlarmTime(2, 30)), after)!!
        assertEquals(LocalDate.of(2027, 3, 28), r.toLocalDate())
        assertEquals(3, r.hour)
    }

    @Test fun snoozePolicy() {
        val fixed = SnoozeConfig(durationMinutes = 9, maxCount = 2)
        assertTrue(SnoozePolicy.canSnooze(fixed, 1))
        assertFalse(SnoozePolicy.canSnooze(fixed, 2))
        assertTrue(SnoozePolicy.canSnooze(fixed.copy(maxCount = 0), 99))
        val dec = SnoozeConfig(durationMinutes = 5, mode = SnoozeMode.DECREASING, decreaseStepMinutes = 2, minDurationMinutes = 1)
        assertEquals(listOf(5, 3, 1, 1), (0..3).map { SnoozePolicy.nextDurationMinutes(dec, it) })
    }

    @Test fun alarmJsonRoundTrip() {
        val alarm = Alarm(id = 5, label = "งาน", repeat = RepeatRule.Rotation(LocalDate.of(2026, 1, 1), listOf(true, false)))
        val json = ModelJson.encodeToString(Alarm.serializer(), alarm)
        assertEquals(alarm, ModelJson.decodeFromString(Alarm.serializer(), json))
        // Unknown keys from a newer version are ignored
        val withExtra = json.dropLast(1) + ",\"futureField\":42}"
        assertEquals(alarm, ModelJson.decodeFromString(Alarm.serializer(), withExtra))
    }

    @Test fun thaiDigitsAndBuddhistYear() {
        assertEquals("๐๗:๓๐", LocalizedFormat.digits("07:30", NumeralStyle.THAI))
        assertEquals("07:30", LocalizedFormat.digits("07:30", NumeralStyle.ARABIC))
        assertEquals(2569, LocalizedFormat.year(2026, YearStyle.BUDDHIST))
    }
}
