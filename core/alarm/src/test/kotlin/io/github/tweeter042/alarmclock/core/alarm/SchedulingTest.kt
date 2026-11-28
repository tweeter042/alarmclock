@file:Suppress("DEPRECATION") // ShadowAlarmManager exposes the PendingIntent only as a deprecated field.

package io.github.tweeter042.alarmclock.core.alarm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.tweeter042.alarmclock.core.alarm.notify.EngineChannels
import io.github.tweeter042.alarmclock.core.alarm.receiver.BootReceiver
import io.github.tweeter042.alarmclock.core.alarm.schedule.RequestCodes
import io.github.tweeter042.alarmclock.core.alarm.schedule.TriggerIdentities
import io.github.tweeter042.alarmclock.core.alarm.schedule.TriggerIntents
import io.github.tweeter042.alarmclock.core.alarm.schedule.extraPayload
import io.github.tweeter042.alarmclock.core.alarm.testing.EngineTestRule
import io.github.tweeter042.alarmclock.core.alarm.testing.TestEngineApp
import io.github.tweeter042.alarmclock.core.alarm.testing.eventually
import io.github.tweeter042.alarmclock.core.model.alarm.Alarm
import io.github.tweeter042.alarmclock.core.model.alarm.PreAlarmKind
import io.github.tweeter042.alarmclock.core.model.alarm.RepeatRule
import io.github.tweeter042.alarmclock.core.model.alarm.RingKind
import io.github.tweeter042.alarmclock.core.model.alarm.SunriseConfig
import io.github.tweeter042.alarmclock.core.model.entity.AlarmGroup
import io.github.tweeter042.alarmclock.core.model.entity.TimerModel
import io.github.tweeter042.alarmclock.core.model.entity.TimerState
import io.github.tweeter042.alarmclock.core.model.entity.TriggerKind
import io.github.tweeter042.alarmclock.core.model.time.AlarmTime
import io.github.tweeter042.alarmclock.core.model.time.IsoDays
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = TestEngineApp::class)
class SchedulingTest {

    @get:Rule
    val rule = EngineTestRule()

    private val app get() = rule.app
    private val clock get() = app.clock

    private fun weekday(h: Int, m: Int, label: String = "Work") =
        Alarm(time = AlarmTime(h, m), repeat = RepeatRule.Weekly(IsoDays.WEEKDAYS), label = label)

    private fun scheduled(): List<ShadowAlarmManager.ScheduledAlarm> = rule.shadowAlarms.scheduledAlarms

    private fun alarmClocks() = scheduled().filter { it.alarmClockInfo != null }

    private fun requestCodeOf(alarm: ShadowAlarmManager.ScheduledAlarm): Int = shadowOf(alarm.operation).requestCode

    @Test fun anAlarmIsRegisteredWithSetAlarmClockAndItsPreAlarmsExactly() {
        val id = runBlocking { app.data.alarms.upsert(weekday(9, 30).copy(sunrise = SunriseConfig(enabled = true, durationMinutes = 15))) }
        val engine = rule.startEngine()
        val occurrence = clock.at(2026, 11, 27, 9, 30)

        val ring = alarmClocks().single { requestCodeOf(it) == RequestCodes.preferred(TriggerIdentities.ring(id)) }
        assertEquals(occurrence, ring.triggerAtMs)
        assertEquals(app.packageName, ring.alarmClockInfo!!.showIntent.creatorPackage)
        val exact = scheduled().filter { it.alarmClockInfo == null }
        assertTrue("pre-alarm events are allow-while-idle", exact.all { it.isAllowWhileIdle })
        val preTimes = exact.map { it.triggerAtMs }.toSet()
        assertTrue(clock.at(2026, 11, 27, 8, 30) in preTimes) // upcoming notice, 60 min
        assertTrue(clock.at(2026, 11, 27, 9, 15) in preTimes) // sunrise, 15 min
        assertTrue(clock.at(2026, 11, 27, 9, 20) in preTimes) // prefetch, 10 min
        assertTrue(clock.at(2026, 11, 28, 0, 0) + 5_000 in preTimes) // midnight recompute

        val next = engine.nextAlarm.value
        assertEquals(RingKind.ALARM, next?.kind)
        assertEquals(id, next?.sourceId)
        assertEquals(occurrence, next?.triggerAt)
        assertEquals("Work", next?.label)
        assertEquals(occurrence, runBlocking { engine.nextOccurrenceOf(id) })
    }

