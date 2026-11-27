package io.github.tweeter042.alarmclock.core.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.tweeter042.alarmclock.core.data.DeviceStorage
import io.github.tweeter042.alarmclock.core.data.InMemoryDatabaseRule
import io.github.tweeter042.alarmclock.core.data.repository.RoomAlarmRepository
import io.github.tweeter042.alarmclock.core.data.repository.RoomTimerRepository
import io.github.tweeter042.alarmclock.core.model.alarm.Alarm
import io.github.tweeter042.alarmclock.core.model.alarm.RepeatRule
import io.github.tweeter042.alarmclock.core.model.time.AlarmTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DatabaseTest {

    @get:Rule
    val database = InMemoryDatabaseRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        AlarmClockDatabase.closeInstance()
        DeviceStorage.databaseFile(context).let { file ->
            listOf(file, File(file.path + "-wal"), File(file.path + "-shm"), File(file.path + "-journal")).forEach { it.delete() }
        }
    }

    @Test
    fun freshDatabaseIsSeededWithTimerPresets() = runTest {
        val presets = RoomTimerRepository(database.db).observePresets().first()

        assertEquals(listOf(1, 3, 5, 10, 15, 30).map { it * 60_000L }, presets.map { it.durationMs })
        assertTrue(presets.all { it.label.isEmpty() })
        assertEquals(listOf(0, 1, 2, 3, 4, 5), presets.map { it.sortOrder })
        assertTrue("ids come from the rows", presets.all { it.id > 0L })
        assertEquals(presets.size, presets.map { it.id }.toSet().size)
    }

    @Test
    fun databaseFileLivesInDeviceProtectedStorage() = runTest {
        val db = AlarmClockDatabase.getInstance(context)
        RoomAlarmRepository(db).upsert(Alarm(label = "x"))

        val file = DeviceStorage.databaseFile(context)
        assertEquals(DeviceStorage.DATABASE_FILE, file.name)
        assertTrue("database must exist at $file", file.exists())
        val deviceDir = context.createDeviceProtectedStorageContext().dataDir.canonicalPath
        assertTrue("$file must be under $deviceDir", file.canonicalPath.startsWith(deviceDir))
        val credentialEncrypted = context.getDatabasePath(DeviceStorage.DATABASE_FILE)
        assertFalse(credentialEncrypted.canonicalPath == file.canonicalPath)
        assertFalse("nothing may be written to credential-encrypted storage", credentialEncrypted.exists())
    }

    @Test
    fun getInstanceIsASingleton() {
        assertTrue(AlarmClockDatabase.getInstance(context) === AlarmClockDatabase.getInstance(context))
    }

    @Test
    fun onDiskDatabaseKeepsDataAndSeedsOnlyOnce() = runTest {
        val first = AlarmClockDatabase.open(context)
        val id = RoomAlarmRepository(first).upsert(Alarm(time = AlarmTime(6, 15), label = "persisted"))
        RoomTimerRepository(first).deletePreset(RoomTimerRepository(first).observePresets().first().first().id)
        first.close()

        val reopened = AlarmClockDatabase.open(context)
        try {
            assertEquals("persisted", RoomAlarmRepository(reopened).getAlarm(id)?.label)
            assertEquals("presets are not re-seeded on open", 5, RoomTimerRepository(reopened).observePresets().first().size)
        } finally {
            reopened.close()
        }
    }

    @Test
    fun rowsWithUnreadableJsonAreSkippedNotFatal() = runTest {
        val dao = database.db.alarms()
        dao.upsert(AlarmEntity(enabled = true, groupId = null, minuteOfDay = 300, updatedAt = 0L, json = "{not json"))
        dao.upsert(AlarmEntity(enabled = true, groupId = null, minuteOfDay = 301, updatedAt = 0L, json = """{"time":{"hour":99,"minute":0}}"""))
        val goodId = RoomAlarmRepository(database.db).upsert(Alarm(time = AlarmTime(7, 0), label = "good"))

        val repository = RoomAlarmRepository(database.db)
        assertEquals(listOf(goodId), repository.getAlarms().map { it.id })
        assertEquals(listOf("good"), repository.observeAlarms().first().map { it.label })
        assertNull(repository.getAlarm(1L))
    }

    @Test
    fun jsonColumnToleratesUnknownAndMissingFields() = runTest {
        // A row written by a "future" version: unknown keys, and most fields left to their defaults.
        val json = """{"time":{"hour":5,"minute":45},"label":"future","newFeature":{"enabled":true},"repeat":{"type":"weekly","days":[1,3]}}"""
        val rowId = database.db.alarms().upsert(AlarmEntity(enabled = true, groupId = null, minuteOfDay = 345, updatedAt = 0L, json = json))

        val alarm = RoomAlarmRepository(database.db).getAlarm(rowId)
        assertNotNull(alarm)
        alarm!!
        assertEquals(rowId, alarm.id)
        assertEquals("future", alarm.label)
        assertEquals(AlarmTime(5, 45), alarm.time)
        assertEquals(RepeatRule.Weekly(setOf(1, 3)), alarm.repeat)
        assertEquals(Alarm().sound, alarm.sound)
    }
}
