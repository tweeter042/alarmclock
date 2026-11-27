package io.github.tweeter042.alarmclock.core.model.repository

import io.github.tweeter042.alarmclock.core.model.alarm.ActiveRing
import io.github.tweeter042.alarmclock.core.model.alarm.Alarm
import io.github.tweeter042.alarmclock.core.model.entity.AlarmEvent
import io.github.tweeter042.alarmclock.core.model.entity.AlarmGroup
import io.github.tweeter042.alarmclock.core.model.entity.CalendarAlarmRule
import io.github.tweeter042.alarmclock.core.model.entity.Holiday
import io.github.tweeter042.alarmclock.core.model.entity.HolidaySource
import io.github.tweeter042.alarmclock.core.model.entity.LocationAlarm
import io.github.tweeter042.alarmclock.core.model.entity.MorningRoutineItem
import io.github.tweeter042.alarmclock.core.model.entity.PhotoTarget
import io.github.tweeter042.alarmclock.core.model.entity.RegisteredCode
import io.github.tweeter042.alarmclock.core.model.entity.ScheduledTrigger
import io.github.tweeter042.alarmclock.core.model.entity.SleepLog
import io.github.tweeter042.alarmclock.core.model.entity.StopwatchState
import io.github.tweeter042.alarmclock.core.model.entity.TimerModel
import io.github.tweeter042.alarmclock.core.model.entity.TimerPreset
import io.github.tweeter042.alarmclock.core.model.entity.WorldClock
import io.github.tweeter042.alarmclock.core.model.settings.AppSettings
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/*
 * Repository contracts, implemented in core:data.
 * All data that the ring path needs lives in DEVICE-PROTECTED storage so alarms work in
 * Direct Boot (after a reboot, before the first unlock). Flows emit on every change.
 * All suspend functions are main-safe.
 */

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun current(): AppSettings
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

/** Encrypted (Android Keystore) small secrets in credential-encrypted storage. Not backed up. */
interface SecretStore {
    suspend fun get(key: String): String?
    /** null removes the key. */
    suspend fun put(key: String, value: String?)

    companion object Keys {
        const val SMARTTHINGS_ACCESS_TOKEN = "smartthings.access_token"
        const val SMARTTHINGS_REFRESH_TOKEN = "smartthings.refresh_token"
        const val SMARTTHINGS_CLIENT_SECRET = "smartthings.client_secret"
    }
}

interface AlarmRepository {
    fun observeAlarms(): Flow<List<Alarm>>
    fun observeAlarm(id: Long): Flow<Alarm?>
    suspend fun getAlarm(id: Long): Alarm?
    suspend fun getAlarms(): List<Alarm>
    /** Insert (id == 0) or update. Sets createdAt/updatedAt. Returns the id. */
    suspend fun upsert(alarm: Alarm): Long
    suspend fun delete(id: Long)
    suspend fun setEnabled(id: Long, enabled: Boolean)
    /** Atomically edits one alarm (no-op if missing). */
    suspend fun update(id: Long, transform: (Alarm) -> Alarm)

    fun observeGroups(): Flow<List<AlarmGroup>>
    suspend fun getGroups(): List<AlarmGroup>
    suspend fun upsertGroup(group: AlarmGroup): Long
    /** Alarms in the group are kept and moved out of the group. */
    suspend fun deleteGroup(id: Long)
    suspend fun setGroupEnabled(id: Long, enabled: Boolean)
}

interface HolidayRepository {
    fun observeAll(): Flow<List<Holiday>>
    suspend fun between(from: LocalDate, to: LocalDate): List<Holiday>
    suspend fun upsert(holiday: Holiday): Long
    suspend fun delete(id: Long)
    /** Replaces all holidays of [source] (used for calendar imports). */
    suspend fun replaceSource(source: HolidaySource, holidays: List<Holiday>)
}

