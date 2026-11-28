package io.github.tweeter042.alarmclock.core.ui.picker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.github.tweeter042.alarmclock.core.designsystem.component.AppBottomSheet
import io.github.tweeter042.alarmclock.core.designsystem.component.AppButton
import io.github.tweeter042.alarmclock.core.designsystem.component.AppButtonVariant
import io.github.tweeter042.alarmclock.core.designsystem.component.AppIconButton
import io.github.tweeter042.alarmclock.core.designsystem.component.readableAccent
import io.github.tweeter042.alarmclock.core.designsystem.icon.AppIcons
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import io.github.tweeter042.alarmclock.core.model.time.AlarmTime
import io.github.tweeter042.alarmclock.core.ui.R
import io.github.tweeter042.alarmclock.core.ui.format.rememberTimeFormatter
import kotlinx.coroutines.launch
import io.github.tweeter042.alarmclock.core.designsystem.R as DsR

/** How [TimePickerSheet] takes input. */
enum class TimeInputMode { Wheel, Keyboard }

/**
 * A bottom sheet for picking a time: wheels or typed digits (toggle in the header), Cancel and
 * Done at the bottom within thumb reach.
 *
 * Exactly one of [onConfirm] / [onDismiss] is called, after the sheet has closed; stop showing
 * the sheet in both.
 *
 * @param supportingText live line under the picker for the chosen time, e.g. "Rings in 7 h 24 min".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerSheet(
    initialTime: AlarmTime,
    onConfirm: (AlarmTime) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.ui_time_picker_title),
    use24h: Boolean = rememberTimeFormatter().use24h,
    minuteStep: Int = 1,
    initialMode: TimeInputMode = TimeInputMode.Wheel,
    confirmLabel: String = stringResource(R.string.ui_done),
    supportingText: ((AlarmTime) -> String?)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var hour by rememberSaveable { mutableIntStateOf(initialTime.hour) }
    var minute by rememberSaveable { mutableIntStateOf(initialTime.minute.roundDown(minuteStep)) }
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var valid by rememberSaveable { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    fun close(then: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { then() }
    }
    AppBottomSheet(onDismissRequest = onDismiss, modifier = modifier, sheetState = sheetState) {
        TimePickerSheetContent(
            title = title,
            hour = hour,
            minute = minute,
            onTimeChange = { h, m ->
                hour = h
                minute = m
            },
            mode = mode,
            onModeChange = {
                mode = it
                valid = true
            },
            use24h = use24h,
            minuteStep = minuteStep,
            valid = valid,
            onValidChange = { valid = it },
            supportingText = supportingText?.invoke(AlarmTime(hour, minute)),
            confirmLabel = confirmLabel,
            onConfirm = { if (valid) close { onConfirm(AlarmTime(hour, minute)) } },
            onCancel = { close(onDismiss) },
        )
    }
}

/** The body of [TimePickerSheet] (also used directly in screenshot tests). */
@Composable
internal fun TimePickerSheetContent(
    title: String,
    hour: Int,
    minute: Int,
    onTimeChange: (Int, Int) -> Unit,
    mode: TimeInputMode,
    onModeChange: (TimeInputMode) -> Unit,
    use24h: Boolean,
    minuteStep: Int,
    valid: Boolean,
    onValidChange: (Boolean) -> Unit,
    supportingText: String?,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    requestKeyboardFocus: Boolean = true,
) {
    val spacing = AppTheme.spacing
    Column(modifier.fillMaxWidth().padding(horizontal = spacing.xl)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = AppTheme.typography.titleLarge,
                color = AppTheme.colors.onSurface,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            AppIconButton(
                icon = if (mode == TimeInputMode.Wheel) AppIcons.Keyboard else AppIcons.Schedule,
                contentDescription = stringResource(
                    if (mode == TimeInputMode.Wheel) R.string.ui_time_picker_use_keyboard else R.string.ui_time_picker_use_wheel,
                ),
                onClick = { onModeChange(if (mode == TimeInputMode.Wheel) TimeInputMode.Keyboard else TimeInputMode.Wheel) },
            )
        }
        Spacer(Modifier.height(spacing.md))
        when (mode) {
            TimeInputMode.Wheel -> TimeWheelPicker(
                hour = hour,
                minute = minute,
                onTimeChange = onTimeChange,
                use24h = use24h,
                minuteStep = minuteStep,
            )
            TimeInputMode.Keyboard -> TimeKeyboardInput(
                hour = hour,
                minute = minute,
                onTimeChange = onTimeChange,
                use24h = use24h,
                onValidChange = onValidChange,
                onDone = onConfirm,
                requestFocus = requestKeyboardFocus,
            )
        }
        if (supportingText != null) {
            Spacer(Modifier.height(spacing.md))
            Text(
                text = supportingText,
                style = AppTheme.typography.bodyMedium,
                color = readableAccent(),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(spacing.xl))
        SheetButtons(confirmLabel = confirmLabel, confirmEnabled = valid, onConfirm = onConfirm, onCancel = onCancel)
    }
}

/** Cancel / confirm at the bottom of a picker sheet. */
@Composable
internal fun SheetButtons(confirmLabel: String, confirmEnabled: Boolean, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.md)) {
        AppButton(
            text = stringResource(DsR.string.ds_cancel),
            onClick = onCancel,
            variant = AppButtonVariant.Secondary,
            modifier = Modifier.weight(1f),
        )
        AppButton(
            text = confirmLabel,
            onClick = onConfirm,
            enabled = confirmEnabled,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun Int.roundDown(step: Int): Int = this - this % step.coerceAtLeast(1)
