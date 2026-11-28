package io.github.tweeter042.alarmclock.core.ui.format

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.tweeter042.alarmclock.core.model.alarm.RepeatRule
import io.github.tweeter042.alarmclock.core.model.settings.NumeralStyle
import io.github.tweeter042.alarmclock.core.model.settings.YearStyle
import io.github.tweeter042.alarmclock.core.model.time.AlarmTime
import io.github.tweeter042.alarmclock.core.model.time.IsoDays
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RepeatFormatterTest {

    private val en = formatters("en").repeat
    private val th = formatters("th").repeat
    private val morning = at(Today, 6)
    private val evening = at(Today, 20)
    private val seven = AlarmTime(7, 0)

    private fun RepeatFormatter.of(rule: RepeatRule, time: AlarmTime? = seven, now: java.time.ZonedDateTime = morning) = summary(rule, time, now)

    @Test
    fun onceWithoutDate() {
        assertEquals("Once", en.summary(RepeatRule.Once(), alarmTime = null, now = morning))
        assertEquals("ครั้งเดียว", th.summary(RepeatRule.Once(), alarmTime = null, now = morning))
        assertEquals("Today", en.of(RepeatRule.Once(), seven, morning))
        assertEquals("Tomorrow", en.of(RepeatRule.Once(), seven, evening))
        assertEquals("วันนี้", th.of(RepeatRule.Once(), seven, morning))
        assertEquals("พรุ่งนี้", th.of(RepeatRule.Once(), seven, evening))
        // An empty weekly rule behaves like "once".
        assertEquals("Tomorrow", en.of(RepeatRule.Weekly(emptySet()), seven, evening))
    }

    @Test
    fun onceOnADate() {
        assertEquals("Today", en.of(RepeatRule.Once(Today)))
        assertEquals("Tomorrow", en.of(RepeatRule.Once(Today.plusDays(1))))
        assertEquals("Wed, 2 Dec", en.of(RepeatRule.Once(LocalDate.of(2026, 12, 2))))
        assertEquals("Mon, 4 Jan 2027", en.of(RepeatRule.Once(LocalDate.of(2027, 1, 4))))
        assertEquals("พ. 2 ธ.ค.", th.of(RepeatRule.Once(LocalDate.of(2026, 12, 2))))
        val buddhist = formatters("th", yearStyle = YearStyle.BUDDHIST).repeat
        assertEquals("จ. 4 ม.ค. 2570", buddhist.of(RepeatRule.Once(LocalDate.of(2027, 1, 4))))
    }

    @Test
    fun weeklyPresets() {
        assertEquals("Every day", en.of(RepeatRule.Weekly(IsoDays.ALL)))
        assertEquals("Weekdays", en.of(RepeatRule.Weekly(IsoDays.WEEKDAYS)))
        assertEquals("Weekends", en.of(RepeatRule.Weekly(IsoDays.WEEKEND)))
        assertEquals("ทุกวัน", th.of(RepeatRule.Weekly(IsoDays.ALL)))
        assertEquals("วันทำงาน", th.of(RepeatRule.Weekly(IsoDays.WEEKDAYS)))
        assertEquals("เสาร์–อาทิตย์", th.of(RepeatRule.Weekly(IsoDays.WEEKEND)))
    }

    @Test
    fun weeklySingleAndAllButOne() {
        assertEquals("Every Monday", en.of(RepeatRule.Weekly(setOf(IsoDays.MONDAY))))
        assertEquals("ทุกวันจันทร์", th.of(RepeatRule.Weekly(setOf(IsoDays.MONDAY))))
        assertEquals("Every day except Sunday", en.of(RepeatRule.Weekly(IsoDays.ALL - IsoDays.SUNDAY)))
        assertEquals("ทุกวัน ยกเว้นวันอาทิตย์", th.of(RepeatRule.Weekly(IsoDays.ALL - IsoDays.SUNDAY)))
    }

    @Test
    fun weeklyCustomDaysFollowTheFirstDayOfTheWeek() {
        assertEquals("Mon, Wed, Fri", en.of(RepeatRule.Weekly(setOf(5, 1, 3))))
        assertEquals("จ. พ. ศ.", th.of(RepeatRule.Weekly(setOf(1, 3, 5))))
        val mondayFirst = formatters("en", firstDayOfWeek = DayOfWeek.MONDAY).repeat
        val sundayFirst = formatters("en", firstDayOfWeek = DayOfWeek.SUNDAY).repeat
        assertEquals("Mon, Wed, Sun", mondayFirst.of(RepeatRule.Weekly(setOf(7, 1, 3))))
        assertEquals("Sun, Mon, Wed", sundayFirst.of(RepeatRule.Weekly(setOf(7, 1, 3))))
        assertEquals("อา. จ. พ.", formatters("th", firstDayOfWeek = DayOfWeek.SUNDAY).repeat.of(RepeatRule.Weekly(setOf(7, 1, 3))))
        // Invalid day numbers are ignored.
        assertEquals("Tue, Thu", en.of(RepeatRule.Weekly(setOf(2, 4, 9))))
    }

    @Test
    fun rotationBlocks() {
        val anchor = LocalDate.of(2026, 11, 25)
        val fourTwo = RepeatRule.Rotation(anchor, listOf(true, true, true, true, false, false))
        assertEquals("4 on / 2 off from 25 Nov", en.of(fourTwo))
        assertEquals("ทำ 4 หยุด 2 เริ่ม 25 พ.ย.", th.of(fourTwo))
        // Days off first: the block starts two days after the anchor.
        assertEquals("4 on / 2 off from 27 Nov", en.of(RepeatRule.Rotation(anchor, listOf(false, false, true, true, true, true))))
        // The cycle wraps around: the on-block starts at index 4.
        assertEquals("4 on / 2 off from 29 Nov", en.of(RepeatRule.Rotation(anchor, listOf(true, true, false, false, true, true))))
        assertEquals("1 on / 1 off from 25 Nov", en.of(RepeatRule.Rotation(anchor, listOf(true, false))))
        assertEquals("2 on / 3 off from 4 Jan 2027", en.of(RepeatRule.Rotation(LocalDate.of(2027, 1, 4), listOf(true, true, false, false, false))))
    }

    @Test
    fun rotationEdgeCases() {
        val anchor = LocalDate.of(2026, 11, 25)
        assertEquals("Every day", en.of(RepeatRule.Rotation(anchor, listOf(true, true))))
        assertEquals("No ring days", en.of(RepeatRule.Rotation(anchor, listOf(false, false))))
        assertEquals("No ring days", en.of(RepeatRule.Rotation(anchor, emptyList())))
        assertEquals("ไม่มีวันที่ปลุก", th.of(RepeatRule.Rotation(anchor, emptyList())))
        val irregular = RepeatRule.Rotation(anchor, listOf(true, false, true, false, false, false, false))
        assertEquals("2 of 7 days, repeating from 25 Nov", en.of(irregular))
        assertEquals("ปลุก 2 จาก 7 วัน วนซ้ำตั้งแต่ 25 พ.ย.", th.of(irregular))
    }

    @Test
    fun rotationWithThaiNumeralsAndBuddhistYear() {
        val thai = formatters("th", numerals = NumeralStyle.THAI, yearStyle = YearStyle.BUDDHIST).repeat
        val rule = RepeatRule.Rotation(LocalDate.of(2027, 1, 4), listOf(true, true, true, true, false, false))
        assertEquals("ทำ ๔ หยุด ๒ เริ่ม ๔ ม.ค. ๒๕๗๐", thai.of(rule))
    }

    @Test
    fun specificDates() {
        assertEquals("No dates chosen", en.of(RepeatRule.SpecificDates(emptyList())))
        assertEquals("ยังไม่ได้เลือกวันที่", th.of(RepeatRule.SpecificDates(emptyList())))
        assertEquals("Wed, 2 Dec", en.of(RepeatRule.SpecificDates(listOf(LocalDate.of(2026, 12, 2)))))
        val three = listOf(LocalDate.of(2026, 12, 16), LocalDate.of(2026, 12, 2), LocalDate.of(2026, 12, 9), LocalDate.of(2026, 12, 2))
        assertEquals("2 Dec, 9 Dec, 16 Dec", en.of(RepeatRule.SpecificDates(three)))
        assertEquals("2 ธ.ค. 9 ธ.ค. 16 ธ.ค.", th.of(RepeatRule.SpecificDates(three)))
        val five = (0L until 5).map { LocalDate.of(2026, 12, 2).plusWeeks(it) }
        assertEquals("2 Dec, 9 Dec and 3 more", en.of(RepeatRule.SpecificDates(five)))
        assertEquals("2 ธ.ค. 9 ธ.ค. และอีก 3 วัน", th.of(RepeatRule.SpecificDates(five)))
        val withNextYear = listOf(LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 6))
        assertEquals("30 Dec, 6 Jan 2027", en.of(RepeatRule.SpecificDates(withNextYear)))
    }

    @Test
    fun specificDatesSkipPastDays() {
        val past = listOf(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 20))
        assertEquals("No upcoming dates", en.of(RepeatRule.SpecificDates(past)))
        assertEquals("ไม่มีวันที่จะปลุกแล้ว", th.of(RepeatRule.SpecificDates(past)))
        // Today still counts while the alarm time is ahead…
        assertEquals("Today", en.of(RepeatRule.SpecificDates(listOf(Today)), seven, morning))
        // …but not once it has passed.
        assertEquals("No upcoming dates", en.of(RepeatRule.SpecificDates(listOf(Today)), seven, evening))
        assertEquals(
            "Tomorrow",
            en.of(RepeatRule.SpecificDates(listOf(LocalDate.of(2026, 11, 20), Today, Today.plusDays(1))), seven, evening),
        )
    }

    @Test
    fun rotationShapes() {
        assertEquals(RotationShape.Never, RotationShape.of(emptyList()))
        assertEquals(RotationShape.Always, RotationShape.of(listOf(true)))
        assertEquals(RotationShape.Block(on = 4, off = 2, startIndex = 0), RotationShape.of(listOf(true, true, true, true, false, false)))
        assertEquals(RotationShape.Block(on = 1, off = 6, startIndex = 6), RotationShape.of(List(6) { false } + true))
        assertEquals(RotationShape.Irregular(on = 3, length = 7), RotationShape.of(listOf(true, false, true, false, true, false, false)))
    }
}
