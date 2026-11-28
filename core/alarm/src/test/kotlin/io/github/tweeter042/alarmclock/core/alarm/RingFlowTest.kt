@file:Suppress("DEPRECATION") // ShadowAlarmManager exposes the PendingIntent only as a deprecated field.

package io.github.tweeter042.alarmclock.core.alarm

import android.app.Notification
import android.app.NotificationManager
import android.os.Looper
import android.os.VibratorManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.tweeter042.alarmclock.core.alarm.engine.TriggerResult
import io.github.tweeter042.alarmclock.core.alarm.notify.EngineChannels
import io.github.tweeter042.alarmclock.core.alarm.notify.NotificationIds
import io.github.tweeter042.alarmclock.core.alarm.ring.AlarmRingService
import io.github.tweeter042.alarmclock.core.alarm.schedule.RequestCodes
import io.github.tweeter042.alarmclock.core.alarm.schedule.TriggerExtra
import io.github.tweeter042.alarmclock.core.alarm.schedule.TriggerIdentities
import io.github.tweeter042.alarmclock.core.alarm.schedule.TriggerPayload
import io.github.tweeter042.alarmclock.core.alarm.schedule.extraPayload
import io.github.tweeter042.alarmclock.core.alarm.testing.EngineTestRule
import io.github.tweeter042.alarmclock.core.alarm.testing.TestEngineApp
import io.github.tweeter042.alarmclock.core.alarm.testing.TestRingUi
import io.github.tweeter042.alarmclock.core.alarm.testing.eventually
import io.github.tweeter042.alarmclock.core.model.alarm.Alarm
import io.github.tweeter042.alarmclock.core.model.alarm.MissionConfig
import io.github.tweeter042.alarmclock.core.model.alarm.MissionType
import io.github.tweeter042.alarmclock.core.model.alarm.PreAlarmKind
import io.github.tweeter042.alarmclock.core.model.alarm.RepeatRule
import io.github.tweeter042.alarmclock.core.model.alarm.RingKind
import io.github.tweeter042.alarmclock.core.model.alarm.RingOutcome
import io.github.tweeter042.alarmclock.core.model.alarm.RingState
import io.github.tweeter042.alarmclock.core.model.alarm.SnoozeConfig
import io.github.tweeter042.alarmclock.core.model.alarm.SnoozeMode
import io.github.tweeter042.alarmclock.core.model.alarm.SoundConfig
import io.github.tweeter042.alarmclock.core.model.alarm.WeatherAdjustConfig
import io.github.tweeter042.alarmclock.core.model.alarm.WeatherCondition
import io.github.tweeter042.alarmclock.core.model.entity.ScheduledTrigger
import io.github.tweeter042.alarmclock.core.model.entity.TimerModel
import io.github.tweeter042.alarmclock.core.model.entity.TimerState
import io.github.tweeter042.alarmclock.core.model.entity.TriggerKind
import io.github.tweeter042.alarmclock.core.model.service.PlaceResult
import io.github.tweeter042.alarmclock.core.model.service.WeatherProvider
import io.github.tweeter042.alarmclock.core.model.service.WeatherSnapshot
import io.github.tweeter042.alarmclock.core.model.time.AlarmTime
import io.github.tweeter042.alarmclock.core.model.time.IsoDays
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = TestEngineApp::class)
class RingFlowTest {

    @get:Rule
    val rule = EngineTestRule()

    private val app get() = rule.app
    private val clock get() = app.clock
    private val notifications get() = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications

    private fun weekday(h: Int, m: Int, label: String = "Work") =
        Alarm(time = AlarmTime(h, m), repeat = RepeatRule.Weekly(IsoDays.WEEKDAYS), label = label)

    private fun save(alarm: Alarm): Alarm = runBlocking { app.data.alarms.getAlarm(app.data.alarms.upsert(alarm))!! }

    private fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

    private fun events() = runBlocking { app.data.events.between(0, Long.MAX_VALUE) }

    private fun ledger() = runBlocking { app.data.scheduleLedger.all() }

