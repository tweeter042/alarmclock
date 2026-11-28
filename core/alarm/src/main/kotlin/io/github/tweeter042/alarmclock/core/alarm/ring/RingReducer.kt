package io.github.tweeter042.alarmclock.core.alarm.ring

import io.github.tweeter042.alarmclock.core.model.alarm.ActiveRing
import io.github.tweeter042.alarmclock.core.model.alarm.AutoAction
import io.github.tweeter042.alarmclock.core.model.alarm.BriefingTiming
import io.github.tweeter042.alarmclock.core.model.alarm.RingKind
import io.github.tweeter042.alarmclock.core.model.alarm.RingOutcome
import io.github.tweeter042.alarmclock.core.model.alarm.RingState
import io.github.tweeter042.alarmclock.core.model.alarm.SnoozePolicy
import io.github.tweeter042.alarmclock.core.model.alarm.isRepeating

/** Everything that can happen to the rings. */
internal sealed interface RingInput {
    /** A new ring (alarm, calendar alarm, nap, test, timer, location, smart wake, weather). */
    data class Arrive(val ring: ActiveRing) : RingInput
    data class SnoozeEnded(val ringId: String) : RingInput
    data class AwakeCheckDue(val ringId: String) : RingInput
    data class Snooze(val ringId: String) : RingInput
    data class Dismiss(val ringId: String, val outcome: RingOutcome) : RingInput
    data class StartMission(val ringId: String) : RingInput
    data class CompleteMission(val ringId: String) : RingInput
    data class AbandonMission(val ringId: String) : RingInput
    /** No mission interaction for missionIdleTimeoutSeconds. */
    data class MissionIdle(val ringId: String) : RingInput
    /** Rang for ringDurationMinutes without an answer. */
    data class RingTimeout(val ringId: String) : RingInput
    /** The awake-check prompt was not answered within responseSeconds. */
    data class AwakeTimeout(val ringId: String) : RingInput
    data class ConfirmAwake(val ringId: String) : RingInput
    /** The ring's source was deleted or disabled while it was ringing or snoozed. */
    data class Cancel(val ringId: String) : RingInput
}

/** Side effects requested by a transition; executed in order by the engine. */
internal sealed interface RingEffect {
    data class EventStarted(val ring: ActiveRing) : RingEffect
    data class EventSnoozed(val ring: ActiveRing) : RingEffect
    data class EventEnded(val ring: ActiveRing, val outcome: RingOutcome) : RingEffect
    data class EventAwake(val ring: ActiveRing, val confirmed: Boolean) : RingEffect
    /** Mission time accounting for the event log. */
    data class MissionClock(val ringId: String, val running: Boolean) : RingEffect
    data class ScheduleSnoozeEnd(val ring: ActiveRing) : RingEffect
    data class ScheduleAwakeCheck(val ring: ActiveRing) : RingEffect
    /** Cancels the ring's SNOOZE_END / AWAKE_CHECK / kick triggers. */
    data class CancelRingTriggers(val ringId: String) : RingEffect
    data class ShowSnoozed(val ring: ActiveRing) : RingEffect
    data class CancelSnoozed(val ringId: String) : RingEffect
    /** The alarm is ringing: its "upcoming" and sunrise notifications are obsolete. */
    data class CancelPreAlarmNotices(val alarmId: Long) : RingEffect
    data class DisableOneTimeAlarm(val alarmId: Long) : RingEffect
    data class DisableOneShotLocation(val locationAlarmId: Long) : RingEffect
    data class TimerRinging(val timerId: Long) : RingEffect
    /** TIMER "+1 minute": the timer runs again and ends at [endAt]. */
    data class TimerRestart(val timerId: Long, val endAt: Long) : RingEffect
    data class TimerReset(val timerId: Long) : RingEffect
    data class NotifyAutoStopped(val ring: ActiveRing) : RingEffect
    data object TurnOffDnd : RingEffect
    data class SpeakBriefing(val ring: ActiveRing) : RingEffect
    data class HookStarted(val ring: ActiveRing) : RingEffect
    data class HookUpdated(val ring: ActiveRing) : RingEffect
    data class HookFinished(val ring: ActiveRing, val outcome: RingOutcome) : RingEffect
    /** The set of rings changed in a way that affects the schedule (next alarm, handled occurrences). */
    data object Reschedule : RingEffect
}

internal data class RingStep(
    val book: RingBook,
    val effects: List<RingEffect>,
    /** False when the input was rejected (unknown ring, wrong state, snoozing not allowed…). */
    val accepted: Boolean,
)

