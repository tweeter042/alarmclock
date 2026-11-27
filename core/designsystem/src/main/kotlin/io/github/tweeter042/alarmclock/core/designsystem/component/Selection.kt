package io.github.tweeter042.alarmclock.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tweeter042.alarmclock.core.designsystem.R
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import io.github.tweeter042.alarmclock.core.designsystem.theme.CardFill
import io.github.tweeter042.alarmclock.core.designsystem.theme.SelectionStyle
import io.github.tweeter042.alarmclock.core.designsystem.theme.ShadowSpec
import io.github.tweeter042.alarmclock.core.designsystem.util.rememberAppHaptics

/**
 * On/off switch. Pass [onCheckedChange] = null when a parent row handles the toggle
 * (see SettingsSwitchRow) so TalkBack announces a single control.
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptics = rememberAppHaptics()
    val interaction = remember { MutableInteractionSource() }
    val toggle = if (onCheckedChange != null) {
        Modifier
            .minimumInteractiveComponentSize()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interaction,
                indication = ripple(bounded = false, radius = 28.dp),
                onValueChange = {
                    haptics.toggle(it)
                    onCheckedChange(it)
                },
            )
    } else {
        Modifier
    }
    Box(modifier.then(toggle), contentAlignment = Alignment.Center) {
        SwitchVisual(checked = checked, enabled = enabled)
    }
}

@Composable
private fun SwitchVisual(checked: Boolean, enabled: Boolean) {
    val tokens = AppTheme.tokens
    val c = tokens.colors
    val motion = tokens.motion
    val alpha = if (enabled) 1f else 0.4f
    when (tokens.traits.selectionStyle) {
        SelectionStyle.Text -> {
            val label = stringResource(if (checked) R.string.ds_switch_on else R.string.ds_switch_off)
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .themedContainer(
                        shape = RectangleShape,
                        fill = if (checked) c.primary.dim(alpha) else Color.Transparent,
                        border = if (checked) null else ContainerBorder(1.dp, c.outline, dashed = true),
                        shadow = ShadowSpec.None,
                    )
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (checked) label else "[$label]",
                    style = tokens.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = (if (checked) c.onPrimary else c.onSurfaceVariant).dim(alpha),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
        SelectionStyle.Square, SelectionStyle.Pixel -> {
            val pixel = tokens.traits.selectionStyle == SelectionStyle.Pixel
            val trackW = if (pixel) 52.dp else 56.dp
            val trackH = if (pixel) 28.dp else 30.dp
            val inset = if (pixel) 6.dp else 4.dp
            val thumb = trackH - inset * 2
            val travel = trackW - thumb - inset * 2
            val x by animateDpAsState(if (checked) travel else 0.dp, motion.spec(), label = "thumb")
            val lineWidth = if (tokens.border.width > 0.dp) tokens.border.width else 2.dp
            val line = if (tokens.border.outlineFilledControls) tokens.border.color else c.outline
            val trackFill = if (checked) c.primary else c.surface
            Box(
                modifier = Modifier
                    .size(trackW, trackH)
                    .themedContainer(
                        shape = tokens.shapes.track,
                        fill = trackFill.dim(alpha),
                        border = ContainerBorder(lineWidth, line.dim(alpha)),
                        shadow = ShadowSpec.None,
                    ),
            ) {
                Box(
                    Modifier
                        .padding(inset)
                        .offset(x = x)
                        .size(thumb)
                        .themedContainer(
                            shape = if (pixel) RectangleShape else tokens.shapes.thumb,
                            fill = (if (checked) c.onPrimary else line).dim(alpha),
                            border = if (checked) ContainerBorder(if (pixel) 0.dp else 2.dp, line) else null,
                            shadow = ShadowSpec.None,
                        ),
                )
            }
        }
        SelectionStyle.Rounded -> {
            val outlined = tokens.traits.outlinedPrimary
            val trackW = 52.dp
            val trackH = 32.dp
            val thumbSize by animateDpAsState(if (checked) 24.dp else 16.dp, motion.spec(), label = "thumbSize")
            // Thumb centre travels from 16 dp to 36 dp (Material switch geometry).
            val center by animateDpAsState(if (checked) trackW - 16.dp else 16.dp, motion.spec(), label = "thumbX")
            val trackFill by animateColorAsState(
                when {
                    outlined -> c.background
                    checked -> c.primary
                    tokens.traits.cardFill == CardFill.Frosted -> c.card
                    else -> c.surfaceVariant
                }.dim(alpha),
                motion.fade(),
                label = "track",
            )
            val thumbColor by animateColorAsState(
                when {
                    outlined && checked -> c.primary
                    checked -> c.onPrimary
                    else -> c.outline
                }.dim(alpha),
                motion.fade(),
                label = "thumbColor",
            )
            val borderColor = (if (checked) c.primary else c.outline).dim(alpha)
            val glow = tokens.shadow is ShadowSpec.Glow && checked && enabled
            Box(
                modifier = Modifier
                    .size(trackW, trackH)
                    .themedContainer(
                        shape = CircleShape,
                        fill = trackFill,
                        border = ContainerBorder(2.dp, borderColor),
                        shadow = if (glow) ShadowSpec.Glow(10.dp, c.glow.copy(alpha = 0.5f)) else ShadowSpec.None,
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    Modifier
                        .offset(x = center - thumbSize / 2)
                        .size(thumbSize)
                        .themedContainer(CircleShape, thumbColor, null, ShadowSpec.None),
                )
            }
        }
    }
}

/** A checkbox. Use [onCheckedChange] = null when the surrounding row is toggleable. */
@Composable
fun AppCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tokens = AppTheme.tokens
    val c = tokens.colors
    val haptics = rememberAppHaptics()
    val interaction = remember { MutableInteractionSource() }
    val alpha = if (enabled) 1f else 0.4f
    val toggle = if (onCheckedChange != null) {
        Modifier
            .minimumInteractiveComponentSize()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                interactionSource = interaction,
                indication = ripple(bounded = false, radius = 22.dp),
                onValueChange = {
                    haptics.toggle(it)
                    onCheckedChange(it)
                },
            )
    } else {
        Modifier
    }
    Box(modifier.then(toggle), contentAlignment = Alignment.Center) {
        if (tokens.traits.selectionStyle == SelectionStyle.Text) {
            Text(
                text = if (checked) "[x]" else "[ ]",
                style = tokens.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp),
                color = (if (checked) c.primary else c.onSurfaceVariant).dim(alpha),
            )
            return@Box
        }
        val progress by animateFloatAsState(if (checked) 1f else 0f, tokens.motion.spec(), label = "check")
        val lineWidth: Dp = when (tokens.traits.selectionStyle) {
            SelectionStyle.Square, SelectionStyle.Pixel -> maxOf(tokens.border.width, 2.dp)
            else -> 2.dp
        }
        val line = if (tokens.border.outlineFilledControls) tokens.border.color else c.outline
        Box(
            modifier = Modifier
                .size(22.dp)
                .themedContainer(
                    shape = tokens.shapes.checkbox,
                    fill = (if (checked && !tokens.traits.outlinedPrimary) c.primary else Color.Transparent).dim(alpha),
                    border = ContainerBorder(
                        lineWidth,
                        (if (checked && !tokens.border.outlineFilledControls) c.primary else line).dim(alpha),
                    ),
                    shadow = ShadowSpec.None,
                ),
        ) {
            val markColor = (if (tokens.traits.outlinedPrimary) c.primary else c.onPrimary).dim(alpha)
            val pixel = tokens.traits.selectionStyle == SelectionStyle.Pixel
            Canvas(Modifier.size(22.dp)) {
                if (progress <= 0f) return@Canvas
                val w = size.width
                val path = Path().apply {
                    moveTo(w * 0.24f, w * 0.52f)
                    lineTo(w * 0.43f, w * 0.70f)
                    lineTo(w * 0.77f, w * 0.32f)
                }
                val measure = PathMeasure().apply { setPath(path, false) }
                val partial = Path()
                measure.getSegment(0f, measure.length * progress, partial, true)
                drawPath(
                    partial,
                    markColor,
                    style = Stroke(
                        width = w * 0.13f,
                        cap = if (pixel) StrokeCap.Square else StrokeCap.Round,
                        join = if (pixel) StrokeJoin.Miter else StrokeJoin.Round,
                    ),
                )
            }
        }
    }
}

