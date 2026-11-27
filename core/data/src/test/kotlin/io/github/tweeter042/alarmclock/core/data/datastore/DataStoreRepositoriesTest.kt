package io.github.tweeter042.alarmclock.core.data.datastore

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.tweeter042.alarmclock.core.model.entity.StopwatchState
import io.github.tweeter042.alarmclock.core.model.settings.AppLanguage
import io.github.tweeter042.alarmclock.core.model.settings.AppSettings
import io.github.tweeter042.alarmclock.core.model.theme.ThemeId
import io.github.tweeter042.alarmclock.core.model.util.ModelJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DataStoreRepositoriesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
    }

    private fun settingsAt(file: File): DataStoreSettingsRepository {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }
        val store = jsonDataStore("Settings", AppSettings.serializer(), AppSettings(), scope) { file }
        return DataStoreSettingsRepository(JsonDocumentStore(store, AppSettings(), "Settings"))
    }

    private fun stopwatchAt(file: File): DataStoreStopwatchRepository {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }
        val store = jsonDataStore("Stopwatch", StopwatchState.serializer(), StopwatchState(), scope) { file }
        return DataStoreStopwatchRepository(JsonDocumentStore(store, StopwatchState(), "Stopwatch"))
    }

    /** Closes the stores (waiting until DataStore has released the files) so they can be reopened. */
    private suspend fun closeAll() {
        scopes.forEach { it.coroutineContext.job.cancelAndJoin() }
        scopes.clear()
    }

    @Test
    fun defaultsWhenNoFileExists() = runTest {
        val file = File(folder.root, "settings.json")
        assertEquals(AppSettings(), settingsAt(file).current())
    }

    @Test
    fun updatesArePersistedAsModelJson() = runTest {
        val file = File(folder.root, "settings.json")
        val repository = settingsAt(file)

        repository.update { it.copy(onboardingCompleted = true, appearance = it.appearance.copy(themeId = ThemeId.BRUTAL)) }

        val onDisk = ModelJson.decodeFromString(AppSettings.serializer(), file.readText())
        assertTrue(onDisk.onboardingCompleted)
        assertEquals(ThemeId.BRUTAL, onDisk.appearance.themeId)

        closeAll()
        val reopened = settingsAt(file)
        assertEquals(ThemeId.BRUTAL, reopened.current().appearance.themeId)
    }

    @Test
    fun flowEmitsDistinctValues() = runTest {
        val repository = settingsAt(File(folder.root, "settings.json"))
        repository.settings.test {
            assertEquals(AppSettings(), awaitItem())
            repository.update { it.copy(region = it.region.copy(language = AppLanguage.THAI)) }
            assertEquals(AppLanguage.THAI, awaitItem().region.language)
            repository.update { it } // unchanged: no emission
            repository.update { it.copy(lastSeenVersionCode = 3) }
            assertEquals(3, awaitItem().lastSeenVersionCode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun concurrentUpdatesAreAtomic() = runTest {
        val repository = settingsAt(File(folder.root, "settings.json"))

        (1..50).map { async { repository.update { it.copy(lastSeenVersionCode = it.lastSeenVersionCode + 1) } } }.awaitAll()

        assertEquals(50, repository.current().lastSeenVersionCode)
    }

    @Test
    fun corruptFileIsReplacedWithDefaults() = runTest {
        val file = File(folder.root, "settings.json").apply { writeText("{\"appearance\": {\"themeId\": \"BRUTAL\"") }
        val repository = settingsAt(file)

        assertEquals(AppSettings(), repository.current())
        // The handler rewrote the file, so the next read is clean and updates work again.
        assertEquals(AppSettings(), ModelJson.decodeFromString(AppSettings.serializer(), file.readText()))
        repository.update { it.copy(onboardingCompleted = true) }
        assertTrue(repository.current().onboardingCompleted)
    }

    @Test
    fun emptyOrBinaryFileIsTreatedAsCorrupt() = runTest {
        val empty = File(folder.root, "empty.json").apply { writeBytes(ByteArray(0)) }
        assertEquals(AppSettings(), settingsAt(empty).current())

        val binary = File(folder.root, "binary.json").apply { writeBytes(byteArrayOf(0x00, 0xFF.toByte(), 0x13, 0x37)) }
        assertEquals(AppSettings(), settingsAt(binary).current())
    }

    @Test
    fun structurallyWrongValuesAreTreatedAsCorrupt() = runTest {
        // Valid JSON, but a model invariant is broken (hour 99): must not crash, must reset.
        val file = File(folder.root, "settings.json").apply { writeText("""{"bedtime":{"time":{"hour":99,"minute":0}}}""") }
        assertEquals(AppSettings(), settingsAt(file).current())
    }

    @Test
    fun unknownKeysFromNewerVersionsAreIgnored() = runTest {
        val file = File(folder.root, "settings.json").apply {
            writeText("""{"onboardingCompleted":true,"someFutureSection":{"x":1},"appearance":{"themeId":"NEON","futureFlag":true}}""")
        }
        val settings = settingsAt(file).current()
        assertTrue(settings.onboardingCompleted)
        assertEquals(ThemeId.NEON, settings.appearance.themeId)
    }

    @Test
    fun unreadableFileFallsBackToDefaultsInsteadOfThrowing() = runTest {
        // A directory where the file should be: every read fails with an IOException.
        val file = File(folder.root, "settings.json").apply { mkdirs() }
        assertEquals(AppSettings(), settingsAt(file).current())
    }

    @Test
    fun observersRecoverOnceTheFileIsReadableAgain() = runTest {
        val file = File(folder.root, "settings.json").apply { mkdirs() }
        val repository = settingsAt(file)

        repository.settings.test {
            assertEquals("defaults instead of an exception", AppSettings(), awaitItem())

            file.delete()
            file.writeText(ModelJson.encodeToString(AppSettings.serializer(), AppSettings(lastSeenVersionCode = 9)))

            assertEquals("the flow keeps retrying and then delivers the real settings", 9, awaitItem().lastSeenVersionCode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun stopwatchStateRoundTrip() = runTest {
        val file = File(folder.root, "stopwatch.json")
        val repository = stopwatchAt(file)
        repository.state.test {
            assertEquals(StopwatchState(), awaitItem())
            repository.update { it.copy(running = true, startedAt = 1_000L) }
            assertTrue(awaitItem().running)
            repository.update { it.copy(laps = it.laps + it.elapsedAt(4_000L)) }
            assertEquals(listOf(3_000L), awaitItem().laps)
            cancelAndIgnoreRemainingEvents()
        }
        closeAll()
        assertEquals(listOf(3_000L), stopwatchAt(file).current().laps)
    }
}
