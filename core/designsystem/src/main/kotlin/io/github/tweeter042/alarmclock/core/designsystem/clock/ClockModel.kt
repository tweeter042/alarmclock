package io.github.tweeter042.alarmclock.core.designsystem.clock

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.tweeter042.alarmclock.core.model.format.LocalizedFormat
import io.github.tweeter042.alarmclock.core.model.settings.NumeralStyle
import java.time.LocalTime

/**
 * Size class of a clock. Sizes are in dp (they do not follow the font scale: big clocks are
 * already large, and fixed sizes keep layouts stable). Hero/Large/Medium shrink to fit the
 * available width.
 */
enum class ClockSize(
    /** Nominal height of a digit. */
    val digitHeight: Dp,
    /** Font size (in dp) used by font-based faces. */
    val fontSizeDp: Float,
    /** Shrinks to the available width when true. */
    val fitWidth: Boolean,
) {
    /** Ring screen, clock tab, bedside clock. */
    Hero(104.dp, 132f, true),
    /** Timer / stopwatch readouts, widgets. */
    Large(68.dp, 86f, true),
    Medium(46.dp, 58f, true),
    /** Alarm list rows. */
    Compact(30.dp, 36f, false),
    /** Inline text, chips, small widgets. */
    Small(18.dp, 22f, false),
}

enum class AmPm { AM, PM }

/** A time prepared for display: [text] uses ASCII digits and ':' separators. */
data class ClockReading(val text: String, val amPm: AmPm?) {
    /** Hour/minute/second groups ("07", "30"). */
    val groups: List<String> get() = text.split(':')
}

/** Pure formatting used by every clock face (unit tested). */
object ClockFormat {

    /**
     * 24 h: "07:05" / "07:05:09". 12 h: "7:05" + [AmPm] (12 for noon and midnight), as on
     * One UI's clock.
     */
    fun reading(time: LocalTime, use24h: Boolean, showSeconds: Boolean): ClockReading {
        val minutes = time.minute.toString().padStart(2, '0')
        val seconds = time.second.toString().padStart(2, '0')
        return if (use24h) {
            val hours = time.hour.toString().padStart(2, '0')
            ClockReading(if (showSeconds) "$hours:$minutes:$seconds" else "$hours:$minutes", null)
        } else {
            val h12 = (time.hour % 12).let { if (it == 0) 12 else it }
            val text = if (showSeconds) "$h12:$minutes:$seconds" else "$h12:$minutes"
            ClockReading(text, if (time.hour < 12) AmPm.AM else AmPm.PM)
        }
    }

    /** Pads a 1-digit first group with a leading blank (" 7:05") for fixed-width digit faces. */
    fun padded(text: String): String {
        val first = text.substringBefore(':')
        return if (first.length == 1 && first[0].isDigit()) " $text" else text
    }

    /** Applies Thai numerals for font-based faces. Segment and dot-matrix faces stay Arabic. */
    fun localizeDigits(text: String, numerals: NumeralStyle): String = LocalizedFormat.digits(text, numerals)
}
