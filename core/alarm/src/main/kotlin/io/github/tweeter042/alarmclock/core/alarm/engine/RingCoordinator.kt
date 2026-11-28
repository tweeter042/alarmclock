package io.github.tweeter042.alarmclock.core.alarm.engine

import android.app.NotificationManager
import android.os.SystemClock
import io.github.tweeter042.alarmclock.core.alarm.EngineLog
import io.github.tweeter042.alarmclock.core.alarm.internal.EngineEnv
import io.github.tweeter042.alarmclock.core.alarm.internal.Guarded
import io.github.tweeter042.alarmclock.core.alarm.internal.guarded
import io.github.tweeter042.alarmclock.core.alarm.internal.guardedOrNull
import io.github.tweeter042.alarmclock.core.alarm.notify.NotificationIds
import io.github.tweeter042.alarmclock.core.alarm.ring.AlarmRingService
import io.github.tweeter042.alarmclock.core.alarm.ring.GesturePolicy
import io.github.tweeter042.alarmclock.core.alarm.ring.GestureResult
import io.github.tweeter042.alarmclock.core.alarm.ring.RingBook
import io.github.tweeter042.alarmclock.core.alarm.ring.RingBookStore
import io.github.tweeter042.alarmclock.core.alarm.ring.RingEffect
import io.github.tweeter042.alarmclock.core.alarm.ring.RingInput
import io.github.tweeter042.alarmclock.core.alarm.ring.RingReducer
import io.github.tweeter042.alarmclock.core.alarm.ring.RingStep
import io.github.tweeter042.alarmclock.core.alarm.schedule.OccurrenceKey
import io.github.tweeter042.alarmclock.core.alarm.schedule.PlanInput
import io.github.tweeter042.alarmclock.core.alarm.schedule.PlannedTrigger
import io.github.tweeter042.alarmclock.core.alarm.schedule.RegisterMethod
import io.github.tweeter042.alarmclock.core.alarm.schedule.TriggerExtra
import io.github.tweeter042.alarmclock.core.alarm.schedule.TriggerIdentities
import io.github.tweeter042.alarmclock.core.alarm.schedule.TriggerPayload
import io.github.tweeter042.alarmclock.core.alarm.schedule.TriggerPlanner
import io.github.tweeter042.alarmclock.core.model.alarm.ActiveRing
import io.github.tweeter042.alarmclock.core.model.alarm.Alarm
import io.github.tweeter042.alarmclock.core.model.alarm.ButtonAction
import io.github.tweeter042.alarmclock.core.model.alarm.RingKind
import io.github.tweeter042.alarmclock.core.model.alarm.RingOutcome
import io.github.tweeter042.alarmclock.core.model.alarm.RingState
import io.github.tweeter042.alarmclock.core.model.alarm.isRepeating
import io.github.tweeter042.alarmclock.core.model.entity.AlarmEvent
import io.github.tweeter042.alarmclock.core.model.entity.CalendarAlarmRule
import io.github.tweeter042.alarmclock.core.model.entity.TimerState
import io.github.tweeter042.alarmclock.core.model.entity.TriggerKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/** What became of a fired ring trigger. */
internal enum class TriggerResult { RANG, QUEUED, RESUMED, SKIPPED }

/**
 * Owns the rings: runs [RingReducer] transitions one at a time (under [mutex]), publishes the new
 * [RingBook], persists it, executes the effects, and arms the in-process ring timers (auto action,
 * mission idle, awake-check answer). Never lets a failing side effect stop a transition.
 */
