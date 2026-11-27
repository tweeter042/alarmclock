package io.github.tweeter042.alarmclock.core.model.engine

import android.content.Context
import android.content.Intent
import io.github.tweeter042.alarmclock.core.model.alarm.ActiveRing
import io.github.tweeter042.alarmclock.core.model.alarm.Alarm
import io.github.tweeter042.alarmclock.core.model.alarm.NextAlarm
import io.github.tweeter042.alarmclock.core.model.alarm.PreAlarmEvent
import io.github.tweeter042.alarmclock.core.model.alarm.RingKind
import io.github.tweeter042.alarmclock.core.model.alarm.RingOutcome
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * The alarm engine (implemented in core:alarm). UI code never talks to AlarmManager directly.
 *
 * Scheduling is automatic: the engine observes the repositories (alarms, groups, holidays,
 * calendar rules, settings) and reschedules after every change. Call [rescheduleAll] only for
 * explicit "repair" actions.
 */
interface AlarmEngine {
    /** The ring currently ringing / in missions / snoozed / awaiting awake check, if any. */
    val activeRing: StateFlow<ActiveRing?>

    /** Next scheduled ring across all sources (alarms, calendar, naps, snoozes, timers excluded). */
    val nextAlarm: StateFlow<NextAlarm?>

    suspend fun rescheduleAll(reason: String)

    /** Next occurrence (epoch millis) for a saved alarm, honouring holidays, groups and skip. */
    suspend fun nextOccurrenceOf(alarmId: Long): Long?

    /** Skips only the next occurrence of a repeating alarm. */
    suspend fun skipNext(alarmId: Long)
    suspend fun clearSkip(alarmId: Long)

    /** From the "upcoming alarm" notification: don't ring this time (logs DISMISSED_IN_ADVANCE). */
    suspend fun dismissUpcoming(alarmId: Long)

    /** Rings in [minutes] using [template] (or alarm defaults). Returns the trigger time. */
    suspend fun startNap(minutes: Int, template: Alarm? = null): Long

    /** Rings [alarm] (unsaved config allowed) after [delaySeconds] as a TEST ring. */
    suspend fun testRing(alarm: Alarm, delaySeconds: Int = 3)

    /** Returns false if snoozing is not allowed (max reached, disabled, or missions required). */
    suspend fun snooze(ringId: String): Boolean

    suspend fun dismiss(ringId: String, outcome: RingOutcome = RingOutcome.DISMISSED)

    /** User opened the missions: state → MISSION, volume lowered if configured. */
    suspend fun startMission(ringId: String)

    /** Any touch/sensor progress inside a mission (resets the idle timer). Cheap, call freely. */
    fun onMissionInteraction(ringId: String)

    /** Current mission solved. After the last one the ring is dismissed (or snoozed if this was a snooze mission). */
    suspend fun completeMission(ringId: String)

    /** User left the mission screen: back to RINGING at full volume. */
    suspend fun abandonMission(ringId: String)

    /** "Yes, I'm awake" for the awake check. */
    suspend fun confirmAwake(ringId: String)

    suspend fun scheduleTimer(timerId: Long, endAt: Long)
    suspend fun cancelTimer(timerId: Long)

    /** Ring immediately (location alarm entered, watch detected light sleep, …). */
    suspend fun triggerNow(kind: RingKind, sourceId: Long, alarm: Alarm)

    /** Verifies that every expected trigger is registered with AlarmManager; repairs if not. */
    suspend fun integrityReport(repair: Boolean = true): IntegrityReport
}

@Serializable
data class IntegrityReport(
    val expected: Int,
    val registered: Int,
    val missingSourceIds: List<Long>,
    val repaired: Boolean,
    val systemNextAlarmMatches: Boolean,
    val checkedAt: Long,
)

/** Side effects of engine events, implemented by the app (wear sync, SmartThings, widgets, …). */
interface AlarmEngineHooks {
    suspend fun onPreAlarm(event: PreAlarmEvent, alarm: Alarm) {}
    suspend fun onRingStarted(ring: ActiveRing) {}
    suspend fun onRingUpdated(ring: ActiveRing) {}
    suspend fun onRingFinished(ring: ActiveRing, outcome: RingOutcome) {}
    /** After every reschedule. */
    suspend fun onScheduleChanged(next: NextAlarm?, alarms: List<Alarm>) {}
}

/** Intents for screens the engine must open; implemented by the app module. */
interface RingUiLauncher {
    /** Full-screen ring activity (feature:ringing). */
    fun ringScreenIntent(context: Context, ringId: String): Intent
    /** Full-screen sunrise activity (feature:ringing). */
    fun sunriseIntent(context: Context, alarmId: Long, occurrenceAt: Long): Intent
    /** Main activity (alarm list). */
    fun mainIntent(context: Context): Intent
    /** Main activity opened on an alarm's editor. */
    fun alarmEditorIntent(context: Context, alarmId: Long): Intent
}

// ---------------------------------------------------------------------------------------------
// Reliability / Health check (implemented in core:alarm, UI in feature:settings)
// ---------------------------------------------------------------------------------------------

enum class HealthItemId {
    NOTIFICATIONS,
    ALARM_CHANNEL,
    FULL_SCREEN_INTENT,
    EXACT_ALARMS,
    BATTERY_UNRESTRICTED,
    BACKGROUND_RESTRICTION,
    STANDBY_BUCKET,
    APP_HIBERNATION,
    DND_ACCESS,
    ALARM_VOLUME,
    ALARMS_REGISTERED,
    /** Samsung: app is in "Never sleeping apps". Not readable → user confirms. */
    SAMSUNG_NEVER_SLEEPING,
    /** Samsung: "Put unused apps to sleep" is off. Not readable → user confirms. */
    SAMSUNG_UNUSED_APPS_SLEEP,
    /** Samsung Device care "Auto optimization" restart — alarms survive it; user informed. */
    SAMSUNG_AUTO_OPTIMIZATION,
    /** Samsung: app locked in Recents ("Keep open"). User confirms. */
    SAMSUNG_RECENTS_LOCK,
    GUARD_SERVICE,
    WATCH_APP,
    TEST_ALARM,
}

enum class HealthStatus { OK, WARNING, ERROR, MANUAL_UNCONFIRMED, MANUAL_CONFIRMED, NOT_APPLICABLE }

data class HealthItem(
    val id: HealthItemId,
    val status: HealthStatus,
    /** Optional technical detail (not localized), e.g. "bucket=RARE". */
    val detail: String? = null,
    val fixable: Boolean,
    /** Failing a required item can make alarms not ring. */
    val required: Boolean,
)

interface ReliabilityChecker {
    suspend fun check(): List<HealthItem>

    /**
     * Candidate intents that open the right settings page, most specific first
     * (Samsung component intents, then generic Android settings). The caller starts the first one
     * that resolves (or catches ActivityNotFoundException and tries the next).
     */
    fun fixIntents(context: Context, id: HealthItemId): List<Intent>

    /** For Samsung items that cannot be read programmatically. */
    suspend fun setManualConfirmed(id: HealthItemId, confirmed: Boolean)

    fun isSamsungDevice(): Boolean
}
