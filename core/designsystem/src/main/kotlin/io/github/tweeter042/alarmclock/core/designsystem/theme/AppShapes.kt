package io.github.tweeter042.alarmclock.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Component shapes of the active design language. Shapes are not always rounded rectangles:
 * Neon uses cut corners, Pixel uses stepped "pixel" corners, Brutal and Terminal are square.
 */
@Immutable
data class AppShapes(
    val extraSmall: Shape,
    val small: Shape,
    val medium: Shape,
    val large: Shape,
    val extraLarge: Shape,
    val card: Shape,
    val button: Shape,
    val chip: Shape,
    val field: Shape,
    val dialog: Shape,
    /** Bottom sheet (top corners only). */
    val sheet: Shape,
    val fab: Shape,
    val badge: Shape,
    /** Switch track, progress track. */
    val track: Shape,
    /** Switch / slider thumb, radio dot. */
    val thumb: Shape,
    /** Selected indicator of navigation items and segmented buttons. */
    val indicator: Shape,
    /** Day-of-week toggles. */
    val dayToggle: Shape,
    /** Checkbox box. */
    val checkbox: Shape,
    /** Container of icons in list rows / empty states. */
    val iconContainer: Shape,
    /** Material 3 shapes so stock widgets follow the theme (corner-based approximations). */
    val material: Shapes,
) {
    internal companion object {

        fun rounded(
            card: Dp, button: Shape, chip: Shape, field: Dp, dialog: Dp, sheet: Dp, fab: Shape,
            indicator: Shape = CircleShape, dayToggle: Shape = CircleShape, checkbox: Dp = 6.dp,
            iconContainer: Shape = CircleShape,
        ) = AppShapes(
            extraSmall = RoundedCornerShape(min(card.value, 6f).dp),
            small = RoundedCornerShape(min(card.value, 10f).dp),
            medium = RoundedCornerShape(card),
            large = RoundedCornerShape(max(card.value, dialog.value * 0.75f).dp),
            extraLarge = RoundedCornerShape(dialog),
            card = RoundedCornerShape(card),
            button = button,
            chip = chip,
            field = RoundedCornerShape(field),
            dialog = RoundedCornerShape(dialog),
            sheet = RoundedCornerShape(topStart = sheet, topEnd = sheet),
            fab = fab,
            badge = CircleShape,
            track = CircleShape,
            thumb = CircleShape,
            indicator = indicator,
            dayToggle = dayToggle,
            checkbox = RoundedCornerShape(checkbox),
            iconContainer = iconContainer,
            material = Shapes(
                extraSmall = RoundedCornerShape(min(card.value, 6f).dp),
                small = RoundedCornerShape(min(card.value, 10f).dp),
                medium = RoundedCornerShape(card),
                large = RoundedCornerShape(max(card.value, dialog.value * 0.75f).dp),
                extraLarge = RoundedCornerShape(dialog),
            ),
        )

        /** Every corner square (Brutal, Terminal). */
        fun square(thumb: Shape = RectangleShape) = AppShapes(
            extraSmall = RectangleShape, small = RectangleShape, medium = RectangleShape,
            large = RectangleShape, extraLarge = RectangleShape,
            card = RectangleShape, button = RectangleShape, chip = RectangleShape,
            field = RectangleShape, dialog = RectangleShape, sheet = RectangleShape,
            fab = RectangleShape, badge = RectangleShape, track = RectangleShape,
            thumb = thumb, indicator = RectangleShape, dayToggle = RectangleShape,
            checkbox = RectangleShape, iconContainer = RectangleShape,
            material = squareMaterial,
        )

        /** Cyberpunk cut corners (Neon). */
        fun cut(card: Dp, button: Dp, chip: Dp) = AppShapes(
            extraSmall = CutCornerShape(topStart = 4.dp, bottomEnd = 4.dp),
            small = CutCornerShape(topStart = chip, bottomEnd = chip),
            medium = CutCornerShape(topStart = card, bottomEnd = card),
            large = CutCornerShape(topStart = card, bottomEnd = card),
            extraLarge = CutCornerShape(topStart = card + 4.dp, bottomEnd = card + 4.dp),
            card = CutCornerShape(topStart = card, bottomEnd = card),
            button = CutCornerShape(topStart = button, bottomEnd = button),
            chip = CutCornerShape(topStart = chip, bottomEnd = chip),
            field = CutCornerShape(topStart = chip, bottomEnd = chip),
            dialog = CutCornerShape(topStart = card + 4.dp, bottomEnd = card + 4.dp),
            sheet = CutCornerShape(topStart = card + 4.dp, topEnd = card + 4.dp),
            fab = CutCornerShape(topStart = button + 2.dp, bottomEnd = button + 2.dp),
            badge = CircleShape,
            track = CircleShape,
            thumb = CircleShape,
            indicator = CutCornerShape(topStart = chip, bottomEnd = chip),
            dayToggle = CutCornerShape(topStart = chip, bottomEnd = chip),
            checkbox = CutCornerShape(topStart = 4.dp, bottomEnd = 4.dp),
            iconContainer = CutCornerShape(topStart = chip, bottomEnd = chip),
            material = Shapes(
                extraSmall = CutCornerShape(topStart = 4.dp, bottomEnd = 4.dp),
                small = CutCornerShape(topStart = chip, bottomEnd = chip),
                medium = CutCornerShape(topStart = card, bottomEnd = card),
                large = CutCornerShape(topStart = card, bottomEnd = card),
                extraLarge = CutCornerShape(topStart = card + 4.dp, bottomEnd = card + 4.dp),
            ),
        )

        /** 8-bit stepped corners (Pixel). */
        fun pixel(pixel: Dp) = AppShapes(
            extraSmall = PixelCornerShape(pixel, steps = 1),
            small = PixelCornerShape(pixel, steps = 1),
            medium = PixelCornerShape(pixel),
            large = PixelCornerShape(pixel),
            extraLarge = PixelCornerShape(pixel, steps = 3),
            card = PixelCornerShape(pixel),
            button = PixelCornerShape(pixel),
            chip = PixelCornerShape(pixel, steps = 1),
            field = PixelCornerShape(pixel, steps = 1),
            dialog = PixelCornerShape(pixel, steps = 3),
            sheet = PixelCornerShape(pixel, steps = 3, bottom = false),
            fab = PixelCornerShape(pixel),
            badge = PixelCornerShape(pixel / 2, steps = 1),
            track = PixelCornerShape(pixel / 2, steps = 1),
            thumb = RectangleShape,
            indicator = PixelCornerShape(pixel, steps = 1),
            dayToggle = PixelCornerShape(pixel, steps = 1),
            checkbox = PixelCornerShape(pixel / 2, steps = 1),
            iconContainer = PixelCornerShape(pixel),
            material = squareMaterial,
        )

        private val squareMaterial = Shapes(
            extraSmall = RoundedCornerShape(0.dp),
            small = RoundedCornerShape(0.dp),
            medium = RoundedCornerShape(0.dp),
            large = RoundedCornerShape(0.dp),
            extraLarge = RoundedCornerShape(0.dp),
        )
    }
}

