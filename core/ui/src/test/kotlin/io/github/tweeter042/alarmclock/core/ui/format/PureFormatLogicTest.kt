package io.github.tweeter042.alarmclock.core.ui.format

import io.github.tweeter042.alarmclock.core.model.settings.YearStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/** Plain JVM tests (no Android resources). */
class PureFormatLogicTest {

    @Test
    fun yearLettersAreReplacedOutsideQuotes() {
        assertEquals("d MMM '2569'", DatePatterns.withYear("d MMM y", "2569"))
        assertEquals("d MMM '2569'", DatePatterns.withYear("d MMM yyyy", "2569"))
        assertEquals("EEEE'ที่ y' d '2569'", DatePatterns.withYear("EEEE'ที่ y' d y", "2569"))
        assertEquals("d 'o''clock' '2569'", DatePatterns.withYear("d 'o''clock' u", "2569"))
        assertEquals("'it''s' d", DatePatterns.withYear("'it''s' d", "2569"))
        assertTrue(DatePatterns.hasYear("MMMM y"))
        assertFalse(DatePatterns.hasYear("EEE d MMM"))
        assertFalse(DatePatterns.hasYear("d 'y' MMM"))
    }

    @Test
    fun formattingWithYearStyles() {
        val date = LocalDate.of(2026, 11, 25)
        assertEquals("25 Nov 2026", DatePatterns.format(date, "d MMM y", Locale.ENGLISH, YearStyle.GREGORIAN))
        assertEquals("25 Nov 2569", DatePatterns.format(date, "d MMM y", Locale.ENGLISH, YearStyle.BUDDHIST))
        assertEquals("25 Nov", DatePatterns.format(date, "d MMM", Locale.ENGLISH, YearStyle.BUDDHIST))
    }

    @Test
    fun minutePartsRounding() {
        val m = 60_000L
        assertEquals(DurationParts(0, 7, 24, 0), DurationParts.ofMinutes(7 * 60 * m + 23 * m + 1, DurationRounding.UP))
        assertEquals(DurationParts(0, 7, 23, 0), DurationParts.ofMinutes(7 * 60 * m + 23 * m + 59_999, DurationRounding.DOWN))
        assertEquals(DurationParts(0, 0, 24, 0), DurationParts.ofMinutes(23 * m + 30_000, DurationRounding.NEAREST))
        assertEquals(DurationParts(0, 0, 23, 0), DurationParts.ofMinutes(23 * m + 29_999, DurationRounding.NEAREST))
        assertEquals(DurationParts(1, 0, 0, 0), DurationParts.ofMinutes(23 * 60 * m + 59 * m + 1, DurationRounding.UP))
        assertEquals(DurationParts(2, 3, 4, 0), DurationParts.ofMinutes(((2 * 24 + 3) * 60 + 4) * m, DurationRounding.DOWN))
        assertEquals(DurationParts(0, 0, 0, 0), DurationParts.ofMinutes(-5 * m, DurationRounding.UP))
        assertTrue(DurationParts.ofMinutes(0, DurationRounding.UP).isZero)
    }

    @Test
    fun secondPartsKeepHoursAboveADay() {
        assertEquals(DurationParts(0, 25, 0, 1), DurationParts.ofSeconds(25 * 3_600_000L + 1, DurationRounding.UP))
        assertEquals(DurationParts(0, 0, 1, 30), DurationParts.ofSeconds(90_400, DurationRounding.NEAREST))
        assertEquals(90L, DurationParts.ofSeconds(90_000, DurationRounding.DOWN).totalSeconds)
    }
}
