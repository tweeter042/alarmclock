package io.github.tweeter042.alarmclock.core.ui.format

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.tweeter042.alarmclock.core.model.settings.NumeralStyle
import io.github.tweeter042.alarmclock.core.model.settings.RegionSettings
import io.github.tweeter042.alarmclock.core.model.settings.TimeFormatPref
import io.github.tweeter042.alarmclock.core.model.settings.YearStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.util.Locale

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DayNamesTest {

    @Test
    fun englishNames() {
        val days = formatters("en").days
        assertEquals("Mon", days.short(1))
        assertEquals("Sun", days.short(7))
        assertEquals("M", days.narrow(1))
        assertEquals("Monday", days.long(1))
        assertEquals("Sunday", days.long(7))
    }

    @Test
    fun thaiNames() {
        val days = formatters("th").days
        assertEquals("จ.", days.short(1))
        assertEquals("พฤ.", days.short(4))
        assertEquals("อา.", days.short(7))
        assertEquals("จ", days.narrow(1))
        assertEquals("วันจันทร์", days.long(1))
        assertEquals("วันอาทิตย์", days.long(7))
    }

    @Test
    fun weekOrderFollowsTheFirstDay() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), formatters(firstDayOfWeek = DayOfWeek.MONDAY).days.week)
        assertEquals(listOf(7, 1, 2, 3, 4, 5, 6), formatters(firstDayOfWeek = DayOfWeek.SUNDAY).days.week)
        assertEquals(listOf(6, 7, 1, 2, 3, 4, 5), formatters(firstDayOfWeek = DayOfWeek.SATURDAY).days.week)
        assertEquals(listOf(7, 1, 5), formatters(firstDayOfWeek = DayOfWeek.SUNDAY).days.inWeekOrder(setOf(5, 1, 7, 8)))
    }

    @Test
    fun firstDayOfWeekSettingOrLocaleDefault() {
        assertEquals(DayOfWeek.SATURDAY, resolveFirstDayOfWeek(6, Locale.forLanguageTag("th-TH")))
        assertEquals(DayOfWeek.MONDAY, resolveFirstDayOfWeek(0, Locale.forLanguageTag("en-GB")))
        assertEquals(DayOfWeek.SUNDAY, resolveFirstDayOfWeek(0, Locale.forLanguageTag("en-US")))
        assertEquals(DayOfWeek.SUNDAY, resolveFirstDayOfWeek(0, Locale.forLanguageTag("th-TH")))
        assertEquals(DayOfWeek.MONDAY, resolveFirstDayOfWeek(9, Locale.forLanguageTag("en-GB")))
    }

    @Test
    fun optionsResolveTheSystemClock() {
        assertTrue(TimeFormatPref.SYSTEM.uses24Hour(systemUses24h = true))
        assertFalse(TimeFormatPref.SYSTEM.uses24Hour(systemUses24h = false))
        assertFalse(TimeFormatPref.H12.uses24Hour(systemUses24h = true))
        assertTrue(TimeFormatPref.H24.uses24Hour(systemUses24h = false))
        val region = RegionSettings(timeFormat = TimeFormatPref.SYSTEM, yearStyle = YearStyle.BUDDHIST, numerals = NumeralStyle.THAI, firstDayOfWeek = 1)
        assertEquals(
            FormatOptions(use24h = false, numerals = NumeralStyle.THAI, yearStyle = YearStyle.BUDDHIST, firstDayOfWeek = DayOfWeek.MONDAY),
            FormatOptions.resolve(region, Locale.forLanguageTag("th-TH"), systemUses24h = false),
        )
    }

    @Test
    fun languageFromResources() {
        assertEquals(io.github.tweeter042.alarmclock.core.model.settings.AppLanguage.THAI, stringsFor("th").language)
        assertEquals(io.github.tweeter042.alarmclock.core.model.settings.AppLanguage.ENGLISH, stringsFor("en").language)
        // Languages the app does not have fall back to English resources.
        assertEquals(io.github.tweeter042.alarmclock.core.model.settings.AppLanguage.ENGLISH, stringsFor("de").language)
        assertEquals(io.github.tweeter042.alarmclock.core.model.settings.AppLanguage.THAI, AppLanguages.fromTag("th-TH"))
        assertEquals(io.github.tweeter042.alarmclock.core.model.settings.AppLanguage.ENGLISH, AppLanguages.fromTag(null))
    }
}
