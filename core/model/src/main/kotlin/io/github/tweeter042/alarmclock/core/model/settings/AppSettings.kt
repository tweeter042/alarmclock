package io.github.tweeter042.alarmclock.core.model.settings

import io.github.tweeter042.alarmclock.core.model.alarm.Alarm
import io.github.tweeter042.alarmclock.core.model.alarm.WatchRingMode
import io.github.tweeter042.alarmclock.core.model.font.FontIds
import io.github.tweeter042.alarmclock.core.model.theme.ThemeCatalog
import io.github.tweeter042.alarmclock.core.model.theme.ThemeId
import io.github.tweeter042.alarmclock.core.model.time.AlarmTime
import io.github.tweeter042.alarmclock.core.model.time.IsoDays
import kotlinx.serialization.Serializable

/** All user preferences. Stored as one JSON document in device-protected storage (core:data). */
@Serializable
data class AppSettings(
    val appearance: AppearanceSettings = AppearanceSettings(),
    val region: RegionSettings = RegionSettings(),
    /** Template for new alarms (time, id and label are ignored). */
    val alarmDefaults: Alarm = Alarm(),
    val reliability: ReliabilitySettings = ReliabilitySettings(),
    val bedtime: BedtimeSettings = BedtimeSettings(),
    val weather: WeatherSettings = WeatherSettings(),
    val holidays: HolidaySettings = HolidaySettings(),
    val smartThings: SmartThingsSettings = SmartThingsSettings(),
    val watch: WatchSettings = WatchSettings(),
    val morning: MorningSettings = MorningSettings(),
    val backup: BackupSettings = BackupSettings(),
    val accessibility: AccessibilitySettings = AccessibilitySettings(),
    val samsung: SamsungSettings = SamsungSettings(),
    val sleepSounds: SleepSoundSettings = SleepSoundSettings(),
    val onboardingCompleted: Boolean = false,
    /** versionCode for which "What's new" was last shown. */
    val lastSeenVersionCode: Int = 0,
    val showWhatsNewOnUpdate: Boolean = true,
)

@Serializable
enum class DarkModePref { SYSTEM, LIGHT, DARK }

@Serializable
data class AppearanceSettings(
    val themeId: ThemeId = ThemeId.MINIMAL,
    /** Remembers the chosen palette per theme. Missing entry = theme's default palette. */
    val paletteByTheme: Map<ThemeId, String> = emptyMap(),
    val darkMode: DarkModePref = DarkModePref.SYSTEM,
    /** FontIds.THEME_DEFAULT, FontIds.SYSTEM, a FontCatalog id, or FontIds.CUSTOM_PREFIX + file name. */
    val bodyFontId: String = FontIds.THEME_DEFAULT,
    val clockFontId: String = FontIds.THEME_DEFAULT,
    /** Extra scale on top of the system font scale (0.85..1.4). */
    val fontScale: Float = 1f,
    /** Optional different theme for the ring screen (null = same as app). */
    val ringThemeId: ThemeId? = null,
    val ringPaletteId: String? = null,
) {
    fun paletteIdFor(theme: ThemeId): String =
        paletteByTheme[theme] ?: ThemeCatalog.theme(theme).defaultPaletteId
}

@Serializable
enum class AppLanguage(val tag: String) { ENGLISH("en"), THAI("th") }

@Serializable
enum class TimeFormatPref { SYSTEM, H12, H24 }

@Serializable
enum class YearStyle { GREGORIAN, BUDDHIST }

@Serializable
enum class NumeralStyle { ARABIC, THAI }

@Serializable
enum class TemperatureUnit { CELSIUS, FAHRENHEIT }

@Serializable
data class RegionSettings(
    /** Mirror of the per-app locale (LocaleManager is the source of truth). */
    val language: AppLanguage = AppLanguage.ENGLISH,
    val timeFormat: TimeFormatPref = TimeFormatPref.SYSTEM,
    val yearStyle: YearStyle = YearStyle.GREGORIAN,
    val numerals: NumeralStyle = NumeralStyle.ARABIC,
    /** 0 = locale default, otherwise ISO day (1 = Monday, 7 = Sunday). */
    val firstDayOfWeek: Int = 0,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
)

