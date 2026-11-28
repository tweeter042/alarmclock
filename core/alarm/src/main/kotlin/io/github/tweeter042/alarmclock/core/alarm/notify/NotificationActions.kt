package io.github.tweeter042.alarmclock.core.alarm.notify

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.tweeter042.alarmclock.core.alarm.receiver.NotificationActionReceiver

/** Broadcast actions of the engine's notification buttons (handled by NotificationActionReceiver). */
internal object NotificationActions {
    const val ACTION_SNOOZE = "io.github.tweeter042.alarmclock.engine.action.SNOOZE"
    const val ACTION_DISMISS = "io.github.tweeter042.alarmclock.engine.action.DISMISS"
    const val ACTION_DISMISS_UPCOMING = "io.github.tweeter042.alarmclock.engine.action.DISMISS_UPCOMING"
    const val ACTION_CONFIRM_AWAKE = "io.github.tweeter042.alarmclock.engine.action.CONFIRM_AWAKE"
    const val ACTION_STOP_BRIEFING = "io.github.tweeter042.alarmclock.engine.action.STOP_BRIEFING"

    val ALL: Set<String> = setOf(ACTION_SNOOZE, ACTION_DISMISS, ACTION_DISMISS_UPCOMING, ACTION_CONFIRM_AWAKE, ACTION_STOP_BRIEFING)

    const val EXTRA_RING_ID = "io.github.tweeter042.alarmclock.engine.extra.RING_ID"
    const val EXTRA_ALARM_ID = "io.github.tweeter042.alarmclock.engine.extra.ALARM_ID"
    const val EXTRA_OCCURRENCE_AT = "io.github.tweeter042.alarmclock.engine.extra.OCCURRENCE_AT"

    fun ring(context: Context, action: String, ringId: String): PendingIntent =
        broadcast(context, action, ringId) { putExtra(EXTRA_RING_ID, ringId) }

    fun dismissUpcoming(context: Context, alarmId: Long, occurrenceAt: Long): PendingIntent =
        broadcast(context, ACTION_DISMISS_UPCOMING, "$alarmId/$occurrenceAt") {
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_OCCURRENCE_AT, occurrenceAt)
        }

    fun stopBriefing(context: Context): PendingIntent = broadcast(context, ACTION_STOP_BRIEFING, "briefing") {}

    /** A data URI makes every (action, target) pair its own PendingIntent. */
    private inline fun broadcast(context: Context, action: String, key: String, extras: Intent.() -> Unit): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(action)
            .setData(Uri.Builder().scheme("alarmclock-engine").authority(action.substringAfterLast('.').lowercase()).appendPath(key).build())
            .apply(extras)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
