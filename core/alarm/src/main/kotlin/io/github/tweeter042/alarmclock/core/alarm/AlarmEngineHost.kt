package io.github.tweeter042.alarmclock.core.alarm

import android.content.Context

/**
 * Implemented by the Application. The engine's receivers, services and workers are created by the
 * system, so they find the single per-process [DefaultAlarmEngine] through
 * `(context.applicationContext as AlarmEngineHost).alarmEngine`.
 *
 * The property must be cheap and safe in Direct Boot (before the first unlock): build the engine
 * lazily from a `DataGraph` and never touch credential-encrypted storage while doing so.
 */
interface AlarmEngineHost {
    val alarmEngine: DefaultAlarmEngine
}

/** The engine of this process, or null if the Application does not implement [AlarmEngineHost]. */
internal fun Context.alarmEngineOrNull(): DefaultAlarmEngine? =
    try {
        (applicationContext as? AlarmEngineHost)?.alarmEngine
    } catch (t: Throwable) {
        EngineLog.engine.e("AlarmEngineHost failed to provide the engine", t)
        null
    }
