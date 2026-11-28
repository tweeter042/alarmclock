package io.github.tweeter042.alarmclock.core.alarm.ring

import io.github.tweeter042.alarmclock.core.model.alarm.ActiveRing
import io.github.tweeter042.alarmclock.core.model.alarm.Alarm
import io.github.tweeter042.alarmclock.core.model.alarm.AutoAction
import io.github.tweeter042.alarmclock.core.model.alarm.AwakeCheckConfig
import io.github.tweeter042.alarmclock.core.model.alarm.BriefingConfig
import io.github.tweeter042.alarmclock.core.model.alarm.BriefingTiming
import io.github.tweeter042.alarmclock.core.model.alarm.DismissConfig
import io.github.tweeter042.alarmclock.core.model.alarm.MissionConfig
import io.github.tweeter042.alarmclock.core.model.alarm.MissionType
import io.github.tweeter042.alarmclock.core.model.alarm.RepeatRule
import io.github.tweeter042.alarmclock.core.model.alarm.RingKind
import io.github.tweeter042.alarmclock.core.model.alarm.RingOutcome
import io.github.tweeter042.alarmclock.core.model.alarm.RingState
import io.github.tweeter042.alarmclock.core.model.alarm.SnoozeConfig
import io.github.tweeter042.alarmclock.core.model.alarm.SnoozeMode
import io.github.tweeter042.alarmclock.core.model.time.IsoDays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RingReducerTest {

    private val t0 = 1_795_000_000_000L
    private val minute = 60_000L

    private fun alarm(
        id: Long = 1L,
        snooze: SnoozeConfig = SnoozeConfig(),
        missions: List<MissionConfig> = emptyList(),
        awake: AwakeCheckConfig = AwakeCheckConfig(),
        repeat: RepeatRule = RepeatRule.Weekly(IsoDays.ALL),
        dismiss: DismissConfig = DismissConfig(),
        briefing: BriefingConfig = BriefingConfig(),
    ) = Alarm(id = id, label = "Wake", repeat = repeat, snooze = snooze, missions = missions, awakeCheck = awake, dismiss = dismiss, briefing = briefing)

    private fun ring(
        id: String,
        sourceId: Long = 1L,
        kind: RingKind = RingKind.ALARM,
        alarm: Alarm = alarm(id = sourceId),
        occurrenceAt: Long = t0 + sourceId,
        state: RingState = RingState.RINGING,
        snoozeCount: Int = 0,
        snoozedUntil: Long? = null,
        awakeDue: Long? = null,
        prompt: Boolean = false,
        startedAt: Long = t0,
    ) = ActiveRing(
        ringId = id,
        kind = kind,
        sourceId = sourceId,
        alarm = alarm,
        occurrenceAt = occurrenceAt,
        startedAt = startedAt,
        state = state,
        snoozeCount = snoozeCount,
        snoozedUntil = snoozedUntil,
        awakeCheckDueAt = awakeDue,
        awakePromptShowing = prompt,
    )

    private fun reduce(book: RingBook, input: RingInput, now: Long = t0 + minute) = RingReducer.reduce(book, input, now)

    private fun arrive(book: RingBook, ring: ActiveRing, now: Long = t0) = reduce(book, RingInput.Arrive(ring), now)

    private inline fun <reified T : RingEffect> RingStep.effects(): List<T> = effects.filterIsInstance<T>()

    private fun missions(n: Int) = List(n) { MissionConfig(MissionType.MATH) }

    // --- arrival & queueing ---------------------------------------------------------------------

    @Test fun arrivalWhenIdleBecomesCurrentAndStarts() {
        val step = arrive(RingBook.EMPTY, ring("a", startedAt = 0L), now = t0 + 5)
        assertTrue(step.accepted)
        assertEquals("a", step.book.current?.ringId)
        assertEquals(RingState.RINGING, step.book.current?.state)
        assertEquals("start time is stamped when it starts sounding", t0 + 5, step.book.current?.startedAt)
        assertEquals(1, step.effects<RingEffect.EventStarted>().size)
        assertEquals(listOf("a"), step.effects<RingEffect.HookStarted>().map { it.ring.ringId })
        assertEquals(listOf(1L), step.effects<RingEffect.CancelPreAlarmNotices>().map { it.alarmId })
        assertTrue(RingEffect.Reschedule in step.effects)
    }

    @Test fun ringsArrivingWhileOneSoundsAreQueuedFifoNeverDropped() {
        var book = arrive(RingBook.EMPTY, ring("a", sourceId = 1)).book
        val b = arrive(book, ring("b", sourceId = 2))
        assertTrue(b.accepted)
        assertTrue("a queued ring is not started yet", b.effects<RingEffect.HookStarted>().isEmpty())
        assertEquals(1, b.effects<RingEffect.EventStarted>().size)
        book = arrive(b.book, ring("c", sourceId = 3)).book
        assertEquals("a", book.current?.ringId)
        assertEquals(listOf("b", "c"), book.others.map { it.ringId })

        val afterA = reduce(book, RingInput.Dismiss("a", RingOutcome.DISMISSED), now = t0 + 7 * minute)
        assertEquals("b", afterA.book.current?.ringId)
        assertEquals(t0 + 7 * minute, afterA.book.current?.startedAt)
        assertEquals(listOf("b"), afterA.effects<RingEffect.HookStarted>().map { it.ring.ringId })
        val afterB = reduce(afterA.book, RingInput.Dismiss("b", RingOutcome.DISMISSED))
        assertEquals("c", afterB.book.current?.ringId)
        assertNull(reduce(afterB.book, RingInput.Dismiss("c", RingOutcome.DISMISSED)).book.current)
    }

    @Test fun duplicateRingsAreRejected() {
        val book = arrive(RingBook.EMPTY, ring("a", sourceId = 1, occurrenceAt = t0)).book
        assertFalse("same ring id", arrive(book, ring("a", sourceId = 9, occurrenceAt = t0 + 1)).accepted)
        val sameOccurrence = arrive(book, ring("other", sourceId = 1, occurrenceAt = t0))
        assertFalse("same alarm occurrence", sameOccurrence.accepted)
        assertEquals(book, sameOccurrence.book)
        assertTrue(sameOccurrence.effects.isEmpty())
    }

    @Test fun aLocationAlarmRingsOnlyOnceAtATime() {
        val loc = ring("l1", sourceId = 4, kind = RingKind.LOCATION, occurrenceAt = t0)
        val first = arrive(RingBook.EMPTY, loc)
        assertEquals(listOf(4L), first.effects<RingEffect.DisableOneShotLocation>().map { it.locationAlarmId })
        assertFalse(arrive(first.book, loc.copy(ringId = "l2", occurrenceAt = t0 + 5000)).accepted)
    }

    @Test fun aNewRingTakesOverFromASnoozedOne() {
        val snoozed = ring("a", state = RingState.SNOOZED, snoozeCount = 1, snoozedUntil = t0 + 10 * minute)
        val book = RingBook(current = snoozed)
        val step = arrive(book, ring("b", sourceId = 2))
        assertEquals("b", step.book.current?.ringId)
        assertEquals(listOf("a"), step.book.others.map { it.ringId })
        assertEquals(RingState.SNOOZED, step.book.find("a")?.state)
    }

    @Test fun timerArrivalMarksTheTimerRinging() {
        val step = arrive(RingBook.EMPTY, ring("t", sourceId = 7, kind = RingKind.TIMER))
        assertEquals(listOf(7L), step.effects<RingEffect.TimerRinging>().map { it.timerId })
        assertTrue(step.effects<RingEffect.CancelPreAlarmNotices>().isEmpty())
    }

    // --- snooze -------------------------------------------------------------------------------

    @Test fun snoozeParksTheRingAndSchedulesItsEnd() {
        val book = arrive(RingBook.EMPTY, ring("a")).book
        val now = t0 + 2 * minute
        val step = reduce(book, RingInput.Snooze("a"), now)
        assertTrue(step.accepted)
        val snoozed = step.book.find("a")!!
        assertEquals(RingState.SNOOZED, snoozed.state)
        assertEquals(1, snoozed.snoozeCount)
        assertEquals(now + 5 * minute, snoozed.snoozedUntil)
        assertEquals("the snoozed ring is shown when nothing else rings", "a", step.book.current?.ringId)
        assertNull(step.book.sounding)
        assertEquals(1, step.effects<RingEffect.ScheduleSnoozeEnd>().size)
        assertEquals(1, step.effects<RingEffect.ShowSnoozed>().size)
        assertEquals(1, step.effects<RingEffect.EventSnoozed>().size)
        assertEquals(1, step.effects<RingEffect.HookUpdated>().size)
        assertTrue(RingEffect.Reschedule in step.effects)
    }

    @Test fun snoozeRespectsTheMaximumAndTheSwitch() {
        val maxed = arrive(RingBook.EMPTY, ring("a", alarm = alarm(snooze = SnoozeConfig(maxCount = 3)), snoozeCount = 3)).book
        val refused = reduce(maxed, RingInput.Snooze("a"))
        assertFalse(refused.accepted)
        assertEquals(RingState.RINGING, refused.book.find("a")?.state)

        val off = arrive(RingBook.EMPTY, ring("b", alarm = alarm(snooze = SnoozeConfig(enabled = false)))).book
        assertFalse(reduce(off, RingInput.Snooze("b")).accepted)

        val unlimited = arrive(RingBook.EMPTY, ring("c", alarm = alarm(snooze = SnoozeConfig(maxCount = 0)), snoozeCount = 50)).book
        assertTrue(reduce(unlimited, RingInput.Snooze("c")).accepted)
    }

    @Test fun decreasingSnoozeGetsShorterDownToTheMinimum() {
        val config = SnoozeConfig(durationMinutes = 10, mode = SnoozeMode.DECREASING, decreaseStepMinutes = 3, minDurationMinutes = 2, maxCount = 0)
        val durations = (0..4).map { count ->
            val book = arrive(RingBook.EMPTY, ring("a", alarm = alarm(snooze = config), snoozeCount = count)).book
            val now = t0 + minute
            (reduce(book, RingInput.Snooze("a"), now).book.find("a")!!.snoozedUntil!! - now) / minute
        }
        assertEquals(listOf(10L, 7L, 4L, 2L, 2L), durations)
    }

    @Test fun snoozingTheSoundingRingStartsTheNextQueuedOne() {
        val book = arrive(arrive(RingBook.EMPTY, ring("a", sourceId = 1)).book, ring("b", sourceId = 2)).book
        val now = t0 + 3 * minute
        val step = reduce(book, RingInput.Snooze("a"), now)
        assertEquals("b", step.book.current?.ringId)
        assertEquals(now, step.book.current?.startedAt)
        assertEquals(RingState.SNOOZED, step.book.find("a")?.state)
        assertEquals(listOf("b"), step.effects<RingEffect.HookStarted>().map { it.ring.ringId })
    }

    @Test fun aWaitingRingCanBeSnoozedWithoutTouchingTheSoundingOne() {
        val book = arrive(arrive(RingBook.EMPTY, ring("a", sourceId = 1)).book, ring("b", sourceId = 2)).book
        val step = reduce(book, RingInput.Snooze("b"))
        assertTrue(step.accepted)
        assertEquals("a", step.book.sounding?.ringId)
        assertEquals(RingState.SNOOZED, step.book.find("b")?.state)
    }

    @Test fun snoozeEndResumesTheRing() {
        val snoozed = ring("a", state = RingState.SNOOZED, snoozeCount = 1, snoozedUntil = t0 + 5 * minute)
        val now = t0 + 5 * minute
        val step = reduce(RingBook(current = snoozed), RingInput.SnoozeEnded("a"), now)
        val resumed = step.book.sounding!!
        assertEquals(RingState.RINGING, resumed.state)
        assertNull(resumed.snoozedUntil)
        assertEquals(now, resumed.startedAt)
        assertEquals(1, resumed.snoozeCount)
        assertTrue("a resumed ring is an update, not a new start", step.effects<RingEffect.HookStarted>().isEmpty())
        assertEquals(1, step.effects<RingEffect.HookUpdated>().size)
        assertEquals(listOf("a"), step.effects<RingEffect.CancelSnoozed>().map { it.ringId })
    }

    @Test fun aSnoozeEndingWhileAnotherRingSoundsWaitsItsTurn() {
        val book = RingBook(
            current = ring("b", sourceId = 2),
            others = listOf(ring("c", sourceId = 3), ring("a", sourceId = 1, state = RingState.SNOOZED, snoozeCount = 1, snoozedUntil = t0)),
        )
        val step = reduce(book, RingInput.SnoozeEnded("a"))
        assertEquals("b", step.book.sounding?.ringId)
        assertEquals("FIFO: after the ring that was already waiting", listOf("c", "a"), step.book.others.map { it.ringId })
        assertEquals(RingState.RINGING, step.book.find("a")?.state)
        assertTrue(step.effects<RingEffect.HookUpdated>().isEmpty())
    }

    @Test fun aDuplicateSnoozeEndIsHarmless() {
        val book = arrive(RingBook.EMPTY, ring("a")).book
        val step = reduce(book, RingInput.SnoozeEnded("a"))
        assertTrue(step.accepted)
        assertEquals(book, step.book)
        assertFalse(reduce(book, RingInput.SnoozeEnded("unknown")).accepted)
    }

    // --- missions -------------------------------------------------------------------------------

    @Test fun missionsMustBeCompletedInOrderToDismiss() {
        var book = arrive(RingBook.EMPTY, ring("a", alarm = alarm(missions = missions(2)))).book
        val start = reduce(book, RingInput.StartMission("a"))
        book = start.book
        assertEquals(RingState.MISSION, book.current?.state)
        assertEquals(listOf(true), start.effects<RingEffect.MissionClock>().map { it.running })

        val first = reduce(book, RingInput.CompleteMission("a"))
        book = first.book
        assertEquals(1, book.current?.currentMissionIndex)
        assertEquals(RingState.MISSION, book.current?.state)
        assertTrue(first.effects<RingEffect.EventEnded>().isEmpty())

        val last = reduce(book, RingInput.CompleteMission("a"))
        assertNull(last.book.find("a"))
        assertEquals(RingOutcome.DISMISSED, last.effects<RingEffect.EventEnded>().single().outcome)
        assertEquals(RingOutcome.DISMISSED, last.effects<RingEffect.HookFinished>().single().outcome)
        assertEquals(listOf(false), last.effects<RingEffect.MissionClock>().map { it.running })
    }

    @Test fun startMissionIsIdempotentAndNeedsTheSpeaker() {
        var book = arrive(RingBook.EMPTY, ring("a", sourceId = 1, alarm = alarm(id = 1, missions = missions(1)))).book
        book = arrive(book, ring("b", sourceId = 2, alarm = alarm(id = 2, missions = missions(1)))).book
        book = reduce(book, RingInput.StartMission("a")).book
        val again = reduce(book, RingInput.StartMission("a"))
        assertTrue(again.accepted)
        assertEquals(book, again.book)
        assertFalse("another ring is sounding", reduce(book, RingInput.StartMission("b")).accepted)
    }

    @Test fun missionToSnoozeRunsTheMissionsThenSnoozes() {
        val config = alarm(snooze = SnoozeConfig(mode = SnoozeMode.MISSION_TO_SNOOZE), missions = missions(2))
        var book = arrive(RingBook.EMPTY, ring("a", alarm = config)).book

        val asked = reduce(book, RingInput.Snooze("a"))
        assertFalse("missions are required first", asked.accepted)
        assertEquals(setOf("a"), asked.book.pendingSnooze)
        assertEquals(RingState.RINGING, asked.book.current?.state)
        book = asked.book

        book = reduce(book, RingInput.StartMission("a")).book
        book = reduce(book, RingInput.CompleteMission("a")).book
        val done = reduce(book, RingInput.CompleteMission("a"), now = t0 + 4 * minute)
        val snoozed = done.book.find("a")!!
        assertEquals(RingState.SNOOZED, snoozed.state)
        assertEquals(1, snoozed.snoozeCount)
        assertEquals(t0 + 9 * minute, snoozed.snoozedUntil)
        assertEquals("missions restart after a snooze", 0, snoozed.currentMissionIndex)
        assertTrue(done.book.pendingSnooze.isEmpty())
        assertTrue(done.effects<RingEffect.EventEnded>().isEmpty())
    }

    @Test fun missionToSnoozeWithoutASnoozeRequestStillDismisses() {
        val config = alarm(snooze = SnoozeConfig(mode = SnoozeMode.MISSION_TO_SNOOZE), missions = missions(1))
        var book = arrive(RingBook.EMPTY, ring("a", alarm = config)).book
        book = reduce(book, RingInput.StartMission("a")).book
        val done = reduce(book, RingInput.CompleteMission("a"))
        assertNull(done.book.find("a"))
        assertEquals(RingOutcome.DISMISSED, done.effects<RingEffect.EventEnded>().single().outcome)
    }

    @Test fun abandoningAMissionReturnsToRingingAndForgetsTheSnoozeRequest() {
        val config = alarm(snooze = SnoozeConfig(mode = SnoozeMode.MISSION_TO_SNOOZE), missions = missions(3))
        var book = arrive(RingBook.EMPTY, ring("a", alarm = config)).book
        book = reduce(book, RingInput.Snooze("a")).book
        book = reduce(book, RingInput.StartMission("a")).book
        book = reduce(book, RingInput.CompleteMission("a")).book
        val left = reduce(book, RingInput.AbandonMission("a"))
        assertEquals(RingState.RINGING, left.book.current?.state)
        assertEquals("progress is kept", 1, left.book.current?.currentMissionIndex)
        assertTrue(left.book.pendingSnooze.isEmpty())
        assertFalse("not in a mission any more", reduce(left.book, RingInput.AbandonMission("a")).accepted)
    }

    @Test fun antiCheatResetsMissionProgressWhenTheScreenIsLeft() {
        val config = alarm(missions = missions(3), dismiss = DismissConfig(antiCheat = true))
        var book = arrive(RingBook.EMPTY, ring("a", alarm = config)).book
        book = reduce(book, RingInput.StartMission("a")).book
        book = reduce(book, RingInput.CompleteMission("a")).book
        assertEquals(0, reduce(book, RingInput.AbandonMission("a")).book.current?.currentMissionIndex)
    }

    @Test fun missionIdleReturnsToFullRingingKeepingProgress() {
        var book = arrive(RingBook.EMPTY, ring("a", alarm = alarm(missions = missions(2)))).book
        book = reduce(book, RingInput.StartMission("a")).book
        book = reduce(book, RingInput.CompleteMission("a")).book
        val idle = reduce(book, RingInput.MissionIdle("a"))
        assertEquals(RingState.RINGING, idle.book.current?.state)
        assertEquals(1, idle.book.current?.currentMissionIndex)
        assertEquals(1, idle.effects<RingEffect.HookUpdated>().size)
    }

    @Test fun dismissingASnoozedAlarmWithMissionsRunsTheMissions() {
        val snoozed = ring("a", alarm = alarm(missions = missions(1)), state = RingState.SNOOZED, snoozeCount = 1, snoozedUntil = t0 + 9 * minute)
        val step = reduce(RingBook(current = snoozed), RingInput.StartMission("a"), now = t0 + 2 * minute)
        assertEquals(RingState.MISSION, step.book.sounding?.state)
        assertNull(step.book.sounding?.snoozedUntil)
        assertEquals(listOf("a"), step.effects<RingEffect.CancelRingTriggers>().map { it.ringId })
        assertEquals(listOf("a"), step.effects<RingEffect.CancelSnoozed>().map { it.ringId })
    }

    // --- ring timeout ---------------------------------------------------------------------------

    @Test fun theRingTimeoutSnoozesAndCountsTowardTheMaximum() {
        val config = alarm(snooze = SnoozeConfig(maxCount = 1), dismiss = DismissConfig(autoAction = AutoAction.SNOOZE))
        val book = arrive(RingBook.EMPTY, ring("a", alarm = config)).book
        val first = reduce(book, RingInput.RingTimeout("a"))
        assertEquals(RingState.SNOOZED, first.book.find("a")?.state)
        assertEquals(1, first.book.find("a")?.snoozeCount)

        val again = reduce(first.book, RingInput.SnoozeEnded("a")).book
        val second = reduce(again, RingInput.RingTimeout("a"))
        assertNull("maximum reached: auto-dismissed", second.book.find("a"))
        assertEquals(RingOutcome.AUTO_DISMISSED, second.effects<RingEffect.EventEnded>().single().outcome)
        assertEquals(1, second.effects<RingEffect.NotifyAutoStopped>().size)
    }

    @Test fun autoSnoozeIgnoresTheMissionRequirement() {
        val config = alarm(snooze = SnoozeConfig(mode = SnoozeMode.MISSION_TO_SNOOZE), missions = missions(1))
        val book = arrive(RingBook.EMPTY, ring("a", alarm = config)).book
        assertEquals(RingState.SNOOZED, reduce(book, RingInput.RingTimeout("a")).book.find("a")?.state)
    }

    @Test fun theRingTimeoutCanDismiss() {
        val config = alarm(dismiss = DismissConfig(autoAction = AutoAction.DISMISS), repeat = RepeatRule.Once())
        val book = arrive(RingBook.EMPTY, ring("a", alarm = config)).book
        val step = reduce(book, RingInput.RingTimeout("a"))
        assertNull(step.book.find("a"))
        assertEquals(RingOutcome.AUTO_DISMISSED, step.effects<RingEffect.HookFinished>().single().outcome)
        assertEquals(listOf(1L), step.effects<RingEffect.DisableOneTimeAlarm>().map { it.alarmId })
        assertTrue("auto dismissal is not a user dismissal", step.effects<RingEffect.SpeakBriefing>().isEmpty())
    }

    @Test fun theRingTimeoutOnlyAppliesToTheRingingSoundingRing() {
        var book = arrive(RingBook.EMPTY, ring("a", sourceId = 1, alarm = alarm(id = 1, missions = missions(1)))).book
        book = arrive(book, ring("b", sourceId = 2)).book
        assertFalse("queued", reduce(book, RingInput.RingTimeout("b")).accepted)
        book = reduce(book, RingInput.StartMission("a")).book
        assertFalse("in a mission", reduce(book, RingInput.RingTimeout("a")).accepted)
    }

    // --- dismiss --------------------------------------------------------------------------------

    @Test fun dismissingAOneTimeAlarmDisablesItButNotARepeatingOne() {
        val once = reduce(arrive(RingBook.EMPTY, ring("a", alarm = alarm(repeat = RepeatRule.Once()))).book, RingInput.Dismiss("a", RingOutcome.DISMISSED))
        assertEquals(listOf(1L), once.effects<RingEffect.DisableOneTimeAlarm>().map { it.alarmId })
        val repeating = reduce(arrive(RingBook.EMPTY, ring("b")).book, RingInput.Dismiss("b", RingOutcome.DISMISSED))
        assertTrue(repeating.effects<RingEffect.DisableOneTimeAlarm>().isEmpty())
        val nap = reduce(arrive(RingBook.EMPTY, ring("c", kind = RingKind.NAP, alarm = alarm(repeat = RepeatRule.Once()))).book, RingInput.Dismiss("c", RingOutcome.DISMISSED))
        assertTrue("only saved alarms are disabled", nap.effects<RingEffect.DisableOneTimeAlarm>().isEmpty())
    }

    @Test fun dismissingAMorningAlarmTurnsOffDoNotDisturb() {
        val alarmStep = reduce(arrive(RingBook.EMPTY, ring("a")).book, RingInput.Dismiss("a", RingOutcome.DISMISSED))
        assertTrue(RingEffect.TurnOffDnd in alarmStep.effects)
        val calendar = reduce(arrive(RingBook.EMPTY, ring("c", kind = RingKind.CALENDAR)).book, RingInput.Dismiss("c", RingOutcome.DISMISSED_ON_WATCH))
        assertTrue(RingEffect.TurnOffDnd in calendar.effects)
        val nap = reduce(arrive(RingBook.EMPTY, ring("n", kind = RingKind.NAP)).book, RingInput.Dismiss("n", RingOutcome.DISMISSED))
        assertFalse(RingEffect.TurnOffDnd in nap.effects)
    }

    @Test fun afterDismissBriefingIsRequestedOnlyForUserDismissals() {
        val config = alarm(briefing = BriefingConfig(enabled = true, timing = BriefingTiming.AFTER_DISMISS))
        val user = reduce(arrive(RingBook.EMPTY, ring("a", alarm = config)).book, RingInput.Dismiss("a", RingOutcome.DISMISSED))
        assertEquals(1, user.effects<RingEffect.SpeakBriefing>().size)
        val during = alarm(briefing = BriefingConfig(enabled = true, timing = BriefingTiming.DURING_RING))
        val duringStep = reduce(arrive(RingBook.EMPTY, ring("b", alarm = during)).book, RingInput.Dismiss("b", RingOutcome.DISMISSED))
        assertTrue(duringStep.effects<RingEffect.SpeakBriefing>().isEmpty())
    }

    @Test fun dismissingAnUnknownRingChangesNothing() {
        val book = arrive(RingBook.EMPTY, ring("a")).book
        val step = reduce(book, RingInput.Dismiss("nope", RingOutcome.DISMISSED))
        assertFalse(step.accepted)
        assertEquals(book, step.book)
        assertTrue(step.effects.isEmpty())
    }

    @Test fun dismissCancelsTheRingsTriggersAndSnoozeNotice() {
        val snoozed = ring("a", state = RingState.SNOOZED, snoozeCount = 1, snoozedUntil = t0 + 5 * minute)
        val step = reduce(RingBook(current = snoozed), RingInput.Dismiss("a", RingOutcome.DISMISSED))
        assertEquals(listOf("a"), step.effects<RingEffect.CancelRingTriggers>().map { it.ringId })
        assertEquals(listOf("a"), step.effects<RingEffect.CancelSnoozed>().map { it.ringId })
        assertEquals(1, step.effects<RingEffect.EventEnded>().single().ring.snoozeCount)
        assertNull(step.book.current)
    }

    // --- awake check ----------------------------------------------------------------------------

    @Test fun theAwakeCheckFollowsAUserDismissal() {
        val config = alarm(awake = AwakeCheckConfig(enabled = true, delayMinutes = 5, responseSeconds = 90))
        val book = arrive(RingBook.EMPTY, ring("a", alarm = config)).book
        val now = t0 + 3 * minute
        val dismissed = reduce(book, RingInput.Dismiss("a", RingOutcome.DISMISSED), now)
        val checking = dismissed.book.find("a")!!
        assertEquals(RingState.AWAKE_CHECK, checking.state)
        assertEquals(now + 5 * minute, checking.awakeCheckDueAt)
        assertFalse(checking.awakePromptShowing)
        assertNull("nothing sounds while waiting", dismissed.book.sounding)
        assertEquals(1, dismissed.effects<RingEffect.ScheduleAwakeCheck>().size)
        assertEquals("the dismissal is logged now", RingOutcome.DISMISSED, dismissed.effects<RingEffect.EventEnded>().single().outcome)
        assertTrue(dismissed.effects<RingEffect.HookFinished>().isEmpty())

        val due = reduce(dismissed.book, RingInput.AwakeCheckDue("a"), now + 5 * minute)
        val prompt = due.book.sounding!!
        assertTrue(prompt.awakePromptShowing)
        assertEquals(now + 5 * minute, prompt.startedAt)
        assertEquals(SoundMode.AWAKE_PING, prompt.soundMode)

        val confirmed = reduce(due.book, RingInput.ConfirmAwake("a"))
        assertNull(confirmed.book.find("a"))
        assertEquals(listOf(true), confirmed.effects<RingEffect.EventAwake>().map { it.confirmed })
        assertEquals(1, confirmed.effects<RingEffect.HookFinished>().size)
    }

    @Test fun anUnansweredAwakeCheckRingsTheFullAlarmAgain() {
        val config = alarm(missions = missions(2), awake = AwakeCheckConfig(enabled = true))
        val prompt = ring("a", alarm = config, state = RingState.AWAKE_CHECK, awakeDue = t0, prompt = true).copy(currentMissionIndex = 2)
        val now = t0 + 2 * minute
        val step = reduce(RingBook(current = prompt), RingInput.AwakeTimeout("a"), now)
        val again = step.book.sounding!!
        assertEquals(RingState.RINGING, again.state)
        assertFalse(again.awakePromptShowing)
        assertEquals("missions start over", 0, again.currentMissionIndex)
        assertEquals(now, again.startedAt)
        assertEquals(listOf(false), step.effects<RingEffect.EventAwake>().map { it.confirmed })
    }

    @Test fun autoDismissalAndTimersSkipTheAwakeCheck() {
        val config = alarm(awake = AwakeCheckConfig(enabled = true), dismiss = DismissConfig(autoAction = AutoAction.DISMISS))
        val auto = reduce(arrive(RingBook.EMPTY, ring("a", alarm = config)).book, RingInput.RingTimeout("a"))
        assertNull(auto.book.find("a"))
        val timer = reduce(arrive(RingBook.EMPTY, ring("t", kind = RingKind.TIMER, alarm = config)).book, RingInput.Dismiss("t", RingOutcome.DISMISSED))
        assertNull(timer.book.find("t"))
    }

    @Test fun anAwakePromptDueWhileAnotherRingSoundsWaits() {
        val checking = ring("a", sourceId = 1, state = RingState.AWAKE_CHECK, awakeDue = t0)
        val book = RingBook(current = ring("b", sourceId = 2), others = listOf(checking))
        val due = reduce(book, RingInput.AwakeCheckDue("a"))
        assertEquals("b", due.book.sounding?.ringId)
        assertTrue(due.book.find("a")!!.awakePromptShowing)
        val afterB = reduce(due.book, RingInput.Dismiss("b", RingOutcome.DISMISSED))
        assertEquals("a", afterB.book.sounding?.ringId)
        assertEquals(SoundMode.AWAKE_PING, afterB.book.sounding?.soundMode)
    }

    @Test fun confirmingAwakeEarlyIsAllowed() {
        val checking = ring("a", state = RingState.AWAKE_CHECK, awakeDue = t0 + 5 * minute)
        val step = reduce(RingBook(current = checking), RingInput.ConfirmAwake("a"))
        assertTrue(step.accepted)
        assertNull(step.book.current)
        assertEquals(listOf("a"), step.effects<RingEffect.CancelRingTriggers>().map { it.ringId })
        assertFalse("only for awake checks", reduce(arrive(RingBook.EMPTY, ring("b")).book, RingInput.ConfirmAwake("b")).accepted)
    }

    // --- timers ---------------------------------------------------------------------------------

    @Test fun snoozingATimerAddsOneMinute() {
        val book = arrive(RingBook.EMPTY, ring("t", sourceId = 7, kind = RingKind.TIMER, alarm = alarm(snooze = SnoozeConfig(enabled = false)))).book
        val now = t0 + minute
        val step = reduce(book, RingInput.Snooze("t"), now)
        assertTrue(step.accepted)
        assertNull("the ring ends; the timer runs again", step.book.find("t"))
        assertEquals(RingReducer.TIMER_SNOOZE_MILLIS, 60_000L)
        val restart = step.effects<RingEffect.TimerRestart>().single()
        assertEquals(7L, restart.timerId)
        assertEquals(now + 60_000L, restart.endAt)
        assertEquals(1, step.effects<RingEffect.EventEnded>().single().ring.snoozeCount)
        assertEquals(1, step.effects<RingEffect.HookFinished>().size)
    }

    @Test fun dismissingATimerResetsIt() {
        val book = arrive(RingBook.EMPTY, ring("t", sourceId = 7, kind = RingKind.TIMER, alarm = alarm(repeat = RepeatRule.Once()))).book
        val step = reduce(book, RingInput.Dismiss("t", RingOutcome.DISMISSED))
        assertEquals(listOf(7L), step.effects<RingEffect.TimerReset>().map { it.timerId })
        assertTrue(step.effects<RingEffect.DisableOneTimeAlarm>().isEmpty())
        assertFalse(RingEffect.TurnOffDnd in step.effects)
    }

    // --- cancel ---------------------------------------------------------------------------------

    @Test fun cancellingARingLogsCancelled() {
        val snoozed = ring("a", state = RingState.SNOOZED, snoozeCount = 1, snoozedUntil = t0 + minute)
        val step = reduce(RingBook(current = snoozed), RingInput.Cancel("a"))
        assertNull(step.book.current)
        assertEquals(RingOutcome.CANCELLED, step.effects<RingEffect.EventEnded>().single().outcome)
        assertEquals(RingOutcome.CANCELLED, step.effects<RingEffect.HookFinished>().single().outcome)
    }

    @Test fun cancellingAnAwakeCheckKeepsTheDismissalOutcome() {
        val checking = ring("a", state = RingState.AWAKE_CHECK, awakeDue = t0 + minute)
        val step = reduce(RingBook(current = checking), RingInput.Cancel("a"))
        assertTrue(step.effects<RingEffect.EventEnded>().isEmpty())
        assertNull(step.book.current)
    }

    // --- book invariants ------------------------------------------------------------------------

    @Test fun everyStepReportsAtMostOneHookPerRing() {
        val config = alarm(snooze = SnoozeConfig(mode = SnoozeMode.MISSION_TO_SNOOZE), missions = missions(1))
        var book = arrive(RingBook.EMPTY, ring("a", alarm = config)).book
        book = arrive(book, ring("b", sourceId = 2)).book
        book = reduce(book, RingInput.Snooze("a")).book
        book = reduce(book, RingInput.StartMission("a")).book
        val step = reduce(book, RingInput.CompleteMission("a"))
        val hooks = step.effects.filter { it is RingEffect.HookStarted || it is RingEffect.HookUpdated || it is RingEffect.HookFinished }
        val perRing = hooks.groupBy {
            when (it) {
                is RingEffect.HookStarted -> it.ring.ringId
                is RingEffect.HookUpdated -> it.ring.ringId
                is RingEffect.HookFinished -> it.ring.ringId
                else -> error("unexpected")
            }
        }
        assertTrue(perRing.values.all { it.size == 1 })
        assertEquals(setOf("a", "b"), perRing.keys)
    }

    @Test fun normalizeIsIdempotentAndOrdersParkedRingsByDueTime() {
        val book = RingBook(
            current = ring("p2", sourceId = 2, state = RingState.SNOOZED, snoozedUntil = t0 + 9 * minute),
            others = listOf(
                ring("p1", sourceId = 1, state = RingState.SNOOZED, snoozedUntil = t0 + 3 * minute),
                ring("w", sourceId = 3),
                ring("p3", sourceId = 4, state = RingState.AWAKE_CHECK, awakeDue = t0 + 5 * minute),
            ),
            pendingSnooze = setOf("w", "gone"),
        )
        val normalized = RingBook.normalize(book)
        assertEquals("a ready ring is always current", "w", normalized.current?.ringId)
        assertEquals(listOf("p1", "p3", "p2"), normalized.others.map { it.ringId })
        assertEquals(setOf("w"), normalized.pendingSnooze)
        assertEquals(normalized, RingBook.normalize(normalized))

        val parkedOnly = RingBook.normalize(RingBook(others = book.all.filter { it.ringId != "w" }))
        assertEquals("the parked ring due first is shown", "p1", parkedOnly.current?.ringId)
        assertNull(parkedOnly.sounding)
    }
}
