package io.github.tweeter042.alarmclock

import android.app.LocaleManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.tweeter042.alarmclock.core.model.settings.AppLanguage
import io.github.tweeter042.alarmclock.icon.AppIconSwitcher
import io.github.tweeter042.alarmclock.stub.NoOpAlarmEngine
import io.github.tweeter042.alarmclock.stub.NoOpReliabilityChecker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * End to end with the real Application, the real AppGraph and core:data storage: a fresh install
 * opens onboarding, finishing it lands on the alarm list, and the graph is wired as promised.
 *
 * One test only: core:data keeps one DataStore per process, so a second test in this JVM would
 * see this test's settings.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class RealAppSmokeTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun freshInstallRunsEndToEnd() {
        val app = ApplicationProvider.getApplicationContext<AlarmClockApplication>()
        val graph = app.appGraph

        // Wiring: build info from BuildConfig, core:data repositories, NoOp stand-ins.
        assertEquals(BuildConfig.APPLICATION_ID, graph.buildInfo.applicationId)
        assertEquals(BuildConfig.VERSION_CODE, graph.buildInfo.versionCode)
        assertEquals(BuildConfig.VERSION_NAME, graph.buildInfo.versionName)
        assertEquals(BuildConfig.BUILD_DATE, graph.buildInfo.buildDate)
        assertEquals(BuildConfig.AI_MODEL_NAME, graph.buildInfo.aiModelName)
        listOf(graph.settings, graph.alarms, graph.timers, graph.stopwatch, graph.worldClocks, graph.backup, graph.changelog).forEach {
            assertTrue(it.javaClass.name, it.javaClass.name.startsWith("io.github.tweeter042.alarmclock.core.data."))
        }
        assertTrue(graph.appIcons is AppIconSwitcher)
        assertTrue(graph.engine is NoOpAlarmEngine)
        assertTrue(graph.reliability is NoOpReliabilityChecker)
        assertNull(graph.engine.activeRing.value)
        assertNull(graph.engine.nextAlarm.value)
        runBlocking {
            val before = System.currentTimeMillis()
            val triggerAt = graph.engine.startNap(20)
            assertTrue(triggerAt >= before + 20 * 60_000L)
            assertTrue("the bundled changelog is readable", graph.changelog.entryFor(BuildConfig.VERSION_CODE) != null)
        }

        // First run: English is set explicitly before any activity exists.
        assertEquals("en", app.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags())

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            compose.waitUntilAtLeastOneExists(hasText("OnboardingRoute"), timeoutMillis = 10_000)
            compose.onNodeWithText("OnboardingRoute").assertIsDisplayed()

            runBlocking { graph.settings.update { it.copy(onboardingCompleted = true) } }
            compose.waitUntilAtLeastOneExists(hasText("AlarmsRoute"), timeoutMillis = 10_000)
            compose.onNodeWithText("AlarmsRoute").assertIsDisplayed()
            compose.onNodeWithText("World clock").assertIsDisplayed()

            // First install: the version is recorded without showing What's New.
            compose.waitUntil(timeoutMillis = 10_000) {
                runBlocking { graph.settings.current().lastSeenVersionCode } == BuildConfig.VERSION_CODE
            }
            assertEquals(AppLanguage.ENGLISH, runBlocking { graph.settings.current().region.language })
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
