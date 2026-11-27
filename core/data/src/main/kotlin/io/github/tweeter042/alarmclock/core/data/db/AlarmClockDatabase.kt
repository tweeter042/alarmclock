package io.github.tweeter042.alarmclock.core.data.db

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.tweeter042.alarmclock.core.data.DataLog
import io.github.tweeter042.alarmclock.core.data.DeviceStorage
import io.github.tweeter042.alarmclock.core.model.entity.TimerPreset

/**
 * The app database, stored in DEVICE-PROTECTED storage (file [DeviceStorage.DATABASE_FILE]) so the
 * ring engine can read alarms in Direct Boot.
 *
 * Schema rules: version [VERSION], schema exported to `core/data/schemas`. There is deliberately
 * NO destructive-migration fallback: losing the user's alarms on an upgrade is worse than a crash
 * that is caught in testing. Every future version bump must ship a Migration (or AutoMigration).
 */
@Database(
    entities = [
        AlarmEntity::class,
        AlarmGroupEntity::class,
        HolidayEntity::class,
        TimerEntity::class,
        TimerPresetEntity::class,
        WorldClockEntity::class,
        AlarmEventEntity::class,
        SleepLogEntity::class,
        RoutineItemEntity::class,
        RegisteredCodeEntity::class,
        PhotoTargetEntity::class,
        CalendarRuleEntity::class,
        LocationAlarmEntity::class,
        ScheduledTriggerEntity::class,
        RingStateEntity::class,
    ],
    version = AlarmClockDatabase.VERSION,
    exportSchema = true,
)
internal abstract class AlarmClockDatabase : RoomDatabase() {
    abstract fun alarms(): AlarmDao
    abstract fun groups(): AlarmGroupDao
    abstract fun holidays(): HolidayDao
    abstract fun timers(): TimerDao
    abstract fun timerPresets(): TimerPresetDao
    abstract fun worldClocks(): WorldClockDao
    abstract fun events(): AlarmEventDao
    abstract fun sleepLogs(): SleepLogDao
    abstract fun routineItems(): RoutineItemDao
    abstract fun registeredCodes(): RegisteredCodeDao
    abstract fun photoTargets(): PhotoTargetDao
    abstract fun calendarRules(): CalendarRuleDao
    abstract fun locationAlarms(): LocationAlarmDao
    abstract fun scheduleLedger(): ScheduleLedgerDao
    abstract fun ringState(): RingStateDao

    companion object {
        const val VERSION = 1

        @Volatile
        private var instance: AlarmClockDatabase? = null

        /** The single per-process instance (device-protected storage). */
        fun getInstance(context: Context): AlarmClockDatabase =
            instance ?: synchronized(this) {
                instance ?: open(context).also { instance = it }
            }

        /** Opens the on-disk database. Prefer [getInstance]; tests use this or [inMemory]. */
        fun open(context: Context): AlarmClockDatabase {
            val deviceContext = DeviceStorage.deviceProtectedContext(context)
            return Room.databaseBuilder(deviceContext, AlarmClockDatabase::class.java, DeviceStorage.DATABASE_FILE)
                .addCallback(SeedCallback)
                .build()
        }

        @VisibleForTesting
        fun inMemory(context: Context): AlarmClockDatabase =
            Room.inMemoryDatabaseBuilder(context, AlarmClockDatabase::class.java)
                .addCallback(SeedCallback)
                .build()

        /** Closes and forgets the process instance (tests only). */
        @VisibleForTesting
        fun closeInstance() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }
    }
}

/** First-run content, written in the same transaction that creates the schema. */
internal object SeedCallback : RoomDatabase.Callback() {
    /** Timer presets offered on a fresh install (blank labels: the UI shows the duration). */
    val TIMER_PRESET_MINUTES: List<Int> = listOf(1, 3, 5, 10, 15, 30)

    override fun onCreate(db: SupportSQLiteDatabase) {
        TIMER_PRESET_MINUTES.forEachIndexed { index, minutes ->
            val preset = TimerPreset(label = "", durationMs = minutes * 60_000L, sortOrder = index)
            db.execSQL(
                "INSERT INTO timer_presets (sort_order, json) VALUES (?, ?)",
                arrayOf<Any>(index, Columns.timerPreset.encode(preset)),
            )
        }
        DataLog.i("Database created; seeded ${TIMER_PRESET_MINUTES.size} timer presets")
    }
}
