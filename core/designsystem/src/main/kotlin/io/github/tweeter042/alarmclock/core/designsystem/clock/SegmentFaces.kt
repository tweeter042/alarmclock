package io.github.tweeter042.alarmclock.core.designsystem.clock

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.tweeter042.alarmclock.core.designsystem.R
import io.github.tweeter042.alarmclock.core.designsystem.component.ContainerBorder
import io.github.tweeter042.alarmclock.core.designsystem.component.themedContainer
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import io.github.tweeter042.alarmclock.core.designsystem.theme.ShadowSpec
import io.github.tweeter042.alarmclock.core.designsystem.theme.mix

// ---------------------------------------------------------------------------------------------
// Shared glyph layout for drawn digit faces.
// ---------------------------------------------------------------------------------------------

internal enum class GlyphKind { Digit, Blank, Colon, Dot, Minus }

internal data class PlacedGlyph(val kind: GlyphKind, val char: Char, val x: Float, val scale: Float)

/** Lays out [text] with widths given per glyph kind (in units of the digit height). */
internal fun layoutGlyphs(text: String, digit: Float, colon: Float, dot: Float, gap: Float, smallScale: Float): Pair<List<PlacedGlyph>, Float> {
    val groups = text.split(':')
    val out = mutableListOf<PlacedGlyph>()
    var x = 0f
    groups.forEachIndexed { gi, group ->
        // A third group (seconds) is drawn smaller, LCD-watch style.
        val scale = if (gi >= 2) smallScale else 1f
        if (gi == 1) {
            out += PlacedGlyph(GlyphKind.Colon, ':', x, 1f)
            x += colon + gap
        } else if (gi >= 2) {
            x += gap * 1.5f
        }
        for (ch in group) {
            val kind = when {
                ch.isDigit() -> GlyphKind.Digit
                ch == '.' || ch == ',' -> GlyphKind.Dot
                ch == '-' -> GlyphKind.Minus
                else -> GlyphKind.Blank
            }
            out += PlacedGlyph(kind, ch, x, scale)
            x += (if (kind == GlyphKind.Dot) dot else digit) * scale + gap * scale
        }
    }
    return out to (x - gap).coerceAtLeast(0f)
}

