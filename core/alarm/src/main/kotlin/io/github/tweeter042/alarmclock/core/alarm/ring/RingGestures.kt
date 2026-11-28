package io.github.tweeter042.alarmclock.core.alarm.ring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.PowerManager
import android.os.SystemClock
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import io.github.tweeter042.alarmclock.core.alarm.EngineLog
import io.github.tweeter042.alarmclock.core.model.alarm.ActiveRing
import io.github.tweeter042.alarmclock.core.model.alarm.ButtonAction
import io.github.tweeter042.alarmclock.core.model.settings.AppSettings

/**
 * Hardware shortcuts while an alarm rings (volume keys are handled by the ring screen):
 * - flip to snooze: accelerometer, face-down for ~1 s after having been face-up ([FlipDetector]);
 * - power key: a dynamic ACTION_SCREEN_OFF receiver (ignored during the first seconds, when the
 *   full-screen intent itself may toggle the screen, and when the screen was not on);
 * - media keys: a MediaSession that is active only while ringing — this is how S Pen Air actions
 *   (media control), headsets and Galaxy Buds taps reach the alarm
 *   (settings.samsung.mediaButtonsWhileRinging).
 * Every detected gesture is reported as the configured [ButtonAction]; what it does is decided by
 * the engine (GesturePolicy).
 */
internal class RingGestures(
    private val context: Context,
    private val ring: ActiveRing,
    private val settings: AppSettings,
    private val onGesture: (ButtonAction) -> Unit,
) {
    private val log = EngineLog.ring
    private val dismiss = ring.alarm.dismiss
    private val startedAt = SystemClock.elapsedRealtime()

    private var sensorManager: SensorManager? = null
    private var flipListener: SensorEventListener? = null
    private var screenReceiver: BroadcastReceiver? = null
    private var mediaSession: MediaSession? = null

    fun start() {
        if (dismiss.flipToSnooze) startFlip()
        if (dismiss.powerButton != ButtonAction.NONE) startPowerKey()
        if (dismiss.mediaButton != ButtonAction.NONE && settings.samsung.mediaButtonsWhileRinging) startMediaSession()
    }

    fun stop() {
        flipListener?.let { listener -> runCatching { sensorManager?.unregisterListener(listener) } }
        flipListener = null
        screenReceiver?.let { receiver -> runCatching { context.unregisterReceiver(receiver) } }
        screenReceiver = null
        mediaSession?.let { session ->
            runCatching {
                session.isActive = false
                session.release()
            }
        }
        mediaSession = null
    }

    private fun startFlip() {
        val manager = context.getSystemService(SensorManager::class.java) ?: return
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        val detector = FlipDetector()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (detector.onSample(event.values[2], SystemClock.elapsedRealtime())) {
                    log.i("Flip detected")
                    onGesture(ButtonAction.SNOOZE)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)) {
            sensorManager = manager
            flipListener = listener
        }
    }

    private fun startPowerKey() {
        val power = context.getSystemService(PowerManager::class.java)
        var screenOnSince: Long? = if (power?.isInteractive == true) SystemClock.elapsedRealtime() else null
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val now = SystemClock.elapsedRealtime()
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> screenOnSince = now
                    Intent.ACTION_SCREEN_OFF -> {
                        val on = screenOnSince
                        screenOnSince = null
                        val settled = now - startedAt >= POWER_KEY_GRACE_MILLIS
                        if (settled && on != null && now - on >= SCREEN_ON_MIN_MILLIS) {
                            log.i("Power key detected")
                            onGesture(dismiss.powerButton)
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        try {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            screenReceiver = receiver
        } catch (t: Throwable) {
            log.w("Power key receiver unavailable", t)
        }
    }

    private fun startMediaSession() {
        try {
            val debouncer = KeyDebouncer()
            val session = MediaSession(context, MEDIA_SESSION_TAG)
            session.setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val event = IntentCompat.getParcelableExtra(mediaButtonIntent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                        ?: return false
                    if (event.keyCode !in GesturePolicy.MEDIA_KEYS) return false
                    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && debouncer.accept(SystemClock.elapsedRealtime())) {
                        log.i("Media key detected")
                        onGesture(dismiss.mediaButton)
                    }
                    return true
                }

                // Some controllers send transport controls instead of key events.
                override fun onPlay() = transport()
                override fun onPause() = transport()
                override fun onSkipToNext() = transport()
                override fun onStop() = transport()

                private fun transport() {
                    if (debouncer.accept(SystemClock.elapsedRealtime())) onGesture(dismiss.mediaButton)
                }
            })
            session.setPlaybackToLocal(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            session.setPlaybackState(
                PlaybackState.Builder()
                    .setActions(
                        PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                            PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_STOP,
                    )
                    .setState(PlaybackState.STATE_PLAYING, 0L, 1f)
                    .build(),
            )
            session.isActive = true
            mediaSession = session
        } catch (t: Throwable) {
            log.w("Media session unavailable", t)
        }
    }

    private companion object {
        const val MEDIA_SESSION_TAG = "AlarmClockRing"
        /** The full-screen intent turns the screen on (and maybe off) right after the ring starts. */
        const val POWER_KEY_GRACE_MILLIS = 3_000L
        const val SCREEN_ON_MIN_MILLIS = 1_500L
    }
}
