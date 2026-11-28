package io.github.tweeter042.alarmclock.core.ui.picker

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.sharp.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tweeter042.alarmclock.core.designsystem.component.AppButton
import io.github.tweeter042.alarmclock.core.designsystem.component.AppButtonSize
import io.github.tweeter042.alarmclock.core.designsystem.component.AppButtonVariant
import io.github.tweeter042.alarmclock.core.designsystem.component.ContainerBorder
import io.github.tweeter042.alarmclock.core.designsystem.component.themedContainer
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import io.github.tweeter042.alarmclock.core.designsystem.theme.IconStyle
import io.github.tweeter042.alarmclock.core.designsystem.theme.ShadowSpec
import io.github.tweeter042.alarmclock.core.designsystem.util.rememberAppHaptics
import io.github.tweeter042.alarmclock.core.ui.R
import io.github.tweeter042.alarmclock.core.ui.format.DurationStyle
import io.github.tweeter042.alarmclock.core.ui.format.rememberUiFormatters

/**
 * Samsung-style timer keypad: a "00 h 05 m 30 s" display that fills from the right as digits
 * are typed ([DurationDigits]) and a 3 × 4 keypad (1–9, 00, 0, delete; long-press delete clears).
 * Typed digits are highlighted, the rest stay dim. Screen readers hear the resulting duration
 * after every key.
 */
@Composable
fun DurationKeypad(
    digits: DurationDigits,
    onDigitsChange: (DurationDigits) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = AppTheme.spacing
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        DurationKeypadDisplay(digits)
        Spacer(Modifier.height(spacing.xl))
        val rows = listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'))
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            rows.forEach { row ->
                KeyRow {
                    row.forEach { digit -> DigitKey(digit.toString(), Modifier.weight(1f)) { onDigitsChange(digits.append(digit)) } }
                }
            }
            KeyRow {
                DigitKey("00", Modifier.weight(1f)) { onDigitsChange(digits.appendDoubleZero()) }
                DigitKey("0", Modifier.weight(1f)) { onDigitsChange(digits.append('0')) }
                DeleteKey(
                    enabled = !digits.isEmpty,
                    onDelete = { onDigitsChange(digits.backspace()) },
                    onClear = { onDigitsChange(digits.clear()) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The "00 h 05 m 30 s" readout. */
@Composable
internal fun DurationKeypadDisplay(digits: DurationDigits, modifier: Modifier = Modifier) {
    val formatters = rememberUiFormatters()
    val c = AppTheme.colors
    val digitStyle = AppTheme.typography.clock.copy(fontSize = 44.sp)
    val unitStyle = AppTheme.typography.titleMedium
    val spoken = stringResource(R.string.ui_keypad_display, formatters.duration.durationWithSeconds(digits.toMillis(), DurationStyle.LONG))
    val padded = digits.digits.padStart(DurationDigits.MAX_DIGITS, '0')
    val firstTyped = DurationDigits.MAX_DIGITS - digits.typedCount
    val units = listOf(
        stringResource(R.string.ui_unit_hours_label),
        stringResource(R.string.ui_unit_minutes_label),
        stringResource(R.string.ui_unit_seconds_label),
    )
    val text = buildAnnotatedString {
        for (group in 0 until 3) {
            for (i in group * 2 until group * 2 + 2) {
                withStyle(SpanStyle(color = if (i >= firstTyped) c.primary else c.onSurfaceVariant.copy(alpha = 0.45f))) {
                    append(formatters.time.localize(padded[i].toString()))
                }
            }
            withStyle(SpanStyle(color = c.onSurfaceVariant, fontSize = unitStyle.fontSize, fontFamily = unitStyle.fontFamily)) {
                append(" ")
                append(units[group])
                if (group < 2) append("  ")
            }
        }
    }
    Text(
        text = text,
        style = digitStyle,
        maxLines = 1,
        modifier = modifier.clearAndSetSemantics {
            contentDescription = spoken
            liveRegion = LiveRegionMode.Polite
        },
    )
}

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm), content = content)
}

@Composable
private fun DigitKey(label: String, modifier: Modifier, onClick: () -> Unit) {
    val formatters = rememberUiFormatters()
    AppButton(
        text = formatters.time.localize(label),
        onClick = onClick,
        variant = AppButtonVariant.Tonal,
        size = AppButtonSize.Large,
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeleteKey(enabled: Boolean, onDelete: () -> Unit, onClear: () -> Unit, modifier: Modifier) {
    val tokens = AppTheme.tokens
    val c = tokens.colors
    val haptics = rememberAppHaptics()
    val interaction = remember { MutableInteractionSource() }
    val deleteLabel = stringResource(R.string.ui_keypad_delete)
    val clearLabel = stringResource(R.string.ui_keypad_clear)
    val border = if (tokens.border.isVisible) ContainerBorder(tokens.border.width, tokens.border.color) else ContainerBorder(1.dp, c.outline)
    Box(
        modifier = modifier
            .height(if (tokens.largeButtons) AppButtonSize.ExtraLarge.height else AppButtonSize.Large.height)
            .themedContainer(tokens.shapes.button, null, border, ShadowSpec.None)
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = deleteLabel,
                onLongClickLabel = clearLabel,
                onLongClick = {
                    haptics.longPress()
                    onClear()
                },
                onClick = onDelete,
            )
            .semantics { contentDescription = deleteLabel },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = backspaceIcon(tokens.iconStyle),
            contentDescription = null,
            tint = if (enabled) c.onSurface else c.disabledContent,
            modifier = Modifier.size(26.dp),
        )
    }
}

private fun backspaceIcon(style: IconStyle): ImageVector = when (style) {
    IconStyle.Filled -> Icons.AutoMirrored.Filled.Backspace
    IconStyle.Rounded -> Icons.AutoMirrored.Rounded.Backspace
    IconStyle.Outlined -> Icons.AutoMirrored.Outlined.Backspace
    IconStyle.Sharp -> Icons.AutoMirrored.Sharp.Backspace
}
