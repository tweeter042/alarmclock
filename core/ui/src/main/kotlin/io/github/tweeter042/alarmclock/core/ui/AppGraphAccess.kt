package io.github.tweeter042.alarmclock.core.ui

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.tweeter042.alarmclock.core.model.AppGraph
import io.github.tweeter042.alarmclock.core.model.AppGraphProvider

/** The process-wide [AppGraph]; provided at the root of every activity's composition. */
val LocalAppGraph = staticCompositionLocalOf<AppGraph> {
    error("LocalAppGraph not provided. Wrap content in ProvideAppGraph / AlarmClockAppRoot.")
}

/** Access the graph outside Compose (receivers, services, workers, ViewModel factories). */
fun Context.appGraph(): AppGraph = (applicationContext as AppGraphProvider).appGraph
