package io.github.tweeter042.alarmclock.feature.alarms

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import io.github.tweeter042.alarmclock.core.ui.PlaceholderScreen
import io.github.tweeter042.alarmclock.core.ui.navigation.AlarmsRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.AlarmEditRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.AlarmGroupsRoute

/** Registers every destination owned by :feature:alarms. Called from the app's NavHost. */
@Suppress("UNUSED_PARAMETER")
fun NavGraphBuilder.alarmsGraph(navController: NavHostController) {
    composable<AlarmsRoute> { PlaceholderScreen("AlarmsRoute") }
    composable<AlarmEditRoute> { PlaceholderScreen("AlarmEditRoute") }
    composable<AlarmGroupsRoute> { PlaceholderScreen("AlarmGroupsRoute") }
}