    @Test fun aRingSnoozesAndIsDismissed() {
        val alarm = save(Alarm(time = AlarmTime(9, 0), repeat = RepeatRule.Once(), label = "Once"))
        val engine = rule.startEngine()

        runBlocking { engine.triggerNow(RingKind.ALARM, alarm.id, alarm) }
        val ring = engine.activeRing.value!!
        assertEquals(RingState.RINGING, ring.state)
        assertEquals("an early ring takes over the next occurrence", clock.at(2026, 11, 27, 9, 0), ring.occurrenceAt)
        assertEquals(AlarmRingService::class.java.name, shadowOf(app).nextStartedService?.component?.className)
        eventually { assertTrue(app.hooks.calls.contains("started:${ring.ringId}")) }
        assertEquals(clock.now, events().single().startedAt)
        // The regular trigger for that occurrence moves on.
        eventually {
            val next = ledger().single { it.kind == TriggerKind.RING && it.sourceId == alarm.id }
            assertEquals(clock.at(2026, 11, 28, 9, 0), next.triggerAt)
        }

        assertTrue(runBlocking { engine.snooze(ring.ringId) })
        val snoozed = engine.activeRing.value!!
        assertEquals(RingState.SNOOZED, snoozed.state)
        assertEquals(clock.now + 5 * 60_000L, snoozed.snoozedUntil)
        val snoozeEnd = ledger().single { it.kind == TriggerKind.SNOOZE_END }
        assertEquals(ring.ringId, snoozeEnd.extraPayload?.ringId)
        assertEquals(snoozed.snoozedUntil, snoozeEnd.triggerAt)
        assertTrue(rule.shadowAlarms.scheduledAlarms.any { it.alarmClockInfo != null && it.triggerAtMs == snoozed.snoozedUntil })
        assertTrue(notifications.any { it.channelId == EngineChannels.SNOOZED })
        eventually { assertEquals(snoozed.snoozedUntil, engine.nextAlarm.value?.triggerAt) }
        assertEquals(1, events().single().snoozeCount)

        runBlocking { engine.dismiss(ring.ringId) }
        assertNull(engine.activeRing.value)
        val event = events().single()
        assertEquals(RingOutcome.DISMISSED, event.outcome)
        assertEquals(1, event.snoozeCount)
        assertNotNull(event.endedAt)
        assertEquals("a one-time alarm is switched off", false, runBlocking { app.data.alarms.getAlarm(alarm.id) }?.enabled)
        assertTrue(ledger().none { it.kind == TriggerKind.SNOOZE_END })
        assertTrue(notifications.none { it.channelId == EngineChannels.SNOOZED })
        eventually { assertEquals(RingOutcome.DISMISSED, app.hooks.finished.single().second) }
        eventually { assertNull(engine.nextAlarm.value) }
    }

    @Test fun theRingServiceGoesForegroundAtOnceAndPlaysTheRing() {
        val alarm = save(weekday(9, 30).copy(sound = SoundConfig(vibrateOnly = true)))
        val engine = rule.startEngine()
        val payload = TriggerPayload(
            kind = TriggerKind.RING,
            requestCode = RequestCodes.preferred(TriggerIdentities.ring(alarm.id)),
            sourceId = alarm.id,
            triggerAt = clock.at(2026, 11, 27, 9, 30),
            occurrenceAt = clock.at(2026, 11, 27, 9, 30),
            extra = null,
            label = "Work",
        )
        assertTrue(AlarmRingService.startForTrigger(app, payload))
        val start = shadowOf(app).nextStartedService
        val controller = Robolectric.buildService(AlarmRingService::class.java, start).create().startCommand(0, 1)
        val service = controller.get()

        val foreground = shadowOf(service).lastForegroundNotification
        assertNotNull("startForeground is called immediately", foreground)
        assertEquals(EngineChannels.ALARMS, foreground.channelId)
        assertEquals(Notification.CATEGORY_ALARM, foreground.category)
        assertNotNull(foreground.fullScreenIntent)
        val screenRingId = shadowOf(foreground.fullScreenIntent).savedIntent.getStringExtra(TestRingUi.EXTRA_RING_ID)

        val ring = eventually {
            idleMain()
            engine.activeRing.value!!.also { assertEquals(RingState.RINGING, it.state) }
        }
        assertEquals("the full-screen intent already carries the ring's id", ring.ringId, screenRingId)
        assertEquals(alarm.id, ring.sourceId)
        val vibrator = app.getSystemService(VibratorManager::class.java).defaultVibrator
        eventually {
            idleMain()
            assertTrue("vibrate-only alarms vibrate", shadowOf(vibrator).isVibrating)
        }

        runBlocking { engine.dismiss(ring.ringId) }
        eventually {
            idleMain()
            assertTrue(shadowOf(service).isStoppedBySelf)
        }
        assertFalse(shadowOf(vibrator).isVibrating)
        controller.destroy()
    }