    @Test fun theLedgerRecordsEveryRegistration() {
        val id = runBlocking { app.data.alarms.upsert(weekday(9, 30)) }
        rule.startEngine()
        val ledger = runBlocking { app.data.scheduleLedger.all() }
        val ring = ledger.single { it.kind == TriggerKind.RING }
        assertEquals(id, ring.sourceId)
        assertEquals(RequestCodes.preferred(TriggerIdentities.ring(id)), ring.requestCode)
        assertEquals(
            setOf(PreAlarmKind.UPCOMING_NOTICE, PreAlarmKind.PREFETCH),
            ledger.filter { it.kind == TriggerKind.PRE_ALARM && it.sourceId == id }.mapNotNull { it.extraPayload?.preAlarm }.toSet(),
        )
        assertEquals("every ledger entry is registered", ledger.map { it.requestCode }.toSet(), scheduled().map(::requestCodeOf).toSet())
        ledger.forEach { entry ->
            assertNotNull(
                PendingIntent.getBroadcast(app, entry.requestCode, TriggerIntents.base(app), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE),
            )
        }
    }

    @Test fun editsAreRescheduledAutomatically() {
        val engine = rule.startEngine()
        assertNull(engine.nextAlarm.value)

        val id = runBlocking { app.data.alarms.upsert(weekday(9, 30)) }
        val code = RequestCodes.preferred(TriggerIdentities.ring(id))
        eventually { assertEquals(clock.at(2026, 11, 27, 9, 30), alarmClocks().single { requestCodeOf(it) == code }.triggerAtMs) }
        eventually { assertEquals(id, engine.nextAlarm.value?.sourceId) }

        runBlocking { app.data.alarms.update(id) { it.copy(time = AlarmTime(10, 15)) } }
        eventually { assertEquals(clock.at(2026, 11, 27, 10, 15), alarmClocks().single { requestCodeOf(it) == code }.triggerAtMs) }

        runBlocking { app.data.alarms.setEnabled(id, false) }
        eventually { assertTrue(scheduled().none { requestCodeOf(it) == code }) }
        eventually { assertNull(engine.nextAlarm.value) }
        val ledger = runBlocking { app.data.scheduleLedger.all() }
        assertTrue(ledger.none { it.sourceId == id })
        assertNull("the PendingIntent is cancelled", PendingIntent.getBroadcast(app, code, TriggerIntents.base(app), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE))
        eventually { assertTrue(app.hooks.scheduleChanges >= 3) }
    }

    @Test fun disablingAGroupRemovesItsAlarms() {
        val groupId = runBlocking { app.data.alarms.upsertGroup(AlarmGroup(name = "Work")) }
        val id = runBlocking { app.data.alarms.upsert(weekday(9, 0).copy(groupId = groupId)) }
        rule.startEngine()
        val code = RequestCodes.preferred(TriggerIdentities.ring(id))
        assertTrue(alarmClocks().any { requestCodeOf(it) == code })
        runBlocking { app.data.alarms.setGroupEnabled(groupId, false) }
        eventually { assertTrue(scheduled().none { requestCodeOf(it) == code }) }
    }

    @Test fun skipNextMovesTheTriggerAndClearSkipRestoresIt() {
        val id = runBlocking { app.data.alarms.upsert(weekday(9, 0)) }
        val engine = rule.startEngine()
        val code = RequestCodes.preferred(TriggerIdentities.ring(id))
        runBlocking { engine.skipNext(id) }
        eventually { assertEquals(clock.at(2026, 11, 30, 9, 0), alarmClocks().single { requestCodeOf(it) == code }.triggerAtMs) }
        assertEquals(clock.at(2026, 11, 27, 9, 0), runBlocking { app.data.alarms.getAlarm(id) }?.skippedOccurrenceAt)
        runBlocking { engine.clearSkip(id) }
        eventually { assertEquals(clock.at(2026, 11, 27, 9, 0), alarmClocks().single { requestCodeOf(it) == code }.triggerAtMs) }
    }

    @Test fun theBootReceiverRegistersEverythingAgain() {
        val id = runBlocking { app.data.alarms.upsert(weekday(9, 30)) }
        rule.startEngine()
        val code = RequestCodes.preferred(TriggerIdentities.ring(id))
        // A reboot clears every alarm and PendingIntent of the app.
        scheduled().forEach {
            rule.alarmManager.cancel(it.operation!!)
            it.operation!!.cancel()
        }
        assertTrue(scheduled().isEmpty())

        app.sendBroadcast(Intent(Intent.ACTION_BOOT_COMPLETED).setClass(app, BootReceiver::class.java))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        eventually { assertTrue(alarmClocks().any { requestCodeOf(it) == code }) }
    }

