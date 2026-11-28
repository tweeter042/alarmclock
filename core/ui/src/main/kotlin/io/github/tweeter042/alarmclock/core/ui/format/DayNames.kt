package io.github.tweeter042.alarmclock.core.ui.format

import androidx.compose.runtime.Stable
import java.time.DayOfWeek

/**
 * Localized weekday names and the order of the week. Days are ISO numbers (1 = Monday …
 * 7 = Sunday) like everywhere in the model.
 *
 * - [short]: "Mon" / "จ." (lists such as "Mon, Wed, Fri")
 * - [narrow]: "M" / "จ" (day toggles)
 * - [long]: "Monday" / "วันจันทร์"
 */
@Stable
class DayNames(
    private val strings: FormatStrings,
    val firstDayOfWeek: DayOfWeek,
) {
    /** The seven ISO days in display order, starting at [firstDayOfWeek]. */
    val week: List<Int> = weekStartingOn(firstDayOfWeek)

    fun short(isoDay: Int): String = strings.string(dayShortRes(isoDay.checkIso()))

    fun narrow(isoDay: Int): String = strings.string(dayNarrowRes(isoDay.checkIso()))

    fun long(isoDay: Int): String = strings.string(dayFullRes(isoDay.checkIso()))

    /** [days] sorted in display order (invalid numbers are dropped). */
    fun inWeekOrder(days: Collection<Int>): List<Int> = week.filter { it in days }

    companion object {
        /** ISO days starting at [first]: MONDAY → [1..7], SUNDAY → [7, 1, 2, 3, 4, 5, 6]. */
        fun weekStartingOn(first: DayOfWeek): List<Int> = (0 until 7).map { ((first.value - 1 + it) % 7) + 1 }

        private fun Int.checkIso(): Int {
            require(this in 1..7) { "ISO day of week must be 1..7, was $this" }
            return this
        }
    }
}
