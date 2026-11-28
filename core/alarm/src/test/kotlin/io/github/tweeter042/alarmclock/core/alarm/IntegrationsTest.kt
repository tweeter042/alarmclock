package io.github.tweeter042.alarmclock.core.alarm

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.tweeter042.alarmclock.core.alarm.engine.TriggerResult
import io.github.tweeter042.alarmclock.core.alarm.guard.AlarmGuardService
import io.github.tweeter042.alarmclock.core.alarm.schedule.TriggerPayload
import io.github.tweeter042.alarmclock.core.alarm.schedule.extraPayload
import io.github.tweeter042.alarmclock.core.alarm.testing.EngineTestRule
import io.github.tweeter042.alarmclock.core.alarm.testing.TestEngineApp
import io.github.tweeter042.alarmclock.core.alarm.testing.eventually
import io.github.tweeter042.alarmclock.core.model.alarm.Alarm
import io.github.tweeter042.alarmclock.core.model.alarm.RepeatRule
import io.github.tweeter042.alarmclock.core.model.alarm.RingKind
import io.github.tweeter042.alarmclock.core.model.entity.CalendarAlarmRule
import io.github.tweeter042.alarmclock.core.model.entity.Holiday
import io.github.tweeter042.alarmclock.core.model.entity.HolidaySource
import io.github.tweeter042.alarmclock.core.model.entity.TriggerKind
import io.github.tweeter042.alarmclock.core.model.service.CalendarEvent
import io.github.tweeter042.alarmclock.core.model.service.CalendarInfo
import io.github.tweeter042.alarmclock.core.model.service.CalendarSource
import io.github.tweeter042.alarmclock.core.model.service.HolidayProvider
import io.github.tweeter042.alarmclock.core.model.time.AlarmTime
import io.github.tweeter042.alarmclock.core.model.time.IsoDays
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = TestEngineApp::class)
class IntegrationsTest {

    @get:Rule
    val rule = EngineTestRule()

    private val app get() = rule.app
    private val clock get() = app.clock

    private fun ledger() = runBlocking { app.data.scheduleLedger.all() }

    private class FakeCalendar(var permitted: Boolean, var events: List<CalendarEvent>, var failing: Boolean = false) : CalendarSource {
        override fun hasPermission() = permitted
        override suspend fun calendars(): List<CalendarInfo> = emptyList()
        override suspend fun events(fromMillis: Long, toMillis: Long, calendarIds: List<Long>): List<CalendarEvent> {
            if (failing) error("calendar provider unavailable")
            return events.filter { it.begin in fromMillis until toMillis }
        }
    }

    @Test fun calendarRulesBecomeCalendarAlarms() {
        val standup = CalendarEvent(id = 1, calendarId = 1, title = "Standup", begin = clock.at(2026, 11, 27, 10, 0), end = clock.at(2026, 11, 27, 10, 30), allDay = false)
        val calendar = FakeCalendar(permitted = true, events = listOf(standup))
        app.dependencies = { it.copy(calendar = calendar) }
        val ruleId = runBlocking { app.data.calendarRules.upsert(CalendarAlarmRule(name = "Work", minutesBefore = 45)) }
        val engine = rule.startEngine()

        val trigger = ledger().single { it.kind == TriggerKind.CALENDAR }
        assertEquals(ruleId, trigger.sourceId)
        assertEquals(clock.at(2026, 11, 27, 9, 15), trigger.triggerAt)
        assertEquals("Standup", trigger.extraPayload?.title)
        assertEquals(RingKind.CALENDAR, engine.nextAlarm.value?.kind)
        assertEquals("Standup", engine.nextAlarm.value?.label)

        clock.now = trigger.triggerAt
        assertEquals(TriggerResult.RANG, runBlocking { engine.onRingTrigger(TriggerPayload.of(trigger), null) })
        val ring = engine.activeRing.value!!
        assertEquals(RingKind.CALENDAR, ring.kind)
        assertEquals("Standup", ring.label)
    }

