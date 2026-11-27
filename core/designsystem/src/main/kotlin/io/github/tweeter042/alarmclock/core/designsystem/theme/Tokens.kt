package io.github.tweeter042.alarmclock.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.DurationBasedAnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.floor

/** How outlines are drawn. [width] = 0 means "no border". */
@Immutable
data class BorderSpec(
    val width: Dp,
    /** Border colour of cards, fields and outlined buttons. */
    val color: Color,
    /** Brutal / Pixel also outline filled buttons, chips and the FAB. */
    val outlineFilledControls: Boolean = false,
    val style: BorderStyle = BorderStyle.Solid,
) {
    val isVisible: Boolean get() = width > 0.dp && color.alpha > 0f
}

enum class BorderStyle { Solid, Dashed }

/** Elevation language of the theme. */
@Immutable
sealed interface ShadowSpec {
    /** Flat (Minimal, Material You, Terminal, Paper, AMOLED). */
    data object None : ShadowSpec

    /** Blurred drop shadow. */
    data class Soft(val radius: Dp, val offsetY: Dp, val color: Color) : ShadowSpec

    /** Solid, unblurred shadow shifted by [x]/[y] (Brutal, Pixel). Pressing slides the element onto it. */
    data class HardOffset(val x: Dp, val y: Dp, val color: Color) : ShadowSpec

    /** Coloured halo around the element (Neon). */
    data class Glow(val radius: Dp, val color: Color) : ShadowSpec
}

/**
 * Motion of the design language. Use [spec] / [fade] instead of raw `tween` so durations, easing,
 * Pixel's stepped motion and reduce-motion are honoured everywhere.
 */
@Immutable
data class MotionSpec(
    val shortMillis: Int,
    val mediumMillis: Int,
    val longMillis: Int,
    val easing: Easing,
    /** > 0: motion advances in this many discrete steps (Pixel). */
    val steps: Int = 0,
    /** Accessibility "reduce motion": everything becomes (near) instant and loops stop. */
    val reduceMotion: Boolean = false,
) {
    /** Whether looping/ambient animations (blinking cursor, drifting blobs, …) may run. */
    val ambientEnabled: Boolean get() = !reduceMotion

    /** Easing including Pixel's steps. */
    val effectiveEasing: Easing
        get() = if (steps > 0) SteppedEasing(steps, easing) else easing

    fun <T> spec(durationMillis: Int = mediumMillis): FiniteAnimationSpec<T> =
        if (reduceMotion) snap() else tween(durationMillis, easing = effectiveEasing)

    fun <T> short(): FiniteAnimationSpec<T> = spec(shortMillis)

    fun <T> long(): FiniteAnimationSpec<T> = spec(longMillis)

    /** Opacity changes stay perceivable (but very quick) with reduce motion. */
    fun <T> fade(durationMillis: Int = shortMillis): DurationBasedAnimationSpec<T> =
        tween(if (reduceMotion) REDUCED_MILLIS else durationMillis, easing = if (reduceMotion) LinearEasing else effectiveEasing)

    fun withReducedMotion(reduce: Boolean): MotionSpec = if (reduce == reduceMotion) this else copy(reduceMotion = reduce)

    companion object {
        const val REDUCED_MILLIS: Int = 40
        val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
        val Emphasized: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
        val Snappy: Easing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)
        val Gentle: Easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
    }
}

/** Quantises another easing into [steps] equal jumps (8-bit motion). */
class SteppedEasing(private val steps: Int, private val base: Easing = LinearEasing) : Easing {
    override fun transform(fraction: Float): Float {
        if (fraction >= 1f) return 1f
        val v = base.transform(fraction)
        return (floor(v * steps) / steps).coerceIn(0f, 1f)
    }
}

/** Spacing scale. Feature code uses these instead of literal dp values for layout rhythm. */
@Immutable
data class Spacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 48.dp,
    /** Horizontal page margin. */
    val screen: Dp = 16.dp,
    /** Inner padding of cards. */
    val card: Dp = 16.dp,
    /** Gap between cards / sections. */
    val gutter: Dp = 12.dp,
    /** Vertical padding of list rows. */
    val listItem: Dp = 12.dp,
) {
    val cardPadding: PaddingValues get() = PaddingValues(card)
    val screenPadding: PaddingValues get() = PaddingValues(horizontal = screen)
}

/** Which Material icon set fits the design language (see [io.github.tweeter042.alarmclock.core.designsystem.icon.AppIcons]). */
enum class IconStyle { Filled, Rounded, Outlined, Sharp }

/** Page background drawn by ThemedBackground. */
enum class BackgroundStyle {
    Plain,
    DotGrid,
    LcdPanel,
    FlipDesk,
    GlassBlobs,
    Tonal,
    Scanlines,
    NeonGrid,
    PaperGrain,
    PixelGrid,
    WatchDial,
    PureBlack,
}

/** How a card/container is filled. */
enum class CardFill { Solid, Frosted, Outline }

/** Feedback when a control is pressed (on top of, or instead of, the ripple). */
enum class PressEffect {
    /** Material ripple. */
    Ripple,
    /** Element slides onto its hard shadow (Brutal, Pixel). */
    ShiftIntoShadow,
    /** Colours invert like a selected terminal line. */
    Invert,
    /** The glow intensifies (Neon). */
    Glow,
    /** Slight scale-down (Glass, Analog, Flip). */
    Scale,
}

enum class DividerStyle { Hairline, Thick, Dashed, Double, Glow, Dotted }

enum class NavIndicator { Pill, Block, Underline, Bracket, GlowLine, Dot }

/** Rendering family of switches, checkboxes and radio buttons. */
enum class SelectionStyle { Rounded, Square, Pixel, Text }

enum class HeaderDecor { None, Prompt, Masthead, Rule, Glow, Pixel }

/**
 * Palette-independent decisions of a design language that components branch on.
 * Keeping them here (instead of `when (themeId)` in every component) keeps components generic.
 */
@Immutable
data class DesignTraits(
    val cardFill: CardFill = CardFill.Solid,
    /** Alpha of frosted cards (Glass). */
    val frostAlpha: Float = 1f,
    val pressEffect: PressEffect = PressEffect.Ripple,
    val dividerStyle: DividerStyle = DividerStyle.Hairline,
    val navIndicator: NavIndicator = NavIndicator.Pill,
    val selectionStyle: SelectionStyle = SelectionStyle.Rounded,
    val headerDecor: HeaderDecor = HeaderDecor.None,
    /** Buttons, chips, section headers and navigation labels in capitals. */
    val uppercaseLabels: Boolean = false,
    val labelLetterSpacing: TextUnit = 0.1.sp,
    /** Text-based controls wrap labels like "[ Save ]" (Terminal). */
    val bracketedLabels: Boolean = false,
    /** Titles and the clock glow softly (Neon, Terminal). */
    val textGlow: Boolean = false,
    /** Buttons get a light top / dark bottom bevel (Pixel, Retro LCD keys). */
    val bevel: Boolean = false,
    /** Primary buttons use a gradient fill (Glass). */
    val gradientButtons: Boolean = false,
    /** Primary actions are drawn as outlines to keep pixels dark (AMOLED). */
    val outlinedPrimary: Boolean = false,
    /** Progress bars and sliders are drawn as discrete blocks (LCD, Pixel, Terminal). */
    val segmentedProgress: Boolean = false,
)
