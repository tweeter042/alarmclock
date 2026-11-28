package io.github.tweeter042.alarmclock.core.alarm.internal

import io.github.tweeter042.alarmclock.core.alarm.EngineLog
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException

/** Outcome of a guarded call: the value, or why there is none. */
internal sealed interface Guarded<out T> {
    data class Ok<T>(val value: T) : Guarded<T>
    data class Failed(val error: Throwable?) : Guarded<Nothing>

    fun getOrNull(): T? = (this as? Ok<T>)?.value
}

/**
 * Runs [block] with a timeout and a catch-all, so an integration (weather, calendar, watch, TTS)
 * or a slow disk can never stop or delay the ring path. Cancellation of the caller still propagates.
 */
internal suspend fun <T> guarded(what: String, timeoutMillis: Long, log: EngineLog, block: suspend () -> T): Guarded<T> =
    try {
        Guarded.Ok(withTimeout(timeoutMillis) { block() })
    } catch (e: TimeoutCancellationException) {
        log.w("$what timed out after $timeoutMillis ms")
        Guarded.Failed(e)
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        log.w("$what failed", t)
        Guarded.Failed(t)
    }

/** [guarded] returning null on failure. */
internal suspend fun <T> guardedOrNull(what: String, timeoutMillis: Long, log: EngineLog, block: suspend () -> T): T? =
    guarded(what, timeoutMillis, log, block).getOrNull()
