package io.github.tweeter042.alarmclock.core.alarm.schedule

import io.github.tweeter042.alarmclock.core.model.alarm.ActiveRing
import io.github.tweeter042.alarmclock.core.model.alarm.Alarm
import io.github.tweeter042.alarmclock.core.model.alarm.AlarmScheduleCalculator
import io.github.tweeter042.alarmclock.core.model.alarm.NextAlarm
import io.github.tweeter042.alarmclock.core.model.alarm.PreAlarmKind
import io.github.tweeter042.alarmclock.core.model.alarm.RingKind
import io.github.tweeter042.alarmclock.core.model.alarm.RingState
import io.github.tweeter042.alarmclock.core.model.entity.AlarmGroup
import io.github.tweeter042.alarmclock.core.model.entity.ScheduledTrigger
import io.github.tweeter042.alarmclock.core.model.entity.TimerModel
import io.github.tweeter042.alarmclock.core.model.entity.TimerState
import io.github.tweeter042.alarmclock.core.model.entity.TriggerKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** How a trigger is handed to AlarmManager. */
internal enum class RegisterMethod {
    /** `setAlarmClock`: exact, exempt from Doze, shown as the next alarm (status bar, AOD). */
    ALARM_CLOCK,

    /** `setExactAndAllowWhileIdle`: pre-alarm events and the midnight recompute. */
    EXACT_WHILE_IDLE,
}

/** A trigger the engine wants registered, before it has a request code. */
internal data class PlannedTrigger(
    val identity: TriggerIdentity,
    val kind: TriggerKind,
    val sourceId: Long,
    val triggerAt: Long,
    val occurrenceAt: Long,
    val extra: TriggerExtra? = null,
    /** Shown in the ring notification until the ring has loaded its alarm. Never logged. */
    val label: String = "",
    val method: RegisterMethod,
) {
    fun toScheduled(requestCode: Int): ScheduledTrigger =
        ScheduledTrigger(requestCode, kind, sourceId, triggerAt, occurrenceAt, extra?.encode())

    override fun toString(): String = "$identity@$triggerAt"
}

/** Everything a full reschedule depends on (a snapshot; no I/O happens while planning). */
internal data class PlanInput(
    val now: Long,
    val zone: ZoneId,
    val alarms: List<Alarm>,
    val groups: List<AlarmGroup>,
    /** Enabled holiday dates (used for alarms with skipHolidays). */
    val holidays: Set<LocalDate>,
    /** settings.reliability.upcomingNoticeMinutes (0 = off). */
    val upcomingNoticeMinutes: Int,
    /** settings.weather.enabled: weather checks are only planned when weather is on. */
    val weatherEnabled: Boolean,
    /** Next generated occurrence per calendar rule, already computed. */
    val calendar: List<CalendarOccurrence> = emptyList(),
    /** Label of the alarm each calendar rule generates (rule id → label). */
    val calendarLabels: Map<Long, String> = emptyMap(),
    /** Every ring the engine currently holds (current + queued + parked). */
    val rings: List<ActiveRing> = emptyList(),
    /**
     * False if the ring state could not be loaded: the ledger's snooze-end and awake-check triggers
     * are then kept as they are instead of being derived from [rings].
     */
    val ringsKnown: Boolean = true,
    /** The ledger before this reschedule (source of one-off triggers: naps, tests, timers, kicks). */
    val ledger: List<ScheduledTrigger> = emptyList(),
    /** All timers; null if they could not be read (the ledger's timer triggers are then kept). */
    val timers: List<TimerModel>? = emptyList(),
    /** Occurrences that already rang, were dismissed in advance, or were missed. */
    val handled: Set<OccurrenceKey> = emptySet(),
)

internal data class Plan(
    val triggers: List<PlannedTrigger>,
    val next: NextAlarm?,
    /** alarm id → planned occurrence (epoch millis), for alarms that have one. */
    val occurrences: Map<Long, Long>,
)

/**
 * Pure trigger planning: turns repositories + ring state into the complete list of AlarmManager
 * registrations. Occurrence times come ONLY from [AlarmScheduleCalculator].
 *
 * Per enabled alarm (group enabled), at its next occurrence that is not already handled:
 * - RING at the occurrence (setAlarmClock);
 * - pre-alarm events (setExactAndAllowWhileIdle), each only if its time is still in the future:
 *   UPCOMING_NOTICE (`upcomingNoticeMinutes` before), SUNRISE_START (sunrise duration before),
 *   SMART_WAKE_START (smart-wake window before), WEATHER_CHECK (`minutesEarlier` before; only with
 *   weather enabled) and PREFETCH ([PREFETCH_MINUTES] before).
 *
 * Plus: calendar alarms, SNOOZE_END / AWAKE_CHECK for parked rings, naps/tests/kicks that have not
 * fired yet, running timers, and the local-midnight recompute.
 */
