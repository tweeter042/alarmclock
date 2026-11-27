package io.github.tweeter042.alarmclock.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import io.github.tweeter042.alarmclock.core.ui.PlaceholderScreen
import io.github.tweeter042.alarmclock.core.ui.navigation.SettingsRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.AppearanceRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.LanguageRegionRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.AlarmDefaultsRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.ReliabilityRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.SamsungSetupRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.BackupRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.AccessibilityRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.MorningRoutineSettingsRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.IntegrationsRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.AboutRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.ChangelogRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.LicensesRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.OnboardingRoute

/** Registers every destination owned by :feature:settings. Called from the app's NavHost. */
@Suppress("UNUSED_PARAMETER")
fun NavGraphBuilder.settingsGraph(navController: NavHostController) {
    composable<SettingsRoute> { PlaceholderScreen("SettingsRoute") }
    composable<AppearanceRoute> { PlaceholderScreen("AppearanceRoute") }
    composable<LanguageRegionRoute> { PlaceholderScreen("LanguageRegionRoute") }
    composable<AlarmDefaultsRoute> { PlaceholderScreen("AlarmDefaultsRoute") }
    composable<ReliabilityRoute> { PlaceholderScreen("ReliabilityRoute") }
    composable<SamsungSetupRoute> { PlaceholderScreen("SamsungSetupRoute") }
    composable<BackupRoute> { PlaceholderScreen("BackupRoute") }
    composable<AccessibilityRoute> { PlaceholderScreen("AccessibilityRoute") }
    composable<MorningRoutineSettingsRoute> { PlaceholderScreen("MorningRoutineSettingsRoute") }
    composable<IntegrationsRoute> { PlaceholderScreen("IntegrationsRoute") }
    composable<AboutRoute> { PlaceholderScreen("AboutRoute") }
    composable<ChangelogRoute> { PlaceholderScreen("ChangelogRoute") }
    composable<LicensesRoute> { PlaceholderScreen("LicensesRoute") }
    composable<OnboardingRoute> { PlaceholderScreen("OnboardingRoute") }
}
