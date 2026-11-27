package io.github.tweeter042.alarmclock.core.designsystem.clock

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.tweeter042.alarmclock.core.designsystem.component.ThemedBackground
import io.github.tweeter042.alarmclock.core.designsystem.theme.AlarmClockTheme
import io.github.tweeter042.alarmclock.core.model.settings.AppearanceSettings
import io.github.tweeter042.alarmclock.core.model.theme.ClockFace
import io.github.tweeter042.alarmclock.core.model.theme.ThemeId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalTime

/**
 * Runs every face with animations ON (blinking colons, cursor, flips, neon flicker, ticking
 * seconds, animated background) on a manually driven clock, and checks the time updates.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AnimatedClockTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun facesAnimateAndFollowTheTime() {
        rule.mainClock.autoAdvance = false
        var time by mutableStateOf(LocalTime.of(7, 29, 58))
        rule.setContent {
            AlarmClockTheme(appearance = AppearanceSettings(themeId = ThemeId.NEON)) {
                ThemedBackground(animated = true) {
                    Column {
                        ClockFace.entries.forEach { face ->
                            ClockDisplay(time = time, use24h = true, showSeconds = true, faceOverride = face, size = ClockSize.Compact)
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(700)
        time = LocalTime.of(7, 30, 0)
        rule.mainClock.advanceTimeBy(16)
        // Mid-flip and mid-tick frames must draw without errors.
        rule.mainClock.advanceTimeBy(300)
        rule.mainClock.advanceTimeBy(1200)
        rule.onAllNodesWithContentDescription("07:30:00", substring = true, useUnmergedTree = true)
            .assertCountEquals(ClockFace.entries.size)
    }
}
