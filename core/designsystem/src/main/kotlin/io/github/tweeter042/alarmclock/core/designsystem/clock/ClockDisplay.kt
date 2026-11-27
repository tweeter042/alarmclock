package io.github.tweeter042.alarmclock.core.designsystem.clock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.tweeter042.alarmclock.core.designsystem.R
import io.github.tweeter042.alarmclock.core.designsystem.component.AppDivider
import io.github.tweeter042.alarmclock.core.designsystem.component.BlinkingBlockCursor
import io.github.tweeter042.alarmclock.core.designsystem.component.LocalContainerColor
import io.github.tweeter042.alarmclock.core.designsystem.component.forText
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import io.github.tweeter042.alarmclock.core.designsystem.theme.ColorContrast
import io.github.tweeter042.alarmclock.core.designsystem.theme.mix
import io.github.tweeter042.alarmclock.core.model.settings.NumeralStyle
import io.github.tweeter042.alarmclock.core.model.theme.ClockFace
import java.time.LocalTime

/**
 * The big clock in the style of the current theme (or [faceOverride]): plain digits, Retro LCD
 * seven-segment with ghost segments, split-flap cards, analog dial, 5×7 dot matrix, terminal
 * with a blinking cursor, layered neon glow or editorial serif.
 *
 * Font-based faces honour [numerals] (Thai digits ๐–๙); seven-segment and dot-matrix faces are
 * drawn as segments/dots and always show Arabic digits, like the hardware they imitate.
 * Animations (blinking colon, flips, cursor, neon flicker) stop with reduce motion.
 */
@Composable
fun ClockDisplay(
    time: LocalTime,
    use24h: Boolean,
    showSeconds: Boolean = false,
    numerals: NumeralStyle = NumeralStyle.ARABIC,
    size: ClockSize = ClockSize.Hero,
    faceOverride: ClockFace? = null,
    modifier: Modifier = Modifier,
) {
    val face = faceOverride ?: AppTheme.clockFace
    val reading = ClockFormat.reading(time, use24h, showSeconds)
    val amPm = reading.amPm?.let { stringResource(if (it == AmPm.AM) R.string.ds_am else R.string.ds_pm) }
    val spoken = listOfNotNull(ClockFormat.localizeDigits(reading.text, numerals), amPm).joinToString(" ")
    val description = stringResource(R.string.ds_clock_description, spoken)
    val semantics = Modifier.clearAndSetSemantics { contentDescription = description }
    when (face) {
        ClockFace.ANALOG -> AnalogClockFace(time, showSeconds, numerals, size, amPm, modifier.then(semantics))
        else -> ClockFaceText(reading.text, amPm, face, numerals, size, modifier.then(semantics), blinkColon = true)
    }
}

/**
 * Clock-styled text for anything that is not a time of day: timer "05:00", stopwatch
 * "01:23.45", countdowns. Analog falls back to the theme's clock font.
 */
@Composable
fun ClockText(
    text: String,
    modifier: Modifier = Modifier,
    size: ClockSize = ClockSize.Large,
    numerals: NumeralStyle = NumeralStyle.ARABIC,
    faceOverride: ClockFace? = null,
    color: Color = Color.Unspecified,
) {
    val face = (faceOverride ?: AppTheme.clockFace).let { if (it == ClockFace.ANALOG) ClockFace.PLAIN else it }
    val description = ClockFormat.localizeDigits(text, numerals)
    ClockFaceText(
        text = text,
        amPm = null,
        face = face,
        numerals = numerals,
        size = size,
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        blinkColon = false,
        color = color,
    )
}