/** A radio button. Use [onClick] = null when the surrounding row is selectable. */
@Composable
fun AppRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tokens = AppTheme.tokens
    val c = tokens.colors
    val haptics = rememberAppHaptics()
    val interaction = remember { MutableInteractionSource() }
    val alpha = if (enabled) 1f else 0.4f
    val select = if (onClick != null) {
        Modifier
            .minimumInteractiveComponentSize()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                interactionSource = interaction,
                indication = ripple(bounded = false, radius = 22.dp),
                onClick = {
                    haptics.tick()
                    onClick()
                },
            )
    } else {
        Modifier
    }
    Box(modifier.then(select), contentAlignment = Alignment.Center) {
        val style = tokens.traits.selectionStyle
        if (style == SelectionStyle.Text) {
            Text(
                text = if (selected) "(*)" else "( )",
                style = tokens.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp),
                color = (if (selected) c.primary else c.onSurfaceVariant).dim(alpha),
            )
            return@Box
        }
        val dot by animateFloatAsState(if (selected) 1f else 0f, tokens.motion.spec(), label = "dot")
        val ringShape = if (style == SelectionStyle.Rounded) CircleShape else tokens.shapes.checkbox
        val ringColor = (if (selected) c.primary else c.outline).dim(alpha)
        Box(
            modifier = Modifier
                .size(22.dp)
                .themedContainer(
                    ringShape,
                    null,
                    ContainerBorder(if (style == SelectionStyle.Rounded) 2.dp else maxOf(tokens.border.width, 2.dp), ringColor),
                    ShadowSpec.None,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (dot > 0f) {
                Box(
                    Modifier
                        .size(10.dp)
                        .scale(dot)
                        .themedContainer(
                            if (style == SelectionStyle.Rounded) CircleShape else RectangleShape,
                            SolidColor(c.primary.dim(alpha)),
                            null,
                            ShadowSpec.None,
                        ),
                )
            }
        }
    }
}
