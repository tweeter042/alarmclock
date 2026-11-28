package io.github.tweeter042.alarmclock.core.ui.navigation

import kotlinx.serialization.Serializable

/*
 * Every navigation destination of the phone app (type-safe Navigation Compose routes).
 * Owner module in brackets — only that module registers the destination (in its
 * `NavGraphBuilder.xxxGraph(...)` function); any module may navigate to any route.
 */

// [feature:alarms]
@Serializable data object AlarmsRoute
/** alarmId = 0 creates a new alarm; presetHour/presetMinute (-1 = none) prefill a new alarm. */
@Serializable data class AlarmEditRoute(val alarmId: Long = 0L, val presetHour: Int = -1, val presetMinute: Int = -1)
@Serializable data object AlarmGroupsRoute

// [feature:clock]
@Serializable data object WorldClockRoute
@Serializable data object TimerRoute
@Serializable data object StopwatchRoute
@Serializable data object BedsideClockRoute

// [feature:sleep]
@Serializable data object SleepRoute
@Serializable data object SleepSoundsRoute
@Serializable data object SleepStatsRoute
@Serializable data object BedtimeSettingsRoute
@Serializable data object WakeCalculatorRoute

// [feature:settings]
@Serializable data object SettingsRoute
@Serializable data object AppearanceRoute
@Serializable data object LanguageRegionRoute
@Serializable data object AlarmDefaultsRoute
@Serializable data object ReliabilityRoute
@Serializable data object SamsungSetupRoute
@Serializable data object BackupRoute
@Serializable data object AccessibilityRoute
@Serializable data object MorningRoutineSettingsRoute
@Serializable data object IntegrationsRoute
@Serializable data object AboutRoute
@Serializable data object ChangelogRoute
@Serializable data object LicensesRoute
/**
 * First-run onboarding. It is the start destination while `settings.onboardingCompleted` is
 * false. To finish onboarding, set `onboardingCompleted = true`: the app shell then makes
 * [AlarmsRoute] the start destination and resets the back stack to it (navigating there yourself
 * as well is harmless).
 */
@Serializable data object OnboardingRoute

// [feature:integrations]
@Serializable data object WeatherSettingsRoute
@Serializable data object HolidaysRoute
@Serializable data object CalendarRulesRoute
@Serializable data class CalendarRuleEditRoute(val ruleId: Long = 0L)
@Serializable data object LocationAlarmsRoute
@Serializable data class LocationAlarmEditRoute(val locationAlarmId: Long = 0L)

// [feature:connected]
@Serializable data object SmartThingsRoute
@Serializable data object WatchRoute
@Serializable data object HealthConnectRoute
@Serializable data object MusicAppsRoute

// [feature:missions]
@Serializable data object MissionAssetsRoute
/** Try a mission outside of an alarm. [missionJson] = ModelJson-encoded MissionConfig. */
@Serializable data class MissionPreviewRoute(val missionJson: String)
@Serializable data object HandwritingModelsRoute

/**
 * Bottom-bar destinations, in order. The app shell shows the bottom bar only on these; switch
 * between them with `navController.navigateToTopLevel(route)`. Each of these screens shows
 * `TopLevelActions(navController)` (the Settings gear) in its top bar.
 */
object TopLevelRoutes {
    val all: List<Any> = listOf(AlarmsRoute, WorldClockRoute, TimerRoute, StopwatchRoute, SleepRoute)
}
