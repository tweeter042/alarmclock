package io.github.tweeter042.alarmclock.core.designsystem.clock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppColors
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import io.github.tweeter042.alarmclock.core.designsystem.theme.mix
import io.github.tweeter042.alarmclock.core.model.format.LocalizedFormat
import io.github.tweeter042.alarmclock.core.model.settings.NumeralStyle
import io.github.tweeter042.alarmclock.core.model.theme.ThemeId
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Dial designs; each Analog palette has its own (Classic numerals, Navy batons, …). */
enum class DialStyle { Numerals, Batons, Roman, Minimal }

/** Dial design for a palette id of the Analog theme (other themes use [DialStyle.Numerals]). */
fun dialStyleFor(paletteId: String): DialStyle = when (paletteId) {
    "navy" -> DialStyle.Batons
    "forest" -> DialStyle.Roman
    "rose_gold" -> DialStyle.Minimal
    else -> DialStyle.Numerals
}

private val ROMAN = arrayOf("XII", "I", "II", "III", "IIII", "V", "VI", "VII", "VIII", "IX", "X", "XI")

/**
 * A classic watch dial: minute ticks, hour indices or numerals, tapered hour/minute hands and a
 * seconds hand with counterweight (accent colour). The seconds hand ticks with a small spring
 * overshoot; reduce motion makes it jump. Compact sizes show a mini dial next to the digits.
 */
@Composable
internal fun AnalogClockFace(
    time: LocalTime,
    showSeconds: Boolean,
    numerals: NumeralStyle,
    size: ClockSize,
    amPm: String?,
    modifier: Modifier,
) {
    val tokens = AppTheme.tokens
    val c = tokens.colors
    val dial = if (tokens.themeId == ThemeId.ANALOG) dialStyleFor(tokens.paletteId) else DialStyle.Numerals
    val measurer = rememberTextMeasurer()

    // Unwrapped second-hand angle so 59 -> 0 keeps turning forward.
    val secondsAngle = remember { Animatable(time.second * 6f) }
    LaunchedEffect(time.second, time.minute, time.hour) {
        val target = time.second * 6f
        val current = secondsAngle.value % 360f
        var delta = target - current
        if (delta < -180f) delta += 360f
        if (delta > 180f) delta -= 360f
        if (tokens.motion.reduceMotion || !showSeconds) {
            secondsAngle.snapTo(secondsAngle.value + delta)
        } else {
            secondsAngle.animateTo(secondsAngle.value + delta, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
        }
    }

    if (!size.fitWidth) {
        // List rows: a mini dial plus the time in the clock font.
        val reading = ClockFormat.reading(time, use24h = amPm == null, showSeconds = false)
        val text = ClockFormat.localizeDigits(reading.text, numerals)
        val px = with(LocalDensity.current) { size.fontSizeDp.dp.toSp() }
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(size.digitHeight * 1.25f)) {
                drawDial(time, secondsAngle.value, false, c, DialStyle.Minimal, null, measurer, tokens.typography.clock, numerals, mini = true)
            }
            Spacer(Modifier.width(size.digitHeight * 0.3f))
            ClockWithLabel(
                digits = text,
                amPm = amPm,
                size = size,
                fontSize = px,
                style = tokens.typography.clock,
                labelStyle = tokens.typography.clockLabel,
                color = clockDigitColor(),
                above = false,
            )
        }
        return
    }

    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val maxDial = when (size) {
            ClockSize.Hero -> 340.dp
            ClockSize.Large -> 240.dp
            else -> 160.dp
        }
        Canvas(
            Modifier
                .widthIn(max = maxDial)
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            drawDial(time, secondsAngle.value, showSeconds, c, dial, amPm, measurer, tokens.typography.clock, numerals, mini = false)
        }
    }
}