@Composable
internal fun ClockFaceText(
    text: String,
    amPm: String?,
    face: ClockFace,
    numerals: NumeralStyle,
    size: ClockSize,
    modifier: Modifier,
    blinkColon: Boolean,
    color: Color = Color.Unspecified,
) {
    when (face) {
        ClockFace.SEVEN_SEGMENT -> SevenSegmentFace(text, amPm, size, modifier, blinkColon, color)
        ClockFace.PIXEL -> PixelMatrixFace(text, amPm, size, modifier, blinkColon, color)
        ClockFace.FLIP -> FlipClockFace(ClockFormat.localizeDigits(text, numerals), amPm, size, modifier)
        ClockFace.TERMINAL -> TerminalClockFace(ClockFormat.localizeDigits(text, numerals), amPm, size, modifier, color)
        ClockFace.NEON_GLOW -> NeonClockFace(ClockFormat.localizeDigits(text, numerals), amPm, size, modifier, color)
        ClockFace.SERIF -> SerifClockFace(ClockFormat.localizeDigits(text, numerals), amPm, size, modifier, color)
        ClockFace.PLAIN, ClockFace.ANALOG -> PlainClockFace(ClockFormat.localizeDigits(text, numerals), amPm, size, modifier, color)
    }
}

/**
 * Digit colour that stays readable: on the page with a foreign face/palette combination and on
 * filled containers (an inverse-video card gets the card's content colour).
 */
@Composable
internal fun clockDigitColor(override: Color = Color.Unspecified): Color {
    if (override != Color.Unspecified) return override
    val c = AppTheme.colors
    val container = LocalContainerColor.current
    val behind = if (container == Color.Unspecified || container.alpha < 1f) c.background else container
    return when {
        ColorContrast.ratio(c.clockDigit, behind) >= 3f -> c.clockDigit
        container != Color.Unspecified -> LocalContentColor.current
        else -> c.onBackground
    }
}

// ---------------------------------------------------------------------------------------------
// Fitting
// ---------------------------------------------------------------------------------------------

/** Font size of AM/PM relative to the digits. */
internal fun ClockSize.labelRatio(): Float = if (this == ClockSize.Hero || this == ClockSize.Large) 0.2f else 0.36f

/** Long labels (Thai "หลังเที่ยง") go above big clocks instead of beside them. */
internal fun labelAbove(amPm: String?, size: ClockSize): Boolean =
    amPm != null && amPm.length > 3 && size.fitWidth

/**
 * Picks the largest font size up to the [size]'s nominal size at which [widthAt] fits the
 * available width, then renders [content] with it.
 */
@Composable
internal fun FittedClock(
    size: ClockSize,
    modifier: Modifier,
    widthAt: (fontSizePx: Float, measurer: TextMeasurer) -> Float,
    content: @Composable (fontSize: TextUnit, fontSizePx: Float) -> Unit,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val nominalPx = with(density) { size.fontSizeDp.dp.toPx() }
    if (!size.fitWidth) {
        Box(modifier) { content(with(density) { nominalPx.toSp() }, nominalPx) }
        return
    }
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val maxW = constraints.maxWidth.toFloat()
        val needed = widthAt(nominalPx, measurer)
        val px = if (constraints.hasBoundedWidth && needed > maxW && needed > 0f) nominalPx * (maxW / needed) * 0.98f else nominalPx
        content(with(density) { px.toSp() }, px)
    }
}

internal fun TextMeasurer.widthOf(text: String, style: TextStyle, fontSizePx: Float, density: androidx.compose.ui.unit.Density): Float =
    measure(text, style.copy(fontSize = with(density) { fontSizePx.toSp() }), maxLines = 1, softWrap = false).size.width.toFloat()

// ---------------------------------------------------------------------------------------------
// Plain / Serif
// ---------------------------------------------------------------------------------------------

@Composable
private fun PlainClockFace(digits: String, amPm: String?, size: ClockSize, modifier: Modifier, color: Color) {
    val tokens = AppTheme.tokens
    val style = tokens.typography.clock
    val labelStyle = tokens.typography.clockLabel
    val ink = clockDigitColor(color)
    val density = LocalDensity.current
    val above = labelAbove(amPm, size)
    FittedClock(
        size = size,
        modifier = modifier,
        widthAt = { px, m ->
            m.widthOf(digits, style, px, density) +
                if (amPm != null && !above) m.widthOf(amPm, labelStyle, px * size.labelRatio(), density) + px * 0.12f else 0f
        },
    ) { fontSize, _ ->
        ClockWithLabel(digits, amPm, size, fontSize, style, labelStyle, ink, above)
    }
}