/** Fully rounded ends (stadium). */
internal val PillShape: Shape = RoundedCornerShape(50)

/**
 * A rectangle with stepped, pixel-art corners: each corner is cut in [steps] square steps of
 * [pixel] size, like the windows of 8-bit games. [bottom] = false keeps the bottom corners square
 * (bottom sheets).
 */
class PixelCornerShape(
    private val pixel: Dp,
    private val steps: Int = 2,
    private val bottom: Boolean = true,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = with(density) { pixel.toPx() }.let { floor(it).coerceAtLeast(1f) }
        val n = steps.coerceAtLeast(0)
        val maxSteps = floor(min(size.width, size.height) / (2f * p)).toInt()
        val s = min(n, maxSteps)
        if (s == 0) return Outline.Rectangle(size.toRect())
        val w = size.width
        val h = size.height
        val path = Path().apply {
            // Top-left corner, walking clockwise.
            moveTo(0f, s * p)
            for (i in s downTo 1) {
                lineTo((s - i) * p, i * p)
                lineTo((s - i + 1) * p, i * p)
                lineTo((s - i + 1) * p, (i - 1) * p)
            }
            lineTo(w - s * p, 0f)
            // Top-right.
            for (i in 0 until s) {
                lineTo(w - (s - i) * p, (i + 1) * p - p)
                lineTo(w - (s - i) * p, (i + 1) * p)
                lineTo(w - (s - i - 1) * p, (i + 1) * p)
            }
            if (bottom) {
                lineTo(w, h - s * p)
                // Bottom-right.
                for (i in s downTo 1) {
                    lineTo(w - (s - i) * p, h - i * p)
                    lineTo(w - (s - i + 1) * p, h - i * p)
                    lineTo(w - (s - i + 1) * p, h - (i - 1) * p)
                }
                lineTo(s * p, h)
                // Bottom-left.
                for (i in 0 until s) {
                    lineTo((s - i) * p, h - i * p)
                    lineTo((s - i) * p, h - (i + 1) * p)
                    lineTo((s - i - 1) * p, h - (i + 1) * p)
                }
            } else {
                lineTo(w, h)
                lineTo(0f, h)
            }
            close()
        }
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean =
        other is PixelCornerShape && other.pixel == pixel && other.steps == steps && other.bottom == bottom

    override fun hashCode(): Int = (pixel.hashCode() * 31 + steps) * 31 + bottom.hashCode()

    override fun toString(): String = "PixelCornerShape(pixel=$pixel, steps=$steps, bottom=$bottom)"
}

private fun Size.toRect() = androidx.compose.ui.geometry.Rect(0f, 0f, width, height)
