package io.github.tweeter042.alarmclock.core.ui.format

import androidx.compose.runtime.Immutable
import io.github.tweeter042.alarmclock.core.model.settings.NumeralStyle
import io.github.tweeter042.alarmclock.core.model.settings.RegionSettings
import io.github.tweeter042.alarmclock.core.model.settings.TimeFormatPref
import io.github.tweeter042.alarmclock.core.model.settings.YearStyle
import java.time.DayOfWeek
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Resolved formatting preferences: [RegionSettings] with "system" / "locale default" choices
 * already decided, so formatting itself is pure.
 */
@Immutable
data class FormatOptions(
    val use24h: Boolean = true,
    val numerals: NumeralStyle = NumeralStyle.ARABIC,
    val yearStyle: YearStyle = YearStyle.GREGORIAN,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
) {
    companion object {
        /**
         * @param locale the current UI locale (with region, if any), for the locale's first day of week.
         * @param systemUses24h `android.text.format.DateFormat.is24HourFormat(context)`, used for
         *   [TimeFormatPref.SYSTEM].
         */
        fun resolve(region: RegionSettings, locale: Locale, systemUses24h: Boolean): FormatOptions = FormatOptions(
            use24h = region.timeFormat.uses24Hour(systemUses24h),
            numerals = region.numerals,
            yearStyle = region.yearStyle,
            firstDayOfWeek = resolveFirstDayOfWeek(region.firstDayOfWeek, locale),
        )
    }
}

/** 24-hour clock for this preference; [TimeFormatPref.SYSTEM] follows the device setting. */
fun TimeFormatPref.uses24Hour(systemUses24h: Boolean): Boolean = when (this) {
    TimeFormatPref.SYSTEM -> systemUses24h
    TimeFormatPref.H12 -> false
    TimeFormatPref.H24 -> true
}

/** [isoDay] 1 (Monday) … 7 (Sunday) wins; 0 (or anything else) uses [locale]'s first day of the week. */
fun resolveFirstDayOfWeek(isoDay: Int, locale: Locale): DayOfWeek =
    if (isoDay in 1..7) DayOfWeek.of(isoDay) else WeekFields.of(locale).firstDayOfWeek