/**
 * The ring state machine, as a pure function (exhaustively unit tested).
 *
 * `RINGING ⇄ MISSION → (dismiss) → AWAKE_CHECK? → FINISHED`, `RINGING → SNOOZED → RINGING`.
 * - A ring arriving while another one sounds is queued (FIFO) and starts when the speaker is free.
 * - Snoozing honours SnoozePolicy. With MISSION_TO_SNOOZE (and missions) a user snooze is refused
 *   but remembered in [RingBook.pendingSnooze]: finishing the missions then snoozes.
 * - The ring timeout snoozes (if allowed, counting toward the maximum) or auto-dismisses.
 * - TIMER rings: snooze = "+1 minute" (the timer is restarted), dismiss = reset the timer.
 * - Dismissing a one-time alarm disables it; a user dismissal starts the awake check if enabled.
 */
internal object RingReducer {

    const val TIMER_SNOOZE_MILLIS = 60_000L
    private const val MINUTE = 60_000L

    private enum class SnoozeSource { USER, MISSIONS, AUTO }

    private data class Outcome(val book: RingBook, val accepted: Boolean)

    fun reduce(book: RingBook, input: RingInput, now: Long): RingStep {
        val fx = ArrayList<RingEffect>()
        val outcome = when (input) {
            is RingInput.Arrive -> arrive(book, input.ring, fx)
            is RingInput.SnoozeEnded -> snoozeEnded(book, input.ringId, now, fx)
            is RingInput.AwakeCheckDue -> awakeCheckDue(book, input.ringId, now, fx)
            is RingInput.Snooze -> snooze(book, input.ringId, now, SnoozeSource.USER, fx)
            is RingInput.Dismiss -> dismiss(book, input.ringId, input.outcome, now, fx)
            is RingInput.StartMission -> startMission(book, input.ringId, now, fx)
            is RingInput.CompleteMission -> completeMission(book, input.ringId, now, fx)
            is RingInput.AbandonMission -> leaveMission(book, input.ringId, abandoned = true, fx)
            is RingInput.MissionIdle -> leaveMission(book, input.ringId, abandoned = false, fx)
            is RingInput.RingTimeout -> ringTimeout(book, input.ringId, now, fx)
            is RingInput.AwakeTimeout -> awakeTimeout(book, input.ringId, now, fx)
            is RingInput.ConfirmAwake -> confirmAwake(book, input.ringId, fx)
            is RingInput.Cancel -> cancel(book, input.ringId, fx)
        }
        val settled = settle(book.current, outcome.book, now, fx)
        if (scheduleKey(book) != scheduleKey(settled)) fx += RingEffect.Reschedule
        return RingStep(settled, dedupeHooks(fx), outcome.accepted)
    }

    // --- transitions --------------------------------------------------------------------------

    private fun arrive(book: RingBook, ring: ActiveRing, fx: MutableList<RingEffect>): Outcome {
        if (book.find(ring.ringId) != null || book.contains(ring.occurrenceKey())) return Outcome(book, false)
        if (ring.kind == RingKind.LOCATION && book.all.any { it.kind == RingKind.LOCATION && it.sourceId == ring.sourceId }) {
            return Outcome(book, false)
        }
        val arrived = ring.copy(
            state = RingState.RINGING,
            snoozedUntil = null,
            awakeCheckDueAt = null,
            awakePromptShowing = false,
            currentMissionIndex = 0,
        )
        fx += RingEffect.EventStarted(arrived)
        when (arrived.kind) {
            RingKind.ALARM -> fx += RingEffect.CancelPreAlarmNotices(arrived.sourceId)
            RingKind.TIMER -> fx += RingEffect.TimerRinging(arrived.sourceId)
            RingKind.LOCATION -> fx += RingEffect.DisableOneShotLocation(arrived.sourceId)
            else -> Unit
        }
        // Queued behind the sounding ring; settle() makes it current when the speaker is free.
        return Outcome(book.copy(others = book.others + arrived), true)
    }

    private fun snoozeEnded(book: RingBook, ringId: String, now: Long, fx: MutableList<RingEffect>): Outcome {
        val ring = book.find(ringId) ?: return Outcome(book, false)
        if (ring.state != RingState.SNOOZED) return Outcome(book, true)
        fx += RingEffect.CancelSnoozed(ringId)
        val ringing = ring.copy(state = RingState.RINGING, snoozedUntil = null, startedAt = now)
        return Outcome(book.requeue(ringing), true)
    }

