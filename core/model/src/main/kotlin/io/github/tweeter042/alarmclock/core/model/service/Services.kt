package io.github.tweeter042.alarmclock.core.model.service

import android.content.Context
import io.github.tweeter042.alarmclock.core.model.alarm.ActiveRing
import io.github.tweeter042.alarmclock.core.model.alarm.Alarm
import io.github.tweeter042.alarmclock.core.model.alarm.NextAlarm
import io.github.tweeter042.alarmclock.core.model.alarm.OpenAppTarget
import io.github.tweeter042.alarmclock.core.model.alarm.RingOutcome
import io.github.tweeter042.alarmclock.core.model.alarm.WeatherCondition
import io.github.tweeter042.alarmclock.core.model.entity.Holiday
import io.github.tweeter042.alarmclock.core.model.theme.ThemeId
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate

// ---------------------------------------------------------------------------------------------
// Weather (feature:integrations, Open-Meteo; no API key)
// ---------------------------------------------------------------------------------------------

@Serializable
data class WeatherSnapshot(
    val temperatureC: Double,
    val apparentTemperatureC: Double? = null,
    val condition: WeatherCondition,
    /** WMO weather code from Open-Meteo. */
    val weatherCode: Int,
    val highC: Double? = null,
    val lowC: Double? = null,
    /** 0..100 */
    val precipitationChance: Int? = null,
    val placeName: String? = null,
    val isDay: Boolean = true,
    val fetchedAt: Long,
)

@Serializable
data class PlaceResult(
    val name: String,
    val admin: String? = null,
    val country: String? = null,
    val latitude: Double,
    val longitude: Double,
)

interface WeatherProvider {
    /** Last cached snapshot (persisted), updated by [refresh]. */
    val latest: Flow<WeatherSnapshot?>

    /** Fetches fresh data (unless a snapshot younger than 15 min exists and !force). */
    suspend fun refresh(force: Boolean = false): Result<WeatherSnapshot>

    /** Cached snapshot if not older than [maxAgeMinutes], else null. Never hits the network. */
    suspend fun cached(maxAgeMinutes: Int = 180): WeatherSnapshot?

    /** Forecast condition around [atMillis] (hourly forecast), for weather-adjusted alarms. */
    suspend fun conditionAt(atMillis: Long): Result<WeatherCondition>

    suspend fun searchPlaces(query: String, languageTag: String): Result<List<PlaceResult>>
}

// ---------------------------------------------------------------------------------------------
// Holidays (feature:integrations)
// ---------------------------------------------------------------------------------------------

interface HolidayProvider {
    /** Enabled holiday dates in [from]..[to] (inclusive) from all enabled sources. */
    suspend fun holidayDates(from: LocalDate, to: LocalDate): Set<LocalDate>

    /** All holidays (enabled or not) in a year, from all sources, sorted by date. */
    suspend fun holidaysIn(year: Int): List<Holiday>

    /** Re-imports holidays from the calendars selected in settings. Returns the number imported. */
    suspend fun importFromCalendars(): Result<Int>
}

// ---------------------------------------------------------------------------------------------
// Calendar (feature:integrations, CalendarContract)
// ---------------------------------------------------------------------------------------------

data class CalendarInfo(
    val id: Long,
    val name: String,
    val accountName: String,
    val color: Int,
    /** Heuristic: looks like a public-holiday calendar. */
    val looksLikeHolidays: Boolean,
)

@Serializable
data class CalendarEvent(
    val id: Long,
    val calendarId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String? = null,
)

interface CalendarSource {
    fun hasPermission(): Boolean
    suspend fun calendars(): List<CalendarInfo>
    /** Instances overlapping [fromMillis, toMillis). Empty [calendarIds] = all calendars. */
    suspend fun events(fromMillis: Long, toMillis: Long, calendarIds: List<Long> = emptyList()): List<CalendarEvent>
}

// ---------------------------------------------------------------------------------------------
// SmartThings (feature:connected)
// ---------------------------------------------------------------------------------------------

@Serializable
data class SmartDevice(
    val id: String,
    val name: String,
    val roomName: String? = null,
    /** SmartThings capability ids, e.g. "switch", "switchLevel", "colorTemperature". */
    val capabilities: Set<String> = emptySet(),
)

@Serializable
data class SmartScene(val id: String, val name: String, val icon: String? = null)

interface SmartHomeController {
    val isConfigured: Flow<Boolean>
    suspend fun testConnection(): Result<Unit>
    suspend fun devices(): Result<List<SmartDevice>>
    suspend fun scenes(): Result<List<SmartScene>>
    suspend fun runScene(sceneId: String): Result<Unit>
    suspend fun setSwitch(deviceIds: List<String>, on: Boolean): Result<Unit>
    suspend fun setLevel(deviceIds: List<String>, percent: Int): Result<Unit>
    suspend fun setColorTemperature(deviceIds: List<String>, kelvin: Int): Result<Unit>
}

// ---------------------------------------------------------------------------------------------
// Galaxy Watch bridge, phone side (feature:connected). Protocol: shared:wearprotocol.
// ---------------------------------------------------------------------------------------------

data class WearNode(val id: String, val displayName: String, val nearby: Boolean)

