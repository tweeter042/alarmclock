package io.github.tweeter042.alarmclock.core.ui.format

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import io.github.tweeter042.alarmclock.core.model.settings.NumeralStyle
import io.github.tweeter042.alarmclock.core.model.settings.YearStyle
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** The app's real string resources in [languageTag] (Robolectric). */
internal fun stringsFor(languageTag: String): FormatStrings {
    val base = ApplicationProvider.getApplicationContext<Context>()
    val config = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(languageTag)) }
    return ResourceFormatStrings(base.createConfigurationContext(config).resources)
}

internal fun formatters(
    languageTag: String = "en",
    use24h: Boolean = true,
    numerals: NumeralStyle = NumeralStyle.ARABIC,
    yearStyle: YearStyle = YearStyle.GREGORIAN,
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
): UiFormatters = UiFormatters(
    strings = stringsFor(languageTag),
    options = FormatOptions(use24h = use24h, numerals = numerals, yearStyle = yearStyle, firstDayOfWeek = firstDayOfWeek),
)

internal val Bangkok: ZoneId = ZoneId.of("Asia/Bangkok")

/** Saturday 28 November 2026 (Wednesday 25 November is three days earlier). */
internal val Today: LocalDate = LocalDate.of(2026, 11, 28)

internal fun at(date: LocalDate, hour: Int, minute: Int = 0): ZonedDateTime = ZonedDateTime.of(date.atTime(hour, minute), Bangkok)

internal const val MINUTE = 60_000L
internal const val HOUR = 60 * MINUTE
internal const val DAY = 24 * HOUR
