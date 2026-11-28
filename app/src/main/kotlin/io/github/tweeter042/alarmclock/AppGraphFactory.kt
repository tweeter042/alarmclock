package io.github.tweeter042.alarmclock

import android.content.Context
import android.util.Log
import io.github.tweeter042.alarmclock.core.alarm.DefaultAlarmEngine
import io.github.tweeter042.alarmclock.core.alarm.EngineDependencies
import io.github.tweeter042.alarmclock.core.alarm.reliability.DefaultReliabilityChecker
import io.github.tweeter042.alarmclock.core.data.DataGraph
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
import io.github.tweeter042.alarmclock.core.model.theme.AppIconController
import io.github.tweeter042.alarmclock.engine.AppRingUiLauncher
import io.github.tweeter042.alarmclock.engine.CompositeEngineHooks
import io.github.tweeter042.alarmclock.engine.WidgetRefreshHooks
import io.github.tweeter042.alarmclock.icon.AppIconSwitcher
import io.github.tweeter042.alarmclock.stub.NoOpBriefingComposer
import io.github.tweeter042.alarmclock.stub.NoOpCalendarSource
import io.github.tweeter042.alarmclock.stub.NoOpHealthSleepSource
import io.github.tweeter042.alarmclock.stub.NoOpHolidayProvider
import io.github.tweeter042.alarmclock.stub.NoOpMusicLauncher
import io.github.tweeter042.alarmclock.stub.NoOpNewsProvider
import io.github.tweeter042.alarmclock.stub.NoOpQuoteProvider
import io.github.tweeter042.alarmclock.stub.NoOpSmartHomeController
import io.github.tweeter042.alarmclock.stub.NoOpSpeaker
import io.github.tweeter042.alarmclock.stub.NoOpWearBridge
import io.github.tweeter042.alarmclock.stub.NoOpWeatherProvider
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Builds the app's [AppGraph].
 *
 * Wired today: build info, the app scope, every core:data repository, backup and changelog, the
 * launcher icon switcher, the real alarm engine (core:alarm) and its reliability checker.
 * Integrations and connected devices are still the temporary `stub.NoOp*` stand-ins; the
 * integration wave passes the real ones here (and into [EngineDependencies]).
 */
object AppGraphFactory {
    private const val TAG = "AlarmClock/App"

    internal fun create(context: Context): DefaultAppGraph {
        val app = context.applicationContext ?: context
        val buildInfo = buildInfo()
        val data = DataGraph(app, buildInfo.versionName)
        val engine = DefaultAlarmEngine(
            app,
            data,
            EngineDependencies(
                hooks = CompositeEngineHooks(listOf(WidgetRefreshHooks(app))),
                ringUi = AppRingUiLauncher,
                buildInfo = buildInfo,
            ),
        )
        return DefaultAppGraph(
            buildInfo = buildInfo,
            appScope = newAppScope(),
            data = data,
            appIcons = AppIconSwitcher(app),
            alarmEngine = engine,
            reliability = DefaultReliabilityChecker(app, data, engine),
            weather = NoOpWeatherProvider(),
            holidayProvider = NoOpHolidayProvider(),
            calendar = NoOpCalendarSource(),
            speaker = NoOpSpeaker(),
            briefing = NoOpBriefingComposer(),
            quotes = NoOpQuoteProvider(),
            news = NoOpNewsProvider(),
            smartHome = NoOpSmartHomeController(),
            wear = NoOpWearBridge(),
            health = NoOpHealthSleepSource(),
            music = NoOpMusicLauncher(),
        )
    }

    /** Version, build date and AI model name from BuildConfig (the model name comes from local.properties). */
    fun buildInfo(): BuildInfo = BuildInfo(
        applicationId = BuildConfig.APPLICATION_ID,
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        buildDate = BuildConfig.BUILD_DATE,
        aiModelName = BuildConfig.AI_MODEL_NAME,
        isDebug = BuildConfig.DEBUG,
    )

    /** Application-lifetime scope: one failing job never cancels the others, and failures are logged. */
    private fun newAppScope(): CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            Log.e(TAG, "Unhandled error in an app-scope job", error)
        },
    )
}

/** The [AppGraph] implementation: core:data members are taken lazily from [data]. */
internal class DefaultAppGraph(
    override val buildInfo: BuildInfo,
    override val appScope: CoroutineScope,
    private val data: DataGraph,
    override val appIcons: AppIconController,
    /** The concrete engine (receivers and services reach it through AlarmEngineHost). */
    val alarmEngine: DefaultAlarmEngine,
    override val reliability: ReliabilityChecker,
    override val weather: WeatherProvider,
    override val holidayProvider: HolidayProvider,
    override val calendar: CalendarSource,
    override val speaker: Speaker,
    override val briefing: BriefingComposer,
    override val quotes: QuoteProvider,
    override val news: NewsProvider,
    override val smartHome: SmartHomeController,
    override val wear: WearBridge,
    override val health: HealthSleepSource,
    override val music: MusicLauncher,
) : AppGraph {
    override val engine: AlarmEngine get() = alarmEngine
    override val settings: SettingsRepository get() = data.settings
    override val secrets: SecretStore get() = data.secrets
    override val alarms: AlarmRepository get() = data.alarms
    override val holidays: HolidayRepository get() = data.holidays
    override val timers: TimerRepository get() = data.timers
    override val stopwatch: StopwatchRepository get() = data.stopwatch
    override val worldClocks: WorldClockRepository get() = data.worldClocks
    override val events: EventLogRepository get() = data.events
    override val routine: RoutineRepository get() = data.routine
    override val missionAssets: MissionAssetRepository get() = data.missionAssets
    override val calendarRules: CalendarRuleRepository get() = data.calendarRules
    override val locationAlarms: LocationAlarmRepository get() = data.locationAlarms
    override val backup: BackupManager get() = data.backupManager
    override val changelog: ChangelogRepository get() = data.changelog

    /** Engine-internal stores for core:alarm (not part of [AppGraph]). */
    val dataGraph: DataGraph get() = data
}