internal class RingCoordinator(
    private val env: EngineEnv,
    private val store: RingBookStore,
    private val schedule: () -> ScheduleCoordinator,
) {
    private val log = EngineLog.ring
    private val mutex = Mutex()
    private val resolver = RingResolver(env)
    private val restored = CompletableDeferred<Unit>()

    private val _book = MutableStateFlow(RingBook.EMPTY)
    private val _active = MutableStateFlow<ActiveRing?>(null)
    private val _briefing = MutableStateFlow<ActiveRing?>(null)

    /** Every ring (current + queued + parked). */
    val book: StateFlow<RingBook> get() = _book

    /** [RingBook.current], the contract's `activeRing`. */
    val activeRing: StateFlow<ActiveRing?> get() = _active

    /** A ring whose morning briefing should be read now (AFTER_DISMISS), consumed by the ring service. */
    val briefingRequest: StateFlow<ActiveRing?> get() = _briefing

    /** False until the persisted ring state was loaded successfully. */
    @Volatile
    var ringsKnown: Boolean = false
        private set

    private val eventIds = ConcurrentHashMap<String, Long>()
    private val missionStartedAt = ConcurrentHashMap<String, Long>()
    private val missionMillis = ConcurrentHashMap<String, Long>()
    private val lastInteraction = ConcurrentHashMap<String, Long>()

    private data class TimerKey(val ringId: String, val state: RingState, val startedAt: Long, val prompt: Boolean)

    private var timerJob: Job? = null
    private var armedKey: TimerKey? = null

    @Volatile
    private var serviceRequestedAt = Long.MIN_VALUE / 2

    // --- lifecycle ------------------------------------------------------------------------------

    suspend fun restore() {
        try {
            when (val loaded = guarded("Loading ring state", RESTORE_TIMEOUT_MILLIS, log) { store.load() }) {
                is Guarded.Ok -> mutex.withLock {
                    publish(loaded.value)
                    ringsKnown = true
                }
                is Guarded.Failed -> {
                    ringsKnown = false
                    env.scope.launch { retryRestore() }
                }
            }
            val sounding = _book.value.sounding
            if (sounding != null) {
                log.i("Restoring a ${sounding.kind} ring in state ${sounding.state}")
                mutex.withLock { armTimers(sounding, force = true) }
                ensureService("restore")
            }
        } finally {
            restored.complete(Unit)
        }
    }

    private suspend fun retryRestore() {
        repeat(RESTORE_RETRIES) {
            delay(RESTORE_RETRY_DELAY_MILLIS)
            val loaded = guardedOrNull("Reloading ring state", RESTORE_TIMEOUT_MILLIS, log) { store.load() } ?: return@repeat
            mutex.withLock {
                // Merge: rings that arrived meanwhile stay; persisted ones come back.
                val merged = RingBook.normalize(RingBook(_book.value.current ?: loaded.current, _book.value.others + loaded.all, loaded.pendingSnooze))
                publish(merged)
                guarded("Saving ring state", PERSIST_TIMEOUT_MILLIS, log) { store.persist(merged) }
                ringsKnown = true
                armTimers(merged.sounding, force = true)
            }
            if (_book.value.sounding != null) ensureService("restore retry")
            schedule().request("ring state restored")
            return
        }
    }

    suspend fun awaitRestored() {
        withTimeoutOrNull(RESTORE_WAIT_MILLIS) { restored.await() }
    }

    // --- transitions ----------------------------------------------------------------------------

    /**
     * Runs one transition. [guard] (checked under the lock) can veto it, e.g. a timer that belongs to
     * a ring session that has ended in the meantime.
     */
    suspend fun dispatch(input: RingInput, guard: ((RingBook) -> Boolean)? = null): RingStep? {
        awaitRestored()
        val step = mutex.withLock {
            val before = _book.value
            if (guard != null && !guard(before)) return@withLock null
            val step = RingReducer.reduce(before, input, env.clock.now())
            // Before publishing: the ring service must see the pending briefing when the ring disappears.
            step.effects.filterIsInstance<RingEffect.SpeakBriefing>().lastOrNull()?.let {
                if (env.deps.speaker != null && env.deps.briefing != null) _briefing.value = it.ring
            }
            if (step.book != before) {
                publish(step.book)
                guarded("Saving ring state", PERSIST_TIMEOUT_MILLIS, log) { store.persist(step.book) }
            }
            for (effect in step.effects) runEffect(effect)
            armTimers(step.book.sounding, force = false)
            step
        } ?: return null
        if (step.book.sounding != null) ensureService("transition")
        return step
    }

    private fun publish(book: RingBook) {
        _book.value = book
        _active.value = book.current
    }

    /** A ring trigger fired (called by AlarmRingService after it went foreground). */
    suspend fun onRingTrigger(payload: TriggerPayload, preRingId: String?): TriggerResult {
        awaitRestored()
        return try {
            when {
                payload.isKick -> TriggerResult.RESUMED
                payload.kind == TriggerKind.SNOOZE_END -> resumeOrRecreate(payload) { RingInput.SnoozeEnded(it) }
                payload.kind == TriggerKind.AWAKE_CHECK -> resumeOrRecreate(payload) { RingInput.AwakeCheckDue(it) }
                payload.createsRing -> arrive(payload, preRingId ?: newRingId())
                else -> TriggerResult.SKIPPED
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            log.e("Handling ${payload.kind} failed; ringing with the fallback configuration", t)
            emergency(payload, preRingId)
        } finally {
            guarded("Clearing the fired trigger", PERSIST_TIMEOUT_MILLIS, log) { schedule().removeFired(payload) }
        }
    }

    private suspend fun arrive(payload: TriggerPayload, ringId: String): TriggerResult {
        val resolution = withTimeoutOrNull(RESOLVE_TIMEOUT_MILLIS) { resolver.resolve(payload) }
            ?: Resolution.Ring(RingResolver.fallbackAlarm(payload)).also { log.w("Loading ${payload.kind} timed out; ringing with the fallback") }
        return when (resolution) {
            is Resolution.Skip -> {
                log.i("${payload.kind} #${payload.sourceId} not rung: ${resolution.reason}")
                TriggerResult.SKIPPED
            }
            is Resolution.Ring -> {
                val ring = ActiveRing(
                    ringId = ringId,
                    kind = payload.ringKind,
                    sourceId = payload.sourceId,
                    alarm = resolution.alarm,
                    occurrenceAt = payload.occurrenceAt,
                    startedAt = env.clock.now(),
                )
                arrival(ring)
            }
        }
    }

    private suspend fun arrival(ring: ActiveRing): TriggerResult {
        val step = dispatch(RingInput.Arrive(ring)) ?: return TriggerResult.SKIPPED
        return when {
            !step.accepted -> TriggerResult.SKIPPED
            step.book.sounding?.ringId == ring.ringId -> TriggerResult.RANG
            else -> TriggerResult.QUEUED
        }
    }

    private suspend fun resumeOrRecreate(payload: TriggerPayload, input: (String) -> RingInput): TriggerResult {
        val ringId = payload.ringId
        if (ringId != null && _book.value.find(ringId) != null) {
            return if (dispatch(input(ringId))?.accepted == true) TriggerResult.RESUMED else TriggerResult.SKIPPED
        }
        // The ring is unknown (ring state lost): never drop it. Ring its source again unless that
        // occurrence was finished (or, for an awake check, already confirmed).
        val key = OccurrenceKey(payload.ringKind, payload.sourceId, payload.occurrenceAt)
        val event = resolver.event(key)
        if (payload.kind == TriggerKind.SNOOZE_END && event?.outcome != null) return TriggerResult.SKIPPED
        if (payload.kind == TriggerKind.AWAKE_CHECK && event?.awakeConfirmed == true) return TriggerResult.SKIPPED
        log.w("${payload.kind} for an unknown ring; ringing its ${payload.ringKind} source again")
        val ring = ActiveRing(
            ringId = newRingId(),
            kind = payload.ringKind,
            sourceId = payload.sourceId,
            alarm = resolver.configFor(payload.ringKind, payload.sourceId, payload),
            occurrenceAt = payload.occurrenceAt,
            startedAt = env.clock.now(),
            snoozeCount = event?.snoozeCount ?: 0,
        )
        return arrival(ring)
    }

    private suspend fun emergency(payload: TriggerPayload, preRingId: String?): TriggerResult = try {
        val ringId = if (payload.createsRing) preRingId ?: newRingId() else newRingId()
        arrival(ActiveRing(ringId, payload.ringKind, payload.sourceId, RingResolver.fallbackAlarm(payload), payload.occurrenceAt, env.clock.now()))
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        log.e("Emergency ring failed", t)
        TriggerResult.SKIPPED
    }

    /** Rings now (triggerNow, weather, missed alarms) unless that occurrence already finished. */
    suspend fun ringNow(kind: RingKind, sourceId: Long, alarm: Alarm, occurrenceAt: Long): Boolean {
        awaitRestored()
        if (resolver.finishedEvent(OccurrenceKey(kind, sourceId, occurrenceAt)) != null) return false
        val ring = ActiveRing(newRingId(), kind, sourceId, alarm, occurrenceAt, env.clock.now())
        val result = arrival(ring)
        return result == TriggerResult.RANG || result == TriggerResult.QUEUED
    }

    /** A gesture (flip, power key, media key) fired while [ringId] was sounding. */
    suspend fun onGesture(ringId: String, action: ButtonAction): GestureResult {
        val ring = _book.value.find(ringId) ?: return GestureResult.NONE
        return when (val result = GesturePolicy.resolve(action, ring)) {
            GestureResult.SNOOZE ->
                if (dispatch(RingInput.Snooze(ringId))?.accepted == true) GestureResult.SNOOZE else GestureResult.OPEN_RING_SCREEN
            GestureResult.DISMISS -> {
                dispatch(RingInput.Dismiss(ringId, RingOutcome.DISMISSED))
                GestureResult.DISMISS
            }
            else -> result
        }
    }

    fun onMissionInteraction(ringId: String) {
        lastInteraction[ringId] = env.clock.now()
    }

    /** Cancels rings whose source was deleted or switched off (never a sounding ring of an unreadable alarm). */
    suspend fun reconcile(input: PlanInput, rules: List<CalendarAlarmRule>?) {
        val alarms = input.alarms.associateBy { it.id }
        val orphans = _book.value.all.filter { ring ->
            if (ring.state == RingState.AWAKE_CHECK) return@filter false
            val parked = ring.state == RingState.SNOOZED
            when (ring.kind) {
                RingKind.ALARM -> {
                    if (ring.sourceId <= 0L) return@filter false
                    val alarm = alarms[ring.sourceId]
                    when {
                        alarm == null -> parked
                        else -> !alarm.enabled || !TriggerPlanner.isGroupEnabled(alarm, input.groups)
                    }
                }
                RingKind.CALENDAR -> rules != null && rules.none { it.id == ring.sourceId && it.enabled } && parked
                else -> false
            }
        }
        orphans.forEach {
            log.i("Cancelling a ${it.kind} ring: its source was removed or disabled")
            dispatch(RingInput.Cancel(it.ringId))
        }
    }

    suspend fun cancelTimerRings(timerId: Long) {
        _book.value.all.filter { it.kind == RingKind.TIMER && it.sourceId == timerId }
            .forEach { dispatch(RingInput.Cancel(it.ringId)) }
    }

    fun consumeBriefing(ringId: String) {
        if (_briefing.value?.ringId == ringId) _briefing.value = null
    }

    // --- service --------------------------------------------------------------------------------

    /** Makes sure the ring service runs while a ring is sounding. */
    fun ensureService(reason: String) {
        if (AlarmRingService.isRunning) return
        val now = SystemClock.elapsedRealtime()
        if (now - serviceRequestedAt < SERVICE_RETRY_MILLIS) return
        serviceRequestedAt = now
        if (!AlarmRingService.start(env.context, reason)) env.scope.launch { onServiceStartRefused() }
    }

    /**
     * Android refused to start the foreground service from the background. Show the ring
     * notification right away (its full-screen intent opens the ring screen), and register an exact
     * alarm one second ahead: its broadcast carries the background-start exemption.
     */
    private suspend fun onServiceStartRefused() {
        val ring = _book.value.sounding ?: return
        val settings = env.settings()
        env.notifier.postRing(NotificationIds.ring(ring.ringId), env.notifier.ring(ring, settings))
        val attempts = kicks.merge(ring.ringId, 1, Int::plus) ?: 1
        if (attempts > MAX_KICKS) {
            log.e("The ring service still cannot start; the alarm is shown as a notification only")
            return
        }
        schedule().registerOne(kick(ring, env.clock.now() + KICK_DELAY_MILLIS * attempts))
        serviceRequestedAt = Long.MIN_VALUE / 2
    }

    private val kicks = ConcurrentHashMap<String, Int>()

    private fun kick(ring: ActiveRing, at: Long) = PlannedTrigger(
        identity = TriggerIdentities.kick(ring.ringId),
        kind = TriggerKind.SNOOZE_END,
        sourceId = ring.sourceId,
        triggerAt = at,
        occurrenceAt = ring.occurrenceAt,
        extra = TriggerExtra(ringId = ring.ringId, ringKind = ring.kind, purpose = TriggerExtra.PURPOSE_KICK),
        label = ring.label,
        method = RegisterMethod.ALARM_CLOCK,
    )

    // --- timers ---------------------------------------------------------------------------------

    /** (Re)arms the timer of the sounding ring when its session changed. Called under [mutex]. */
    private fun armTimers(sounding: ActiveRing?, force: Boolean) {
        val key = sounding?.let { TimerKey(it.ringId, it.state, it.startedAt, it.awakePromptShowing) }
        if (!force && key == armedKey) return
        armedKey = key
        timerJob?.cancel()
        timerJob = null
        if (sounding == null || key == null) return
        val armedAt = env.clock.now()
        timerJob = env.scope.launch {
            when {
                sounding.state == RingState.RINGING -> {
                    val duration = sounding.alarm.dismiss.ringDurationMinutes.coerceAtLeast(1) * MINUTE
                    val at = maxOf(sounding.startedAt + duration, armedAt + MIN_RING_LEFT_MILLIS)
                    delay(at - env.clock.now())
                    fire(RingInput.RingTimeout(sounding.ringId), key)
                }
                sounding.state == RingState.MISSION && sounding.alarm.dismiss.lowerVolumeDuringMission -> {
                    val idle = sounding.alarm.dismiss.missionIdleTimeoutSeconds.coerceAtLeast(MIN_IDLE_SECONDS) * 1_000L
                    lastInteraction.putIfAbsent(sounding.ringId, armedAt)
                    while (true) {
                        val remaining = (lastInteraction[sounding.ringId] ?: armedAt) + idle - env.clock.now()
                        if (remaining <= 0) break
                        delay(remaining)
                    }
                    fire(RingInput.MissionIdle(sounding.ringId), key)
                }
                sounding.state == RingState.AWAKE_CHECK && sounding.awakePromptShowing -> {
                    val answer = sounding.alarm.awakeCheck.responseSeconds.coerceAtLeast(MIN_AWAKE_SECONDS) * 1_000L
                    val at = maxOf(sounding.startedAt + answer, armedAt + MIN_AWAKE_SECONDS * 1_000L)
                    delay(at - env.clock.now())
                    fire(RingInput.AwakeTimeout(sounding.ringId), key)
                }
            }
        }
    }

    /** Timer events run as their own transition (never inside the timer job they would cancel). */
    private fun fire(input: RingInput, key: TimerKey) {
        env.scope.launch {
            dispatch(input) { book -> book.sounding?.let { TimerKey(it.ringId, it.state, it.startedAt, it.awakePromptShowing) } == key }
        }
    }

    // --- effects --------------------------------------------------------------------------------

    private suspend fun runEffect(effect: RingEffect) {
        guarded("Ring effect ${effect.javaClass.simpleName}", EFFECT_TIMEOUT_MILLIS, log) { apply(effect) }
    }

    private suspend fun apply(effect: RingEffect) {
        val now = env.clock.now()
        when (effect) {
            is RingEffect.EventStarted -> {
                val ring = effect.ring
                val id = env.data.events.insert(
                    AlarmEvent(
                        kind = ring.kind,
                        sourceId = ring.sourceId,
                        label = ring.label,
                        occurrenceAt = ring.occurrenceAt,
                        startedAt = ring.startedAt,
                        snoozeCount = ring.snoozeCount,
                    ),
                )
                eventIds[ring.ringId] = id
            }
            is RingEffect.EventSnoozed -> updateEvent(effect.ring) { it.copy(snoozeCount = effect.ring.snoozeCount) }
            is RingEffect.EventEnded -> {
                val ring = effect.ring
                val missionTotal = (missionMillis.remove(ring.ringId) ?: 0L) +
                    (missionStartedAt.remove(ring.ringId)?.let { now - it } ?: 0L)
                updateEvent(ring) {
                    it.copy(
                        endedAt = now,
                        outcome = effect.outcome,
                        snoozeCount = ring.snoozeCount,
                        missionMillis = it.missionMillis + missionTotal,
                    )
                }
            }
            is RingEffect.EventAwake -> updateEvent(effect.ring) { it.copy(awakeConfirmed = effect.confirmed) }
            is RingEffect.MissionClock -> if (effect.running) {
                missionStartedAt[effect.ringId] = now
                lastInteraction[effect.ringId] = now
            } else {
                missionStartedAt.remove(effect.ringId)?.let { started -> missionMillis.merge(effect.ringId, now - started, Long::plus) }
                lastInteraction.remove(effect.ringId)
            }
            is RingEffect.ScheduleSnoozeEnd, is RingEffect.ScheduleAwakeCheck -> {
                val ring = if (effect is RingEffect.ScheduleSnoozeEnd) effect.ring else (effect as RingEffect.ScheduleAwakeCheck).ring
                TriggerPlanner.ringTriggers(listOf(ring)).forEach { schedule().registerOne(it) }
            }
            is RingEffect.CancelRingTriggers -> schedule().cancelIdentities(ScheduleCoordinator.ringTriggerIdentities(effect.ringId))
            is RingEffect.ShowSnoozed -> env.notifier.showSnoozed(effect.ring, env.settings())
            is RingEffect.CancelSnoozed -> env.notifier.cancelSnoozed(effect.ringId)
            is RingEffect.CancelPreAlarmNotices -> env.notifier.cancelPreAlarmNotices(effect.alarmId)
            is RingEffect.DisableOneTimeAlarm -> env.data.alarms.update(effect.alarmId) {
                if (it.repeat.isRepeating) it else it.copy(enabled = false, skippedOccurrenceAt = null)
            }
            is RingEffect.DisableOneShotLocation -> env.data.locationAlarms.get(effect.locationAlarmId)
                ?.takeIf { it.oneShot && it.enabled }
                ?.let { env.data.locationAlarms.upsert(it.copy(enabled = false)) }
            is RingEffect.TimerRinging -> env.data.timers.getTimer(effect.timerId)?.let {
                env.data.timers.upsert(it.copy(state = TimerState.RINGING, remainingMs = 0L))
            }
            is RingEffect.TimerRestart -> {
                env.data.timers.getTimer(effect.timerId)?.let {
                    env.data.timers.upsert(it.copy(state = TimerState.RUNNING, endAt = effect.endAt, remainingMs = effect.endAt - now))
                }
                schedule().registerOne(timerTrigger(effect.timerId, effect.endAt))
            }
            is RingEffect.TimerReset -> {
                env.data.timers.getTimer(effect.timerId)?.let {
                    env.data.timers.upsert(it.copy(state = TimerState.IDLE, endAt = null, remainingMs = it.durationMs))
                }
                schedule().cancelIdentities(setOf(TriggerIdentities.timer(effect.timerId)))
            }
            is RingEffect.NotifyAutoStopped -> env.notifier.showAutoStopped(effect.ring)
            RingEffect.TurnOffDnd -> turnOffDnd()
            is RingEffect.SpeakBriefing -> {
                if (_briefing.value?.ringId == effect.ring.ringId) AlarmRingService.startForBriefing(env.context)
            }
            is RingEffect.HookStarted -> effect.ring.let { ring -> env.hook("onRingStarted") { onRingStarted(ring) } }
            is RingEffect.HookUpdated -> effect.ring.let { ring -> env.hook("onRingUpdated") { onRingUpdated(ring) } }
            is RingEffect.HookFinished -> {
                val ring = effect.ring
                val outcome = effect.outcome
                eventIds.remove(ring.ringId)
                lastInteraction.remove(ring.ringId)
                env.hook("onRingFinished") { onRingFinished(ring, outcome) }
            }
            RingEffect.Reschedule -> schedule().request("ring state changed")
        }
    }

    private suspend fun updateEvent(ring: ActiveRing, transform: (AlarmEvent) -> AlarmEvent) {
        val candidates = env.data.events.between(ring.occurrenceAt, ring.occurrenceAt + 1)
            .filter { it.kind == ring.kind && it.sourceId == ring.sourceId }
        val known = eventIds[ring.ringId]
        val event = candidates.firstOrNull { it.id == known } ?: candidates.maxByOrNull { it.id } ?: return
        env.data.events.update(transform(event))
    }

    private fun timerTrigger(timerId: Long, endAt: Long) = PlannedTrigger(
        identity = TriggerIdentities.timer(timerId),
        kind = TriggerKind.TIMER,
        sourceId = timerId,
        triggerAt = endAt,
        occurrenceAt = endAt,
        method = RegisterMethod.ALARM_CLOCK,
    )

    /** settings.bedtime.disableDndAtAlarm: Do Not Disturb off when the morning alarm is dismissed. */
    private suspend fun turnOffDnd() {
        if (!env.settings().bedtime.disableDndAtAlarm) return
        val manager = env.context.getSystemService(NotificationManager::class.java) ?: return
        if (!manager.isNotificationPolicyAccessGranted) return
        val filter = manager.currentInterruptionFilter
        if (filter == NotificationManager.INTERRUPTION_FILTER_ALL || filter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) return
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        log.i("Do Not Disturb turned off after the alarm")
    }

    companion object {
        private const val MINUTE = 60_000L
        const val RESTORE_TIMEOUT_MILLIS = 10_000L
        const val RESTORE_WAIT_MILLIS = 12_000L
        private const val RESTORE_RETRIES = 20
        private const val RESTORE_RETRY_DELAY_MILLIS = 30_000L
        const val PERSIST_TIMEOUT_MILLIS = 5_000L
        const val EFFECT_TIMEOUT_MILLIS = 5_000L
        const val RESOLVE_TIMEOUT_MILLIS = 3_000L
        /** A restored ring, or one back from a mission, keeps ringing at least this long before the auto action. */
        const val MIN_RING_LEFT_MILLIS = 60_000L
        const val MIN_IDLE_SECONDS = 5
        const val MIN_AWAKE_SECONDS = 10
        private const val SERVICE_RETRY_MILLIS = 3_000L
        const val KICK_DELAY_MILLIS = 1_000L
        private const val MAX_KICKS = 5

        fun newRingId(): String = UUID.randomUUID().toString()
    }
}
