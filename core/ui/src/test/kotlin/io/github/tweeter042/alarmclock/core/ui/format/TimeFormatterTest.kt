package io.github.tweeter042.alarmclock.core.ui.format

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.tweeter042.alarmclock.core.model.settings.NumeralStyle
import io.github.tweeter042.alarmclock.core.model.settings.YearStyle
import io.github.tweeter042.alarmclock.core.model.time.AlarmTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TimeFormatterTest {

    @Test
    fun englishTwentyFourHour() {
        val time = formatters("en", use24h = true).time
        assertEquals("07:05", time.time(7, 5))
        assertEquals("00:00", time.time(0, 0))
        assertEquals("23:59", time.time(AlarmTime(23, 59)))
        assertEquals("07:05:09", time.time(LocalTime.of(7, 5, 9), showSeconds = true))
        assertEquals(TimeParts("07:05", null), time.parts(7, 5))
    }

    @Test
    fun englishTwelveHourHandlesNoonAndMidnight() {
        val time = formatters("en", use24h = false).time
        assertEquals("7:05 AM", time.time(7, 5))
        assertEquals("12:00 AM", time.time(0, 0))
        assertEquals("12:30 PM", time.time(12, 30))
        assertEquals("11:59 PM", time.time(23, 59))
        assertEquals(TimeParts("1:00", "PM"), time.parts(13, 0))
        assertEquals("7", time.hourLabel(19))
        assertEquals("05", time.minuteLabel(5))
    }

    @Test
    fun thaiTimes() {
        val h24 = formatters("th", use24h = true).time
        assertEquals("07:05", h24.time(7, 5))
        val h12 = formatters("th", use24h = false).time
        assertEquals("7:05 ก่อนเที่ยง", h12.time(7, 5))
        assertEquals("1:00 หลังเที่ยง", h12.time(13, 0))
        assertEquals("12:00 ก่อนเที่ยง", h12.time(0, 0))
    }

    @Test
    fun thaiNumeralsApplyToEveryDigit() {
        val time = formatters("th", use24h = true, numerals = NumeralStyle.THAI).time
        assertEquals("๐๗:๓๐", time.time(7, 30))
        assertEquals("๒๓:๕๙:๐๘", time.time(LocalTime.of(23, 59, 8), showSeconds = true))
        assertEquals("๑๒", time.number(12))
        // Thai numerals are a separate choice from the language.
        assertEquals("๗:๐๐ AM", formatters("en", use24h = false, numerals = NumeralStyle.THAI).time.time(7, 0))
    }

    @Test
    fun epochMillisUseTheGivenZone() {
        val millis = at(Today, 6, 45).toInstant().toEpochMilli()
        assertEquals("06:45", formatters("en").time.time(millis, Bangkok))
        assertEquals("23:45", formatters("en").time.time(millis, java.time.ZoneOffset.UTC))
    }

    @Test
    fun shortDatesAddTheYearOnlyForOtherYears() {
        val en = formatters("en").time
        assertEquals("Wed, 25 Nov", en.dateShort(LocalDate.of(2026, 11, 25), Today))
        assertEquals("Mon, 4 Jan 2027", en.dateShort(LocalDate.of(2027, 1, 4), Today))
        assertEquals("Mon, 4 Jan 2027", en.dateShort(LocalDate.of(2027, 1, 4)))
        assertEquals("25 Nov", en.dayMonth(LocalDate.of(2026, 11, 25), Today))
        assertEquals("4 Jan 2027", en.dayMonth(LocalDate.of(2027, 1, 4), Today))

        val th = formatters("th").time
        assertEquals("พ. 25 พ.ย.", th.dateShort(LocalDate.of(2026, 11, 25), Today))
        assertEquals("จ. 4 ม.ค. 2027", th.dateShort(LocalDate.of(2027, 1, 4), Today))
        assertEquals("25 พ.ย.", th.dayMonth(LocalDate.of(2026, 11, 25), Today))
    }

    @Test
    fun longDates() {
        assertEquals("Wednesday, 25 November 2026", formatters("en").time.dateLong(LocalDate.of(2026, 11, 25)))
        assertEquals("Wednesday, 25 November", formatters("en").time.dateLong(LocalDate.of(2026, 11, 25), withYear = false))
        assertEquals("วันพุธที่ 25 พฤศจิกายน 2026", formatters("th").time.dateLong(LocalDate.of(2026, 11, 25)))
        assertEquals("November 2026", formatters("en").time.monthYear(LocalDate.of(2026, 11, 1)))
    }

    @Test
    fun buddhistYearReplacesTheYearInEveryFormat() {
        val th = formatters("th", yearStyle = YearStyle.BUDDHIST).time
        assertEquals("วันพุธที่ 25 พฤศจิกายน 2569", th.dateLong(LocalDate.of(2026, 11, 25)))
        assertEquals("จ. 4 ม.ค. 2570", th.dateShort(LocalDate.of(2027, 1, 4), Today))
        assertEquals("4 ม.ค. 2570", th.dayMonth(LocalDate.of(2027, 1, 4), Today))
        assertEquals("พฤศจิกายน 2569", th.monthYear(LocalDate.of(2026, 11, 1)))
        assertEquals("2569", th.year(2026))
        // English text with the Buddhist era keeps English names.
        assertEquals("Wednesday, 25 November 2569", formatters("en", yearStyle = YearStyle.BUDDHIST).time.dateLong(LocalDate.of(2026, 11, 25)))
        // Day numbers equal to the year's digits are never touched.
        assertEquals("Thursday, 20 August 2569", formatters("en", yearStyle = YearStyle.BUDDHIST).time.dateLong(LocalDate.of(2026, 8, 20)))
    }

    @Test
    fun buddhistYearWithThaiNumerals() {
        val th = formatters("th", yearStyle = YearStyle.BUDDHIST, numerals = NumeralStyle.THAI).time
        assertEquals("วันพุธที่ ๒๕ พฤศจิกายน ๒๕๖๙", th.dateLong(LocalDate.of(2026, 11, 25)))
        assertEquals("๒๕๖๙", th.year(2026))
    }

    @Test
    fun relativeDays() {
        val en = formatters("en").time
        assertEquals("Today", en.relativeDay(Today, Today))
        assertEquals("Tomorrow", en.relativeDay(Today.plusDays(1), Today))
        assertEquals("Yesterday", en.relativeDay(Today.minusDays(1), Today))
        assertEquals("Wednesday", en.relativeDay(LocalDate.of(2026, 12, 2), Today))
        assertEquals("Sat, 5 Dec", en.relativeDay(Today.plusDays(7), Today))
        val th = formatters("th").time
        assertEquals("พรุ่งนี้", th.relativeDay(Today.plusDays(1), Today))
        assertEquals("วันพุธ", th.relativeDay(LocalDate.of(2026, 12, 2), Today))
    }

    @Test
    fun dayAndTime() {
        val now = at(Today, 23, 36).toInstant().toEpochMilli()
        val tomorrowSeven = at(Today.plusDays(1), 7).toInstant().toEpochMilli()
        assertEquals("Tomorrow at 7:00 AM", formatters("en", use24h = false).time.dayAndTime(tomorrowSeven, Bangkok, now))
        assertEquals("พรุ่งนี้ เวลา 07:00", formatters("th").time.dayAndTime(tomorrowSeven, Bangkok, now))
        val nextYear = at(LocalDate.of(2027, 1, 4), 6, 30).toInstant().toEpochMilli()
        assertEquals("Mon, 4 Jan 2027 at 06:30", formatters("en").time.dayAndTime(nextYear, Bangkok, now))
    }

    @Test
    fun forcingTheClockStyle() {
        val base = formatters("en", use24h = true).time
        assertEquals("7:00 PM", base.with24h(false).time(19, 0))
        assertEquals(base, base.with24h(true))
        assertNull(base.parts(19, 0).amPm)
    }
}
