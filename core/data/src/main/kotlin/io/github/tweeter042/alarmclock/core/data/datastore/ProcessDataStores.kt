package io.github.tweeter042.alarmclock.core.data.datastore

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import io.github.tweeter042.alarmclock.core.data.DeviceStorage
import io.github.tweeter042.alarmclock.core.model.entity.StopwatchState
import io.github.tweeter042.alarmclock.core.model.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Process-wide DataStore instances. DataStore must never have two live instances for the same
 * file in one process, so these are created once and shared by every [io.github.tweeter042.alarmclock.core.data.DataGraph].
 * Files live in device-protected storage (see [DeviceStorage]).
 */
internal object ProcessDataStores {
    private val lock = Any()
    private var scope = newScope()

    @Volatile
    private var settings: DataStore<AppSettings>? = null

    @Volatile
    private var stopwatch: DataStore<StopwatchState>? = null

    fun settings(context: Context): DataStore<AppSettings> =
        settings ?: synchronized(lock) {
            settings ?: jsonDataStore(
                name = "Settings",
                serializer = AppSettings.serializer(),
                defaultValue = AppSettings(),
                scope = scope,
                produceFile = { DeviceStorage.dataStoreFile(context, DeviceStorage.SETTINGS_FILE) },
            ).also { settings = it }
        }

    fun stopwatch(context: Context): DataStore<StopwatchState> =
        stopwatch ?: synchronized(lock) {
            stopwatch ?: jsonDataStore(
                name = "Stopwatch",
                serializer = StopwatchState.serializer(),
                defaultValue = StopwatchState(),
                scope = scope,
                produceFile = { DeviceStorage.dataStoreFile(context, DeviceStorage.STOPWATCH_FILE) },
            ).also { stopwatch = it }
        }

    /** Closes both stores and forgets them (tests only). */
    @VisibleForTesting
    fun reset() {
        synchronized(lock) {
            scope.cancel()
            scope = newScope()
            settings = null
            stopwatch = null
        }
    }

    private fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
