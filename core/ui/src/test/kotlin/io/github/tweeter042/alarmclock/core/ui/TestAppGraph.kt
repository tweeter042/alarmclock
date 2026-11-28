package io.github.tweeter042.alarmclock.core.ui

import io.github.tweeter042.alarmclock.core.model.AppGraph
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
import io.github.tweeter042.alarmclock.core.model.settings.AppSettings
import io.github.tweeter042.alarmclock.core.model.theme.AppIconController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update

/** Settings held in memory. Start with [initial] = null to simulate a store that has not answered yet. */
class FakeSettingsRepository(initial: AppSettings? = AppSettings()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> get() = state.filterNotNull()
    override suspend fun current(): AppSettings = state.value ?: AppSettings()
    override suspend fun update(transform: (AppSettings) -> AppSettings) = state.update { transform(it ?: AppSettings()) }
}

/** An [AppGraph] for core:ui tests: only settings, build info and the scope are usable. */
class TestAppGraph(override val settings: SettingsRepository = FakeSettingsRepository()) : AppGraph {
    override val buildInfo = BuildInfo(
        applicationId = "io.github.tweeter042.alarmclock",
        versionName = "1.0.0",
        versionCode = 1,
        buildDate = "2026-11-28T09:00:00+07:00",
        aiModelName = "",
        isDebug = true,
    )
    override val appScope: CoroutineScope = CoroutineScope(SupervisorJob())
    override val secrets: SecretStore get() = unused()
    override val alarms: AlarmRepository get() = unused()
    override val holidays: HolidayRepository get() = unused()
    override val timers: TimerRepository get() = unused()
    override val stopwatch: StopwatchRepository get() = unused()
    override val worldClocks: WorldClockRepository get() = unused()
    override val events: EventLogRepository get() = unused()
    override val routine: RoutineRepository get() = unused()
    override val missionAssets: MissionAssetRepository get() = unused()
    override val calendarRules: CalendarRuleRepository get() = unused()
    override val locationAlarms: LocationAlarmRepository get() = unused()
    override val backup: BackupManager get() = unused()
    override val changelog: ChangelogRepository get() = unused()
    override val appIcons: AppIconController get() = unused()
    override val engine: AlarmEngine get() = unused()
    override val reliability: ReliabilityChecker get() = unused()
    override val weather: WeatherProvider get() = unused()
    override val holidayProvider: HolidayProvider get() = unused()
    override val calendar: CalendarSource get() = unused()
    override val speaker: Speaker get() = unused()
    override val briefing: BriefingComposer get() = unused()
    override val quotes: QuoteProvider get() = unused()
    override val news: NewsProvider get() = unused()
    override val smartHome: SmartHomeController get() = unused()
    override val wear: WearBridge get() = unused()
    override val health: HealthSleepSource get() = unused()
    override val music: MusicLauncher get() = unused()

    private fun unused(): Nothing = error("Not used by core:ui tests")
}
