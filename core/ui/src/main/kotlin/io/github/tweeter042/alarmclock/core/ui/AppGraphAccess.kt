package io.github.tweeter042.alarmclock.core.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tweeter042.alarmclock.core.model.AppGraph
import io.github.tweeter042.alarmclock.core.model.AppGraphProvider

/** The process-wide [AppGraph]; provided at the root of every activity's composition. */
val LocalAppGraph = staticCompositionLocalOf<AppGraph> {
    error("LocalAppGraph not provided. Wrap content in ProvideAppGraph / AlarmClockAppRoot.")
}

/** Access the graph outside Compose (receivers, services, workers, ViewModel factories). */
fun Context.appGraph(): AppGraph = (applicationContext as AppGraphProvider).appGraph

/** Provides [graph] as [LocalAppGraph] only (no settings, no theme). Mostly for tests and previews. */
@Composable
fun ProvideAppGraph(graph: AppGraph, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppGraph provides graph, content = content)
}

/**
 * A ViewModel built from the [AppGraph], scoped like androidx `viewModel()` (to the navigation
 * destination inside a NavHost, otherwise to the activity). Inject only what the ViewModel needs:
 *
 * ```
 * val vm = appViewModel { graph -> AlarmListViewModel(graph.alarms, graph.engine) }
 * ```
 *
 * @param key distinguishes several ViewModels of the same class in one scope.
 */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppGraph) -> VM,
): VM {
    val graph = LocalAppGraph.current
    return viewModel(key = key) { create(graph) }
}
