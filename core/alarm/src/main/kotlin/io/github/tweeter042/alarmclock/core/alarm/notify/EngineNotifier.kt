package io.github.tweeter042.alarmclock.core.alarm.notify

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.tweeter042.alarmclock.core.alarm.EngineClock
import io.github.tweeter042.alarmclock.core.alarm.EngineLog
import io.github.tweeter042.alarmclock.core.alarm.R
import io.github.tweeter042.alarmclock.core.alarm.schedule.CodeBlock
import io.github.tweeter042.alarmclock.core.alarm.schedule.RequestCodes
import io.github.tweeter042.alarmclock.core.alarm.schedule.TriggerPayload
import io.github.tweeter042.alarmclock.core.model.alarm.ActiveRing
import io.github.tweeter042.alarmclock.core.model.alarm.Alarm
import io.github.tweeter042.alarmclock.core.model.alarm.NextAlarm
import io.github.tweeter042.alarmclock.core.model.alarm.RingKind
import io.github.tweeter042.alarmclock.core.model.alarm.RingState
import io.github.tweeter042.alarmclock.core.model.alarm.SnoozePolicy
import io.github.tweeter042.alarmclock.core.model.engine.RingUiLauncher
import io.github.tweeter042.alarmclock.core.model.settings.AppSettings

/** Notification ids and tags. Tag-less ids are kept apart from the ring ids (1000–1999). */
internal object NotificationIds {
    const val RESTORED = 3001
    const val GUARD = 3002
    const val BRIEFING = 3003

    const val TAG_SNOOZED = "engine_snoozed"
    const val TAG_UPCOMING = "engine_upcoming"
    const val TAG_SUNRISE = "engine_sunrise"
    const val TAG_MISSED = "engine_missed"
    const val TAG_AUTO_STOPPED = "engine_auto_stopped"

    const val EXTRA_ALARM_ID = "io.github.tweeter042.alarmclock.engine.notification.ALARM_ID"
    const val EXTRA_OCCURRENCE_AT = "io.github.tweeter042.alarmclock.engine.notification.OCCURRENCE_AT"

    /**
     * Foreground notification of a ring. A new [generation] gives the same ring a new id: posting a
     * *new* notification is what makes the system launch the full-screen intent again.
     */
    fun ring(ringId: String, generation: Int = 0): Int = 1000 + Math.floorMod(ringId.hashCode() * 31 + generation, 1000)

    fun forRing(ringId: String): Int = ringId.hashCode()

    fun forAlarm(alarmId: Long): Int = (alarmId xor (alarmId ushr 32)).toInt()
}

