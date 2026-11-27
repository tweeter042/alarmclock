package io.github.tweeter042.alarmclock.core.data

import android.content.Context
import io.github.tweeter042.alarmclock.core.data.backup.JsonBackupManager
import io.github.tweeter042.alarmclock.core.data.changelog.AssetChangelogRepository
import io.github.tweeter042.alarmclock.core.data.datastore.DataStoreSettingsRepository
import io.github.tweeter042.alarmclock.core.data.datastore.DataStoreStopwatchRepository
import io.github.tweeter042.alarmclock.core.data.datastore.JsonDocumentStore
import io.github.tweeter042.alarmclock.core.data.datastore.ProcessDataStores
import io.github.tweeter042.alarmclock.core.data.db.AlarmClockDatabase
import io.github.tweeter042.alarmclock.core.data.repository.RoomAlarmRepository
import io.github.tweeter042.alarmclock.core.data.repository.RoomCalendarRuleRepository
import io.github.tweeter042.alarmclock.core.data.repository.RoomEventLogRepository
import io.github.tweeter042.alarmclock.core.data.repository.RoomHolidayRepository
import io.github.tweeter042.alarmclock.core.data.repository.RoomLocationAlarmRepository
import io.github.tweeter042.alarmclock.core.data.repository.RoomMissionAssetRepository
import io.github.tweeter042.alarmclock.core.data.repository.RoomRingStateStore
import io.github.tweeter042.alarmclock.core.data.repository.RoomRoutineRepository
import io.github.tweeter042.alarmclock.core.data.repository.RoomScheduleLedger
import io.github.tweeter042.alarmclock.core.data.repository.RoomTimerRepository
import io.github.tweeter042.alarmclock.core.data.repository.RoomWorldClockRepository
import io.github.tweeter042.alarmclock.core.data.secret.KeystoreSecretStore
import io.github.tweeter042.alarmclock.core.model.entity.StopwatchState
import io.github.tweeter042.alarmclock.core.model.repository.AlarmRepository
import io.github.tweeter042.alarmclock.core.model.repository.CalendarRuleRepository
import io.github.tweeter042.alarmclock.core.model.repository.EventLogRepository
import io.github.tweeter042.alarmclock.core.model.repository.HolidayRepository
import io.github.tweeter042.alarmclock.core.model.repository.LocationAlarmRepository
import io.github.tweeter042.alarmclock.core.model.repository.MissionAssetRepository
import io.github.tweeter042.alarmclock.core.model.repository.RingStateStore
import io.github.tweeter042.alarmclock.core.model.repository.RoutineRepository
import io.github.tweeter042.alarmclock.core.model.repository.ScheduleLedger
import io.github.tweeter042.alarmclock.core.model.repository.SecretStore
import io.github.tweeter042.alarmclock.core.model.repository.SettingsRepository
import io.github.tweeter042.alarmclock.core.model.repository.StopwatchRepository
import io.github.tweeter042.alarmclock.core.model.repository.TimerRepository
import io.github.tweeter042.alarmclock.core.model.repository.WorldClockRepository
import io.github.tweeter042.alarmclock.core.model.service.BackupManager
import io.github.tweeter042.alarmclock.core.model.service.ChangelogRepository
import io.github.tweeter042.alarmclock.core.model.settings.AppSettings

/**
 * Everything core:data provides, typed as the core:model contracts. Build it once in the
 * Application and hand its members to the AppGraph.
 *
 * Every member is created lazily (thread-safe) on first use; nothing touches the disk in the
 * constructor. The Room database and the DataStores are process-wide singletons in
 * DEVICE-PROTECTED storage, so all ring-path data is readable in Direct Boot (before the first
 * unlock after a reboot). The exception is [secrets]: credential-encrypted, null/no-op until unlock.
 *
 * Single process: every component that uses this data (activities, receivers, the ring service)
 * must run in the app's default process. DataStore and Room change notifications are per process.
 *
 * @param versionName the app's versionName, written into backups.
 */
class DataGraph(context: Context, private val versionName: String) {

    private val appContext: Context = context.applicationContext ?: context
    private val clock: () -> Long = System::currentTimeMillis

    private val database: AlarmClockDatabase by lazy { AlarmClockDatabase.getInstance(appContext) }

    val settings: SettingsRepository by lazy {
        DataStoreSettingsRepository(JsonDocumentStore(ProcessDataStores.settings(appContext), AppSettings(), "Settings"))
    }

    /** Credential-encrypted: [SecretStore.get] returns null and [SecretStore.put] is a no-op before the first unlock. */
    val secrets: SecretStore by lazy { KeystoreSecretStore(appContext) }

    val alarms: AlarmRepository by lazy { RoomAlarmRepository(database, clock) }

    val holidays: HolidayRepository by lazy { RoomHolidayRepository(database) }

    val timers: TimerRepository by lazy { RoomTimerRepository(database, clock) }

    val stopwatch: StopwatchRepository by lazy {
        DataStoreStopwatchRepository(JsonDocumentStore(ProcessDataStores.stopwatch(appContext), StopwatchState(), "Stopwatch"))
    }

    val worldClocks: WorldClockRepository by lazy { RoomWorldClockRepository(database) }

    val events: EventLogRepository by lazy { RoomEventLogRepository(database) }

    val routine: RoutineRepository by lazy { RoomRoutineRepository(database) }

    val missionAssets: MissionAssetRepository by lazy { RoomMissionAssetRepository(database, clock) }

    val calendarRules: CalendarRuleRepository by lazy { RoomCalendarRuleRepository(database) }

    val locationAlarms: LocationAlarmRepository by lazy { RoomLocationAlarmRepository(database) }

    /** Engine-internal (core:alarm): the active ring and the queue behind it. */
    val ringStateStore: RingStateStore by lazy { RoomRingStateStore(database) }

    /** Engine-internal (core:alarm): every trigger registered with AlarmManager. */
    val scheduleLedger: ScheduleLedger by lazy { RoomScheduleLedger(database) }

    val backupManager: BackupManager by lazy { JsonBackupManager(appContext, database, settings, versionName, clock) }

    /** Reads the `changelog.json` asset of the app. */
    val changelog: ChangelogRepository by lazy { AssetChangelogRepository(appContext) }
}