    @Test fun aSecondRingWaitsForTheFirst() {
        val first = save(weekday(9, 0, "First"))
        val second = save(weekday(9, 5, "Second"))
        val engine = rule.startEngine()
        runBlocking { engine.triggerNow(RingKind.ALARM, first.id, first) }
        runBlocking { engine.triggerNow(RingKind.ALARM, second.id, second) }
        val a = engine.activeRing.value!!
        assertEquals(first.id, a.sourceId)
        assertEquals(2, engine.ringBook.value.all.size)

        runBlocking { engine.dismiss(a.ringId) }
        val b = engine.activeRing.value!!
        assertEquals(second.id, b.sourceId)
        assertEquals(RingState.RINGING, b.state)
        eventually { assertTrue(app.hooks.calls.indexOf("started:${b.ringId}") > app.hooks.calls.indexOf("finished:${a.ringId}:DISMISSED")) }
    }

    @Test fun aSnoozeEndTriggerResumesTheRing() {
        val alarm = save(weekday(9, 0))
        val engine = rule.startEngine()
        runBlocking { engine.triggerNow(RingKind.ALARM, alarm.id, alarm) }
        val ringId = engine.activeRing.value!!.ringId
        runBlocking { engine.snooze(ringId) }
        val entry = ledger().single { it.kind == TriggerKind.SNOOZE_END }

        clock.now = entry.triggerAt
        val result = runBlocking { engine.onRingTrigger(TriggerPayload.of(entry), preRingId = ringId) }
        assertEquals(TriggerResult.RESUMED, result)
        val resumed = engine.activeRing.value!!
        assertEquals(ringId, resumed.ringId)
        assertEquals(RingState.RINGING, resumed.state)
        assertEquals(1, resumed.snoozeCount)
        assertEquals(entry.triggerAt, resumed.startedAt)
        assertTrue("the fired one-off trigger is forgotten", ledger().none { it.kind == TriggerKind.SNOOZE_END })
    }

    @Test fun aSnoozeEndForALostRingStillRings() {
        val alarm = save(weekday(9, 0))
        val engine = rule.startEngine()
        val entry = ScheduledTrigger(
            RequestCodes.preferred(TriggerIdentities.snoozeEnd("lost")), TriggerKind.SNOOZE_END, alarm.id, clock.now, clock.now - 300_000,
            TriggerExtra(ringId = "lost", ringKind = RingKind.ALARM).encode(),
        )
        assertEquals(TriggerResult.RANG, runBlocking { engine.onRingTrigger(TriggerPayload.of(entry), "lost") })
        assertEquals(alarm.id, engine.activeRing.value?.sourceId)
    }

    @Test fun missionToSnoozeSnoozesAfterTheMissions() {
        val alarm = save(
            weekday(9, 0).copy(
                missions = listOf(MissionConfig(MissionType.MATH)),
                snooze = SnoozeConfig(mode = SnoozeMode.MISSION_TO_SNOOZE),
            ),
        )
        val engine = rule.startEngine()
        runBlocking { engine.triggerNow(RingKind.ALARM, alarm.id, alarm) }
        val ringId = engine.activeRing.value!!.ringId
        assertFalse("missions first", runBlocking { engine.snooze(ringId) })
        runBlocking { engine.startMission(ringId) }
        assertEquals(RingState.MISSION, engine.activeRing.value?.state)
        engine.onMissionInteraction(ringId)
        runBlocking { engine.completeMission(ringId) }
        assertEquals(RingState.SNOOZED, engine.activeRing.value?.state)
    }

