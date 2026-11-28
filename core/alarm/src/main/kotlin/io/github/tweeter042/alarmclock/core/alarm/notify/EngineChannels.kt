package io.github.tweeter042.alarmclock.core.alarm.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import io.github.tweeter042.alarmclock.core.alarm.EngineLog
import io.github.tweeter042.alarmclock.core.alarm.R

/**
 * The engine's notification channels. Created (idempotently) by DefaultAlarmEngine.start().
 *
 * Ring channels have no sound and no vibration: the engine plays the alarm itself on the alarm
 * stream (USAGE_ALARM), so the channel must never add a second, DND-dependent sound. They are
 * IMPORTANCE_HIGH because full-screen intents need it.
 */
internal object EngineChannels {
    const val ALARMS = "engine_alarms"
    const val UPCOMING = "engine_upcoming"
    const val SNOOZED = "engine_snoozed"
    const val MISSED = "engine_missed"
    const val GUARD = "engine_guard"
    const val TIMERS = "engine_timers"
    const val AWAKE = "engine_awake"
    const val SUNRISE = "engine_sunrise"

    val ALL: List<String> = listOf(ALARMS, UPCOMING, SNOOZED, MISSED, GUARD, TIMERS, AWAKE, SUNRISE)

    fun createAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        try {
            manager.createNotificationChannels(
                listOf(
                    ringChannel(context, ALARMS, R.string.engine_channel_alarms_name, R.string.engine_channel_alarms_desc),
                    ringChannel(context, TIMERS, R.string.engine_channel_timers_name, R.string.engine_channel_timers_desc),
                    ringChannel(context, AWAKE, R.string.engine_channel_awake_name, R.string.engine_channel_awake_desc),
                    ringChannel(context, SUNRISE, R.string.engine_channel_sunrise_name, R.string.engine_channel_sunrise_desc),
                    quietChannel(
                        context, UPCOMING, R.string.engine_channel_upcoming_name, R.string.engine_channel_upcoming_desc,
                        NotificationManager.IMPORTANCE_LOW,
                    ),
                    quietChannel(
                        context, SNOOZED, R.string.engine_channel_snoozed_name, R.string.engine_channel_snoozed_desc,
                        NotificationManager.IMPORTANCE_LOW,
                    ),
                    NotificationChannel(MISSED, context.getString(R.string.engine_channel_missed_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = context.getString(R.string.engine_channel_missed_desc)
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    },
                    quietChannel(
                        context, GUARD, R.string.engine_channel_guard_name, R.string.engine_channel_guard_desc,
                        NotificationManager.IMPORTANCE_MIN,
                    ),
                ),
            )
        } catch (t: Throwable) {
            EngineLog.engine.e("Could not create notification channels", t)
        }
    }

    private fun ringChannel(context: Context, id: String, name: Int, description: Int) =
        NotificationChannel(id, context.getString(name), NotificationManager.IMPORTANCE_HIGH).apply {
            this.description = context.getString(description)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            setBypassDnd(true)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

    private fun quietChannel(context: Context, id: String, name: Int, description: Int, importance: Int) =
        NotificationChannel(id, context.getString(name), importance).apply {
            this.description = context.getString(description)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
}
