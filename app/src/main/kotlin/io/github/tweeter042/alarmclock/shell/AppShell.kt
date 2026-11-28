package io.github.tweeter042.alarmclock.shell

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.tweeter042.alarmclock.core.designsystem.component.AppNavigationBar
import io.github.tweeter042.alarmclock.core.designsystem.component.AppNavigationItem
import io.github.tweeter042.alarmclock.core.designsystem.component.ThemedBackground
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import io.github.tweeter042.alarmclock.core.designsystem.theme.MotionSpec
import io.github.tweeter042.alarmclock.core.ui.LocalAppSettings
import io.github.tweeter042.alarmclock.core.ui.navigation.AlarmsRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.ChangelogRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.OnboardingRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.TopLevelRoutes
import io.github.tweeter042.alarmclock.core.ui.navigation.isRoute
import io.github.tweeter042.alarmclock.core.ui.navigation.isTopLevel
import io.github.tweeter042.alarmclock.core.ui.navigation.navigateToTopLevel
import io.github.tweeter042.alarmclock.feature.alarms.alarmsGraph
import io.github.tweeter042.alarmclock.feature.clock.clockGraph
import io.github.tweeter042.alarmclock.feature.connected.connectedGraph
import io.github.tweeter042.alarmclock.feature.integrations.integrationsGraph
import io.github.tweeter042.alarmclock.feature.missions.missionsGraph
import io.github.tweeter042.alarmclock.feature.settings.settingsGraph
import io.github.tweeter042.alarmclock.feature.sleep.sleepGraph
import io.github.tweeter042.alarmclock.whatsnew.WhatsNewHost

/**
 * The phone app's UI: the navigation graph of every feature, the bottom bar on the top-level
 * screens, the onboarding gate and the "What's new" sheet. Must run inside `AlarmClockAppRoot`.
 *
 * Onboarding gate: the start destination is [OnboardingRoute] until
 * `settings.onboardingCompleted`; when that becomes true the start destination switches to
 * [AlarmsRoute], which also resets the back stack (the onboarding screens are gone).
 *
 * @param pendingDestination a screen requested through `MainIntents`, opened once onboarding is
 *   done; [onPendingHandled] is called when it has been opened.
 */
@Composable
internal fun AppShell(
    pendingDestination: Any?,
    onPendingHandled: (Any) -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val onboarded = LocalAppSettings.current.onboardingCompleted
    val startDestination: Any = if (onboarded) AlarmsRoute else OnboardingRoute
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    // Before the NavHost attaches its graph there is no entry yet: the start destination
    // (AlarmsRoute once onboarded) is about to show, so the bar is there from the first frame.
    val showBottomBar = onboarded && (destination == null || destination.isTopLevel())
    val selectedTab = TopLevelDestination.entries.firstOrNull { destination.isRoute(it.route) }
        ?: TopLevelDestination.ALARMS.takeIf { destination == null }
    val motion = AppTheme.motion

    ThemedBackground(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // The bar below handles the navigation-bar inset; screens must not add it again.
                    .then(if (showBottomBar) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier),
            ) {
                AppNavHost(navController, startDestination, motion)
            }
            AnimatedVisibility(
                visible = showBottomBar,
                enter = expandVertically(motion.spec(), expandFrom = Alignment.Top) + fadeIn(motion.fade()),
                exit = shrinkVertically(motion.spec(), shrinkTowards = Alignment.Top) + fadeOut(motion.fade()),
            ) {
                ShellNavigationBar(selectedTab, onSelect = { navController.navigateToTopLevel(it.route) })
            }
        }
    }

    val handled by rememberUpdatedState(onPendingHandled)
    LaunchedEffect(pendingDestination, onboarded) {
        val target = pendingDestination ?: return@LaunchedEffect
        if (!onboarded) return@LaunchedEffect
        navController.open(target)
        handled(target)
    }

    WhatsNewHost(onSeeAllChanges = { navController.navigate(ChangelogRoute) { launchSingleTop = true } })
}

/** The bottom bar: one item per [TopLevelDestination]. */
@Composable
internal fun ShellNavigationBar(selected: TopLevelDestination?, onSelect: (TopLevelDestination) -> Unit, modifier: Modifier = Modifier) {
    AppNavigationBar(modifier) {
        TopLevelDestination.entries.forEach { tab ->
            AppNavigationItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = tab.icon,
                label = stringResource(tab.labelRes),
            )
        }
    }
}

@Composable
private fun AppNavHost(navController: NavHostController, startDestination: Any, motion: MotionSpec) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { enter(motion, pop = false) },
        exitTransition = { exit(motion, pop = false) },
        popEnterTransition = { enter(motion, pop = true) },
        popExitTransition = { exit(motion, pop = true) },
    ) {
        alarmsGraph(navController)
        clockGraph(navController)
        sleepGraph(navController)
        settingsGraph(navController)
        integrationsGraph(navController)
        connectedGraph(navController)
        missionsGraph(navController)
    }
}

/** Tabs cross-fade; screens pushed on top slide in slightly from the side (back reverses it). */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.enter(motion: MotionSpec, pop: Boolean): EnterTransition {
    val fade = fadeIn(motion.fade(motion.mediumMillis))
    if (isTabSwitch()) return fade
    return fade + slideInHorizontally(motion.spec()) { width -> (if (pop) -width else width) / SLIDE_FRACTION }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.exit(motion: MotionSpec, pop: Boolean): ExitTransition {
    val fade = fadeOut(motion.fade(motion.shortMillis))
    if (isTabSwitch()) return fade
    return fade + slideOutHorizontally(motion.spec()) { width -> (if (pop) width else -width) / SLIDE_FRACTION }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    initialState.destination.isTopLevel() && targetState.destination.isTopLevel()

private const val SLIDE_FRACTION = 10

/** Opens a requested screen: bottom-bar destinations as tabs, anything else on top. */
private fun NavHostController.open(route: Any) {
    if (route in TopLevelRoutes.all) navigateToTopLevel(route) else navigate(route) { launchSingleTop = true }
}
