package io.github.tweeter042.alarmclock.core.ui.picker

import io.github.tweeter042.alarmclock.core.model.settings.AppLanguage
import io.github.tweeter042.alarmclock.core.model.settings.NumeralStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** Plain JVM tests of the pickers' pure logic. */
class PickerLogicTest {

    @Test
    fun keypadDigitsEnterFromTheRight() {
        var d = DurationDigits()
        assertTrue(d.isEmpty)
        d = d.append('1')
        assertEquals(Triple(0, 0, 1), Triple(d.hours, d.minutes, d.seconds))
        d = d.append('3').append('0')
        assertEquals(Triple(0, 1, 30), Triple(d.hours, d.minutes, d.seconds))
        assertEquals(90_000L, d.toMillis())
        d = d.appendDoubleZero()
        assertEquals(Triple(1, 30, 0), Triple(d.hours, d.minutes, d.seconds))
        assertEquals(5_400_000L, d.toMillis())
        assertEquals(DurationDigits("13"), d.backspace().backspace().backspace())
        assertEquals(DurationDigits(), d.clear())
    }

    @Test
    fun keypadIgnoresLeadingZerosAndExtraDigits() {
        assertEquals(DurationDigits(), DurationDigits().append('0'))
        assertEquals(DurationDigits(), DurationDigits().appendDoubleZero())
        val full = DurationDigits("123456")
        assertEquals(full, full.append('7'))
        assertEquals(6, full.typedCount)
        assertEquals(DurationDigits(), DurationDigits().backspace())
    }

    @Test
    fun keypadCarriesOverflowingMinutesAndSeconds() {
        // "99" seconds and "90" minutes are accepted while typing and carried over.
        assertEquals(99_000L, DurationDigits("99").toMillis())
        assertEquals((90 * 60 + 99) * 1000L, DurationDigits("9099").toMillis())
    }

    @Test
    fun keypadDigitsFromAnExistingDuration() {
        assertEquals(DurationDigits("130"), DurationDigits.fromMillis(90_000))
        assertEquals(DurationDigits("10000"), DurationDigits.fromMillis(3_600_000))
        assertEquals(DurationDigits(), DurationDigits.fromMillis(0))
        assertEquals(DurationDigits("995959"), DurationDigits.fromMillis(500L * 3_600_000))
    }

    @Test
    fun twelveHourConversions() {
        assertEquals(11, TimeInputRules.hour12Index(0)) // 12 AM
        assertEquals(0, TimeInputRules.hour12Index(1))
        assertEquals(11, TimeInputRules.hour12Index(12)) // 12 PM
        assertEquals(6, TimeInputRules.hour12Index(19)) // 7 PM
        assertEquals(0, TimeInputRules.hour24(12, pm = false))
        assertEquals(12, TimeInputRules.hour24(12, pm = true))
        assertEquals(19, TimeInputRules.hour24(7, pm = true))
        assertEquals(7, TimeInputRules.hour24(7, pm = false))
        assertEquals(19, TimeInputRules.withPm(7, pm = true))
        assertEquals(7, TimeInputRules.withPm(19, pm = false))
        assertEquals(12, TimeInputRules.withPm(0, pm = true))
    }

    @Test
    fun typedTimeValidation() {
        assertEquals(0, TimeInputRules.parseHour("00", use24h = true))
        assertEquals(23, TimeInputRules.parseHour("23", use24h = true))
        assertNull(TimeInputRules.parseHour("24", use24h = true))
        assertNull(TimeInputRules.parseHour("0", use24h = false))
        assertEquals(12, TimeInputRules.parseHour("12", use24h = false))
        assertNull(TimeInputRules.parseHour("13", use24h = false))
        assertNull(TimeInputRules.parseHour("", use24h = true))
        assertNull(TimeInputRules.parseHour("1a", use24h = true))
        assertEquals(5, TimeInputRules.parseMinute("5"))
        assertEquals(59, TimeInputRules.parseMinute("59"))
        assertNull(TimeInputRules.parseMinute("60"))
        assertEquals("12", TimeInputRules.sanitize("1a2b3"))
        assertEquals("", TimeInputRules.sanitize("๑๒"))
    }

    @Test
    fun hourCompleteMovesFocusAtTheRightTime() {
        assertFalse(TimeInputRules.hourComplete("1", use24h = true))
        assertFalse(TimeInputRules.hourComplete("2", use24h = true))
        assertTrue(TimeInputRules.hourComplete("3", use24h = true))
        assertTrue(TimeInputRules.hourComplete("07", use24h = true))
        assertFalse(TimeInputRules.hourComplete("1", use24h = false))
        assertTrue(TimeInputRules.hourComplete("2", use24h = false))
        assertFalse(TimeInputRules.hourComplete("", use24h = false))
    }

    @Test
    fun loopingWheelTakesTheShortWay() {
        assertEquals(1002, nearestVirtualIndex(centerVirtual = 1000, current = 10, target = 12, count = 60))
        assertEquals(998, nearestVirtualIndex(centerVirtual = 1000, current = 0, target = 58, count = 60))
        assertEquals(1001, nearestVirtualIndex(centerVirtual = 1000, current = 59, target = 0, count = 60))
        assertEquals(1000, nearestVirtualIndex(centerVirtual = 1000, current = 5, target = 5, count = 60))
    }

    @Test
    fun dateBoundsAlwaysIncludeTodayAndTheInitialDate() {
        val today = LocalDate.of(2026, 11, 28)
        val bounds = DateBounds.of(min = today, max = null, initial = null, today = today)
        assertEquals(2026, bounds.years.first)
        assertTrue(bounds.contains(today))
        assertFalse(bounds.contains(today.minusDays(1)))
        assertEquals(today, bounds.clamp(today.minusDays(3)))
        val withOldInitial = DateBounds.of(min = today, max = today.plusYears(1), initial = LocalDate.of(2020, 1, 1), today = today)
        assertEquals(2020..2027, withOldInitial.years)
        assertEquals(today.plusYears(1), withOldInitial.clamp(today.plusYears(3)))
    }

    @Test
    fun calendarLocaleCarriesNumeralsAndFirstDay() {
        val thai = calendarLocale(AppLanguage.THAI, NumeralStyle.THAI, DayOfWeek.MONDAY)
        assertEquals("th", thai.language)
        assertEquals("TH", thai.country)
        assertEquals("thai", thai.getUnicodeLocaleType("nu"))
        assertEquals("mon", thai.getUnicodeLocaleType("fw"))
        val english = calendarLocale(AppLanguage.ENGLISH, NumeralStyle.ARABIC, DayOfWeek.SUNDAY)
        assertEquals("en", english.language)
        assertNull(english.getUnicodeLocaleType("nu"))
        assertEquals("sun", english.getUnicodeLocaleType("fw"))
    }
}
