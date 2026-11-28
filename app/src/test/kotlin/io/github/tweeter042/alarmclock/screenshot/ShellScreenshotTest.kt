package io.github.tweeter042.alarmclock.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.tweeter042.alarmclock.FakeSettingsRepository
import io.github.tweeter042.alarmclock.TestAppGraph
import io.github.tweeter042.alarmclock.TestApplication
import io.github.tweeter042.alarmclock.changelogEntry
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import io.github.tweeter042.alarmclock.core.model.settings.AccessibilitySettings
import io.github.tweeter042.alarmclock.core.model.settings.AppSettings
import io.github.tweeter042.alarmclock.core.model.settings.AppearanceSettings
import io.github.tweeter042.alarmclock.core.model.settings.DarkModePref
import io.github.tweeter042.alarmclock.core.model.theme.ThemeId
import io.github.tweeter042.alarmclock.core.ui.AlarmClockAppRoot
import io.github.tweeter042.alarmclock.shell.AppShell
import io.github.tweeter042.alarmclock.whatsnew.WhatsNewContent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

private const val OUT = "build/screenshots-out"

/** The app shell (bottom bar) and the What's New sheet content in Minimal, Brutal and Retro LCD. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = TestApplication::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ShellScreenshotTest(private val themeId: ThemeId) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun themes(): List<Array<Any>> = listOf(ThemeId.MINIMAL, ThemeId.BRUTAL, ThemeId.RETRO_LCD).map { arrayOf(it) }
    }

    private val slug = themeId.name.lowercase(Locale.ROOT)

    private fun graph(dark: Boolean = false) = TestAppGraph(
        FakeSettingsRepository(
            AppSettings(
                appearance = AppearanceSettings(themeId = themeId, darkMode = if (dark) DarkModePref.DARK else DarkModePref.LIGHT),
                accessibility = AccessibilitySettings(reduceMotion = true),
                onboardingCompleted = true,
                lastSeenVersionCode = 1,
            ),
        ),
    )

    @Test
    fun shell() {
        captureRoboImage("$OUT/shell_$slug.png") {
            AlarmClockAppRoot(graph()) { AppShell(pendingDestination = null, onPendingHandled = {}) }
        }
        captureRoboImage("$OUT/shell_${slug}_dark.png") {
            AlarmClockAppRoot(graph(dark = true)) { AppShell(pendingDestination = null, onPendingHandled = {}) }
        }
    }

    @Test
    @Config(qualifiers = "+th")
    fun shellThai() {
        captureRoboImage("$OUT/shell_th_$slug.png") {
            AlarmClockAppRoot(graph()) { AppShell(pendingDestination = null, onPendingHandled = {}) }
        }
    }

    @Test
    fun whatsNew() {
        val entry = changelogEntry(
            2, "1.1.0",
            "A bigger snooze button that is easier to hit half asleep." to "ปุ่มเลื่อนปลุกใหญ่ขึ้น กดง่ายแม้ยังงัวเงีย",
            "Timers keep counting after a restart." to "ตัวจับเวลานับต่อได้แม้รีสตาร์ตเครื่อง",
            "Thai holidays for 2027." to "วันหยุดไทยปี 2570",
        )
        captureRoboImage("$OUT/whats_new_$slug.png") {
            AlarmClockAppRoot(graph()) {
                Box(Modifier.fillMaxWidth().background(AppTheme.colors.surface).padding(vertical = 24.dp)) {
                    WhatsNewContent(entry = entry, aiModelName = "", onGotIt = {}, onSeeAllChanges = {})
                }
            }
        }
    }

    @Test
    @Config(qualifiers = "+th")
    fun whatsNewThai() {
        val entry = changelogEntry(2, "1.1.0", "A bigger snooze button." to "ปุ่มเลื่อนปลุกใหญ่ขึ้น กดง่ายแม้ยังงัวเงีย")
        captureRoboImage("$OUT/whats_new_th_$slug.png") {
            AlarmClockAppRoot(graph()) {
                Box(Modifier.fillMaxWidth().background(AppTheme.colors.surface).padding(vertical = 24.dp)) {
                    WhatsNewContent(entry = entry, aiModelName = "", onGotIt = {}, onSeeAllChanges = {})
                }
            }
        }
    }
}
