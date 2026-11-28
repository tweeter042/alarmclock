package io.github.tweeter042.alarmclock.core.alarm.ring

import io.github.tweeter042.alarmclock.core.model.alarm.VibrationPattern

/**
 * A repeating vibration waveform for `VibrationEffect.createWaveform(timings, amplitudes, repeat)`:
 * segment i lasts [timings]`[i]` ms at amplitude [amplitudes]`[i]` (0 = off, 1..255); playback loops
 * from [repeatIndex] forever.
 */
internal class Waveform(val timings: LongArray, val amplitudes: IntArray, val repeatIndex: Int) {
    /** Length of one repeating cycle in ms. */
    val cycleMillis: Long get() = timings.drop(repeatIndex).sum()

    override fun equals(other: Any?): Boolean =
        other is Waveform && timings.contentEquals(other.timings) && amplitudes.contentEquals(other.amplitudes) &&
            repeatIndex == other.repeatIndex

    override fun hashCode(): Int = (timings.contentHashCode() * 31 + amplitudes.contentHashCode()) * 31 + repeatIndex
}

/** Maps every [VibrationPattern] to a repeating waveform (pure; unit tested). */
internal object VibrationWaveforms {
    private const val MAX = 255

    fun of(pattern: VibrationPattern): Waveform = when (pattern) {
        VibrationPattern.BASIC -> build { on(800); off(800) }
        VibrationPattern.HEARTBEAT -> build { on(120); off(120); on(180, 170); off(900) }
        VibrationPattern.RAPID -> build { on(150); off(150) }
        VibrationPattern.WAVE -> build {
            val steps = listOf(40, 80, 120, 160, 200, 230, 255)
            steps.forEach { on(110, it) }
            steps.reversed().forEach { on(110, it) }
            off(500)
        }
        VibrationPattern.SOS -> build {
            repeat(3) { on(DOT); off(GAP) }
            off(LETTER_GAP - GAP)
            repeat(3) { on(DASH); off(GAP) }
            off(LETTER_GAP - GAP)
            repeat(3) { on(DOT); off(GAP) }
            off(WORD_GAP - GAP)
        }
        VibrationPattern.TICK_TOCK -> build { on(60); off(940); on(60, 140); off(940) }
        VibrationPattern.ESCALATING -> build {
            listOf(64, 128, 192).forEach { on(500, it); off(700) }
            repeatFromHere()
            on(700); off(500)
        }
    }

    private const val DOT = 200L
    private const val DASH = 600L
    private const val GAP = 200L
    private const val LETTER_GAP = 600L
    private const val WORD_GAP = 1_400L

    private class Builder {
        val timings = ArrayList<Long>()
        val amplitudes = ArrayList<Int>()
        var repeatIndex = 0

        fun on(ms: Long, amplitude: Int = MAX) {
            timings += ms
            amplitudes += amplitude
        }

        fun on(ms: Int, amplitude: Int = MAX) = on(ms.toLong(), amplitude)

        fun off(ms: Long) {
            // Merge consecutive pauses so every segment is a real on/off change.
            if (amplitudes.isNotEmpty() && amplitudes.last() == 0 && timings.size > repeatIndex) {
                timings[timings.lastIndex] = timings.last() + ms
            } else {
                timings += ms
                amplitudes += 0
            }
        }

        fun off(ms: Int) = off(ms.toLong())

        fun repeatFromHere() {
            repeatIndex = timings.size
        }
    }

    private inline fun build(block: Builder.() -> Unit): Waveform {
        val b = Builder().apply(block)
        return Waveform(b.timings.toLongArray(), b.amplitudes.toIntArray(), b.repeatIndex)
    }
}