/** Colon blink state: 1 = visible. Solid when blinking is off or motion is reduced. */
@Composable
internal fun rememberColonAlpha(blink: Boolean): State<Float> {
    val motion = AppTheme.motion
    return if (blink && motion.ambientEnabled) {
        rememberInfiniteTransition(label = "colon").animateFloat(
            initialValue = 1f,
            targetValue = 0.15f,
            animationSpec = infiniteRepeatable(tween(1000, easing = { f -> if (f < 0.5f) 0f else 1f }), RepeatMode.Restart),
            label = "colonAlpha",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }
}

/** LCD-style annunciators: both labels, the active one lit, the other ghosted. */
@Composable
private fun Annunciators(amPm: String?, heightDp: Dp, lit: Color, ghost: Color) {
    if (amPm == null) return
    val am = stringResource(R.string.ds_am)
    val pm = stringResource(R.string.ds_pm)
    val style = AppTheme.typography.clockLabel
    val fontSize = with(LocalDensity.current) { (heightDp * 0.2f).toSp() }
    Column(Modifier.padding(end = heightDp * 0.12f), horizontalAlignment = Alignment.End) {
        Text(am, style = style.copy(fontSize = fontSize, lineHeight = fontSize * 1.25f), color = if (amPm == am) lit else ghost, maxLines = 1)
        Text(pm, style = style.copy(fontSize = fontSize, lineHeight = fontSize * 1.25f), color = if (amPm == pm) lit else ghost, maxLines = 1)
    }
}

/** Ghost colour that remains visible as "unlit" even on palettes with a very faint ghost. */
@Composable
private fun ghostColor(): Color {
    val c = AppTheme.colors
    val ghost = c.clockGhost
    return if (ghost.alpha < 0.07f) c.clockDigit.copy(alpha = 0.08f) else ghost
}

/**
 * Wraps a drawn face: fits it to the width ([contentWidthUnits] × digit height) and frames big
 * sizes in an LCD window.
 */
@Composable
private fun DrawnFaceFrame(
    size: ClockSize,
    amPm: String?,
    contentWidthUnits: Float,
    framed: Boolean,
    modifier: Modifier,
    lit: Color,
    ghost: Color,
    content: @Composable (height: Dp) -> Unit,
) {
    val c = AppTheme.colors
    val labelUnits = if (amPm != null) 1.1f else 0f
    val padUnits = if (framed) 0.5f else 0f
    BoxWithConstraints(
        if (size.fitWidth) modifier.fillMaxWidth() else modifier,
        contentAlignment = if (size.fitWidth) Alignment.Center else Alignment.CenterStart,
    ) {
        val nominal = size.digitHeight
        val needed = nominal * (contentWidthUnits + labelUnits + padUnits)
        val height = if (size.fitWidth && constraints.hasBoundedWidth && needed > maxWidth) nominal * (maxWidth / needed) * 0.98f else nominal
        val frame = if (framed) {
            Modifier
                .themedContainer(
                    RoundedCornerShape(height * 0.14f),
                    SolidColor(c.background.mix(lit, if (c.isDark) 0.05f else 0.035f)),
                    ContainerBorder(2.dp, c.outline.copy(alpha = 0.7f)),
                    ShadowSpec.None,
                )
                .padding(horizontal = height * 0.22f, vertical = height * 0.2f)
        } else {
            Modifier
        }
        Row(frame, verticalAlignment = Alignment.CenterVertically) {
            Annunciators(amPm, height, lit, ghost)
            content(height)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Seven segment
// ---------------------------------------------------------------------------------------------

/** Segment masks a..g for digits 0-9 (bit 0 = a … bit 6 = g). */
private val SEGMENTS = intArrayOf(
    0b0111111, 0b0000110, 0b1011011, 0b1001111, 0b1100110,
    0b1101101, 0b1111101, 0b0000111, 0b1111111, 0b1101111,
)

private const val SEG_DIGIT = 0.56f
private const val SEG_COLON = 0.26f
private const val SEG_DOT = 0.22f
private const val SEG_GAP = 0.16f
private const val SEG_SMALL = 0.62f
private const val SLANT = 0.12f

@Composable
internal fun SevenSegmentFace(text: String, amPm: String?, size: ClockSize, modifier: Modifier, blinkColon: Boolean, color: Color) {
    val lit = clockDigitColor(color)
    val ghost = ghostColor()
    val c = AppTheme.colors
    val padded = ClockFormat.padded(text)
    val (glyphs, width) = remember(padded) { layoutGlyphs(padded, SEG_DIGIT, SEG_COLON, SEG_DOT, SEG_GAP, SEG_SMALL) }
    val colon = rememberColonAlpha(blinkColon)
    val framed = size == ClockSize.Hero || size == ClockSize.Large
    DrawnFaceFrame(size, amPm, width + SLANT, framed, modifier, lit, ghost) { height ->
        val glow = c.isDark
        Canvas(Modifier.size(height * (width + SLANT), height)) {
            val h = this.size.height
            for (g in glyphs) {
                val gh = h * g.scale
                val top = h - gh
                val x = g.x * h
                when (g.kind) {
                    GlyphKind.Digit -> drawSegmentDigit(x, top, gh, SEGMENTS[g.char - '0'], lit, ghost, glow)
                    GlyphKind.Blank -> drawSegmentDigit(x, top, gh, 0, lit, ghost, glow)
                    GlyphKind.Minus -> drawSegmentDigit(x, top, gh, 0b1000000, lit, ghost, glow)
                    GlyphKind.Colon -> {
                        val dot = gh * 0.12f
                        val cx = x + gh * SEG_COLON / 2
                        for (fy in floatArrayOf(0.32f, 0.70f)) {
                            val cy = top + gh * fy
                            val skew = (h - cy) * SLANT
                            drawRect(ghost, Offset(cx - dot / 2 + skew, cy - dot / 2), Size(dot, dot))
                            drawRect(lit.copy(alpha = lit.alpha * colon.value), Offset(cx - dot / 2 + skew, cy - dot / 2), Size(dot, dot))
                        }
                    }
                    GlyphKind.Dot -> {
                        val dot = gh * 0.12f
                        drawRect(lit, Offset(x + gh * 0.05f, top + gh - dot), Size(dot, dot))
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawSegmentDigit(x: Float, top: Float, h: Float, mask: Int, lit: Color, ghost: Color, glow: Boolean) {
    val w = h * SEG_DIGIT
    val t = h * 0.13f
    val g = t * 0.16f
    val left = x + t / 2
    val right = x + w - t / 2
    val yTop = top + t / 2
    val yMid = top + h / 2
    val yBottom = top + h - t / 2
    val bottomY = top + h
    fun skew(px: Float, py: Float) = Offset(px + (bottomY - py) * SLANT, py)
    fun horizontal(y: Float): Path = Path().apply {
        val x1 = left + g
        val x2 = right - g
        moveTo(skew(x1, y)); lineTo(skew(x1 + t / 2, y - t / 2)); lineTo(skew(x2 - t / 2, y - t / 2))
        lineTo(skew(x2, y)); lineTo(skew(x2 - t / 2, y + t / 2)); lineTo(skew(x1 + t / 2, y + t / 2)); close()
    }
    fun vertical(xc: Float, y1: Float, y2: Float): Path = Path().apply {
        val a = y1 + g
        val b = y2 - g
        moveTo(skew(xc, a)); lineTo(skew(xc + t / 2, a + t / 2)); lineTo(skew(xc + t / 2, b - t / 2))
        lineTo(skew(xc, b)); lineTo(skew(xc - t / 2, b - t / 2)); lineTo(skew(xc - t / 2, a + t / 2)); close()
    }
    val segments = listOf(
        horizontal(yTop),                 // a
        vertical(right, yTop, yMid),      // b
        vertical(right, yMid, yBottom),   // c
        horizontal(yBottom),              // d
        vertical(left, yMid, yBottom),    // e
        vertical(left, yTop, yMid),       // f
        horizontal(yMid),                 // g
    )
    segments.forEachIndexed { i, path ->
        val on = mask and (1 shl i) != 0
        if (!on) {
            drawPath(path, ghost)
        } else if (glow) {
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = lit.toArgb()
                    setShadowLayer(t * 0.9f, 0f, 0f, lit.copy(alpha = 0.55f).toArgb())
                }
                canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
            }
        } else {
            drawPath(path, lit)
        }
    }
}

private fun Path.moveTo(o: Offset) = moveTo(o.x, o.y)
private fun Path.lineTo(o: Offset) = lineTo(o.x, o.y)

// ---------------------------------------------------------------------------------------------
// 5 × 7 dot matrix
// ---------------------------------------------------------------------------------------------

/** Classic 5×7 LCD-controller glyphs (7 rows of 5 columns) for the digits 0-9. */
private val DOT_FONT: Array<Array<String>> = arrayOf(
    arrayOf(".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."),
    arrayOf("..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."),
    arrayOf(".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"),
    arrayOf("#####", "...#.", "..#..", "...#.", "....#", "#...#", ".###."),
    arrayOf("...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."),
    arrayOf("#####", "#....", "####.", "....#", "....#", "#...#", ".###."),
    arrayOf("..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###."),
    arrayOf("#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."),
    arrayOf(".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."),
    arrayOf(".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##.."),
)

private const val PX_DIGIT = 5f / 7f
private const val PX_COLON = 2f / 7f
private const val PX_DOT = 2f / 7f
private const val PX_GAP = 1f / 7f
private const val PX_SMALL = 0.6f

internal fun dotLit(digit: Int, row: Int, col: Int): Boolean = DOT_FONT[digit][row][col] == '#'

@Composable
internal fun PixelMatrixFace(text: String, amPm: String?, size: ClockSize, modifier: Modifier, blinkColon: Boolean, color: Color) {
    val lit = clockDigitColor(color)
    val ghost = if (size.fitWidth) ghostColor() else Color.Transparent
    val padded = ClockFormat.padded(text)
    val (glyphs, width) = remember(padded) { layoutGlyphs(padded, PX_DIGIT, PX_COLON, PX_DOT, PX_GAP, PX_SMALL) }
    val colon = rememberColonAlpha(blinkColon)
    DrawnFaceFrame(size, amPm, width, framed = false, modifier = modifier, lit = lit, ghost = ghost) { height ->
        Canvas(Modifier.size(height * width, height)) {
            val h = this.size.height
            for (g in glyphs) {
                val gh = h * g.scale
                val p = gh / 7f
                val inset = p * 0.08f
                val top = h - gh
                val x0 = g.x * h
                fun pixel(col: Int, row: Int, on: Boolean, alpha: Float = 1f) {
                    drawRect(
                        if (on) lit.copy(alpha = lit.alpha * alpha) else ghost,
                        Offset(x0 + col * p + inset, top + row * p + inset),
                        Size(p - inset * 2, p - inset * 2),
                    )
                }
                when (g.kind) {
                    GlyphKind.Digit -> for (r in 0 until 7) for (cc in 0 until 5) pixel(cc, r, dotLit(g.char - '0', r, cc))
                    GlyphKind.Blank -> for (r in 0 until 7) for (cc in 0 until 5) pixel(cc, r, false)
                    GlyphKind.Minus -> for (r in 0 until 7) for (cc in 0 until 5) pixel(cc, r, r == 3)
                    GlyphKind.Colon -> for (r in listOf(1, 2, 4, 5)) for (cc in 0 until 2) {
                        pixel(cc, r, false)
                        pixel(cc, r, true, colon.value)
                    }
                    GlyphKind.Dot -> for (r in 5 until 7) for (cc in 0 until 2) pixel(cc, r, true)
                }
            }
        }
    }
}