    @Test fun timersRingAddAMinuteAndReset() {
        val engine = rule.startEngine()
        val timerId = runBlocking { app.data.timers.upsert(TimerModel(label = "Tea", durationMs = 180_000, state = TimerState.RUNNING, endAt = clock.now)) }
        val trigger = ScheduledTrigger(RequestCodes.preferred(TriggerIdentities.timer(timerId)), TriggerKind.TIMER, timerId, clock.now, clock.now)
        assertEquals(TriggerResult.RANG, runBlocking { engine.onRingTrigger(TriggerPayload.of(trigger), null) })
        val ring = engine.activeRing.value!!
        assertEquals(RingKind.TIMER, ring.kind)
        assertEquals(TimerState.RINGING, runBlocking { app.data.timers.getTimer(timerId) }?.state)

        assertTrue("+1 minute", runBlocking { engine.snooze(ring.ringId) })
        assertNull(engine.activeRing.value)
        val restarted = runBlocking { app.data.timers.getTimer(timerId) }!!
        assertEquals(TimerState.RUNNING, restarted.state)
        assertEquals(clock.now + 60_000, restarted.endAt)
        assertEquals(clock.now + 60_000, ledger().single { it.kind == TriggerKind.TIMER }.triggerAt)

        val again = ledger().single { it.kind == TriggerKind.TIMER }
        assertEquals(TriggerResult.RANG, runBlocking { engine.onRingTrigger(TriggerPayload.of(again), null) })
        runBlocking { engine.dismiss(engine.activeRing.value!!.ringId) }
        val reset = runBlocking { app.data.timers.getTimer(timerId) }!!
        assertEquals(TimerState.IDLE, reset.state)
        assertEquals(180_000L, reset.remainingMs)
        assertTrue(ledger().none { it.kind == TriggerKind.TIMER })
    }

    @Test fun triggersOfDeletedOrDisabledAlarmsDoNotRing() {
        val alarm = save(weekday(9, 0))
        val engine = rule.startEngine()
        val deleted = ScheduledTrigger(1, TriggerKind.RING, 999, clock.now, clock.now)
        assertEquals(TriggerResult.SKIPPED, runBlocking { engine.onRingTrigger(TriggerPayload.of(deleted), null) })
        runBlocking { app.data.alarms.setEnabled(alarm.id, false) }
        val disabled = ScheduledTrigger(2, TriggerKind.RING, alarm.id, clock.now, clock.now)
        assertEquals(TriggerResult.SKIPPED, runBlocking { engine.onRingTrigger(TriggerPayload.of(disabled), null) })
        assertNull(engine.activeRing.value)
    }

    @Test fun aFinishedOccurrenceDoesNotRingTwice() {
        val alarm = save(weekday(9, 30))
        val engine = rule.startEngine()
        val occurrence = clock.at(2026, 11, 27, 9, 30)
        runBlocking { engine.triggerNow(RingKind.ALARM, alarm.id, alarm) }
        runBlocking { engine.dismiss(engine.activeRing.value!!.ringId) }
        clock.now = occurrence
        val regular = ScheduledTrigger(RequestCodes.preferred(TriggerIdentities.ring(alarm.id)), TriggerKind.RING, alarm.id, occurrence, occurrence)
        assertEquals("rang early (smart wake) and was dismissed", TriggerResult.SKIPPED, runBlocking { engine.onRingTrigger(TriggerPayload.of(regular), null) })
    }

