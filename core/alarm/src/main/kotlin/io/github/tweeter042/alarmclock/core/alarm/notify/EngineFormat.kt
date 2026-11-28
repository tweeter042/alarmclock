package io.github.tweeter042.alarmclock.core.alarm.notify

import android.content.Context
import android.content.res.Configuration
import android.text.format.DateFormat
import io.github.tweeter042.alarmclock.core.model.alarm.Alarm
import io.github.tweeter042.alarmclock.core.model.format.LocalizedFormat
import io.github.tweeter042.alarmclock.core.model.settings.AppSettings
import io.github.tweeter042.alarmclock.core.model.settings.DarkModePref
import io.github.tweeter042.alarmclock.core.model.settings.TimeFormatPref
import io.github.tweeter042.alarmclock.core.model.theme.DYNAMIC_PALETTE_ID
import io.github.tweeter042.alarmclock.core.model.theme.ThemeCatalog
import io.github.tweeter042.alarmclock.core.model.theme.ThemeId
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Times in notifications: the app language (per-app locale), 12/24 h and Thai numerals from the
 * region settings.
 */
internal class EngineFormat(context: Context, settings: AppSettings, private val zone: ZoneId) {
    private val locale: Locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
    private val numerals = settings.region.numerals
    private val use24h: Boolean = when (settings.region.timeFormat) {
        TimeFormatPref.H24 -> true
        TimeFormatPref.H12 -> false
        TimeFormatPref.SYSTEM -> runCatching { DateFormat.is24HourFormat(context) }.getOrDefault(true)
    }

    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm a", locale)
    private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", locale)

    /** "06:30" / "6:30 AM" (Thai digits if chosen). */
    fun time(epochMillis: Long): String =
        LocalizedFormat.digits(timeFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(zone)), numerals)

    /** "06:30" today, "Mon 06:30" on another day. */
    fun dayAndTime(epochMillis: Long, now: Long): String {
        val at = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return if (at.toLocalDate() == today) {
            time(epochMillis)
        } else {
            LocalizedFormat.digits("${dayFormatter.format(at)} ${timeFormatter.format(at)}", numerals)
        }
    }
}

/** The theme accent colour for the ring notification (Samsung Edge lighting tint). */
internal object ThemeAccent {
    fun argb(context: Context, settings: AppSettings, alarm: Alarm?): Int? = try {
        val appearance = settings.appearance
        val alarmTheme = alarm?.appearance?.themeId
        val themeId: ThemeId = alarmTheme ?: appearance.ringThemeId ?: appearance.themeId
        val paletteId = when {
            alarmTheme != null -> alarm.appearance.paletteId
            appearance.ringThemeId != null -> appearance.ringPaletteId
            else -> null
        } ?: appearance.paletteIdFor(themeId)
        val theme = ThemeCatalog.theme(themeId)
        val systemNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val dark = !theme.supportsLight || when (appearance.darkMode) {
            DarkModePref.DARK -> true
            DarkModePref.LIGHT -> false
            DarkModePref.SYSTEM -> systemNight
        }
        if (themeId == ThemeId.MATERIAL_YOU && paletteId == DYNAMIC_PALETTE_ID) {
            context.getColor(if (dark) android.R.color.system_accent1_200 else android.R.color.system_accent1_600)
        } else {
            val palette = ThemeCatalog.palette(themeId, paletteId)
            (if (dark) palette.dark else palette.light).accent.toInt()
        }
    } catch (t: Throwable) {
        null
    }
}
