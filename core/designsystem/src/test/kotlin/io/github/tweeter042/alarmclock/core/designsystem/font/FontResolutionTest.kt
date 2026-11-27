package io.github.tweeter042.alarmclock.core.designsystem.font

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.ResourceFont
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.tweeter042.alarmclock.core.designsystem.theme.resolveFonts
import io.github.tweeter042.alarmclock.core.model.font.FontCatalog
import io.github.tweeter042.alarmclock.core.model.font.FontIds
import io.github.tweeter042.alarmclock.core.model.theme.ThemeCatalog
import io.github.tweeter042.alarmclock.core.model.theme.ThemeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class FontResolutionTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun specialIds() {
        assertSame(FontFamily.Default, FontFamilies.resolve(context, FontIds.SYSTEM, "prompt"))
        assertSame(FontFamilies.bundled("prompt"), FontFamilies.resolve(context, FontIds.THEME_DEFAULT, "prompt"))
        assertSame(FontFamilies.bundled("kanit"), FontFamilies.resolve(context, "kanit", "prompt"))
    }

    @Test
    fun unknownAndMissingIdsFallBackToTheTheme() {
        assertSame(FontFamilies.bundled("prompt"), FontFamilies.resolve(context, "no_such_font", "prompt"))
        assertSame(FontFamilies.bundled("prompt"), FontFamilies.resolve(context, "custom:missing.ttf", "prompt"))
        assertSame(FontFamilies.bundled("prompt"), FontFamilies.resolve(null, "custom:missing.ttf", "prompt"))
        assertEquals("prompt", FontFamilies.effectiveId(context, FontIds.THEME_DEFAULT, "prompt"))
        assertEquals("prompt", FontFamilies.effectiveId(context, "custom:missing.ttf", "prompt"))
        assertEquals(FontIds.SYSTEM, FontFamilies.effectiveId(context, FontIds.SYSTEM, "prompt"))
        assertEquals("mitr", FontFamilies.effectiveId(context, "mitr", "prompt"))
    }

    @Test
    fun bundledFamiliesContainEveryDownloadedWeight() {
        val prompt = FontFamilies.bundled("prompt") as FontListFontFamily
        assertEquals(listOf(200, 300, 400, 700), prompt.fonts.map { it.weight.weight })
        assertTrue(prompt.fonts.all { it is ResourceFont })
        // Latin-only fonts carry their Thai partner as a per-glyph fallback.
        val inter = FontFamilies.bundled("inter") as FontListFontFamily
        assertEquals(listOf(100, 300, 400, 700), inter.fonts.map { it.weight.weight })
        assertTrue(inter.fonts.all { it is AndroidFont })
        assertSame(FontFamilies.bundled("inter"), FontFamilies.bundled("inter"))
    }

    @Test
    fun everyCatalogFontLoadsATypeface() {
        val resolver = createFontFamilyResolver(context)
        for (font in FontCatalog.fonts) {
            val family = FontFamilies.bundled(font.id)!!
            for (weight in FontFamilies.bundledWeights(font.id)) {
                val typeface = resolver.resolve(family, FontWeight(weight)).value
                assertTrue("${font.id} $weight did not load", typeface is Typeface)
            }
        }
    }

    @Test
    fun pairedTypefacesAreBuiltWithThaiFallback() {
        val resolver = createFontFamilyResolver(context)
        val typeface = resolver.resolve(FontFamilies.bundled("jetbrains_mono")!!, FontWeight.Bold).value as Typeface
        assertNotNull(typeface)
        assertTrue(typeface.weight >= 600)
    }

    @Test
    fun themeHeadingFontIsReplacedByAUserBodyFont() {
        val paper = ThemeCatalog.theme(ThemeId.PAPER)
        val themeFonts = resolveFonts(context, paper, FontIds.THEME_DEFAULT, FontIds.THEME_DEFAULT)
        assertSame(FontFamilies.bundled("playfair_display"), themeFonts.display)
        assertSame(FontFamilies.bundled("taviraj"), themeFonts.body)
        assertSame(FontFamilies.bundled("dm_serif_display"), themeFonts.clock)
        val userFonts = resolveFonts(context, paper, "sarabun", "inter")
        assertSame(FontFamilies.bundled("sarabun"), userFonts.display)
        assertSame(FontFamilies.bundled("sarabun"), userFonts.body)
        assertSame(FontFamilies.bundled("inter"), userFonts.clock)
    }

    @Test
    fun thaiUiSwapsRescaledLatinFontsForTheirPartners() {
        val pixel = ThemeCatalog.theme(ThemeId.PIXEL)
        val latin = resolveFonts(context, pixel, FontIds.THEME_DEFAULT, FontIds.THEME_DEFAULT, thaiUi = false)
        val thai = resolveFonts(context, pixel, FontIds.THEME_DEFAULT, FontIds.THEME_DEFAULT, thaiUi = true)
        assertSame(FontFamilies.bundled("vt323"), latin.body)
        assertSame(FontFamilies.bundled("press_start_2p"), latin.display)
        assertSame(FontFamilies.bundled("chakra_petch"), thai.body)
        assertSame(FontFamilies.bundled("chakra_petch"), thai.display)
        // Not rescaled: Brutal keeps Archivo Black (its Thai partner is weight-matched instead).
        val brutal = resolveFonts(context, ThemeCatalog.theme(ThemeId.BRUTAL), FontIds.THEME_DEFAULT, FontIds.THEME_DEFAULT, thaiUi = true)
        assertSame(FontFamilies.bundled("archivo_black"), brutal.display)
        assertEquals(300, FontFamilies.partnerWeightShift["archivo_black"])
    }
}
