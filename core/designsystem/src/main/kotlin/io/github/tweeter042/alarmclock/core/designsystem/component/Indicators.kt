package io.github.tweeter042.alarmclock.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tweeter042.alarmclock.core.designsystem.R
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import io.github.tweeter042.alarmclock.core.designsystem.theme.DividerStyle
import io.github.tweeter042.alarmclock.core.designsystem.theme.SelectionStyle
import io.github.tweeter042.alarmclock.core.designsystem.theme.ShadowSpec
import kotlin.math.roundToInt

/** A themed horizontal rule: hairline, thick, dashed, double (newspaper), glowing or dotted. */
@Composable
fun AppDivider(modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val tokens = AppTheme.tokens
    val c = tokens.colors
    val ink = if (color != Color.Unspecified) color else when (tokens.traits.dividerStyle) {
        DividerStyle.Thick -> tokens.border.color
        DividerStyle.Double -> c.onBackground.copy(alpha = 0.8f)
        DividerStyle.Glow -> c.glow
        else -> c.outlineVariant
    }
    val height = when (tokens.traits.dividerStyle) {
        DividerStyle.Thick -> tokens.border.width
        DividerStyle.Double -> 4.dp
        DividerStyle.Glow -> 6.dp
        DividerStyle.Dotted -> 3.dp
        else -> 1.dp
    }
    Canvas(modifier.fillMaxWidth().height(height)) {
        val y = size.height / 2f
        when (tokens.traits.dividerStyle) {
            DividerStyle.Hairline -> drawLine(ink, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            DividerStyle.Thick -> drawRect(ink)
            DividerStyle.Dashed -> drawLine(
                ink, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
            )
            DividerStyle.Double -> {
                drawRect(ink, size = Size(size.width, 1.5.dp.toPx()))
                drawRect(ink, topLeft = Offset(0f, size.height - 0.75.dp.toPx()), size = Size(size.width, 0.75.dp.toPx()))
            }
            DividerStyle.Glow -> drawGlowLine(ink, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), 5.dp.toPx())
            DividerStyle.Dotted -> {
                val p = size.height
                var x = 0f
                while (x < size.width) {
                    drawRect(ink, topLeft = Offset(x, 0f), size = Size(p, p))
                    x += p * 2.5f
                }
            }
        }
    }
}

/** A count badge (e.g. unread items). [count] > 99 shows "99+". With no count it is a dot. */
@Composable
fun AppBadge(modifier: Modifier = Modifier, count: Int? = null, text: String? = null) {
    val tokens = AppTheme.tokens
    val c = tokens.colors
    val label = when {
        text != null -> text
        count == null -> null
        count > 99 -> stringResource(R.string.ds_badge_overflow, 99)
        else -> count.toString()
    }
    if (label == null) {
        Box(modifier.size(8.dp).themedContainer(tokens.shapes.badge, SolidColor(c.error), null, ShadowSpec.None))
        return
    }
    Box(
        modifier = modifier
            .heightIn(min = 18.dp)
            .widthIn(min = 18.dp)
            .themedContainer(tokens.shapes.badge, SolidColor(c.error), filledControlBorder()?.copy(width = 1.5.dp), ShadowSpec.None)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = tokens.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp), color = c.onError, maxLines = 1)
    }
}

/**
 * Linear progress. [progress] null = indeterminate. LCD/Pixel/Terminal draw discrete blocks,
 * Brutal a bordered bar, Neon a glowing line.
 */
@Composable
fun AppLinearProgress(progress: Float?, modifier: Modifier = Modifier) {
    val tokens = AppTheme.tokens
    val c = tokens.colors
    val motion = tokens.motion
    val loading = stringResource(R.string.ds_loading)
    val activeColor = visibleAccent(c.primary)
    val indeterminate = if (progress == null && motion.ambientEnabled) {
        val transition = rememberInfiniteTransition(label = "indeterminate")
        transition.animateFloat(
            0f, 1f,
            infiniteRepeatable(tween(1400, easing = motion.effectiveEasing.takeIf { motion.steps > 0 } ?: LinearEasing), RepeatMode.Restart),
            label = "sweep",
        )
    } else {
        remember { mutableFloatStateOf(0.3f) }
    }
    val sweep by indeterminate
    val square = tokens.traits.selectionStyle == SelectionStyle.Square
    val height = when {
        tokens.traits.segmentedProgress -> 12.dp
        square -> 14.dp
        else -> 6.dp
    }
    val semantics = Modifier.semantics {
        if (progress != null) progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
        else contentDescription = loading
    }
    val container = if (square) {
        Modifier.themedContainer(
            tokens.shapes.track,
            SolidColor(c.surface),
            ContainerBorder(maxOf(tokens.border.width, 2.dp), if (tokens.border.outlineFilledControls) tokens.border.color else c.outline),
            ShadowSpec.None,
        )
    } else {
        Modifier
    }
    Canvas(modifier.fillMaxWidth().height(height).then(semantics).then(container)) {
        val start = if (progress == null) (sweep * 1.3f - 0.3f).coerceIn(0f, 1f) else 0f
        val end = if (progress == null) (sweep * 1.3f).coerceIn(0f, 1f) else progress.coerceIn(0f, 1f)
        drawProgressTrack(start, end, tokens.traits.segmentedProgress, square, activeColor, activeColor.copy(alpha = 0.22f).takeIf { activeColor != c.primary } ?: c.outline.copy(alpha = 0.3f), c.clockGhost, tokens.shadow is ShadowSpec.Glow)
    }
}

