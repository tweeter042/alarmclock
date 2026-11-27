package io.github.tweeter042.alarmclock.feature.connected

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import io.github.tweeter042.alarmclock.core.ui.PlaceholderScreen
import io.github.tweeter042.alarmclock.core.ui.navigation.SmartThingsRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.WatchRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.HealthConnectRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.MusicAppsRoute

/** Registers every destination owned by :feature:connected. Called from the app's NavHost. */
@Suppress("UNUSED_PARAMETER")
fun NavGraphBuilder.connectedGraph(navController: NavHostController) {
    composable<SmartThingsRoute> { PlaceholderScreen("SmartThingsRoute") }
    composable<WatchRoute> { PlaceholderScreen("WatchRoute") }
    composable<HealthConnectRoute> { PlaceholderScreen("HealthConnectRoute") }
    composable<MusicAppsRoute> { PlaceholderScreen("MusicAppsRoute") }
}