internal object TriggerPlanner {

    const val PREFETCH_MINUTES = 10
    /** The midnight recompute runs a few seconds after local midnight. */
    const val MIDNIGHT_OFFSET_MILLIS = 5_000L
    private const val MAX_HANDLED_SKIPS = 16

    fun plan(input: PlanInput): Plan {
        val triggers = ArrayList<PlannedTrigger>()
        val occurrences = HashMap<Long, Long>()

        for (alarm in input.alarms) {
            val occurrence = nextOccurrence(alarm, input.now, input.zone, input.groups, input.holidays, input.handled) ?: continue
            occurrences[alarm.id] = occurrence
            triggers += alarmTriggers(alarm, occurrence, input)
        }

        for (calendar in input.calendar) {
            if (OccurrenceKey(RingKind.CALENDAR, calendar.ruleId, calendar.alarmAt) in input.handled) continue
            triggers += PlannedTrigger(
                identity = TriggerIdentities.calendar(calendar.ruleId),
                kind = TriggerKind.CALENDAR,
                sourceId = calendar.ruleId,
                triggerAt = calendar.alarmAt,
                occurrenceAt = calendar.alarmAt,
                extra = TriggerExtra(title = calendar.eventTitle),
                label = input.calendarLabels[calendar.ruleId] ?: calendar.eventTitle,
                method = RegisterMethod.ALARM_CLOCK,
            )
        }

        triggers += if (input.ringsKnown) ringTriggers(input.rings) else keptRingTriggers(input.ledger)
        triggers += oneOffTriggers(input)
        triggers += timerTriggers(input)
        triggers += PlannedTrigger(
            identity = TriggerIdentities.midnight,
            kind = TriggerKind.PRE_ALARM,
            sourceId = 0L,
            triggerAt = nextMidnight(input.now, input.zone),
            occurrenceAt = nextMidnight(input.now, input.zone),
            extra = TriggerExtra(purpose = TriggerExtra.PURPOSE_MIDNIGHT),
            method = RegisterMethod.EXACT_WHILE_IDLE,
        )

        val unique = triggers.distinctBy { it.identity }.sortedWith(compareBy({ it.triggerAt }, { it.identity }))
        return Plan(unique, nextAlarm(unique), occurrences)
    }

    /**
     * Next occurrence of [alarm] after [now] that is not [handled] (an early smart-wake/weather ring
     * or a dismissal in advance handles an occurrence before its time).
     */
    fun nextOccurrence(
        alarm: Alarm,
        now: Long,
        zone: ZoneId,
        groups: List<AlarmGroup>,
        holidays: Set<LocalDate>,
        handled: Set<OccurrenceKey> = emptySet(),
    ): Long? {
        val groupEnabled = isGroupEnabled(alarm, groups)
        var after = Instant.ofEpochMilli(now).atZone(zone)
        repeat(MAX_HANDLED_SKIPS) {
            val next = AlarmScheduleCalculator.nextOccurrence(
                alarm = alarm,
                after = after,
                isHoliday = { it in holidays },
                groupEnabled = groupEnabled,
            ) ?: return null
            val millis = next.toInstant().toEpochMilli()
            if (OccurrenceKey(RingKind.ALARM, alarm.id, millis) !in handled) return millis
            after = next
        }
        return null
    }

    fun isGroupEnabled(alarm: Alarm, groups: List<AlarmGroup>): Boolean {
        val groupId = alarm.groupId ?: return true
        return groups.firstOrNull { it.id == groupId }?.enabled ?: true
    }

