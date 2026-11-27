package io.github.tweeter042.alarmclock.core.designsystem.component

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppColors
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import io.github.tweeter042.alarmclock.core.designsystem.theme.BackgroundStyle
import io.github.tweeter042.alarmclock.core.designsystem.theme.mix
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * The page background of the current theme: Glass colour blobs, Terminal scanlines, Paper grain,
 * Pixel grid, Neon perspective grid, Brutal dot grid, LCD panel, watch-dial sunburst, pure AMOLED
 * black, … [animated] = true lets ambient backgrounds move slowly (ring screen, bedside clock);
 * it is ignored with reduce motion.
 */
@Composable
fun ThemedBackground(
    modifier: Modifier = Modifier,
    animated: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val tokens = AppTheme.tokens
    val phase: State<Float> = if (animated && tokens.motion.ambientEnabled && tokens.background.isAnimatable()) {
        rememberInfiniteTransition(label = "background").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(tokens.background.loopMillis(), easing = LinearEasing), RepeatMode.Restart),
            label = "phase",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    Box(modifier.themedBackground(tokens.background, tokens.colors, phase), content = content)
}

private fun BackgroundStyle.isAnimatable() = this in setOf(
    BackgroundStyle.GlassBlobs, BackgroundStyle.Scanlines, BackgroundStyle.NeonGrid, BackgroundStyle.Tonal,
)

private fun BackgroundStyle.loopMillis(): Int = when (this) {
    BackgroundStyle.GlassBlobs -> 24_000
    BackgroundStyle.Scanlines -> 7_000
    BackgroundStyle.NeonGrid -> 3_000
    else -> 20_000
}

/** Draws [style] behind the content. [phase] (0..1) drives ambient motion. */
fun Modifier.themedBackground(style: BackgroundStyle, colors: AppColors, phase: State<Float>): Modifier =
    drawWithCache { buildBackground(style, colors, phase) }

