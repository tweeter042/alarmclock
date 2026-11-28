package io.github.tweeter042.alarmclock.core.ui.time

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.tweeter042.alarmclock.core.designsystem.theme.AlarmClockTheme
import io.github.tweeter042.alarmclock.core.model.alarm.NextAlarm
import io.github.tweeter042.alarmclock.core.model.alarm.RingKind
import io.github.tweeter042.alarmclock.core.model.settings.AccessibilitySettings
import io.github.tweeter042.alarmclock.core.model.settings.AppSettings
import io.github.tweeter042.alarmclock.core.model.settings.AppearanceSettings
import io.github.tweeter042.alarmclock.core.model.settings.RegionSettings
import io.github.tweeter042.alarmclock.core.model.settings.TimeFormatPref
import io.github.tweeter042.alarmclock.core.ui.ProvideAppEnvironment
import io.github.tweeter042.alarmclock.core.ui.TestAppGraph
import io.github.tweeter042.alarmclock.core.ui.component.NextAlarmBanner
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TimeComposablesTest {

    @get:Rule
    val compose = createComposeRule()

    private val bangkok = ZoneId.of("Asia/Bangkok")

    /** Saturday 28 November 2026, 23:36:30 in Bangkok. */
    private val now = ZonedDateTime.of(2026, 11, 28, 23, 36, 30, 0, bangkok).toInstant().toEpochMilli()
    private val sevenTomorrow = ZonedDateTime.of(2026, 11, 29, 7, 0, 0, 0, bangkok).toInstant().toEpochMilli()

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            val settings = AppSettings(region = RegionSettings(timeFormat = TimeFormatPref.H12))
            ProvideAppEnvironment(TestAppGraph(), settings) {
                CompositionLocalProvider(LocalUiClock provides FixedUiClock(now, bangkok)) {
                    AlarmClockTheme(AppearanceSettings(), AccessibilitySettings(reduceMotion = true), content = content)
                }
            }
        }
    }

    @Test
    fun countdownStyles() {
        show {
            CountdownText(targetAt = sevenTomorrow, capitalize = true)
            CountdownText(targetAt = sevenTomorrow, style = CountdownStyle.Duration)
            CountdownText(targetAt = now + 5 * 60_000L, style = CountdownStyle.Digits)
        }
        compose.onNodeWithContentDescription("in 7 hours and 24 minutes").assertExists()
        compose.onNodeWithContentDescription("7 hours and 24 minutes").assertExists()
        compose.onNodeWithContentDescription("5 minutes").assertExists()
        compose.onNodeWithText("In 7 h 24 min", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("7 h 24 min", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("05:00", useUnmergedTree = true).assertExists()
    }

    @Test
    fun countdownReportsTheEnd() {
        var finished = 0
        show { CountdownText(targetAt = now - 1_000L, onFinished = { finished++ }) }
        compose.waitForIdle()
        assertEquals(1, finished)
        compose.onNodeWithContentDescription("in less than a minute").assertExists()
    }

    @Test
    fun nowComesFromTheProvidedClock() {
        show { Text(rememberNow(60_000L).toString()) }
        compose.onNodeWithText(now.toString()).assertExists()
    }

    @Test
    fun nextAlarmBannerReadsWholeWords() {
        show { NextAlarmBanner(NextAlarm(RingKind.ALARM, 1, sevenTomorrow, "Morning run")) }
        compose.onNodeWithText("Tomorrow at 7:00 AM", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Rings in 7 h 24 min", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Next alarm, Tomorrow at 7:00 AM, Rings in 7 hours and 24 minutes, Morning run").assertExists()
    }

    @Test
    fun emptyBanner() {
        show { NextAlarmBanner(null) }
        compose.onNodeWithText("No alarms on", useUnmergedTree = true).assertExists()
    }
}
