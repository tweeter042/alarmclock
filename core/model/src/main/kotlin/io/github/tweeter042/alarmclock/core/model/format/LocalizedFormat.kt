package io.github.tweeter042.alarmclock.core.model.format

import io.github.tweeter042.alarmclock.core.model.settings.NumeralStyle
import io.github.tweeter042.alarmclock.core.model.settings.YearStyle

/** Locale helpers that java.time does not cover. */
object LocalizedFormat {
    private const val THAI_ZERO = '๐'

    /** Replaces ASCII digits with Thai digits (๐–๙) when [style] is THAI. */
    fun digits(text: String, style: NumeralStyle): String =
        if (style == NumeralStyle.ARABIC) {
            text
        } else {
            buildString(text.length) {
                for (c in text) append(if (c in '0'..'9') THAI_ZERO + (c - '0') else c)
            }
        }

    /** Gregorian year → displayed year (Buddhist era adds 543). */
    fun year(gregorianYear: Int, style: YearStyle): Int =
        if (style == YearStyle.BUDDHIST) gregorianYear + 543 else gregorianYear
}
