package io.github.tweeter042.alarmclock.core.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tweeter042.alarmclock.core.designsystem.theme.AlarmClockTheme
import io.github.tweeter042.alarmclock.core.designsystem.theme.ThemedSystemBars
import io.github.tweeter042.alarmclock.core.model.AppGraph
import io.github.tweeter042.alarmclock.core.model.alarm.RingAppearance
import io.github.tweeter042.alarmclock.core.model.repository.SettingsRepository
import io.github.tweeter042.alarmclock.core.model.settings.AppSettings
import io.github.tweeter042.alarmclock.core.model.settings.AppearanceSettings
import io.github.tweeter042.alarmclock.core.model.settings.RegionSettings
import io.github.tweeter042.alarmclock.core.model.theme.ThemeId
import io.github.tweeter042.alarmclock.core.ui.format.LocalUiFormatters
import io.github.tweeter042.alarmclock.core.ui.format.rememberUiFormatters
import io.github.tweeter042.alarmclock.core.ui.time.ProvideTimeChanges
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/** The user's settings, provided by [AlarmClockAppRoot]. Read with `LocalAppSettings.current`. */
val LocalAppSettings = compositionLocalOf { AppSettings() }

/** Shortcut for `LocalAppSettings.current.region` (language, 12/24 h, numerals, era, first day of week). */
val LocalRegionSettings = compositionLocalOf { RegionSettings() }

/** How long a root waits for the first settings emission before falling back to defaults. */
const val DEFAULT_SETTINGS_TIMEOUT_MILLIS: Long = 5_000L

/** The ring screen must appear quickly even if the settings file is slow; it waits less. */
const val RING_SETTINGS_TIMEOUT_MILLIS: Long = 1_000L

/** A theme different from the app theme (ring screen, previews). Null fields follow the app settings. */
@Immutable
data class ThemeOverrides(
    val themeId: ThemeId? = null,
    val paletteId: String? = null,
    val forceDark: Boolean? = null,
) {
    companion object {
        val None = ThemeOverrides()

        /**
         * The ring-screen theme: the alarm's own appearance first, then Settings › Appearance ›
         * ring screen theme, then the app theme. A palette without a theme applies to the theme
         * that wins.
         */
        fun forRing(appearance: AppearanceSettings, alarm: RingAppearance?, forceDark: Boolean? = null): ThemeOverrides = when {
            alarm?.themeId != null -> ThemeOverrides(alarm.themeId, alarm.paletteId, forceDark)
            appearance.ringThemeId != null -> ThemeOverrides(appearance.ringThemeId, alarm?.paletteId ?: appearance.ringPaletteId, forceDark)
            else -> ThemeOverrides(null, alarm?.paletteId, forceDark)
        }
    }
}

/**
 * The root of every activity's composition (MainActivity, RingActivity, SunriseActivity, …).
 *
 * Collects [AppGraph.settings] and provides [LocalAppGraph], [LocalAppSettings],
 * [LocalRegionSettings] and [LocalUiFormatters], then applies [AlarmClockTheme] with the user's
 * appearance and accessibility settings. Nothing is composed until the first settings value has
 * arrived (no flash of the default theme); if the settings cannot be read within
 * [settingsTimeoutMillis] the defaults are used. Settings survive configuration changes, so a
 * rotation never shows an empty frame.
 *
 * ```
 * setContent {
 *     AlarmClockAppRoot(appGraph()) { MyScreen() }
 * }
 * ```
 *
 * @param themedSystemBars calls [ThemedSystemBars] (readable status/navigation bar icons).
 * @param themedWindowBackground paints the window in the theme's background colour so window
 *   transitions and the keyboard never flash a different colour. Turn off for translucent windows.
 */
@Composable
fun AlarmClockAppRoot(
    graph: AppGraph,
    overrides: ThemeOverrides = ThemeOverrides.None,
    settingsTimeoutMillis: Long = DEFAULT_SETTINGS_TIMEOUT_MILLIS,
    themedSystemBars: Boolean = true,
    themedWindowBackground: Boolean = true,
    content: @Composable () -> Unit,
) {
    AppRoot(graph, { overrides }, settingsTimeoutMillis, themedSystemBars, themedWindowBackground, content)
}

