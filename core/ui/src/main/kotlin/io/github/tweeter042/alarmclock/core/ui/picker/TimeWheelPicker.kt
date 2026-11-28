package io.github.tweeter042.alarmclock.core.ui.picker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import io.github.tweeter042.alarmclock.core.ui.R
import io.github.tweeter042.alarmclock.core.ui.format.rememberTimeFormatter

/**
 * Hour / minute (/ AM-PM) wheels in the theme's clock font, One UI style. 12-hour mode shows
 * 1–12 and an AM/PM wheel; 24-hour mode 00–23. Values are always reported as a 24-hour [hour].
 *
 * @param minuteStep minute increments (must divide 60), e.g. 5 for 00, 05, 10, …
 */
@Composable
fun TimeWheelPicker(
    hour: Int,
    minute: Int,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
    use24h: Boolean = rememberTimeFormatter().use24h,
    minuteStep: Int = 1,
    enabled: Boolean = true,
) {
    require(hour in 0..23 && minute in 0..59) { "Invalid time $hour:$minute" }
    require(minuteStep in 1..30 && 60 % minuteStep == 0) { "minuteStep must divide 60" }
    val formatter = rememberTimeFormatter().let { if (it.use24h == use24h) it else it.with24h(use24h) }
    val digitStyle = AppTheme.typography.clock.copy(fontSize = 32.sp, color = AppTheme.colors.onSurface)
    val labelStyle = AppTheme.typography.titleLarge.copy(color = AppTheme.colors.onSurface)
    val itemHeight = wheelItemHeight(digitStyle)
    val digitWidth = rememberWheelWidth(listOf(formatter.minuteLabel(0), formatter.minuteLabel(88)), digitStyle)
    val amPmLabels = remember(formatter) { listOf(formatter.amPm(0), formatter.amPm(12)) }
    val amPmWidth = rememberWheelWidth(amPmLabels, labelStyle, padding = 12.dp)
    val minuteCount = 60 / minuteStep
    val isPm = hour >= 12

    val hourDescription = stringResource(R.string.ui_time_picker_hour)
    val minuteDescription = stringResource(R.string.ui_time_picker_minute)
    val amPmDescription = stringResource(R.string.ui_time_picker_am_pm)

    Box(modifier.fillMaxWidth().semantics { isTraversalGroup = true }, contentAlignment = Alignment.Center) {
        WheelSelectionBand(itemHeight, Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (use24h) {
                WheelColumn(
                    count = 24,
                    selectedIndex = hour,
                    onSelectedIndexChange = { onTimeChange(it, minute) },
                    label = formatter::hourLabel,
                    contentDescription = hourDescription,
                    textStyle = digitStyle,
                    itemHeight = itemHeight,
                    width = digitWidth,
                    enabled = enabled,
                )
            } else {
                WheelColumn(
                    count = 12,
                    selectedIndex = TimeInputRules.hour12Index(hour),
                    onSelectedIndexChange = { onTimeChange(TimeInputRules.hour24(it + 1, isPm), minute) },
                    label = { formatter.hourLabel(TimeInputRules.hour24(it + 1, pm = false)) },
                    contentDescription = hourDescription,
                    textStyle = digitStyle,
                    itemHeight = itemHeight,
                    width = digitWidth,
                    enabled = enabled,
                )
            }
            Text(
                text = ":",
                style = digitStyle,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(16.dp).clearAndSetSemantics { },
            )
            WheelColumn(
                count = minuteCount,
                selectedIndex = minute / minuteStep,
                onSelectedIndexChange = { onTimeChange(hour, it * minuteStep) },
                label = { formatter.minuteLabel(it * minuteStep) },
                contentDescription = minuteDescription,
                textStyle = digitStyle,
                itemHeight = itemHeight,
                width = digitWidth,
                enabled = enabled,
            )
            if (!use24h) {
                Spacer(Modifier.width(8.dp))
                WheelColumn(
                    count = 2,
                    selectedIndex = if (isPm) 1 else 0,
                    onSelectedIndexChange = { pmIndex -> onTimeChange(TimeInputRules.withPm(hour, pmIndex == 1), minute) },
                    label = { amPmLabels[it] },
                    contentDescription = amPmDescription,
                    textStyle = labelStyle,
                    itemHeight = itemHeight,
                    width = amPmWidth,
                    loop = false,
                    enabled = enabled,
                )
            }
        }
    }
}

/** Pure 12/24-hour conversions and keyboard-entry validation (unit tested). */
object TimeInputRules {
    /** 0–23 → index of the 12-hour wheel (1 → 0 … 12 → 11). */
    fun hour12Index(hour24: Int): Int = (hour24 % 12 + 11) % 12

    /** 12-hour clock (1–12) + AM/PM → 0–23. 12 AM is midnight (0), 12 PM is noon (12). */
    fun hour24(hour12: Int, pm: Boolean): Int {
        require(hour12 in 1..12) { "hour12 must be 1..12" }
        return hour12 % 12 + if (pm) 12 else 0
    }

    /** The same clock time in the morning ([pm] false) or the afternoon. */
    fun withPm(hour24: Int, pm: Boolean): Int = hour24 % 12 + if (pm) 12 else 0

    /** Parses a typed hour: 0–23 in 24-hour mode, 1–12 in 12-hour mode. Null if invalid. */
    fun parseHour(text: String, use24h: Boolean): Int? {
        val value = text.trim().takeIf { it.length in 1..2 && it.all(Char::isDigit) }?.toInt() ?: return null
        return value.takeIf { if (use24h) it in 0..23 else it in 1..12 }
    }

    /** Parses typed minutes (0–59, one or two digits). Null if invalid. */
    fun parseMinute(text: String): Int? {
        val value = text.trim().takeIf { it.length in 1..2 && it.all(Char::isDigit) }?.toInt() ?: return null
        return value.takeIf { it in 0..59 }
    }

    /** Keeps only ASCII digits, at most two. */
    fun sanitize(text: String): String = text.filter { it in '0'..'9' }.take(2)

    /**
     * True when no further digit could make a valid hour, so focus can move on to the minutes:
     * two digits, or a single digit that cannot start a two-digit hour (e.g. "3" in 24-hour mode).
     */
    fun hourComplete(text: String, use24h: Boolean): Boolean {
        if (text.length >= 2) return true
        val digit = text.singleOrNull()?.digitToIntOrNull() ?: return false
        return if (use24h) digit >= 3 else digit >= 2
    }
}
