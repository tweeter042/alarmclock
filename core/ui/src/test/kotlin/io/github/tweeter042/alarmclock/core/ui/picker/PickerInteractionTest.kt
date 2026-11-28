package io.github.tweeter042.alarmclock.core.ui.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.tweeter042.alarmclock.core.designsystem.theme.AlarmClockTheme
import io.github.tweeter042.alarmclock.core.model.settings.AccessibilitySettings
import io.github.tweeter042.alarmclock.core.model.settings.AppearanceSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PickerInteractionTest {

    @get:Rule
    val compose = createComposeRule()

    private fun themed(content: @Composable () -> Unit) {
        compose.setContent {
            AlarmClockTheme(AppearanceSettings(), AccessibilitySettings(reduceMotion = true), content = content)
        }
    }

    @Test
    fun wheelsAreAdjustableWithAccessibilityActions() {
        var hour by mutableIntStateOf(7)
        var minute by mutableIntStateOf(30)
        themed {
            TimeWheelPicker(hour = hour, minute = minute, onTimeChange = { h, m -> hour = h; minute = m }, use24h = true)
        }
        compose.onNodeWithContentDescription("Hour").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "07"))
        compose.onNodeWithContentDescription("Hour").performSemanticsAction(SemanticsActions.SetProgress) { it(10f) }
        compose.waitForIdle()
        assertEquals(10, hour)
        compose.onNodeWithContentDescription("Hour").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "10"))
        compose.onNodeWithContentDescription("Minute").performSemanticsAction(SemanticsActions.SetProgress) { it(5f) }
        compose.waitForIdle()
        assertEquals(10 to 5, hour to minute)
    }

    @Test
    fun twelveHourWheelsReportTwentyFourHourValues() {
        var hour by mutableIntStateOf(7)
        themed {
            TimeWheelPicker(hour = hour, minute = 0, onTimeChange = { h, _ -> hour = h }, use24h = false)
        }
        compose.onNodeWithContentDescription("AM or PM").performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        compose.waitForIdle()
        assertEquals(19, hour)
        // Index 11 of the 12-hour wheel is "12": 12 PM is noon.
        compose.onNodeWithContentDescription("Hour").performSemanticsAction(SemanticsActions.SetProgress) { it(11f) }
        compose.waitForIdle()
        assertEquals(12, hour)
    }

    @Test
    fun minuteStepsOnlyOfferMultiples() {
        var minute by mutableIntStateOf(10)
        themed {
            TimeWheelPicker(hour = 6, minute = minute, onTimeChange = { _, m -> minute = m }, use24h = true, minuteStep = 5)
        }
        compose.onNodeWithContentDescription("Minute").performSemanticsAction(SemanticsActions.SetProgress) { it(9f) }
        compose.waitForIdle()
        assertEquals(45, minute)
    }

    @Test
    fun typingTheTime() {
        var time by mutableStateOf(7 to 30)
        var valid by mutableStateOf(true)
        themed {
            TimeKeyboardInput(
                hour = 7,
                minute = 30,
                onTimeChange = { h, m -> time = h to m },
                use24h = true,
                onValidChange = { valid = it },
                requestFocus = false,
            )
        }
        compose.onNodeWithText("07").performTextReplacement("21")
        compose.onNodeWithText("30").performTextReplacement("45")
        compose.waitForIdle()
        assertEquals(21 to 45, time)
        assertTrue(valid)
        compose.onNodeWithText("21").performTextReplacement("24")
        compose.waitForIdle()
        assertFalse(valid)
        compose.onNodeWithText("Enter an hour from 0 to 23").assertExists()
        assertEquals(21 to 45, time)
    }

    @Test
    fun typingATwelveHourTimeUsesAmPm() {
        var time by mutableStateOf(7 to 0)
        themed {
            TimeKeyboardInput(hour = 7, minute = 0, onTimeChange = { h, m -> time = h to m }, use24h = false, requestFocus = false)
        }
        compose.onNodeWithText("PM").performClick()
        compose.waitForIdle()
        assertEquals(19 to 0, time)
        compose.onNodeWithText("7").performTextReplacement("12")
        compose.waitForIdle()
        assertEquals(12 to 0, time)
    }

    @Test
    fun keypadTyping() {
        var digits by mutableStateOf(DurationDigits())
        themed { DurationKeypad(digits = digits, onDigitsChange = { digits = it }) }
        compose.onNodeWithText("1").performClick()
        compose.onNodeWithText("3").performClick()
        compose.onNodeWithText("0").performClick()
        compose.waitForIdle()
        assertEquals(DurationDigits("130"), digits)
        compose.onNodeWithContentDescription("Duration 1 minute and 30 seconds").assertExists()
        compose.onNodeWithText("00").performClick()
        compose.waitForIdle()
        assertEquals(5_400_000L, digits.toMillis())
        compose.onNodeWithContentDescription("Delete").performClick()
        compose.waitForIdle()
        assertEquals(DurationDigits("1300"), digits)
        compose.onNodeWithContentDescription("Delete").performTouchInput { longClick() }
        compose.waitForIdle()
        assertTrue(digits.isEmpty)
        compose.onNodeWithContentDescription("Delete").assertIsNotEnabled()
    }

    @Test
    fun durationWheelsReportMilliseconds() {
        var millis by mutableLongStateOf(90_000L)
        themed { DurationPicker(durationMillis = millis, onDurationChange = { millis = it }) }
        compose.onNodeWithContentDescription("Hours").performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
        compose.waitForIdle()
        assertEquals((2 * 3600 + 90) * 1000L, millis)
        compose.onNodeWithContentDescription("Seconds").performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        compose.waitForIdle()
        assertEquals((2 * 3600 + 60) * 1000L, millis)
    }
}
