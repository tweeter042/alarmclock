package io.github.tweeter042.alarmclock.core.alarm.schedule

import io.github.tweeter042.alarmclock.core.model.entity.CalendarAlarmRule
import io.github.tweeter042.alarmclock.core.model.service.CalendarEvent
import io.github.tweeter042.alarmclock.core.model.time.AlarmTime
import io.github.tweeter042.alarmclock.core.model.time.IsoDays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class CalendarAlarmPlannerTest {
    private val zone = ZoneId.of("Asia/Bangkok")

    /** Friday 2026-11-27 04:00 Bangkok. */
    private val now = at(2026, 11, 27, 4, 0)

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone).toInstant().toEpochMilli()

    private var nextId = 1L
    private fun event(begin: Long, title: String = "Meeting", allDay: Boolean = false, calendarId: Long = 1) =
        CalendarEvent(id = nextId++, calendarId = calendarId, title = title, begin = begin, end = begin + 3_600_000, allDay = allDay)

    private fun allDay(date: LocalDate, title: String = "Holiday") =
        event(date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), title, allDay = true)

    private val rule = CalendarAlarmRule(
        id = 3,
        minutesBefore = 60,
        days = IsoDays.ALL,
        earliest = AlarmTime(5, 0),
        latest = AlarmTime(11, 0),
    )

    private fun next(rule: CalendarAlarmRule, events: List<CalendarEvent>, isHandled: (Long) -> Boolean = { false }) =
        CalendarAlarmPlanner.nextOccurrence(rule, events, now, zone, isHandled = isHandled)

    @Test fun ringsMinutesBeforeTheFirstEventOfTheDay() {
        val events = listOf(event(at(2026, 11, 27, 10, 0), "Second"), event(at(2026, 11, 27, 8, 30), "First"))
        val result = next(rule, events)!!
        assertEquals(at(2026, 11, 27, 7, 30), result.alarmAt)
        assertEquals("First", result.eventTitle)
        assertEquals(3L, result.ruleId)
    }

    @Test fun neverRingsBeforeTheEarliestBound() {
        val result = next(rule, listOf(event(at(2026, 11, 27, 5, 30))))!!
        assertEquals(at(2026, 11, 27, 5, 0), result.alarmAt)
    }

    @Test fun aFirstEventNeedingAnAlarmAfterTheLatestBoundMeansNoAlarmThatDay() {
        val events = listOf(event(at(2026, 11, 27, 13, 0)), event(at(2026, 11, 28, 9, 0)))
        val result = next(rule, events)!!
        assertEquals("today's 12:00 alarm is later than 11:00", at(2026, 11, 28, 8, 0), result.alarmAt)
    }

    @Test fun onlyTheFirstEventCountsEvenIfItsAlarmIsAlreadyPast() {
        val laterNow = at(2026, 11, 27, 8, 0)
        val events = listOf(event(at(2026, 11, 27, 8, 30)), event(at(2026, 11, 27, 10, 0)), event(at(2026, 11, 28, 9, 0)))
        val result = CalendarAlarmPlanner.nextOccurrence(rule, events, laterNow, zone)!!
        assertEquals("not 09:00 for the second event today", at(2026, 11, 28, 8, 0), result.alarmAt)
    }

    @Test fun keywordMatchesCaseInsensitively() {
        val keyworded = rule.copy(keyword = "flight")
        val events = listOf(event(at(2026, 11, 27, 7, 0), "Breakfast"), event(at(2026, 11, 27, 9, 0), "FLIGHT TG 101"))
        val result = next(keyworded, events)!!
        assertEquals(at(2026, 11, 27, 8, 0), result.alarmAt)
        assertEquals("FLIGHT TG 101", result.eventTitle)
    }

    @Test fun allDayEventsAreIgnoredUnlessAllowed() {
        val events = listOf(allDay(LocalDate.of(2026, 11, 27)), event(at(2026, 11, 27, 9, 0), "Real"))
        assertEquals(at(2026, 11, 27, 8, 0), next(rule, events)!!.alarmAt)
        val withAllDay = next(rule.copy(ignoreAllDayEvents = false), events)!!
        assertEquals("all-day events begin at local midnight; the earliest bound applies", at(2026, 11, 27, 5, 0), withAllDay.alarmAt)
        assertEquals("Holiday", withAllDay.eventTitle)
    }

    @Test fun onlySelectedDaysAndCalendars() {
        val weekdaysOnly = rule.copy(days = IsoDays.WEEKDAYS, calendarIds = listOf(2))
        val events = listOf(
            event(at(2026, 11, 27, 9, 0), calendarId = 1),
            event(at(2026, 11, 28, 9, 0), calendarId = 2),
            event(at(2026, 11, 29, 9, 0), calendarId = 2),
        )
        assertNull("Saturday and Sunday are not selected; Friday's event is in another calendar", next(weekdaysOnly, events))
    }

    @Test fun lookAheadIsLimitedTo48Hours() {
        assertNull(next(rule, listOf(event(at(2026, 11, 29, 9, 0)))))
        assertEquals(at(2026, 11, 29, 3, 30), next(rule.copy(earliest = null), listOf(event(at(2026, 11, 29, 4, 30))))?.alarmAt)
    }

    @Test fun handledOccurrencesMoveToTheNextDay() {
        val events = listOf(event(at(2026, 11, 27, 9, 0)), event(at(2026, 11, 28, 9, 30)))
        val result = next(rule, events) { it == at(2026, 11, 27, 8, 0) }!!
        assertEquals(at(2026, 11, 28, 8, 30), result.alarmAt)
    }

    @Test fun disabledRulesAndEmptyCalendarsGiveNothing() {
        assertNull(next(rule.copy(enabled = false), listOf(event(at(2026, 11, 27, 9, 0)))))
        assertNull(next(rule.copy(days = emptySet()), listOf(event(at(2026, 11, 27, 9, 0)))))
        assertNull(next(rule, emptyList()))
    }

    @Test fun theQueryWindowCoversTodayToTheEndOfTheHorizonDay() {
        val window = CalendarAlarmPlanner.queryWindow(now, zone)
        assertEquals(at(2026, 11, 27, 0, 0), window.first)
        assertEquals(at(2026, 11, 30, 0, 0), window.last + 1)
        assertTrue(window.last + 1 - window.first > CalendarAlarmPlanner.HORIZON_MILLIS)
    }
}