    @Test fun recentlyMissedAlarmsRingAtStartOlderOnesAreReported() {
        val recent = save(weekday(7, 55, "Recent"))
        val old = save(weekday(7, 20, "Old"))
        val once = save(Alarm(time = AlarmTime(7, 10), repeat = RepeatRule.Once(), label = "Once"))
        runBlocking {
            app.data.scheduleLedger.replaceAll(
                listOf(
                    ScheduledTrigger(101, TriggerKind.RING, recent.id, clock.at(2026, 11, 27, 7, 55), clock.at(2026, 11, 27, 7, 55)),
                    ScheduledTrigger(102, TriggerKind.RING, old.id, clock.at(2026, 11, 27, 7, 20), clock.at(2026, 11, 27, 7, 20)),
                    ScheduledTrigger(103, TriggerKind.RING, once.id, clock.at(2026, 11, 27, 7, 10), clock.at(2026, 11, 27, 7, 10)),
                ),
            )
        }
        val engine = rule.startEngine()

        val ringing = engine.activeRing.value!!
        assertEquals(recent.id, ringing.sourceId)
        assertEquals(clock.at(2026, 11, 27, 7, 55), ringing.occurrenceAt)
        val missed = events().filter { it.outcome == RingOutcome.MISSED }
        assertEquals(setOf(old.id, once.id), missed.map { it.sourceId }.toSet())
        assertTrue(notifications.count { it.channelId == EngineChannels.MISSED } >= 2)
        assertEquals("a missed one-time alarm must not ring tomorrow", false, runBlocking { app.data.alarms.getAlarm(once.id) }?.enabled)
        assertEquals(true, runBlocking { app.data.alarms.getAlarm(old.id) }?.enabled)
    }

    @Test fun aRingSurvivesProcessDeath() {
        val alarm = save(weekday(9, 0))
        val engine = rule.startEngine()
        runBlocking { engine.triggerNow(RingKind.ALARM, alarm.id, alarm) }
        val ringId = engine.activeRing.value!!.ringId
        shadowOf(app).clearStartedServices()

        val reborn = app.replaceEngine()
        reborn.start()
        runBlocking { reborn.awaitStartup() }
        val restored = reborn.activeRing.value!!
        assertEquals(ringId, restored.ringId)
        assertEquals(RingState.RINGING, restored.state)
        assertEquals("the ring service is started again", AlarmRingService::class.java.name, shadowOf(app).nextStartedService?.component?.className)
    }

    @Test fun aSnoozedRingSurvivesProcessDeathWithItsTrigger() {
        val alarm = save(weekday(9, 0))
        val engine = rule.startEngine()
        runBlocking { engine.triggerNow(RingKind.ALARM, alarm.id, alarm) }
        val ringId = engine.activeRing.value!!.ringId
        runBlocking { engine.snooze(ringId) }
        // The process dies and every PendingIntent is lost (reboot).
        rule.shadowAlarms.scheduledAlarms.forEach { rule.alarmManager.cancel(it.operation!!); it.operation!!.cancel() }

        val reborn = app.replaceEngine()
        reborn.start()
        runBlocking { reborn.awaitStartup() }
        assertEquals(RingState.SNOOZED, reborn.activeRing.value?.state)
        val code = ledger().single { it.kind == TriggerKind.SNOOZE_END }.requestCode
        assertTrue(rule.shadowAlarms.scheduledAlarms.any { shadowOf(it.operation).requestCode == code && it.alarmClockInfo != null })
    }

    @Test fun forceStopIsDetectedAndReported() {
        android.provider.Settings.Global.putInt(app.contentResolver, android.provider.Settings.Global.BOOT_COUNT, 7)
        save(weekday(9, 0))
        rule.startEngine()
        rule.shadowAlarms.scheduledAlarms.forEach { rule.alarmManager.cancel(it.operation!!); it.operation!!.cancel() }

        val reborn = app.replaceEngine()
        reborn.start()
        runBlocking { reborn.awaitStartup() }
        assertNotNull(shadowOf(app.getSystemService(NotificationManager::class.java)).getNotification(NotificationIds.RESTORED))
        assertTrue(rule.shadowAlarms.scheduledAlarms.any { it.alarmClockInfo != null })
    }

    @Test fun anUpcomingNoticeCanDismissTheAlarmInAdvance() {
        val alarm = save(weekday(9, 30))
        val engine = rule.startEngine()
        val entry = ledger().single { it.kind == TriggerKind.PRE_ALARM && it.extraPayload?.preAlarm == PreAlarmKind.UPCOMING_NOTICE }
        clock.now = entry.triggerAt
        runBlocking { engine.onPreAlarmTrigger(TriggerPayload.of(entry)) }
        val notice = notifications.single { it.channelId == EngineChannels.UPCOMING }
        assertTrue(app.hooks.calls.contains("preAlarm:UPCOMING_NOTICE"))

        val dismissNow = notice.actions.single().actionIntent
        dismissNow.send()
        idleMain()
        eventually { assertEquals(RingOutcome.DISMISSED_IN_ADVANCE, events().single().outcome) }
        eventually { assertTrue(notifications.none { it.channelId == EngineChannels.UPCOMING }) }
        assertEquals(clock.at(2026, 11, 27, 9, 30), runBlocking { app.data.alarms.getAlarm(alarm.id) }?.skippedOccurrenceAt)
    }

