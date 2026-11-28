package io.github.tweeter042.alarmclock.core.ui.picker

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.tweeter042.alarmclock.core.designsystem.theme.AlarmClockTheme
import io.github.tweeter042.alarmclock.core.model.settings.AccessibilitySettings
import io.github.tweeter042.alarmclock.core.model.settings.AppearanceSettings
import io.github.tweeter042.alarmclock.core.model.time.AlarmTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The sheets as feature modules use them (no experimental opt-in needed). Robolectric does not
 * route touches into dialog windows reliably, so buttons are pressed through semantics actions.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SheetCallbacksTest {

    @get:Rule
    val compose = createComposeRule()

    private fun themed(content: @Composable () -> Unit) {
        compose.setContent { AlarmClockTheme(AppearanceSettings(), AccessibilitySettings(reduceMotion = true), content = content) }
    }

    @Test
    fun timeSheetConfirmsTheTimeOnce() {
        var confirmed: AlarmTime? = null
        var confirmations = 0
        var dismissed = false
        themed {
            TimePickerSheet(
                initialTime = AlarmTime(6, 45),
                onConfirm = {
                    confirmed = it
                    confirmations++
                },
                onDismiss = { dismissed = true },
                use24h = true,
                supportingText = { "Rings at ${it.hour}:${it.minute}" },
            )
        }
        compose.onNodeWithText("Set time").assertExists()
        compose.onNodeWithText("Rings at 6:45").assertExists()
        compose.onNodeWithText("Done").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(AlarmTime(6, 45), confirmed)
        assertEquals(1, confirmations)
        assertFalse(dismissed)
    }

    @Test
    fun timeSheetCancelOnlyDismisses() {
        var confirmed: AlarmTime? = null
        var dismissed = false
        themed { TimePickerSheet(initialTime = AlarmTime(7, 0), onConfirm = { confirmed = it }, onDismiss = { dismissed = true }) }
        compose.onNodeWithText("Cancel").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertTrue(dismissed)
        assertNull(confirmed)
    }

    @Test
    fun timeSheetRoundsToTheMinuteStep() {
        var confirmed: AlarmTime? = null
        themed { TimePickerSheet(initialTime = AlarmTime(7, 13), onConfirm = { confirmed = it }, onDismiss = {}, minuteStep = 5) }
        compose.onNodeWithText("Done").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(AlarmTime(7, 10), confirmed)
    }

    @Test
    fun dateSheetConfirmsTheSelectedDay() {
        var confirmed: LocalDate? = null
        themed {
            DatePickerSheet(initialDate = LocalDate.of(2026, 12, 2), onConfirm = { confirmed = it }, onDismiss = {})
        }
        // The headline and the selected day's description both use the app's date format.
        compose.onAllNodesWithText("Wednesday, 2 December 2026").assertCountEquals(2)
        compose.onNodeWithText("Done").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(LocalDate.of(2026, 12, 2), confirmed)
    }

    @Test
    fun dateSheetNeedsASelection() {
        themed { DatePickerSheet(initialDate = null, onConfirm = {}, onDismiss = {}) }
        compose.onNodeWithText("No date chosen").assertExists()
        compose.onNodeWithText("Done").assertIsNotEnabled()
    }
}
