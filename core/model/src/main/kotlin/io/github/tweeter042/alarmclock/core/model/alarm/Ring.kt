package io.github.tweeter042.alarmclock.core.model.alarm

import kotlinx.serialization.Serializable

/** What produced a ring. */
@Serializable
enum class RingKind {
    ALARM,
    /** Quick nap / "alarm in N minutes". */
    NAP,
    /** Preview / self-test ring. */
    TEST,
    TIMER,
    /** Alarm generated from a CalendarAlarmRule. */
    CALENDAR,
    /** Location (geofence) alarm. */
    LOCATION,
}

@Serializable
enum class RingState {
    RINGING,
    /** User is doing missions (volume may be lowered). */
    MISSION,
    SNOOZED,
    /** Dismissed; waiting for the "Are you awake?" confirmation. */
    AWAKE_CHECK,
    FINISHED,
}

@Serializable
enum class RingOutcome {
    DISMISSED,
    AUTO_DISMISSED,
    DISMISSED_ON_WATCH,
    /** Dismissed from the "upcoming alarm" notification before it rang. */
    DISMISSED_IN_ADVANCE,
    /** Should have rung but did not (device off / app killed); detected afterwards. */
    MISSED,
    /** Cancelled because the alarm was deleted/disabled while ringing or snoozed. */
    CANCELLED,
}

/**
 * The single source of truth for an alarm that is ringing, snoozed or awaiting the awake check.
 * Persisted by the engine (survives process death and reboot).
 */
@Serializable
data class ActiveRing(
    /** Random UUID string. */
    val ringId: String,
    val kind: RingKind,
    /** Alarm id / timer id / location alarm id / calendar rule id, depending on [kind]. */
    val sourceId: Long,
    /** Snapshot of the configuration used for this ring (synthesized for NAP/TIMER/TEST/LOCATION). */
    val alarm: Alarm,
    /** The occurrence this ring belongs to (epoch millis). */
    val occurrenceAt: Long,
    /** When ringing (re)started (epoch millis). */
    val startedAt: Long,
    val state: RingState = RingState.RINGING,
    val snoozeCount: Int = 0,
    val snoozedUntil: Long? = null,
    /** Index into alarm.missions of the mission currently shown. */
    val currentMissionIndex: Int = 0,
    val awakeCheckDueAt: Long? = null,
    /** True while an awake-check prompt is waiting for the user's answer. */
    val awakePromptShowing: Boolean = false,
) {
    val label: String get() = alarm.label
    val hasMissions: Boolean get() = alarm.missions.isNotEmpty()
}

/** The next thing that will ring. */
@Serializable
data class NextAlarm(
    val kind: RingKind,
    val sourceId: Long,
    /** Epoch millis. */
    val triggerAt: Long,
    val label: String,
)

@Serializable
enum class PreAlarmKind {
    /** "Upcoming alarm" notification with a "Dismiss now" action. */
    UPCOMING_NOTICE,
    /** Start of the sunrise light ramp. */
    SUNRISE_START,
    /** Start of the smart-wake window (watch begins monitoring). */
    SMART_WAKE_START,
    /** Weather check that may ring earlier. */
    WEATHER_CHECK,
    /** Prefetch weather/calendar so the ring screen and briefing work offline. */
    PREFETCH,
}

@Serializable
data class PreAlarmEvent(
    val kind: PreAlarmKind,
    val alarmId: Long,
    /** The alarm occurrence this pre-event belongs to (epoch millis). */
    val occurrenceAt: Long,
    /** When this pre-event fires (epoch millis). */
    val triggerAt: Long,
)
