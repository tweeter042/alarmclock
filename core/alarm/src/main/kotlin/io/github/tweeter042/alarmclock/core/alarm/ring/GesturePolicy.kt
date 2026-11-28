package io.github.tweeter042.alarmclock.core.alarm.ring

import android.view.KeyEvent
import io.github.tweeter042.alarmclock.core.model.alarm.ActiveRing
import io.github.tweeter042.alarmclock.core.model.alarm.ButtonAction
import io.github.tweeter042.alarmclock.core.model.alarm.RingKind
import io.github.tweeter042.alarmclock.core.model.alarm.SnoozePolicy

/** What a hardware button / gesture finally does for a ring. */
internal enum class GestureResult { NONE, SNOOZE, DISMISS, OPEN_RING_SCREEN }

/** Pure rules for flip, power and media-button shortcuts while ringing. */
internal object GesturePolicy {

    /** Media keys that act while ringing (S Pen Air actions media control, headset, Galaxy Buds taps). */
    val MEDIA_KEYS: Set<Int> = setOf(
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_HEADSETHOOK,
        KeyEvent.KEYCODE_MEDIA_NEXT,
    )

    /**
     * - DISMISS with missions → open the ring screen (missions cannot be skipped).
     * - SNOOZE when missions are required to snooze, or snoozing is no longer allowed → open the
     *   ring screen so the user sees why. Timers always "snooze" (+1 minute).
     */
    fun resolve(action: ButtonAction, ring: ActiveRing): GestureResult = when (action) {
        ButtonAction.NONE -> GestureResult.NONE
        ButtonAction.DISMISS -> if (ring.hasMissions && ring.kind != RingKind.TIMER) GestureResult.OPEN_RING_SCREEN else GestureResult.DISMISS
        ButtonAction.SNOOZE -> when {
            ring.kind == RingKind.TIMER -> GestureResult.SNOOZE
            SnoozePolicy.missionsRequiredToSnooze(ring.alarm) -> GestureResult.OPEN_RING_SCREEN
            !SnoozePolicy.canSnooze(ring.alarm.snooze, ring.snoozeCount) -> GestureResult.OPEN_RING_SCREEN
            else -> GestureResult.SNOOZE
        }
    }
}

/**
 * Face-down detection from accelerometer z (m/s²; ≈ +9.8 face-up, ≈ −9.8 face-down), smoothed.
 *
 * Fires once when the phone has been face-down for [holdMillis] AFTER having been seen face-up:
 * a phone that is already lying face-down when the alarm starts must not snooze it at once.
 */
internal class FlipDetector(
    private val holdMillis: Long = 1_000L,
    private val faceDownBelow: Float = -7.5f,
    private val faceUpAbove: Float = -3.0f,
    private val smoothing: Float = 0.3f,
) {
    private var filtered: Float? = null
    private var armed = false
    private var faceDownSince: Long? = null
    private var fired = false

    /** Returns true exactly once, when the flip completes. */
    fun onSample(z: Float, timestampMillis: Long): Boolean {
        if (fired) return false
        val value = filtered?.let { it + smoothing * (z - it) } ?: z
        filtered = value
        when {
            value > faceUpAbove -> {
                armed = true
                faceDownSince = null
            }
            value < faceDownBelow -> {
                if (!armed) return false
                val since = faceDownSince ?: timestampMillis.also { faceDownSince = it }
                if (timestampMillis - since >= holdMillis) {
                    fired = true
                    return true
                }
            }
            else -> faceDownSince = null
        }
        return false
    }
}

/** Ignores repeated media-key presses (one press can arrive as several events). */
internal class KeyDebouncer(private val windowMillis: Long = 800L) {
    private var last = Long.MIN_VALUE / 2

    fun accept(nowMillis: Long): Boolean {
        if (nowMillis - last < windowMillis) return false
        last = nowMillis
        return true
    }
}
