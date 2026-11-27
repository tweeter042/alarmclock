package io.github.tweeter042.alarmclock.feature.integrations

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import io.github.tweeter042.alarmclock.core.ui.PlaceholderScreen
import io.github.tweeter042.alarmclock.core.ui.navigation.WeatherSettingsRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.HolidaysRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.CalendarRulesRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.CalendarRuleEditRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.LocationAlarmsRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.LocationAlarmEditRoute

/** Registers every destination owned by :feature:integrations. Called from the app's NavHost. */
@Suppress("UNUSED_PARAMETER")
fun NavGraphBuilder.integrationsGraph(navController: NavHostController) {
    composable<WeatherSettingsRoute> { PlaceholderScreen("WeatherSettingsRoute") }
    composable<HolidaysRoute> { PlaceholderScreen("HolidaysRoute") }
    composable<CalendarRulesRoute> { PlaceholderScreen("CalendarRulesRoute") }
    composable<CalendarRuleEditRoute> { PlaceholderScreen("CalendarRuleEditRoute") }
    composable<LocationAlarmsRoute> { PlaceholderScreen("LocationAlarmsRoute") }
    composable<LocationAlarmEditRoute> { PlaceholderScreen("LocationAlarmEditRoute") }
}
