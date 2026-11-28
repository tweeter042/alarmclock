package io.github.tweeter042.alarmclock.core.alarm.internal

import android.content.Context
import android.os.PowerManager
import io.github.tweeter042.alarmclock.core.alarm.EngineLog

/**
 * Wake locks of the ring path.
 *
 * Between AlarmTriggerReceiver returning and AlarmRingService taking its own wake lock the CPU could
 * otherwise sleep, so the receiver holds a short "startup" lock that the service releases.
 */
internal object WakeLocks {
    private const val STARTUP_TAG = "AlarmClock:RingStartup"
    private const val RING_TAG = "AlarmClock:Ring"
    const val STARTUP_TIMEOUT_MILLIS = 30_000L

    private val lock = Any()
    private var startup: PowerManager.WakeLock? = null

    fun acquireStartup(context: Context) {
        synchronized(lock) {
            try {
                val wakeLock = startup ?: newLock(context, STARTUP_TAG).also { startup = it }
                wakeLock?.acquire(STARTUP_TIMEOUT_MILLIS)
            } catch (t: Throwable) {
                EngineLog.ring.w("Startup wake lock failed", t)
            }
        }
    }

    fun releaseStartup() {
        synchronized(lock) {
            try {
                startup?.takeIf { it.isHeld }?.release()
            } catch (t: Throwable) {
                EngineLog.ring.w("Startup wake lock release failed", t)
            }
        }
    }

    /** A non-reference-counted partial wake lock for one ring session. */
    fun newRingLock(context: Context): PowerManager.WakeLock? = newLock(context, RING_TAG)

    private fun newLock(context: Context, tag: String): PowerManager.WakeLock? =
        context.applicationContext.getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag)
            ?.apply { setReferenceCounted(false) }
}
