package io.github.tweeter042.alarmclock.core.ui.picker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.tweeter042.alarmclock.core.designsystem.component.AppSegmentedButtons
import io.github.tweeter042.alarmclock.core.designsystem.component.AppTextField
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import io.github.tweeter042.alarmclock.core.ui.R
import io.github.tweeter042.alarmclock.core.ui.format.rememberTimeFormatter

/**
 * Typing the time instead of using the wheels: hour and minute fields (digits keyboard, focus
 * moves to the minutes once the hour is complete) and, in 12-hour mode, AM/PM buttons. Invalid
 * input shows an error under the field and [onValidChange] reports false until it is fixed;
 * every valid entry is reported through [onTimeChange] as a 24-hour time.
 */
@Composable
fun TimeKeyboardInput(
    hour: Int,
    minute: Int,
    onTimeChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
    use24h: Boolean = rememberTimeFormatter().use24h,
    onValidChange: (Boolean) -> Unit = {},
    onDone: () -> Unit = {},
    requestFocus: Boolean = true,
) {
    val formatter = rememberTimeFormatter().let { if (it.use24h == use24h) it else it.with24h(use24h) }
    var hourText by rememberSaveable(use24h) { mutableStateOf(initialHourText(hour, use24h)) }
    var minuteText by rememberSaveable { mutableStateOf(minute.toString().padStart(2, '0')) }
    var pm by rememberSaveable { mutableStateOf(hour >= 12) }
    val currentOnTimeChange by rememberUpdatedState(onTimeChange)
    val currentOnValidChange by rememberUpdatedState(onValidChange)

    val parsedHour = TimeInputRules.parseHour(hourText, use24h)
    val parsedMinute = TimeInputRules.parseMinute(minuteText)
    val valid = parsedHour != null && parsedMinute != null
    LaunchedEffect(parsedHour, parsedMinute, pm) {
        currentOnValidChange(valid)
        if (parsedHour != null && parsedMinute != null) {
            val hour24 = if (use24h) parsedHour else TimeInputRules.hour24(parsedHour, pm)
            currentOnTimeChange(hour24, parsedMinute)
        }
    }

    val hourFocus = remember { FocusRequester() }
    val minuteFocus = remember { FocusRequester() }
    if (requestFocus) {
        LaunchedEffect(Unit) { runCatching { hourFocus.requestFocus() } }
    }

    val hourRange = if (use24h) formatter.number(0) to formatter.number(23) else formatter.number(1) to formatter.number(12)
    val hourError = if (hourText.isNotEmpty() && parsedHour == null) {
        stringResource(R.string.ui_time_picker_invalid_hour, hourRange.first, hourRange.second)
    } else {
        null
    }
    val minuteError = if (minuteText.isNotEmpty() && parsedMinute == null) {
        stringResource(R.string.ui_time_picker_invalid_minute, formatter.minuteLabel(0), formatter.minuteLabel(59))
    } else {
        null
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.md)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm)) {
            AppTextField(
                value = hourText,
                onValueChange = { text ->
                    val clean = TimeInputRules.sanitize(text)
                    val typed = clean.length > hourText.length
                    hourText = clean
                    if (typed && TimeInputRules.hourComplete(clean, use24h) && TimeInputRules.parseHour(clean, use24h) != null) {
                        runCatching { minuteFocus.requestFocus() }
                    }
                },
                label = stringResource(R.string.ui_time_picker_hour),
                errorText = hourError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { runCatching { minuteFocus.requestFocus() } }),
                modifier = Modifier.weight(1f).focusRequester(hourFocus),
            )
            Text(
                text = ":",
                style = AppTheme.typography.headlineMedium,
                color = AppTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 34.dp).clearAndSetSemantics { },
            )
            AppTextField(
                value = minuteText,
                onValueChange = { minuteText = TimeInputRules.sanitize(it) },
                label = stringResource(R.string.ui_time_picker_minute),
                errorText = minuteError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (valid) onDone() }),
                modifier = Modifier.weight(1f).focusRequester(minuteFocus),
            )
        }
        if (!use24h) {
            AppSegmentedButtons(
                options = listOf(formatter.amPm(0), formatter.amPm(12)),
                selectedIndex = if (pm) 1 else 0,
                onSelect = { pm = it == 1 },
            )
        }
    }
}

private fun initialHourText(hour24: Int, use24h: Boolean): String =
    if (use24h) hour24.toString().padStart(2, '0') else (TimeInputRules.hour12Index(hour24) + 1).toString()
