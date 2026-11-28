package io.github.tweeter042.alarmclock.core.ui.picker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import io.github.tweeter042.alarmclock.core.ui.R
import io.github.tweeter042.alarmclock.core.ui.format.rememberTimeFormatter

/**
 * Hours / minutes / seconds wheels for timers, snooze lengths, nap durations and sleep timers.
 * Reports the total in milliseconds. Hidden units count as zero.
 *
 * @param maxHours the largest hour value (the hour wheel does not loop past it).
 */
@Composable
fun DurationPicker(
    durationMillis: Long,
    onDurationChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    showHours: Boolean = true,
    showSeconds: Boolean = true,
    maxHours: Int = 99,
    enabled: Boolean = true,
) {
    require(maxHours in 1..99) { "maxHours must be 1..99" }
    val totalSeconds = (durationMillis.coerceAtLeast(0L) / 1000L).coerceAtMost(maxHours * 3600L + 59 * 60 + 59)
    val hours = if (showHours) (totalSeconds / 3600).toInt() else 0
    val minutes = if (showHours) (totalSeconds / 60 % 60).toInt() else (totalSeconds / 60).toInt().coerceAtMost(59)
    val seconds = if (showSeconds) (totalSeconds % 60).toInt() else 0
    fun report(h: Int, m: Int, s: Int) = onDurationChange((h * 3600L + m * 60L + s) * 1000L)

    val formatter = rememberTimeFormatter()
    val digitStyle = AppTheme.typography.clock.copy(fontSize = 32.sp, color = AppTheme.colors.onSurface)
    val unitStyle = AppTheme.typography.labelLarge.copy(color = AppTheme.colors.onSurfaceVariant)
    val itemHeight = wheelItemHeight(digitStyle)
    val digitWidth = rememberWheelWidth(listOf(formatter.minuteLabel(88), formatter.minuteLabel(0)), digitStyle, padding = 12.dp)

    Box(modifier.fillMaxWidth().semantics { isTraversalGroup = true }, contentAlignment = Alignment.Center) {
        WheelSelectionBand(itemHeight, Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showHours) {
                WheelColumn(
                    count = maxHours + 1,
                    selectedIndex = hours,
                    onSelectedIndexChange = { report(it, minutes, seconds) },
                    label = formatter::minuteLabel,
                    contentDescription = stringResource(R.string.ui_duration_picker_hours),
                    textStyle = digitStyle,
                    itemHeight = itemHeight,
                    width = digitWidth,
                    loop = false,
                    enabled = enabled,
                )
                UnitLabel(stringResource(R.string.ui_unit_hours_label), unitStyle)
            }
            WheelColumn(
                count = 60,
                selectedIndex = minutes,
                onSelectedIndexChange = { report(hours, it, seconds) },
                label = formatter::minuteLabel,
                contentDescription = stringResource(R.string.ui_duration_picker_minutes),
                textStyle = digitStyle,
                itemHeight = itemHeight,
                width = digitWidth,
                enabled = enabled,
            )
            UnitLabel(stringResource(R.string.ui_unit_minutes_label), unitStyle)
            if (showSeconds) {
                WheelColumn(
                    count = 60,
                    selectedIndex = seconds,
                    onSelectedIndexChange = { report(hours, minutes, it) },
                    label = formatter::minuteLabel,
                    contentDescription = stringResource(R.string.ui_duration_picker_seconds),
                    textStyle = digitStyle,
                    itemHeight = itemHeight,
                    width = digitWidth,
                    enabled = enabled,
                )
                UnitLabel(stringResource(R.string.ui_unit_seconds_label), unitStyle)
            }
        }
    }
}

@Composable
private fun UnitLabel(text: String, style: androidx.compose.ui.text.TextStyle) {
    Text(text = text, style = style, modifier = Modifier.clearAndSetSemantics { })
    Spacer(Modifier.width(4.dp))
}
