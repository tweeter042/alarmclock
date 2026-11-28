package io.github.tweeter042.alarmclock.core.ui.format

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.tweeter042.alarmclock.core.model.settings.RegionSettings
import io.github.tweeter042.alarmclock.core.ui.LocalRegionSettings

/**
 * Every formatter for one set of [FormatStrings] (language) and [FormatOptions] (12/24 h,
 * numerals, era, first day of week).
 *
 * In Compose use [rememberUiFormatters] (or the single-formatter helpers below); elsewhere
 * (notifications, widgets, workers) use [UiFormatters.from].
 */
@Stable
class UiFormatters(
    val strings: FormatStrings,
    val options: FormatOptions,
) {
    val time: TimeFormatter = TimeFormatter(strings, options)
    val duration: DurationFormatter = DurationFormatter(strings, options.numerals)
    val days: DayNames = DayNames(strings, options.firstDayOfWeek)
    val repeat: RepeatFormatter = RepeatFormatter(strings, time, days)

    companion object {
        /** Formatters for [context]'s current locale and the user's [region] settings. */
        fun from(context: Context, region: RegionSettings): UiFormatters {
            val configuration = context.resources.configuration
            return UiFormatters(
                strings = ResourceFormatStrings(context.resources),
                options = FormatOptions.resolve(region, configuration.locales[0], DateFormat.is24HourFormat(context)),
            )
        }
    }
}

/**
 * Formatters provided by `AlarmClockAppRoot` for the whole activity. Null outside of it
 * ([rememberUiFormatters] then builds its own).
 */
val LocalUiFormatters = compositionLocalOf<UiFormatters?> { null }

/** The formatters for the current composition (language, region settings, system 24-hour setting). */
@Composable
fun rememberUiFormatters(): UiFormatters = LocalUiFormatters.current ?: rememberUiFormatters(LocalRegionSettings.current)

/** Formatters for an explicit [region] (e.g. a preview of a setting the user has not saved yet). */
@Composable
fun rememberUiFormatters(region: RegionSettings): UiFormatters {
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    val system24h = rememberSystemUses24Hour()
    return remember(resources, configuration, region, system24h) {
        UiFormatters(
            strings = ResourceFormatStrings(resources),
            options = FormatOptions.resolve(region, configuration.locales[0], system24h),
        )
    }
}

@Composable
fun rememberTimeFormatter(): TimeFormatter = rememberUiFormatters().time

@Composable
fun rememberDurationFormatter(): DurationFormatter = rememberUiFormatters().duration

@Composable
fun rememberDayNames(): DayNames = rememberUiFormatters().days

@Composable
fun rememberRepeatFormatter(): RepeatFormatter = rememberUiFormatters().repeat

/**
 * The device's 12/24-hour setting (`DateFormat.is24HourFormat`). Re-read whenever the screen
 * resumes, so a change made in the system Settings app shows up on return.
 */
@Composable
fun rememberSystemUses24Hour(): Boolean {
    val context = LocalContext.current
    var uses24h by remember(context) { mutableStateOf(DateFormat.is24HourFormat(context)) }
    LifecycleResumeEffect(context) {
        uses24h = DateFormat.is24HourFormat(context)
        onPauseOrDispose { }
    }
    return uses24h
}
