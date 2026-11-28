package io.github.tweeter042.alarmclock.core.alarm.schedule

import io.github.tweeter042.alarmclock.core.model.alarm.Alarm
import io.github.tweeter042.alarmclock.core.model.alarm.PreAlarmKind
import io.github.tweeter042.alarmclock.core.model.alarm.RingKind
import io.github.tweeter042.alarmclock.core.model.entity.ScheduledTrigger
import io.github.tweeter042.alarmclock.core.model.entity.TriggerKind
import io.github.tweeter042.alarmclock.core.model.util.ModelJson
import kotlinx.serialization.Serializable

/**
 * The engine's payload in [ScheduledTrigger.extra], stored as JSON ([ModelJson]).
 *
 * | Trigger kind | Fields used |
 * |---|---|
 * | PRE_ALARM | [preAlarm]; or [purpose] = [PURPOSE_MIDNIGHT] for the local-midnight recompute |
 * | SNOOZE_END | [ringId], [ringKind]; [purpose] = [PURPOSE_KICK] for the "start the ring service now" kick |
 * | AWAKE_CHECK | [ringId], [ringKind] |
 * | NAP, TEST | [alarm]: the configuration to ring with (unsaved alarms allowed) |
 * | CALENDAR | [title]: title of the calendar event that generated the alarm |
 * | RING, TIMER | nothing |
 */
@Serializable
internal data class TriggerExtra(
    val ringId: String? = null,
    val ringKind: RingKind? = null,
    val preAlarm: PreAlarmKind? = null,
    val alarm: Alarm? = null,
    val title: String? = null,
    val purpose: String? = null,
) {
    fun encode(): String = ModelJson.encodeToString(serializer(), this)

    companion object {
        const val PURPOSE_MIDNIGHT = "midnight"
        const val PURPOSE_KICK = "kick"

        /** Tolerant decoding: an unreadable extra is treated as absent. */
        fun decode(json: String?): TriggerExtra? =
            if (json.isNullOrBlank()) null else runCatching { ModelJson.decodeFromString(serializer(), json) }.getOrNull()
    }
}

internal val ScheduledTrigger.extraPayload: TriggerExtra? get() = TriggerExtra.decode(extra)

/** Triggers that ring (registered with setAlarmClock) as opposed to pre-alarm events. */
internal val TriggerKind.isRingType: Boolean get() = this != TriggerKind.PRE_ALARM
