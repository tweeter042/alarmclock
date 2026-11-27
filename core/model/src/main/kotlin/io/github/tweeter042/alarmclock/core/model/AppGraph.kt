package io.github.tweeter042.alarmclock.core.model

import io.github.tweeter042.alarmclock.core.model.engine.AlarmEngine
import io.github.tweeter042.alarmclock.core.model.engine.ReliabilityChecker
import io.github.tweeter042.alarmclock.core.model.repository.AlarmRepository
import io.github.tweeter042.alarmclock.core.model.repository.CalendarRuleRepository
import io.github.tweeter042.alarmclock.core.model.repository.EventLogRepository
import io.github.tweeter042.alarmclock.core.model.repository.HolidayRepository
import io.github.tweeter042.alarmclock.core.model.repository.LocationAlarmRepository
import io.github.tweeter042.alarmclock.core.model.repository.MissionAssetRepository
import io.github.tweeter042.alarmclock.core.model.repository.RoutineRepository
import io.github.tweeter042.alarmclock.core.model.repository.SecretStore
import io.github.tweeter042.alarmclock.core.model.repository.SettingsRepository
import io.github.tweeter042.alarmclock.core.model.repository.StopwatchRepository
import io.github.tweeter042.alarmclock.core.model.repository.TimerRepository
import io.github.tweeter042.alarmclock.core.model.repository.WorldClockRepository
import io.github.tweeter042.alarmclock.core.model.service.BackupManager
import io.github.tweeter042.alarmclock.core.model.service.BriefingComposer
import io.github.tweeter042.alarmclock.core.model.service.BuildInfo
import io.github.tweeter042.alarmclock.core.model.service.CalendarSource
import io.github.tweeter042.alarmclock.core.model.service.ChangelogRepository
import io.github.tweeter042.alarmclock.core.model.service.HealthSleepSource
import io.github.tweeter042.alarmclock.core.model.service.HolidayProvider
import io.github.tweeter042.alarmclock.core.model.service.MusicLauncher
import io.github.tweeter042.alarmclock.core.model.service.NewsProvider
import io.github.tweeter042.alarmclock.core.model.service.QuoteProvider
import io.github.tweeter042.alarmclock.core.model.service.SmartHomeController
import io.github.tweeter042.alarmclock.core.model.service.Speaker
import io.github.tweeter042.alarmclock.core.model.service.WeatherProvider
import io.github.tweeter042.alarmclock.core.model.service.WearBridge
import io.github.tweeter042.alarmclock.core.model.theme.AppIconController
import kotlinx.coroutines.CoroutineScope

/**
 * Manual dependency graph (no DI framework). Built once by the Application in :app.
 *
 * Get it from UI code with `LocalAppGraph.current` (core:ui) or, outside Compose,
 * `context.appGraph()` (core:ui). Every member is a process-wide singleton and safe to
 * use in Direct Boot unless its KDoc says otherwise.
 */
interface AppGraph {
    val buildInfo: BuildInfo
    /** Application-lifetime scope (SupervisorJob + Dispatchers.Default). */
    val appScope: CoroutineScope

    // Data (core:data)
    val settings: SettingsRepository
    /** Credential-encrypted storage: NOT available before first unlock. */
    val secrets: SecretStore
    val alarms: AlarmRepository
    val holidays: HolidayRepository
    val timers: TimerRepository
    val stopwatch: StopwatchRepository
    val worldClocks: WorldClockRepository
    val events: EventLogRepository
    val routine: RoutineRepository
    val missionAssets: MissionAssetRepository
    val calendarRules: CalendarRuleRepository
    val locationAlarms: LocationAlarmRepository
    val backup: BackupManager
    val changelog: ChangelogRepository

    // App shell (:app)
    /** Launcher icon switching (activity-aliases). */
    val appIcons: AppIconController

    // Engine (core:alarm)
    val engine: AlarmEngine
    val reliability: ReliabilityChecker

    // Integrations (feature:integrations)
    val weather: WeatherProvider
    val holidayProvider: HolidayProvider
    val calendar: CalendarSource
    val speaker: Speaker
    val briefing: BriefingComposer
    val quotes: QuoteProvider
    val news: NewsProvider

    // Connected devices & services (feature:connected)
    val smartHome: SmartHomeController
    val wear: WearBridge
    val health: HealthSleepSource
    val music: MusicLauncher
}

/** Implemented by the Application class. */
interface AppGraphProvider {
    val appGraph: AppGraph
}
