package io.github.tweeter042.alarmclock.core.ui.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.tweeter042.alarmclock.core.designsystem.component.AppTopBar
import io.github.tweeter042.alarmclock.core.designsystem.theme.AlarmClockTheme
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import io.github.tweeter042.alarmclock.core.model.alarm.NextAlarm
import io.github.tweeter042.alarmclock.core.model.alarm.RingKind
import io.github.tweeter042.alarmclock.core.model.settings.AccessibilitySettings
import io.github.tweeter042.alarmclock.core.model.settings.AppSettings
import io.github.tweeter042.alarmclock.core.model.settings.AppearanceSettings
import io.github.tweeter042.alarmclock.core.model.settings.NumeralStyle
import io.github.tweeter042.alarmclock.core.model.settings.RegionSettings
import io.github.tweeter042.alarmclock.core.model.settings.TimeFormatPref
import io.github.tweeter042.alarmclock.core.model.settings.YearStyle
import io.github.tweeter042.alarmclock.core.model.theme.ThemeId
import io.github.tweeter042.alarmclock.core.ui.ProvideAppEnvironment
import io.github.tweeter042.alarmclock.core.ui.TestAppGraph
import io.github.tweeter042.alarmclock.core.ui.component.ErrorState
import io.github.tweeter042.alarmclock.core.ui.component.LoadingState
import io.github.tweeter042.alarmclock.core.ui.component.NextAlarmBanner
import io.github.tweeter042.alarmclock.core.ui.component.TopLevelActions
import io.github.tweeter042.alarmclock.core.ui.permission.NotificationPermissionCardContent
import io.github.tweeter042.alarmclock.core.ui.picker.DatePickerSheetContent
import io.github.tweeter042.alarmclock.core.ui.picker.DurationDigits
import io.github.tweeter042.alarmclock.core.ui.picker.DurationKeypad
import io.github.tweeter042.alarmclock.core.ui.picker.DurationPicker
import io.github.tweeter042.alarmclock.core.ui.picker.TimeInputMode
import io.github.tweeter042.alarmclock.core.ui.picker.TimePickerSheetContent
import io.github.tweeter042.alarmclock.core.ui.picker.rememberAppDatePickerState
import io.github.tweeter042.alarmclock.core.ui.time.FixedUiClock
import io.github.tweeter042.alarmclock.core.ui.time.LocalUiClock
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.util.TimeZone

private const val OUT = "build/screenshots-out"
private val Bangkok: ZoneId = ZoneId.of("Asia/Bangkok")

/** Saturday 28 November 2026, 23:36 in Bangkok. */
private val NOW: Long = ZonedDateTime.of(2026, 11, 28, 23, 36, 0, 0, Bangkok).toInstant().toEpochMilli()