    private fun awakeCheckDue(book: RingBook, ringId: String, now: Long, fx: MutableList<RingEffect>): Outcome {
        val ring = book.find(ringId) ?: return Outcome(book, false)
        if (ring.state != RingState.AWAKE_CHECK) return Outcome(book, false)
        if (ring.awakePromptShowing) return Outcome(book, true)
        val prompting = ring.copy(awakePromptShowing = true, startedAt = now)
        fx += RingEffect.HookUpdated(prompting)
        return Outcome(book.requeue(prompting), true)
    }

    private fun snooze(book: RingBook, ringId: String, now: Long, source: SnoozeSource, fx: MutableList<RingEffect>): Outcome {
        val ring = book.find(ringId) ?: return Outcome(book, false)
        if (ring.state != RingState.RINGING && ring.state != RingState.MISSION) return Outcome(book, false)

        if (ring.kind == RingKind.TIMER) {
            val counted = ring.copy(snoozeCount = ring.snoozeCount + 1)
            fx += RingEffect.CancelRingTriggers(ringId)
            fx += RingEffect.EventEnded(counted, RingOutcome.DISMISSED)
            fx += RingEffect.TimerRestart(ring.sourceId, now + TIMER_SNOOZE_MILLIS)
            fx += RingEffect.HookFinished(counted, RingOutcome.DISMISSED)
            return Outcome(book.without(ringId), true)
        }

        val config = ring.alarm.snooze
        if (!SnoozePolicy.canSnooze(config, ring.snoozeCount)) {
            return Outcome(book.copy(pendingSnooze = book.pendingSnooze - ringId), false)
        }
        if (source == SnoozeSource.USER && SnoozePolicy.missionsRequiredToSnooze(ring.alarm)) {
            // The UI runs the missions; completeMission() then snoozes instead of dismissing.
            return Outcome(book.copy(pendingSnooze = book.pendingSnooze + ringId), false)
        }
        val minutes = SnoozePolicy.nextDurationMinutes(config, ring.snoozeCount)
        val snoozed = ring.copy(
            state = RingState.SNOOZED,
            snoozeCount = ring.snoozeCount + 1,
            snoozedUntil = now + minutes * MINUTE,
            currentMissionIndex = 0,
            awakePromptShowing = false,
        )
        if (ring.state == RingState.MISSION) fx += RingEffect.MissionClock(ringId, running = false)
        fx += RingEffect.ScheduleSnoozeEnd(snoozed)
        fx += RingEffect.ShowSnoozed(snoozed)
        fx += RingEffect.EventSnoozed(snoozed)
        fx += RingEffect.HookUpdated(snoozed)
        return Outcome(book.replace(snoozed).copy(pendingSnooze = book.pendingSnooze - ringId), true)
    }

    private fun dismiss(book: RingBook, ringId: String, outcome: RingOutcome, now: Long, fx: MutableList<RingEffect>): Outcome {
        val ring = book.find(ringId) ?: return Outcome(book, false)
        fx += RingEffect.CancelRingTriggers(ringId)
        fx += RingEffect.CancelSnoozed(ringId)
        if (ring.state == RingState.MISSION) fx += RingEffect.MissionClock(ringId, running = false)

        if (ring.state == RingState.AWAKE_CHECK) {
            // The alarm itself was dismissed earlier; this ends the awake check.
            if (ring.awakePromptShowing) fx += RingEffect.EventAwake(ring, confirmed = true)
            fx += RingEffect.HookFinished(ring, RingOutcome.DISMISSED)
            return Outcome(book.without(ringId), true)
        }

        when (ring.kind) {
            RingKind.TIMER -> fx += RingEffect.TimerReset(ring.sourceId)
            RingKind.ALARM -> if (!ring.alarm.repeat.isRepeating) fx += RingEffect.DisableOneTimeAlarm(ring.sourceId)
            else -> Unit
        }
        val byUser = outcome == RingOutcome.DISMISSED || outcome == RingOutcome.DISMISSED_ON_WATCH
        val morningAlarm = ring.kind == RingKind.ALARM || ring.kind == RingKind.CALENDAR
        if (morningAlarm && (byUser || outcome == RingOutcome.AUTO_DISMISSED)) fx += RingEffect.TurnOffDnd
        if (outcome == RingOutcome.AUTO_DISMISSED) fx += RingEffect.NotifyAutoStopped(ring)
        fx += RingEffect.EventEnded(ring, outcome)
        if (byUser && ring.kind != RingKind.TIMER && ring.alarm.briefing.enabled &&
            ring.alarm.briefing.timing == BriefingTiming.AFTER_DISMISS
        ) {
            fx += RingEffect.SpeakBriefing(ring)
        }

        val awake = ring.alarm.awakeCheck
        if (byUser && awake.enabled && ring.kind != RingKind.TIMER) {
            val checking = ring.copy(
                state = RingState.AWAKE_CHECK,
                awakeCheckDueAt = now + awake.delayMinutes.coerceAtLeast(1) * MINUTE,
                awakePromptShowing = false,
                snoozedUntil = null,
                currentMissionIndex = 0,
            )
            fx += RingEffect.ScheduleAwakeCheck(checking)
            fx += RingEffect.HookUpdated(checking)
            return Outcome(book.replace(checking).copy(pendingSnooze = book.pendingSnooze - ringId), true)
        }
        fx += RingEffect.HookFinished(ring, outcome)
        return Outcome(book.without(ringId), true)
    }

