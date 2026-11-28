package io.github.tweeter042.alarmclock.core.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.tweeter042.alarmclock.core.alarm.alarmEngineOrNull
import io.github.tweeter042.alarmclock.core.alarm.notify.NotificationActions
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Buttons of the engine's notifications: Snooze, Dismiss, I'm awake, Dismiss now (upcoming alarm)
 * and Stop (briefing). Buttons that must open the ring screen (missions) are activity
 * PendingIntents instead and never come here.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in NotificationActions.ALL) return
        val engine = context.alarmEngineOrNull() ?: return
        val ringId = intent.getStringExtra(NotificationActions.EXTRA_RING_ID)
        val alarmId = intent.getLongExtra(NotificationActions.EXTRA_ALARM_ID, -1L)
        val occurrenceAt = intent.getLongExtra(NotificationActions.EXTRA_OCCURRENCE_AT, -1L)
        val pending = goAsync()
        engine.scope.launch {
            try {
                withTimeoutOrNull(BUDGET_MILLIS) {
                    when (action) {
                        NotificationActions.ACTION_SNOOZE -> ringId?.let { engine.snooze(it) }
                        NotificationActions.ACTION_DISMISS -> ringId?.let { engine.dismiss(it) }
                        NotificationActions.ACTION_CONFIRM_AWAKE -> ringId?.let { engine.confirmAwake(it) }
                        NotificationActions.ACTION_DISMISS_UPCOMING ->
                            if (alarmId >= 0) engine.dismissUpcoming(alarmId, occurrenceAt.takeIf { it > 0 })
                        NotificationActions.ACTION_STOP_BRIEFING -> engine.stopBriefing()
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val BUDGET_MILLIS = 9_000L
    }
}