    @Test fun calendarAlarmsNeedThePermissionButSurviveAnUnreadableCalendar() {
        val event = CalendarEvent(id = 1, calendarId = 1, title = "Flight", begin = clock.at(2026, 11, 27, 10, 0), end = clock.at(2026, 11, 27, 12, 0), allDay = false)
        val calendar = FakeCalendar(permitted = true, events = listOf(event))
        app.dependencies = { it.copy(calendar = calendar) }
        runBlocking { app.data.calendarRules.upsert(CalendarAlarmRule(name = "Trips")) }
        val engine = rule.startEngine()
        assertEquals(1, ledger().count { it.kind == TriggerKind.CALENDAR })

        calendar.failing = true // e.g. before the first unlock the calendar provider is not available
        runBlocking { engine.rescheduleAll("test") }
        assertEquals("the previous plan is kept", 1, ledger().count { it.kind == TriggerKind.CALENDAR })

        calendar.failing = false
        calendar.permitted = false
        runBlocking { engine.rescheduleAll("test") }
        assertTrue("no permission, no calendar alarms", ledger().none { it.kind == TriggerKind.CALENDAR })
    }

    @Test fun theHolidayProviderIsPreferredOverTheRepository() {
        val holidays = object : HolidayProvider {
            override suspend fun holidayDates(from: LocalDate, to: LocalDate): Set<LocalDate> = setOf(LocalDate.of(2026, 11, 30))
            override suspend fun holidaysIn(year: Int): List<Holiday> = emptyList()
            override suspend fun importFromCalendars(): Result<Int> = Result.success(0)
        }
        app.dependencies = { it.copy(holidays = holidays) }
        val id = runBlocking {
            app.data.alarms.upsert(Alarm(time = AlarmTime(7, 0), repeat = RepeatRule.Weekly(IsoDays.WEEKDAYS), skipHolidays = true))
        }
        rule.startEngine()
        assertEquals(clock.at(2026, 12, 1, 7, 0), ledger().single { it.kind == TriggerKind.RING && it.sourceId == id }.triggerAt)
    }

    @Test fun enabledHolidaysOfTheRepositoryAreUsedWithoutAProvider() {
        runBlocking {
            app.data.holidays.upsert(Holiday(date = LocalDate.of(2026, 11, 30), nameEn = "Day off", source = HolidaySource.CUSTOM))
            app.data.holidays.upsert(Holiday(date = LocalDate.of(2026, 12, 1), nameEn = "Ignored", source = HolidaySource.CUSTOM, enabled = false))
        }
        val id = runBlocking {
            app.data.alarms.upsert(Alarm(time = AlarmTime(7, 0), repeat = RepeatRule.Weekly(IsoDays.WEEKDAYS), skipHolidays = true))
        }
        rule.startEngine()
        assertEquals(clock.at(2026, 12, 1, 7, 0), ledger().single { it.kind == TriggerKind.RING && it.sourceId == id }.triggerAt)
    }

    @Test fun theGuardServiceFollowsItsSetting() {
        rule.startEngine()
        shadowOf(app).clearStartedServices()
        runBlocking { app.data.settings.update { it.copy(reliability = it.reliability.copy(guardServiceEnabled = true)) } }
        eventually {
            val started = generateSequence { shadowOf(app).nextStartedService }.toList()
            assertTrue(started.any { it.component?.className == AlarmGuardService::class.java.name })
        }
        runBlocking { app.data.settings.update { it.copy(reliability = it.reliability.copy(guardServiceEnabled = false)) } }
        eventually {
            val stopped = generateSequence { shadowOf(app).nextStoppedService }.toList()
            assertTrue(stopped.any { it.component?.className == AlarmGuardService::class.java.name })
        }
    }

    @Test fun aHangingIntegrationNeverBlocksScheduling() {
        val hanging = object : HolidayProvider {
            override suspend fun holidayDates(from: LocalDate, to: LocalDate): Set<LocalDate> {
                delay(60_000)
                return emptySet()
            }
            override suspend fun holidaysIn(year: Int): List<Holiday> = emptyList()
            override suspend fun importFromCalendars(): Result<Int> = Result.success(0)
        }
        app.dependencies = { it.copy(holidays = hanging) }
        val id = runBlocking { app.data.alarms.upsert(Alarm(time = AlarmTime(9, 0), repeat = RepeatRule.Weekly(IsoDays.WEEKDAYS))) }
        val started = System.currentTimeMillis()
        rule.startEngine()
        assertTrue(ledger().any { it.kind == TriggerKind.RING && it.sourceId == id })
        assertTrue("bounded by the integration timeout", System.currentTimeMillis() - started < 45_000)
    }
}