@Serializable
data class ReliabilitySettings(
    /** Optional always-on foreground "alarm guard" service. */
    val guardServiceEnabled: Boolean = false,
    /** Minutes before an alarm to show the "upcoming alarm" notification. 0 = off. */
    val upcomingNoticeMinutes: Int = 60,
    val missedAlarmNotice: Boolean = true,
    val lowBatteryWarning: Boolean = true,
    val lowBatteryPercent: Int = 20,
    /** After a reboot, alarms missed within this many minutes ring immediately. */
    val rebootGraceMinutes: Int = 15,
    /** Samsung settings that cannot be read programmatically, confirmed by the user (HealthItemId name -> true). */
    val manualChecks: Map<String, Boolean> = emptyMap(),
    val lastTestAt: Long? = null,
    val lastTestOk: Boolean? = null,
)

@Serializable
data class BedtimeSettings(
    val enabled: Boolean = false,
    val time: AlarmTime = AlarmTime(22, 30),
    val days: Set<Int> = IsoDays.ALL,
    val sleepGoalMinutes: Int = 480,
    val reminderMinutesBefore: Int = 30,
    /** Turn on Do Not Disturb at bedtime (needs notification policy access). */
    val enableDnd: Boolean = false,
    /** Turn Do Not Disturb off again when the morning alarm is dismissed. */
    val disableDndAtAlarm: Boolean = true,
    val batteryCheck: Boolean = true,
    /** Dim SmartThings lights at bedtime. */
    val dimLights: Boolean = false,
    val dimDeviceIds: List<String> = emptyList(),
)

@Serializable
enum class LocationMode { DEVICE, MANUAL }

@Serializable
data class WeatherSettings(
    val enabled: Boolean = true,
    val locationMode: LocationMode = LocationMode.DEVICE,
    val manualLatitude: Double? = null,
    val manualLongitude: Double? = null,
    val manualPlaceName: String? = null,
)

@Serializable
data class HolidaySettings(
    /** Fixed-date Thai public holidays generated by the app. */
    val builtInThai: Boolean = true,
    /** Calendars to import holidays from (e.g. Google "Holidays in Thailand"). */
    val calendarIds: List<Long> = emptyList(),
    val lastCalendarImportAt: Long? = null,
)

@Serializable
enum class SmartThingsAuthMode { PERSONAL_TOKEN, OAUTH }

/** Tokens and client secrets are NOT stored here; see SecretStore keys. */
@Serializable
data class SmartThingsSettings(
    val enabled: Boolean = false,
    val authMode: SmartThingsAuthMode = SmartThingsAuthMode.PERSONAL_TOKEN,
    val oauthClientId: String = "",
    val oauthRedirectUri: String = "",
    val accessTokenExpiresAt: Long? = null,
    val locationId: String? = null,
)

@Serializable
data class WatchSettings(
    val syncEnabled: Boolean = true,
    val defaultMode: WatchRingMode = WatchRingMode.PHONE_AND_WATCH,
    val themeFollowsPhone: Boolean = true,
)

@Serializable
data class MorningSettings(
    val routineEnabled: Boolean = true,
    val quotesEnabled: Boolean = true,
    /** RSS/Atom feed for headlines. Blank = no news. */
    val newsFeedUrl: String = "",
)

@Serializable
data class BackupSettings(
    val autoBackupEnabled: Boolean = false,
    /** SAF tree URI (e.g. a Google Drive folder) with a persisted write permission. */
    val treeUri: String? = null,
    val intervalDays: Int = 1,
    val lastBackupAt: Long? = null,
)

@Serializable
data class AccessibilitySettings(
    val largeButtons: Boolean = false,
    val highContrast: Boolean = false,
    val reduceMotion: Boolean = false,
    val hapticFeedback: Boolean = true,
)

@Serializable
data class SamsungSettings(
    /** Handle media keys (S Pen Air actions media control / headset) while ringing. */
    val mediaButtonsWhileRinging: Boolean = true,
    /** Tint the alarm notification (Edge lighting colour) with the theme accent. */
    val edgeLightingThemeColor: Boolean = true,
    /** Keep primary actions in the lower half of the screen (One UI reachability). */
    val oneHandedLayout: Boolean = true,
)

@Serializable
enum class SleepSound { WHITE_NOISE, PINK_NOISE, BROWN_NOISE, RAIN, OCEAN, FAN, FIREPLACE, FOREST_NIGHT }

@Serializable
data class SleepSoundSettings(
    val lastSound: SleepSound = SleepSound.RAIN,
    val volumePercent: Int = 50,
    /** 0 = play until stopped. */
    val timerMinutes: Int = 30,
    val fadeOutSeconds: Int = 60,
)
