package io.github.tweeter042.alarmclock.core.alarm.ring

import io.github.tweeter042.alarmclock.core.model.alarm.ActiveRing
import io.github.tweeter042.alarmclock.core.model.alarm.Alarm
import io.github.tweeter042.alarmclock.core.model.alarm.ButtonAction
import io.github.tweeter042.alarmclock.core.model.alarm.MissionConfig
import io.github.tweeter042.alarmclock.core.model.alarm.MissionType
import io.github.tweeter042.alarmclock.core.model.alarm.RingKind
import io.github.tweeter042.alarmclock.core.model.alarm.SnoozeConfig
import io.github.tweeter042.alarmclock.core.model.alarm.SnoozeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GesturePolicyTest {

    private fun ring(alarm: Alarm = Alarm(), kind: RingKind = RingKind.ALARM, snoozeCount: Int = 0) =
        ActiveRing("r", kind, 1, alarm, 0L, 0L, snoozeCount = snoozeCount)

    private val math = listOf(MissionConfig(MissionType.MATH))

    @Test fun plainAlarmsDoWhatTheButtonSays() {
        assertEquals(GestureResult.SNOOZE, GesturePolicy.resolve(ButtonAction.SNOOZE, ring()))
        assertEquals(GestureResult.DISMISS, GesturePolicy.resolve(ButtonAction.DISMISS, ring()))
        assertEquals(GestureResult.NONE, GesturePolicy.resolve(ButtonAction.NONE, ring()))
    }

    @Test fun missionsCannotBeSkippedByAButton() {
        val withMissions = ring(Alarm(missions = math))
        assertEquals(GestureResult.OPEN_RING_SCREEN, GesturePolicy.resolve(ButtonAction.DISMISS, withMissions))
        assertEquals("snoozing a mission alarm is still allowed", GestureResult.SNOOZE, GesturePolicy.resolve(ButtonAction.SNOOZE, withMissions))
        val missionToSnooze = ring(Alarm(missions = math, snooze = SnoozeConfig(mode = SnoozeMode.MISSION_TO_SNOOZE)))
        assertEquals(GestureResult.OPEN_RING_SCREEN, GesturePolicy.resolve(ButtonAction.SNOOZE, missionToSnooze))
    }

    @Test fun aSnoozeThatIsNotAllowedOpensTheRingScreen() {
        val maxed = ring(Alarm(snooze = SnoozeConfig(maxCount = 2)), snoozeCount = 2)
        assertEquals(GestureResult.OPEN_RING_SCREEN, GesturePolicy.resolve(ButtonAction.SNOOZE, maxed))
        val off = ring(Alarm(snooze = SnoozeConfig(enabled = false)))
        assertEquals(GestureResult.OPEN_RING_SCREEN, GesturePolicy.resolve(ButtonAction.SNOOZE, off))
    }

    @Test fun timersSnoozeByAMinuteAndStop() {
        val timer = ring(Alarm(snooze = SnoozeConfig(enabled = false), missions = math), kind = RingKind.TIMER)
        assertEquals(GestureResult.SNOOZE, GesturePolicy.resolve(ButtonAction.SNOOZE, timer))
        assertEquals(GestureResult.DISMISS, GesturePolicy.resolve(ButtonAction.DISMISS, timer))
    }

    @Test fun mediaKeysCoverSPenHeadsetsAndBuds() {
        assertEquals(setOf(85, 126, 127, 79, 87), GesturePolicy.MEDIA_KEYS)
    }
}

class FlipDetectorTest {

    private fun feed(detector: FlipDetector, z: Float, from: Long, until: Long, step: Long = 100): Int {
        var fired = 0
        var t = from
        while (t <= until) {
            if (detector.onSample(z, t)) fired++
            t += step
        }
        return fired
    }

    @Test fun flippingFaceDownForASecondFiresOnce() {
        val detector = FlipDetector()
        assertEquals(0, feed(detector, 9.8f, 0, 1_000))
        assertEquals(1, feed(detector, -9.8f, 1_100, 4_000))
        assertEquals("only once", 0, feed(detector, 9.8f, 4_100, 5_000) + feed(detector, -9.8f, 5_100, 8_000))
    }

    @Test fun aPhoneAlreadyFaceDownDoesNotSnoozeAtOnce() {
        val detector = FlipDetector()
        assertEquals(0, feed(detector, -9.8f, 0, 5_000))
        assertEquals(0, feed(detector, 9.8f, 5_100, 6_000))
        assertEquals(1, feed(detector, -9.8f, 6_100, 9_000))
    }

    @Test fun aShortTurnIsIgnored() {
        val detector = FlipDetector()
        feed(detector, 9.8f, 0, 500)
        assertEquals(0, feed(detector, -9.8f, 600, 1_000))
        assertEquals(0, feed(detector, 9.8f, 1_100, 1_500))
        assertEquals(0, feed(detector, -9.8f, 1_600, 2_100))
    }

    @Test fun tiltingResetsTheHoldTimer() {
        val detector = FlipDetector()
        feed(detector, 9.8f, 0, 500)
        assertEquals(0, feed(detector, -9.8f, 600, 1_400))
        assertEquals(0, feed(detector, -5f, 1_500, 2_500))
        assertEquals(0, feed(detector, -9.8f, 2_600, 3_000))
        assertEquals(1, feed(detector, -9.8f, 3_100, 4_500))
    }

    @Test fun keyDebouncerSwallowsRepeats() {
        val debouncer = KeyDebouncer(windowMillis = 800)
        assertTrue(debouncer.accept(10_000))
        assertFalse(debouncer.accept(10_200))
        assertFalse(debouncer.accept(10_799))
        assertTrue(debouncer.accept(10_800))
    }
}