    /** RING plus every pre-alarm event of one occurrence. */
    fun alarmTriggers(alarm: Alarm, occurrence: Long, input: PlanInput): List<PlannedTrigger> {
        val result = ArrayList<PlannedTrigger>(6)
        result += PlannedTrigger(
            identity = TriggerIdentities.ring(alarm.id),
            kind = TriggerKind.RING,
            sourceId = alarm.id,
            triggerAt = occurrence,
            occurrenceAt = occurrence,
            label = alarm.label,
            method = RegisterMethod.ALARM_CLOCK,
        )

        fun pre(kind: PreAlarmKind, minutesBefore: Int) {
            if (minutesBefore <= 0) return
            val at = occurrence - minutesBefore * 60_000L
            if (at <= input.now) return
            result += PlannedTrigger(
                identity = TriggerIdentities.preAlarm(alarm.id, kind),
                kind = TriggerKind.PRE_ALARM,
                sourceId = alarm.id,
                triggerAt = at,
                occurrenceAt = occurrence,
                extra = TriggerExtra(preAlarm = kind),
                label = alarm.label,
                method = RegisterMethod.EXACT_WHILE_IDLE,
            )
        }

        pre(PreAlarmKind.UPCOMING_NOTICE, input.upcomingNoticeMinutes)
        if (alarm.sunrise.enabled) pre(PreAlarmKind.SUNRISE_START, alarm.sunrise.durationMinutes)
        if (alarm.smartWake.enabled) pre(PreAlarmKind.SMART_WAKE_START, alarm.smartWake.windowMinutes)
        if (alarm.weatherAdjust.enabled && input.weatherEnabled && alarm.weatherAdjust.conditions.isNotEmpty()) {
            pre(PreAlarmKind.WEATHER_CHECK, alarm.weatherAdjust.minutesEarlier)
        }
        pre(PreAlarmKind.PREFETCH, PREFETCH_MINUTES)
        return result
    }

    /** SNOOZE_END / AWAKE_CHECK for parked rings: derived from the ring state, never from the ledger. */
    fun ringTriggers(rings: List<ActiveRing>): List<PlannedTrigger> = rings.mapNotNull { ring ->
        when {
            ring.state == RingState.SNOOZED && ring.snoozedUntil != null -> PlannedTrigger(
                identity = TriggerIdentities.snoozeEnd(ring.ringId),
                kind = TriggerKind.SNOOZE_END,
                sourceId = ring.sourceId,
                triggerAt = ring.snoozedUntil!!,
                occurrenceAt = ring.occurrenceAt,
                extra = TriggerExtra(ringId = ring.ringId, ringKind = ring.kind),
                label = ring.label,
                method = RegisterMethod.ALARM_CLOCK,
            )
            ring.state == RingState.AWAKE_CHECK && !ring.awakePromptShowing && ring.awakeCheckDueAt != null -> PlannedTrigger(
                identity = TriggerIdentities.awakeCheck(ring.ringId),
                kind = TriggerKind.AWAKE_CHECK,
                sourceId = ring.sourceId,
                triggerAt = ring.awakeCheckDueAt!!,
                occurrenceAt = ring.occurrenceAt,
                extra = TriggerExtra(ringId = ring.ringId, ringKind = ring.kind),
                label = ring.label,
                method = RegisterMethod.ALARM_CLOCK,
            )
            else -> null
        }
    }

    /** Snooze ends and awake checks recorded in the ledger, kept verbatim while the ring state is unknown. */
    private fun keptRingTriggers(ledger: List<ScheduledTrigger>): List<PlannedTrigger> = ledger.mapNotNull { entry ->
        val extra = entry.extraPayload
        val isRingTrigger = (entry.kind == TriggerKind.SNOOZE_END && extra?.purpose == null) || entry.kind == TriggerKind.AWAKE_CHECK
        if (!isRingTrigger) return@mapNotNull null
        PlannedTrigger(
            identity = TriggerIdentities.of(entry) ?: return@mapNotNull null,
            kind = entry.kind,
            sourceId = entry.sourceId,
            triggerAt = entry.triggerAt,
            occurrenceAt = entry.occurrenceAt,
            extra = extra,
            method = RegisterMethod.ALARM_CLOCK,
        )
    }

