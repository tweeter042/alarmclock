package io.github.tweeter042.alarmclock.stub

import android.content.Context
import io.github.tweeter042.alarmclock.core.model.alarm.Alarm
import io.github.tweeter042.alarmclock.core.model.alarm.ActiveRing
import io.github.tweeter042.alarmclock.core.model.alarm.NextAlarm
import io.github.tweeter042.alarmclock.core.model.alarm.OpenAppTarget
import io.github.tweeter042.alarmclock.core.model.alarm.RingOutcome
import io.github.tweeter042.alarmclock.core.model.alarm.WeatherCondition
import io.github.tweeter042.alarmclock.core.model.entity.Holiday
import io.github.tweeter042.alarmclock.core.model.service.Briefing
import io.github.tweeter042.alarmclock.core.model.service.BriefingComposer
import io.github.tweeter042.alarmclock.core.model.service.CalendarEvent
import io.github.tweeter042.alarmclock.core.model.service.CalendarInfo
import io.github.tweeter042.alarmclock.core.model.service.CalendarSource
import io.github.tweeter042.alarmclock.core.model.service.HealthAvailability
import io.github.tweeter042.alarmclock.core.model.service.HealthSleepSource
import io.github.tweeter042.alarmclock.core.model.service.HolidayProvider
import io.github.tweeter042.alarmclock.core.model.service.MusicLauncher
import io.github.tweeter042.alarmclock.core.model.service.NewsHeadline
import io.github.tweeter042.alarmclock.core.model.service.NewsProvider
import io.github.tweeter042.alarmclock.core.model.service.PlaceResult
import io.github.tweeter042.alarmclock.core.model.service.Quote
import io.github.tweeter042.alarmclock.core.model.service.QuoteProvider
import io.github.tweeter042.alarmclock.core.model.service.SleepSessionInfo
import io.github.tweeter042.alarmclock.core.model.service.SmartDevice
import io.github.tweeter042.alarmclock.core.model.service.SmartHomeController
import io.github.tweeter042.alarmclock.core.model.service.SmartScene
import io.github.tweeter042.alarmclock.core.model.service.Speaker
import io.github.tweeter042.alarmclock.core.model.service.WearBridge
import io.github.tweeter042.alarmclock.core.model.service.WearNode
import io.github.tweeter042.alarmclock.core.model.service.WeatherProvider
import io.github.tweeter042.alarmclock.core.model.service.WeatherSnapshot
import io.github.tweeter042.alarmclock.core.model.theme.ThemeId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate

/*
 * TEMPORARY stand-ins for feature:integrations and feature:connected, replaced by the real
 * implementations in the integration wave. They never touch the network or other apps; calls
 * that would need them fail with [NotAvailableYet].
 */

/** The failure every not-yet-implemented call returns. */
internal class NotAvailableYet(feature: String) : UnsupportedOperationException("$feature is not available in this build yet")

// --- feature:integrations ------------------------------------------------------------------------

internal class NoOpWeatherProvider : WeatherProvider {
    override val latest: Flow<WeatherSnapshot?> = flowOf(null)
    override suspend fun refresh(force: Boolean): Result<WeatherSnapshot> = Result.failure(NotAvailableYet("Weather"))
    override suspend fun cached(maxAgeMinutes: Int): WeatherSnapshot? = null
    override suspend fun conditionAt(atMillis: Long): Result<WeatherCondition> = Result.failure(NotAvailableYet("Weather"))
    override suspend fun searchPlaces(query: String, languageTag: String): Result<List<PlaceResult>> = Result.failure(NotAvailableYet("Weather"))
}

internal class NoOpHolidayProvider : HolidayProvider {
    override suspend fun holidayDates(from: LocalDate, to: LocalDate): Set<LocalDate> = emptySet()
    override suspend fun holidaysIn(year: Int): List<Holiday> = emptyList()
    override suspend fun importFromCalendars(): Result<Int> = Result.success(0)
}