    @Test fun integrityReportFindsAndRepairsLostTriggers() {
        val id = runBlocking { app.data.alarms.upsert(weekday(9, 30)) }
        val engine = rule.startEngine()
        val healthy = runBlocking { engine.integrityReport(repair = false) }
        assertTrue(healthy.missingSourceIds.isEmpty())
        assertEquals(healthy.expected, healthy.registered)
        assertTrue(healthy.systemNextAlarmMatches)

        val code = RequestCodes.preferred(TriggerIdentities.ring(id))
        val lost = scheduled().single { requestCodeOf(it) == code }
        rule.alarmManager.cancel(lost.operation!!)
        lost.operation!!.cancel()

        val broken = runBlocking { engine.integrityReport(repair = false) }
        assertEquals(listOf(id), broken.missingSourceIds)
        assertEquals(broken.expected - 1, broken.registered)
        assertEquals(false, broken.repaired)

        val repaired = runBlocking { engine.integrityReport(repair = true) }
        assertTrue(repaired.repaired)
        assertTrue(alarmClocks().any { requestCodeOf(it) == code })
        assertTrue(runBlocking { engine.integrityReport(repair = false) }.missingSourceIds.isEmpty())
    }

    @Test fun napsTimersAndTestsAreRegisteredAsAlarmClocks() {
        val engine = rule.startEngine()
        val napAt = runBlocking { engine.startNap(20) }
        assertEquals(clock.now + 20 * 60_000L, napAt)
        val nap = runBlocking { app.data.scheduleLedger.all() }.single { it.kind == TriggerKind.NAP }
        assertEquals(napAt, nap.sourceId)
        assertNotNull(nap.extraPayload?.alarm)
        assertTrue(alarmClocks().any { it.triggerAtMs == napAt })
        eventually { assertEquals(RingKind.NAP, engine.nextAlarm.value?.kind) }

        val timerId = runBlocking { app.data.timers.upsert(TimerModel(durationMs = 300_000, state = TimerState.RUNNING, endAt = clock.now + 300_000)) }
        runBlocking { engine.scheduleTimer(timerId, clock.now + 300_000) }
        assertTrue(alarmClocks().any { requestCodeOf(it) == RequestCodes.preferred(TriggerIdentities.timer(timerId)) && it.triggerAtMs == clock.now + 300_000 })
        assertEquals("timers are not the next alarm", RingKind.NAP, engine.nextAlarm.value?.kind)
        runBlocking { engine.cancelTimer(timerId) }
        assertTrue(scheduled().none { requestCodeOf(it) == RequestCodes.preferred(TriggerIdentities.timer(timerId)) })
        assertTrue(runBlocking { app.data.scheduleLedger.all() }.none { it.kind == TriggerKind.TIMER })

        runBlocking { engine.testRing(Alarm(label = "Test"), delaySeconds = 3) }
        val test = runBlocking { app.data.scheduleLedger.all() }.single { it.kind == TriggerKind.TEST }
        assertEquals(clock.now + 3_000, test.triggerAt)
        assertTrue(alarmClocks().any { it.triggerAtMs == clock.now + 3_000 })
    }

    @Test fun dismissingInAdvanceSkipsThatOccurrence() {
        val id = runBlocking { app.data.alarms.upsert(weekday(9, 0)) }
        val engine = rule.startEngine()
        runBlocking { engine.dismissUpcoming(id) }
        val code = RequestCodes.preferred(TriggerIdentities.ring(id))
        eventually { assertEquals(clock.at(2026, 11, 30, 9, 0), alarmClocks().single { requestCodeOf(it) == code }.triggerAtMs) }
        val events = runBlocking { app.data.events.between(0, Long.MAX_VALUE) }
        assertEquals(io.github.tweeter042.alarmclock.core.model.alarm.RingOutcome.DISMISSED_IN_ADVANCE, events.single().outcome)
        assertEquals(clock.at(2026, 11, 27, 9, 0), events.single().occurrenceAt)
    }

    @Test fun aOneTimeAlarmDismissedInAdvanceIsSwitchedOff() {
        val id = runBlocking { app.data.alarms.upsert(Alarm(time = AlarmTime(9, 0), repeat = RepeatRule.Once())) }
        val engine = rule.startEngine()
        runBlocking { engine.dismissUpcoming(id) }
        assertEquals(false, runBlocking { app.data.alarms.getAlarm(id) }?.enabled)
        eventually { assertNull(engine.nextAlarm.value) }
    }

    @Test fun notificationChannelsAreCreatedAtStart() {
        rule.startEngine()
        val manager = app.getSystemService(NotificationManager::class.java)
        EngineChannels.ALL.forEach { assertNotNull(it, manager.getNotificationChannel(it)) }
        val alarms = manager.getNotificationChannel(EngineChannels.ALARMS)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, alarms.importance)
        assertNull("the engine plays the alarm itself", alarms.sound)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, manager.getNotificationChannel(EngineChannels.TIMERS).importance)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, manager.getNotificationChannel(EngineChannels.SUNRISE).importance)
        assertEquals(NotificationManager.IMPORTANCE_MIN, manager.getNotificationChannel(EngineChannels.GUARD).importance)
        assertEquals(NotificationManager.IMPORTANCE_LOW, manager.getNotificationChannel(EngineChannels.UPCOMING).importance)
    }
}
