package io.github.tweeter042.alarmclock.core.alarm.schedule

import io.github.tweeter042.alarmclock.core.model.entity.ScheduledTrigger

/** Pure parts of the integrity check. */
internal object IntegrityMath {

    /** Tolerance when comparing AlarmManager's next alarm clock with ours. */
    const val TOLERANCE_MILLIS = 1_000L

    /**
     * Whether `AlarmManager.getNextAlarmClock()` is consistent with the engine's earliest
     * setAlarmClock trigger ([ourEarliest], null = none).
     *
     * - The system's next alarm clock is ours → its time must equal [ourEarliest].
     * - It belongs to another app → fine as long as it is not later than ours (ours would be
     *   reported instead if it were registered and earlier).
     * - There is none → we must not expect one either.
     */
    fun systemNextAlarmMatches(ourEarliest: Long?, systemTriggerAt: Long?, systemIsOurs: Boolean): Boolean = when {
        systemTriggerAt == null -> ourEarliest == null
        systemIsOurs -> ourEarliest != null && kotlin.math.abs(systemTriggerAt - ourEarliest) <= TOLERANCE_MILLIS
        else -> ourEarliest == null || systemTriggerAt <= ourEarliest + TOLERANCE_MILLIS
    }

    /**
     * Planned future triggers that are not registered as planned: missing from the ledger, recorded
     * with another time, or without a live PendingIntent.
     */
    fun missing(
        planned: List<PlannedTrigger>,
        ledger: List<ScheduledTrigger>,
        now: Long,
        isRegistered: (requestCode: Int) -> Boolean,
    ): List<PlannedTrigger> {
        val byIdentity = ledger.mapNotNull { entry -> TriggerIdentities.of(entry)?.let { it to entry } }.toMap()
        return planned
            .filter { it.triggerAt > now }
            .filter { trigger ->
                val entry = byIdentity[trigger.identity]
                entry == null || entry.triggerAt != trigger.triggerAt || !isRegistered(entry.requestCode)
            }
    }
}
