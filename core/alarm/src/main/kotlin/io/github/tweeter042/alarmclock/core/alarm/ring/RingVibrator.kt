package io.github.tweeter042.alarmclock.core.alarm.ring

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import io.github.tweeter042.alarmclock.core.alarm.EngineLog
import io.github.tweeter042.alarmclock.core.model.alarm.VibrationPattern

/** Repeating alarm vibration (VibrationAttributes.USAGE_ALARM, so it is not muted by ringer mode). */
internal class RingVibrator(context: Context) {
    private val vibrator: Vibrator? = try {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } catch (t: Throwable) {
        null
    }
    @Volatile private var running = false

    fun start(pattern: VibrationPattern, amplitudeScale: Float = 1f) {
        val device = vibrator ?: return
        if (!device.hasVibrator()) return
        val waveform = VibrationWaveforms.of(pattern)
        val amplitudes = if (device.hasAmplitudeControl()) {
            waveform.amplitudes.map { if (it == 0) 0 else (it * amplitudeScale).toInt().coerceIn(1, 255) }.toIntArray()
        } else {
            waveform.amplitudes.map { if (it == 0) 0 else VibrationEffect.DEFAULT_AMPLITUDE }.toIntArray()
        }
        try {
            val effect = VibrationEffect.createWaveform(waveform.timings, amplitudes, waveform.repeatIndex)
            device.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            running = true
        } catch (t: Throwable) {
            EngineLog.audio.w("Vibration failed", t)
        }
    }

    fun stop() {
        if (!running) return
        running = false
        runCatching { vibrator?.cancel() }
    }
}

/**
 * Last-resort sound when no audio file can be played: ToneGenerator beeps on the alarm stream
 * (the caller raises the alarm volume to maximum).
 */
internal class FallbackBeeper {
    private val handler = Handler(Looper.getMainLooper())
    private var generator: ToneGenerator? = null
    private var running = false

    private val beep = object : Runnable {
        override fun run() {
            if (!running) return
            try {
                generator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, BEEP_MILLIS)
            } catch (t: Throwable) {
                EngineLog.audio.w("Beep failed", t)
            }
            handler.postDelayed(this, PERIOD_MILLIS)
        }
    }

    /** Returns false if even ToneGenerator is unavailable. */
    fun start(): Boolean {
        if (running) return true
        generator = try {
            ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME)
        } catch (t: Throwable) {
            EngineLog.audio.e("ToneGenerator unavailable", t)
            null
        }
        if (generator == null) return false
        running = true
        handler.post(beep)
        return true
    }

    fun stop() {
        running = false
        handler.removeCallbacks(beep)
        runCatching { generator?.stopTone() }
        runCatching { generator?.release() }
        generator = null
    }

    private companion object {
        const val BEEP_MILLIS = 1_000
        const val PERIOD_MILLIS = 1_400L
    }
}
