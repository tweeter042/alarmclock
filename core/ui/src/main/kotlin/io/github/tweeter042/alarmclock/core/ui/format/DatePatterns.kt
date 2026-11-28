package io.github.tweeter042.alarmclock.core.ui.format

import io.github.tweeter042.alarmclock.core.model.format.LocalizedFormat
import io.github.tweeter042.alarmclock.core.model.settings.YearStyle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Date formatting with the year shown in the user's [YearStyle].
 *
 * Instead of switching java.time to the Thai Buddhist chronology (whose month and era names
 * differ between Android's ICU data and the JDK), the year field of the pattern is replaced by a
 * quoted literal holding the year to display. Day and month names come from the plain ISO
 * calendar, so the output is identical on every platform.
 */
internal object DatePatterns {

    private val yearLetters = setOf('y', 'u', 'Y')

    /** True if [pattern] has a year field outside quoted text. */
    fun hasYear(pattern: String): Boolean = scan(pattern, replacement = null).second

    /**
     * Replaces every unquoted run of year letters (`y`, `u`, `Y`) in [pattern] with the quoted
     * literal [year]. Quoted text (`'…'`) and escaped quotes (`''`) are kept as they are.
     */
    fun withYear(pattern: String, year: String): String = scan(pattern, replacement = year).first

    /** Formats [date] with [pattern] in [locale], writing the year in [yearStyle]. Digits stay ASCII. */
    fun format(date: LocalDate, pattern: String, locale: Locale, yearStyle: YearStyle): String {
        val effective = if (hasYear(pattern)) {
            withYear(pattern, LocalizedFormat.year(date.year, yearStyle).toString())
        } else {
            pattern
        }
        return DateTimeFormatter.ofPattern(effective, locale).format(date)
    }

    private fun scan(pattern: String, replacement: String?): Pair<String, Boolean> {
        val out = StringBuilder(pattern.length + 8)
        var found = false
        var inQuote = false
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            when {
                c == '\'' -> {
                    // '' is an escaped quote both inside and outside quoted text.
                    if (i + 1 < pattern.length && pattern[i + 1] == '\'') {
                        out.append("''")
                        i += 2
                        continue
                    }
                    inQuote = !inQuote
                    out.append(c)
                }
                !inQuote && c in yearLetters -> {
                    found = true
                    while (i + 1 < pattern.length && pattern[i + 1] == c) i++
                    if (replacement != null) out.append('\'').append(replacement.replace("'", "''")).append('\'') else out.append(c)
                }
                else -> out.append(c)
            }
            i++
        }
        return out.toString() to found
    }
}
