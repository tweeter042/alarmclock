package io.github.tweeter042.alarmclock.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import io.github.tweeter042.alarmclock.core.designsystem.theme.CardFill
import io.github.tweeter042.alarmclock.core.designsystem.theme.HeaderDecor
import io.github.tweeter042.alarmclock.core.designsystem.theme.ShadowSpec

/**
 * A text field with the label above the box (One UI style). Paper draws only an underline like a
 * form on paper; Terminal a dashed prompt line with "> "; Brutal a thick black box.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    errorText: String? = null,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 6,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val tokens = AppTheme.tokens
    val c = tokens.colors
    val traits = tokens.traits
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val isError = errorText != null
    val accent = when {
        isError -> c.error
        focused -> c.primary
        else -> if (tokens.border.outlineFilledControls) tokens.border.color else c.outline
    }
    val lineColor by animateColorAsState(accent, tokens.motion.fade(), label = "fieldLine")
    val underlineOnly = traits.headerDecor == HeaderDecor.Masthead || traits.bracketedLabels
    val textStyle = tokens.typography.bodyLarge.copy(color = if (enabled) c.onSurface else c.disabledContent)

    Column(modifier) {
        if (label != null) {
            Text(
                text = label,
                style = tokens.typography.labelLarge,
                color = if (isError) c.error else if (focused) c.primary else c.onSurfaceVariant,
                modifier = Modifier.padding(start = if (underlineOnly) 0.dp else 4.dp, bottom = 6.dp),
            )
        }
        val box: Modifier = if (underlineOnly) {
            Modifier.drawBehind {
                val h = (if (focused || isError) 2.dp else 1.dp).toPx()
                if (traits.bracketedLabels) {
                    drawLine(
                        lineColor, Offset(0f, size.height - h / 2), Offset(size.width, size.height - h / 2), h,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                    )
                } else {
                    drawRect(lineColor, topLeft = Offset(0f, size.height - h), size = Size(size.width, h))
                }
            }
        } else {
            val width = when {
                tokens.border.outlineFilledControls -> tokens.border.width
                focused || isError -> 2.dp
                else -> 1.dp
            }
            Modifier.themedContainer(
                shape = tokens.shapes.field,
                fill = SolidColor(
                    when {
                        traits.cardFill == CardFill.Frosted -> c.card
                        tokens.border.width == 0.dp -> c.surfaceVariant
                        tokens.border.outlineFilledControls -> c.surface
                        else -> c.surface.copy(alpha = if (traits.cardFill == CardFill.Outline) 0f else 1f)
                    },
                ),
                border = ContainerBorder(width, if (tokens.border.width == 0.dp && !focused && !isError) Color.Transparent else lineColor),
                shadow = if (tokens.border.outlineFilledControls && enabled) tokens.smallShadow else ShadowSpec.None,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { if (errorText != null) error(errorText) },
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            maxLines = maxLines,
            visualTransformation = visualTransformation,
            interactionSource = interaction,
            cursorBrush = SolidColor(c.primary),
            decorationBox = { inner ->
                Row(
                    modifier = box
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .padding(horizontal = if (underlineOnly) 0.dp else 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (traits.bracketedLabels) {
                        Text("> ", style = textStyle, color = c.primary)
                    }
                    if (leadingIcon != null) {
                        Icon(leadingIcon, null, tint = c.onSurfaceVariant, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                    }
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(placeholder, style = textStyle, color = c.onSurfaceVariant.copy(alpha = 0.8f), maxLines = 1)
                        }
                        inner()
                    }
                    if (trailing != null) {
                        Spacer(Modifier.width(8.dp))
                        trailing()
                    }
                }
            },
        )
        val helper = errorText ?: supportingText
        if (helper != null) {
            Text(
                text = helper,
                style = tokens.typography.bodySmall,
                color = if (isError) c.error else c.onSurfaceVariant,
                modifier = Modifier.padding(start = if (underlineOnly) 0.dp else 4.dp, top = 4.dp),
            )
        }
    }
}
