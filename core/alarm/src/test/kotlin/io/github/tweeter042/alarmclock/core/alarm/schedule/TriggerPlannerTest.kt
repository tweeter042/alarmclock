package io.github.tweeter042.alarmclock.core.alarm.schedule

import io.github.tweeter042.alarmclock.core.model.alarm.ActiveRing
import io.github.tweeter042.alarmclock.core.model.alarm.Alarm
import io.github.tweeter042.alarmclock.core.model.alarm.PreAlarmKind
import io.github.tweeter042.alarmclock.core.model.alarm.RepeatRule
import io.github.tweeter042.alarmclock.core.model.alarm.RingKind
import io.github.tweeter042.alarmclock.core.model.alarm.RingState
import io.github.tweeter042.alarmclock.core.model.alarm.SmartWakeConfig
import io.github.tweeter042.alarmclock.core.model.alarm.SunriseConfig
import io.github.tweeter042.alarmclock.core.model.alarm.WeatherAdjustConfig
import io.github.tweeter042.alarmclock.core.model.entity.AlarmGroup
import io.github.tweeter042.alarmclock.core.model.entity.ScheduledTrigger
import io.github.tweeter042.alarmclock.core.model.entity.TimerModel
import io.github.tweeter042.alarmclock.core.model.entity.TimerState
import io.github.tweeter042.alarmclock.core.model.entity.TriggerKind
import io.github.tweeter042.alarmclock.core.model.time.AlarmTime
import io.github.tweeter042.alarmclock.core.model.time.IsoDays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class TriggerPlannerTest {
    private val zone = ZoneId.of("Asia/Bangkok")

    /** Friday 2026-11-27 08:00 Bangkok. */
    private val now = at(2026, 11, 27, 8, 0)

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone).toInstant().toEpochMilli()

    private fun input(
        alarms: List<Alarm> = emptyList(),
        groups: List<AlarmGroup> = emptyList(),
        holidays: Set<LocalDate> = emptySet(),
        upcoming: Int = 60,
        weather: Boolean = true,
        rings: List<ActiveRing> = emptyList(),
        ringsKnown: Boolean = true,
        ledger: List<ScheduledTrigger> = emptyList(),
        timers: List<TimerModel>? = emptyList(),
        handled: Set<OccurrenceKey> = emptySet(),
        calendar: List<CalendarOccurrence> = emptyList(),
        calendarLabels: Map<Long, String> = emptyMap(),
    ) = PlanInput(now, zone, alarms, groups, holidays, upcoming, weather, calendar, calendarLabels, rings, ringsKnown, ledger, timers, handled)

    private fun weekday(id: Long, h: Int, m: Int, label: String = "") =
        Alarm(id = id, time = AlarmTime(h, m), repeat = RepeatRule.Weekly(IsoDays.WEEKDAYS), label = label)

    private fun Plan.at(kind: TriggerKind) = triggers.filter { it.kind == kind }
    private fun Plan.pre(kind: PreAlarmKind) = triggers.singleOrNull { it.kind == TriggerKind.PRE_ALARM && it.extra?.preAlarm == kind }

    @Test fun anAlarmGetsItsRingTriggerAndEveryPreAlarmEvent() {
        val alarm = weekday(1, 9, 30, "Work").copy(
            sunrise = SunriseConfig(enabled = true, durationMinutes = 15),
            smartWake = SmartWakeConfig(enabled = true, windowMinutes = 30),
            weatherAdjust = WeatherAdjustConfig(enabled = true, minutesEarlier = 20),
        )
        val plan = TriggerPlanner.plan(input(alarms = listOf(alarm)))
        val occurrence = at(2026, 11, 27, 9, 30)

        val ring = plan.at(TriggerKind.RING).single()
        assertEquals(occurrence, ring.triggerAt)
        assertEquals(occurrence, ring.occurrenceAt)
        assertEquals(RegisterMethod.ALARM_CLOCK, ring.method)
        assertEquals("Work", ring.label)

        assertEquals(at(2026, 11, 27, 8, 30), plan.pre(PreAlarmKind.UPCOMING_NOTICE)?.triggerAt)
        assertEquals(at(2026, 11, 27, 9, 15), plan.pre(PreAlarmKind.SUNRISE_START)?.triggerAt)
        assertEquals(at(2026, 11, 27, 9, 0), plan.pre(PreAlarmKind.SMART_WAKE_START)?.triggerAt)
        assertEquals(at(2026, 11, 27, 9, 10), plan.pre(PreAlarmKind.WEATHER_CHECK)?.triggerAt)
        assertEquals(at(2026, 11, 27, 9, 20), plan.pre(PreAlarmKind.PREFETCH)?.triggerAt)
        plan.at(TriggerKind.PRE_ALARM).filter { it.extra?.preAlarm != null }.forEach {
            assertEquals(RegisterMethod.EXACT_WHILE_IDLE, it.method)
            assertEquals(occurrence, it.occurrenceAt)
            assertEquals(1L, it.sourceId)
        }
        assertEquals(occurrence, plan.occurrences[1L])
        assertEquals(NextAlarmExpectation(RingKind.ALARM, 1L, occurrence, "Work"), plan.next?.let { NextAlarmExpectation(it.kind, it.sourceId, it.triggerAt, it.label) })
    }

    private data class NextAlarmExpectation(val kind: RingKind, val sourceId: Long, val at: Long, val label: String)

    @Test fun preAlarmEventsInThePastAreNotRegistered() {
        val alarm = weekday(1, 8, 20).copy(sunrise = SunriseConfig(enabled = true, durationMinutes = 30))
        val plan = TriggerPlanner.plan(input(alarms = listOf(alarm)))
        assertNull("07:20 is past", plan.pre(PreAlarmKind.UPCOMING_NOTICE))
        assertNull("07:50 is past", plan.pre(PreAlarmKind.SUNRISE_START))
        assertEquals(at(2026, 11, 27, 8, 10), plan.pre(PreAlarmKind.PREFETCH)?.triggerAt)
        assertEquals(at(2026, 11, 27, 8, 20), plan.at(TriggerKind.RING).single().triggerAt)
    }

    @Test fun optionalPreAlarmsDependOnTheirSettings() {
        val alarm = weekday(1, 9, 30).copy(weatherAdjust = WeatherAdjustConfig(enabled = true))
        val plan = TriggerPlanner.plan(input(alarms = listOf(alarm), upcoming = 0, weather = false))
        assertNull(plan.pre(PreAlarmKind.UPCOMING_NOTICE))
        assertNull("weather is off in settings", plan.pre(PreAlarmKind.WEATHER_CHECK))
        assertNull(plan.pre(PreAlarmKind.SUNRISE_START))
        assertNull(plan.pre(PreAlarmKind.SMART_WAKE_START))
        val noConditions = alarm.copy(weatherAdjust = WeatherAdjustConfig(enabled = true, conditions = emptySet()))
        assertNull(TriggerPlanner.plan(input(alarms = listOf(noConditions))).pre(PreAlarmKind.WEATHER_CHECK))
    }

    @Test fun disabledAlarmsAndDisabledGroupsHaveNoTriggers() {
        val disabled = weekday(1, 9, 0).copy(enabled = false)
        val inGroup = weekday(2, 9, 0).copy(groupId = 5)
        val plan = TriggerPlanner.plan(input(alarms = listOf(disabled, inGroup), groups = listOf(AlarmGroup(id = 5, name = "Off", enabled = false))))
        assertTrue(plan.triggers.none { it.sourceId == 1L || it.sourceId == 2L })
        assertNull(plan.next)
        assertEquals(1, plan.triggers.size)
        assertEquals(TriggerIdentities.midnight, plan.triggers.single().identity)
    }

    @Test fun holidaysAreSkippedForAlarmsThatAskForIt() {
        val alarm = weekday(1, 7, 0).copy(skipHolidays = true)
        // Next weekday after Friday 08:00 is Monday 30 Nov; make it a holiday.
        val plan = TriggerPlanner.plan(input(alarms = listOf(alarm), holidays = setOf(LocalDate.of(2026, 11, 30))))
        assertEquals(at(2026, 12, 1, 7, 0), plan.at(TriggerKind.RING).single().triggerAt)
    }

    @Test fun handledOccurrencesAreSkipped() {
        val alarm = weekday(1, 9, 30)
        val today = at(2026, 11, 27, 9, 30)
        val plan = TriggerPlanner.plan(input(alarms = listOf(alarm), handled = setOf(OccurrenceKey(RingKind.ALARM, 1, today))))
        assertEquals("rang early (smart wake) or dismissed in advance", at(2026, 11, 30, 9, 30), plan.at(TriggerKind.RING).single().triggerAt)
    }

    @Test fun parkedRingsGetTheirTriggers() {
        val snoozed = ActiveRing("r1", RingKind.ALARM, 1, Alarm(id = 1, label = "Up"), now - 60_000, now - 60_000, RingState.SNOOZED, 1, now + 300_000)
        val checking = ActiveRing("r2", RingKind.NAP, 99, Alarm(), now - 120_000, now - 120_000, RingState.AWAKE_CHECK, awakeCheckDueAt = now + 200_000)
        val sounding = ActiveRing("r3", RingKind.ALARM, 3, Alarm(id = 3), now, now)
        val plan = TriggerPlanner.plan(input(rings = listOf(snoozed, checking, sounding)))
        val snooze = plan.at(TriggerKind.SNOOZE_END).single()
        assertEquals(now + 300_000, snooze.triggerAt)
        assertEquals("r1", snooze.extra?.ringId)
        assertEquals(RingKind.ALARM, snooze.extra?.ringKind)
        assertEquals(now - 60_000, snooze.occurrenceAt)
        val awake = plan.at(TriggerKind.AWAKE_CHECK).single()
        assertEquals(now + 200_000, awake.triggerAt)
        assertEquals("r2", awake.extra?.ringId)
        assertEquals("the snooze is the next alarm", now + 300_000, plan.next?.triggerAt)
        assertEquals("Up", plan.next?.label)
    }

    @Test fun ringTriggersAreKeptFromTheLedgerWhileTheRingStateIsUnknown() {
        val snoozeEnd = ScheduledTrigger(
            RequestCodes.preferred(TriggerIdentities.snoozeEnd("r1")), TriggerKind.SNOOZE_END, 1, now + 1000, now - 5000,
            TriggerExtra(ringId = "r1", ringKind = RingKind.ALARM).encode(),
        )
        val plan = TriggerPlanner.plan(input(ringsKnown = false, ledger = listOf(snoozeEnd)))
        assertEquals(listOf(now + 1000), plan.at(TriggerKind.SNOOZE_END).map { it.triggerAt })
        val known = TriggerPlanner.plan(input(ringsKnown = true, ledger = listOf(snoozeEnd)))
        assertTrue("derived from the (empty) ring state", known.at(TriggerKind.SNOOZE_END).isEmpty())
    }

    @Test fun napsAndTestsStayUntilTheyHaveFired() {
        val futureNap = ScheduledTrigger(1, TriggerKind.NAP, now + 600_000, now + 600_000, now + 600_000, TriggerExtra(alarm = Alarm(label = "Nap")).encode())
        val pastNap = ScheduledTrigger(2, TriggerKind.NAP, now - 1000, now - 1000, now - 1000, null)
        val rungNap = ScheduledTrigger(3, TriggerKind.NAP, now - 5000, now - 5000, now - 5000, null)
        val test = ScheduledTrigger(4, TriggerKind.TEST, 7, now + 3000, now + 3000, TriggerExtra(alarm = Alarm(id = 7)).encode())
        val plan = TriggerPlanner.plan(
            input(
                ledger = listOf(futureNap, pastNap, rungNap, test),
                handled = setOf(OccurrenceKey(RingKind.NAP, now - 5000, now - 5000)),
            ),
        )
        assertEquals(listOf(now - 1000, now + 600_000), plan.at(TriggerKind.NAP).map { it.triggerAt }.sorted())
        assertEquals(listOf(now + 3000), plan.at(TriggerKind.TEST).map { it.triggerAt })
        assertEquals("a past nap fires at once and is the next ring", now - 1000, plan.next?.triggerAt)
        assertEquals(RingKind.NAP, plan.next?.kind)
    }

    @Test fun aKickIsKeptOnlyWhileItsRingIsSounding() {
        val kick = ScheduledTrigger(
            RequestCodes.preferred(TriggerIdentities.kick("r1")), TriggerKind.SNOOZE_END, 1, now + 1000, now,
            TriggerExtra(ringId = "r1", ringKind = RingKind.ALARM, purpose = TriggerExtra.PURPOSE_KICK).encode(),
        )
        val sounding = ActiveRing("r1", RingKind.ALARM, 1, Alarm(id = 1), now, now)
        assertEquals(1, TriggerPlanner.plan(input(rings = listOf(sounding), ledger = listOf(kick))).at(TriggerKind.SNOOZE_END).size)
        assertTrue(TriggerPlanner.plan(input(ledger = listOf(kick))).at(TriggerKind.SNOOZE_END).isEmpty())
        assertNull("a kick is never the next alarm", TriggerPlanner.plan(input(rings = listOf(sounding), ledger = listOf(kick))).next)
    }

    @Test fun timersFollowTheRepositoryAndTheLedger() {
        val running = TimerModel(id = 1, durationMs = 60_000, state = TimerState.RUNNING, endAt = now + 60_000)
        val paused = TimerModel(id = 2, durationMs = 60_000, state = TimerState.PAUSED)
        val ringing = TimerModel(id = 3, durationMs = 60_000, state = TimerState.RINGING, endAt = now - 1)
        val ledger = listOf(
            ScheduledTrigger(10, TriggerKind.TIMER, 2, now + 30_000, now + 30_000),
            ScheduledTrigger(11, TriggerKind.TIMER, 3, now - 1, now - 1),
            ScheduledTrigger(12, TriggerKind.TIMER, 4, now + 5_000, now + 5_000),
        )
        val plan = TriggerPlanner.plan(input(timers = listOf(running, paused, ringing), ledger = ledger))
        val byTimer = plan.at(TriggerKind.TIMER).associate { it.sourceId to it.triggerAt }
        assertEquals(mapOf(1L to now + 60_000, 2L to now + 30_000), byTimer)
        assertNull("timers are not the next alarm", plan.next)

        val unknown = TriggerPlanner.plan(input(timers = null, ledger = ledger))
        assertEquals("unreadable timers keep their triggers", setOf(2L, 3L, 4L), unknown.at(TriggerKind.TIMER).map { it.sourceId }.toSet())
    }

    @Test fun calendarAlarmsAreRingTriggers() {
        val occurrence = CalendarOccurrence(ruleId = 4, alarmAt = now + 3_600_000, eventId = 9, eventTitle = "Standup", eventBegin = now + 7_200_000)
        val plan = TriggerPlanner.plan(input(calendar = listOf(occurrence), calendarLabels = mapOf(4L to "Work")))
        val trigger = plan.at(TriggerKind.CALENDAR).single()
        assertEquals(now + 3_600_000, trigger.triggerAt)
        assertEquals("Standup", trigger.extra?.title)
        assertEquals(RegisterMethod.ALARM_CLOCK, trigger.method)
        assertEquals(RingKind.CALENDAR, plan.next?.kind)
        assertEquals("Work", plan.next?.label)
        val handled = TriggerPlanner.plan(input(calendar = listOf(occurrence), handled = setOf(OccurrenceKey(RingKind.CALENDAR, 4, now + 3_600_000))))
        assertTrue(handled.at(TriggerKind.CALENDAR).isEmpty())
    }

    @Test fun theNextAlarmIsTheEarliestRingNotATimerOrTest() {
        val alarm = weekday(1, 9, 0, "Alarm")
        val test = ScheduledTrigger(1, TriggerKind.TEST, 0, now + 1000, now + 1000, TriggerExtra(alarm = Alarm()).encode())
        val timer = TimerModel(id = 2, durationMs = 1000, state = TimerState.RUNNING, endAt = now + 2000)
        val plan = TriggerPlanner.plan(input(alarms = listOf(alarm), ledger = listOf(test), timers = listOf(timer)))
        assertEquals(at(2026, 11, 27, 9, 0), plan.next?.triggerAt)
        assertEquals(RingKind.ALARM, plan.next?.kind)
    }

    @Test fun theMidnightRecomputeIsAlwaysPlanned() {
        val plan = TriggerPlanner.plan(input())
        val midnight = plan.triggers.single { it.identity == TriggerIdentities.midnight }
        assertEquals(at(2026, 11, 28, 0, 0) + TriggerPlanner.MIDNIGHT_OFFSET_MILLIS, midnight.triggerAt)
        assertEquals(TriggerExtra.PURPOSE_MIDNIGHT, midnight.extra?.purpose)
        assertEquals(RegisterMethod.EXACT_WHILE_IDLE, midnight.method)
    }

    @Test fun everyPlannedIdentityIsUniqueAndTheListIsSorted() {
        val alarms = (1L..30L).map { weekday(it, 6 + (it % 10).toInt(), (it * 7 % 60).toInt()).copy(sunrise = SunriseConfig(enabled = true)) }
        val plan = TriggerPlanner.plan(input(alarms = alarms))
        assertEquals(plan.triggers.size, plan.triggers.map { it.identity }.toSet().size)
        assertEquals(plan.triggers.sortedBy { it.triggerAt }.map { it.triggerAt }, plan.triggers.map { it.triggerAt })
    }

    @Test fun nextOccurrenceHonoursGroupsAndHandledOccurrences() {
        val alarm = weekday(1, 9, 0).copy(groupId = 3)
        val groups = listOf(AlarmGroup(id = 3, name = "G", enabled = true))
        val first = TriggerPlanner.nextOccurrence(alarm, now, zone, groups, emptySet())
        assertEquals(at(2026, 11, 27, 9, 0), first)
        val skipped = TriggerPlanner.nextOccurrence(alarm, now, zone, groups, emptySet(), setOf(OccurrenceKey(RingKind.ALARM, 1, first!!)))
        assertEquals(at(2026, 11, 30, 9, 0), skipped)
        assertNull(TriggerPlanner.nextOccurrence(alarm, now, zone, listOf(AlarmGroup(id = 3, name = "G", enabled = false)), emptySet()))
        assertTrue("an unknown group does not block the alarm", TriggerPlanner.isGroupEnabled(alarm, emptyList()))
    }
}
