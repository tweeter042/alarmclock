package io.github.tweeter042.alarmclock.core.ui.picker

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.saveable.Saver

/**
 * The digits typed on a [DurationKeypad], Samsung-timer style: digits enter from the right and
 * shift left through seconds, minutes and hours ("1", "13", "130" → 00:01:30). At most six digits
 * (HH MM SS). Minutes and seconds may be typed up to 99 and are carried over by [toMillis]
 * (90 seconds = 1 min 30 s). Pure and unit tested.
 */
@Immutable
data class DurationDigits(val digits: String = "") {
    init {
        require(digits.length <= MAX_DIGITS && digits.all { it in '0'..'9' }) { "Invalid duration digits: $digits" }
    }

    /** "000130" for "130". */
    private val padded: String get() = digits.padStart(MAX_DIGITS, '0')

    val hours: Int get() = padded.substring(0, 2).toInt()
    val minutes: Int get() = padded.substring(2, 4).toInt()
    val seconds: Int get() = padded.substring(4, 6).toInt()

    val isEmpty: Boolean get() = digits.isEmpty()

    /** How many of the six positions (from the right) the user has typed. */
    val typedCount: Int get() = digits.length

    /** Appends [digit]; leading zeros are ignored and a full keypad ignores more digits. */
    fun append(digit: Char): DurationDigits {
        require(digit in '0'..'9') { "Not a digit: $digit" }
        if (digits.length >= MAX_DIGITS || (digits.isEmpty() && digit == '0')) return this
        return DurationDigits(digits + digit)
    }

    /** The "00" key. */
    fun appendDoubleZero(): DurationDigits = append('0').append('0')

    fun backspace(): DurationDigits = if (digits.isEmpty()) this else DurationDigits(digits.dropLast(1))

    fun clear(): DurationDigits = DurationDigits()

    /** Total length with minutes/seconds above 59 carried over. */
    fun toMillis(): Long = ((hours * 3600L) + minutes * 60L + seconds) * 1000L

    companion object {
        const val MAX_DIGITS = 6

        /** Digits for an existing duration (rounded down to the second, capped at 99:59:59). */
        fun fromMillis(millis: Long): DurationDigits {
            val total = (millis.coerceAtLeast(0L) / 1000L).coerceAtMost(99L * 3600 + 59 * 60 + 59)
            val text = buildString {
                append((total / 3600).toString().padStart(2, '0'))
                append((total / 60 % 60).toString().padStart(2, '0'))
                append((total % 60).toString().padStart(2, '0'))
            }.trimStart('0')
            return DurationDigits(text)
        }

        val Saver: Saver<DurationDigits, String> = Saver(save = { it.digits }, restore = { DurationDigits(it) })
    }
}
