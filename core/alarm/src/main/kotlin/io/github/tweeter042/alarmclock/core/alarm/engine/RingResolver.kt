package io.github.tweeter042.alarmclock.core.alarm.engine

import io.github.tweeter042.alarmclock.core.alarm.EngineLog
import io.github.tweeter042.alarmclock.core.alarm.internal.EngineEnv
import io.github.tweeter042.alarmclock.core.alarm.internal.Guarded
import io.github.tweeter042.alarmclock.core.alarm.internal.guarded
import io.github.tweeter042.alarmclock.core.alarm.internal.guardedOrNull
import io.github.tweeter042.alarmclock.core.alarm.schedule.OccurrenceKey
import io.github.tweeter042.alarmclock.core.alarm.schedule.TriggerPayload
import io.github.tweeter042.alarmclock.core.alarm.schedule.TriggerPlanner
import io.github.tweeter042.alarmclock.core.alarm.schedule.extraPayload
import io.github.tweeter042.alarmclock.core.model.alarm.Alarm
import io.github.tweeter042.alarmclock.core.model.alarm.AutoAction
import io.github.tweeter042.alarmclock.core.model.alarm.AwakeCheckConfig
import io.github.tweeter042.alarmclock.core.model.alarm.BriefingConfig
import io.github.tweeter042.alarmclock.core.model.alarm.BuiltInTones
import io.github.tweeter042.alarmclock.core.model.alarm.RepeatRule
import io.github.tweeter042.alarmclock.core.model.alarm.RingKind
import io.github.tweeter042.alarmclock.core.model.alarm.SmartWakeConfig
import io.github.tweeter042.alarmclock.core.model.alarm.SnoozeConfig
import io.github.tweeter042.alarmclock.core.model.alarm.SoundConfig
import io.github.tweeter042.alarmclock.core.model.alarm.SoundSource
import io.github.tweeter042.alarmclock.core.model.alarm.SunriseConfig
import io.github.tweeter042.alarmclock.core.model.alarm.VibrationConfig
import io.github.tweeter042.alarmclock.core.model.alarm.WeatherAdjustConfig
import io.github.tweeter042.alarmclock.core.model.entity.AlarmEvent
import io.github.tweeter042.alarmclock.core.model.entity.TimerModel
import io.github.tweeter042.alarmclock.core.model.entity.TimerState
import io.github.tweeter042.alarmclock.core.model.entity.TriggerKind

/** What a fired trigger turns into. */
internal sealed interface Resolution {
    data class Ring(val alarm: Alarm) : Resolution
    data class Skip(val reason: String) : Resolution
}

/**
 * Decides whether a fired trigger rings, and with which configuration.
 *
 * Reliability rule: a trigger is only skipped on positive evidence (alarm deleted or disabled,
 * occurrence skipped or already finished, timer stopped). If the data cannot be read in time, the
 * alarm rings with a fallback configuration instead.
 */
internal class RingResolver(private val env: EngineEnv) {
    private val log = EngineLog.ring

    suspend fun resolve(payload: TriggerPayload): Resolution {
        val key = OccurrenceKey(payload.ringKind, payload.sourceId, payload.occurrenceAt)
        if (payload.kind != TriggerKind.TIMER && finishedEvent(key) != null) return Resolution.Skip("occurrence already finished")
        return when (payload.kind) {
            TriggerKind.RING -> resolveAlarm(payload)
            TriggerKind.CALENDAR -> resolveCalendar(payload)
            TriggerKind.NAP, TriggerKind.TEST -> Resolution.Ring(snapshot(payload))
            TriggerKind.TIMER -> resolveTimer(payload)
            TriggerKind.PRE_ALARM, TriggerKind.SNOOZE_END, TriggerKind.AWAKE_CHECK -> Resolution.Skip("not a new ring")
        }
    }

    private suspend fun resolveAlarm(payload: TriggerPayload): Resolution {
        val alarm = when (val loaded = guarded("Loading alarm", LOAD_TIMEOUT_MILLIS, log) { env.data.alarms.getAlarm(payload.sourceId) }) {
            is Guarded.Failed -> return Resolution.Ring(fallbackAlarm(payload))
            is Guarded.Ok -> loaded.value ?: return Resolution.Skip("alarm deleted")
        }
        if (!alarm.enabled) return Resolution.Skip("alarm disabled")
        val groups = guardedOrNull("Loading groups", LOAD_TIMEOUT_MILLIS, log) { env.data.alarms.getGroups() }
        if (groups != null && !TriggerPlanner.isGroupEnabled(alarm, groups)) return Resolution.Skip("group disabled")
        if (alarm.skippedOccurrenceAt == payload.occurrenceAt) return Resolution.Skip("occurrence skipped")
        return Resolution.Ring(alarm)
    }

    private suspend fun resolveCalendar(payload: TriggerPayload): Resolution {
        val rule = when (val loaded = guarded("Loading calendar rule", LOAD_TIMEOUT_MILLIS, log) { env.data.calendarRules.get(payload.sourceId) }) {
            is Guarded.Failed -> return Resolution.Ring(fallbackAlarm(payload))
            is Guarded.Ok -> loaded.value ?: return Resolution.Skip("calendar rule deleted")
        }
        if (!rule.enabled) return Resolution.Skip("calendar rule disabled")
        val title = payload.extra?.title.orEmpty()
        return Resolution.Ring(
            rule.template.copy(
                id = 0L,
                enabled = true,
                label = ScheduleCoordinator.calendarLabel(rule, title),
                repeat = RepeatRule.Once(),
                skippedOccurrenceAt = null,
            ),
        )
    }

