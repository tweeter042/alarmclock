package io.github.tweeter042.alarmclock.core.designsystem.clock

import io.github.tweeter042.alarmclock.core.model.settings.NumeralStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class ClockFormatTest {

    @Test
    fun twentyFourHourPadsHours() {
        assertEquals(ClockReading("07:05", null), ClockFormat.reading(LocalTime.of(7, 5), use24h = true, showSeconds = false))
        assertEquals(ClockReading("23:59:09", null), ClockFormat.reading(LocalTime.of(23, 59, 9), use24h = true, showSeconds = true))
        assertEquals("00:00", ClockFormat.reading(LocalTime.MIDNIGHT, use24h = true, showSeconds = false).text)
    }

    @Test
    fun twelveHourUsesTwelveForNoonAndMidnight() {
        val midnight = ClockFormat.reading(LocalTime.MIDNIGHT, use24h = false, showSeconds = false)
        assertEquals("12:00", midnight.text)
        assertEquals(AmPm.AM, midnight.amPm)
        val noon = ClockFormat.reading(LocalTime.NOON, use24h = false, showSeconds = false)
        assertEquals("12:00", noon.text)
        assertEquals(AmPm.PM, noon.amPm)
        val evening = ClockFormat.reading(LocalTime.of(19, 30, 5), use24h = false, showSeconds = true)
        assertEquals("7:30:05", evening.text)
        assertEquals(AmPm.PM, evening.amPm)
        assertEquals(AmPm.AM, ClockFormat.reading(LocalTime.of(11, 59), use24h = false, showSeconds = false).amPm)
    }

    @Test
    fun twentyFourHourHasNoAmPm() {
        assertNull(ClockFormat.reading(LocalTime.of(13, 0), use24h = true, showSeconds = false).amPm)
    }

    @Test
    fun groupsSplitOnColons() {
        assertEquals(listOf("07", "30", "15"), ClockReading("07:30:15", null).groups)
    }

    @Test
    fun paddedAddsLeadingBlankOnlyForOneDigitHours() {
        assertEquals(" 7:30", ClockFormat.padded("7:30"))
        assertEquals("12:30", ClockFormat.padded("12:30"))
        assertEquals("05:00", ClockFormat.padded("05:00"))
    }

    @Test
    fun thaiNumeralsOnlyChangeDigits() {
        assertEquals("๐๗:๓๐", ClockFormat.localizeDigits("07:30", NumeralStyle.THAI))
        assertEquals("07:30", ClockFormat.localizeDigits("07:30", NumeralStyle.ARABIC))
    }

    @Test
    fun clockSizesShrinkMonotonically() {
        val sizes = ClockSize.entries
        for (i in 1 until sizes.size) {
            assertTrue(sizes[i].digitHeight < sizes[i - 1].digitHeight)
            assertTrue(sizes[i].fontSizeDp < sizes[i - 1].fontSizeDp)
        }
        assertTrue(ClockSize.Hero.fitWidth)
        assertFalse(ClockSize.Compact.fitWidth)
    }

    @Test
    fun longAmPmLabelsGoAboveBigClocks() {
        assertTrue(labelAbove("หลังเที่ยง", ClockSize.Hero))
        assertFalse(labelAbove("PM", ClockSize.Hero))
        assertFalse(labelAbove("หลังเที่ยง", ClockSize.Compact))
        assertFalse(labelAbove(null, ClockSize.Hero))
    }

    @Test
    fun dotMatrixDigitsAreFiveBySeven() {
        for (digit in 0..9) {
            var lit = 0
            for (row in 0 until 7) for (col in 0 until 5) if (dotLit(digit, row, col)) lit++
            assertTrue("digit $digit has lit dots", lit in 8..30)
        }
        // "1" has its stem in the middle column on every row but the first.
        for (row in 1 until 7) assertTrue(dotLit(1, row, 2))
        // "8" is symmetric left/right.
        for (row in 0 until 7) for (col in 0 until 5) assertEquals(dotLit(8, row, col), dotLit(8, row, 4 - col))
    }

    @Test
    fun glyphLayoutPlacesColonsAndSmallSeconds() {
        val (glyphs, width) = layoutGlyphs("07:30:15", digit = 0.5f, colon = 0.2f, dot = 0.2f, gap = 0.1f, smallScale = 0.5f)
        assertEquals(7, glyphs.size)
        assertEquals(GlyphKind.Colon, glyphs[2].kind)
        assertEquals(1f, glyphs[0].scale)
        assertEquals(0.5f, glyphs.last().scale)
        assertTrue(glyphs.zipWithNext().all { (a, b) -> b.x > a.x })
        assertTrue(width > glyphs.last().x)
        val (blank, _) = layoutGlyphs(" 7:30", 0.5f, 0.2f, 0.2f, 0.1f, 0.5f)
        assertEquals(GlyphKind.Blank, blank.first().kind)
    }

    @Test
    fun analogDialStylesPerPalette() {
        assertEquals(DialStyle.Numerals, dialStyleFor("classic"))
        assertEquals(DialStyle.Batons, dialStyleFor("navy"))
        assertEquals(DialStyle.Roman, dialStyleFor("forest"))
        assertEquals(DialStyle.Minimal, dialStyleFor("rose_gold"))
        assertEquals(DialStyle.Numerals, dialStyleFor("unknown"))
    }
}
