package io.github.tweeter042.alarmclock.core.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.tweeter042.alarmclock.core.alarm.EngineLog
import io.github.tweeter042.alarmclock.core.alarm.alarmEngineOrNull
import io.github.tweeter042.alarmclock.core.alarm.internal.WakeLocks
import io.github.tweeter042.alarmclock.core.alarm.ring.AlarmRingService
import io.github.tweeter042.alarmclock.core.alarm.schedule.TriggerIntents
import io.github.tweeter042.alarmclock.core.alarm.schedule.isRingType
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Receives every trigger registered by the engine (not exported; directBootAware).
 *
 * - Ring triggers start AlarmRingService immediately (the alarm broadcast carries the exemption that
 *   allows a foreground-service start from the background), holding a short wake lock until the
 *   service has its own. Should Android still refuse, the engine rings without the service: it
 *   posts the full-screen notification and retries the start with a one-second exact alarm.
 * - Pre-alarm events run inside the goAsync window, every step bounded by a timeout.
 */
class AlarmTriggerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TriggerIntents.ACTION_TRIGGER) return
        val payload = TriggerIntents.parse(intent) ?: return
        if (payload.kind.isRingType) {
            WakeLocks.acquireStartup(context)
            if (AlarmRingService.startForTrigger(context, payload)) return
            val engine = context.alarmEngineOrNull() ?: return
            val pending = goAsync()
            engine.scope.launch {
                try {
                    withTimeoutOrNull(BUDGET_MILLIS) { engine.onRingTrigger(payload, preRingId = null) }
                } finally {
                    pending.finish()
                }
            }
            return
        }
        val engine = context.alarmEngineOrNull() ?: return
        val pending = goAsync()
        engine.scope.launch {
            try {
                withTimeoutOrNull(BUDGET_MILLIS) { engine.onPreAlarmTrigger(payload) }
                    ?: EngineLog.schedule.w("Pre-alarm event did not finish within the broadcast window")
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        /** goAsync() work must finish well within the ~10 s broadcast timeout. */
        const val BUDGET_MILLIS = 9_000L
    }
}
