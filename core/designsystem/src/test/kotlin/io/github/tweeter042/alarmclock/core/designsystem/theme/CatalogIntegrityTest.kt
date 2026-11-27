package io.github.tweeter042.alarmclock.core.designsystem.theme

import io.github.tweeter042.alarmclock.core.designsystem.font.BundledFontFiles
import io.github.tweeter042.alarmclock.core.designsystem.font.FontFamilies
import io.github.tweeter042.alarmclock.core.model.font.FontCatalog
import io.github.tweeter042.alarmclock.core.model.theme.ClockFace
import io.github.tweeter042.alarmclock.core.model.theme.DYNAMIC_PALETTE_ID
import io.github.tweeter042.alarmclock.core.model.theme.ThemeCatalog
import io.github.tweeter042.alarmclock.core.model.theme.ThemeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogIntegrityTest {

    @Test
    fun twelveThemesInEnumOrder() {
        assertEquals(12, ThemeCatalog.themes.size)
        assertEquals(ThemeId.entries.toList(), ThemeCatalog.themes.map { it.id })
    }

    @Test
    fun everyThemeHasAtLeastFourUniquePalettes() {
        for (theme in ThemeCatalog.themes) {
            assertTrue("${theme.id} has ${theme.palettes.size} palettes", theme.palettes.size >= 4)
            val ids = theme.palettes.map { it.id }
            assertEquals("${theme.id} duplicate palette ids", ids.size, ids.toSet().size)
            ids.forEach { assertTrue("bad palette id $it", it.matches(Regex("[a-z][a-z0-9_]*"))) }
            assertEquals(theme.palettes.first().id, theme.defaultPaletteId)
        }
    }

    @Test
    fun namesAndDescriptionsAreLocalized() {
        for (theme in ThemeCatalog.themes) {
            assertTrue(theme.nameEn.isNotBlank() && theme.nameTh.isNotBlank())
            assertTrue(theme.descriptionEn.isNotBlank() && theme.descriptionTh.isNotBlank())
            assertTrue("${theme.id} Thai description should contain Thai", theme.descriptionTh.any { it in '฀'..'๿' })
            for (p in theme.palettes) assertTrue("${theme.id}/${p.id}", p.nameEn.isNotBlank() && p.nameTh.isNotBlank())
        }
    }

    @Test
    fun defaultFontsExistAndFitTheirRole() {
        for (theme in ThemeCatalog.themes) {
            val body = FontCatalog.byId(theme.defaultBodyFontId)
            val clock = FontCatalog.byId(theme.defaultClockFontId)
            assertNotNull("${theme.id} body font ${theme.defaultBodyFontId}", body)
            assertNotNull("${theme.id} clock font ${theme.defaultClockFontId}", clock)
            assertTrue("${theme.id} body font must be a body font", body!!.forBody)
            assertTrue("${theme.id} clock font must be a clock font", clock!!.forClock)
            DesignLanguages.of(theme.id).displayFontId?.let {
                assertNotNull("${theme.id} heading font $it", FontCatalog.byId(it))
            }
        }
    }

    @Test
    fun darkOnlyThemesUseOnePaletteForBothModes() {
        val darkOnly = setOf(ThemeId.TERMINAL, ThemeId.NEON, ThemeId.AMOLED)
        for (theme in ThemeCatalog.themes) {
            assertEquals("${theme.id} supportsLight", theme.id !in darkOnly, theme.supportsLight)
            if (!theme.supportsLight) theme.palettes.forEach { assertEquals(it.dark, it.light) }
        }
        ThemeCatalog.theme(ThemeId.AMOLED).palettes.forEach {
            assertEquals("AMOLED must be pure black", 0xFF000000, it.dark.background)
        }
    }

    @Test
    fun clockFacesMatchTheSpec() {
        val expected = mapOf(
            ThemeId.MINIMAL to ClockFace.PLAIN, ThemeId.BRUTAL to ClockFace.PLAIN,
            ThemeId.RETRO_LCD to ClockFace.SEVEN_SEGMENT, ThemeId.FLIP_CLOCK to ClockFace.FLIP,
            ThemeId.GLASS to ClockFace.PLAIN, ThemeId.MATERIAL_YOU to ClockFace.PLAIN,
            ThemeId.TERMINAL to ClockFace.TERMINAL, ThemeId.NEON to ClockFace.NEON_GLOW,
            ThemeId.PAPER to ClockFace.SERIF, ThemeId.PIXEL to ClockFace.PIXEL,
            ThemeId.ANALOG to ClockFace.ANALOG, ThemeId.AMOLED to ClockFace.PLAIN,
        )
        expected.forEach { (id, face) -> assertEquals(id.name, face, ThemeCatalog.theme(id).clockFace) }
    }

    @Test
    fun materialYouOffersWallpaperColours() {
        val my = ThemeCatalog.theme(ThemeId.MATERIAL_YOU)
        assertEquals(DYNAMIC_PALETTE_ID, my.palettes.first().id)
    }

    @Test
    fun paletteLookupFallsBackToDefault() {
        assertEquals("mono", ThemeCatalog.palette(ThemeId.MINIMAL, "does_not_exist").id)
        assertEquals("sage", ThemeCatalog.palette(ThemeId.MINIMAL, "sage").id)
        assertEquals("mono", ThemeCatalog.palette(ThemeId.MINIMAL, null).id)
    }

    @Test
    fun everyCatalogFontIsBundled() {
        val ids = FontCatalog.fonts.map { it.id }
        assertEquals("duplicate font ids", ids.size, ids.toSet().size)
        assertEquals(42, ids.size)
        for (font in FontCatalog.fonts) {
            val files = BundledFontFiles.byFamily[font.id]
            assertNotNull("${font.id} has no bundled files", files)
            assertTrue(files!!.isNotEmpty())
            files.forEach { assertTrue("${font.id}: resource id", it.resId != 0) }
            assertNotNull("${font.id} does not resolve", FontFamilies.bundled(font.id))
        }
        assertEquals("bundled families not in the catalog", ids.toSet(), BundledFontFiles.byFamily.keys)
    }

    @Test
    fun thaiSupportMatchesTheFontFiles() {
        for (font in FontCatalog.fonts) {
            assertEquals(
                "${font.id}: supportsThai must match the cmap check of the download script",
                font.id in BundledFontFiles.thaiCapable,
                font.supportsThai,
            )
        }
    }

    @Test
    fun latinOnlyFontsHaveAThaiPartner() {
        for (font in FontCatalog.fonts.filterNot { it.supportsThai }) {
            val partner = FontFamilies.thaiPartners[font.id]
            assertNotNull("${font.id} needs a Thai partner", partner)
            assertTrue("$partner must support Thai", FontCatalog.byId(partner!!)!!.supportsThai)
        }
        for (font in FontCatalog.fonts.filter { it.supportsThai }) {
            assertFalse("${font.id} supports Thai itself", FontFamilies.thaiPartners.containsKey(font.id))
        }
    }
}
