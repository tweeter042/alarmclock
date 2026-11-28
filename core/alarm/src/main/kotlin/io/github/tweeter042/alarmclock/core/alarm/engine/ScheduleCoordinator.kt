package io.github.tweeter042.alarmclock.core.alarm.engine

import io.github.tweeter042.alarmclock.core.alarm.EngineLog
import io.github.tweeter042.alarmclock.core.alarm.internal.BootCount
import io.github.tweeter042.alarmclock.core.alarm.internal.EngineEnv
import io.github.tweeter042.alarmclock.core.alarm.internal.Guarded
import io.github.tweeter042.alarmclock.core.alarm.internal.guarded
import io.github.tweeter042.alarmclock.core.alarm.internal.guardedOrNull
import io.github.tweeter042.alarmclock.core.alarm.ring.RingBook
import io.github.tweeter042.alarmclock.core.alarm.ring.occurrenceKey
import io.github.tweeter042.alarmclock.core.alarm.schedule.CalendarAlarmPlanner
import io.github.tweeter042.alarmclock.core.alarm.schedule.CalendarOccurrence
import io.github.tweeter042.alarmclock.core.alarm.schedule.IntegrityMath
import io.github.tweeter042.alarmclock.core.alarm.schedule.OccurrenceKey
import io.github.tweeter042.alarmclock.core.alarm.schedule.Plan
import io.github.tweeter042.alarmclock.core.alarm.schedule.PlanInput
import io.github.tweeter042.alarmclock.core.alarm.schedule.PlannedTrigger
import io.github.tweeter042.alarmclock.core.alarm.schedule.RegisterMethod
import io.github.tweeter042.alarmclock.core.alarm.schedule.TriggerIdentities
import io.github.tweeter042.alarmclock.core.alarm.schedule.TriggerIdentity
import io.github.tweeter042.alarmclock.core.alarm.schedule.TriggerPayload
import io.github.tweeter042.alarmclock.core.alarm.schedule.TriggerPlanner
import io.github.tweeter042.alarmclock.core.alarm.schedule.extraPayload
import io.github.tweeter042.alarmclock.core.alarm.schedule.isRingType
import io.github.tweeter042.alarmclock.core.model.alarm.Alarm
import io.github.tweeter042.alarmclock.core.model.alarm.NextAlarm
import io.github.tweeter042.alarmclock.core.model.alarm.RingKind
import io.github.tweeter042.alarmclock.core.model.engine.IntegrityReport
import io.github.tweeter042.alarmclock.core.model.entity.CalendarAlarmRule
import io.github.tweeter042.alarmclock.core.model.entity.ScheduledTrigger
import io.github.tweeter042.alarmclock.core.model.entity.TriggerKind
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Ring state as seen by the scheduler. */
internal data class RingSnapshot(val book: RingBook, val known: Boolean)

/**
 * Full reschedules, direct one-off registrations, the integrity check and the next-alarm state.
 *
 * Every AlarmManager/ledger write happens under one [mutex]. A full reschedule reads the ring state
 * only after taking the mutex, and ring transitions publish their new state before registering
 * their own triggers, so a reschedule can never cancel a snooze it has not seen.
 */