/**
 * [AlarmClockAppRoot] for the ring screen: the theme is resolved with [ThemeOverrides.forRing]
 * from the ringing alarm's [alarmAppearance] and the ring-screen theme setting, and the settings
 * wait is short ([RING_SETTINGS_TIMEOUT_MILLIS]).
 */
@Composable
fun AlarmClockRingRoot(
    graph: AppGraph,
    alarmAppearance: RingAppearance?,
    forceDark: Boolean? = null,
    themedSystemBars: Boolean = true,
    themedWindowBackground: Boolean = true,
    content: @Composable () -> Unit,
) {
    AppRoot(
        graph = graph,
        overrides = { settings -> ThemeOverrides.forRing(settings.appearance, alarmAppearance, forceDark) },
        settingsTimeoutMillis = RING_SETTINGS_TIMEOUT_MILLIS,
        themedSystemBars = themedSystemBars,
        themedWindowBackground = themedWindowBackground,
        content = content,
    )
}

@Composable
private fun AppRoot(
    graph: AppGraph,
    overrides: (AppSettings) -> ThemeOverrides,
    settingsTimeoutMillis: Long,
    themedSystemBars: Boolean,
    themedWindowBackground: Boolean,
    content: @Composable () -> Unit,
) {
    val holder = viewModel(key = SETTINGS_HOLDER_KEY) { AppSettingsHolder(graph.settings, settingsTimeoutMillis) }
    val settings by holder.settings.collectAsStateWithLifecycle()
    val loaded = settings ?: return
    ProvideAppEnvironment(graph, loaded) {
        val o = overrides(loaded)
        AlarmClockTheme(
            appearance = loaded.appearance,
            accessibility = loaded.accessibility,
            forceDark = o.forceDark,
            themeOverride = o.themeId,
            paletteOverride = o.paletteId,
        ) {
            if (themedSystemBars) ThemedSystemBars()
            if (themedWindowBackground) ThemedWindowBackground()
            ProvideTimeChanges(content)
        }
    }
}

/**
 * Provides [graph] and already-loaded [settings] (graph, settings, region and formatters) without
 * applying a theme. For tests and previews; activities use [AlarmClockAppRoot].
 */
@Composable
fun ProvideAppEnvironment(graph: AppGraph, settings: AppSettings, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalAppGraph provides graph,
        LocalAppSettings provides settings,
        LocalRegionSettings provides settings.region,
    ) {
        val formatters = rememberUiFormatters(settings.region)
        CompositionLocalProvider(LocalUiFormatters provides formatters, content = content)
    }
}

private const val SETTINGS_HOLDER_KEY = "io.github.tweeter042.alarmclock.core.ui.AppSettingsHolder"
private const val TAG = "AlarmClock/UI"

/**
 * Keeps the latest settings for an activity (survives configuration changes). Emits null until
 * the first value arrives; falls back to defaults if reading fails or takes longer than
 * [timeoutMillis], and keeps listening so the real values replace the defaults when they come.
 */
internal class AppSettingsHolder(repository: SettingsRepository, timeoutMillis: Long) : ViewModel() {
    private val state = MutableStateFlow<AppSettings?>(null)
    val settings: StateFlow<AppSettings?> = state.asStateFlow()

    init {
        val fallback = viewModelScope.launch {
            delay(timeoutMillis)
            if (state.compareAndSet(null, AppSettings())) Log.w(TAG, "Settings not available after $timeoutMillis ms; using defaults")
        }
        viewModelScope.launch {
            repository.settings
                .catch { error ->
                    if (error is CancellationException) throw error
                    Log.e(TAG, "Settings could not be read; using defaults (${error.javaClass.simpleName})")
                    emit(AppSettings())
                }
                .collect { value ->
                    state.value = value
                    fallback.cancel()
                }
        }
    }
}