    /**
     * Naps, tests and kicks only exist in the ledger. They are kept until they have fired:
     * future ones always, past ones while their occurrence is not handled (a past time fires at once).
     */
    private fun oneOffTriggers(input: PlanInput): List<PlannedTrigger> {
        val readyRingIds = input.rings
            .filter { it.state == RingState.RINGING || it.state == RingState.MISSION || (it.state == RingState.AWAKE_CHECK && it.awakePromptShowing) }
            .mapTo(HashSet()) { it.ringId }
        return input.ledger.mapNotNull { entry ->
            val extra = entry.extraPayload
            when (entry.kind) {
                TriggerKind.NAP, TriggerKind.TEST -> {
                    val key = entry.occurrenceKey() ?: return@mapNotNull null
                    if (entry.triggerAt <= input.now && key in input.handled) return@mapNotNull null
                    PlannedTrigger(
                        identity = TriggerIdentities.of(entry) ?: return@mapNotNull null,
                        kind = entry.kind,
                        sourceId = entry.sourceId,
                        triggerAt = entry.triggerAt,
                        occurrenceAt = entry.occurrenceAt,
                        extra = extra,
                        label = extra?.alarm?.label.orEmpty(),
                        method = RegisterMethod.ALARM_CLOCK,
                    )
                }
                TriggerKind.SNOOZE_END -> {
                    if (extra?.purpose != TriggerExtra.PURPOSE_KICK || extra.ringId !in readyRingIds) return@mapNotNull null
                    PlannedTrigger(
                        identity = TriggerIdentities.kick(extra.ringId!!),
                        kind = TriggerKind.SNOOZE_END,
                        sourceId = entry.sourceId,
                        triggerAt = entry.triggerAt,
                        occurrenceAt = entry.occurrenceAt,
                        extra = extra,
                        method = RegisterMethod.ALARM_CLOCK,
                    )
                }
                else -> null
            }
        }
    }

    /**
     * Timers: the repository is the source of truth for RUNNING timers; a ledger entry registered by
     * scheduleTimer is kept until cancelTimer, the timer rings, or the timer is deleted.
     */
    private fun timerTriggers(input: PlanInput): List<PlannedTrigger> {
        val ledgerTimers = input.ledger.filter { it.kind == TriggerKind.TIMER }.associateBy { it.sourceId }
        val timers = input.timers ?: return ledgerTimers.values.map { entry ->
            PlannedTrigger(
                identity = TriggerIdentities.timer(entry.sourceId),
                kind = TriggerKind.TIMER,
                sourceId = entry.sourceId,
                triggerAt = entry.triggerAt,
                occurrenceAt = entry.occurrenceAt,
                method = RegisterMethod.ALARM_CLOCK,
            )
        }
        val byId = timers.associateBy { it.id }
        val ids = LinkedHashSet<Long>().apply {
            addAll(ledgerTimers.keys)
            timers.filter { it.state == TimerState.RUNNING && it.endAt != null }.mapTo(this) { it.id }
        }
        return ids.mapNotNull { id ->
            val timer = byId[id] ?: return@mapNotNull null
            if (timer.state == TimerState.RINGING) return@mapNotNull null
            val endAt = if (timer.state == TimerState.RUNNING && timer.endAt != null) timer.endAt!! else ledgerTimers[id]?.triggerAt ?: return@mapNotNull null
            if (OccurrenceKey(RingKind.TIMER, id, endAt) in input.handled) return@mapNotNull null
            PlannedTrigger(
                identity = TriggerIdentities.timer(id),
                kind = TriggerKind.TIMER,
                sourceId = id,
                triggerAt = endAt,
                occurrenceAt = endAt,
                label = timer.label,
                method = RegisterMethod.ALARM_CLOCK,
            )
        }
    }

    /** Earliest ring-type trigger: alarms, calendar alarms, naps and snoozes (not timers, tests, kicks). */
    fun nextAlarm(triggers: List<PlannedTrigger>): NextAlarm? = triggers
        .mapNotNull { t ->
            val kind = when (t.kind) {
                TriggerKind.RING -> RingKind.ALARM
                TriggerKind.CALENDAR -> RingKind.CALENDAR
                TriggerKind.NAP -> RingKind.NAP
                TriggerKind.SNOOZE_END -> t.extra?.takeIf { it.purpose == null }?.ringKind
                    ?.takeIf { it != RingKind.TIMER && it != RingKind.TEST }
                else -> null
            } ?: return@mapNotNull null
            NextAlarm(kind = kind, sourceId = t.sourceId, triggerAt = t.triggerAt, label = t.label)
        }
        .minWithOrNull(compareBy({ it.triggerAt }, { it.kind.ordinal }, { it.sourceId }))

    /** A few seconds after the next local midnight. */
    fun nextMidnight(now: Long, zone: ZoneId): Long {
        val tomorrow = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1)
        return tomorrow.atStartOfDay(zone).toInstant().toEpochMilli() + MIDNIGHT_OFFSET_MILLIS
    }
}
