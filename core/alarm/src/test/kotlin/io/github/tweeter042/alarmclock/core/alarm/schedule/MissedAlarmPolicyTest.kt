package io.github.tweeter042.alarmclock.core.alarm.schedule

import io.github.tweeter042.alarmclock.core.model.alarm.RingKind
import io.github.tweeter042.alarmclock.core.model.entity.ScheduledTrigger
import io.github.tweeter042.alarmclock.core.model.entity.TriggerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MissedAlarmPolicyTest {
    private val now = 1_795_000_000_000L
    private val minute = 60_000L

    private fun trigger(kind: TriggerKind, sourceId: Long, triggerAt: Long, code: Int = sourceId.toInt()) =
        ScheduledTrigger(code, kind, sourceId, triggerAt, triggerAt)

    private fun decide(vararg ledger: ScheduledTrigger, grace: Int = 15, handled: Set<OccurrenceKey> = emptySet()) =
        MissedAlarmPolicy.decide(ledger.toList(), now, grace, isHandled = { it in handled }).associate { it.trigger.requestCode to it.action }

    @Test fun recentMissesRingNowOlderOnesAreReported() {
        val result = decide(
            trigger(TriggerKind.RING, 1, now - 10 * minute),
            trigger(TriggerKind.RING, 2, now - 16 * minute),
            trigger(TriggerKind.CALENDAR, 3, now - 1 * minute),
            trigger(TriggerKind.CALENDAR, 4, now - 60 * minute),
        )
        assertEquals(MissedAlarmPolicy.Action.RING_NOW, result[1])
        assertEquals(MissedAlarmPolicy.Action.NOTIFY_MISSED, result[2])
        assertEquals(MissedAlarmPolicy.Action.RING_NOW, result[3])
        assertEquals(MissedAlarmPolicy.Action.NOTIFY_MISSED, result[4])
    }

    @Test fun theGraceBoundaryIsInclusive() {
        assertEquals(MissedAlarmPolicy.Action.RING_NOW, decide(trigger(TriggerKind.RING, 1, now - 15 * minute))[1])
        assertEquals(MissedAlarmPolicy.Action.NOTIFY_MISSED, decide(trigger(TriggerKind.RING, 1, now - 15 * minute - 1))[1])
        assertEquals("grace 0: report everything", MissedAlarmPolicy.Action.NOTIFY_MISSED, decide(trigger(TriggerKind.RING, 1, now - 1), grace = 0)[1])
    }

    @Test fun handledOccurrencesAndFutureTriggersAreLeftAlone() {
        val ringing = trigger(TriggerKind.RING, 1, now - minute)
        val result = decide(
            ringing,
            trigger(TriggerKind.RING, 2, now + minute),
            handled = setOf(OccurrenceKey(RingKind.ALARM, 1, now - minute)),
        )
        assertEquals(MissedAlarmPolicy.Action.IGNORE, result[1])
        assertTrue("future triggers are not missed", 2 !in result)
    }

    @Test fun onlyRingCreatingTriggersCount() {
        val result = decide(
            trigger(TriggerKind.PRE_ALARM, 1, now - minute),
            trigger(TriggerKind.SNOOZE_END, 2, now - minute),
            trigger(TriggerKind.AWAKE_CHECK, 3, now - minute),
        )
        assertTrue(result.isEmpty())
    }

    @Test fun timersAlwaysRingNapsFollowTheGraceTestsAreDropped() {
        val result = decide(
            trigger(TriggerKind.TIMER, 1, now - 3 * 60 * minute),
            trigger(TriggerKind.NAP, 2, now - 5 * minute),
            trigger(TriggerKind.NAP, 3, now - 90 * minute),
            trigger(TriggerKind.TEST, 4, now - 2 * minute),
            trigger(TriggerKind.TEST, 5, now - 90 * minute),
        )
        assertEquals(MissedAlarmPolicy.Action.RING_NOW, result[1])
        assertEquals(MissedAlarmPolicy.Action.RING_NOW, result[2])
        assertEquals(MissedAlarmPolicy.Action.NOTIFY_MISSED, result[3])
        assertEquals(MissedAlarmPolicy.Action.RING_NOW, result[4])
        assertEquals(MissedAlarmPolicy.Action.DROP, result[5])
    }

    @Test fun staleEntriesAreIgnored() {
        val result = decide(trigger(TriggerKind.RING, 1, now - MissedAlarmPolicy.MAX_AGE_MILLIS - 1))
        assertEquals(MissedAlarmPolicy.Action.IGNORE, result[1])
    }

    @Test fun duplicateOccurrencesAreDecidedOnce() {
        val decisions = MissedAlarmPolicy.decide(
            listOf(trigger(TriggerKind.RING, 1, now - minute, code = 10), trigger(TriggerKind.RING, 1, now - minute, code = 11)),
            now,
            15,
            isHandled = { false },
        )
        assertEquals(1, decisions.size)
    }
}
