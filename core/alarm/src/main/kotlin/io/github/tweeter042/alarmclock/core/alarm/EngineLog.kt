package io.github.tweeter042.alarmclock.core.alarm

import android.util.Log

/**
 * Engine logging (`AlarmClock/<Area>`). Never log personal data: no labels, event titles, places
 * or URIs — only ids, kinds, times and exception class names.
 */
internal class EngineLog private constructor(private val tag: String) {

    fun d(message: String) {
        runCatching { Log.d(tag, message) }
    }

    fun i(message: String) {
        runCatching { Log.i(tag, message) }
    }

    fun w(message: String, error: Throwable? = null) {
        runCatching { if (error == null) Log.w(tag, message) else Log.w(tag, "$message (${describe(error)})") }
    }

    fun e(message: String, error: Throwable? = null) {
        runCatching { if (error == null) Log.e(tag, message) else Log.e(tag, "$message (${describe(error)})", error) }
    }

    companion object {
        val engine = EngineLog("AlarmClock/Engine")
        val schedule = EngineLog("AlarmClock/Schedule")
        val ring = EngineLog("AlarmClock/Ring")
        val audio = EngineLog("AlarmClock/Audio")
        val reliability = EngineLog("AlarmClock/Reliability")

        /** Class name only: exception messages can quote user data (labels, URIs). */
        fun describe(error: Throwable): String = error.javaClass.simpleName.ifEmpty { error.javaClass.name }
    }
}
