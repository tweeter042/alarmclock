package io.github.tweeter042.alarmclock.core.alarm

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.tweeter042.alarmclock.core.alarm.engine.PreAlarmHandler
import io.github.tweeter042.alarmclock.core.alarm.engine.RingCoordinator
import io.github.tweeter042.alarmclock.core.alarm.engine.RingSnapshot
import io.github.tweeter042.alarmclock.core.alarm.engine.ScheduleCoordinator
import io.github.tweeter042.alarmclock.core.alarm.engine.TriggerResult
import io.github.tweeter042.alarmclock.core.alarm.guard.AlarmGuardService
import io.github.tweeter042.alarmclock.core.alarm.internal.EngineEnv
import io.github.tweeter042.alarmclock.core.alarm.internal.EnginePrefs
import io.github.tweeter042.alarmclock.core.alarm.internal.guarded
import io.github.tweeter042.alarmclock.core.alarm.notify.EngineChannels
import io.github.tweeter042.alarmclock.core.alarm.notify.EngineNotifier
import io.github.tweeter042.alarmclock.core.alarm.ring.AlarmRingService
import io.github.tweeter042.alarmclock.core.alarm.ring.AlarmVolumeController
import io.github.tweeter042.alarmclock.core.alarm.ring.GestureResult
import io.github.tweeter042.alarmclock.core.alarm.ring.RingBook
import io.github.tweeter042.alarmclock.core.alarm.ring.RingBookStore
import io.github.tweeter042.alarmclock.core.alarm.ring.RingInput
import io.github.tweeter042.alarmclock.core.alarm.schedule.AlarmScheduler
import io.github.tweeter042.alarmclock.core.alarm.schedule.MissedAlarmPolicy
import io.github.tweeter042.alarmclock.core.alarm.schedule.Plan
import io.github.tweeter042.alarmclock.core.alarm.schedule.PlanInput
import io.github.tweeter042.alarmclock.core.alarm.schedule.PlannedTrigger
import io.github.tweeter042.alarmclock.core.alarm.schedule.RegisterMethod
import io.github.tweeter042.alarmclock.core.alarm.schedule.TriggerExtra
import io.github.tweeter042.alarmclock.core.alarm.schedule.TriggerIdentities
import io.github.tweeter042.alarmclock.core.alarm.schedule.TriggerPayload
import io.github.tweeter042.alarmclock.core.alarm.schedule.extraPayload
import io.github.tweeter042.alarmclock.core.alarm.work.Watchdog
import io.github.tweeter042.alarmclock.core.data.DataGraph
import io.github.tweeter042.alarmclock.core.model.alarm.ActiveRing
import io.github.tweeter042.alarmclock.core.model.alarm.Alarm
import io.github.tweeter042.alarmclock.core.model.alarm.ButtonAction
import io.github.tweeter042.alarmclock.core.model.alarm.NextAlarm
import io.github.tweeter042.alarmclock.core.model.alarm.RepeatRule
import io.github.tweeter042.alarmclock.core.model.alarm.RingKind
import io.github.tweeter042.alarmclock.core.model.alarm.RingOutcome
import io.github.tweeter042.alarmclock.core.model.alarm.isRepeating
import io.github.tweeter042.alarmclock.core.model.engine.AlarmEngine
import io.github.tweeter042.alarmclock.core.model.engine.IntegrityReport
import io.github.tweeter042.alarmclock.core.model.entity.AlarmEvent
import io.github.tweeter042.alarmclock.core.model.entity.CalendarAlarmRule
import io.github.tweeter042.alarmclock.core.model.entity.TriggerKind
import io.github.tweeter042.alarmclock.core.model.settings.AppSettings
import io.github.tweeter042.alarmclock.core.model.time.AlarmTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

