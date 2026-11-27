package io.github.tweeter042.alarmclock.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import io.github.tweeter042.alarmclock.core.designsystem.theme.SelectionStyle
import io.github.tweeter042.alarmclock.core.designsystem.theme.ShadowSpec
import io.github.tweeter042.alarmclock.core.designsystem.util.rememberAppHaptics
import kotlin.math.roundToInt

/**
 * A themed slider with an optional [label] and live value text ([valueLabel]). LCD, Pixel and
 * Terminal draw the track as discrete blocks; Brutal as a bordered bar; Neon as a glowing line.
 * Steps produce a haptic tick.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    label: String? = null,
    valueLabel: ((Float) -> String)? = null,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val tokens = AppTheme.tokens
    val c = tokens.colors
    val haptics = rememberAppHaptics()
    val lastStep = remember { mutableIntStateOf(Int.MIN_VALUE) }
    val interaction = remember { MutableInteractionSource() }
    val dragged by interaction.collectIsDraggedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val active = dragged || pressed

    Column(modifier) {
        if (label != null || valueLabel != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (label != null) {
                    Text(
                        text = label,
                        style = tokens.typography.titleMedium,
                        color = if (enabled) c.onSurface else c.disabledContent,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (valueLabel != null) {
                    Text(
                        text = valueLabel(value),
                        style = tokens.typography.labelLarge,
                        color = if (enabled) c.primary else c.disabledContent,
                        maxLines = 1,
                    )
                }
            }
        }
        val state = remember(steps, valueRange) { SliderState(value, steps, valueRange) }
        // The caller owns the value; keep the slider state in sync with it.
        if (state.value != value) state.value = value
        Slider(
            state = state,
            onValueChange = { v: Float ->
                if (steps > 0) {
                    val span = valueRange.endInclusive - valueRange.start
                    val index = if (span == 0f) 0 else (((v - valueRange.start) / span) * (steps + 1)).roundToInt()
                    if (lastStep.intValue != Int.MIN_VALUE && index != lastStep.intValue) haptics.tick()
                    lastStep.intValue = index
                }
                onValueChange(v)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            onValueChangeFinished = onValueChangeFinished,
            interactionSource = interaction,
            thumb = { _: SliderState -> SliderThumb(enabled, active) },
            track = { s: SliderState -> SliderTrack(s, enabled) },
        )
    }
}

@Composable
private fun SliderThumb(enabled: Boolean, active: Boolean) {
    val tokens = AppTheme.tokens
    val c = tokens.colors
    val color = if (enabled) c.primary else c.disabledContent
    when (tokens.traits.selectionStyle) {
        SelectionStyle.Text -> Box(Modifier.size(12.dp, 26.dp).themedContainer(RectangleShape, color, null, ShadowSpec.None))
        SelectionStyle.Pixel -> Box(
            Modifier
                .size(18.dp, 26.dp)
                .themedContainer(RectangleShape, SolidColor(color), ContainerBorder(3.dp, tokens.border.color), if (enabled) tokens.smallShadow else ShadowSpec.None)
                .bevel(3.dp, Color.White.copy(alpha = 0.3f), Color.Black.copy(alpha = 0.25f)),
        )
        SelectionStyle.Square -> Box(
            Modifier
                .size(if (tokens.border.outlineFilledControls) 24.dp else 16.dp, 28.dp)
                .themedContainer(
                    tokens.shapes.thumb,
                    SolidColor(color),
                    ContainerBorder(maxOf(tokens.border.width, 2.dp), if (tokens.border.outlineFilledControls) tokens.border.color else c.surface),
                    if (enabled) tokens.smallShadow else ShadowSpec.None,
                ),
        )
        SelectionStyle.Rounded -> {
            val glow = tokens.shadow is ShadowSpec.Glow
            val size = if (active) 26.dp else 22.dp
            Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(size)
                        .themedContainer(
                            CircleShape,
                            SolidColor(if (tokens.traits.outlinedPrimary) c.background else color),
                            ContainerBorder(if (tokens.traits.outlinedPrimary) 2.dp else 3.dp, if (tokens.traits.outlinedPrimary) color else c.surface),
                            when {
                                glow && enabled -> ShadowSpec.Glow(12.dp, c.glow.copy(alpha = 0.6f))
                                tokens.shadow is ShadowSpec.Soft && enabled -> ShadowSpec.Soft(4.dp, 1.dp, Color.Black.copy(alpha = 0.25f))
                                else -> ShadowSpec.None
                            },
                        ),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SliderTrack(state: SliderState, enabled: Boolean) {
    val tokens = AppTheme.tokens
    val c = tokens.colors
    val fraction = state.coercedValueAsFraction
    val active = if (enabled) c.primary else c.disabledContent
    val inactive = if (enabled) c.outline.copy(alpha = if (c.isDark) 0.55f else 0.35f) else c.disabledContainer
    when {
        tokens.traits.segmentedProgress -> {
            val segments = if (state.steps > 0) state.steps + 1 else 20
            Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                val gap = 3.dp.toPx()
                val w = (size.width - gap * (segments - 1)) / segments
                val lit = (fraction * segments).roundToInt()
                for (i in 0 until segments) {
                    drawRect(
                        color = if (i < lit) active else c.clockGhost.copy(alpha = 0.6f).let { if (it.alpha < 0.08f) inactive else it },
                        topLeft = Offset(i * (w + gap), 0f),
                        size = Size(w, size.height),
                    )
                }
            }
        }
        tokens.traits.selectionStyle == SelectionStyle.Square -> {
            val line = if (tokens.border.outlineFilledControls) tokens.border.color else c.outline
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .themedContainer(tokens.shapes.track, SolidColor(c.surface), ContainerBorder(maxOf(tokens.border.width, 2.dp), line), ShadowSpec.None),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(14.dp)
                        .themedContainer(RectangleShape, SolidColor(active), null, ShadowSpec.None),
                )
            }
        }
        else -> {
            val glow = tokens.shadow is ShadowSpec.Glow
            val thin = tokens.typography.clock.fontWeight?.weight?.let { it <= 300 } == true
            Canvas(Modifier.fillMaxWidth().height(16.dp)) {
                val h = (if (thin) 3.dp else 5.dp).toPx()
                val y = size.height / 2f
                drawRoundRect(inactive, Offset(0f, y - h / 2), Size(size.width, h), CornerRadius(h / 2))
                if (glow && fraction > 0f) {
                    drawGlowLine(active, Offset(h / 2, y), Offset(size.width * fraction - h / 2, y), h * 0.8f, 9.dp.toPx())
                } else {
                    drawRoundRect(active, Offset(0f, y - h / 2), Size(size.width * fraction, h), CornerRadius(h / 2))
                }
            }
        }
    }
}
