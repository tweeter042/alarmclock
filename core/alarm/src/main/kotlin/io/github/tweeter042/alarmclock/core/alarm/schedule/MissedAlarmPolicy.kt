package io.github.tweeter042.alarmclock.core.alarm.schedule

import io.github.tweeter042.alarmclock.core.model.alarm.RingKind
import io.github.tweeter042.alarmclock.core.model.entity.ScheduledTrigger
import io.github.tweeter042.alarmclock.core.model.entity.TriggerKind

/** Identifies one occurrence of one ring source, e.g. (ALARM, alarm 3, Mon 06:30). */
internal data class OccurrenceKey(val kind: RingKind, val sourceId: Long, val occurrenceAt: Long)

/** The ring kind a ring-producing trigger creates (null for triggers that resume an existing ring). */
internal val TriggerKind.newRingKind: RingKind?
    get() = when (this) {
        TriggerKind.RING -> RingKind.ALARM
        TriggerKind.CALENDAR -> RingKind.CALENDAR
        TriggerKind.NAP -> RingKind.NAP
        TriggerKind.TEST -> RingKind.TEST
        TriggerKind.TIMER -> RingKind.TIMER
        TriggerKind.PRE_ALARM, TriggerKind.SNOOZE_END, TriggerKind.AWAKE_CHECK -> null
    }

internal fun ScheduledTrigger.occurrenceKey(): OccurrenceKey? = kind.newRingKind?.let { OccurrenceKey(it, sourceId, occurrenceAt) }

/**
 * What to do, at app start or boot, with ledger triggers whose time has passed without ringing
 * (device off, Device care restart, process killed while the trigger fired, force stop).
 *
 * Rules (pure; unit tested):
 * - Only triggers that create a ring are considered (alarms, calendar alarms, naps, tests, timers).
 *   Snooze ends and awake checks belong to rings that are still persisted and are re-registered
 *   from the ring state (a past time fires at once).
 * - A trigger is "handled" if its occurrence already has an event or a live ring — then nothing
 *   happens. This also deduplicates a trigger that is being delivered right now.
 * - Older than [maxAgeMillis] (default 2 days): ignored (stale ledger).
 * - Timers always ring now: a finished countdown is still news.
 * - Otherwise, missed by at most `graceMinutes`: ring now. Missed by more: alarms, calendar alarms
 *   and naps get a MISSED event and (if enabled) a "Missed alarm" notification; tests are dropped.
 */
internal object MissedAlarmPolicy {

    enum class Action { RING_NOW, NOTIFY_MISSED, DROP, IGNORE }

    data class Decision(val trigger: ScheduledTrigger, val key: OccurrenceKey, val action: Action)

    const val MAX_AGE_MILLIS: Long = 2L * 24 * 60 * 60 * 1000

    fun decide(
        ledger: List<ScheduledTrigger>,
        now: Long,
        graceMinutes: Int,
        isHandled: (OccurrenceKey) -> Boolean,
        maxAgeMillis: Long = MAX_AGE_MILLIS,
    ): List<Decision> {
        val grace = graceMinutes.coerceAtLeast(0) * 60_000L
        return ledger
            .asSequence()
            .filter { it.triggerAt < now }
            .mapNotNull { trigger -> trigger.occurrenceKey()?.let { trigger to it } }
            .distinctBy { it.second }
            .sortedBy { it.first.triggerAt }
            .map { (trigger, key) ->
                val late = now - trigger.triggerAt
                val action = when {
                    isHandled(key) -> Action.IGNORE
                    late > maxAgeMillis -> Action.IGNORE
                    trigger.kind == TriggerKind.TIMER -> Action.RING_NOW
                    late <= grace -> Action.RING_NOW
                    trigger.kind == TriggerKind.TEST -> Action.DROP
                    else -> Action.NOTIFY_MISSED
                }
                Decision(trigger, key, action)
            }
            .toList()
    }
}