private fun CacheDrawScope.buildBackground(style: BackgroundStyle, c: AppColors, phase: State<Float>): DrawResult {
    val bg = c.background
    val w = size.width
    val h = size.height
    return when (style) {
        BackgroundStyle.Plain -> onDrawBehind { drawRect(bg) }
        BackgroundStyle.PureBlack -> onDrawBehind { drawRect(Color.Black) }

        BackgroundStyle.DotGrid -> {
            val step = 22.dp.toPx()
            val dot = c.outline.copy(alpha = if (c.isDark) 0.22f else 0.26f)
            val tile = tileBrush(step, step) { drawCircle(dot, radius = 1.4.dp.toPx(), center = Offset(step / 2, step / 2)) }
            onDrawBehind {
                drawRect(bg)
                drawRect(tile)
            }
        }

        BackgroundStyle.LcdPanel -> {
            val line = 3.dp.toPx()
            val lines = tileBrush(1f, line) { drawRect(c.clockDigit.copy(alpha = if (c.isDark) 0.05f else 0.035f), size = Size(1f, 1.dp.toPx())) }
            val sheen = Brush.verticalGradient(
                listOf(bg.mix(Color.White, if (c.isDark) 0.04f else 0.12f), bg, bg.mix(Color.Black, if (c.isDark) 0.2f else 0.06f)),
            )
            val vignette = vignette(w, h, if (c.isDark) 0.45f else 0.14f)
            onDrawBehind {
                drawRect(sheen)
                drawRect(lines)
                drawRect(vignette)
            }
        }

        BackgroundStyle.FlipDesk -> {
            val gradient = Brush.verticalGradient(
                listOf(bg.mix(Color.White, if (c.isDark) 0.05f else 0.10f), bg, bg.mix(Color.Black, if (c.isDark) 0.35f else 0.07f)),
            )
            val spot = Brush.radialGradient(
                listOf(Color.White.copy(alpha = if (c.isDark) 0.05f else 0.18f), Color.Transparent),
                center = Offset(w * 0.5f, h * 0.18f),
                radius = max(w, h) * 0.6f,
            )
            onDrawBehind {
                drawRect(gradient)
                drawRect(spot)
            }
        }

        BackgroundStyle.GlassBlobs -> {
            val alpha = if (c.isDark) 0.55f else 0.5f
            val blobColors = listOf(c.primary, c.accent, c.glow, c.primary.mix(c.accent, 0.5f))
            val anchors = listOf(Offset(0.12f, 0.10f), Offset(0.98f, 0.38f), Offset(0.18f, 0.78f), Offset(0.85f, 0.98f))
            val radii = listOf(0.80f, 0.70f, 0.72f, 0.60f)
            val wash = if (c.isDark) Color.Transparent else Color.White.copy(alpha = 0.18f)
            onDrawBehind {
                drawRect(bg)
                val t = phase.value * 2f * PI.toFloat()
                for (i in blobColors.indices) {
                    val a = anchors[i]
                    val dx = sin(t + i * 1.7f) * 0.06f
                    val dy = cos(t * (if (i % 2 == 0) 1f else -1f) + i) * 0.05f
                    val center = Offset((a.x + dx) * w, (a.y + dy) * h)
                    val radius = radii[i] * w
                    drawCircle(
                        Brush.radialGradient(
                            listOf(blobColors[i].copy(alpha = alpha), blobColors[i].copy(alpha = alpha * 0.45f), Color.Transparent),
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                        center = center,
                    )
                }
                drawRect(wash)
            }
        }

        BackgroundStyle.Tonal -> {
            val tint = c.primary.copy(alpha = if (c.isDark) 0.10f else 0.07f)
            onDrawBehind {
                drawRect(bg)
                val shift = sin(phase.value * 2f * PI.toFloat()) * 0.03f
                drawCircle(
                    Brush.radialGradient(listOf(tint, Color.Transparent), center = Offset(w * (0.9f + shift), 0f), radius = w * 0.9f),
                    radius = w * 0.9f,
                    center = Offset(w * (0.9f + shift), 0f),
                )
            }
        }

        BackgroundStyle.Scanlines -> {
            val period = 3.dp.toPx()
            val scan = tileBrush(1f, period) { drawRect(Color.Black.copy(alpha = 0.32f), size = Size(1f, period / 3f)) }
            val glow = Brush.radialGradient(
                listOf(c.onBackground.copy(alpha = 0.07f), Color.Transparent),
                center = Offset(w / 2, h * 0.4f),
                radius = max(w, h) * 0.7f,
            )
            val vignette = vignette(w, h, 0.65f)
            val bandHeight = 140.dp.toPx()
            onDrawBehind {
                drawRect(bg)
                drawRect(glow)
                if (phase.value > 0f) {
                    val y = phase.value * (h + bandHeight) - bandHeight
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, c.onBackground.copy(alpha = 0.035f), Color.Transparent),
                            startY = y,
                            endY = y + bandHeight,
                        ),
                        topLeft = Offset(0f, y),
                        size = Size(w, bandHeight),
                    )
                }
                drawRect(scan)
                drawRect(vignette)
            }
        }

        BackgroundStyle.NeonGrid -> {
            val horizon = h * 0.66f
            val line = c.glow.copy(alpha = 0.2f)
            val sky = Brush.verticalGradient(
                0f to bg,
                0.66f to bg.mix(c.glow, 0.10f),
                1f to bg,
            )
            val vignette = vignette(w, h, 0.55f)
            onDrawBehind {
                drawRect(sky)
                // Horizon glow.
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, c.glow.copy(alpha = 0.22f), Color.Transparent), startY = horizon - 60.dp.toPx(), endY = horizon + 30.dp.toPx()),
                    topLeft = Offset(0f, horizon - 60.dp.toPx()),
                    size = Size(w, 90.dp.toPx()),
                )
                drawPerspectiveGrid(horizon, line, phase.value)
                drawLine(c.glow.copy(alpha = 0.7f), Offset(0f, horizon), Offset(w, horizon), 1.5.dp.toPx())
                drawRect(vignette)
            }
        }

        BackgroundStyle.PaperGrain -> {
            val grain = noiseBrush(ink = c.onBackground, dark = c.isDark)
            val vignette = vignette(w, h, if (c.isDark) 0.35f else 0.10f, tint = if (c.isDark) Color.Black else c.onBackground.mix(bg, 0.5f))
            onDrawBehind {
                drawRect(bg)
                drawRect(grain)
                drawRect(vignette)
            }
        }

        BackgroundStyle.PixelGrid -> {
            val step = 12.dp.toPx().roundToInt().toFloat()
            val ink = c.onBackground.copy(alpha = if (c.isDark) 0.07f else 0.08f)
            val grid = tileBrush(step, step) {
                drawRect(ink, size = Size(step, 1.dp.toPx()))
                drawRect(ink, size = Size(1.dp.toPx(), step))
            }
            onDrawBehind {
                drawRect(bg)
                drawRect(grid)
            }
        }

        BackgroundStyle.WatchDial -> {
            val center = Offset(w / 2f, h * 0.36f)
            val radial = Brush.radialGradient(
                listOf(bg.mix(Color.White, if (c.isDark) 0.07f else 0.35f), bg, bg.mix(Color.Black, if (c.isDark) 0.35f else 0.05f)),
                center = center,
                radius = max(w, h) * 0.85f,
            )
            val rayColor = (if (c.isDark) Color.White else Color.Black).copy(alpha = if (c.isDark) 0.018f else 0.014f)
            val length = max(w, h) * 1.2f
            onDrawBehind {
                drawRect(radial)
                for (i in 0 until 120 step 2) {
                    val angle = i * (2 * PI / 120)
                    drawLine(rayColor, center, center + Offset((cos(angle) * length).toFloat(), (sin(angle) * length).toFloat()), 1.dp.toPx())
                }
            }
        }
    }
}

