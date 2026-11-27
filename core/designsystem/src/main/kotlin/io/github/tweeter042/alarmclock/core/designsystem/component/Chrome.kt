package io.github.tweeter042.alarmclock.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import io.github.tweeter042.alarmclock.core.designsystem.theme.BorderStyle
import io.github.tweeter042.alarmclock.core.designsystem.theme.PressEffect
import io.github.tweeter042.alarmclock.core.designsystem.theme.ShadowSpec

/** A resolved border for one container. */
@Immutable
data class ContainerBorder(val width: Dp, val color: Color, val dashed: Boolean = false)

/**
 * Draws a themed container behind its content, in this order: shadow (soft, hard offset or glow),
 * fill, border, then clips the content to [shape].
 *
 * [pressed] (0..1) animates the design language's press effect: Brutal/Pixel elements slide onto
 * their hard shadow, Neon glows brighter, Glass/Analog/Flip shrink slightly.
 */
fun Modifier.themedContainer(
    shape: Shape,
    fill: Brush?,
    border: ContainerBorder?,
    shadow: ShadowSpec,
    pressEffect: PressEffect = PressEffect.Ripple,
    pressed: Float = 0f,
): Modifier {
    var m: Modifier = this
    when (shadow) {
        ShadowSpec.None -> Unit
        is ShadowSpec.Soft -> m = m.dropShadow(
            shape,
            Shadow(radius = shadow.radius, color = shadow.color, offset = DpOffset(0.dp, shadow.offsetY)),
        )
        is ShadowSpec.Glow -> {
            val boost = if (pressEffect == PressEffect.Glow) 1f + pressed * 0.6f else 1f
            m = m.dropShadow(
                shape,
                Shadow(
                    radius = shadow.radius * boost,
                    color = shadow.color.copy(alpha = (shadow.color.alpha * boost).coerceAtMost(1f)),
                ),
            )
        }
        is ShadowSpec.HardOffset -> {
            // Drawn outside the translated layer so the shadow stays put while the element moves.
            m = m.drawBehind {
                translate(shadow.x.toPx(), shadow.y.toPx()) {
                    drawOutline(shape.createOutline(size, layoutDirection, this), shadow.color)
                }
            }
        }
    }
    if (pressed > 0f) {
        m = when {
            pressEffect == PressEffect.ShiftIntoShadow && shadow is ShadowSpec.HardOffset -> m.graphicsLayer {
                translationX = shadow.x.toPx() * pressed
                translationY = shadow.y.toPx() * pressed
            }
            pressEffect == PressEffect.Scale -> m.graphicsLayer {
                val s = 1f - 0.035f * pressed
                scaleX = s
                scaleY = s
            }
            else -> m
        }
    }
    if (fill != null) m = m.background(fill, shape)
    if (border != null && border.width > 0.dp && border.color.alpha > 0f) {
        m = if (border.dashed) m.dashedBorder(border.width, border.color, shape) else m.border(border.width, border.color, shape)
    }
    return m.clip(shape)
}

/** Overload taking a plain colour fill. */
fun Modifier.themedContainer(
    shape: Shape,
    fill: Color,
    border: ContainerBorder?,
    shadow: ShadowSpec,
    pressEffect: PressEffect = PressEffect.Ripple,
    pressed: Float = 0f,
): Modifier = themedContainer(
    shape = shape,
    fill = if (fill.alpha > 0f) SolidColor(fill) else null,
    border = border,
    shadow = shadow,
    pressEffect = pressEffect,
    pressed = pressed,
)

/** A dashed outline drawn inside the bounds (Terminal boxes). */
fun Modifier.dashedBorder(width: Dp, color: Color, shape: Shape): Modifier = drawWithContent {
    drawContent()
    val w = width.toPx()
    val dashes = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
    when (val outline = shape.createOutline(size, layoutDirection, this)) {
        is Outline.Rectangle -> drawRect(
            color = color,
            topLeft = Offset(w / 2f, w / 2f),
            size = Size(size.width - w, size.height - w),
            style = Stroke(width = w, pathEffect = dashes),
        )
        else -> drawOutline(outline, color, style = Stroke(width = w, pathEffect = dashes))
    }
}