interface TimerRepository {
    fun observeTimers(): Flow<List<TimerModel>>
    suspend fun getTimer(id: Long): TimerModel?
    suspend fun upsert(timer: TimerModel): Long
    suspend fun delete(id: Long)
    fun observePresets(): Flow<List<TimerPreset>>
    suspend fun upsertPreset(preset: TimerPreset): Long
    suspend fun deletePreset(id: Long)
}

interface StopwatchRepository {
    val state: Flow<StopwatchState>
    suspend fun current(): StopwatchState
    suspend fun update(transform: (StopwatchState) -> StopwatchState)
}

interface WorldClockRepository {
    fun observe(): Flow<List<WorldClock>>
    suspend fun upsert(clock: WorldClock): Long
    suspend fun delete(id: Long)
    /** Persists the given order (ids first to last). */
    suspend fun reorder(ids: List<Long>)
}

interface EventLogRepository {
    suspend fun insert(event: AlarmEvent): Long
    suspend fun update(event: AlarmEvent)
    fun observeBetween(fromMillis: Long, toMillis: Long): Flow<List<AlarmEvent>>
    suspend fun between(fromMillis: Long, toMillis: Long): List<AlarmEvent>
    suspend fun insertSleepLog(log: SleepLog): Long
    fun observeSleepLogs(fromMillis: Long, toMillis: Long): Flow<List<SleepLog>>
    /** Deletes history older than [beforeMillis]. */
    suspend fun prune(beforeMillis: Long)
}

interface RoutineRepository {
    fun observeItems(): Flow<List<MorningRoutineItem>>
    suspend fun upsert(item: MorningRoutineItem): Long
    suspend fun delete(id: Long)
    suspend fun reorder(ids: List<Long>)
}

interface MissionAssetRepository {
    fun observeCodes(): Flow<List<RegisteredCode>>
    suspend fun getCodes(ids: List<Long>): List<RegisteredCode>
    suspend fun upsertCode(code: RegisteredCode): Long
    suspend fun deleteCode(id: Long)
    fun observePhotoTargets(): Flow<List<PhotoTarget>>
    suspend fun getPhotoTargets(ids: List<Long>): List<PhotoTarget>
    suspend fun upsertPhotoTarget(target: PhotoTarget): Long
    suspend fun deletePhotoTarget(id: Long)
}

interface CalendarRuleRepository {
    fun observe(): Flow<List<CalendarAlarmRule>>
    suspend fun getAll(): List<CalendarAlarmRule>
    suspend fun get(id: Long): CalendarAlarmRule?
    suspend fun upsert(rule: CalendarAlarmRule): Long
    suspend fun delete(id: Long)
}

interface LocationAlarmRepository {
    fun observe(): Flow<List<LocationAlarm>>
    suspend fun getAll(): List<LocationAlarm>
    suspend fun get(id: Long): LocationAlarm?
    suspend fun upsert(alarm: LocationAlarm): Long
    suspend fun delete(id: Long)
}

// ---------------------------------------------------------------------------------------------
// Engine-internal stores (used only by core:alarm; also implemented in core:data)
// ---------------------------------------------------------------------------------------------

/** Persists the active ring and the queue of rings waiting behind it. */
interface RingStateStore {
    val active: Flow<ActiveRing?>
    suspend fun getActive(): ActiveRing?
    suspend fun setActive(ring: ActiveRing?)
    suspend fun queue(): List<ActiveRing>
    suspend fun enqueue(ring: ActiveRing)
    suspend fun dequeue(): ActiveRing?
    suspend fun clearQueue()
}

/** Records every trigger registered with AlarmManager. */
interface ScheduleLedger {
    suspend fun all(): List<ScheduledTrigger>
    suspend fun replaceAll(triggers: List<ScheduledTrigger>)
    suspend fun put(trigger: ScheduledTrigger)
    suspend fun remove(requestCode: Int)
    suspend fun get(requestCode: Int): ScheduledTrigger?
}
