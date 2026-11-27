package io.github.tweeter042.alarmclock.core.designsystem.clock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import io.github.tweeter042.alarmclock.core.designsystem.theme.mix
import kotlin.math.PI
import kotlin.math.cos

/** Card height relative to the size's digit height. */
private const val CARD_HEIGHT = 1.45f

/** Fraction of the card height used by the font size. */
private const val FONT_IN_CARD = 0.9f

/**
 * Split-flap cards, one per group ("07", "30"), with seconds on smaller cards. When a group
 * changes, its top flap falls over the hinge revealing the new value (instant with reduce motion).
 */
@Composable
internal fun FlipClockFace(digits: String, amPm: String?, size: ClockSize, modifier: Modifier) {
    val tokens = AppTheme.tokens
    val c = tokens.colors
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val groups = digits.split(':')
    val style = tokens.typography.clock
    BoxWithConstraints(
        if (size.fitWidth) modifier.fillMaxWidth() else modifier,
        contentAlignment = if (size.fitWidth) Alignment.Center else Alignment.CenterStart,
    ) {
        // Width of all cards in units of the card height, measured with the reference "88".
        val refPx = 100f
        val refStyle = style.copy(fontSize = with(density) { (refPx * FONT_IN_CARD).toSp() })
        fun cardWidthUnits(len: Int): Float {
            val w = measurer.measure("8".repeat(len.coerceAtLeast(2)), refStyle, maxLines = 1, softWrap = false).size.width
            return w / refPx + 0.36f
        }
        val scales = groups.indices.map { if (it >= 2) 0.62f else 1f }
        val gapUnits = 0.12f
        val totalUnits = groups.indices.sumOf { (cardWidthUnits(groups[it].length) * scales[it]).toDouble() }.toFloat() +
            gapUnits * (groups.size - 1)
        val nominalCard = size.digitHeight * CARD_HEIGHT
        val needed = nominalCard * totalUnits
        val cardHeight = if (size.fitWidth && constraints.hasBoundedWidth && needed > maxWidth) nominalCard * (maxWidth / needed) * 0.98f else nominalCard
        Row(
            horizontalArrangement = Arrangement.spacedBy(cardHeight * gapUnits),
            verticalAlignment = Alignment.Bottom,
        ) {
            groups.forEachIndexed { i, group ->
                val h = cardHeight * scales[i]
                FlipCard(
                    text = group,
                    width = h * cardWidthUnits(group.length),
                    height = h,
                    style = style,
                    card = c.flipCard,
                    digit = c.flipDigit,
                    hinge = c.background,
                    corner = if (i == 0) amPm else null,
                    cornerStyle = tokens.typography.clockLabel,
                )
            }
        }
    }
}

@Composable
private fun FlipCard(
    text: String,
    width: Dp,
    height: Dp,
    style: TextStyle,
    card: Color,
    digit: Color,
    hinge: Color,
    corner: String?,
    cornerStyle: TextStyle,
) {
    val motion = AppTheme.motion
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    var shown by remember { mutableStateOf(text) }
    var previous by remember { mutableStateOf(text) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(text) {
        if (text != shown) {
            previous = shown
            shown = text
            if (motion.reduceMotion) {
                progress.snapTo(1f)
            } else {
                progress.snapTo(0f)
                progress.animateTo(1f, tween(620, easing = FlipEasing))
            }
        }
    }
    val heightPx = with(density) { height.toPx() }
    val fontStyle = style.copy(fontSize = with(density) { (heightPx * FONT_IN_CARD).toSp() }, lineHeight = with(density) { (heightPx * FONT_IN_CARD).toSp() })
    val newLayout = remember(shown, fontStyle) { measurer.measure(shown, fontStyle, maxLines = 1, softWrap = false) }
    val oldLayout = remember(previous, fontStyle) { measurer.measure(previous, fontStyle, maxLines = 1, softWrap = false) }
    val cornerLayout = remember(corner, heightPx) {
        corner?.let { measurer.measure(it, cornerStyle.copy(fontSize = with(density) { (heightPx * 0.11f).toSp() }), maxLines = 1) }
    }
    val radius = height * 0.09f
    Canvas(
        Modifier
            .size(width, height)
            .dropShadow(RoundedCornerShape(radius), Shadow(radius = height * 0.08f, color = Color.Black.copy(alpha = 0.35f), offset = DpOffset(0.dp, height * 0.03f)))
            .clip(RoundedCornerShape(radius)),
    ) {
        val w = this.size.width
        val h = this.size.height
        val mid = h / 2f
        val p = progress.value
        val top = Rect(0f, 0f, w, mid)
        val bottom = Rect(0f, mid, w, h)
        val topFill = Brush.verticalGradient(listOf(card.mix(Color.White, 0.10f), card.mix(Color.White, 0.03f)), startY = 0f, endY = mid)
        val bottomFill = Brush.verticalGradient(listOf(card, card.mix(Color.Black, 0.10f)), startY = mid, endY = h)

        // Static halves: top already shows the new value, bottom the old one until the flap lands.
        drawHalf(top, topFill, newLayout, digit)
        drawHalf(bottom, bottomFill, if (p < 0.5f) oldLayout else newLayout, digit)
        if (p < 1f) {
            if (p < 0.5f) {
                val sy = cos(p * PI).toFloat()
                scale(1f, sy, pivot = Offset(w / 2f, mid)) {
                    drawHalf(top, topFill, oldLayout, digit)
                    drawRect(Color.Black.copy(alpha = 0.45f * p * 2f), top.topLeft, top.size)
                }
            } else {
                val sy = -cos(p * PI).toFloat()
                scale(1f, sy, pivot = Offset(w / 2f, mid)) {
                    drawHalf(bottom, bottomFill, newLayout, digit)
                    drawRect(Color.Black.copy(alpha = 0.35f * (1f - p) * 2f), bottom.topLeft, bottom.size)
                }
            }
        }
        // Hinge split and the side notches of the card axle.
        val hingeH = (h * 0.012f).coerceAtLeast(1.5f)
        drawRect(hinge.copy(alpha = 0.9f), Offset(0f, mid - hingeH / 2), Size(w, hingeH))
        val notchW = w * 0.035f
        drawRect(Color.Black.copy(alpha = 0.35f), Offset(0f, mid - h * 0.04f), Size(notchW, h * 0.08f))
        drawRect(Color.Black.copy(alpha = 0.35f), Offset(w - notchW, mid - h * 0.04f), Size(notchW, h * 0.08f))
        if (cornerLayout != null) {
            drawText(cornerLayout, color = digit.copy(alpha = 0.85f), topLeft = Offset(w * 0.07f, h - cornerLayout.size.height - h * 0.05f))
        }
    }
}

/** Draws one half of the card (background + clipped digits centred on the card). */
private fun DrawScope.drawHalf(half: Rect, fill: Brush, layout: TextLayoutResult, digit: Color) {
    clipRect(half.left, half.top, half.right, half.bottom) {
        drawRect(fill, half.topLeft, half.size)
        val glyphs = layout.getPathForRange(0, layout.layoutInput.text.length).getBounds()
        val h = size.height
        val w = size.width
        val dx = (w - glyphs.width) / 2f - glyphs.left
        val dy = (h - glyphs.height) / 2f - glyphs.top
        drawText(layout, color = digit, topLeft = Offset(dx, dy))
    }
}

/** Gravity-like flap: slow start, fast fall, slight settle. */
private val FlipEasing = CubicBezierEasing(0.45f, 0f, 0.6f, 1f)