internal class NoOpCalendarSource : CalendarSource {
    override fun hasPermission(): Boolean = false
    override suspend fun calendars(): List<CalendarInfo> = emptyList()
    override suspend fun events(fromMillis: Long, toMillis: Long, calendarIds: List<Long>): List<CalendarEvent> = emptyList()
}

internal class NoOpSpeaker : Speaker {
    override suspend fun speak(text: String, languageTag: String, ratePercent: Int, useAlarmStream: Boolean): Boolean = false
    override fun stop() = Unit
    override suspend fun isLanguageAvailable(languageTag: String): Boolean = false
}

internal class NoOpBriefingComposer : BriefingComposer {
    override suspend fun compose(alarm: Alarm, atMillis: Long, languageTag: String): Briefing =
        Briefing(spokenText = "", weather = null, events = emptyList(), quote = null, headlines = emptyList())
}

internal class NoOpQuoteProvider : QuoteProvider {
    override fun quoteOfTheDay(date: LocalDate, languageTag: String): Quote = Quote(text = "")
}

internal class NoOpNewsProvider : NewsProvider {
    override suspend fun headlines(limit: Int): Result<List<NewsHeadline>> = Result.success(emptyList())
}

// --- feature:connected ---------------------------------------------------------------------------

internal class NoOpSmartHomeController : SmartHomeController {
    override val isConfigured: Flow<Boolean> = flowOf(false)
    override suspend fun testConnection(): Result<Unit> = Result.failure(NotAvailableYet("SmartThings"))
    override suspend fun devices(): Result<List<SmartDevice>> = Result.failure(NotAvailableYet("SmartThings"))
    override suspend fun scenes(): Result<List<SmartScene>> = Result.failure(NotAvailableYet("SmartThings"))
    override suspend fun runScene(sceneId: String): Result<Unit> = Result.failure(NotAvailableYet("SmartThings"))
    override suspend fun setSwitch(deviceIds: List<String>, on: Boolean): Result<Unit> = Result.failure(NotAvailableYet("SmartThings"))
    override suspend fun setLevel(deviceIds: List<String>, percent: Int): Result<Unit> = Result.failure(NotAvailableYet("SmartThings"))
    override suspend fun setColorTemperature(deviceIds: List<String>, kelvin: Int): Result<Unit> = Result.failure(NotAvailableYet("SmartThings"))
}

internal class NoOpWearBridge : WearBridge {
    override val nodes: Flow<List<WearNode>> = flowOf(emptyList())
    override suspend fun refreshNodes(): List<WearNode> = emptyList()
    override suspend fun syncAlarms(alarms: List<Alarm>, next: NextAlarm?) = Unit
    override suspend fun syncAppearance(themeId: ThemeId, paletteId: String, dark: Boolean) = Unit
    override suspend fun ringStarted(ring: ActiveRing) = Unit
    override suspend fun ringSnoozed(ringId: String, until: Long) = Unit
    override suspend fun ringStopped(ringId: String, outcome: RingOutcome) = Unit
    override suspend fun startSmartWake(alarmId: Long, occurrenceAt: Long, windowStartAt: Long) = Unit
}

internal class NoOpHealthSleepSource : HealthSleepSource {
    override fun availability(): HealthAvailability = HealthAvailability.NOT_SUPPORTED
    override suspend fun hasPermissions(): Boolean = false
    override suspend fun sleepSessions(fromMillis: Long, toMillis: Long): Result<List<SleepSessionInfo>> = Result.success(emptyList())
}

internal class NoOpMusicLauncher : MusicLauncher {
    override fun installedTargets(): List<OpenAppTarget> = emptyList()
    override fun launch(context: Context, target: OpenAppTarget): Boolean = false
    override suspend fun startSpotifyPlayback(uri: String): Boolean = false
    override fun stopSpotifyPlayback() = Unit
}