/** Digits plus an AM/PM label beside (baseline) or above them. */
@Composable
internal fun ClockWithLabel(
    digits: String,
    amPm: String?,
    size: ClockSize,
    fontSize: TextUnit,
    style: TextStyle,
    labelStyle: TextStyle,
    color: Color,
    above: Boolean,
    labelColor: Color = AppTheme.colors.onSurfaceVariant.let { if (ColorContrast.ratio(it, AppTheme.colors.background) >= 4.5f) it else color },
    digitsDecoration: @Composable (text: String, style: TextStyle) -> Unit = { t, s -> Text(t, style = s, color = color, maxLines = 1, softWrap = false) },
) {
    val digitStyle = style.copy(fontSize = fontSize, lineHeight = fontSize)
    val labelSize = fontSize * size.labelRatio()
    val label = labelStyle.copy(fontSize = labelSize, lineHeight = labelSize * 1.3f).let { if (amPm != null) it.forText(amPm) else it }
    if (amPm == null) {
        digitsDecoration(digits, digitStyle)
        return
    }
    if (above) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(amPm, style = label, color = labelColor, maxLines = 1)
            digitsDecoration(digits, digitStyle)
        }
    } else {
        Row(verticalAlignment = Alignment.Bottom) {
            Box(Modifier.alignByBaseline()) { digitsDecoration(digits, digitStyle) }
            Spacer(Modifier.width(with(LocalDensity.current) { (fontSize.toPx() * 0.1f).toDp() }))
            Text(amPm, style = label, color = labelColor, maxLines = 1, modifier = Modifier.alignByBaseline())
        }
    }
}

@Composable
private fun SerifClockFace(digits: String, amPm: String?, size: ClockSize, modifier: Modifier, color: Color) {
    val tokens = AppTheme.tokens
    val style = tokens.typography.clock
    val labelStyle = tokens.typography.clockLabel.copy(fontStyle = FontStyle.Italic, letterSpacing = 0.12.em)
    val ink = clockDigitColor(color)
    val density = LocalDensity.current
    val above = labelAbove(amPm, size)
    val editorial = size == ClockSize.Hero || size == ClockSize.Large
    FittedClock(
        size = size,
        modifier = modifier,
        widthAt = { px, m ->
            m.widthOf(digits, style, px, density) +
                if (amPm != null && !above) m.widthOf(amPm, labelStyle, px * size.labelRatio(), density) + px * 0.12f else 0f
        },
    ) { fontSize, _ ->
        if (!editorial) {
            ClockWithLabel(digits, amPm, size, fontSize, style, labelStyle, ink, above)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Newspaper rules framing the time like a headline.
                AppDivider(Modifier.width(IntrinsicRuleWidth))
                Spacer(Modifier.height(8.dp))
                ClockWithLabel(digits, amPm, size, fontSize, style, labelStyle, ink, above)
                Spacer(Modifier.height(10.dp))
                AppDivider(Modifier.width(IntrinsicRuleWidth), color = AppTheme.colors.onBackground.copy(alpha = 0.35f))
            }
        }
    }
}

private val IntrinsicRuleWidth: Dp = 240.dp

// ---------------------------------------------------------------------------------------------
// Terminal
// ---------------------------------------------------------------------------------------------

