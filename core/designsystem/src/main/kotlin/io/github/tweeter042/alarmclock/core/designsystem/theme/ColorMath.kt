package io.github.tweeter042.alarmclock.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/** WCAG contrast helpers used by the theme builder, components and tests. */
object ColorContrast {

    /** WCAG 2.x contrast ratio (1..21). Translucent colours are composited over [background]. */
    fun ratio(foreground: Color, background: Color): Float {
        val bg = if (background.alpha < 1f) background.compositeOver(Color.White) else background
        val fg = if (foreground.alpha < 1f) foreground.compositeOver(bg) else foreground
        val l1 = fg.luminance()
        val l2 = bg.luminance()
        return (max(l1, l2) + 0.05f) / (min(l1, l2) + 0.05f)
    }

    /** ARGB long overload (palette colours from core:model). */
    fun ratio(foreground: Long, background: Long): Float = ratio(Color(foreground), Color(background))

    /** The candidate with the highest contrast on [background]. */
    fun bestOn(background: Color, candidates: List<Color>): Color =
        candidates.maxByOrNull { ratio(it, background) } ?: Color.Unspecified

    /**
     * Returns [preferred] if it reaches [minRatio] on [background]; otherwise moves it towards
     * black or white (whichever helps) until it does. Keeps the hue as long as possible.
     */
    fun ensure(preferred: Color, background: Color, minRatio: Float = 4.5f): Color {
        if (ratio(preferred, background) >= minRatio) return preferred
        val target = if (background.luminance() > 0.4f) Color.Black else Color.White
        var t = 0f
        var candidate = preferred
        while (t < 1f) {
            t += 0.05f
            candidate = lerp(preferred, target, t.coerceAtMost(1f))
            if (ratio(candidate, background) >= minRatio) return candidate
        }
        return target
    }
}

/** Opaque blend of two colours (t = 0 -> this, t = 1 -> [other]). */
internal fun Color.mix(other: Color, t: Float): Color = lerp(this, other, t.coerceIn(0f, 1f))
