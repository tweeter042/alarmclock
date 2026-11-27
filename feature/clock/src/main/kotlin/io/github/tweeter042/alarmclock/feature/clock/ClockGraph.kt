package io.github.tweeter042.alarmclock.feature.clock

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import io.github.tweeter042.alarmclock.core.ui.PlaceholderScreen
import io.github.tweeter042.alarmclock.core.ui.navigation.WorldClockRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.TimerRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.StopwatchRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.BedsideClockRoute

/** Registers every destination owned by :feature:clock. Called from the app's NavHost. */
@Suppress("UNUSED_PARAMETER")
fun NavGraphBuilder.clockGraph(navController: NavHostController) {
    composable<WorldClockRoute> { PlaceholderScreen("WorldClockRoute") }
    composable<TimerRoute> { PlaceholderScreen("TimerRoute") }
    composable<StopwatchRoute> { PlaceholderScreen("StopwatchRoute") }
    composable<BedsideClockRoute> { PlaceholderScreen("BedsideClockRoute") }
}
