package io.github.tweeter042.alarmclock.core.alarm.engine

import io.github.tweeter042.alarmclock.core.alarm.EngineLog
import io.github.tweeter042.alarmclock.core.alarm.internal.EngineEnv
import io.github.tweeter042.alarmclock.core.alarm.internal.guardedOrNull
import io.github.tweeter042.alarmclock.core.alarm.schedule.TriggerExtra
import io.github.tweeter042.alarmclock.core.alarm.schedule.TriggerPayload
import io.github.tweeter042.alarmclock.core.model.alarm.Alarm
import io.github.tweeter042.alarmclock.core.model.alarm.PreAlarmEvent
import io.github.tweeter042.alarmclock.core.model.alarm.PreAlarmKind
import io.github.tweeter042.alarmclock.core.model.alarm.RingKind
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Pre-alarm events (setExactAndAllowWhileIdle), handled inside the trigger broadcast's goAsync
 * window, each step with its own timeout:
 * - UPCOMING_NOTICE: "Upcoming alarm 06:30 · label" with "Dismiss now".
 * - SUNRISE_START: full-screen sunrise screen (if the alarm uses the screen); lights via the hook.
 * - SMART_WAKE_START: the hook starts the watch's light-sleep monitoring (the watch may call triggerNow).
 * - WEATHER_CHECK: if the forecast for the alarm time matches a configured condition, ring now.
 * - PREFETCH: refresh weather so the ring screen and briefing work offline.
 * - the local-midnight recompute (holidays, rotations, calendar alarms).
 * Every event is also passed to AlarmEngineHooks.onPreAlarm. Stale events (the alarm changed since)
 * are ignored and trigger a reschedule.
 */
internal class PreAlarmHandler(
    private val env: EngineEnv,
    private val schedule: ScheduleCoordinator,
    private val rings: RingCoordinator,
) {
    private val log = EngineLog.schedule

    suspend fun handle(payload: TriggerPayload) {
        if (payload.extra?.purpose == TriggerExtra.PURPOSE_MIDNIGHT) {
            schedule.rescheduleAll("local midnight")
            return
        }
        val kind = payload.extra?.preAlarm ?: return
        val alarm = guardedOrNull("Loading alarm", LOAD_TIMEOUT_MILLIS, log) { env.data.alarms.getAlarm(payload.sourceId) } ?: return
        val occurrence = schedule.upcomingOccurrence(alarm)
        if (occurrence != payload.occurrenceAt) {
            log.i("Stale $kind for alarm #${alarm.id}; rescheduling")
            schedule.request("stale pre-alarm")
            return
        }
        val event = PreAlarmEvent(kind = kind, alarmId = alarm.id, occurrenceAt = occurrence, triggerAt = payload.triggerAt)
        val settings = env.settings()
        coroutineScope {
            launch { env.hookNow("onPreAlarm") { onPreAlarm(event, alarm) } }
            when (kind) {
                PreAlarmKind.UPCOMING_NOTICE -> env.notifier.showUpcoming(alarm, occurrence, settings)
                PreAlarmKind.SUNRISE_START ->
                    if (alarm.sunrise.enabled && alarm.sunrise.useScreen) env.notifier.showSunrise(alarm, occurrence, settings)
                PreAlarmKind.SMART_WAKE_START -> Unit
                PreAlarmKind.WEATHER_CHECK -> weatherCheck(alarm, occurrence)
                PreAlarmKind.PREFETCH -> if (settings.weather.enabled) {
                    env.deps.weather?.let { weather -> guardedOrNull("Weather prefetch", WEATHER_TIMEOUT_MILLIS, log) { weather.refresh() } }
                }
            }
        }
    }

    private suspend fun weatherCheck(alarm: Alarm, occurrence: Long) {
        val weather = env.deps.weather ?: return
        val condition = guardedOrNull("Weather check", WEATHER_TIMEOUT_MILLIS, log) { weather.conditionAt(occurrence) }?.getOrNull() ?: return
        if (condition !in alarm.weatherAdjust.conditions) return
        log.i("Weather matches for alarm #${alarm.id}: ringing ${alarm.weatherAdjust.minutesEarlier} min early")
        if (rings.ringNow(RingKind.ALARM, alarm.id, alarm, occurrence)) schedule.request("weather ring")
    }

    private companion object {
        const val LOAD_TIMEOUT_MILLIS = 2_500L
        const val WEATHER_TIMEOUT_MILLIS = 8_000L
    }
}
