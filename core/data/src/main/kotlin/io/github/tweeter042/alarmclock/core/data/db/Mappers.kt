package io.github.tweeter042.alarmclock.core.data.db

import io.github.tweeter042.alarmclock.core.data.DataLog
import io.github.tweeter042.alarmclock.core.model.alarm.ActiveRing
import io.github.tweeter042.alarmclock.core.model.alarm.Alarm
import io.github.tweeter042.alarmclock.core.model.entity.AlarmEvent
import io.github.tweeter042.alarmclock.core.model.entity.AlarmGroup
import io.github.tweeter042.alarmclock.core.model.entity.CalendarAlarmRule
import io.github.tweeter042.alarmclock.core.model.entity.Holiday
import io.github.tweeter042.alarmclock.core.model.entity.LocationAlarm
import io.github.tweeter042.alarmclock.core.model.entity.MorningRoutineItem
import io.github.tweeter042.alarmclock.core.model.entity.PhotoTarget
import io.github.tweeter042.alarmclock.core.model.entity.RegisteredCode
import io.github.tweeter042.alarmclock.core.model.entity.ScheduledTrigger
import io.github.tweeter042.alarmclock.core.model.entity.SleepLog
import io.github.tweeter042.alarmclock.core.model.entity.TimerModel
import io.github.tweeter042.alarmclock.core.model.entity.TimerPreset
import io.github.tweeter042.alarmclock.core.model.entity.WorldClock
import io.github.tweeter042.alarmclock.core.model.util.ModelJson
import kotlinx.serialization.KSerializer

/**
 * Encodes/decodes the `json` column of one table with [ModelJson].
 *
 * Decoding never throws: a row that cannot be read (for example written by a newer app version
 * with an incompatible change) is skipped and logged, so one bad row can never break a whole list
 * or the ring path.
 */
internal class JsonColumn<T>(private val serializer: KSerializer<T>, private val table: String) {
    fun encode(value: T): String = ModelJson.encodeToString(serializer, value)

    fun decode(json: String, rowId: Long): T? = try {
        ModelJson.decodeFromString(serializer, json)
    } catch (e: RuntimeException) {
        // SerializationException, IllegalArgumentException from model init blocks, DateTimeException…
        DataLog.w("Skipping unreadable row $table#$rowId (${DataLog.describe(e)})")
        null
    }
}

internal object Columns {
    val alarm = JsonColumn(Alarm.serializer(), "alarms")
    val group = JsonColumn(AlarmGroup.serializer(), "alarm_groups")
    val holiday = JsonColumn(Holiday.serializer(), "holidays")
    val timer = JsonColumn(TimerModel.serializer(), "timers")
    val timerPreset = JsonColumn(TimerPreset.serializer(), "timer_presets")
    val worldClock = JsonColumn(WorldClock.serializer(), "world_clocks")
    val event = JsonColumn(AlarmEvent.serializer(), "events")
    val sleepLog = JsonColumn(SleepLog.serializer(), "sleep_logs")
    val routineItem = JsonColumn(MorningRoutineItem.serializer(), "routine_items")
    val registeredCode = JsonColumn(RegisteredCode.serializer(), "registered_codes")
    val photoTarget = JsonColumn(PhotoTarget.serializer(), "photo_targets")
    val calendarRule = JsonColumn(CalendarAlarmRule.serializer(), "calendar_rules")
    val locationAlarm = JsonColumn(LocationAlarm.serializer(), "location_alarms")
    val scheduledTrigger = JsonColumn(ScheduledTrigger.serializer(), "schedule_ledger")
    val activeRing = JsonColumn(ActiveRing.serializer(), "ring_state")
}

/** Ids <= 0 mean "not saved yet"; Room auto-generates an id for 0. */
internal fun Long.idOrNew(): Long = if (this > 0L) this else 0L

// --- alarms ----------------------------------------------------------------------------------

internal fun Alarm.toEntity() = AlarmEntity(
    id = id.idOrNew(),
    enabled = enabled,
    groupId = groupId,
    minuteOfDay = time.minuteOfDay,
    updatedAt = updatedAt,
    json = Columns.alarm.encode(this),
)

internal fun AlarmEntity.toModel(): Alarm? = Columns.alarm.decode(json, id)?.copy(id = id)

internal fun AlarmGroup.toEntity() = AlarmGroupEntity(
    id = id.idOrNew(),
    enabled = enabled,
    sortOrder = sortOrder,
    json = Columns.group.encode(this),
)

internal fun AlarmGroupEntity.toModel(): AlarmGroup? = Columns.group.decode(json, id)?.copy(id = id)

// --- holidays ---------------------------------------------------------------------------------

