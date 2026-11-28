package io.github.tweeter042.alarmclock.core.alarm.ring

import android.content.Context
import android.media.AudioManager
import io.github.tweeter042.alarmclock.core.alarm.EngineLog
import io.github.tweeter042.alarmclock.core.alarm.internal.EnginePrefs

/**
 * Temporarily changes the alarm stream (STREAM_ALARM) volume while ringing and restores it after.
 *
 * The original volume is persisted (device-protected prefs) the first time it is changed, so it is
 * restored even if the process dies mid-ring; consecutive rings keep the first saved value.
 */
internal class AlarmVolumeController(context: Context, private val prefs: EnginePrefs) {
    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)
    private val log = EngineLog.audio

    /** overrideSystemVolume: alarm stream to [percent] of its maximum (at least one step). */
    fun applyPercent(percent: Int) {
        val manager = audio ?: return
        val max = manager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val min = runCatching { manager.getStreamMinVolume(AudioManager.STREAM_ALARM) }.getOrDefault(0)
        setVolume(manager, VolumeMath.streamIndexFor(percent, min, max))
    }

    /** Without the override: only make sure a muted alarm stream cannot silence the alarm. */
    fun ensureAudible() {
        val manager = audio ?: return
        val floor = maxOf(1, runCatching { manager.getStreamMinVolume(AudioManager.STREAM_ALARM) }.getOrDefault(0))
        if (manager.getStreamVolume(AudioManager.STREAM_ALARM) < floor) setVolume(manager, floor)
    }

    /** Last resort (ToneGenerator beeping): full alarm volume. */
    fun maximize() {
        val manager = audio ?: return
        setVolume(manager, manager.getStreamMaxVolume(AudioManager.STREAM_ALARM))
    }

    fun restore() {
        val saved = prefs.savedAlarmVolume ?: return
        val manager = audio
        if (manager != null) {
            try {
                manager.setStreamVolume(AudioManager.STREAM_ALARM, saved.coerceIn(0, manager.getStreamMaxVolume(AudioManager.STREAM_ALARM)), 0)
            } catch (t: Throwable) {
                log.w("Could not restore the alarm volume", t)
            }
        }
        prefs.savedAlarmVolume = null
    }

    private fun setVolume(manager: AudioManager, index: Int) {
        try {
            val current = manager.getStreamVolume(AudioManager.STREAM_ALARM)
            if (current == index) return
            if (prefs.savedAlarmVolume == null) prefs.savedAlarmVolume = current
            manager.setStreamVolume(AudioManager.STREAM_ALARM, index, 0)
        } catch (t: Throwable) {
            log.w("Could not change the alarm volume", t)
        }
    }
}
