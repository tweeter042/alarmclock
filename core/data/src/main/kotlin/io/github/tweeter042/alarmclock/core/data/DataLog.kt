package io.github.tweeter042.alarmclock.core.data

import android.util.Log

/**
 * Logging for core:data (tag `AlarmClock/Data`).
 *
 * Never log model contents: alarm labels, codes, place names and tokens are personal data.
 * Callers log ids, counts and exception class names only.
 */
internal object DataLog {
    const val TAG = "AlarmClock/Data"

    fun d(message: String) {
        Log.d(TAG, message)
    }

    fun i(message: String) {
        Log.i(TAG, message)
    }

    fun w(message: String, error: Throwable? = null) {
        if (error == null) Log.w(TAG, message) else Log.w(TAG, message, error)
    }

    fun e(message: String, error: Throwable? = null) {
        if (error == null) Log.e(TAG, message) else Log.e(TAG, message, error)
    }

    /** Short description of [error] that never includes its message (which may quote user data). */
    fun describe(error: Throwable): String = error.javaClass.simpleName.ifEmpty { error.javaClass.name }
}
