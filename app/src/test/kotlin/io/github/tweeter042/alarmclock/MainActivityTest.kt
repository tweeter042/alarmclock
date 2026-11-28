package io.github.tweeter042.alarmclock

import android.app.LocaleManager
import android.content.Intent
import android.os.LocaleList
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.semantics.SemanticsActions
import io.github.tweeter042.alarmclock.core.model.settings.AppLanguage
import io.github.tweeter042.alarmclock.core.model.settings.AppSettings
import io.github.tweeter042.alarmclock.core.model.settings.RegionSettings
import io.github.tweeter042.alarmclock.core.ui.navigation.MainIntents
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * The app shell on Robolectric with an in-memory graph: onboarding gate, bottom bar, MainIntents
 * deep links, What's New and the language mirror. Feature screens are still placeholders that
 * show their route name.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = TestApplication::class, qualifiers = "w411dp-h891dp-xxhdpi")
class MainActivityTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: TestApplication get() = ApplicationProvider.getApplicationContext()

    /** Robolectric drives the lifecycle synchronously (ActivityScenario.close() deadlocks inside the compose rule). */
    private var controller: ActivityController<MainActivity>? = null
    private val activity: MainActivity get() = controller!!.get()

    private val onboarded = AppSettings(onboardingCompleted = true, lastSeenVersionCode = 1)

    @Before
    fun followSystemLanguage() {
        app.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.getEmptyLocaleList()
    }

    @After
    fun close() {
        controller?.let { if (!it.get().isDestroyed) it.pause().stop().destroy() }
    }

    private fun launch(graph: TestAppGraph, intent: Intent = Intent(app, MainActivity::class.java)): ActivityController<MainActivity> {
        app.graph = graph
        return Robolectric.buildActivity(MainActivity::class.java, intent).setup().also { controller = it }
    }

    private fun launch(settings: AppSettings, intent: Intent = Intent(app, MainActivity::class.java)) =
        launch(TestAppGraph(FakeSettingsRepository(settings)), intent)

    @Test
    fun freshInstallShowsOnboardingWithoutTheBottomBar() {
        launch(AppSettings())
        compose.onNodeWithText("OnboardingRoute").assertIsDisplayed()
        compose.onNodeWithText("Alarms").assertDoesNotExist()
    }

    @Test
    fun onboardedUserLandsOnAlarmsWithTheBottomBar() {
        launch(onboarded)
        compose.onNodeWithText("AlarmsRoute").assertIsDisplayed()
        listOf("Alarms", "World clock", "Timer", "Stopwatch", "Sleep").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithText("Alarms").assertIsSelected()
    }

    @Test
    fun finishingOnboardingSwitchesToAlarmsAndDropsOnboarding() {
        val settings = FakeSettingsRepository(AppSettings())
        launch(TestAppGraph(settings))
        compose.onNodeWithText("OnboardingRoute").assertIsDisplayed()

        settings.state.value = settings.state.value.copy(onboardingCompleted = true)
        compose.waitForIdle()
        compose.onNodeWithText("AlarmsRoute").assertIsDisplayed()
        compose.onNodeWithText("OnboardingRoute").assertDoesNotExist()

        // Onboarding is not on the back stack any more: back leaves the app.
        activity.onBackPressedDispatcher.onBackPressed()
        assertTrue(activity.isFinishing)
    }

    @Test
    fun bottomBarSwitchesTabsAndBackReturnsToAlarms() {
        launch(onboarded)
        compose.onNodeWithText("Timer").performClick()
        compose.onNodeWithText("TimerRoute").assertIsDisplayed()
        compose.onNodeWithText("Timer").assertIsSelected()
        compose.onNodeWithText("Sleep").performClick()
        compose.onNodeWithText("SleepRoute").assertIsDisplayed()
        activity.onBackPressedDispatcher.onBackPressed()
        compose.onNodeWithText("AlarmsRoute").assertIsDisplayed()
    }

    @Test
    fun deepLinkOpensATab() {
        launch(TestAppGraph(FakeSettingsRepository(onboarded)), MainIntents.openRoute(app, MainIntents.ROUTE_STOPWATCH))
        compose.onNodeWithText("StopwatchRoute").assertIsDisplayed()
        compose.onNodeWithText("Stopwatch").assertIsSelected()
    }

    @Test
    fun deepLinkToAScreenHidesTheBottomBar() {
        launch(TestAppGraph(FakeSettingsRepository(onboarded)), MainIntents.openRoute(app, MainIntents.ROUTE_RELIABILITY))
        compose.onNodeWithText("ReliabilityRoute").assertIsDisplayed()
        compose.onNodeWithText("World clock").assertDoesNotExist()
        // Back goes to the alarm list below it.
        activity.onBackPressedDispatcher.onBackPressed()
        compose.onNodeWithText("AlarmsRoute").assertIsDisplayed()
    }

    @Test
    fun newIntentOpensTheAlarmEditor() {
        launch(onboarded)
        compose.onNodeWithText("AlarmsRoute").assertIsDisplayed()
        controller!!.newIntent(MainIntents.openAlarm(app, 7))
        compose.onNodeWithText("AlarmEditRoute").assertIsDisplayed()
    }

    @Test
    fun deepLinkWaitsUntilOnboardingIsDone() {
        val settings = FakeSettingsRepository(AppSettings())
        launch(TestAppGraph(settings), MainIntents.openRoute(app, MainIntents.ROUTE_TIMER))
        compose.onNodeWithText("OnboardingRoute").assertIsDisplayed()
        settings.state.value = settings.state.value.copy(onboardingCompleted = true)
        compose.waitForIdle()
        compose.onNodeWithText("TimerRoute").assertIsDisplayed()
    }

    @Test
    fun whatsNewAppearsOnceAfterAnUpdate() {
        val settings = FakeSettingsRepository(onboarded)
        val graph = TestAppGraph(
            settings = settings,
            changelog = FakeChangelog(listOf(changelogEntry(2, "1.1.0", "A bigger snooze button" to "ปุ่มเลื่อนปลุกใหญ่ขึ้น"))),
            versionCode = 2,
            versionName = "1.1.0",
        )
        launch(graph)
        // The sheet is a dialog window, which Robolectric neither draws nor routes touches to
        // reliably: check it through semantics (its look is covered by ShellScreenshotTest).
        compose.onNodeWithText("What's new in 1.1.0").assertExists()
        compose.onNodeWithText("Released 15 Dec 2026").assertExists()
        compose.onNodeWithText("A bigger snooze button").assertExists()
        compose.onNodeWithText("This app was developed with the assistance of AI (model: not specified).").assertExists()
        assertEquals("not stored while the sheet is open", 1, settings.state.value.lastSeenVersionCode)

        compose.onNodeWithText("Got it").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithText("What's new in 1.1.0").assertDoesNotExist()
        assertEquals(2, settings.state.value.lastSeenVersionCode)
    }

    @Test
    fun whatsNewSeeAllOpensTheChangelog() {
        val settings = FakeSettingsRepository(onboarded)
        val graph = TestAppGraph(
            settings = settings,
            changelog = FakeChangelog(listOf(changelogEntry(2, "1.1.0", "Faster start" to "เปิดแอปเร็วขึ้น"))),
            versionCode = 2,
            versionName = "1.1.0",
            aiModelName = "Test Model",
        )
        launch(graph)
        compose.onNodeWithText("This app was developed with the assistance of AI (model: Test Model).").assertExists()
        compose.onNodeWithText("See all changes").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithText("What's new in 1.1.0").assertDoesNotExist()
        compose.onNodeWithText("ChangelogRoute").assertIsDisplayed()
        assertEquals(2, settings.state.value.lastSeenVersionCode)
    }

    @Test
    fun firstInstallRecordsTheVersionSilently() {
        val settings = FakeSettingsRepository(AppSettings(onboardingCompleted = true, lastSeenVersionCode = 0))
        launch(TestAppGraph(settings, FakeChangelog(listOf(changelogEntry(1, "1.0.0", "Hello" to "สวัสดี")))))
        compose.onNodeWithText("AlarmsRoute").assertIsDisplayed()
        compose.waitForIdle()
        compose.onNodeWithText("Hello").assertDoesNotExist()
        assertEquals(1, settings.state.value.lastSeenVersionCode)
    }

    @Test
    fun whatsNewTurnedOffIsNeverShown() {
        val settings = FakeSettingsRepository(onboarded.copy(showWhatsNewOnUpdate = false))
        launch(TestAppGraph(settings, FakeChangelog(listOf(changelogEntry(2, "1.1.0", "Hidden" to "ซ่อน"))), versionCode = 2))
        compose.onNodeWithText("AlarmsRoute").assertIsDisplayed()
        compose.onNodeWithText("Hidden").assertDoesNotExist()
        assertEquals(2, settings.state.value.lastSeenVersionCode)
    }

    @Test
    fun firstRunSwitchesToEnglishAndMirrorsTheLanguage() {
        val settings = FakeSettingsRepository(AppSettings(region = RegionSettings(language = AppLanguage.THAI)))
        launch(TestAppGraph(settings))
        compose.waitForIdle()
        assertEquals("en", app.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags())
        assertEquals(AppLanguage.ENGLISH, settings.state.value.region.language)
    }

    @Test
    fun theUsersLanguageIsMirroredIntoSettings() {
        app.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("th")
        val settings = FakeSettingsRepository(onboarded)
        launch(TestAppGraph(settings))
        compose.waitForIdle()
        assertEquals(AppLanguage.THAI, settings.state.value.region.language)
    }
}
