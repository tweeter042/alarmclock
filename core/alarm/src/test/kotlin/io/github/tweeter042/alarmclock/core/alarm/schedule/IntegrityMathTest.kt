package io.github.tweeter042.alarmclock.core.alarm.schedule

import io.github.tweeter042.alarmclock.core.model.entity.ScheduledTrigger
import io.github.tweeter042.alarmclock.core.model.entity.TriggerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegrityMathTest {

    @Test fun systemNextAlarmComparison() {
        assertTrue("nothing expected, nothing there", IntegrityMath.systemNextAlarmMatches(null, null, false))
        assertFalse("ours is missing", IntegrityMath.systemNextAlarmMatches(1_000L, null, false))
        assertTrue("ours, same time", IntegrityMath.systemNextAlarmMatches(1_000L, 1_000L, true))
        assertTrue("within tolerance", IntegrityMath.systemNextAlarmMatches(1_000L, 1_500L, true))
        assertFalse("ours, but another time", IntegrityMath.systemNextAlarmMatches(1_000L, 60_000L, true))
        assertFalse("ours registered although we expect none", IntegrityMath.systemNextAlarmMatches(null, 5_000L, true))
        assertTrue("another app rings earlier", IntegrityMath.systemNextAlarmMatches(10_000L, 5_000L, false))
        assertFalse("another app is reported although ours is earlier", IntegrityMath.systemNextAlarmMatches(5_000L, 10_000L, false))
        assertTrue("another app, we expect none", IntegrityMath.systemNextAlarmMatches(null, 10_000L, false))
    }

    @Test fun missingTriggersAreFound() {
        val now = 1_000L
        fun planned(id: Long, at: Long) = PlannedTrigger(TriggerIdentities.ring(id), TriggerKind.RING, id, at, at, method = RegisterMethod.ALARM_CLOCK)
        val plan = listOf(planned(1, 5_000), planned(2, 6_000), planned(3, 7_000), planned(4, 500))
        val ledger = listOf(
            ScheduledTrigger(101, TriggerKind.RING, 1, 5_000, 5_000),
            ScheduledTrigger(102, TriggerKind.RING, 2, 6_500, 6_500),
            ScheduledTrigger(103, TriggerKind.RING, 3, 7_000, 7_000),
        )
        val registered = setOf(101, 102)
        val missing = IntegrityMath.missing(plan, ledger, now) { it in registered }
        assertEquals(
            "2: recorded with another time, 3: PendingIntent gone; 4 is in the past",
            listOf(2L, 3L),
            missing.map { it.sourceId },
        )
    }
}