internal fun Holiday.toEntity() = HolidayEntity(
    id = id.idOrNew(),
    date = date.toString(),
    source = source.name,
    enabled = enabled,
    json = Columns.holiday.encode(this),
)

internal fun HolidayEntity.toModel(): Holiday? = Columns.holiday.decode(json, id)?.copy(id = id)

// --- clock tools ------------------------------------------------------------------------------

internal fun TimerModel.toEntity() = TimerEntity(
    id = id.idOrNew(),
    state = state.name,
    createdAt = createdAt,
    json = Columns.timer.encode(this),
)

internal fun TimerEntity.toModel(): TimerModel? = Columns.timer.decode(json, id)?.copy(id = id)

internal fun TimerPreset.toEntity() = TimerPresetEntity(
    id = id.idOrNew(),
    sortOrder = sortOrder,
    json = Columns.timerPreset.encode(this),
)

internal fun TimerPresetEntity.toModel(): TimerPreset? = Columns.timerPreset.decode(json, id)?.copy(id = id)

internal fun WorldClock.toEntity() = WorldClockEntity(
    id = id.idOrNew(),
    sortOrder = sortOrder,
    json = Columns.worldClock.encode(this),
)

internal fun WorldClockEntity.toModel(): WorldClock? = Columns.worldClock.decode(json, id)?.copy(id = id)

// --- history ----------------------------------------------------------------------------------

internal fun AlarmEvent.toEntity() = AlarmEventEntity(
    id = id.idOrNew(),
    occurrenceAt = occurrenceAt,
    sourceId = sourceId,
    kind = kind.name,
    json = Columns.event.encode(this),
)

internal fun AlarmEventEntity.toModel(): AlarmEvent? = Columns.event.decode(json, id)?.copy(id = id)

internal fun SleepLog.toEntity() = SleepLogEntity(
    id = id.idOrNew(),
    wakeAt = wakeAt,
    json = Columns.sleepLog.encode(this),
)

internal fun SleepLogEntity.toModel(): SleepLog? = Columns.sleepLog.decode(json, id)?.copy(id = id)

// --- morning routine & mission assets -----------------------------------------------------------

internal fun MorningRoutineItem.toEntity() = RoutineItemEntity(
    id = id.idOrNew(),
    sortOrder = sortOrder,
    json = Columns.routineItem.encode(this),
)

internal fun RoutineItemEntity.toModel(): MorningRoutineItem? = Columns.routineItem.decode(json, id)?.copy(id = id)

internal fun RegisteredCode.toEntity() = RegisteredCodeEntity(
    id = id.idOrNew(),
    json = Columns.registeredCode.encode(this),
)

internal fun RegisteredCodeEntity.toModel(): RegisteredCode? = Columns.registeredCode.decode(json, id)?.copy(id = id)

internal fun PhotoTarget.toEntity() = PhotoTargetEntity(
    id = id.idOrNew(),
    json = Columns.photoTarget.encode(this),
)

internal fun PhotoTargetEntity.toModel(): PhotoTarget? = Columns.photoTarget.decode(json, id)?.copy(id = id)

// --- calendar & location alarms -----------------------------------------------------------------

internal fun CalendarAlarmRule.toEntity() = CalendarRuleEntity(
    id = id.idOrNew(),
    enabled = enabled,
    json = Columns.calendarRule.encode(this),
)

internal fun CalendarRuleEntity.toModel(): CalendarAlarmRule? = Columns.calendarRule.decode(json, id)?.copy(id = id)

internal fun LocationAlarm.toEntity() = LocationAlarmEntity(
    id = id.idOrNew(),
    enabled = enabled,
    json = Columns.locationAlarm.encode(this),
)

internal fun LocationAlarmEntity.toModel(): LocationAlarm? = Columns.locationAlarm.decode(json, id)?.copy(id = id)

// --- engine stores ------------------------------------------------------------------------------

internal fun ScheduledTrigger.toEntity() = ScheduledTriggerEntity(
    requestCode = requestCode,
    kind = kind.name,
    sourceId = sourceId,
    triggerAt = triggerAt,
    json = Columns.scheduledTrigger.encode(this),
)

internal fun ScheduledTriggerEntity.toModel(): ScheduledTrigger? =
    Columns.scheduledTrigger.decode(json, requestCode.toLong())?.copy(requestCode = requestCode)

internal fun ActiveRing.toEntity(slot: String) = RingStateEntity(
    slot = slot,
    ringId = ringId,
    json = Columns.activeRing.encode(this),
)

internal fun RingStateEntity.toModel(): ActiveRing? = Columns.activeRing.decode(json, id)
