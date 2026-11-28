package io.github.tweeter042.alarmclock.core.ui.navigation

import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination

/**
 * Switches to a bottom-bar destination like a tab: the start destination stays at the bottom of
 * the back stack, the state of the tab being left is saved and the target tab's state restored.
 */
fun NavController.navigateToTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Opens Settings (the gear of every top-level screen). */
fun NavController.navigateToSettings() {
    navigate(SettingsRoute) { launchSingleTop = true }
}

/** True if this destination (or a parent graph) is one of [TopLevelRoutes]. */
fun NavDestination?.isTopLevel(): Boolean =
    this != null && TopLevelRoutes.all.any { route -> hierarchy.any { it.hasRoute(route::class) } }

/** True if this destination (or a parent graph) is [route]'s destination. */
fun NavDestination?.isRoute(route: Any): Boolean = this != null && hierarchy.any { it.hasRoute(route::class) }
