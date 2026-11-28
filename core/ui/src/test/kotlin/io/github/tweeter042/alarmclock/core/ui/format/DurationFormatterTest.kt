package io.github.tweeter042.alarmclock.core.ui.format

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.tweeter042.alarmclock.core.model.settings.NumeralStyle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DurationFormatterTest {

    private val en = formatters("en").duration
    private val th = formatters("th").duration

    @Test
    fun untilRoundsUpToTheMinuteLikeTheClockOnScreen() {
        // 23:36:30 → 07:00 is 7 h 23 min 30 s; the user sees 23:36 and expects 7 h 24 min.
        assertEquals("in 7 h 24 min", en.until(7 * HOUR + 23 * MINUTE + 30_000))
        assertEquals("อีก 7 ชม. 24 นาที", th.until(7 * HOUR + 23 * MINUTE + 30_000))
        assertEquals("in 7 h 23 min", en.until(7 * HOUR + 23 * MINUTE))
        assertEquals("in 7 h", en.until(7 * HOUR))
        assertEquals("in 23 min", en.until(23 * MINUTE))
        assertEquals("in 1 min", en.until(MINUTE))
        assertEquals("อีก 1 นาที", th.until(MINUTE))
    }

    @Test
    fun lessThanAMinute() {
        assertEquals("in less than a minute", en.until(59_999))
        assertEquals("in less than a minute", en.until(0))
        assertEquals("อีกไม่ถึง 1 นาที", th.until(5_000))
        assertEquals("less than a minute", en.duration(20_000))
        assertEquals("ไม่ถึง 1 นาที", th.duration(20_000))
        assertEquals("just now", en.since(59_000))
        assertEquals("เมื่อสักครู่", th.since(1_000))
    }

    @Test
    fun daysDropTheMinutes() {
        assertEquals("in 1 day 3 h", en.until(DAY + 3 * HOUR + 10 * MINUTE))
        assertEquals("in 2 days", en.until(2 * DAY))
        assertEquals("in 1 day", en.until(DAY + 20 * MINUTE))
        assertEquals("อีก 2 วัน 5 ชม.", th.until(2 * DAY + 5 * HOUR))
    }

    @Test
    fun longFormForScreenReadersUsesPlurals() {
        assertEquals("in 7 hours and 24 minutes", en.until(7 * HOUR + 24 * MINUTE, DurationStyle.LONG))
        assertEquals("in 1 hour and 1 minute", en.until(HOUR + MINUTE, DurationStyle.LONG))
        assertEquals("in 1 day and 1 hour", en.until(DAY + HOUR, DurationStyle.LONG))
        assertEquals("in 2 days and 3 hours", en.until(2 * DAY + 3 * HOUR, DurationStyle.LONG))
        assertEquals("อีก 7 ชั่วโมง 24 นาที", th.until(7 * HOUR + 24 * MINUTE, DurationStyle.LONG))
        assertEquals("5 minutes ago", en.since(5 * MINUTE + 59_000, DurationStyle.LONG))
    }

    @Test
    fun sinceRoundsDown() {
        assertEquals("5 min ago", en.since(5 * MINUTE + 59_000))
        assertEquals("5 นาทีที่แล้ว", th.since(5 * MINUTE + 59_000))
        assertEquals("1 h 30 min ago", en.since(90 * MINUTE))
        assertEquals("3 days ago", en.since(3 * DAY + 5 * MINUTE))
    }

    @Test
    fun plainDurations() {
        assertEquals("1 h 30 min", en.duration(90 * MINUTE))
        assertEquals("1 ชม. 30 นาที", th.duration(90 * MINUTE))
        assertEquals("0 min", en.duration(0))
        assertEquals("30 min", en.duration(29 * MINUTE + 30_000))
        assertEquals("8 h", en.duration(7 * HOUR + 59 * MINUTE + 40_000))
        assertEquals("1 hour and 30 minutes", en.duration(90 * MINUTE, DurationStyle.LONG))
        assertEquals("29 min", en.duration(29 * MINUTE + 50_000, rounding = DurationRounding.DOWN))
    }

    @Test
    fun durationsWithSeconds() {
        assertEquals("1 min 30 s", en.durationWithSeconds(90_000))
        assertEquals("1 นาที 30 วินาที", th.durationWithSeconds(90_000))
        assertEquals("1 h 30 s", en.durationWithSeconds(HOUR + 30_000))
        assertEquals("45 s", en.durationWithSeconds(45_000))
        assertEquals("0 s", en.durationWithSeconds(0))
        assertEquals("1 hour, 1 minute and 30 seconds", en.durationWithSeconds(HOUR + MINUTE + 30_000, DurationStyle.LONG))
        assertEquals("1 second", en.durationWithSeconds(1_000, DurationStyle.LONG))
        assertEquals("1 ชั่วโมง 1 นาที 30 วินาที", th.durationWithSeconds(HOUR + MINUTE + 30_000, DurationStyle.LONG))
    }

    @Test
    fun countdownDigitsRoundUpToTheSecond() {
        assertEquals("05:00", en.countdown(5 * MINUTE))
        assertEquals("05:00", en.countdown(4 * MINUTE + 59_200))
        assertEquals("00:01", en.countdown(1))
        assertEquals("00:00", en.countdown(0))
        assertEquals("00:00", en.countdown(-5_000))
        assertEquals("1:05:09", en.countdown(HOUR + 5 * MINUTE + 9_000))
        assertEquals("25:00:00", en.countdown(25 * HOUR))
    }

    @Test
    fun thaiNumerals() {
        val thai = formatters("th", numerals = NumeralStyle.THAI).duration
        assertEquals("อีก ๗ ชม. ๒๔ นาที", thai.until(7 * HOUR + 24 * MINUTE))
        assertEquals("อีกไม่ถึง ๑ นาที", thai.until(10_000))
        assertEquals("๐๕:๐๐", thai.countdown(5 * MINUTE))
        assertEquals("๑ นาที ๓๐ วินาที", thai.durationWithSeconds(90_000))
    }
}
