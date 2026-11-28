package io.github.tweeter042.alarmclock.core.alarm.notify

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.tweeter042.alarmclock.core.alarm.testing.TestClock
import io.github.tweeter042.alarmclock.core.alarm.testing.TestRingUi
import io.github.tweeter042.alarmclock.core.model.alarm.ActiveRing
import io.github.tweeter042.alarmclock.core.model.alarm.Alarm
import io.github.tweeter042.alarmclock.core.model.alarm.MissionConfig
import io.github.tweeter042.alarmclock.core.model.alarm.MissionType
import io.github.tweeter042.alarmclock.core.model.alarm.RingKind
import io.github.tweeter042.alarmclock.core.model.alarm.RingState
import io.github.tweeter042.alarmclock.core.model.alarm.SnoozeConfig
import io.github.tweeter042.alarmclock.core.model.alarm.SnoozeMode
import io.github.tweeter042.alarmclock.core.model.settings.AppSettings
import io.github.tweeter042.alarmclock.core.model.settings.NumeralStyle
import io.github.tweeter042.alarmclock.core.model.settings.RegionSettings
import io.github.tweeter042.alarmclock.core.model.settings.SamsungSettings
import io.github.tweeter042.alarmclock.core.model.settings.TimeFormatPref
import io.github.tweeter042.alarmclock.core.model.theme.ThemeCatalog
import io.github.tweeter042.alarmclock.core.model.theme.ThemeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class NotificationTextTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val clock = TestClock(0L, ZoneId.of("Asia/Bangkok")).also { it.now = it.at(2026, 11, 27, 6, 30) }
    private val notifier get() = EngineNotifier(context, TestRingUi, clock)

    private fun ring(alarm: Alarm = Alarm(label = "Gym"), kind: RingKind = RingKind.ALARM, state: RingState = RingState.RINGING) =
        ActiveRing("ring-1", kind, 1, alarm, clock.now, clock.now, state)

    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE).toString()
    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT).toString()

    @Test fun timesFollowTheRegionSettings() {
        val h24 = EngineFormat(context, AppSettings(region = RegionSettings(timeFormat = TimeFormatPref.H24)), clock.zoneId)
        assertEquals("06:30", h24.time(clock.now))
        val thai = EngineFormat(context, AppSettings(region = RegionSettings(timeFormat = TimeFormatPref.H24, numerals = NumeralStyle.THAI)), clock.zoneId)
        assertEquals("๐๖:๓๐", thai.time(clock.now))
        val h12 = EngineFormat(context, AppSettings(region = RegionSettings(timeFormat = TimeFormatPref.H12)), clock.zoneId)
        assertTrue(h12.time(clock.now).startsWith("6:30"))
        assertEquals("06:30", h24.dayAndTime(clock.now, clock.now))
        assertTrue("another day shows the weekday", h24.dayAndTime(clock.at(2026, 11, 30, 7, 0), clock.now).endsWith("07:00"))
        assertNotEquals("07:00", h24.dayAndTime(clock.at(2026, 11, 30, 7, 0), clock.now))
    }

    @Test fun theRingNotificationIsAFullScreenAlarm() {
        val settings = AppSettings(region = RegionSettings(timeFormat = TimeFormatPref.H24))
        val notification = notifier.ring(ring(), settings)
        assertEquals("Gym", notification.title())
        assertEquals("Ringing · 06:30", notification.text())
        assertEquals(Notification.CATEGORY_ALARM, notification.category)
        assertEquals(EngineChannels.ALARMS, notification.channelId)
        assertEquals(Notification.VISIBILITY_PUBLIC, notification.visibility)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals("ring-1", shadowOf(notification.fullScreenIntent).savedIntent.getStringExtra(TestRingUi.EXTRA_RING_ID))
        assertEquals(listOf("Snooze", "Dismiss"), notification.actions.map { it.title.toString() })
    }

    @Test fun theNotificationIsTintedWithTheThemeAccent() {
        val tinted = notifier.ring(ring(), AppSettings())
        val accent = ThemeCatalog.palette(ThemeId.MINIMAL, null).let { listOf(it.light.accent.toInt(), it.dark.accent.toInt()) }
        assertTrue(tinted.color in accent)
        val plain = notifier.ring(ring(), AppSettings(samsung = SamsungSettings(edgeLightingThemeColor = false)))
        assertEquals(Notification.COLOR_DEFAULT, plain.color)
    }

    @Test fun actionsAdaptToMissionsTimersAndTheAwakeCheck() {
        val missions = listOf(MissionConfig(MissionType.MATH))
        val strict = notifier.ring(ring(Alarm(missions = missions, snooze = SnoozeConfig(mode = SnoozeMode.MISSION_TO_SNOOZE))), AppSettings())
        assertTrue("snooze needs the missions: ring screen", shadowOf(strict.actions[0].actionIntent).isActivity)
        assertTrue("dismiss needs the missions: ring screen", shadowOf(strict.actions[1].actionIntent).isActivity)

        val noSnooze = notifier.ring(ring(Alarm(snooze = SnoozeConfig(enabled = false))), AppSettings())
        assertEquals(listOf("Dismiss"), noSnooze.actions.map { it.title.toString() })

        val timer = notifier.ring(ring(Alarm(label = ""), kind = RingKind.TIMER), AppSettings())
        assertEquals("Timer", timer.title())
        assertEquals("Time's up", timer.text())
        assertEquals(EngineChannels.TIMERS, timer.channelId)
        assertEquals(listOf("+1 minute", "Stop"), timer.actions.map { it.title.toString() })

        val awake = notifier.ring(ring(state = RingState.AWAKE_CHECK).copy(awakePromptShowing = true), AppSettings())
        assertEquals("Are you awake?", awake.title())
        assertEquals(EngineChannels.AWAKE, awake.channelId)
        assertEquals(listOf("I'm awake"), awake.actions.map { it.title.toString() })
    }

    @Test
    @Config(qualifiers = "th")
    fun everythingIsInThaiWhenTheAppIsInThai() {
        val settings = AppSettings(region = RegionSettings(timeFormat = TimeFormatPref.H24, numerals = NumeralStyle.THAI))
        val notification = notifier.ring(ring(Alarm(label = "")), settings)
        assertEquals("นาฬิกาปลุก", notification.title())
        assertEquals("กำลังดัง · ๐๖:๓๐", notification.text())
        assertEquals(listOf("เลื่อนปลุก", "ปิดปลุก"), notification.actions.map { it.title.toString() })
        val guard = notifier.guard(null, settings)
        assertEquals("ตัวป้องกันการปลุกทำงานอยู่", guard.title())
        assertEquals("ยังไม่ได้ตั้งปลุก", guard.text())
    }
}