private fun DrawScope.drawProgressTrack(
    start: Float,
    end: Float,
    segmented: Boolean,
    square: Boolean,
    active: Color,
    track: Color,
    ghost: Color,
    glow: Boolean,
) {
    when {
        segmented -> {
            val gap = 3.dp.toPx()
            val n = ((size.width + gap) / (size.height + gap)).toInt().coerceAtLeast(4)
            val w = (size.width - gap * (n - 1)) / n
            for (i in 0 until n) {
                val center = (i + 0.5f) / n
                val lit = center in start..end
                drawRect(
                    color = if (lit) active else if (ghost.alpha >= 0.06f) ghost else track,
                    topLeft = Offset(i * (w + gap), 0f),
                    size = Size(w, size.height),
                )
            }
        }
        square -> {
            val inset = 4.dp.toPx()
            drawRect(active, topLeft = Offset(size.width * start + inset, inset), size = Size(((end - start) * size.width - 2 * inset).coerceAtLeast(0f), size.height - 2 * inset))
        }
        else -> {
            val h = size.height
            drawRoundRect(track, cornerRadius = CornerRadius(h / 2))
            if (glow && end > start) {
                drawGlowLine(active, Offset(size.width * start + h / 2, h / 2), Offset(size.width * end - h / 2, h / 2), h * 0.7f, 8.dp.toPx())
            } else {
                drawRoundRect(active, topLeft = Offset(size.width * start, 0f), size = Size((end - start) * size.width, h), cornerRadius = CornerRadius(h / 2))
            }
        }
    }
}

/**
 * Circular progress (timers, mission progress) with optional centre [content].
 * Pixel and LCD themes draw a ring of blocks; Neon glows.
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    strokeWidth: Dp = 10.dp,
    color: Color = Color.Unspecified,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val tokens = AppTheme.tokens
    val c = tokens.colors
    val active = if (color != Color.Unspecified) color else visibleAccent(c.primary)
    val p = progress.coerceIn(0f, 1f)
    val percent = stringResource(R.string.ds_progress_percent, (p * 100).roundToInt())
    Box(
        modifier = modifier
            .size(size)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(p, 0f..1f)
                contentDescription = percent
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val sw = strokeWidth.toPx()
            val inset = sw / 2 + if (tokens.shadow is ShadowSpec.Glow) sw else 0f
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            if (tokens.traits.segmentedProgress) {
                val segments = 60
                val lit = (p * segments).roundToInt()
                val r = arcSize.width / 2
                val center = Offset(this.size.width / 2, this.size.height / 2)
                for (i in 0 until segments) {
                    val angle = Math.toRadians((i * 360.0 / segments) - 90.0)
                    val dir = Offset(kotlin.math.cos(angle).toFloat(), kotlin.math.sin(angle).toFloat())
                    drawLine(
                        if (i < lit) active else c.clockGhost.copy(alpha = maxOf(c.clockGhost.alpha, 0.12f)),
                        center + dir * (r - sw / 2),
                        center + dir * (r + sw / 2),
                        strokeWidth = sw * 0.55f,
                        cap = StrokeCap.Butt,
                    )
                }
            } else {
                val square = tokens.traits.selectionStyle != SelectionStyle.Rounded
                drawArc(if (active == c.primary) c.outline.copy(alpha = 0.3f) else active.copy(alpha = 0.22f), 0f, 360f, false, topLeft, arcSize, style = Stroke(sw))
                if (tokens.shadow is ShadowSpec.Glow) {
                    drawGlowArc(active, -90f, 360f * p, topLeft, arcSize, sw, sw * 1.6f)
                } else {
                    drawArc(active, -90f, 360f * p, false, topLeft, arcSize, style = Stroke(sw, cap = if (square) StrokeCap.Butt else StrokeCap.Round))
                }
            }
        }
        content()
    }
}
