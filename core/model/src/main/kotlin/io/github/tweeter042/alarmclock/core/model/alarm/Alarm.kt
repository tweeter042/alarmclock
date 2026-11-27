package io.github.tweeter042.alarmclock.core.model.alarm

import io.github.tweeter042.alarmclock.core.model.theme.ThemeId
import io.github.tweeter042.alarmclock.core.model.time.AlarmTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A user alarm. Everything the ring engine and the UI need is in here; nested configs are
 * stored as JSON by core:data, so adding a field with a default value is a safe change.
 *
 * id == 0L means "not saved yet".
 */
@Serializable
data class Alarm(
    val id: Long = 0L,
    val enabled: Boolean = true,
    val time: AlarmTime = AlarmTime(7, 0),
    val label: String = "",
    val repeat: RepeatRule = RepeatRule.Once(),
    val groupId: Long? = null,
    /** Skip days that are holidays (see HolidayProvider). */
    val skipHolidays: Boolean = false,
    /** Epoch millis of one upcoming occurrence the user chose to skip ("Skip next"). */
    val skippedOccurrenceAt: Long? = null,
    val sound: SoundConfig = SoundConfig(),
    val vibration: VibrationConfig = VibrationConfig(),
    val snooze: SnoozeConfig = SnoozeConfig(),
    val dismiss: DismissConfig = DismissConfig(),
    /** Wake-up missions that must be completed (in order) to dismiss. Empty = plain dismiss. */
    val missions: List<MissionConfig> = emptyList(),
    val awakeCheck: AwakeCheckConfig = AwakeCheckConfig(),
    val smartWake: SmartWakeConfig = SmartWakeConfig(),
    val sunrise: SunriseConfig = SunriseConfig(),
    val briefing: BriefingConfig = BriefingConfig(),
    val weatherAdjust: WeatherAdjustConfig = WeatherAdjustConfig(),
    val smartHome: SmartHomeConfig = SmartHomeConfig(),
    val afterDismiss: AfterDismissConfig = AfterDismissConfig(),
    val watch: WatchConfig = WatchConfig(),
    val appearance: RingAppearance = RingAppearance(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

// ---------------------------------------------------------------------------------------------
// Sound
// ---------------------------------------------------------------------------------------------

@Serializable
data class SoundConfig(
    val source: SoundSource = SoundSource.BuiltIn(BuiltInTones.DEFAULT_ID),
    /** Target volume as a percentage of the alarm stream maximum (1..100). */
    val volumePercent: Int = 80,
    /** Seconds to ramp from silence to [volumePercent]. 0 = start at full volume. */
    val fadeInSeconds: Int = 30,
    /** Vibrate only, no sound. */
    val vibrateOnly: Boolean = false,
    /**
     * When true the engine forces the alarm stream to [volumePercent] while ringing (and restores it
     * afterwards) so a muted alarm stream can never silence the alarm.
     */
    val overrideSystemVolume: Boolean = true,
)

@Serializable
sealed interface SoundSource {
    /** A tone bundled with the app; [toneId] is one of [BuiltInTones.ALL]. */
    @Serializable
    @SerialName("builtin")
    data class BuiltIn(val toneId: String) : SoundSource

    /** A system ringtone/alarm sound picked through RingtoneManager. */
    @Serializable
    @SerialName("ringtone")
    data class SystemRingtone(val uri: String, val title: String) : SoundSource

    /** A user file (SAF document URI with a persisted read permission). */
    @Serializable
    @SerialName("file")
    data class LocalFile(val uri: String, val title: String) : SoundSource

    /** Several user files; one is picked per ring (shuffled or in order). */
    @Serializable
    @SerialName("playlist")
    data class Playlist(val items: List<PlaylistItem>, val shuffle: Boolean = true) : SoundSource

    /**
     * Experimental: ask the Spotify app to play [uri] (e.g. "spotify:playlist:…").
     * The engine ALWAYS starts the fallback built-in tone and only stops it once Spotify audio is
     * confirmed to be playing, so a failure can never produce a silent alarm.
     */
    @Serializable
    @SerialName("spotify")
    data class Spotify(val uri: String, val title: String) : SoundSource
}

@Serializable
data class PlaylistItem(val uri: String, val title: String)

/** Bundled tone (audio file `res/raw/tone_<id>.ogg` in core:alarm). */
data class BuiltInTone(val id: String, val nameEn: String, val nameTh: String, val loud: Boolean = false)

object BuiltInTones {
    const val DEFAULT_ID = "sunrise_chime"
    const val TIMER_ID = "timer_done"
    const val AWAKE_CHECK_ID = "awake_ping"
    /** Used when the configured sound cannot be played. Must always exist. */
    const val FALLBACK_ID = "classic_beep"

    val ALL: List<BuiltInTone> = listOf(
        BuiltInTone("sunrise_chime", "Sunrise chime", "ระฆังรุ่งอรุณ"),
        BuiltInTone("gentle_bells", "Gentle bells", "ระฆังแผ่วเบา"),
        BuiltInTone("soft_keys", "Soft keys", "คีย์นุ่ม"),
        BuiltInTone("marimba", "Marimba", "มาริมบา"),
        BuiltInTone("kalimba", "Kalimba", "คาลิมบา"),
        BuiltInTone("birdsong", "Birdsong", "เสียงนกร้อง"),
        BuiltInTone("ocean_wake", "Ocean wake", "คลื่นยามเช้า"),
        BuiltInTone("rising_tones", "Rising tones", "เสียงไล่ระดับ"),
        BuiltInTone("classic_beep", "Classic beep", "บี๊บคลาสสิก"),
        BuiltInTone("digital_alarm", "Digital alarm", "นาฬิกาดิจิทัล"),
        BuiltInTone("lcd_watch", "LCD watch", "นาฬิกา LCD"),
        BuiltInTone("tick_tock", "Tick-tock", "ติ๊กต็อก"),
        BuiltInTone("pulse", "Pulse", "จังหวะพัลส์"),
        BuiltInTone("retro_arcade", "Retro arcade", "เกมตู้ย้อนยุค"),
        BuiltInTone("terminal_beep", "Terminal", "เทอร์มินัล"),
        BuiltInTone("neon_synth", "Neon synth", "ซินธ์นีออน"),
        BuiltInTone("siren", "Siren", "ไซเรน", loud = true),
        BuiltInTone("heavy_sleeper", "Heavy sleeper", "สำหรับคนตื่นยาก", loud = true),
    )

    /** Non-alarm tones (also bundled, not offered in the alarm sound picker). */
    val SYSTEM_TONES: List<String> = listOf(TIMER_ID, AWAKE_CHECK_ID)

    fun byId(id: String): BuiltInTone? = ALL.firstOrNull { it.id == id }
}

// ---------------------------------------------------------------------------------------------
// Vibration
// ---------------------------------------------------------------------------------------------

@Serializable
data class VibrationConfig(
    val enabled: Boolean = true,
    val pattern: VibrationPattern = VibrationPattern.BASIC,
)

@Serializable
enum class VibrationPattern { BASIC, HEARTBEAT, RAPID, WAVE, SOS, TICK_TOCK, ESCALATING }

// ---------------------------------------------------------------------------------------------
// Snooze & dismiss
// ---------------------------------------------------------------------------------------------

@Serializable
data class SnoozeConfig(
    val enabled: Boolean = true,
    val durationMinutes: Int = 5,
    /** Maximum number of snoozes; 0 = unlimited. */
    val maxCount: Int = 3,
    val mode: SnoozeMode = SnoozeMode.FIXED,
    /** For [SnoozeMode.DECREASING]: minutes removed per snooze. */
    val decreaseStepMinutes: Int = 1,
    /** For [SnoozeMode.DECREASING]: lower bound. */
    val minDurationMinutes: Int = 1,
)

@Serializable
enum class SnoozeMode {
    /** Same duration every time. */
    FIXED,
    /** Each snooze is shorter by [SnoozeConfig.decreaseStepMinutes]. */
    DECREASING,
    /** The alarm's missions must be completed to snooze as well as to dismiss. */
    MISSION_TO_SNOOZE,
    /** Each snooze raises mission difficulty by one level. */
    HARDER_EACH_TIME,
}

@Serializable
data class DismissConfig(
    /** How long the alarm rings before [autoAction] happens. */
    val ringDurationMinutes: Int = 10,
    val autoAction: AutoAction = AutoAction.SNOOZE,
    /** Turning the phone face-down snoozes. */
    val flipToSnooze: Boolean = false,
    val volumeButtons: ButtonAction = ButtonAction.SNOOZE,
    /** Screen-off (power key) while ringing. */
    val powerButton: ButtonAction = ButtonAction.NONE,
    /** Media key events: S Pen button (Air actions media control), headset or Galaxy Buds. */
    val mediaButton: ButtonAction = ButtonAction.SNOOZE,
    /** Require a slide gesture instead of a tap to dismiss. */
    val slideToDismiss: Boolean = false,
    /**
     * Anti-cheat: block back/home escapes (ring screen is re-shown), keep the alarm volume up and
     * restart ringing if the ring screen is closed while missions are unfinished.
     */
    val antiCheat: Boolean = false,
    /** Lower the volume while the user is actively doing a mission. */
    val lowerVolumeDuringMission: Boolean = true,
    /** If the user stops interacting with a mission for this long, full volume resumes. */
    val missionIdleTimeoutSeconds: Int = 30,
)

@Serializable
enum class AutoAction { SNOOZE, DISMISS }

/** What a hardware/gesture shortcut does. DISMISS is downgraded to "open missions" if missions exist. */
@Serializable
enum class ButtonAction { NONE, SNOOZE, DISMISS }

// ---------------------------------------------------------------------------------------------
// Post-dismiss & extras
// ---------------------------------------------------------------------------------------------

/** "Are you awake?" follow-up after dismissing. */
@Serializable
data class AwakeCheckConfig(
    val enabled: Boolean = false,
    val delayMinutes: Int = 5,
    /** If not confirmed within this time the full alarm rings again. */
    val responseSeconds: Int = 90,
)

/** Wake in light sleep within [windowMinutes] before the alarm time. */
@Serializable
data class SmartWakeConfig(
    val enabled: Boolean = false,
    val windowMinutes: Int = 30,
    val source: SmartWakeSource = SmartWakeSource.WATCH,
)

@Serializable
enum class SmartWakeSource {
    /** Galaxy Watch sensors (movement + heart rate). */
    WATCH,
    /** Phone accelerometer (phone placed on the mattress). */
    PHONE,
}

/** Gradual light before the alarm: screen and/or SmartThings lights. */
@Serializable
data class SunriseConfig(
    val enabled: Boolean = false,
    val durationMinutes: Int = 15,
    val useScreen: Boolean = true,
    val useLights: Boolean = false,
    /** SmartThings device ids (switchLevel / colorTemperature capable). */
    val lightDeviceIds: List<String> = emptyList(),
    val maxBrightnessPercent: Int = 100,
    /** Ramp colour temperature from warm (2200K) to cool daylight (5000K). */
    val warmToCool: Boolean = true,
)

/** Text-to-speech morning briefing. */
@Serializable
data class BriefingConfig(
    val enabled: Boolean = false,
    val timing: BriefingTiming = BriefingTiming.AFTER_DISMISS,
    val includeTime: Boolean = true,
    val includeDate: Boolean = true,
    val includeWeather: Boolean = true,
    val includeCalendar: Boolean = true,
    val includeLabel: Boolean = true,
    val includeQuote: Boolean = false,
    /** 50..200, 100 = normal speed. */
    val speechRatePercent: Int = 100,
)

@Serializable
enum class BriefingTiming {
    /** Speak over the (ducked) alarm sound after it has rung for a few seconds. */
    DURING_RING,
    /** Speak once the alarm is dismissed. */
    AFTER_DISMISS,
}

/** Ring earlier when the forecast matches one of [conditions]. */
@Serializable
data class WeatherAdjustConfig(
    val enabled: Boolean = false,
    val conditions: Set<WeatherCondition> = setOf(
        WeatherCondition.RAIN,
        WeatherCondition.HEAVY_RAIN,
        WeatherCondition.THUNDERSTORM,
    ),
    val minutesEarlier: Int = 15,
)

@Serializable
enum class WeatherCondition {
    CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, HEAVY_RAIN, THUNDERSTORM, SNOW, UNKNOWN
}

/** SmartThings scenes to run. */
@Serializable
data class SmartHomeConfig(
    val onRingSceneId: String? = null,
    val onRingSceneName: String? = null,
    val onDismissSceneId: String? = null,
    val onDismissSceneName: String? = null,
)

@Serializable
data class AfterDismissConfig(
    /** App to open after dismissing (e.g. YouTube Music / Spotify playlist deep link). */
    val openApp: OpenAppTarget? = null,
    /** Show the morning-routine checklist. */
    val showMorningRoutine: Boolean = false,
    /** Show the briefing card (weather, calendar, quote, news). */
    val showBriefingCard: Boolean = true,
)

@Serializable
data class OpenAppTarget(
    val packageName: String,
    val label: String,
    /** Optional deep link, e.g. "spotify:playlist:…" or "https://music.youtube.com/playlist?list=…". */
    val uri: String? = null,
)

@Serializable
data class WatchConfig(
    val mode: WatchRingMode = WatchRingMode.PHONE_AND_WATCH,
    /** Watch also plays a sound (otherwise vibration only). */
    val watchSound: Boolean = false,
)

@Serializable
enum class WatchRingMode {
    PHONE_AND_WATCH,
    /** Silent for the bedroom: only the watch vibrates. The phone still rings if no watch answers. */
    WATCH_ONLY,
    PHONE_ONLY,
}

/** Per-alarm look of the ring screen. Null = follow app settings. */
@Serializable
data class RingAppearance(
    val themeId: ThemeId? = null,
    val paletteId: String? = null,
    val showWeather: Boolean = true,
    val showDate: Boolean = true,
)
