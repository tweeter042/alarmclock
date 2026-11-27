package io.github.tweeter042.alarmclock.shared.wear

import io.github.tweeter042.alarmclock.core.model.alarm.MissionType
import io.github.tweeter042.alarmclock.core.model.alarm.NextAlarm
import io.github.tweeter042.alarmclock.core.model.alarm.RingKind
import io.github.tweeter042.alarmclock.core.model.alarm.RingOutcome
import io.github.tweeter042.alarmclock.core.model.alarm.VibrationPattern
import io.github.tweeter042.alarmclock.core.model.alarm.WatchRingMode
import io.github.tweeter042.alarmclock.core.model.theme.PaletteColors
import io.github.tweeter042.alarmclock.core.model.theme.ThemeId
import io.github.tweeter042.alarmclock.core.model.util.ModelJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Phone ⇄ Galaxy Watch protocol over the Wearable Data Layer.
 * Both apps have the same applicationId and signing key (required by the Data Layer).
 *
 * State lives in DataClient items (delivered even after reconnecting); events are MessageClient
 * messages (best effort). Every payload is UTF-8 JSON encoded with [WearCodec].
 *
 * Watch-side alarm ownership: the PHONE computes occurrences (holidays, groups, skips, rotation)
 * and sends `nextOccurrenceAt` per alarm. The WATCH schedules its own exact alarms from that so it
 * rings even when the phone is out of reach. Rings are correlated by (alarmId, occurrenceAt).
 */
object WearPaths {
    // DataClient items (phone → watch state)
    const val ALARMS = "/alarmclock/alarms"
    const val APPEARANCE = "/alarmclock/appearance"

    // Messages phone → watch
    const val MSG_RING_STARTED = "/alarmclock/msg/ring-started"
    const val MSG_RING_SNOOZED = "/alarmclock/msg/ring-snoozed"
    const val MSG_RING_STOPPED = "/alarmclock/msg/ring-stopped"
    const val MSG_SMART_WAKE_START = "/alarmclock/msg/smart-wake-start"

    // Messages watch → phone
    const val MSG_WATCH_SNOOZE = "/alarmclock/msg/watch-snooze"
    const val MSG_WATCH_DISMISS = "/alarmclock/msg/watch-dismiss"
    /** The watch started ringing an alarm by itself (used by the phone's WATCH_ONLY fallback logic). */
    const val MSG_WATCH_RINGING = "/alarmclock/msg/watch-ringing"
    const val MSG_SMART_WAKE_TRIGGER = "/alarmclock/msg/smart-wake-trigger"
    const val MSG_REQUEST_SYNC = "/alarmclock/msg/request-sync"
    const val MSG_TOGGLE_ALARM = "/alarmclock/msg/toggle-alarm"
    const val MSG_NAP = "/alarmclock/msg/nap"

    /** Capability names declared in res/values/wear.xml of each app. */
    const val CAPABILITY_PHONE = "alarmclock_phone"
    const val CAPABILITY_WATCH = "alarmclock_watch"
}

/** Missions the watch can run itself. Others show "Finish the mission on your phone". */
val WATCH_SUPPORTED_MISSIONS: Set<MissionType> = setOf(MissionType.MATH, MissionType.SHAKE)

@Serializable
data class WearAlarm(
    val id: Long,
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
    val label: String,
    val repeatSummaryEn: String,
    val repeatSummaryTh: String,
    /** Computed on the phone. Null = not scheduled. */
    val nextOccurrenceAt: Long?,
    val watchMode: WatchRingMode,
    val watchSound: Boolean,
    val vibration: VibrationPattern,
    val snoozeAllowed: Boolean,
    val snoozeMinutes: Int,
    val ringDurationMinutes: Int,
    val missions: List<MissionType>,
    val smartWake: Boolean,
    val smartWakeWindowMinutes: Int,
)

@Serializable
data class WearAlarmsPayload(
    val alarms: List<WearAlarm>,
    val next: NextAlarm?,
    val use24h: Boolean,
    /** "en" or "th". */
    val language: String,
    val generatedAt: Long,
)

@Serializable
data class WearAppearancePayload(
    val themeId: ThemeId,
    val paletteId: String,
    val dark: Boolean,
    val colors: PaletteColors,
)

@Serializable
data class WearRingPayload(
    val ringId: String,
    val alarmId: Long,
    val occurrenceAt: Long,
    val kind: RingKind,
    val label: String,
    val startedAt: Long,
    val snoozeAllowed: Boolean,
    val snoozeMinutes: Int,
    val missions: List<MissionType>,
    val vibration: VibrationPattern,
    val watchSound: Boolean,
)

@Serializable
data class WearRingSnoozed(val ringId: String, val alarmId: Long, val occurrenceAt: Long, val until: Long)

@Serializable
data class WearRingStopped(val ringId: String, val alarmId: Long, val occurrenceAt: Long, val outcome: RingOutcome)

/** Watch → phone snooze/dismiss. [ringId] is null when the watch rang on its own. */
@Serializable
data class WearRingAction(val ringId: String?, val alarmId: Long, val occurrenceAt: Long, val at: Long)

@Serializable
data class WearSmartWakeRequest(val alarmId: Long, val occurrenceAt: Long, val windowStartAt: Long)

@Serializable
data class WearSmartWakeTrigger(val alarmId: Long, val occurrenceAt: Long, val detectedAt: Long, val reason: String)

@Serializable
data class WearToggleAlarm(val alarmId: Long, val enabled: Boolean)

@Serializable
data class WearNapRequest(val minutes: Int)

object WearCodec {
    fun <T> encode(serializer: KSerializer<T>, value: T): ByteArray =
        ModelJson.encodeToString(serializer, value).encodeToByteArray()

    fun <T> decode(serializer: KSerializer<T>, bytes: ByteArray): T =
        ModelJson.decodeFromString(serializer, bytes.decodeToString())
}