/**
 * The alarm engine. Create exactly ONE instance per process (in the Application, exposed through
 * [AlarmEngineHost]) and call [start] from `Application.onCreate` — also in Direct Boot.
 *
 * Architecture (details in the KDoc of each part):
 * - Scheduling (`engine/ScheduleCoordinator`, `schedule/TriggerPlanner`): occurrences come only from
 *   AlarmScheduleCalculator; rings use `setAlarmClock`, pre-alarm events
 *   `setExactAndAllowWhileIdle`; every registration is recorded in the ScheduleLedger. The engine
 *   observes alarms, groups, holidays, calendar rules and the schedule-relevant settings and
 *   reschedules after each change (debounced ~250 ms, conflated, serialized by a mutex).
 * - Ringing (`engine/RingCoordinator`, `ring/RingReducer`): a pure state machine over every
 *   ring (current, queued, snoozed, awaiting the awake check), persisted in RingStateStore so it
 *   survives process death and reboots. `ring/AlarmRingService` is the foreground service that
 *   plays it (audio with fallbacks, vibration, wake lock, gestures, full-screen notification).
 * - Reliability: missed-alarm detection at start, force-stop detection, integrity checks with
 *   repair (watchdog worker, app foreground, boot, time changes), optional guard service.
 *
 * All optional [EngineDependencies] may be null; the engine then simply skips that feature. Calls
 * into integrations are bounded by timeouts and can never stop an alarm from ringing.
 *
 * For the ring screen (feature:ringing): [activeRing] is the ring to show. A full-screen intent can
 * open the ring screen a few milliseconds before the ring with that id is published, so the screen
 * should wait (a few seconds) for `activeRing` to carry it. With SnoozeMode.MISSION_TO_SNOOZE,
 * [snooze] returns false and remembers the request: run the missions ([startMission] …
 * [completeMission]) and the last completed mission snoozes instead of dismissing.
 */
