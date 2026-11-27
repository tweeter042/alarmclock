package io.github.tweeter042.alarmclock.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/*
 * DAOs. Conventions:
 * - `upsert` uses REPLACE (insert, or overwrite the row with the same primary key) and always
 *   returns the row id.
 * - `insert` uses ABORT and is used by backup restores, where a conflict means corrupt input.
 * - Lists are always returned in a deterministic order (the order the UI shows).
 */

@Dao
internal interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY minute_of_day, id")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE id = :id")
    fun observe(id: Long): Flow<AlarmEntity?>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun get(id: Long): AlarmEntity?

    @Query("SELECT * FROM alarms ORDER BY minute_of_day, id")
    suspend fun getAll(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE group_id = :groupId ORDER BY id")
    suspend fun getInGroup(groupId: Long): List<AlarmEntity>

    @Query("UPDATE alarms SET group_id = NULL WHERE id = :id")
    suspend fun clearGroupColumn(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AlarmEntity): Long

    @Insert
    suspend fun insert(entity: AlarmEntity): Long

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("DELETE FROM alarms")
    suspend fun deleteAll()
}

@Dao
internal interface AlarmGroupDao {
    @Query("SELECT * FROM alarm_groups ORDER BY sort_order, id")
    fun observeAll(): Flow<List<AlarmGroupEntity>>

    @Query("SELECT * FROM alarm_groups ORDER BY sort_order, id")
    suspend fun getAll(): List<AlarmGroupEntity>

    @Query("SELECT * FROM alarm_groups WHERE id = :id")
    suspend fun get(id: Long): AlarmGroupEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM alarm_groups WHERE id = :id)")
    suspend fun exists(id: Long): Boolean

    @Query("SELECT MAX(sort_order) FROM alarm_groups")
    suspend fun maxSortOrder(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AlarmGroupEntity): Long

    @Insert
    suspend fun insert(entity: AlarmGroupEntity): Long

    @Query("DELETE FROM alarm_groups WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("DELETE FROM alarm_groups")
    suspend fun deleteAll()
}

@Dao
internal interface HolidayDao {
    @Query("SELECT * FROM holidays ORDER BY date, id")
    fun observeAll(): Flow<List<HolidayEntity>>

    /** [from] and [to] are ISO dates; both ends inclusive. */
    @Query("SELECT * FROM holidays WHERE date >= :from AND date <= :to ORDER BY date, id")
    suspend fun between(from: String, to: String): List<HolidayEntity>

    @Query("SELECT * FROM holidays WHERE source IN (:sources) ORDER BY date, id")
    suspend fun bySources(sources: List<String>): List<HolidayEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM holidays WHERE id = :id)")
    suspend fun exists(id: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HolidayEntity): Long

    @Insert
    suspend fun insert(entity: HolidayEntity): Long

    @Query("DELETE FROM holidays WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("DELETE FROM holidays WHERE source IN (:sources)")
    suspend fun deleteSources(sources: List<String>): Int
}

@Dao
internal interface TimerDao {
    @Query("SELECT * FROM timers ORDER BY created_at, id")
    fun observeAll(): Flow<List<TimerEntity>>

    @Query("SELECT * FROM timers ORDER BY created_at, id")
    suspend fun getAll(): List<TimerEntity>

    @Query("SELECT * FROM timers WHERE id = :id")
    suspend fun get(id: Long): TimerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TimerEntity): Long

    @Insert
    suspend fun insert(entity: TimerEntity): Long

    @Query("DELETE FROM timers WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("DELETE FROM timers")
    suspend fun deleteAll()
}

@Dao
internal interface TimerPresetDao {
    @Query("SELECT * FROM timer_presets ORDER BY sort_order, id")
    fun observeAll(): Flow<List<TimerPresetEntity>>

    @Query("SELECT * FROM timer_presets ORDER BY sort_order, id")
    suspend fun getAll(): List<TimerPresetEntity>

    @Query("SELECT MAX(sort_order) FROM timer_presets")
    suspend fun maxSortOrder(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TimerPresetEntity): Long

