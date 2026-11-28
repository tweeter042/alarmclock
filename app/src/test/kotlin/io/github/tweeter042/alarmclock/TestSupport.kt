package io.github.tweeter042.alarmclock

import android.app.Application
import io.github.tweeter042.alarmclock.core.model.AppGraph
import io.github.tweeter042.alarmclock.core.model.AppGraphProvider
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
import io.github.tweeter042.alarmclock.core.model.service.ChangelogEntry
import io.github.tweeter042.alarmclock.core.model.service.ChangelogRepository
import io.github.tweeter042.alarmclock.core.model.service.HealthSleepSource
import io.github.tweeter042.alarmclock.core.model.service.HolidayProvider
import io.github.tweeter042.alarmclock.core.model.service.LocalizedText
import io.github.tweeter042.alarmclock.core.model.service.MusicLauncher
import io.github.tweeter042.alarmclock.core.model.service.NewsProvider
import io.github.tweeter042.alarmclock.core.model.service.QuoteProvider
import io.github.tweeter042.alarmclock.core.model.service.SmartHomeController
import io.github.tweeter042.alarmclock.core.model.service.Speaker
import io.github.tweeter042.alarmclock.core.model.service.WeatherProvider
import io.github.tweeter042.alarmclock.core.model.service.WearBridge
import io.github.tweeter042.alarmclock.core.model.settings.AppSettings
import io.github.tweeter042.alarmclock.core.model.theme.AppIconController
import io.github.tweeter042.alarmclock.stub.NoOpAlarmEngine
import io.github.tweeter042.alarmclock.stub.NoOpBriefingComposer
import io.github.tweeter042.alarmclock.stub.NoOpCalendarSource
import io.github.tweeter042.alarmclock.stub.NoOpHealthSleepSource
import io.github.tweeter042.alarmclock.stub.NoOpHolidayProvider
import io.github.tweeter042.alarmclock.stub.NoOpMusicLauncher
import io.github.tweeter042.alarmclock.stub.NoOpNewsProvider
import io.github.tweeter042.alarmclock.stub.NoOpQuoteProvider
import io.github.tweeter042.alarmclock.stub.NoOpReliabilityChecker
import io.github.tweeter042.alarmclock.stub.NoOpSmartHomeController
import io.github.tweeter042.alarmclock.stub.NoOpSpeaker
import io.github.tweeter042.alarmclock.stub.NoOpWearBridge
import io.github.tweeter042.alarmclock.stub.NoOpWeatherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Application for shell tests: the graph is swapped per test (no disk, no shared state). */
class TestApplication : Application(), AppGraphProvider {
    var graph: AppGraph = TestAppGraph()
    override val appGraph: AppGraph get() = graph
}

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> get() = state
    override suspend fun current(): AppSettings = state.value
    override suspend fun update(transform: (AppSettings) -> AppSettings) = state.update(transform)
}

class FakeChangelog(private val entries: List<ChangelogEntry> = emptyList()) : ChangelogRepository {
    override suspend fun entries(): List<ChangelogEntry> = entries.sortedByDescending { it.versionCode }
    override suspend fun entryFor(versionCode: Int): ChangelogEntry? = entries.firstOrNull { it.versionCode == versionCode }
}

fun changelogEntry(versionCode: Int, versionName: String, vararg highlights: Pair<String, String>) = ChangelogEntry(
    versionName = versionName,
    versionCode = versionCode,
    date = "2026-12-15",
    highlights = highlights.map { (en, th) -> LocalizedText(en, th) },
)

/** An [AppGraph] with in-memory settings and changelog, the NoOp stand-ins and nothing on disk. */
class TestAppGraph(
    override val settings: FakeSettingsRepository = FakeSettingsRepository(),
    override val changelog: ChangelogRepository = FakeChangelog(),
    versionCode: Int = 1,
    versionName: String = "1.0.0",
    aiModelName: String = "",
    appIcons: AppIconController? = null,
) : AppGraph {
    override val buildInfo = BuildInfo(
        applicationId = "io.github.tweeter042.alarmclock",
        versionName = versionName,
        versionCode = versionCode,
        buildDate = "2026-12-15T09:00:00+07:00",
        aiModelName = aiModelName,
        isDebug = true,
    )

    /** Unconfined: work launched by the UI (e.g. storing the seen version) finishes immediately. */
    override val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private val icons = appIcons
    override val appIcons: AppIconController get() = icons ?: unused()
    override val engine: AlarmEngine = NoOpAlarmEngine()
    override val reliability: ReliabilityChecker = NoOpReliabilityChecker()
    override val weather: WeatherProvider = NoOpWeatherProvider()
    override val holidayProvider: HolidayProvider = NoOpHolidayProvider()
    override val calendar: CalendarSource = NoOpCalendarSource()
    override val speaker: Speaker = NoOpSpeaker()
    override val briefing: BriefingComposer = NoOpBriefingComposer()
    override val quotes: QuoteProvider = NoOpQuoteProvider()
    override val news: NewsProvider = NoOpNewsProvider()
    override val smartHome: SmartHomeController = NoOpSmartHomeController()
    override val wear: WearBridge = NoOpWearBridge()
    override val health: HealthSleepSource = NoOpHealthSleepSource()
    override val music: MusicLauncher = NoOpMusicLauncher()

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

    private fun unused(): Nothing = error("Not used by the app shell tests")
}
