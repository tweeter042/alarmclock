package io.github.tweeter042.alarmclock.core.ui.locale

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.tweeter042.alarmclock.core.model.settings.AppLanguage
import io.github.tweeter042.alarmclock.core.model.settings.AppSettings
import io.github.tweeter042.alarmclock.core.model.settings.RegionSettings
import io.github.tweeter042.alarmclock.core.ui.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AppLocalesTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val localeManager: LocaleManager get() = context.getSystemService(LocaleManager::class.java)

    @Before
    fun followSystem() {
        localeManager.applicationLocales = LocaleList.getEmptyLocaleList()
    }

    @Test
    fun setLanguageStoresThePerAppLocale() {
        assertTrue(AppLocales.followsSystem(context))
        assertNull(AppLocales.selectedLanguage(context))
        AppLocales.setLanguage(context, AppLanguage.THAI)
        assertEquals("th", localeManager.applicationLocales.toLanguageTags())
        assertEquals(AppLanguage.THAI, AppLocales.selectedLanguage(context))
        assertEquals(AppLanguage.THAI, AppLocales.currentLanguage(context))
        assertFalse(AppLocales.followsSystem(context))
        AppLocales.setLanguage(context, AppLanguage.ENGLISH)
        assertEquals(AppLanguage.ENGLISH, AppLocales.currentLanguage(context))
    }

    @Test
    fun currentLanguageFollowsTheShownResourcesWhenNoneIsSet() {
        assertEquals(AppLanguage.ENGLISH, AppLocales.currentLanguage(context))
    }

    @Test
    @Config(qualifiers = "th")
    fun thaiSystemLanguageIsDetected() {
        assertEquals(AppLanguage.THAI, AppLocales.currentLanguage(context))
    }

    @Test
    fun firstRunSetsEnglishAndMirrorsIt() = runTest {
        val settings = FakeSettingsRepository(AppSettings(region = RegionSettings(language = AppLanguage.THAI)))
        assertTrue(AppLocales.syncWithSettings(context, settings))
        assertEquals("en", localeManager.applicationLocales.toLanguageTags())
        assertEquals(AppLanguage.ENGLISH, settings.current().region.language)
    }

    @Test
    fun afterOnboardingTheUsersChoiceIsKeptAndMirrored() = runTest {
        localeManager.applicationLocales = LocaleList.forLanguageTags("th")
        val settings = FakeSettingsRepository(AppSettings(onboardingCompleted = true))
        assertFalse(AppLocales.syncWithSettings(context, settings))
        assertEquals("th", localeManager.applicationLocales.toLanguageTags())
        assertEquals(AppLanguage.THAI, settings.current().region.language)
    }

    @Test
    fun afterOnboardingFollowingTheSystemIsRespected() = runTest {
        val settings = FakeSettingsRepository(AppSettings(onboardingCompleted = true))
        assertFalse(AppLocales.syncWithSettings(context, settings))
        assertTrue(AppLocales.followsSystem(context))
        assertEquals(AppLanguage.ENGLISH, settings.current().region.language)
    }

    @Test
    fun firstRunDefaultBeforeAnySettingsExist() {
        assertFalse(AppLocales.applyFirstRunDefault(context, hasStoredSettings = true))
        assertTrue(AppLocales.followsSystem(context))
        assertTrue(AppLocales.applyFirstRunDefault(context, hasStoredSettings = false))
        assertEquals("en", localeManager.applicationLocales.toLanguageTags())
        // Never overrides a language the user picked.
        localeManager.applicationLocales = LocaleList.forLanguageTags("th")
        assertFalse(AppLocales.applyFirstRunDefault(context, hasStoredSettings = false))
        assertEquals("th", localeManager.applicationLocales.toLanguageTags())
    }
}
