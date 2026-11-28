package io.github.tweeter042.alarmclock.core.alarm.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.tweeter042.alarmclock.core.alarm.alarmEngineOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Re-registers every trigger after LOCKED_BOOT_COMPLETED (Direct Boot: alarms ring before the
 * first unlock), BOOT_COMPLETED, MY_PACKAGE_REPLACED, clock and time-zone changes and exact-alarm
 * permission changes, then runs the integrity check. Missed alarms are handled by the engine's
 * start-up (the Application starts the engine before this receiver runs). Exported only because
 * these are system broadcasts; every other action is ignored.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED_ACTIONS) return
        val engine = context.alarmEngineOrNull() ?: return
        val pending = goAsync()
        engine.scope.launch {
            try {
                withTimeoutOrNull(BUDGET_MILLIS) { engine.onSystemEvent(action) }
            } finally {
                pending.finish()
            }
        }
    }

    internal companion object {
        val HANDLED_ACTIONS: Set<String> = setOf(
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
        private const val BUDGET_MILLIS = 9_000L
    }
}