private fun DrawScope.drawPerspectiveGrid(horizon: Float, color: Color, phase: Float) {
    val w = size.width
    val h = size.height
    val depth = h - horizon
    val stroke = 1.dp.toPx()
    // Horizontal lines bunch up towards the horizon; phase scrolls them towards the viewer.
    val rows = 10
    for (i in 0..rows) {
        val t = ((i + phase) / rows).coerceIn(0f, 1f)
        val y = horizon + depth * t.pow(2.2f)
        drawLine(color.copy(alpha = color.alpha * (0.35f + 0.65f * t)), Offset(0f, y), Offset(w, y), stroke)
    }
    // Vertical lines radiate from the vanishing point.
    val vp = Offset(w / 2, horizon)
    val columns = 14
    for (i in -columns..columns) {
        val xBottom = w / 2 + i * (w / columns) * 1.6f
        drawLine(color, vp, Offset(xBottom, h), stroke)
    }
}

/** A repeating pattern brush drawn once into a small bitmap tile. */
private fun CacheDrawScope.tileBrush(tileWidth: Float, tileHeight: Float, draw: DrawScope.() -> Unit): Brush {
    val tw = tileWidth.roundToInt().coerceAtLeast(1)
    val th = tileHeight.roundToInt().coerceAtLeast(1)
    val bitmap = ImageBitmap(tw, th)
    CanvasDrawScope().draw(this, LayoutDirection.Ltr, Canvas(bitmap), Size(tw.toFloat(), th.toFloat()), draw)
    return ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
}

private fun vignette(w: Float, h: Float, strength: Float, tint: Color = Color.Black): Brush = Brush.radialGradient(
    0f to Color.Transparent,
    0.55f to Color.Transparent,
    1f to tint.copy(alpha = strength),
    center = Offset(w / 2, h / 2),
    radius = max(w, h) * 0.75f,
)

/** Deterministic paper grain: sparse specks plus a few fibres, tinted with the ink colour. */
private fun noiseBrush(ink: Color, dark: Boolean): Brush {
    val size = 192
    val random = Random(20261127)
    val pixels = IntArray(size * size)
    val r = (ink.red * 255).roundToInt()
    val g = (ink.green * 255).roundToInt()
    val b = (ink.blue * 255).roundToInt()
    val maxAlpha = if (dark) 22 else 30
    for (i in pixels.indices) {
        if (random.nextFloat() < 0.22f) {
            val a = random.nextInt(4, maxAlpha)
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
    repeat(14) {
        var x = random.nextInt(size).toFloat()
        var y = random.nextInt(size).toFloat()
        val angle = random.nextFloat() * 2f * PI.toFloat()
        val length = random.nextInt(8, 26)
        repeat(length) {
            val ix = ((x.roundToInt() % size) + size) % size
            val iy = ((y.roundToInt() % size) + size) % size
            pixels[iy * size + ix] = ((maxAlpha + 6) shl 24) or (r shl 16) or (g shl 8) or b
            x += cos(angle)
            y += sin(angle)
        }
    }
    val bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
    return ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
}
