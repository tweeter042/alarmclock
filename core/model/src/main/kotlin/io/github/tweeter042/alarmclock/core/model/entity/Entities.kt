@file:UseSerializers(LocalDateSerializer::class)

package io.github.tweeter042.alarmclock.core.model.entity

import io.github.tweeter042.alarmclock.core.model.alarm.Alarm
import io.github.tweeter042.alarmclock.core.model.alarm.BuiltInTones
import io.github.tweeter042.alarmclock.core.model.alarm.RingKind
import io.github.tweeter042.alarmclock.core.model.alarm.RingOutcome
import io.github.tweeter042.alarmclock.core.model.alarm.SoundConfig
import io.github.tweeter042.alarmclock.core.model.alarm.SoundSource
import io.github.tweeter042.alarmclock.core.model.time.AlarmTime
import io.github.tweeter042.alarmclock.core.model.time.IsoDays
import io.github.tweeter042.alarmclock.core.model.util.LocalDateSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.LocalDate

/** A named set of alarms that can be switched on/off together (e.g. "Workdays", "School break"). */
@Serializable
data class AlarmGroup(
    val id: Long = 0L,
    val name: String,
    val enabled: Boolean = true,
    /** Index into the theme's accent colours. */
    val colorIndex: Int = 0,
    val sortOrder: Int = 0,
)

@Serializable
enum class HolidaySource { BUILT_IN, CALENDAR, CUSTOM }

@Serializable
data class Holiday(
    val id: Long = 0L,
    val date: LocalDate,
    val nameEn: String,
    val nameTh: String = nameEn,
    val source: HolidaySource,
    /** Disabled holidays are ignored by "skip holidays". */
    val enabled: Boolean = true,
)

/** Automatically creates an alarm [minutesBefore] the first calendar event of the day. */
@Serializable
data class CalendarAlarmRule(
    val id: Long = 0L,
    val enabled: Boolean = true,
    val name: String = "",
    /** Empty = all visible calendars. */
    val calendarIds: List<Long> = emptyList(),
    val minutesBefore: Int = 60,
    val days: Set<Int> = IsoDays.ALL,
    /** Never ring earlier than this. */
    val earliest: AlarmTime? = AlarmTime(5, 0),
    /** Ignore events that would need an alarm later than this. */
    val latest: AlarmTime? = AlarmTime(11, 0),
    /** Only events whose title contains this text (case-insensitive). Blank = any. */
    val keyword: String = "",
    val ignoreAllDayEvents: Boolean = true,
    /** Sound, missions etc. for generated alarms (time/repeat/id are ignored). */
    val template: Alarm = Alarm(label = ""),
)

@Serializable
enum class GeofenceTrigger { ENTER, EXIT }

/** Rings when entering/leaving an area, e.g. "wake me near my station". */
@Serializable
data class LocationAlarm(
    val id: Long = 0L,
    val enabled: Boolean = true,
    val label: String = "",
    val placeName: String = "",
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int = 500,
    val trigger: GeofenceTrigger = GeofenceTrigger.ENTER,
    /** Disable after it has rung once. */
    val oneShot: Boolean = true,
    /** Optional active window (e.g. only on the morning commute). Null = always. */
    val activeFrom: AlarmTime? = null,
    val activeTo: AlarmTime? = null,
    /** Sound / vibration / missions template. */
    val template: Alarm = Alarm(),
)

@Serializable
enum class TimerState { IDLE, RUNNING, PAUSED, RINGING }

@Serializable
data class TimerModel(
    val id: Long = 0L,
    val label: String = "",
    val durationMs: Long,
    val state: TimerState = TimerState.IDLE,
    /** Epoch millis when a RUNNING timer ends. */
    val endAt: Long? = null,
    /** Remaining time while PAUSED/IDLE. */
    val remainingMs: Long = durationMs,
    val sound: SoundConfig = SoundConfig(source = SoundSource.BuiltIn(BuiltInTones.TIMER_ID), fadeInSeconds = 0),
    val vibrate: Boolean = true,
    val createdAt: Long = 0L,
)

@Serializable
data class TimerPreset(
    val id: Long = 0L,
    val label: String,
    val durationMs: Long,
    val sortOrder: Int = 0,
)

@Serializable
data class StopwatchState(
    val running: Boolean = false,
    /** Epoch millis when the current running segment started. */
    val startedAt: Long? = null,
    /** Time accumulated in previous segments. */
    val accumulatedMs: Long = 0L,
    /** Total elapsed time at each lap press, in order. */
    val laps: List<Long> = emptyList(),
) {
    fun elapsedAt(now: Long): Long = accumulatedMs + if (running && startedAt != null) (now - startedAt) else 0L
}

@Serializable
data class WorldClock(
    val id: Long = 0L,
    /** java.time ZoneId, e.g. "Asia/Tokyo". */
    val zoneId: String,
    /** Custom display name; blank = derive from zone. */
    val label: String = "",
    val sortOrder: Int = 0,
)

/** One ring's history (used for stats, missed-alarm detection and the snooze statistics). */
@Serializable
data class AlarmEvent(
    val id: Long = 0L,
    val kind: RingKind,
    val sourceId: Long,
    val label: String = "",
    val occurrenceAt: Long,
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val outcome: RingOutcome? = null,
    val snoozeCount: Int = 0,
    /** Total time spent in missions. */
    val missionMillis: Long = 0L,
    val awakeConfirmed: Boolean? = null,
)

@Serializable
enum class SleepSource { APP, HEALTH_CONNECT }

@Serializable
data class SleepLog(
    val id: Long = 0L,
    val bedtimeAt: Long? = null,
    val wakeAt: Long,
    val source: SleepSource = SleepSource.APP,
    val note: String = "",
)

@Serializable
data class MorningRoutineItem(
    val id: Long = 0L,
    val text: String,
    val sortOrder: Int = 0,
    val enabled: Boolean = true,
)

/** A QR code / barcode registered for the BARCODE mission. */
@Serializable
data class RegisteredCode(
    val id: Long = 0L,
    val label: String,
    val value: String,
    /** ML Kit Barcode.FORMAT_* value. */
    val format: Int = 0,
    val createdAt: Long = 0L,
)

/** An object registered for the PHOTO mission. */
@Serializable
data class PhotoTarget(
    val id: Long = 0L,
    val label: String,
    /** App-private file path of the reference photo. */
    val imagePath: String,
    /** ML Kit image labels detected on the reference photo, with confidences. */
    val labels: Map<String, Float> = emptyMap(),
    val createdAt: Long = 0L,
)

/** What the engine registered with AlarmManager (for integrity checks and missed-alarm detection). */
@Serializable
enum class TriggerKind { RING, PRE_ALARM, SNOOZE_END, AWAKE_CHECK, TIMER, NAP, TEST, CALENDAR }

@Serializable
data class ScheduledTrigger(
    /** PendingIntent request code (unique per trigger). */
    val requestCode: Int,
    val kind: TriggerKind,
    val sourceId: Long,
    val triggerAt: Long,
    val occurrenceAt: Long,
    /** Extra payload, e.g. PreAlarmKind name or ringId. */
    val extra: String? = null,
)
