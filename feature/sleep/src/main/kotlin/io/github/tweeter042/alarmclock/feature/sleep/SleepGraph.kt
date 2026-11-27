package io.github.tweeter042.alarmclock.feature.sleep

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import io.github.tweeter042.alarmclock.core.ui.PlaceholderScreen
import io.github.tweeter042.alarmclock.core.ui.navigation.SleepRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.SleepSoundsRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.SleepStatsRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.BedtimeSettingsRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.WakeCalculatorRoute

/** Registers every destination owned by :feature:sleep. Called from the app's NavHost. */
@Suppress("UNUSED_PARAMETER")
fun NavGraphBuilder.sleepGraph(navController: NavHostController) {
    composable<SleepRoute> { PlaceholderScreen("SleepRoute") }
    composable<SleepSoundsRoute> { PlaceholderScreen("SleepSoundsRoute") }
    composable<SleepStatsRoute> { PlaceholderScreen("SleepStatsRoute") }
    composable<BedtimeSettingsRoute> { PlaceholderScreen("BedtimeSettingsRoute") }
    composable<WakeCalculatorRoute> { PlaceholderScreen("WakeCalculatorRoute") }
}