class DefaultAlarmEngine internal constructor(
    context: Context,
    private val data: DataGraph,
    private val deps: EngineDependencies,
    private val clock: EngineClock,
) : AlarmEngine {

    /** @param context any context (the application context is used). */
    constructor(context: Context, data: DataGraph, deps: EngineDependencies) : this(context, data, deps, SystemEngineClock)

    private val log = EngineLog.engine
    private val appContext: Context = context.applicationContext ?: context

    internal val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error -> log.e("Uncaught engine error", error) },
    )
    private val prefs = EnginePrefs(appContext)
    internal val notifier = EngineNotifier(appContext, deps.ringUi, clock)
    private val scheduler = AlarmScheduler(appContext, data.scheduleLedger) { notifier.alarmClockShowIntent() }
    internal val env = EngineEnv(appContext, data, deps, clock, scope, prefs, notifier, scheduler)
    internal val volume = AlarmVolumeController(appContext, prefs)
    internal val ringPrefs: EnginePrefs get() = prefs

    internal val rings: RingCoordinator = RingCoordinator(env, RingBookStore(appContext, data.ringStateStore, prefs)) { schedule }
    internal val schedule: ScheduleCoordinator = ScheduleCoordinator(
        env = env,
        rings = { RingSnapshot(rings.book.value, rings.ringsKnown) },
        awaitRings = { rings.awaitRestored() },
    )
    private val preAlarms = PreAlarmHandler(env, schedule, rings)

    private val started = AtomicBoolean(false)
    private val startup = CompletableDeferred<Unit>()
    @Volatile private var lastForegroundCheck = Long.MIN_VALUE / 2

    override val activeRing: StateFlow<ActiveRing?> get() = rings.activeRing
    override val nextAlarm: StateFlow<NextAlarm?> get() = schedule.nextAlarm

    // --- lifecycle ------------------------------------------------------------------------------

    /**
     * Starts the engine (idempotent; returns immediately, the work runs in the background):
     * creates the notification channels, restores an interrupted ring, rings or reports alarms
     * missed while the device was off, re-registers every trigger, notifies if Android had removed
     * the alarms (force stop), starts the guard service if enabled, enqueues the watchdog worker
     * (only once the user is unlocked) and from then on observes the data and reschedules on change.
     */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        EngineChannels.createAll(appContext)
        schedule.afterReschedule = { plan, input, rules -> afterReschedule(plan, input, rules) }
        scope.launch {
            try {
                startupSequence()
            } finally {
                startup.complete(Unit)
            }
        }
        observeForeground()
    }

    private suspend fun startupSequence() {
        step("restore rings") { rings.restore() }
        val lost = step("force-stop detection") { schedule.detectLostTriggers() } ?: false
        step("missed alarms") { handleMissedAlarms() }
        step("reschedule") { schedule.rescheduleAll("start") }
        if (lost) {
            log.w("Android had removed the alarms (force stop?); they are registered again")
            notifier.showRestored()
        }
        step("volume") { if (rings.book.value.sounding == null && !AlarmRingService.isRunning) volume.restore() }
        step("guard") { syncGuard(env.settings().reliability.guardServiceEnabled) }
        step("watchdog") { Watchdog.enqueueIfUnlocked(appContext) }
        observeSources()
        schedule.startDebouncer()
    }

    private suspend fun <T> step(name: String, block: suspend () -> T): T? = try {
        block()
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        log.e("Start-up step '$name' failed", t)
        null
    }

    /** Waits (bounded) until [start]'s background work is done. Starts the engine if needed. */
    internal suspend fun awaitStartup(timeoutMillis: Long = STARTUP_WAIT_MILLIS) {
        start()
        withTimeoutOrNull(timeoutMillis) { startup.await() }
    }

    private fun observeSources() {
        val scheduleSettings = data.settings.settings.map(::scheduleSettingsKey).distinctUntilChanged()
        scope.launch {
            combine(
                data.alarms.observeAlarms(),
                data.alarms.observeGroups(),
                data.holidays.observeAll(),
                data.calendarRules.observe(),
                scheduleSettings,
            ) { _, _, _, _, _ -> }
                .retryWhen { cause, attempt -> retryDelay("Observing alarm data", cause, attempt) }
                .collect { schedule.request("data changed") }
        }
        scope.launch {
            data.settings.settings
                .retryWhen { cause, attempt -> retryDelay("Observing settings", cause, attempt) }
                .collect { env.rememberSettings(it) }
        }
        scope.launch {
            data.settings.settings.map { it.reliability.guardServiceEnabled }.distinctUntilChanged()
                .retryWhen { cause, attempt -> retryDelay("Observing guard setting", cause, attempt) }
                .collect { syncGuard(it) }
        }
    }

    private suspend fun retryDelay(what: String, cause: Throwable, attempt: Long): Boolean {
        log.w("$what failed; retrying", cause)
        delay((1_000L shl attempt.coerceAtMost(6).toInt()).coerceAtMost(60_000L))
        return true
    }

    private fun observeForeground() {
        Handler(Looper.getMainLooper()).post {
            try {
                ProcessLifecycleOwner.get().lifecycle.addObserver(
                    object : DefaultLifecycleObserver {
                        override fun onStart(owner: LifecycleOwner) {
                            scope.launch { onAppForeground() }
                        }
                    },
                )
            } catch (t: Throwable) {
                log.w("Foreground observer unavailable", t)
            }
        }
    }

    private suspend fun onAppForeground() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastForegroundCheck < FOREGROUND_CHECK_INTERVAL_MILLIS) return
        lastForegroundCheck = now
        awaitStartup()
        // The app is visible: starting the ring service is allowed now if it is missing.
        if (rings.book.value.sounding != null) rings.ensureService("app foreground")
        guarded("Integrity check", INTEGRITY_TIMEOUT_MILLIS, log) { schedule.integrityReport(repair = true) }
        Watchdog.enqueueIfUnlocked(appContext)
    }

    /** Boot, package replaced, time / time-zone change, exact-alarm permission change. */
    internal suspend fun onSystemEvent(action: String) {
        awaitStartup()
        log.i("System event $action")
        when (action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                schedule.rescheduleAll(action)
                syncGuard(env.settings().reliability.guardServiceEnabled)
                Watchdog.enqueueIfUnlocked(appContext)
            }
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> schedule.rescheduleAll(action)
        }
        guarded("Integrity check", INTEGRITY_TIMEOUT_MILLIS, log) { schedule.integrityReport(repair = true) }
    }

    private fun syncGuard(enabled: Boolean) {
        if (enabled) AlarmGuardService.start(appContext) else AlarmGuardService.stop(appContext)
    }

    /** At start / boot: ring alarms missed within the grace period, report older ones. */
    private suspend fun handleMissedAlarms() {
        val ledger = schedule.ledgerEntries() ?: return
        val now = clock.now()
        val settings = env.settings()
        val handled = schedule.handledKeys(now, rings.book.value)
        val decisions = MissedAlarmPolicy.decide(ledger, now, settings.reliability.rebootGraceMinutes, isHandled = { it in handled })
        val forget = ArrayList<Int>()
        for (decision in decisions) {
            val trigger = decision.trigger
            when (decision.action) {
                MissedAlarmPolicy.Action.RING_NOW -> {
                    log.w("${trigger.kind} #${trigger.sourceId} was missed by ${(now - trigger.triggerAt) / 1000} s; ringing now")
                    rings.onRingTrigger(TriggerPayload.of(trigger), preRingId = null)
                }
                MissedAlarmPolicy.Action.NOTIFY_MISSED -> {
                    log.w("${trigger.kind} #${trigger.sourceId} was missed by ${(now - trigger.triggerAt) / 60_000} min")
                    guarded("Recording a missed alarm", RECORD_TIMEOUT_MILLIS, log) { recordMissed(decision, settings) }
                    forget += trigger.requestCode
                }
                MissedAlarmPolicy.Action.DROP -> forget += trigger.requestCode
                MissedAlarmPolicy.Action.IGNORE -> Unit
            }
        }
        schedule.removeEntries(forget)
    }

    private suspend fun recordMissed(decision: MissedAlarmPolicy.Decision, settings: AppSettings) {
        val trigger = decision.trigger
        var oneTime = false
        val label = when (trigger.kind) {
            TriggerKind.RING -> data.alarms.getAlarm(trigger.sourceId)?.also { oneTime = !it.repeat.isRepeating }?.label.orEmpty()
            TriggerKind.CALENDAR -> data.calendarRules.get(trigger.sourceId)
                ?.let { ScheduleCoordinator.calendarLabel(it, trigger.extraPayload?.title.orEmpty()) }.orEmpty()
            TriggerKind.NAP -> trigger.extraPayload?.alarm?.label.orEmpty()
            else -> ""
        }
        data.events.insert(
            AlarmEvent(
                kind = decision.key.kind,
                sourceId = trigger.sourceId,
                label = label,
                occurrenceAt = trigger.occurrenceAt,
                endedAt = clock.now(),
                outcome = RingOutcome.MISSED,
            ),
        )
        if (settings.reliability.missedAlarmNotice) notifier.showMissed(decision.key.kind, trigger.sourceId, label, trigger.occurrenceAt, settings)
        // A missed one-time alarm must not ring "again" tomorrow.
        if (oneTime) data.alarms.update(trigger.sourceId) { if (it.repeat.isRepeating) it else it.copy(enabled = false, skippedOccurrenceAt = null) }
    }

    /** Housekeeping after every reschedule (outside the schedule lock). */
    private suspend fun afterReschedule(plan: Plan, input: PlanInput, rules: List<CalendarAlarmRule>?) {
        // Skips of past occurrences have served their purpose.
        input.alarms.filter { (it.skippedOccurrenceAt ?: Long.MAX_VALUE) < input.now }.forEach { alarm ->
            guarded("Clearing a stale skip", RECORD_TIMEOUT_MILLIS, log) {
                data.alarms.update(alarm.id) { current ->
                    if (current.skippedOccurrenceAt == alarm.skippedOccurrenceAt) current.copy(skippedOccurrenceAt = null) else current
                }
            }
        }
        // "Upcoming" / sunrise notices of occurrences that are no longer planned.
        notifier.shownPreAlarmNotices().forEach { (alarmId, occurrence) ->
            if (plan.occurrences[alarmId] != occurrence) notifier.cancelPreAlarmNotices(alarmId)
        }
        rings.reconcile(input, rules)
        env.hook("onScheduleChanged") { onScheduleChanged(plan.next, input.alarms) }
    }

    // --- AlarmEngine ------------------------------------------------------------------------------

    override suspend fun rescheduleAll(reason: String) {
        awaitStartup()
        schedule.rescheduleAll(reason)
    }

    override suspend fun nextOccurrenceOf(alarmId: Long): Long? {
        val alarm = data.alarms.getAlarm(alarmId) ?: return null
        return schedule.upcomingOccurrence(alarm)
    }

    override suspend fun skipNext(alarmId: Long) {
        val alarm = data.alarms.getAlarm(alarmId) ?: return
        val occurrence = schedule.upcomingOccurrence(alarm.copy(skippedOccurrenceAt = null)) ?: return
        data.alarms.update(alarmId) { it.copy(skippedOccurrenceAt = occurrence) }
        schedule.request("skip next")
    }

    override suspend fun clearSkip(alarmId: Long) {
        data.alarms.update(alarmId) { it.copy(skippedOccurrenceAt = null) }
        schedule.request("clear skip")
    }

    override suspend fun dismissUpcoming(alarmId: Long) = dismissUpcoming(alarmId, expectedOccurrence = null)

    /** "Dismiss now" on the upcoming notification; ignored if the alarm's next occurrence changed meanwhile. */
    internal suspend fun dismissUpcoming(alarmId: Long, expectedOccurrence: Long?) {
        val alarm = data.alarms.getAlarm(alarmId) ?: return
        val occurrence = schedule.upcomingOccurrence(alarm) ?: return
        notifier.cancelPreAlarmNotices(alarmId)
        if (expectedOccurrence != null && expectedOccurrence != occurrence) return
        data.events.insert(
            AlarmEvent(
                kind = RingKind.ALARM,
                sourceId = alarmId,
                label = alarm.label,
                occurrenceAt = occurrence,
                endedAt = clock.now(),
                outcome = RingOutcome.DISMISSED_IN_ADVANCE,
            ),
        )
        data.alarms.update(alarmId) {
            if (it.repeat.isRepeating) it.copy(skippedOccurrenceAt = occurrence) else it.copy(enabled = false, skippedOccurrenceAt = null)
        }
        schedule.rescheduleAll("dismissed in advance")
    }

    override suspend fun startNap(minutes: Int, template: Alarm?): Long {
        val triggerAt = clock.now() + minutes.coerceIn(1, MAX_NAP_MINUTES) * 60_000L
        val local = Instant.ofEpochMilli(triggerAt).atZone(clock.zone())
        val base = template ?: env.settings().alarmDefaults
        val snapshot = base.copy(
            id = 0L,
            enabled = true,
            label = template?.label.orEmpty(),
            time = AlarmTime(local.hour, local.minute),
            repeat = RepeatRule.Once(),
            groupId = null,
            skippedOccurrenceAt = null,
        )
        schedule.registerOne(
            PlannedTrigger(
                identity = TriggerIdentities.nap(triggerAt),
                kind = TriggerKind.NAP,
                sourceId = triggerAt,
                triggerAt = triggerAt,
                occurrenceAt = triggerAt,
                extra = TriggerExtra(alarm = snapshot),
                label = snapshot.label,
                method = RegisterMethod.ALARM_CLOCK,
            ),
        )
        schedule.request("nap")
        return triggerAt
    }

    override suspend fun testRing(alarm: Alarm, delaySeconds: Int) {
        val triggerAt = clock.now() + delaySeconds.coerceIn(0, MAX_TEST_DELAY_SECONDS) * 1_000L
        schedule.registerOne(
            PlannedTrigger(
                identity = TriggerIdentities.test(alarm.id, triggerAt),
                kind = TriggerKind.TEST,
                sourceId = alarm.id,
                triggerAt = triggerAt,
                occurrenceAt = triggerAt,
                extra = TriggerExtra(alarm = alarm.copy(enabled = true)),
                label = alarm.label,
                method = RegisterMethod.ALARM_CLOCK,
            ),
        )
    }

    override suspend fun snooze(ringId: String): Boolean = rings.dispatch(RingInput.Snooze(ringId))?.accepted == true

    override suspend fun dismiss(ringId: String, outcome: RingOutcome) {
        rings.dispatch(RingInput.Dismiss(ringId, outcome))
    }

    override suspend fun startMission(ringId: String) {
        rings.dispatch(RingInput.StartMission(ringId))
    }

    override fun onMissionInteraction(ringId: String) = rings.onMissionInteraction(ringId)

    override suspend fun completeMission(ringId: String) {
        rings.dispatch(RingInput.CompleteMission(ringId))
    }

    override suspend fun abandonMission(ringId: String) {
        rings.dispatch(RingInput.AbandonMission(ringId))
    }

    override suspend fun confirmAwake(ringId: String) {
        rings.dispatch(RingInput.ConfirmAwake(ringId))
    }

    override suspend fun scheduleTimer(timerId: Long, endAt: Long) {
        schedule.registerOne(
            PlannedTrigger(
                identity = TriggerIdentities.timer(timerId),
                kind = TriggerKind.TIMER,
                sourceId = timerId,
                triggerAt = endAt,
                occurrenceAt = endAt,
                method = RegisterMethod.ALARM_CLOCK,
            ),
        )
    }

    override suspend fun cancelTimer(timerId: Long) {
        schedule.cancelIdentities(setOf(TriggerIdentities.timer(timerId)))
        rings.cancelTimerRings(timerId)
    }

    /**
     * Rings now. For an ALARM (smart wake) or CALENDAR source whose next occurrence is at most two
     * hours away, the ring takes over that occurrence: the regular trigger does not ring again.
     */
    override suspend fun triggerNow(kind: RingKind, sourceId: Long, alarm: Alarm) {
        val now = clock.now()
        val occurrence = when (kind) {
            RingKind.ALARM -> schedule.upcomingOccurrence(data.alarms.getAlarm(sourceId) ?: alarm, now)
            RingKind.CALENDAR -> schedule.ledgerEntries()?.firstOrNull { it.kind == TriggerKind.CALENDAR && it.sourceId == sourceId }?.occurrenceAt
            else -> null
        }?.takeIf { it > now && it - now <= EARLY_RING_WINDOW_MILLIS } ?: now
        if (rings.ringNow(kind, sourceId, alarm, occurrence) && occurrence > now) schedule.request("early ring")
    }

    override suspend fun integrityReport(repair: Boolean): IntegrityReport {
        awaitStartup()
        return schedule.integrityReport(repair)
    }

    // --- internal API for the engine's receivers and services ---------------------------------------

    internal val ringBook: StateFlow<RingBook> get() = rings.book

    internal suspend fun awaitRingsRestored() {
        start()
        rings.awaitRestored()
    }

    internal val briefingRequest: StateFlow<ActiveRing?> get() = rings.briefingRequest

    internal fun consumeBriefing(ringId: String) = rings.consumeBriefing(ringId)

    internal fun settingsSnapshot(): AppSettings = env.settingsSnapshot.value ?: AppSettings()

    internal suspend fun currentSettings(): AppSettings = env.settings()

    internal suspend fun onRingTrigger(payload: TriggerPayload, preRingId: String?): TriggerResult {
        start()
        return rings.onRingTrigger(payload, preRingId)
    }

    internal suspend fun onPreAlarmTrigger(payload: TriggerPayload) {
        awaitStartup()
        preAlarms.handle(payload)
    }

    internal suspend fun onGesture(ringId: String, action: ButtonAction): GestureResult = rings.onGesture(ringId, action)

    /** "Stop" on the briefing notification. */
    internal fun stopBriefing() {
        runCatching { deps.speaker?.stop() }
    }

    /** A TEST ring became audible (or could not play any audio): the health check's TEST_ALARM item. */
    internal suspend fun recordTestResult(ok: Boolean) {
        guarded("Recording the test result", RECORD_TIMEOUT_MILLIS, log) {
            data.settings.update { it.copy(reliability = it.reliability.copy(lastTestAt = clock.now(), lastTestOk = ok)) }
        }
    }

    internal companion object {
        const val STARTUP_WAIT_MILLIS = 15_000L
        const val FOREGROUND_CHECK_INTERVAL_MILLIS = 60_000L
        const val INTEGRITY_TIMEOUT_MILLIS = 30_000L
        const val RECORD_TIMEOUT_MILLIS = 5_000L
        const val EARLY_RING_WINDOW_MILLIS = 2L * 60 * 60 * 1000
        const val MAX_NAP_MINUTES = 24 * 60
        const val MAX_TEST_DELAY_SECONDS = 3_600

        /** The settings that change the schedule (location and appearance settings do not). */
        fun scheduleSettingsKey(settings: AppSettings): List<Any?> = listOf(
            settings.reliability.upcomingNoticeMinutes,
            settings.weather.enabled,
            settings.holidays.builtInThai,
            settings.holidays.calendarIds,
        )
    }
}
