package io.github.tweeter042.alarmclock.feature.missions

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import io.github.tweeter042.alarmclock.core.ui.PlaceholderScreen
import io.github.tweeter042.alarmclock.core.ui.navigation.MissionAssetsRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.MissionPreviewRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.HandwritingModelsRoute

/** Registers every destination owned by :feature:missions. Called from the app's NavHost. */
@Suppress("UNUSED_PARAMETER")
fun NavGraphBuilder.missionsGraph(navController: NavHostController) {
    composable<MissionAssetsRoute> { PlaceholderScreen("MissionAssetsRoute") }
    composable<MissionPreviewRoute> { PlaceholderScreen("MissionPreviewRoute") }
    composable<HandwritingModelsRoute> { PlaceholderScreen("HandwritingModelsRoute") }
}