private fun DrawScope.drawDial(
    time: LocalTime,
    secondsAngle: Float,
    showSeconds: Boolean,
    c: AppColors,
    style: DialStyle,
    amPm: String?,
    measurer: TextMeasurer,
    clockStyle: TextStyle,
    numerals: NumeralStyle,
    mini: Boolean,
) {
    val r = min(size.width, size.height) / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    val ink = c.clockDigit
    val ticks = c.clockGhost
    val face = c.surface.copy(alpha = 1f)

    // Case: bezel ring with a soft gradient, then the dial.
    drawCircle(
        Brush.radialGradient(listOf(face.mix(Color.White, if (c.isDark) 0.04f else 0.5f), face), center = center - Offset(r * 0.3f, r * 0.3f), radius = r * 1.4f),
        radius = r * 0.97f,
        center = center,
    )
    drawCircle(c.outline.copy(alpha = 0.55f), radius = r * 0.97f, center = center, style = Stroke(width = r * if (mini) 0.06f else 0.022f))
    if (!mini) drawCircle(ink.copy(alpha = 0.08f), radius = r * 0.9f, center = center, style = Stroke(width = r * 0.006f))

    // Minute track.
    if (!mini) {
        for (i in 0 until 60) {
            val major = i % 5 == 0
            if (major && style == DialStyle.Batons) continue
            val angle = Math.toRadians(i * 6.0 - 90.0)
            val outer = r * 0.9f
            val inner = if (major) r * 0.8f else r * 0.86f
            val dir = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            drawLine(
                if (major) ink else ticks.copy(alpha = ticks.alpha.coerceAtLeast(0.25f)),
                center + dir * inner,
                center + dir * outer,
                strokeWidth = if (major) r * 0.022f else r * 0.008f,
                cap = StrokeCap.Butt,
            )
        }
    } else {
        for (i in 0 until 12 step 3) {
            val angle = Math.toRadians(i * 30.0 - 90.0)
            val dir = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            drawLine(ink.copy(alpha = 0.6f), center + dir * (r * 0.62f), center + dir * (r * 0.8f), strokeWidth = r * 0.08f)
        }
    }

    // Hour indices / numerals.
    if (!mini) {
        val numeralStyle = clockStyle.copy(fontSize = (r * 0.17f).toSp(), color = ink)
        for (hour in 0 until 12) {
            val angle = Math.toRadians(hour * 30.0 - 90.0)
            val dir = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            when (style) {
                DialStyle.Numerals -> {
                    val label = LocalizedFormat.digits((if (hour == 0) 12 else hour).toString(), numerals)
                    drawCentered(measurer.measure(label, numeralStyle), center + dir * (r * 0.66f))
                }
                DialStyle.Roman -> {
                    val layout = measurer.measure(ROMAN[hour], numeralStyle.copy(fontSize = numeralStyle.fontSize * 0.8f))
                    drawCentered(layout, center + dir * (r * 0.66f))
                }
                DialStyle.Batons -> {
                    val wide = hour % 3 == 0
                    drawLine(
                        ink,
                        center + dir * (r * (if (wide) 0.66f else 0.7f)),
                        center + dir * (r * 0.88f),
                        strokeWidth = r * (if (wide) 0.06f else 0.035f),
                        cap = StrokeCap.Butt,
                    )
                    // Luminous dot at the tip.
                    drawCircle(c.accent.copy(alpha = 0.9f), radius = r * 0.018f, center = center + dir * (r * 0.92f))
                }
                DialStyle.Minimal -> {
                    if (hour == 0) {
                        drawCentered(measurer.measure(LocalizedFormat.digits("12", numerals), numeralStyle), center + dir * (r * 0.68f))
                    } else {
                        drawCircle(ink, radius = r * (if (hour % 3 == 0) 0.03f else 0.018f), center = center + dir * (r * 0.78f))
                    }
                }
            }
        }
        if (amPm != null) {
            val label = measurer.measure(amPm, clockStyle.copy(fontSize = numeralStyle.fontSize * 0.55f, color = ink.copy(alpha = 0.7f)))
            drawCentered(label, center - Offset(0f, r * 0.36f))
        }
    }

    // Hands (with a soft shadow for depth).
    val hourAngle = ((time.hour % 12) + time.minute / 60f + time.second / 3600f) * 30f
    val minuteAngle = (time.minute + time.second / 60f) * 6f
    val shadow = Color.Black.copy(alpha = if (c.isDark) 0.45f else 0.18f)
    val shadowOffset = Offset(r * 0.012f, r * 0.022f)
    translate(shadowOffset.x, shadowOffset.y) {
        drawHand(center, hourAngle, r * 0.5f, r * (if (mini) 0.1f else 0.055f), shadow)
        drawHand(center, minuteAngle, r * 0.78f, r * (if (mini) 0.07f else 0.04f), shadow)
    }
    drawHand(center, hourAngle, r * 0.5f, r * (if (mini) 0.1f else 0.055f), ink)
    drawHand(center, minuteAngle, r * 0.78f, r * (if (mini) 0.07f else 0.04f), ink)
    if (showSeconds && !mini) {
        rotate(secondsAngle, pivot = center) {
            drawLine(shadow, center + Offset(shadowOffset.x, r * 0.2f + shadowOffset.y), center + Offset(shadowOffset.x, -r * 0.86f + shadowOffset.y), strokeWidth = r * 0.012f)
            drawLine(c.accent, center + Offset(0f, r * 0.2f), center + Offset(0f, -r * 0.86f), strokeWidth = r * 0.012f, cap = StrokeCap.Round)
            drawCircle(c.accent, radius = r * 0.035f, center = center + Offset(0f, r * 0.16f))
        }
    }
    drawCircle(if (showSeconds && !mini) c.accent else ink, radius = r * (if (mini) 0.1f else 0.045f), center = center)
    drawCircle(face, radius = r * (if (mini) 0.04f else 0.016f), center = center)
}

/** A tapered hand pointing at [angleDegrees] (0 = 12 o'clock). */
private fun DrawScope.drawHand(center: Offset, angleDegrees: Float, length: Float, width: Float, color: Color) {
    rotate(angleDegrees, pivot = center) {
        val path = Path().apply {
            moveTo(center.x - width / 2f, center.y + length * 0.14f)
            lineTo(center.x - width * 0.3f, center.y - length)
            quadraticTo(center.x, center.y - length - width * 0.6f, center.x + width * 0.3f, center.y - length)
            lineTo(center.x + width / 2f, center.y + length * 0.14f)
            close()
        }
        drawPath(path, color)
    }
}

private fun DrawScope.drawCentered(layout: TextLayoutResult, at: Offset) {
    drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
}
