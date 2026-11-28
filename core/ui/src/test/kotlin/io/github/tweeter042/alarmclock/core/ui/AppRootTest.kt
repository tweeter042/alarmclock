package io.github.tweeter042.alarmclock.core.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import io.github.tweeter042.alarmclock.core.model.AppGraph
import io.github.tweeter042.alarmclock.core.model.alarm.RingAppearance
import io.github.tweeter042.alarmclock.core.model.settings.AppSettings
import io.github.tweeter042.alarmclock.core.model.settings.AppearanceSettings
import io.github.tweeter042.alarmclock.core.model.settings.RegionSettings
import io.github.tweeter042.alarmclock.core.model.settings.TimeFormatPref
import io.github.tweeter042.alarmclock.core.model.theme.ThemeId
import io.github.tweeter042.alarmclock.core.ui.format.LocalUiFormatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AppRootTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun providesGraphSettingsRegionFormattersAndTheme() {
        val settings = AppSettings(
            appearance = AppearanceSettings(themeId = ThemeId.BRUTAL),
            region = RegionSettings(timeFormat = TimeFormatPref.H12),
            onboardingCompleted = true,
        )
        val graph = TestAppGraph(FakeSettingsRepository(settings))
        var seenGraph: AppGraph? = null
        var seenSettings: AppSettings? = null
        var seenRegion: RegionSettings? = null
        var seenTheme: ThemeId? = null
        var use24h: Boolean? = null
        compose.setContent {
            AlarmClockAppRoot(graph) {
                seenGraph = LocalAppGraph.current
                seenSettings = LocalAppSettings.current
                seenRegion = LocalRegionSettings.current
                seenTheme = AppTheme.themeId
                use24h = LocalUiFormatters.current?.time?.use24h
                Text("content")
            }
        }
        compose.onNodeWithText("content").assertExists()
        assertSame(graph, seenGraph)
        assertEquals(settings, seenSettings)
        assertEquals(settings.region, seenRegion)
        assertEquals(ThemeId.BRUTAL, seenTheme)
        assertEquals(false, use24h)
    }

    @Test
    fun followsSettingsChanges() {
        val repository = FakeSettingsRepository(AppSettings())
        var seenTheme: ThemeId? = null
        compose.setContent {
            AlarmClockAppRoot(TestAppGraph(repository)) { seenTheme = AppTheme.themeId }
        }
        compose.waitForIdle()
        assertEquals(ThemeId.MINIMAL, seenTheme)
        repository.state.value = AppSettings(appearance = AppearanceSettings(themeId = ThemeId.RETRO_LCD))
        compose.waitForIdle()
        assertEquals(ThemeId.RETRO_LCD, seenTheme)
    }

    @Test
    fun waitsForSettingsThenFallsBackToDefaults() {
        val silent = FakeSettingsRepository(initial = null)
        var composed = false
        var theme: ThemeId? = null
        compose.setContent {
            AlarmClockAppRoot(TestAppGraph(silent), settingsTimeoutMillis = 200) {
                composed = true
                theme = AppTheme.themeId
                Text("content")
            }
        }
        compose.waitForIdle()
        assertFalse("nothing is composed before the settings arrive", composed)
        ShadowLooper.idleMainLooper(300, TimeUnit.MILLISECONDS)
        compose.waitForIdle()
        compose.onNodeWithText("content").assertExists()
        assertEquals(ThemeId.MINIMAL, theme)
        // The real values still win when they arrive late.
        silent.state.value = AppSettings(appearance = AppearanceSettings(themeId = ThemeId.NEON))
        compose.waitForIdle()
        assertNotNull(theme)
        assertEquals(ThemeId.NEON, theme)
    }

    @Test
    fun ringRootUsesTheAlarmAppearance() {
        val settings = AppSettings(appearance = AppearanceSettings(themeId = ThemeId.MINIMAL, ringThemeId = ThemeId.FLIP_CLOCK))
        var seenTheme: ThemeId? = null
        compose.setContent {
            AlarmClockRingRoot(TestAppGraph(FakeSettingsRepository(settings)), alarmAppearance = RingAppearance(themeId = ThemeId.TERMINAL)) {
                seenTheme = AppTheme.themeId
            }
        }
        compose.waitForIdle()
        assertEquals(ThemeId.TERMINAL, seenTheme)
    }

    @Test
    fun ringThemePrecedence() {
        val app = AppearanceSettings(themeId = ThemeId.MINIMAL)
        val withRingTheme = app.copy(ringThemeId = ThemeId.FLIP_CLOCK, ringPaletteId = "night")
        // Nothing special: the app theme.
        assertEquals(ThemeOverrides(), ThemeOverrides.forRing(app, alarm = null))
        assertEquals(ThemeOverrides(), ThemeOverrides.forRing(app, RingAppearance()))
        // Settings › ring screen theme.
        assertEquals(ThemeOverrides(ThemeId.FLIP_CLOCK, "night"), ThemeOverrides.forRing(withRingTheme, RingAppearance()))
        // The alarm's own theme wins, with its own palette (or the remembered one when null).
        assertEquals(ThemeOverrides(ThemeId.NEON, "pink"), ThemeOverrides.forRing(withRingTheme, RingAppearance(ThemeId.NEON, "pink")))
        assertEquals(ThemeOverrides(ThemeId.NEON, null), ThemeOverrides.forRing(withRingTheme, RingAppearance(themeId = ThemeId.NEON)))
        // A palette alone applies to whichever theme wins.
        assertEquals(ThemeOverrides(ThemeId.FLIP_CLOCK, "amber"), ThemeOverrides.forRing(withRingTheme, RingAppearance(paletteId = "amber")))
        assertEquals(ThemeOverrides(null, "amber"), ThemeOverrides.forRing(app, RingAppearance(paletteId = "amber")))
        assertEquals(ThemeOverrides(ThemeId.NEON, null, forceDark = true), ThemeOverrides.forRing(app, RingAppearance(ThemeId.NEON), forceDark = true))
    }
}