internal class ScheduleCoordinator(
    private val env: EngineEnv,
    private val rings: () -> RingSnapshot,
    private val awaitRings: suspend () -> Unit,
) {
    private val log = EngineLog.schedule
    private val mutex = Mutex()
    private val requests = Channel<String>(Channel.CONFLATED)
    private val _next = MutableStateFlow<NextAlarm?>(null)
    @Volatile private var lastHolidays: Set<LocalDate> = emptySet()

    val nextAlarm: StateFlow<NextAlarm?> get() = _next

    @Volatile
    var lastPlan: Plan? = null
        private set

    /** Called (outside the mutex) after every successful reschedule. */
    var afterReschedule: (suspend (Plan, PlanInput, List<CalendarAlarmRule>?) -> Unit)? = null

    /** Requests a debounced (~250 ms), conflated full reschedule. */
    fun request(reason: String) {
        requests.trySend(reason)
    }

    fun startDebouncer() {
        env.scope.launch {
            requests.receiveAsFlow().debounce(DEBOUNCE_MILLIS).collect { reason -> rescheduleAll(reason) }
        }
    }

    /** Recomputes and re-registers everything. Returns null if the data could not be read (nothing changed). */
    suspend fun rescheduleAll(reason: String): Plan? = mutex.withLock { rescheduleLocked(reason) }

    private suspend fun rescheduleLocked(reason: String): Plan? {
        awaitRings()
        val (input, rules) = try {
            buildInput()
        } catch (t: Throwable) {
            log.e("Reschedule ($reason) skipped: data unavailable, existing alarms kept", t)
            return null
        }
        val plan = TriggerPlanner.plan(input)
        try {
            env.scheduler.apply(plan.triggers, input.ledger)
        } catch (t: Throwable) {
            log.e("Reschedule ($reason) could not be recorded", t)
            return null
        }
        _next.value = plan.next
        lastPlan = plan
        BootCount.current(env.context)?.let { env.prefs.lastScheduleBootCount = it }
        log.i("Rescheduled ($reason): ${plan.triggers.size} triggers, next ${plan.next?.kind} at ${plan.next?.triggerAt}")
        afterReschedule?.let { callback -> env.scope.launch { callback(plan, input, rules) } }
        return plan
    }

    /** Snapshot of everything planning needs. Throws if alarms, groups or the ledger cannot be read. */
    suspend fun buildInput(): Pair<PlanInput, List<CalendarAlarmRule>?> {
        val now = env.clock.now()
        val zone = env.clock.zone()
        val settings = env.settings()
        val alarms = required("alarms") { env.data.alarms.getAlarms() }
        val groups = required("groups") { env.data.alarms.getGroups() }
        val ledger = required("ledger") { env.data.scheduleLedger.all() }
        val snapshot = rings()
        val holidays = holidayDates(now, zone)
        val timers = guardedOrNull("Reading timers", READ_TIMEOUT_MILLIS, log) { env.data.timers.observeTimers().first() }
        val handled = handledKeys(now, snapshot.book)
        val rules = guardedOrNull("Reading calendar rules", READ_TIMEOUT_MILLIS, log) { env.data.calendarRules.getAll() }
        val (calendar, labels) = calendarOccurrences(rules, now, zone, ledger, handled)
        val input = PlanInput(
            now = now,
            zone = zone,
            alarms = alarms,
            groups = groups,
            holidays = holidays,
            upcomingNoticeMinutes = settings.reliability.upcomingNoticeMinutes,
            weatherEnabled = settings.weather.enabled,
            calendar = calendar,
            calendarLabels = labels,
            rings = snapshot.book.all,
            ringsKnown = snapshot.known,
            ledger = ledger,
            timers = timers,
            handled = handled,
        )
        return input to rules
    }

    private suspend fun <T> required(what: String, block: suspend () -> T): T =
        when (val result = guarded("Reading $what", READ_TIMEOUT_MILLIS, log, block)) {
            is Guarded.Ok -> result.value
            is Guarded.Failed -> throw IllegalStateException("Cannot read $what", result.error)
        }

    /** Holiday dates (yesterday … +400 days) from the provider, else the repository, else the last known set. */
    suspend fun holidayDates(now: Long, zone: ZoneId): Set<LocalDate> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val from = today.minusDays(1)
        val to = today.plusDays(HOLIDAY_DAYS_AHEAD)
        val provider = env.deps.holidays
        val dates = provider?.let { guardedOrNull("Holiday provider", INTEGRATION_TIMEOUT_MILLIS, log) { it.holidayDates(from, to) } }
            ?: guardedOrNull("Reading holidays", READ_TIMEOUT_MILLIS, log) {
                env.data.holidays.between(from, to).filter { it.enabled }.mapTo(HashSet()) { it.date }
            }
        if (dates != null) lastHolidays = dates
        return dates ?: lastHolidays
    }

    /** Occurrences that must not ring again: events with an outcome, and every ring the engine holds. */
    suspend fun handledKeys(now: Long, book: RingBook): Set<OccurrenceKey> {
        val events = guardedOrNull("Reading events", READ_TIMEOUT_MILLIS, log) {
            env.data.events.between(now - HANDLED_WINDOW_MILLIS, now + HANDLED_WINDOW_MILLIS)
        }.orEmpty()
        return buildSet {
            events.filter { it.outcome != null }.forEach { add(OccurrenceKey(it.kind, it.sourceId, it.occurrenceAt)) }
            book.all.forEach { add(it.occurrenceKey()) }
        }
    }

    private suspend fun calendarOccurrences(
        rules: List<CalendarAlarmRule>?,
        now: Long,
        zone: ZoneId,
        ledger: List<ScheduledTrigger>,
        handled: Set<OccurrenceKey>,
    ): Pair<List<CalendarOccurrence>, Map<Long, String>> {
        val previous = ledger.filter { it.kind == TriggerKind.CALENDAR && it.triggerAt > now }.associateBy { it.sourceId }
        fun kept(ruleId: Long): CalendarOccurrence? = previous[ruleId]?.let {
            CalendarOccurrence(ruleId, it.triggerAt, eventId = 0L, eventTitle = it.extraPayload?.title.orEmpty(), eventBegin = it.occurrenceAt)
        }
        if (rules == null) {
            // Rules unreadable: keep what was planned before.
            val keptAll = previous.keys.mapNotNull(::kept)
            return keptAll to keptAll.associate { it.ruleId to it.eventTitle }
        }
        val enabled = rules.filter { it.enabled }
        val source = env.deps.calendar
        if (enabled.isEmpty() || source == null) return emptyList<CalendarOccurrence>() to emptyMap()
        val permitted = runCatching { source.hasPermission() }.getOrDefault(false)
        if (!permitted) return emptyList<CalendarOccurrence>() to emptyMap()

        val window = CalendarAlarmPlanner.queryWindow(now, zone)
        val occurrences = ArrayList<CalendarOccurrence>()
        val labels = HashMap<Long, String>()
        for (rule in enabled) {
            val events = guarded("Calendar events", INTEGRATION_TIMEOUT_MILLIS, log) {
                source.events(window.first, window.last + 1, rule.calendarIds)
            }
            val occurrence = when (events) {
                is Guarded.Ok -> CalendarAlarmPlanner.nextOccurrence(rule, events.value, now, zone) { alarmAt ->
                    OccurrenceKey(RingKind.CALENDAR, rule.id, alarmAt) in handled
                }
                // Calendar not readable (e.g. before the first unlock): keep the previous plan.
                is Guarded.Failed -> kept(rule.id)
            } ?: continue
            occurrences += occurrence
            labels[rule.id] = calendarLabel(rule, occurrence.eventTitle)
        }
        return occurrences to labels
    }

    /** Next occurrence of [alarm] that is not handled yet (for nextOccurrenceOf, skipNext, triggerNow). */
    suspend fun upcomingOccurrence(alarm: Alarm, now: Long = env.clock.now()): Long? {
        val zone = env.clock.zone()
        val groups = guardedOrNull("Reading groups", READ_TIMEOUT_MILLIS, log) { env.data.alarms.getGroups() }.orEmpty()
        return TriggerPlanner.nextOccurrence(alarm, now, zone, groups, holidayDates(now, zone), handledKeys(now, rings().book))
    }

    suspend fun registerOne(planned: PlannedTrigger): ScheduledTrigger? = mutex.withLock {
        guardedOrNull("Registering ${planned.identity}", WRITE_TIMEOUT_MILLIS, log) { env.scheduler.registerOne(planned) }
    }

    suspend fun cancelIdentities(identities: Set<TriggerIdentity>) {
        mutex.withLock {
            guarded("Cancelling triggers", WRITE_TIMEOUT_MILLIS, log) { env.scheduler.cancelIdentities(identities) }
        }
    }

    /** Forgets a one-off trigger that has fired (derived triggers are replaced by the next reschedule). */
    suspend fun removeFired(payload: TriggerPayload) {
        if (payload.kind == TriggerKind.RING || payload.kind == TriggerKind.CALENDAR || payload.kind == TriggerKind.PRE_ALARM) return
        mutex.withLock {
            val ledger = env.data.scheduleLedger
            val entry = ledger.get(payload.requestCode) ?: return
            if (entry.kind == payload.kind && entry.sourceId == payload.sourceId && entry.triggerAt == payload.triggerAt) {
                ledger.remove(payload.requestCode)
            }
        }
    }

    /** Ledger entries (for missed-alarm detection at start). */
    suspend fun ledgerEntries(): List<ScheduledTrigger>? =
        guardedOrNull("Reading the ledger", READ_TIMEOUT_MILLIS, log) { env.data.scheduleLedger.all() }

    suspend fun removeEntries(requestCodes: Collection<Int>) {
        if (requestCodes.isEmpty()) return
        mutex.withLock {
            guarded("Removing ledger entries", WRITE_TIMEOUT_MILLIS, log) {
                requestCodes.forEach { env.data.scheduleLedger.remove(it) }
            }
        }
    }

    /**
     * True if, in the same boot session in which the schedule was last registered, future ring
     * triggers have vanished from AlarmManager: the app was force-stopped (or the system removed
     * its alarms). After a reboot, missing PendingIntents are normal and not reported.
     */
    suspend fun detectLostTriggers(): Boolean {
        val boot = BootCount.current(env.context) ?: return false
        if (env.prefs.lastScheduleBootCount != boot) return false
        val now = env.clock.now()
        val ledger = ledgerEntries() ?: return false
        return ledger.any { it.kind.isRingType && it.triggerAt > now + LOST_TRIGGER_MARGIN_MILLIS && !env.scheduler.isRegistered(it.requestCode) }
    }

    /** Verifies every planned future trigger is registered and that AlarmManager's next alarm clock agrees. */
    suspend fun integrityReport(repair: Boolean): IntegrityReport = mutex.withLock {
        awaitRings()
        val now = env.clock.now()
        val input = try {
            buildInput().first
        } catch (t: Throwable) {
            log.e("Integrity check: data unavailable", t)
            return@withLock IntegrityReport(0, 0, emptyList(), repaired = false, systemNextAlarmMatches = false, checkedAt = now)
        }
        val plan = TriggerPlanner.plan(input)
        val expected = plan.triggers.filter { it.triggerAt > now }
        val missing = IntegrityMath.missing(plan.triggers, input.ledger, now) { env.scheduler.isRegistered(it) }
        val ourEarliest = expected.filter { it.method == RegisterMethod.ALARM_CLOCK }.minOfOrNull { it.triggerAt }
        val system = env.scheduler.systemNextAlarm()
        val matches = IntegrityMath.systemNextAlarmMatches(ourEarliest, system?.first, system?.second == true)
        var repaired = false
        if (repair && (missing.isNotEmpty() || !matches)) {
            log.w("Integrity: ${missing.size} of ${expected.size} triggers missing, system next alarm matches=$matches; repairing")
            repaired = rescheduleLocked("integrity repair") != null
        }
        IntegrityReport(
            expected = expected.size,
            registered = expected.size - missing.size,
            missingSourceIds = missing.map { it.sourceId }.distinct(),
            repaired = repaired,
            systemNextAlarmMatches = matches,
            checkedAt = now,
        )
    }

    companion object {
        const val DEBOUNCE_MILLIS = 250L
        const val READ_TIMEOUT_MILLIS = 5_000L
        const val WRITE_TIMEOUT_MILLIS = 5_000L
        const val INTEGRATION_TIMEOUT_MILLIS = 5_000L
        const val HOLIDAY_DAYS_AHEAD = 400L
        const val HANDLED_WINDOW_MILLIS = 3L * 24 * 60 * 60 * 1000
        const val LOST_TRIGGER_MARGIN_MILLIS = 5_000L

        fun calendarLabel(rule: CalendarAlarmRule, eventTitle: String): String =
            rule.template.label.ifBlank { eventTitle }.ifBlank { rule.name }

        /** Identities of the triggers that belong to one ring. */
        fun ringTriggerIdentities(ringId: String): Set<TriggerIdentity> = setOf(
            TriggerIdentities.snoozeEnd(ringId),
            TriggerIdentities.awakeCheck(ringId),
            TriggerIdentities.kick(ringId),
        )
    }
}