/** Animated 0..1 press state for the theme's press effect (instant with reduce motion). */
@Composable
internal fun rememberPressFraction(interactionSource: InteractionSource, enabled: Boolean = true): State<Float> {
    val pressed by interactionSource.collectIsPressedAsState()
    val motion = AppTheme.motion
    return animateFloatAsState(
        targetValue = if (pressed && enabled) 1f else 0f,
        animationSpec = motion.spec(durationMillis = if (pressed) motion.shortMillis / 2 else motion.shortMillis),
        label = "press",
    )
}

/** A constant "not pressed" state for static containers. */
@Composable
internal fun notPressed(): State<Float> = remember { mutableFloatStateOf(0f) }

/** The default border for cards and outlined controls, or null when the theme has none. */
@Composable
internal fun cardBorder(): ContainerBorder? {
    val b = AppTheme.border
    return if (b.isVisible) ContainerBorder(b.width, b.color, b.style == BorderStyle.Dashed) else null
}

/** Border for filled controls (only Brutal/Pixel/Neon outline their fills). */
@Composable
internal fun filledControlBorder(color: Color = AppTheme.border.color): ContainerBorder? {
    val b = AppTheme.border
    return if (b.outlineFilledControls && b.width > 0.dp) ContainerBorder(b.width, color) else null
}

/** Draws a 1-pixel-art bevel (light top/left, dark bottom/right) inside the bounds. */
internal fun Modifier.bevel(width: Dp, light: Color, dark: Color): Modifier = drawWithContent {
    drawContent()
    val w = width.toPx()
    drawRect(light, topLeft = Offset(w, w), size = Size(size.width - 2 * w, w))
    drawRect(light, topLeft = Offset(w, w), size = Size(w, size.height - 2 * w))
    drawRect(dark, topLeft = Offset(w, size.height - 2 * w), size = Size(size.width - 2 * w, w))
    drawRect(dark, topLeft = Offset(size.width - 2 * w, w), size = Size(w, size.height - 2 * w))
}

/** A line with a real blurred halo (Neon tubes). */
internal fun DrawScope.drawGlowLine(color: Color, start: Offset, end: Offset, width: Float, glowRadius: Float) {
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            strokeWidth = width
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            setShadowLayer(glowRadius, 0f, 0f, color.copy(alpha = 0.85f).toArgb())
        }
        canvas.nativeCanvas.drawLine(start.x, start.y, end.x, end.y, paint)
    }
}

/** An arc with a real blurred halo (Neon progress ring). */
internal fun DrawScope.drawGlowArc(color: Color, startAngle: Float, sweep: Float, topLeft: Offset, arcSize: Size, width: Float, glowRadius: Float) {
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            strokeWidth = width
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            setShadowLayer(glowRadius, 0f, 0f, color.copy(alpha = 0.85f).toArgb())
        }
        canvas.nativeCanvas.drawArc(topLeft.x, topLeft.y, topLeft.x + arcSize.width, topLeft.y + arcSize.height, startAngle, sweep, false, paint)
    }
}

/** Multiplies the alpha (unlike `copy(alpha = …)`, keeps transparent colours transparent). */
internal fun Color.dim(factor: Float): Color = copy(alpha = alpha * factor)

/**
 * Solid colour of the container that content is drawn on (set by AppCard and other filled
 * containers). Clock faces and progress indicators switch to the content colour when their accent
 * would not stand out on it (e.g. an inverse-video Terminal card).
 */
val LocalContainerColor = androidx.compose.runtime.compositionLocalOf { Color.Unspecified }

/** True if [text] contains Thai characters. */
internal fun hasThai(text: String): Boolean = text.any { it in '\u0E00'..'\u0E7F' }

/**
 * Thai is never letter-spaced or slanted: tracking pulls tone marks away from their consonants
 * and Thai fonts have no italics.
 */
internal fun androidx.compose.ui.text.TextStyle.forText(text: String): androidx.compose.ui.text.TextStyle =
    if (hasThai(text)) copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified, fontStyle = androidx.compose.ui.text.font.FontStyle.Normal) else this

/** An accent that stays visible on the current container (falls back to the content colour). */
@Composable
internal fun visibleAccent(preferred: Color, minRatio: Float = 3f): Color {
    val container = LocalContainerColor.current
    if (container == Color.Unspecified || container.alpha < 1f) return preferred
    if (io.github.tweeter042.alarmclock.core.designsystem.theme.ColorContrast.ratio(preferred, container) >= minRatio) return preferred
    return androidx.compose.material3.LocalContentColor.current
}
