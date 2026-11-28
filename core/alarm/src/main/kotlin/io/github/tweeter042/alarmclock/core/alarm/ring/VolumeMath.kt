package io.github.tweeter042.alarmclock.core.alarm.ring

import io.github.tweeter042.alarmclock.core.model.alarm.SoundConfig
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Volume arithmetic of the ring (pure; unit tested).
 *
 * The audible level is `stream volume × player gain`. With [SoundConfig.overrideSystemVolume] the
 * alarm stream is set to `volumePercent` of its maximum and the player gain ramps to 1; without it
 * the stream is left alone and the gain ramps to `volumePercent / 100` instead.
 */
internal object VolumeMath {
    /** Gain at the start of a fade-in (≈ −26 dB). */
    const val FADE_START_GAIN = 0.05f
    /** Gain multiplier while the user is doing a mission (lowerVolumeDuringMission). */
    const val MISSION_DUCK = 0.15f
    /** Gain multiplier while the briefing is spoken over the alarm. */
    const val BRIEFING_DUCK = 0.30f
    /** Player gain of the awake-check ping ("softly"). */
    const val AWAKE_PING_GAIN = 0.35f

    /** Alarm-stream index for [volumePercent] of [maxIndex]: rounded, never below max(1, [minIndex]). */
    fun streamIndexFor(volumePercent: Int, minIndex: Int, maxIndex: Int): Int {
        if (maxIndex <= 0) return 0
        val floor = maxOf(1, minIndex).coerceAtMost(maxIndex)
        val wanted = (volumePercent.coerceIn(0, 100) / 100f * maxIndex).roundToInt()
        return wanted.coerceIn(floor, maxIndex)
    }

    /** Player gain the fade-in ends at. */
    fun targetGain(sound: SoundConfig): Float =
        if (sound.overrideSystemVolume) 1f else (sound.volumePercent.coerceIn(1, 100) / 100f).coerceAtLeast(FADE_START_GAIN)

    /**
     * Perceptual fade-in factor after [elapsedMs] of a [fadeInMs] ramp: equal decibel steps per unit
     * of time, from [FADE_START_GAIN] (−26 dB) to 1 (0 dB), i.e. `start^(1 − p)`.
     */
    fun fadeFactor(elapsedMs: Long, fadeInMs: Long): Float {
        if (fadeInMs <= 0 || elapsedMs >= fadeInMs) return 1f
        if (elapsedMs <= 0) return FADE_START_GAIN
        val progress = elapsedMs.toDouble() / fadeInMs
        return exp((1 - progress) * ln(FADE_START_GAIN.toDouble())).toFloat().coerceIn(FADE_START_GAIN, 1f)
    }

    /** Player gain: target × fade-in × duck, clamped to 0..1. */
    fun playerGain(target: Float, elapsedMs: Long, fadeInMs: Long, duck: Float = 1f): Float =
        (target * fadeFactor(elapsedMs, fadeInMs) * duck).coerceIn(0f, 1f)
}
