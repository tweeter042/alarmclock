package io.github.tweeter042.alarmclock

import android.app.Application
import android.os.UserManager
import android.util.Log
import androidx.work.Configuration
import io.github.tweeter042.alarmclock.core.alarm.AlarmEngineHost
import io.github.tweeter042.alarmclock.core.alarm.DefaultAlarmEngine
import io.github.tweeter042.alarmclock.core.data.DeviceStorage
import io.github.tweeter042.alarmclock.core.model.AppGraph
import io.github.tweeter042.alarmclock.core.model.AppGraphProvider
import io.github.tweeter042.alarmclock.core.ui.locale.AppLocales
import kotlinx.coroutines.launch

/**
 * Application entry point. Builds the manual dependency graph lazily.
 *
 * Direct Boot: this class also runs before the first unlock after a reboot (for the alarm
 * receivers); [alarmEngine] starts in both states. Nothing here may touch credential-encrypted
 * storage then: the graph is lazy, and WorkManager (whose database is credential-encrypted) initializes on demand through
 * [workManagerConfiguration] instead of at process start (its startup initializer is removed in
 * the manifest).
 */
class AlarmClockApplication : Application(), AppGraphProvider, AlarmEngineHost, Configuration.Provider {

    private val graph: DefaultAppGraph by lazy { AppGraphFactory.create(this) }

    override val appGraph: AppGraph get() = graph

    /** The single per-process alarm engine (also used by the engine's receivers and services). */
    override val alarmEngine: DefaultAlarmEngine get() = graph.alarmEngine

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.WARN)
            .setInitializationExceptionHandler { error ->
                Log.e(TAG, "WorkManager could not initialize (${error.javaClass.simpleName})")
            }
            .build()

    override fun onCreate() {
        super.onCreate()
        // Direct Boot safe and returns immediately: restores rings, reschedules, repairs.
        alarmEngine.start()
        if (isUserUnlocked()) syncLanguage()
    }

    /**
     * English is the default language: a first start on a Thai phone still opens in English
     * (decided synchronously, before any activity is created), and the effective language is
     * mirrored into the settings for code that runs without a UI.
     */
    private fun syncLanguage() {
        try {
            val hasSettings = DeviceStorage.dataStoreFile(this, DeviceStorage.SETTINGS_FILE).exists()
            AppLocales.applyFirstRunDefault(this, hasStoredSettings = hasSettings)
        } catch (e: RuntimeException) {
            Log.w(TAG, "First-run language check failed (${e.javaClass.simpleName})")
        }
        appGraph.appScope.launch { AppLocales.syncWithSettings(this@AlarmClockApplication, appGraph.settings) }
    }

    private fun isUserUnlocked(): Boolean = getSystemService(UserManager::class.java)?.isUserUnlocked ?: true

    private companion object {
        const val TAG = "AlarmClock/App"
    }
}
