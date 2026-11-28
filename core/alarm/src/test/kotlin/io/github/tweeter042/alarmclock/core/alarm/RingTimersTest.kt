package io.github.tweeter042.alarmclock.core.alarm

import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.tweeter042.alarmclock.core.alarm.notify.EngineChannels
import io.github.tweeter042.alarmclock.core.alarm.schedule.TriggerPayload
import io.github.tweeter042.alarmclock.core.alarm.testing.EngineTestRule
import io.github.tweeter042.alarmclock.core.alarm.testing.TestEngineApp
import io.github.tweeter042.alarmclock.core.alarm.testing.eventually
import io.github.tweeter042.alarmclock.core.model.alarm.Alarm
import io.github.tweeter042.alarmclock.core.model.alarm.AutoAction
import io.github.tweeter042.alarmclock.core.model.alarm.AwakeCheckConfig
import io.github.tweeter042.alarmclock.core.model.alarm.DismissConfig
import io.github.tweeter042.alarmclock.core.model.alarm.MissionConfig
import io.github.tweeter042.alarmclock.core.model.alarm.MissionType
import io.github.tweeter042.alarmclock.core.model.alarm.RepeatRule
import io.github.tweeter042.alarmclock.core.model.alarm.RingKind
import io.github.tweeter042.alarmclock.core.model.alarm.RingOutcome
import io.github.tweeter042.alarmclock.core.model.alarm.RingState
import io.github.tweeter042.alarmclock.core.model.alarm.SnoozeConfig
import io.github.tweeter042.alarmclock.core.model.entity.TriggerKind
import io.github.tweeter042.alarmclock.core.model.time.AlarmTime
import io.github.tweeter042.alarmclock.core.model.time.IsoDays
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The in-process ring timers (auto action, mission idle, awake-check answer), driven by the test clock. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = TestEngineApp::class)
class RingTimersTest {

    @get:Rule
    val rule = EngineTestRule()

    private val app get() = rule.app
    private val clock get() = app.clock

    private fun ringing(alarm: Alarm): Pair<DefaultAlarmEngine, String> {
        val saved = runBlocking { app.data.alarms.getAlarm(app.data.alarms.upsert(alarm))!! }
        val engine = rule.startEngine()
        runBlocking { engine.triggerNow(RingKind.ALARM, saved.id, saved) }
        return engine to engine.activeRing.value!!.ringId
    }

    private fun base() = Alarm(time = AlarmTime(9, 0), repeat = RepeatRule.Weekly(IsoDays.WEEKDAYS))

    @Test fun theAutoActionSnoozesAfterTheRingDuration() {
        val (engine, ringId) = ringing(base().copy(dismiss = DismissConfig(ringDurationMinutes = 1, autoAction = AutoAction.SNOOZE)))
        clock.now += 30_000
        Thread.sleep(1_500)
        assertEquals("not yet", RingState.RINGING, engine.activeRing.value?.state)
        clock.now += 31_000
        eventually {
            val ring = engine.activeRing.value!!
            assertEquals(ringId, ring.ringId)
            assertEquals(RingState.SNOOZED, ring.state)
            assertEquals(1, ring.snoozeCount)
        }
    }

    @Test fun anAlarmThatCannotSnoozeStopsAutomaticallyAndSaysSo() {
        val (engine, _) = ringing(
            base().copy(
                snooze = SnoozeConfig(enabled = false),
                dismiss = DismissConfig(ringDurationMinutes = 1, autoAction = AutoAction.SNOOZE),
            ),
        )
        clock.now += 61_000
        eventually { assertEquals(null, engine.activeRing.value) }
        // The state is published first; the event log and notification follow.
        eventually { assertEquals(RingOutcome.AUTO_DISMISSED, runBlocking { app.data.events.between(0, Long.MAX_VALUE) }.single().outcome) }
        eventually {
            val notifications = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications
            assertTrue(notifications.any { it.channelId == EngineChannels.MISSED })
        }
    }

    @Test fun anIdleMissionReturnsToFullRinging() {
        val (engine, ringId) = ringing(
            base().copy(
                missions = listOf(MissionConfig(MissionType.MATH)),
                dismiss = DismissConfig(lowerVolumeDuringMission = true, missionIdleTimeoutSeconds = 30),
            ),
        )
        runBlocking { engine.startMission(ringId) }
        assertEquals(RingState.MISSION, engine.activeRing.value?.state)

        clock.now += 20_000
        engine.onMissionInteraction(ringId)
        clock.now += 20_000
        Thread.sleep(1_500)
        assertEquals("the interaction moved the deadline", RingState.MISSION, engine.activeRing.value?.state)

        clock.now += 15_000
        eventually { assertEquals(RingState.RINGING, engine.activeRing.value?.state) }
    }

    @Test fun anUnansweredAwakeCheckRingsAgain() {
        val (engine, ringId) = ringing(base().copy(awakeCheck = AwakeCheckConfig(enabled = true, delayMinutes = 5, responseSeconds = 60)))
        runBlocking { engine.dismiss(ringId) }
        assertEquals(RingState.AWAKE_CHECK, engine.activeRing.value?.state)

        val check = runBlocking { app.data.scheduleLedger.all() }.single { it.kind == TriggerKind.AWAKE_CHECK }
        assertEquals(clock.now + 5 * 60_000L, check.triggerAt)
        clock.now = check.triggerAt
        runBlocking { engine.onRingTrigger(TriggerPayload.of(check), ringId) }
        val prompt = engine.activeRing.value!!
        assertTrue(prompt.awakePromptShowing)

        clock.now += 61_000
        eventually {
            val again = engine.activeRing.value!!
            assertEquals(RingState.RINGING, again.state)
            assertEquals(ringId, again.ringId)
        }
        eventually { assertEquals(false, runBlocking { app.data.events.between(0, Long.MAX_VALUE) }.single().awakeConfirmed) }
    }

    @Test fun confirmingTheAwakeCheckFinishesTheRing() {
        val (engine, ringId) = ringing(base().copy(awakeCheck = AwakeCheckConfig(enabled = true)))
        runBlocking { engine.dismiss(ringId) }
        val check = runBlocking { app.data.scheduleLedger.all() }.single { it.kind == TriggerKind.AWAKE_CHECK }
        clock.now = check.triggerAt
        runBlocking { engine.onRingTrigger(TriggerPayload.of(check), ringId) }
        runBlocking { engine.confirmAwake(ringId) }
        assertEquals(null, engine.activeRing.value)
        val event = runBlocking { app.data.events.between(0, Long.MAX_VALUE) }.single()
        assertEquals(RingOutcome.DISMISSED, event.outcome)
        assertEquals(true, event.awakeConfirmed)
        assertTrue(runBlocking { app.data.scheduleLedger.all() }.none { it.kind == TriggerKind.AWAKE_CHECK })
    }
}
