package io.github.tweeter042.alarmclock.core.alarm.guard

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.tweeter042.alarmclock.core.alarm.DefaultAlarmEngine
import io.github.tweeter042.alarmclock.core.alarm.EngineLog
import io.github.tweeter042.alarmclock.core.alarm.alarmEngineOrNull
import io.github.tweeter042.alarmclock.core.alarm.notify.NotificationIds
import io.github.tweeter042.alarmclock.core.model.alarm.NextAlarm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Optional always-on foreground service (settings.reliability.guardServiceEnabled): a permanent,
 * minimised "Alarm guard active · next alarm 06:30" notification that keeps the process in a
 * higher priority class on devices with aggressive battery management. It does no work and holds
 * no wake lock; it only follows the engine's next alarm. Started at app start and boot when
 * enabled, stopped when disabled.
 */
class AlarmGuardService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var engine: DefaultAlarmEngine? = null
    private var foreground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        val engine = applicationContext.alarmEngineOrNull()
        this.engine = engine
        if (engine != null) {
            scope.launch { engine.nextAlarm.collect { next -> if (foreground) show(next) } }
            scope.launch {
                if (!engine.currentSettings().reliability.guardServiceEnabled) stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        show(engine?.nextAlarm?.value)
        if (engine == null) stopSelf()
        return START_STICKY
    }

    private fun show(next: NextAlarm?) {
        val engine = engine
        try {
            val notification = if (engine != null) {
                engine.notifier.guard(next, engine.settingsSnapshot())
            } else {
                return
            }
            ServiceCompat.startForeground(this, NotificationIds.GUARD, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            foreground = true
        } catch (t: Throwable) {
            EngineLog.reliability.w("Alarm guard could not go foreground", t)
            stopSelf()
        }
    }

    override fun onDestroy() {
        isRunning = false
        foreground = false
        scope.cancel()
        super.onDestroy()
    }

    internal companion object {
        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, AlarmGuardService::class.java))
            true
        } catch (t: Throwable) {
            // Background start refused (e.g. the process was started by a broadcast without an exemption).
            EngineLog.reliability.w("Alarm guard could not be started now", t)
            false
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, AlarmGuardService::class.java)) }
        }
    }
}