interface WearBridge {
    val nodes: Flow<List<WearNode>>
    suspend fun refreshNodes(): List<WearNode>
    /** Pushes the full alarm list + next alarm (DataClient item, survives disconnects). */
    suspend fun syncAlarms(alarms: List<Alarm>, next: NextAlarm?)
    suspend fun syncAppearance(themeId: ThemeId, paletteId: String, dark: Boolean)
    suspend fun ringStarted(ring: ActiveRing)
    suspend fun ringSnoozed(ringId: String, until: Long)
    suspend fun ringStopped(ringId: String, outcome: RingOutcome)
    /** Asks the watch to start light-sleep monitoring for a smart-wake window. */
    suspend fun startSmartWake(alarmId: Long, occurrenceAt: Long, windowStartAt: Long)
}

// ---------------------------------------------------------------------------------------------
// Health Connect sleep data (feature:connected) — Samsung Health syncs Galaxy Watch sleep here.
// ---------------------------------------------------------------------------------------------

enum class HealthAvailability { AVAILABLE, NOT_INSTALLED, UPDATE_REQUIRED, NOT_SUPPORTED }

enum class SleepStageType { AWAKE, LIGHT, DEEP, REM, SLEEPING, OUT_OF_BED, UNKNOWN }

data class SleepStageInfo(val start: Long, val end: Long, val stage: SleepStageType)

data class SleepSessionInfo(
    val start: Long,
    val end: Long,
    val title: String?,
    val stages: List<SleepStageInfo>,
    /** Data origin package, e.g. "com.sec.android.app.shealth". */
    val source: String?,
)

interface HealthSleepSource {
    fun availability(): HealthAvailability
    suspend fun hasPermissions(): Boolean
    suspend fun sleepSessions(fromMillis: Long, toMillis: Long): Result<List<SleepSessionInfo>>
}

// ---------------------------------------------------------------------------------------------
// Music apps (feature:connected)
// ---------------------------------------------------------------------------------------------

interface MusicLauncher {
    /** Installed music apps that can be opened after dismiss (Spotify, YouTube Music, …). */
    fun installedTargets(): List<OpenAppTarget>
    /** Opens [target] (deep link if present). Must be called from a foreground activity context. */
    fun launch(context: Context, target: OpenAppTarget): Boolean
    /** Experimental: tries to make Spotify play [uri]. Returns true only if playback was confirmed. */
    suspend fun startSpotifyPlayback(uri: String): Boolean
    fun stopSpotifyPlayback()
}

// ---------------------------------------------------------------------------------------------
// Speech + briefing (feature:integrations)
// ---------------------------------------------------------------------------------------------

interface Speaker {
    /** Speaks [text]; suspends until finished or stopped. Returns false if TTS was unavailable. */
    suspend fun speak(text: String, languageTag: String, ratePercent: Int = 100, useAlarmStream: Boolean = true): Boolean
    fun stop()
    suspend fun isLanguageAvailable(languageTag: String): Boolean
}

@Serializable
data class Quote(val text: String, val author: String? = null)

@Serializable
data class NewsHeadline(val title: String, val link: String? = null, val source: String? = null, val publishedAt: Long? = null)

data class Briefing(
    val spokenText: String,
    val weather: WeatherSnapshot?,
    val events: List<CalendarEvent>,
    val quote: Quote?,
    val headlines: List<NewsHeadline>,
)

interface BriefingComposer {
    /** Builds the morning briefing for [alarm] at [atMillis] in [languageTag] ("en"/"th"). Uses cached data first. */
    suspend fun compose(alarm: Alarm, atMillis: Long, languageTag: String): Briefing
}

interface QuoteProvider {
    fun quoteOfTheDay(date: LocalDate, languageTag: String): Quote
}

interface NewsProvider {
    suspend fun headlines(limit: Int = 5): Result<List<NewsHeadline>>
}

// ---------------------------------------------------------------------------------------------
// Backup (core:data) and app info
// ---------------------------------------------------------------------------------------------

@Serializable
data class BackupSummary(
    val alarms: Int,
    val groups: Int,
    val timers: Int,
    val worldClocks: Int,
    val holidays: Int,
    val other: Int,
    val createdAt: Long,
    val appVersion: String,
)

interface BackupManager {
    /** Writes a JSON backup (no secrets/tokens). */
    suspend fun exportTo(out: OutputStream): Result<BackupSummary>
    /** Restores a backup. [replace] = wipe existing data first; otherwise merge as new items. */
    suspend fun importFrom(input: InputStream, replace: Boolean): Result<BackupSummary>
    /** Writes a dated backup into the SAF folder from settings.backup.treeUri. Returns the document URI. */
    suspend fun autoBackupNow(): Result<String>
}

@Serializable
data class LocalizedText(val en: String, val th: String) {
    fun get(languageTag: String): String = if (languageTag.startsWith("th")) th else en
}

@Serializable
data class ChangelogEntry(
    val versionName: String,
    val versionCode: Int,
    /** ISO date, e.g. "2026-11-27". */
    val date: String,
    /** Short list shown in the "What's new" sheet. */
    val highlights: List<LocalizedText> = emptyList(),
    val new: List<LocalizedText> = emptyList(),
    val improved: List<LocalizedText> = emptyList(),
    val fixed: List<LocalizedText> = emptyList(),
)

interface ChangelogRepository {
    /** Newest first. Read from app assets `changelog.json`. */
    suspend fun entries(): List<ChangelogEntry>
    suspend fun entryFor(versionCode: Int): ChangelogEntry?
}

data class BuildInfo(
    val applicationId: String,
    val versionName: String,
    val versionCode: Int,
    /** ISO-8601 build timestamp. */
    val buildDate: String,
    /** AI model used to develop the app (from local.properties). Blank = not specified. */
    val aiModelName: String,
    val isDebug: Boolean,
)
