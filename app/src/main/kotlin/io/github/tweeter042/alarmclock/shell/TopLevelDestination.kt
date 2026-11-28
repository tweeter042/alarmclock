package io.github.tweeter042.alarmclock.shell

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.tweeter042.alarmclock.R
import io.github.tweeter042.alarmclock.core.designsystem.icon.AppIcons
import io.github.tweeter042.alarmclock.core.ui.navigation.AlarmsRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.SleepRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.StopwatchRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.TimerRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.TopLevelRoutes
import io.github.tweeter042.alarmclock.core.ui.navigation.WorldClockRoute

/** The bottom-bar tabs, in the order of [TopLevelRoutes.all]. */
internal enum class TopLevelDestination(val route: Any, @StringRes val labelRes: Int) {
    ALARMS(AlarmsRoute, R.string.app_nav_alarms),
    WORLD_CLOCK(WorldClockRoute, R.string.app_nav_world_clock),
    TIMER(TimerRoute, R.string.app_nav_timer),
    STOPWATCH(StopwatchRoute, R.string.app_nav_stopwatch),
    SLEEP(SleepRoute, R.string.app_nav_sleep),
    ;

    /** Icon in the style of the current theme. */
    val icon: ImageVector
        @Composable get() = when (this) {
            ALARMS -> AppIcons.Alarm
            WORLD_CLOCK -> AppIcons.WorldClock
            TIMER -> AppIcons.Hourglass
            STOPWATCH -> AppIcons.Stopwatch
            SLEEP -> AppIcons.Bedtime
        }
}