/**
 * The shared pickers and components in Minimal, Brutal and Retro LCD (light and dark), plus Thai
 * variants with Thai numerals and the Buddhist year. PNGs go to build/screenshots-out/.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class PickerScreenshotTest(private val themeId: ThemeId) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun themes(): List<Array<Any>> = listOf(ThemeId.MINIMAL, ThemeId.BRUTAL, ThemeId.RETRO_LCD).map { arrayOf(it) }
    }

    private val slug = themeId.name.lowercase(Locale.ROOT)

    @Before
    fun zone() {
        TimeZone.setDefault(TimeZone.getTimeZone(Bangkok))
    }

    private fun capture(
        name: String,
        dark: Boolean = false,
        region: RegionSettings = RegionSettings(timeFormat = TimeFormatPref.H12),
        content: @Composable () -> Unit,
    ) {
        captureRoboImage("$OUT/$name.png") {
            val settings = AppSettings(
                appearance = AppearanceSettings(themeId = themeId),
                region = region,
                // Static frames: reduce motion stops infinite animations (cursor, blinking colon).
                accessibility = AccessibilitySettings(reduceMotion = true),
            )
            ProvideAppEnvironment(TestAppGraph(), settings) {
                CompositionLocalProvider(LocalUiClock provides FixedUiClock(NOW, Bangkok)) {
                    AlarmClockTheme(settings.appearance, settings.accessibility, forceDark = dark) {
                        Box(Modifier.fillMaxWidth().background(AppTheme.colors.surface).padding(vertical = 20.dp)) { content() }
                    }
                }
            }
        }
    }

    @Composable
    private fun TimeSheet(mode: TimeInputMode, hour: Int = 6, minute: Int = 45, supporting: String? = "Rings in 7 h 9 min") {
        TimePickerSheetContent(
            title = if (mode == TimeInputMode.Wheel) "Set time" else "Type the time",
            hour = hour,
            minute = minute,
            onTimeChange = { _, _ -> },
            mode = mode,
            onModeChange = {},
            use24h = false,
            minuteStep = 1,
            valid = true,
            onValidChange = {},
            supportingText = supporting,
            confirmLabel = "Done",
            onConfirm = {},
            onCancel = {},
            requestKeyboardFocus = false,
        )
    }

    @Test
    fun timeWheel() {
        capture("picker_time_wheel_$slug") { TimeSheet(TimeInputMode.Wheel) }
        capture("picker_time_wheel_${slug}_dark", dark = true) { TimeSheet(TimeInputMode.Wheel) }
    }

    @Test
    fun timeWheel24h() {
        capture("picker_time_wheel_24h_$slug", region = RegionSettings(timeFormat = TimeFormatPref.H24)) {
            TimePickerSheetContent(
                title = "Set time", hour = 21, minute = 5, onTimeChange = { _, _ -> }, mode = TimeInputMode.Wheel,
                onModeChange = {}, use24h = true, minuteStep = 5, valid = true, onValidChange = {}, supportingText = null,
                confirmLabel = "Done", onConfirm = {}, onCancel = {},
            )
        }
    }

    @Test
    fun timeKeyboard() {
        capture("picker_time_keyboard_$slug") { TimeSheet(TimeInputMode.Keyboard, supporting = null) }
    }

    @Test
    fun duration() {
        capture("picker_duration_$slug") {
            Column(verticalArrangement = Arrangement.spacedBy(28.dp), modifier = Modifier.padding(horizontal = 24.dp)) {
                DurationPicker(durationMillis = (1 * 3600 + 25 * 60 + 30) * 1000L, onDurationChange = {})
                DurationKeypad(digits = DurationDigits("530"), onDigitsChange = {})
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun date() {
        capture("picker_date_$slug") {
            val state = rememberAppDatePickerState(LocalDate.of(2026, 12, 2), minDate = LocalDate.of(2026, 11, 28))
            DatePickerSheetContent(state = state, title = "Choose a date", confirmLabel = "Done", onConfirm = {}, onCancel = {})
        }
    }

    @Test
    fun components() {
        capture("components_$slug") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AppTopBar(title = "Alarms", actions = { TopLevelActions(onOpenSettings = {}) })
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    NextAlarmBanner(NextAlarm(RingKind.ALARM, 1, NOW + 7 * 3_600_000L + 24 * 60_000L, "Morning run"))
                    NextAlarmBanner(null)
                    NotificationPermissionCardContent(
                        title = "Notifications are off",
                        body = "Turn them on so alarms can appear on the lock screen and you can see timers, upcoming alarms and missed-alarm warnings.",
                        onTurnOn = {},
                    )
                }
            }
        }
        capture("states_$slug") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                LoadingState()
                ErrorState(onRetry = {})
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    @Config(qualifiers = "+th")
    fun thai() {
        val thai = RegionSettings(timeFormat = TimeFormatPref.H12, numerals = NumeralStyle.THAI, yearStyle = YearStyle.BUDDHIST)
        capture("picker_time_wheel_th_$slug", region = thai) {
            TimePickerSheetContent(
                title = "ตั้งเวลา", hour = 6, minute = 45, onTimeChange = { _, _ -> }, mode = TimeInputMode.Wheel, onModeChange = {},
                use24h = false, minuteStep = 1, valid = true, onValidChange = {}, supportingText = "จะดังในอีก ๗ ชม. ๙ นาที",
                confirmLabel = "เสร็จสิ้น", onConfirm = {}, onCancel = {},
            )
        }
        capture("picker_date_th_$slug", region = thai) {
            val state = rememberAppDatePickerState(LocalDate.of(2026, 12, 2))
            DatePickerSheetContent(state = state, title = "เลือกวันที่", confirmLabel = "เสร็จสิ้น", onConfirm = {}, onCancel = {})
        }
        capture("picker_duration_th_$slug", region = thai) {
            Column(verticalArrangement = Arrangement.spacedBy(28.dp), modifier = Modifier.padding(horizontal = 24.dp)) {
                DurationPicker(durationMillis = (25 * 60 + 30) * 1000L, onDurationChange = {}, showHours = false)
                DurationKeypad(digits = DurationDigits("1500"), onDigitsChange = {})
            }
        }
        capture("components_th_$slug", region = thai) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                NextAlarmBanner(NextAlarm(RingKind.NAP, 1, NOW + 25 * 60_000L, ""))
                NextAlarmBanner(NextAlarm(RingKind.ALARM, 1, NOW + 3 * 24 * 3_600_000L, "วิ่งตอนเช้า"))
                ErrorState(onRetry = {})
            }
        }
    }
}