    @Insert
    suspend fun insert(entity: TimerPresetEntity): Long

    @Query("DELETE FROM timer_presets WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("DELETE FROM timer_presets")
    suspend fun deleteAll()
}

@Dao
internal interface WorldClockDao {
    @Query("SELECT * FROM world_clocks ORDER BY sort_order, id")
    fun observeAll(): Flow<List<WorldClockEntity>>

    @Query("SELECT * FROM world_clocks ORDER BY sort_order, id")
    suspend fun getAll(): List<WorldClockEntity>

    @Query("SELECT MAX(sort_order) FROM world_clocks")
    suspend fun maxSortOrder(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WorldClockEntity): Long

    @Insert
    suspend fun insert(entity: WorldClockEntity): Long

    @Query("DELETE FROM world_clocks WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("DELETE FROM world_clocks")
    suspend fun deleteAll()
}

@Dao
internal interface AlarmEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AlarmEventEntity): Long

    @Update
    suspend fun update(entity: AlarmEventEntity): Int

    /** Half-open range: from <= occurrence_at < to. */
    @Query("SELECT * FROM events WHERE occurrence_at >= :from AND occurrence_at < :to ORDER BY occurrence_at, id")
    fun observeBetween(from: Long, to: Long): Flow<List<AlarmEventEntity>>

    @Query("SELECT * FROM events WHERE occurrence_at >= :from AND occurrence_at < :to ORDER BY occurrence_at, id")
    suspend fun between(from: Long, to: Long): List<AlarmEventEntity>

    @Query("DELETE FROM events WHERE occurrence_at < :before")
    suspend fun deleteBefore(before: Long): Int
}

@Dao
internal interface SleepLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SleepLogEntity): Long

    /** Half-open range: from <= wake_at < to. */
    @Query("SELECT * FROM sleep_logs WHERE wake_at >= :from AND wake_at < :to ORDER BY wake_at, id")
    fun observeBetween(from: Long, to: Long): Flow<List<SleepLogEntity>>

    @Query("DELETE FROM sleep_logs WHERE wake_at < :before")
    suspend fun deleteBefore(before: Long): Int
}

@Dao
internal interface RoutineItemDao {
    @Query("SELECT * FROM routine_items ORDER BY sort_order, id")
    fun observeAll(): Flow<List<RoutineItemEntity>>

    @Query("SELECT * FROM routine_items ORDER BY sort_order, id")
    suspend fun getAll(): List<RoutineItemEntity>

    @Query("SELECT MAX(sort_order) FROM routine_items")
    suspend fun maxSortOrder(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RoutineItemEntity): Long

    @Insert
    suspend fun insert(entity: RoutineItemEntity): Long

    @Query("DELETE FROM routine_items WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("DELETE FROM routine_items")
    suspend fun deleteAll()
}

@Dao
internal interface RegisteredCodeDao {
    @Query("SELECT * FROM registered_codes ORDER BY id")
    fun observeAll(): Flow<List<RegisteredCodeEntity>>

    @Query("SELECT * FROM registered_codes ORDER BY id")
    suspend fun getAll(): List<RegisteredCodeEntity>

    @Query("SELECT * FROM registered_codes WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<RegisteredCodeEntity>

    @Query("SELECT * FROM registered_codes WHERE id = :id")
    suspend fun get(id: Long): RegisteredCodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RegisteredCodeEntity): Long

    @Insert
    suspend fun insert(entity: RegisteredCodeEntity): Long

    @Query("DELETE FROM registered_codes WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("DELETE FROM registered_codes")
    suspend fun deleteAll()
}

@Dao
internal interface PhotoTargetDao {
    @Query("SELECT * FROM photo_targets ORDER BY id")
    fun observeAll(): Flow<List<PhotoTargetEntity>>

    @Query("SELECT * FROM photo_targets ORDER BY id")
    suspend fun getAll(): List<PhotoTargetEntity>

