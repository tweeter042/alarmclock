package io.github.tweeter042.alarmclock.core.alarm.ring

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import io.github.tweeter042.alarmclock.core.alarm.R
import io.github.tweeter042.alarmclock.core.model.alarm.BuiltInTones

/**
 * The bundled tones (`res/raw/tone_<id>.ogg`) by [BuiltInTones] id. A compile-time table (no
 * `getIdentifier`), so a missing file is a build error and resource shrinking keeps every tone.
 */
internal object ToneResources {
    private val byId: Map<String, Int> = mapOf(
        "sunrise_chime" to R.raw.tone_sunrise_chime,
        "gentle_bells" to R.raw.tone_gentle_bells,
        "soft_keys" to R.raw.tone_soft_keys,
        "marimba" to R.raw.tone_marimba,
        "kalimba" to R.raw.tone_kalimba,
        "birdsong" to R.raw.tone_birdsong,
        "ocean_wake" to R.raw.tone_ocean_wake,
        "rising_tones" to R.raw.tone_rising_tones,
        "classic_beep" to R.raw.tone_classic_beep,
        "digital_alarm" to R.raw.tone_digital_alarm,
        "lcd_watch" to R.raw.tone_lcd_watch,
        "tick_tock" to R.raw.tone_tick_tock,
        "pulse" to R.raw.tone_pulse,
        "retro_arcade" to R.raw.tone_retro_arcade,
        "terminal_beep" to R.raw.tone_terminal_beep,
        "neon_synth" to R.raw.tone_neon_synth,
        "siren" to R.raw.tone_siren,
        "heavy_sleeper" to R.raw.tone_heavy_sleeper,
        BuiltInTones.TIMER_ID to R.raw.tone_timer_done,
        BuiltInTones.AWAKE_CHECK_ID to R.raw.tone_awake_ping,
    )

    val ids: Set<String> get() = byId.keys

    fun resId(toneId: String): Int? = byId[toneId]

    /** `android.resource://<package>/<resId>`, playable by ExoPlayer, also in Direct Boot. */
    fun uri(context: Context, toneId: String): Uri? = resId(toneId)?.let { uri(context, it) }

    fun uri(context: Context, resId: Int): Uri = Uri.Builder()
        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        .authority(context.packageName)
        .appendPath(resId.toString())
        .build()

    fun fallbackUri(context: Context): Uri = uri(context, R.raw.tone_classic_beep)
}