@Composable
private fun TerminalClockFace(digits: String, amPm: String?, size: ClockSize, modifier: Modifier, color: Color) {
    val tokens = AppTheme.tokens
    val c = tokens.colors
    val style = tokens.typography.clock.copy(
        shadow = Shadow(color = c.glow.copy(alpha = 0.55f), offset = Offset.Zero, blurRadius = 24f),
    )
    val labelStyle = tokens.typography.clockLabel.copy(fontFamily = tokens.typography.clockFamily)
    val ink = clockDigitColor(color)
    val density = LocalDensity.current
    val big = size == ClockSize.Hero || size == ClockSize.Large
    val line = if (amPm != null) "$digits $amPm" else digits
    FittedClock(
        size = size,
        modifier = modifier,
        widthAt = { px, m -> m.widthOf(line, style, px, density) + if (big) px * 0.62f else 0f },
    ) { fontSize, px ->
        Column(horizontalAlignment = Alignment.Start) {
            if (big) {
                Text(
                    text = stringResource(R.string.ds_terminal_prompt),
                    style = labelStyle.copy(fontSize = fontSize * 0.16f),
                    color = c.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.height(with(density) { (px * 0.06f).toDp() }))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = digits,
                    style = style.copy(fontSize = fontSize, lineHeight = fontSize),
                    color = ink,
                    maxLines = 1,
                    softWrap = false,
                )
                if (amPm != null) {
                    Text(
                        text = " $amPm",
                        style = style.copy(fontSize = fontSize * 0.32f, lineHeight = fontSize * 0.4f),
                        color = ink.copy(alpha = 0.8f),
                        maxLines = 1,
                    )
                }
                if (big) {
                    BlinkingBlockCursor(
                        height = with(density) { (px * 0.72f).toDp() },
                        color = ink,
                        modifier = Modifier.padding(start = with(density) { (px * 0.04f).toDp() }),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Neon
// ---------------------------------------------------------------------------------------------

@Composable
private fun NeonClockFace(digits: String, amPm: String?, size: ClockSize, modifier: Modifier, color: Color) {
    val tokens = AppTheme.tokens
    val c = tokens.colors
    val tube = clockDigitColor(color)
    val glow = c.glow
    val style = tokens.typography.clock
    val labelStyle = tokens.typography.clockLabel
    val density = LocalDensity.current
    val above = labelAbove(amPm, size)
    // Neon tubes "warm up" with a flicker when the face appears.
    val flicker = remember { Animatable(if (tokens.motion.ambientEnabled) 0f else 1f) }
    LaunchedEffect(tokens.motion.ambientEnabled) {
        if (tokens.motion.ambientEnabled) {
            flicker.animateTo(1f, keyframes {
                durationMillis = 900
                0f at 0
                1f at 90
                0.25f at 160
                1f at 260
                0.55f at 420
                1f at 520
            })
        } else {
            flicker.snapTo(1f)
        }
    }
    FittedClock(
        size = size,
        modifier = modifier,
        widthAt = { px, m ->
            m.widthOf(digits, style, px, density) * 1.06f +
                if (amPm != null && !above) m.widthOf(amPm, labelStyle, px * size.labelRatio(), density) + px * 0.12f else 0f
        },
    ) { fontSize, px ->
        // Digits recoloured for a filled container are drawn without glow (the halo would smear).
        val glowing = tube == c.clockDigit || color != Color.Unspecified
        val core = if (c.isDark) tube.mix(Color.White, 0.45f) else tube
        Box(Modifier.alpha(flicker.value)) {
            ClockWithLabel(
                digits = digits,
                amPm = amPm,
                size = size,
                fontSize = fontSize,
                style = style,
                labelStyle = labelStyle.copy(shadow = Shadow(glow, Offset.Zero, px * 0.06f)),
                color = tube,
                above = above,
                labelColor = tube,
                digitsDecoration = { text, s ->
                    if (glowing) {
                        NeonText(text, s, tube, if (c.isDark) glow else glow.copy(alpha = 0.5f), core, px)
                    } else {
                        Text(text, style = s, color = tube, maxLines = 1, softWrap = false)
                    }
                },
            )
        }
    }
}

/** Three stacked layers: wide bloom, tight halo and a hot white-ish core. */
@Composable
private fun NeonText(text: String, style: TextStyle, tube: Color, glow: Color, core: Color, px: Float) {
    Box {
        Text(text, style = style.copy(shadow = Shadow(glow, Offset.Zero, px * 0.32f)), color = tube.copy(alpha = 0.9f), maxLines = 1, softWrap = false)
        Text(text, style = style.copy(shadow = Shadow(glow, Offset.Zero, px * 0.12f)), color = tube, maxLines = 1, softWrap = false)
        Text(text, style = style.copy(shadow = Shadow(tube, Offset.Zero, px * 0.03f)), color = core, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
    }
}
