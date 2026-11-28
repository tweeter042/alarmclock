package io.github.tweeter042.alarmclock.core.alarm

import io.github.tweeter042.alarmclock.core.model.engine.AlarmEngineHooks
import io.github.tweeter042.alarmclock.core.model.engine.RingUiLauncher
import io.github.tweeter042.alarmclock.core.model.service.BriefingComposer
import io.github.tweeter042.alarmclock.core.model.service.BuildInfo
import io.github.tweeter042.alarmclock.core.model.service.CalendarSource
import io.github.tweeter042.alarmclock.core.model.service.HolidayProvider
import io.github.tweeter042.alarmclock.core.model.service.MusicLauncher
import io.github.tweeter042.alarmclock.core.model.service.Speaker
import io.github.tweeter042.alarmclock.core.model.service.WeatherProvider
import io.github.tweeter042.alarmclock.core.model.service.WearBridge

/**
 * What the engine needs from the rest of the app, handed to [DefaultAlarmEngine] by the Application.
 *
 * Only [hooks], [ringUi] and [buildInfo] are required. Every optional integration may be null (or
 * fail, hang, or be unavailable in Direct Boot): the engine wraps each call in a timeout and a
 * catch-all, and an alarm always rings without them.
 *
 * @property hooks side effects of engine events (watch sync, SmartThings, widgets, …).
 * @property ringUi intents of the ring, sunrise and main screens (feature:ringing / :app).
 * @property buildInfo app version info (logged with integrity reports).
 * @property speaker text-to-speech for the morning briefing.
 * @property briefing composes the spoken morning briefing.
 * @property weather forecast for weather-adjusted alarms and pre-alarm prefetching.
 * @property holidays holiday dates for "skip holidays"; when null the engine reads the enabled
 *   holidays of the holiday repository directly.
 * @property calendar calendar events for calendar alarm rules (used only while it has permission).
 * @property wear Galaxy Watch bridge: nearby-watch detection for WATCH_ONLY alarms and the health check.
 * @property music experimental Spotify playback as an alarm sound.
 */
data class EngineDependencies(
    val hooks: AlarmEngineHooks,
    val ringUi: RingUiLauncher,
    val buildInfo: BuildInfo,
    val speaker: Speaker? = null,
    val briefing: BriefingComposer? = null,
    val weather: WeatherProvider? = null,
    val holidays: HolidayProvider? = null,
    val calendar: CalendarSource? = null,
    val wear: WearBridge? = null,
    val music: MusicLauncher? = null,
)