    @Test fun badWeatherRingsEarlyAndTheRegularTriggerIsSkipped() {
        app.dependencies = { it.copy(weather = RainyWeather) }
        val alarm = save(weekday(9, 30).copy(weatherAdjust = WeatherAdjustConfig(enabled = true, minutesEarlier = 20)))
        val engine = rule.startEngine()
        val entry = ledger().single { it.kind == TriggerKind.PRE_ALARM && it.extraPayload?.preAlarm == PreAlarmKind.WEATHER_CHECK }
        assertEquals(clock.at(2026, 11, 27, 9, 10), entry.triggerAt)
        clock.now = entry.triggerAt
        runBlocking { engine.onPreAlarmTrigger(TriggerPayload.of(entry)) }
        val ring = engine.activeRing.value!!
        assertEquals(alarm.id, ring.sourceId)
        assertEquals(clock.at(2026, 11, 27, 9, 30), ring.occurrenceAt)

        val regular = ScheduledTrigger(RequestCodes.preferred(TriggerIdentities.ring(alarm.id)), TriggerKind.RING, alarm.id, ring.occurrenceAt, ring.occurrenceAt)
        assertEquals(TriggerResult.SKIPPED, runBlocking { engine.onRingTrigger(TriggerPayload.of(regular), null) })
        eventually { assertEquals(clock.at(2026, 11, 30, 9, 30), ledger().single { it.kind == TriggerKind.RING }.triggerAt) }
    }

    @Test fun theNotificationButtonsSnoozeAndDismiss() {
        val alarm = save(weekday(9, 0))
        val engine = rule.startEngine()
        runBlocking { engine.triggerNow(RingKind.ALARM, alarm.id, alarm) }
        val ring = engine.activeRing.value!!
        val notification = engine.notifier.ring(ring, engine.settingsSnapshot())
        assertEquals(2, notification.actions.size)
        notification.actions.first().actionIntent.send()
        idleMain()
        eventually { assertEquals(RingState.SNOOZED, engine.activeRing.value?.state) }

        val snoozedNotice = eventually { notifications.single { it.channelId == EngineChannels.SNOOZED } }
        snoozedNotice.actions.single().actionIntent.send()
        idleMain()
        eventually { assertNull(engine.activeRing.value) }
    }

    @Test fun missionAlarmsOpenTheRingScreenFromTheNotification() {
        val alarm = save(weekday(9, 0).copy(missions = listOf(MissionConfig(MissionType.MATH))))
        val engine = rule.startEngine()
        runBlocking { engine.triggerNow(RingKind.ALARM, alarm.id, alarm) }
        val notification = engine.notifier.ring(engine.activeRing.value!!, engine.settingsSnapshot())
        val dismiss = notification.actions.last().actionIntent
        assertTrue("dismiss opens the ring screen", shadowOf(dismiss).isActivity)
        assertEquals(TestRingUi.RING_ACTIVITY, shadowOf(dismiss).savedIntent.component?.className)
    }

    private object RainyWeather : WeatherProvider {
        override val latest: Flow<WeatherSnapshot?> = flowOf(null)
        override suspend fun refresh(force: Boolean): Result<WeatherSnapshot> = Result.failure(UnsupportedOperationException())
        override suspend fun cached(maxAgeMinutes: Int): WeatherSnapshot? = null
        override suspend fun conditionAt(atMillis: Long): Result<WeatherCondition> = Result.success(WeatherCondition.RAIN)
        override suspend fun searchPlaces(query: String, languageTag: String): Result<List<PlaceResult>> = Result.success(emptyList())
    }

}
