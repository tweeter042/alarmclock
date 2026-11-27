package io.github.tweeter042.alarmclock.core.designsystem.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.tweeter042.alarmclock.core.model.settings.AccessibilitySettings
import io.github.tweeter042.alarmclock.core.model.settings.DarkModePref
import io.github.tweeter042.alarmclock.core.model.theme.ThemeCatalog
import io.github.tweeter042.alarmclock.core.model.theme.ThemeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TokensTest {

    private val fonts = ResolvedFonts(FontFamily.Default, FontFamily.Serif, FontFamily.Monospace)

    private fun tokens(id: ThemeId, dark: Boolean, a11y: AccessibilitySettings = AccessibilitySettings()): ThemeTokens {
        val palette = ThemeCatalog.theme(id).palettes.first()
        return buildThemeTokens(id, palette.id, if (dark) palette.dark else palette.light, dark, false, fonts, a11y)
    }

    @Test
    fun everyThemeAndPaletteBuildsTokens() {
        for (theme in ThemeCatalog.themes) for (palette in theme.palettes) for (dark in listOf(false, true)) {
            val t = buildThemeTokens(theme.id, palette.id, if (dark) palette.dark else palette.light, dark, false, fonts, AccessibilitySettings())
            assertEquals(theme.clockFace, t.clockFace)
            assertEquals(palette.id, t.paletteId)
        }
    }

    @Test
    fun designLanguagesAreDistinct() {
        // No two themes share the same combination of shape, shadow, background and traits.
        val signatures = ThemeId.entries.map { id ->
            val t = tokens(id, dark = true)
            listOf(t.shapes.card, t.shadow::class, t.background, t.traits.copy(frostAlpha = 0f), t.iconStyle)
        }
        assertEquals(signatures.size, signatures.toSet().size)
    }

    @Test
    fun signatureTraits() {
        assertTrue(tokens(ThemeId.BRUTAL, false).shadow is ShadowSpec.HardOffset)
        assertTrue(tokens(ThemeId.BRUTAL, false).border.width >= 2.dp)
        assertEquals(ShadowSpec.None, tokens(ThemeId.MINIMAL, false).shadow)
        assertTrue(tokens(ThemeId.NEON, true).shadow is ShadowSpec.Glow)
        assertEquals(CardFill.Frosted, tokens(ThemeId.GLASS, false).traits.cardFill)
        assertTrue(tokens(ThemeId.GLASS, false).colors.card.alpha < 1f)
        assertEquals(BackgroundStyle.PureBlack, tokens(ThemeId.AMOLED, true).background)
        assertEquals(4, tokens(ThemeId.PIXEL, false).motion.steps)
        assertTrue(tokens(ThemeId.TERMINAL, true).traits.bracketedLabels)
        assertEquals(HeaderDecor.Masthead, tokens(ThemeId.PAPER, false).traits.headerDecor)
    }

    @Test
    fun reduceMotionSnapsEveryAnimation() {
        for (id in ThemeId.entries) {
            val m = tokens(id, false, AccessibilitySettings(reduceMotion = true)).motion
            assertTrue(m.reduceMotion)
            assertFalse(m.ambientEnabled)
            assertTrue(m.spec<Float>() is SnapSpec)
            val fade = m.fade<Float>() as TweenSpec
            assertTrue(fade.durationMillis <= MotionSpec.REDUCED_MILLIS)
        }
        assertTrue(tokens(ThemeId.MINIMAL, false).motion.spec<Float>() is TweenSpec)
    }

    @Test
    fun thaiUiDropsOpticalRescaling() {
        val palette = ThemeCatalog.theme(ThemeId.PIXEL).palettes.first()
        val latin = buildThemeTokens(ThemeId.PIXEL, palette.id, palette.light, false, false, fonts, AccessibilitySettings())
        val thai = buildThemeTokens(ThemeId.PIXEL, palette.id, palette.light, false, false, fonts, AccessibilitySettings(), thaiUi = true)
        assertTrue(latin.typography.labelLarge.fontSize.value < 12f)
        assertEquals(14f, thai.typography.labelLarge.fontSize.value, 0.01f)
        assertEquals(16f, thai.typography.bodyLarge.fontSize.value, 0.01f)
    }

    @Test
    fun steppedEasingQuantises() {
        val e = SteppedEasing(4, LinearEasing)
        assertEquals(0f, e.transform(0.1f))
        assertEquals(0.25f, e.transform(0.3f))
        assertEquals(0.75f, e.transform(0.99f))
        assertEquals(1f, e.transform(1f))
    }

    @Test
    fun darkModeResolution() {
        val minimal = ThemeCatalog.theme(ThemeId.MINIMAL)
        val amoled = ThemeCatalog.theme(ThemeId.AMOLED)
        assertTrue(resolveDark(amoled, DarkModePref.LIGHT, forceDark = false, systemDark = false))
        assertFalse(resolveDark(minimal, DarkModePref.SYSTEM, null, systemDark = false))
        assertTrue(resolveDark(minimal, DarkModePref.SYSTEM, null, systemDark = true))
        assertTrue(resolveDark(minimal, DarkModePref.DARK, null, systemDark = false))
        assertFalse(resolveDark(minimal, DarkModePref.LIGHT, null, systemDark = true))
        assertTrue(resolveDark(minimal, DarkModePref.LIGHT, forceDark = true, systemDark = false))
    }

    @Test
    fun highContrastStrengthensSecondaryTextAndBorders() {
        for (id in ThemeId.entries) {
            val normal = tokens(id, false)
            val hc = tokens(id, false, AccessibilitySettings(highContrast = true))
            assertTrue(
                "$id secondary text",
                ColorContrast.ratio(hc.colors.onSurfaceVariant, hc.colors.surfaceVariant) >=
                    ColorContrast.ratio(normal.colors.onSurfaceVariant, normal.colors.surfaceVariant) - 0.01f,
            )
            assertTrue("$id border", hc.border.width >= normal.border.width)
            assertEquals(1f, hc.colors.cardBorder.alpha)
        }
    }

    @Test
    fun pixelShapeHasSteppedCorners() {
        val outline = PixelCornerShape(4.dp).createOutline(Size(100f, 60f), LayoutDirection.Ltr, Density(1f))
        assertTrue(outline is Outline.Generic)
        val bounds = (outline as Outline.Generic).path.getBounds()
        assertEquals(0f, bounds.left)
        assertEquals(100f, bounds.right)
        assertEquals(60f, bounds.bottom)
        // Tiny sizes fall back to a rectangle instead of a broken path.
        assertTrue(PixelCornerShape(4.dp).createOutline(Size(5f, 5f), LayoutDirection.Ltr, Density(1f)) is Outline.Rectangle)
        assertEquals(PixelCornerShape(4.dp), PixelCornerShape(4.dp))
        assertNotEquals(PixelCornerShape(4.dp), PixelCornerShape(4.dp, steps = 1))
    }

    @Test
    fun flipDigitsReadableOnCards() {
        for (palette in ThemeCatalog.theme(ThemeId.FLIP_CLOCK).palettes) {
            for (dark in listOf(false, true)) {
                val c = AppColors.fromPalette(if (dark) palette.dark else palette.light, dark)
                assertTrue("${palette.id} dark=$dark", ColorContrast.ratio(c.flipDigit, c.flipCard) >= 4.5f)
            }
        }
    }
}
