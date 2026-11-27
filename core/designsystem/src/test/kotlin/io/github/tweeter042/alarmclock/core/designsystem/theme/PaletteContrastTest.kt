package io.github.tweeter042.alarmclock.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import io.github.tweeter042.alarmclock.core.model.theme.PaletteColors
import io.github.tweeter042.alarmclock.core.model.theme.ThemeCatalog
import io.github.tweeter042.alarmclock.core.model.theme.ThemeId
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every palette of every theme, light and dark, must keep text readable (WCAG AA 4.5:1 for text
 * pairs, 3:1 for the large clock digits).
 */
class PaletteContrastTest {

    private data class Pair(val name: String, val fg: Long, val bg: Long, val min: Float)

    private fun pairs(c: PaletteColors) = listOf(
        Pair("onBackground/background", c.onBackground, c.background, 4.5f),
        Pair("onSurface/surface", c.onSurface, c.surface, 4.5f),
        Pair("onSurfaceVariant/surfaceVariant", c.onSurfaceVariant, c.surfaceVariant, 4.5f),
        Pair("onPrimary/primary", c.onPrimary, c.primary, 4.5f),
        Pair("onSecondary/secondary", c.onSecondary, c.secondary, 4.5f),
        Pair("onAccent/accent", c.onAccent, c.accent, 4.5f),
        Pair("onError/error", c.onError, c.error, 4.5f),
        Pair("clockDigit/background", c.clockDigit, c.background, 3f),
    )

    private fun forEachPalette(block: (label: String, theme: ThemeId, colors: PaletteColors, dark: Boolean) -> Unit) {
        for (theme in ThemeCatalog.themes) {
            for (palette in theme.palettes) {
                block("${theme.id}/${palette.id}/light", theme.id, palette.light, false)
                block("${theme.id}/${palette.id}/dark", theme.id, palette.dark, true)
            }
        }
    }

    @Test
    fun textPairsMeetWcagAa() {
        val failures = mutableListOf<String>()
        forEachPalette { label, _, colors, _ ->
            for (p in pairs(colors)) {
                val ratio = ColorContrast.ratio(p.fg, p.bg)
                if (ratio < p.min) failures += "$label ${p.name} = ${"%.2f".format(ratio)} (< ${p.min})"
            }
        }
        assertTrue("Contrast failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun testedRolesAreOpaque() {
        val failures = mutableListOf<String>()
        forEachPalette { label, _, c, _ ->
            val roles = listOf(
                "background" to c.background, "onBackground" to c.onBackground, "surface" to c.surface,
                "onSurface" to c.onSurface, "surfaceVariant" to c.surfaceVariant,
                "onSurfaceVariant" to c.onSurfaceVariant, "primary" to c.primary, "onPrimary" to c.onPrimary,
                "secondary" to c.secondary, "onSecondary" to c.onSecondary, "accent" to c.accent,
                "onAccent" to c.onAccent, "error" to c.error, "onError" to c.onError, "clockDigit" to c.clockDigit,
            )
            for ((name, value) in roles) {
                if ((value ushr 24) != 0xFFL) failures += "$label $name is not opaque"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun derivedRolesStayReadable() {
        val failures = mutableListOf<String>()
        forEachPalette { label, theme, palette, dark ->
            val colors = AppColors.fromPalette(palette, dark)
            fun check(name: String, fg: Color, bg: Color, min: Float) {
                val r = ColorContrast.ratio(fg, bg)
                if (r < min) failures += "$label $name = ${"%.2f".format(r)}"
            }
            check("onPrimaryContainer/primaryContainer", colors.onPrimaryContainer, colors.primaryContainer, 4.5f)
            check("flipDigit/flipCard", colors.flipDigit, colors.flipCard, if (theme == ThemeId.FLIP_CLOCK) 4.5f else 1f)
            // Secondary text is also shown on cards and on the page background.
            check("onSurfaceVariant/surface", colors.onSurfaceVariant, colors.surface, 4.5f)
            check("onSurfaceVariant/background", colors.onSurfaceVariant, colors.background, 4.5f)
            // Icons and status colours on the page (non-text, 3:1). Brutal, Pixel and Neon always
            // outline primary fills, so the fill itself may sit close to the page colour.
            if (!DesignLanguages.of(theme).outlineFilledControls) {
                check("primary/background", colors.primary, colors.background, 3f)
            }
            check("success/background", colors.success, colors.background, 3f)
            check("warning/background", colors.warning, colors.background, 3f)
            check("error/background", colors.error, colors.background, 3f)
        }
        assertTrue("Derived contrast failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun highContrastKeepsTextPairs() {
        val failures = mutableListOf<String>()
        forEachPalette { label, _, palette, dark ->
            val colors = AppColors.fromPalette(palette, dark).highContrast()
            val r = ColorContrast.ratio(colors.onSurfaceVariant, colors.surfaceVariant)
            if (r < 7f) failures += "$label high-contrast secondary text = ${"%.2f".format(r)}"
            val d = ColorContrast.ratio(colors.clockDigit, colors.background)
            if (d < 4.5f) failures += "$label high-contrast clock = ${"%.2f".format(d)}"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