/** Builds and posts every notification of the engine. */
internal class EngineNotifier(
    private val context: Context,
    private val ringUi: RingUiLauncher,
    private val clock: EngineClock,
) {
    private val manager = NotificationManagerCompat.from(context)

    // --- PendingIntents ---------------------------------------------------------------------

    fun ringScreen(ringId: String): PendingIntent = activity(
        RequestCodes.ui(CodeBlock.UI_RING_SCREEN, ringId),
        ringUi.ringScreenIntent(context, ringId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
    )

    fun mainScreen(): PendingIntent = activity(
        RequestCodes.ui(CodeBlock.UI_MAIN, "main"),
        ringUi.mainIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    /** The `showIntent` of setAlarmClock (what the system opens from the next-alarm UI). */
    fun alarmClockShowIntent(): PendingIntent = activity(
        RequestCodes.ui(CodeBlock.UI_SHOW_ALARM, "show"),
        ringUi.mainIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    private fun editor(alarmId: Long): PendingIntent = activity(
        RequestCodes.ui(CodeBlock.UI_EDITOR, alarmId.toString()),
        ringUi.alarmEditorIntent(context, alarmId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    private fun sunriseScreen(alarmId: Long, occurrenceAt: Long): PendingIntent = activity(
        RequestCodes.ui(CodeBlock.UI_SUNRISE, "$alarmId/$occurrenceAt"),
        ringUi.sunriseIntent(context, alarmId, occurrenceAt).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
    )

    private fun activity(requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    // --- Ringing (foreground service notifications) ------------------------------------------

    /** The foreground notification of a sounding ring: full-screen intent → ring screen. */
    fun ring(ring: ActiveRing, settings: AppSettings, watchSilent: Boolean = false): Notification {
        val format = EngineFormat(context, settings, clock.zone())
        val awake = ring.state == RingState.AWAKE_CHECK
        val channel = when {
            awake -> EngineChannels.AWAKE
            ring.kind == RingKind.TIMER -> EngineChannels.TIMERS
            else -> EngineChannels.ALARMS
        }
        val screen = ringScreen(ring.ringId)
        val text = when {
            awake -> context.getString(R.string.engine_awake_text)
            ring.kind == RingKind.TIMER -> context.getString(R.string.engine_ring_text_timer)
            watchSilent -> context.getString(R.string.engine_ring_text_watch)
            ring.state == RingState.MISSION -> context.getString(R.string.engine_ring_text_mission)
            else -> context.getString(R.string.engine_ring_text_time, format.time(displayTime(ring)))
        }
        val builder = ringBuilder(channel, settings, ring.alarm)
            .setContentTitle(if (awake) context.getString(R.string.engine_awake_title) else titleOf(ring))
            .setContentText(text)
            .setContentIntent(screen)
            .setFullScreenIntent(screen, true)
            .setWhen(ring.startedAt)

        if (awake) {
            builder.addAction(0, context.getString(R.string.engine_action_awake), NotificationActions.ring(context, NotificationActions.ACTION_CONFIRM_AWAKE, ring.ringId))
        } else {
            snoozeAction(ring)?.let { builder.addAction(it) }
            builder.addAction(dismissAction(ring))
        }
        return builder.build()
    }

    /**
     * Shown the instant a ring trigger fires, before the ring is loaded (startForeground must be
     * immediate). It already carries the full-screen intent for [ringId].
     */
    fun starting(payload: TriggerPayload?, ringId: String?, settings: AppSettings): Notification {
        val format = EngineFormat(context, settings, clock.zone())
        val kind = payload?.ringKind ?: RingKind.ALARM
        val title = payload?.label?.takeIf { it.isNotBlank() } ?: kindTitle(kind)
        val time = payload?.occurrenceAt?.takeIf { it > 0 && kind != RingKind.TIMER } ?: clock.now()
        val builder = ringBuilder(if (kind == RingKind.TIMER) EngineChannels.TIMERS else EngineChannels.ALARMS, settings, null)
            .setContentTitle(title)
            .setContentText(
                if (kind == RingKind.TIMER) context.getString(R.string.engine_ring_text_timer)
                else context.getString(R.string.engine_ring_text_time, format.time(time)),
            )
        if (ringId != null) {
            val screen = ringScreen(ringId)
            builder.setContentIntent(screen).setFullScreenIntent(screen, true)
        } else {
            builder.setContentIntent(mainScreen())
        }
        return builder.build()
    }

    /** While the morning briefing is read after dismissing (foreground, silent). */
    fun briefing(settings: AppSettings): Notification =
        ringBuilder(EngineChannels.ALARMS, settings, null)
            .setContentTitle(context.getString(R.string.engine_briefing_title))
            .setContentText(context.getString(R.string.engine_briefing_text))
            .setContentIntent(mainScreen())
            .setSilent(true)
            .addAction(0, context.getString(R.string.engine_action_stop), NotificationActions.stopBriefing(context))
            .build()

    private fun ringBuilder(channel: String, settings: AppSettings, alarm: Alarm?): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.engine_ic_notification)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setLocalOnly(true) // the watch app shows its own ring UI
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .also { builder ->
                if (settings.samsung.edgeLightingThemeColor) ThemeAccent.argb(context, settings, alarm)?.let { builder.setColor(it) }
            }

    private fun snoozeAction(ring: ActiveRing): NotificationCompat.Action? = when {
        ring.kind == RingKind.TIMER -> NotificationCompat.Action(
            0, context.getString(R.string.engine_action_add_minute),
            NotificationActions.ring(context, NotificationActions.ACTION_SNOOZE, ring.ringId),
        )
        !SnoozePolicy.canSnooze(ring.alarm.snooze, ring.snoozeCount) -> null
        // Missions first: a broadcast cannot open the missions (no activity trampolines).
        SnoozePolicy.missionsRequiredToSnooze(ring.alarm) ->
            NotificationCompat.Action(0, context.getString(R.string.engine_action_snooze), ringScreen(ring.ringId))
        else -> NotificationCompat.Action(
            0, context.getString(R.string.engine_action_snooze),
            NotificationActions.ring(context, NotificationActions.ACTION_SNOOZE, ring.ringId),
        )
    }

    private fun dismissAction(ring: ActiveRing): NotificationCompat.Action = when {
        ring.kind == RingKind.TIMER -> NotificationCompat.Action(
            0, context.getString(R.string.engine_action_stop),
            NotificationActions.ring(context, NotificationActions.ACTION_DISMISS, ring.ringId),
        )
        // Missions cannot be skipped from the notification: open the ring screen instead.
        ring.hasMissions -> NotificationCompat.Action(0, context.getString(R.string.engine_action_dismiss), ringScreen(ring.ringId))
        else -> NotificationCompat.Action(
            0, context.getString(R.string.engine_action_dismiss),
            NotificationActions.ring(context, NotificationActions.ACTION_DISMISS, ring.ringId),
        )
    }

    // --- Snoozed ------------------------------------------------------------------------------

    fun showSnoozed(ring: ActiveRing, settings: AppSettings) {
        val until = ring.snoozedUntil ?: return
        val format = EngineFormat(context, settings, clock.zone())
        val notification = NotificationCompat.Builder(context, EngineChannels.SNOOZED)
            .setSmallIcon(R.drawable.engine_ic_notification)
            .setContentTitle(context.getString(R.string.engine_snoozed_title, format.time(until)))
            .setContentText(titleOf(ring))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setLocalOnly(true)
            .setContentIntent(ringScreen(ring.ringId))
            .addAction(dismissAction(ring))
            .build()
        post(NotificationIds.TAG_SNOOZED, NotificationIds.forRing(ring.ringId), notification)
    }

    fun cancelSnoozed(ringId: String) = cancel(NotificationIds.TAG_SNOOZED, NotificationIds.forRing(ringId))

    // --- Pre-alarm notices --------------------------------------------------------------------

    fun showUpcoming(alarm: Alarm, occurrenceAt: Long, settings: AppSettings) {
        val format = EngineFormat(context, settings, clock.zone())
        val time = format.time(occurrenceAt)
        val title = if (alarm.label.isBlank()) {
            context.getString(R.string.engine_upcoming_title, time)
        } else {
            context.getString(R.string.engine_upcoming_title_label, time, alarm.label)
        }
        val notification = NotificationCompat.Builder(context, EngineChannels.UPCOMING)
            .setSmallIcon(R.drawable.engine_ic_notification)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.engine_upcoming_text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setAutoCancel(true)
            .setLocalOnly(true)
            .setTimeoutAfter((occurrenceAt - clock.now()).coerceAtLeast(1_000L))
            .setContentIntent(editor(alarm.id))
            .addAction(0, context.getString(R.string.engine_action_dismiss_now), NotificationActions.dismissUpcoming(context, alarm.id, occurrenceAt))
            .addExtras(noticeExtras(alarm.id, occurrenceAt))
            .build()
        post(NotificationIds.TAG_UPCOMING, NotificationIds.forAlarm(alarm.id), notification)
    }

    fun showSunrise(alarm: Alarm, occurrenceAt: Long, settings: AppSettings) {
        val format = EngineFormat(context, settings, clock.zone())
        val screen = sunriseScreen(alarm.id, occurrenceAt)
        val notification = NotificationCompat.Builder(context, EngineChannels.SUNRISE)
            .setSmallIcon(R.drawable.engine_ic_notification)
            .setContentTitle(context.getString(R.string.engine_sunrise_title))
            .setContentText(context.getString(R.string.engine_sunrise_text, format.time(occurrenceAt)))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setLocalOnly(true)
            .setTimeoutAfter((occurrenceAt - clock.now() + SUNRISE_GRACE_MILLIS).coerceAtLeast(1_000L))
            .setContentIntent(screen)
            .setFullScreenIntent(screen, true)
            .addExtras(noticeExtras(alarm.id, occurrenceAt))
            .also { builder ->
                if (settings.samsung.edgeLightingThemeColor) ThemeAccent.argb(context, settings, alarm)?.let { builder.setColor(it) }
            }
            .build()
        post(NotificationIds.TAG_SUNRISE, NotificationIds.forAlarm(alarm.id), notification)
    }

    fun cancelPreAlarmNotices(alarmId: Long) {
        cancel(NotificationIds.TAG_UPCOMING, NotificationIds.forAlarm(alarmId))
        cancel(NotificationIds.TAG_SUNRISE, NotificationIds.forAlarm(alarmId))
    }

    /** (alarm id, occurrence) of every upcoming / sunrise notice currently shown. */
    fun shownPreAlarmNotices(): List<Pair<Long, Long>> = try {
        context.getSystemService(NotificationManager::class.java)?.activeNotifications.orEmpty()
            .filter { it.tag == NotificationIds.TAG_UPCOMING || it.tag == NotificationIds.TAG_SUNRISE }
            .mapNotNull { sbn ->
                val extras = sbn.notification.extras
                val alarmId = extras.getLong(NotificationIds.EXTRA_ALARM_ID, Long.MIN_VALUE)
                val occurrence = extras.getLong(NotificationIds.EXTRA_OCCURRENCE_AT, Long.MIN_VALUE)
                if (alarmId == Long.MIN_VALUE || occurrence == Long.MIN_VALUE) null else alarmId to occurrence
            }
    } catch (t: Throwable) {
        emptyList()
    }

    // --- Missed / stopped / restored ------------------------------------------------------------

    fun showMissed(kind: RingKind, sourceId: Long, label: String, occurrenceAt: Long, settings: AppSettings) {
        val format = EngineFormat(context, settings, clock.zone())
        val text = context.getString(R.string.engine_missed_text)
        val notification = NotificationCompat.Builder(context, EngineChannels.MISSED)
            .setSmallIcon(R.drawable.engine_ic_notification)
            .setContentTitle(context.getString(R.string.engine_missed_title, format.dayAndTime(occurrenceAt, clock.now())))
            .setContentText(label.ifBlank { text })
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (label.isBlank()) text else "$label\n$text"))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setLocalOnly(true)
            .setContentIntent(mainScreen())
            .build()
        post(NotificationIds.TAG_MISSED, "$kind:$sourceId:$occurrenceAt".hashCode(), notification)
    }

    fun showAutoStopped(ring: ActiveRing) {
        val minutes = ring.alarm.dismiss.ringDurationMinutes.coerceAtLeast(1)
        val text = context.resources.getQuantityString(R.plurals.engine_auto_stopped_text, minutes, minutes)
        val notification = NotificationCompat.Builder(context, EngineChannels.MISSED)
            .setSmallIcon(R.drawable.engine_ic_notification)
            .setContentTitle(context.getString(R.string.engine_auto_stopped_title))
            .setContentText(text)
            .setSubText(ring.label.takeIf { it.isNotBlank() })
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setLocalOnly(true)
            .setContentIntent(mainScreen())
            .build()
        post(NotificationIds.TAG_AUTO_STOPPED, NotificationIds.forRing(ring.ringId), notification)
    }

    fun showRestored() {
        val text = context.getString(R.string.engine_restored_text)
        val notification = NotificationCompat.Builder(context, EngineChannels.MISSED)
            .setSmallIcon(R.drawable.engine_ic_notification)
            .setContentTitle(context.getString(R.string.engine_restored_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setLocalOnly(true)
            .setContentIntent(mainScreen())
            .build()
        post(null, NotificationIds.RESTORED, notification)
    }

    // --- Guard --------------------------------------------------------------------------------

    fun guard(next: NextAlarm?, settings: AppSettings): Notification {
        val format = EngineFormat(context, settings, clock.zone())
        return NotificationCompat.Builder(context, EngineChannels.GUARD)
            .setSmallIcon(R.drawable.engine_ic_notification)
            .setContentTitle(context.getString(R.string.engine_guard_title))
            .setContentText(
                next?.let { context.getString(R.string.engine_guard_text_next, format.dayAndTime(it.triggerAt, clock.now())) }
                    ?: context.getString(R.string.engine_guard_text_none),
            )
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setLocalOnly(true)
            .setContentIntent(mainScreen())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    // --- Posting ------------------------------------------------------------------------------

    /** Posts without a foreground service (fallback when the ring service could not start). */
    fun postRing(id: Int, notification: Notification) = post(null, id, notification)

    fun cancelRing(id: Int) = cancel(null, id)

    private fun post(tag: String?, id: Int, notification: Notification) {
        try {
            if (tag == null) manager.notify(id, notification) else manager.notify(tag, id, notification)
        } catch (e: SecurityException) {
            EngineLog.engine.w("Notification not allowed", e)
        } catch (t: Throwable) {
            EngineLog.engine.e("Notification failed", t)
        }
    }

    private fun cancel(tag: String?, id: Int) {
        try {
            if (tag == null) manager.cancel(id) else manager.cancel(tag, id)
        } catch (t: Throwable) {
            EngineLog.engine.w("Notification cancel failed", t)
        }
    }

    // --- Text -----------------------------------------------------------------------------------

    fun titleOf(ring: ActiveRing): String = ring.label.ifBlank { kindTitle(ring.kind) }

    fun kindTitle(kind: RingKind): String = context.getString(
        when (kind) {
            RingKind.ALARM -> R.string.engine_ring_title_alarm
            RingKind.NAP -> R.string.engine_ring_title_nap
            RingKind.TEST -> R.string.engine_ring_title_test
            RingKind.TIMER -> R.string.engine_ring_title_timer
            RingKind.CALENDAR -> R.string.engine_ring_title_calendar
            RingKind.LOCATION -> R.string.engine_ring_title_location
        },
    )

    /** Alarms show their scheduled time; ad-hoc rings show when they started. */
    private fun displayTime(ring: ActiveRing): Long =
        if ((ring.kind == RingKind.ALARM || ring.kind == RingKind.CALENDAR) && ring.occurrenceAt > 0) ring.occurrenceAt else ring.startedAt

    private fun noticeExtras(alarmId: Long, occurrenceAt: Long) = Bundle().apply {
        putLong(NotificationIds.EXTRA_ALARM_ID, alarmId)
        putLong(NotificationIds.EXTRA_OCCURRENCE_AT, occurrenceAt)
    }

    private companion object {
        const val SUNRISE_GRACE_MILLIS = 2 * 60_000L
    }
}