    private fun startMission(book: RingBook, ringId: String, now: Long, fx: MutableList<RingEffect>): Outcome {
        val ring = book.find(ringId) ?: return Outcome(book, false)
        val otherSounding = book.sounding?.takeIf { it.ringId != ringId }
        when (ring.state) {
            RingState.MISSION -> return Outcome(book, true)
            RingState.RINGING -> if (otherSounding != null) return Outcome(book, false)
            RingState.SNOOZED -> {
                // "Dismiss" on a snoozed alarm with missions: the missions run (alarm sound ducked).
                if (otherSounding != null) return Outcome(book, false)
                fx += RingEffect.CancelRingTriggers(ringId)
                fx += RingEffect.CancelSnoozed(ringId)
            }
            else -> return Outcome(book, false)
        }
        val inMission = ring.copy(
            state = RingState.MISSION,
            snoozedUntil = null,
            startedAt = if (ring.state == RingState.SNOOZED) now else ring.startedAt,
        )
        fx += RingEffect.MissionClock(ringId, running = true)
        fx += RingEffect.HookUpdated(inMission)
        return Outcome(book.makeCurrent(inMission), true)
    }

    private fun completeMission(book: RingBook, ringId: String, now: Long, fx: MutableList<RingEffect>): Outcome {
        val ring = book.find(ringId) ?: return Outcome(book, false)
        if (ring.state != RingState.MISSION && ring.state != RingState.RINGING) return Outcome(book, false)
        val missions = ring.alarm.missions.size
        val next = ring.currentMissionIndex + 1
        if (next >= missions) {
            val done = book.replace(ring.copy(currentMissionIndex = missions))
            if (ringId in book.pendingSnooze) {
                val snoozed = snooze(done, ringId, now, SnoozeSource.MISSIONS, fx)
                if (snoozed.accepted) return snoozed
            }
            return dismiss(done.copy(pendingSnooze = done.pendingSnooze - ringId), ringId, RingOutcome.DISMISSED, now, fx)
        }
        val progressed = ring.copy(state = RingState.MISSION, currentMissionIndex = next)
        if (ring.state != RingState.MISSION) fx += RingEffect.MissionClock(ringId, running = true)
        fx += RingEffect.HookUpdated(progressed)
        return Outcome(book.replace(progressed), true)
    }

    private fun leaveMission(book: RingBook, ringId: String, abandoned: Boolean, fx: MutableList<RingEffect>): Outcome {
        val ring = book.find(ringId) ?: return Outcome(book, false)
        if (ring.state != RingState.MISSION) return Outcome(book, false)
        val index = if (abandoned && ring.alarm.dismiss.antiCheat) 0 else ring.currentMissionIndex
        val ringing = ring.copy(state = RingState.RINGING, currentMissionIndex = index)
        fx += RingEffect.MissionClock(ringId, running = false)
        fx += RingEffect.HookUpdated(ringing)
        return Outcome(book.replace(ringing).copy(pendingSnooze = book.pendingSnooze - ringId), true)
    }

    private fun ringTimeout(book: RingBook, ringId: String, now: Long, fx: MutableList<RingEffect>): Outcome {
        val ring = book.sounding?.takeIf { it.ringId == ringId && it.state == RingState.RINGING } ?: return Outcome(book, false)
        if (ring.kind != RingKind.TIMER && ring.alarm.dismiss.autoAction == AutoAction.SNOOZE &&
            SnoozePolicy.canSnooze(ring.alarm.snooze, ring.snoozeCount)
        ) {
            val snoozed = snooze(book, ringId, now, SnoozeSource.AUTO, fx)
            if (snoozed.accepted) return snoozed
        }
        return dismiss(book, ringId, RingOutcome.AUTO_DISMISSED, now, fx)
    }

