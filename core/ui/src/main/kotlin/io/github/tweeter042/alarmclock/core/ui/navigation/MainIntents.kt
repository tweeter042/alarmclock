package io.github.tweeter042.alarmclock.core.ui.navigation

import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Intents that open the main activity on a specific screen. Used by notifications (core:alarm,
 * through the app's RingUiLauncher), widgets, Quick Settings tiles, app shortcuts and the watch
 * bridge. The app's MainActivity handles them in `onCreate` and `onNewIntent`.
 *
 * Extras:
 * - [EXTRA_OPEN_ALARM_ID] (Long, > 0): opens [AlarmEditRoute] for that alarm. Wins over the route.
 * - [EXTRA_OPEN_ROUTE] (String): one of
 *   [ROUTE_NEW_ALARM] → `AlarmEditRoute()` (optionally prefilled with [EXTRA_PRESET_HOUR] /
 *   [EXTRA_PRESET_MINUTE], Int), [ROUTE_TIMER], [ROUTE_STOPWATCH], [ROUTE_WORLD_CLOCK],
 *   [ROUTE_SLEEP] (bottom-bar tabs), [ROUTE_BEDSIDE] (night clock), [ROUTE_SETTINGS],
 *   [ROUTE_RELIABILITY] (health check).
 *
 * Unknown values are ignored (the app just opens). Before onboarding is finished, the requested
 * screen opens once onboarding completes.
 *
 * ```
 * val intent = MainIntents.openRoute(context, MainIntents.ROUTE_TIMER)
 * PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
 * ```
 */
object MainIntents {
    const val EXTRA_OPEN_ALARM_ID = "io.github.tweeter042.alarmclock.extra.OPEN_ALARM_ID"
    const val EXTRA_OPEN_ROUTE = "io.github.tweeter042.alarmclock.extra.OPEN_ROUTE"
    const val EXTRA_PRESET_HOUR = "io.github.tweeter042.alarmclock.extra.PRESET_HOUR"
    const val EXTRA_PRESET_MINUTE = "io.github.tweeter042.alarmclock.extra.PRESET_MINUTE"

    const val ROUTE_NEW_ALARM = "new_alarm"
    const val ROUTE_TIMER = "timer"
    const val ROUTE_STOPWATCH = "stopwatch"
    const val ROUTE_BEDSIDE = "bedside"
    const val ROUTE_SLEEP = "sleep"
    const val ROUTE_WORLD_CLOCK = "world_clock"
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_RELIABILITY = "reliability"

    /** Every value accepted in [EXTRA_OPEN_ROUTE]. */
    val ROUTES: List<String> = listOf(
        ROUTE_NEW_ALARM, ROUTE_TIMER, ROUTE_STOPWATCH, ROUTE_BEDSIDE, ROUTE_SLEEP, ROUTE_WORLD_CLOCK, ROUTE_SETTINGS, ROUTE_RELIABILITY,
    )

    /**
     * Class name of the app's single activity (in :app). Launcher icons are activity-aliases that
     * can be switched off, so intents always address the activity itself.
     */
    const val MAIN_ACTIVITY_CLASS = "io.github.tweeter042.alarmclock.MainActivity"

    /** Opens the app (alarm list, or the screen that was open). */
    fun main(context: Context): Intent =
        Intent(Intent.ACTION_MAIN)
            .setClassName(context, MAIN_ACTIVITY_CLASS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Opens the editor of alarm [alarmId]. */
    fun openAlarm(context: Context, alarmId: Long): Intent {
        require(alarmId > 0) { "alarmId must be a saved alarm id" }
        return main(context).putExtra(EXTRA_OPEN_ALARM_ID, alarmId)
    }

    /** Opens one of the [ROUTES]. */
    fun openRoute(context: Context, route: String): Intent {
        require(route in ROUTES) { "Unknown route: $route" }
        return main(context).putExtra(EXTRA_OPEN_ROUTE, route)
    }

    /** Opens the editor for a new alarm, optionally prefilled with a time. */
    fun newAlarm(context: Context, presetHour: Int? = null, presetMinute: Int? = null): Intent =
        openRoute(context, ROUTE_NEW_ALARM).apply {
            if (presetHour != null && presetMinute != null) {
                putExtra(EXTRA_PRESET_HOUR, presetHour)
                putExtra(EXTRA_PRESET_MINUTE, presetMinute)
            }
        }

    /** The destination requested by [intent], or null for a plain launch (or unreadable extras). */
    fun destinationOf(intent: Intent?): Any? =
        // Extras of an exported entry point may come from anywhere: never crash on them.
        runCatching { destinationOf(intent?.extras) }.getOrNull()

    fun destinationOf(extras: Bundle?): Any? {
        if (extras == null) return null
        return destinationOf(
            MainIntentExtras(
                alarmId = extras.getLong(EXTRA_OPEN_ALARM_ID, 0L).takeIf { extras.containsKey(EXTRA_OPEN_ALARM_ID) },
                route = extras.getString(EXTRA_OPEN_ROUTE),
                presetHour = extras.getInt(EXTRA_PRESET_HOUR, -1).takeIf { extras.containsKey(EXTRA_PRESET_HOUR) },
                presetMinute = extras.getInt(EXTRA_PRESET_MINUTE, -1).takeIf { extras.containsKey(EXTRA_PRESET_MINUTE) },
            ),
        )
    }

    /** Pure mapping of the extras to a navigation route (see the class documentation). */
    fun destinationOf(extras: MainIntentExtras): Any? {
        val alarmId = extras.alarmId
        if (alarmId != null && alarmId > 0) return AlarmEditRoute(alarmId = alarmId)
        return when (extras.route?.trim()?.lowercase()) {
            ROUTE_NEW_ALARM -> {
                val hour = extras.presetHour?.takeIf { it in 0..23 }
                val minute = extras.presetMinute?.takeIf { it in 0..59 }
                if (hour != null && minute != null) AlarmEditRoute(presetHour = hour, presetMinute = minute) else AlarmEditRoute()
            }
            ROUTE_TIMER -> TimerRoute
            ROUTE_STOPWATCH -> StopwatchRoute
            ROUTE_BEDSIDE -> BedsideClockRoute
            ROUTE_SLEEP -> SleepRoute
            ROUTE_WORLD_CLOCK -> WorldClockRoute
            ROUTE_SETTINGS -> SettingsRoute
            ROUTE_RELIABILITY -> ReliabilityRoute
            else -> null
        }
    }

    /** Removes the MainIntents extras so a recreated activity does not open the screen again. */
    fun consume(intent: Intent) {
        intent.removeExtra(EXTRA_OPEN_ALARM_ID)
        intent.removeExtra(EXTRA_OPEN_ROUTE)
        intent.removeExtra(EXTRA_PRESET_HOUR)
        intent.removeExtra(EXTRA_PRESET_MINUTE)
    }
}

/** The raw MainIntents extras (null = absent). */
data class MainIntentExtras(
    val alarmId: Long? = null,
    val route: String? = null,
    val presetHour: Int? = null,
    val presetMinute: Int? = null,
)