    @Query("SELECT * FROM photo_targets WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<PhotoTargetEntity>

    @Query("SELECT * FROM photo_targets WHERE id = :id")
    suspend fun get(id: Long): PhotoTargetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PhotoTargetEntity): Long

    @Insert
    suspend fun insert(entity: PhotoTargetEntity): Long

    @Query("DELETE FROM photo_targets WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("DELETE FROM photo_targets")
    suspend fun deleteAll()
}

@Dao
internal interface CalendarRuleDao {
    @Query("SELECT * FROM calendar_rules ORDER BY id")
    fun observeAll(): Flow<List<CalendarRuleEntity>>

    @Query("SELECT * FROM calendar_rules ORDER BY id")
    suspend fun getAll(): List<CalendarRuleEntity>

    @Query("SELECT * FROM calendar_rules WHERE id = :id")
    suspend fun get(id: Long): CalendarRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CalendarRuleEntity): Long

    @Insert
    suspend fun insert(entity: CalendarRuleEntity): Long

    @Query("DELETE FROM calendar_rules WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("DELETE FROM calendar_rules")
    suspend fun deleteAll()
}

@Dao
internal interface LocationAlarmDao {
    @Query("SELECT * FROM location_alarms ORDER BY id")
    fun observeAll(): Flow<List<LocationAlarmEntity>>

    @Query("SELECT * FROM location_alarms ORDER BY id")
    suspend fun getAll(): List<LocationAlarmEntity>

    @Query("SELECT * FROM location_alarms WHERE id = :id")
    suspend fun get(id: Long): LocationAlarmEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocationAlarmEntity): Long

    @Insert
    suspend fun insert(entity: LocationAlarmEntity): Long

    @Query("DELETE FROM location_alarms WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("DELETE FROM location_alarms")
    suspend fun deleteAll()
}

@Dao
internal interface ScheduleLedgerDao {
    @Query("SELECT * FROM schedule_ledger ORDER BY trigger_at, request_code")
    suspend fun getAll(): List<ScheduledTriggerEntity>

    @Query("SELECT * FROM schedule_ledger WHERE request_code = :requestCode")
    suspend fun get(requestCode: Int): ScheduledTriggerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: ScheduledTriggerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAll(entities: List<ScheduledTriggerEntity>)

    @Query("DELETE FROM schedule_ledger WHERE request_code = :requestCode")
    suspend fun remove(requestCode: Int): Int

    @Query("DELETE FROM schedule_ledger")
    suspend fun clear()
}

@Dao
internal interface RingStateDao {
    @Query("SELECT * FROM ring_state WHERE slot = '${RingStateEntity.SLOT_ACTIVE}' ORDER BY id DESC LIMIT 1")
    fun observeActive(): Flow<RingStateEntity?>

    @Query("SELECT * FROM ring_state WHERE slot = '${RingStateEntity.SLOT_ACTIVE}' ORDER BY id DESC LIMIT 1")
    suspend fun getActive(): RingStateEntity?

    @Query("DELETE FROM ring_state WHERE slot = '${RingStateEntity.SLOT_ACTIVE}'")
    suspend fun clearActive()

    @Query("SELECT * FROM ring_state WHERE slot = '${RingStateEntity.SLOT_QUEUED}' ORDER BY id")
    suspend fun queued(): List<RingStateEntity>

    @Query("SELECT * FROM ring_state WHERE slot = '${RingStateEntity.SLOT_QUEUED}' ORDER BY id LIMIT 1")
    suspend fun headOfQueue(): RingStateEntity?

    @Query("SELECT * FROM ring_state WHERE slot = '${RingStateEntity.SLOT_QUEUED}' AND ring_id = :ringId ORDER BY id LIMIT 1")
    suspend fun findQueued(ringId: String): RingStateEntity?

    @Query("UPDATE ring_state SET json = :json WHERE id = :id")
    suspend fun updateJson(id: Long, json: String)

    @Insert
    suspend fun insert(entity: RingStateEntity): Long

    @Query("DELETE FROM ring_state WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM ring_state WHERE slot = '${RingStateEntity.SLOT_QUEUED}'")
    suspend fun clearQueue()
}