    private suspend fun resolveTimer(payload: TriggerPayload): Resolution {
        val timer = when (val loaded = guarded("Loading timer", LOAD_TIMEOUT_MILLIS, log) { env.data.timers.getTimer(payload.sourceId) }) {
            is Guarded.Failed -> return Resolution.Ring(timerAlarm(null, payload, env.settingsSnapshot.value?.alarmDefaults ?: Alarm()))
            is Guarded.Ok -> loaded.value ?: return Resolution.Skip("timer deleted")
        }
        when (timer.state) {
            TimerState.IDLE, TimerState.PAUSED -> return Resolution.Skip("timer not running")
            TimerState.RUNNING -> {
                val endAt = timer.endAt
                if (endAt != null && endAt != payload.triggerAt && endAt > env.clock.now() + TIMER_EXTENDED_MARGIN_MILLIS) {
                    return Resolution.Skip("timer was extended")
                }
            }
            TimerState.RINGING -> Unit
        }
        return Resolution.Ring(timerAlarm(timer, payload, env.settings().alarmDefaults))
    }

    /** Configuration of a nap/test ring: the snapshot stored with the trigger, else the alarm defaults. */
    private suspend fun snapshot(payload: TriggerPayload): Alarm {
        payload.extra?.alarm?.let { return it }
        val fromLedger = guardedOrNull("Loading trigger", LOAD_TIMEOUT_MILLIS, log) { env.data.scheduleLedger.get(payload.requestCode) }
            ?.extraPayload?.alarm
        return fromLedger ?: env.settings().alarmDefaults.copy(id = 0L, label = payload.label.orEmpty(), repeat = RepeatRule.Once())
    }

    /** The configuration to ring with when a ring has to be re-created (ring state lost). */
    suspend fun configFor(kind: RingKind, sourceId: Long, payload: TriggerPayload): Alarm = when (kind) {
        RingKind.ALARM -> guardedOrNull("Loading alarm", LOAD_TIMEOUT_MILLIS, log) { env.data.alarms.getAlarm(sourceId) } ?: fallbackAlarm(payload)
        RingKind.CALENDAR -> (resolveCalendar(payload) as? Resolution.Ring)?.alarm ?: fallbackAlarm(payload)
        RingKind.TIMER -> timerAlarm(
            guardedOrNull("Loading timer", LOAD_TIMEOUT_MILLIS, log) { env.data.timers.getTimer(sourceId) },
            payload,
            env.settings().alarmDefaults,
        )
        else -> fallbackAlarm(payload)
    }

    /** The newest event for [key] that has an outcome, or null (also when events cannot be read). */
    suspend fun finishedEvent(key: OccurrenceKey): AlarmEvent? = event(key)?.takeIf { it.outcome != null }

    suspend fun event(key: OccurrenceKey): AlarmEvent? =
        guardedOrNull("Reading events", LOAD_TIMEOUT_MILLIS, log) {
            env.data.events.between(key.occurrenceAt, key.occurrenceAt + 1)
                .filter { it.kind == key.kind && it.sourceId == key.sourceId }
                .maxByOrNull { it.id }
        }

    companion object {
        const val LOAD_TIMEOUT_MILLIS = 2_500L
        private const val TIMER_EXTENDED_MARGIN_MILLIS = 2_000L

        /**
         * Rings with the default sound when the real configuration cannot be loaded. Never silent.
         * (The one-time "disable after dismiss" re-reads the stored alarm, so a repeating alarm
         * that rang with this fallback stays enabled.)
         */
        fun fallbackAlarm(payload: TriggerPayload): Alarm = Alarm(
            id = if (payload.kind == TriggerKind.RING) payload.sourceId else 0L,
            label = payload.label.orEmpty(),
            repeat = RepeatRule.Once(),
        )

        /** A timer ring: the timer's sound, "+1 minute" snooze (unlimited), auto-stop after the default ring duration. */
        fun timerAlarm(timer: TimerModel?, payload: TriggerPayload, defaults: Alarm): Alarm = Alarm(
            id = timer?.id ?: payload.sourceId,
            label = timer?.label ?: payload.label.orEmpty(),
            repeat = RepeatRule.Once(),
            sound = timer?.sound ?: SoundConfig(source = SoundSource.BuiltIn(BuiltInTones.TIMER_ID), fadeInSeconds = 0),
            vibration = VibrationConfig(enabled = timer?.vibrate ?: true),
            snooze = SnoozeConfig(enabled = true, maxCount = 0),
            dismiss = defaults.dismiss.copy(autoAction = AutoAction.DISMISS, flipToSnooze = false, slideToDismiss = false, antiCheat = false),
            missions = emptyList(),
            awakeCheck = AwakeCheckConfig(enabled = false),
            smartWake = SmartWakeConfig(enabled = false),
            sunrise = SunriseConfig(enabled = false),
            briefing = BriefingConfig(enabled = false),
            weatherAdjust = WeatherAdjustConfig(enabled = false),
            watch = defaults.watch,
            appearance = defaults.appearance,
        )
    }
}
