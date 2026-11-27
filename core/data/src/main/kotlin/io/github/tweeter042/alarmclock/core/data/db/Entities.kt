package io.github.tweeter042.alarmclock.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Room rows. Every table keeps the full model in a `json` TEXT column (encoded with ModelJson)
 * plus only the columns that queries filter or sort on. Nested configuration therefore never needs
 * a schema migration: adding a model field with a default value is enough.
 *
 * Writers always derive the columns from the model they encode (see Mappers.kt), so columns and
 * JSON never disagree. Readers return `model.copy(id = row.id)`, which makes ids correct even for
 * rows that were inserted with id 0.
 */

@Entity(
    tableName = "alarms",
    indices = [Index(value = ["group_id"]), Index(value = ["minute_of_day"])],
)
internal data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val enabled: Boolean,
    @ColumnInfo(name = "group_id") val groupId: Long?,
    @ColumnInfo(name = "minute_of_day") val minuteOfDay: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val json: String,
)

@Entity(tableName = "alarm_groups")
internal data class AlarmGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val enabled: Boolean,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    val json: String,
)

@Entity(
    tableName = "holidays",
    indices = [Index(value = ["date"]), Index(value = ["source"])],
)
internal data class HolidayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** ISO-8601 (yyyy-MM-dd), so lexicographic order is chronological order. */
    val date: String,
    /** [io.github.tweeter042.alarmclock.core.model.entity.HolidaySource] name. */
    val source: String,
    val enabled: Boolean,
    val json: String,
)

@Entity(tableName = "timers")
internal data class TimerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** [io.github.tweeter042.alarmclock.core.model.entity.TimerState] name. */
    val state: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val json: String,
)

@Entity(tableName = "timer_presets")
internal data class TimerPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    val json: String,
)

@Entity(tableName = "world_clocks")
internal data class WorldClockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    val json: String,
)

@Entity(
    tableName = "events",
    indices = [Index(value = ["occurrence_at"])],
)
internal data class AlarmEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "occurrence_at") val occurrenceAt: Long,
    @ColumnInfo(name = "source_id") val sourceId: Long,
    /** [io.github.tweeter042.alarmclock.core.model.alarm.RingKind] name. */
    val kind: String,
    val json: String,
)

@Entity(
    tableName = "sleep_logs",
    indices = [Index(value = ["wake_at"])],
)
internal data class SleepLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "wake_at") val wakeAt: Long,
    val json: String,
)

@Entity(tableName = "routine_items")
internal data class RoutineItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    val json: String,
)

@Entity(tableName = "registered_codes")
internal data class RegisteredCodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val json: String,
)

@Entity(tableName = "photo_targets")
internal data class PhotoTargetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val json: String,
)

@Entity(tableName = "calendar_rules")
internal data class CalendarRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val enabled: Boolean,
    val json: String,
)

@Entity(tableName = "location_alarms")
internal data class LocationAlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val enabled: Boolean,
    val json: String,
)

/** One trigger registered with AlarmManager, keyed by its PendingIntent request code. */
@Entity(
    tableName = "schedule_ledger",
    indices = [Index(value = ["trigger_at"])],
)
internal data class ScheduledTriggerEntity(
    @PrimaryKey @ColumnInfo(name = "request_code") val requestCode: Int,
    /** [io.github.tweeter042.alarmclock.core.model.entity.TriggerKind] name. */
    val kind: String,
    @ColumnInfo(name = "source_id") val sourceId: Long,
    @ColumnInfo(name = "trigger_at") val triggerAt: Long,
    val json: String,
)

/**
 * The persisted ring state: at most one row in the [SLOT_ACTIVE] slot plus the FIFO queue of rings
 * waiting behind it ([SLOT_QUEUED] rows, ordered by the AUTOINCREMENT [id], which SQLite never
 * reuses, so the order is strictly first-in first-out and survives process death).
 */
@Entity(
    tableName = "ring_state",
    indices = [Index(value = ["slot"])],
)
internal data class RingStateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val slot: String,
    @ColumnInfo(name = "ring_id") val ringId: String,
    val json: String,
) {
    companion object {
        const val SLOT_ACTIVE = "active"
        const val SLOT_QUEUED = "queued"
    }
}
