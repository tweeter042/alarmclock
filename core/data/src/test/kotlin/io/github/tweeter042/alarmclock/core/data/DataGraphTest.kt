package io.github.tweeter042.alarmclock.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.tweeter042.alarmclock.core.data.datastore.ProcessDataStores
import io.github.tweeter042.alarmclock.core.data.db.AlarmClockDatabase
import io.github.tweeter042.alarmclock.core.model.alarm.Alarm
import io.github.tweeter042.alarmclock.core.model.repository.SecretStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DataGraphTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        AlarmClockDatabase.closeInstance()
        ProcessDataStores.reset()
    }

    @Test
    fun everyMemberIsAvailableAndLazy() = runTest {
        val graph = DataGraph(context, versionName = "1.0.0")
        assertFalse("constructing the graph must not touch the disk", DeviceStorage.databaseFile(context).exists())

        // Touch every member: each must resolve without error.
        listOf(
            graph.settings, graph.secrets, graph.alarms, graph.holidays, graph.timers, graph.stopwatch,
            graph.worldClocks, graph.events, graph.routine, graph.missionAssets, graph.calendarRules,
            graph.locationAlarms, graph.ringStateStore, graph.scheduleLedger, graph.backupManager, graph.changelog,
        ).forEach { assertTrue(it.javaClass.name.startsWith("io.github.tweeter042.alarmclock.core.data.")) }

        assertSame(graph.alarms, graph.alarms)
        assertEquals("seeded on first open", 6, graph.timers.observePresets().first().size)
        assertTrue(graph.changelog.entries().isEmpty()) // the asset ships with :app, not this library
    }

    @Test
    fun ringPathDataLivesInDeviceProtectedStorage() = runTest {
        val graph = DataGraph(context, versionName = "1.0.0")
        graph.alarms.upsert(Alarm(label = "Direct Boot"))
        graph.settings.update { it.copy(onboardingCompleted = true) }
        graph.stopwatch.update { it.copy(running = true, startedAt = 1L) }

        val deviceProtected = context.createDeviceProtectedStorageContext()
        val deviceDir = deviceProtected.dataDir.canonicalPath
        val credentialDir = context.dataDir.canonicalPath
        assertFalse(deviceDir.startsWith(credentialDir) || credentialDir.startsWith(deviceDir))

        val database = DeviceStorage.databaseFile(context)
        val settingsFile = File(deviceProtected.filesDir, "datastore/settings.json")
        val stopwatchFile = File(deviceProtected.filesDir, "datastore/stopwatch.json")
        listOf(database, settingsFile, stopwatchFile).forEach { file ->
            assertTrue("$file must exist", file.exists())
            assertTrue("$file must be in device-protected storage", file.canonicalPath.startsWith(deviceDir))
        }
        assertFalse(File(context.filesDir, "datastore/settings.json").exists())
        assertFalse(File(context.filesDir, "datastore/stopwatch.json").exists())
        assertFalse(context.getDatabasePath(DeviceStorage.DATABASE_FILE).exists())
    }

    @Test
    fun graphsShareOneDatabaseAndOneDataStorePerProcess() = runTest {
        val first = DataGraph(context, versionName = "1.0.0")
        val second = DataGraph(context, versionName = "1.0.0")

        val id = first.alarms.upsert(Alarm(label = "shared"))
        second.settings.update { it.copy(lastSeenVersionCode = 7) }

        assertEquals("shared", second.alarms.getAlarm(id)?.label)
        assertEquals(7, first.settings.current().lastSeenVersionCode)
    }

    @Test
    fun secretsUseCredentialEncryptedStorage() = runTest {
        val graph = DataGraph(context, versionName = "1.0.0")
        // Robolectric has no AndroidKeyStore, so only the locked/absent paths can run here.
        assertEquals(null, graph.secrets.get(SecretStore.SMARTTHINGS_ACCESS_TOKEN))
        assertFalse(File(context.createDeviceProtectedStorageContext().dataDir, "shared_prefs/secrets.xml").exists())
    }
}
