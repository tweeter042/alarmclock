package io.github.tweeter042.alarmclock.core.alarm.internal

import android.content.Context
import io.github.tweeter042.alarmclock.core.alarm.EngineClock
import io.github.tweeter042.alarmclock.core.alarm.EngineDependencies
import io.github.tweeter042.alarmclock.core.alarm.EngineLog
import io.github.tweeter042.alarmclock.core.alarm.notify.EngineNotifier
import io.github.tweeter042.alarmclock.core.alarm.schedule.AlarmScheduler
import io.github.tweeter042.alarmclock.core.data.DataGraph
import io.github.tweeter042.alarmclock.core.model.engine.AlarmEngineHooks
import io.github.tweeter042.alarmclock.core.model.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Everything the engine's parts share. One instance per engine (per process). */
internal class EngineEnv(
    val context: Context,
    val data: DataGraph,
    val deps: EngineDependencies,
    val clock: EngineClock,
    val scope: CoroutineScope,
    val prefs: EnginePrefs,
    val notifier: EngineNotifier,
    val scheduler: AlarmScheduler,
) {
    private val latestSettings = MutableStateFlow<AppSettings?>(null)

    /** Last settings read (null until the first read); for code that cannot suspend. */
    val settingsSnapshot: StateFlow<AppSettings?> get() = latestSettings

    fun rememberSettings(settings: AppSettings) {
        latestSettings.value = settings
    }

    /** Current settings; falls back to the last known (or default) settings if the store fails. */
    suspend fun settings(): AppSettings =
        guardedOrNull("Reading settings", SETTINGS_TIMEOUT_MILLIS, EngineLog.engine) { data.settings.current() }
            ?.also { latestSettings.value = it }
            ?: latestSettings.value
            ?: AppSettings()

    private val hookQueue = Channel<Pair<String, suspend AlarmEngineHooks.() -> Unit>>(Channel.UNLIMITED)

    init {
        scope.launch {
            for ((name, call) in hookQueue) {
                guarded("Hook $name", HOOK_TIMEOUT_MILLIS, EngineLog.engine) { deps.hooks.call() }
            }
        }
    }

    /**
     * Calls an app hook without blocking the caller: hooks run one at a time in the order they were
     * requested, each with a timeout; a failing hook is logged and never affects ringing.
     */
    fun hook(name: String, call: suspend AlarmEngineHooks.() -> Unit) {
        hookQueue.trySend(name to call)
    }

    /** Calls a hook and waits for it (bounded): used inside a broadcast's goAsync window. */
    suspend fun hookNow(name: String, timeoutMillis: Long = PRE_ALARM_HOOK_TIMEOUT_MILLIS, call: suspend AlarmEngineHooks.() -> Unit) {
        guarded("Hook $name", timeoutMillis, EngineLog.engine) { deps.hooks.call() }
    }

    companion object {
        const val SETTINGS_TIMEOUT_MILLIS = 3_000L
        const val HOOK_TIMEOUT_MILLIS = 10_000L
        const val PRE_ALARM_HOOK_TIMEOUT_MILLIS = 8_000L
    }
}