    private fun awakeTimeout(book: RingBook, ringId: String, now: Long, fx: MutableList<RingEffect>): Outcome {
        val ring = book.sounding
            ?.takeIf { it.ringId == ringId && it.state == RingState.AWAKE_CHECK && it.awakePromptShowing }
            ?: return Outcome(book, false)
        val again = ring.copy(
            state = RingState.RINGING,
            awakePromptShowing = false,
            awakeCheckDueAt = null,
            currentMissionIndex = 0,
            startedAt = now,
        )
        fx += RingEffect.EventAwake(ring, confirmed = false)
        fx += RingEffect.HookUpdated(again)
        return Outcome(book.replace(again), true)
    }

    private fun confirmAwake(book: RingBook, ringId: String, fx: MutableList<RingEffect>): Outcome {
        val ring = book.find(ringId)?.takeIf { it.state == RingState.AWAKE_CHECK } ?: return Outcome(book, false)
        fx += RingEffect.CancelRingTriggers(ringId)
        fx += RingEffect.EventAwake(ring, confirmed = true)
        fx += RingEffect.HookFinished(ring, RingOutcome.DISMISSED)
        return Outcome(book.without(ringId), true)
    }

    private fun cancel(book: RingBook, ringId: String, fx: MutableList<RingEffect>): Outcome {
        val ring = book.find(ringId) ?: return Outcome(book, false)
        fx += RingEffect.CancelRingTriggers(ringId)
        fx += RingEffect.CancelSnoozed(ringId)
        if (ring.state == RingState.MISSION) fx += RingEffect.MissionClock(ringId, running = false)
        // An awake check already logged the dismissal; keep that outcome.
        if (ring.state != RingState.AWAKE_CHECK) fx += RingEffect.EventEnded(ring, RingOutcome.CANCELLED)
        fx += RingEffect.HookFinished(ring, RingOutcome.CANCELLED)
        return Outcome(book.without(ringId), true)
    }

    // --- helpers ------------------------------------------------------------------------------

    /**
     * Normalizes the book and, when a ring starts sounding (it was not the sounding ring before),
     * stamps its start time and reports it: HookStarted for a fresh ring, HookUpdated for a resumed
     * one (snooze ended, awake prompt, missions on a snoozed ring).
     */
    private fun settle(previous: ActiveRing?, book: RingBook, now: Long, fx: MutableList<RingEffect>): RingBook {
        val normalized = RingBook.normalize(book)
        val current = normalized.current ?: return normalized
        if (!current.isReady) return normalized
        val wasSounding = previous != null && previous.ringId == current.ringId && previous.isReady
        if (wasSounding) return normalized
        val started = if (current.state == RingState.RINGING) current.copy(startedAt = now) else current
        fx += if (started.state == RingState.RINGING && started.snoozeCount == 0) {
            RingEffect.HookStarted(started)
        } else {
            RingEffect.HookUpdated(started)
        }
        return normalized.copy(current = started)
    }

    /** Ring ids with their schedule-relevant state: when this changes, the schedule must be recomputed. */
    private fun scheduleKey(book: RingBook): Set<Triple<String, RingState, Long?>> =
        book.all.mapTo(HashSet()) { Triple(it.ringId, it.state, it.dueAt) }

    /** One hook per ring and step: Finished beats Started beats Updated; the latest ring state wins. */
    private fun dedupeHooks(effects: List<RingEffect>): List<RingEffect> {
        fun ringIdOf(effect: RingEffect): String? = when (effect) {
            is RingEffect.HookStarted -> effect.ring.ringId
            is RingEffect.HookUpdated -> effect.ring.ringId
            is RingEffect.HookFinished -> effect.ring.ringId
            else -> null
        }
        fun rank(effect: RingEffect): Int = when (effect) {
            is RingEffect.HookFinished -> 3
            is RingEffect.HookStarted -> 2
            else -> 1
        }
        val hooks = effects.filter { ringIdOf(it) != null }
        if (hooks.size <= 1) return effects
        val chosen = hooks.groupBy { ringIdOf(it)!! }.mapValues { (_, list) ->
            val best = list.maxOf { rank(it) }
            val latest = list.last()
            val latestRing = when (latest) {
                is RingEffect.HookStarted -> latest.ring
                is RingEffect.HookUpdated -> latest.ring
                is RingEffect.HookFinished -> latest.ring
                else -> error("not a hook")
            }
            when (best) {
                3 -> list.last { it is RingEffect.HookFinished }
                2 -> RingEffect.HookStarted(latestRing)
                else -> RingEffect.HookUpdated(latestRing)
            }
        }
        val emitted = HashSet<String>()
        return effects.mapNotNull { effect ->
            val id = ringIdOf(effect) ?: return@mapNotNull effect
            if (emitted.add(id)) chosen.getValue(id) else null
        }
    }
}
