package io.github.tweeter042.alarmclock.core.designsystem.font

import android.content.Context
import android.content.res.Resources
import android.graphics.Typeface
import android.util.Log
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.tweeter042.alarmclock.core.model.font.FontCatalog
import io.github.tweeter042.alarmclock.core.model.font.FontIds
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import android.graphics.fonts.Font as PlatformFont
import android.graphics.fonts.FontFamily as PlatformFontFamily
import android.graphics.fonts.FontStyle as PlatformFontStyle

/**
 * Turns font ids from settings into Compose [FontFamily]s backed by the bundled TTF files.
 *
 * - FontCatalog ids ("prompt", "ibm_plex_sans_thai", …) -> every bundled weight of that family.
 * - [FontIds.SYSTEM] -> [FontFamily.Default] (the One UI system font on Galaxy devices).
 * - [FontIds.THEME_DEFAULT] -> the theme's default font id (resolved by the caller).
 * - "custom:<file>" -> a user font imported with [CustomFonts] (files/fonts/<file>).
 *
 * Latin-only families get a stylistically matching Thai partner font appended as a per-glyph
 * fallback (Typeface.CustomFallbackBuilder), so Thai text never drops to a random system font.
 */
object FontFamilies {

    private const val TAG = "AlarmClock/Fonts"

    /**
     * Thai partner of every Latin-only bundled family. Chosen for matching proportions and
     * stroke contrast (e.g. Plex Mono pairs with Plex Sans Thai Looped, Playfair with Trirong).
     */
    val thaiPartners: Map<String, String> = mapOf(
        "inter" to "ibm_plex_sans_thai",
        "space_grotesk" to "bai_jamjuree",
        "space_mono" to "ibm_plex_sans_thai_looped",
        "jetbrains_mono" to "ibm_plex_sans_thai_looped",
        "ibm_plex_mono" to "ibm_plex_sans_thai_looped",
        "share_tech_mono" to "chakra_petch",
        "vt323" to "chakra_petch",
        "press_start_2p" to "chakra_petch",
        "silkscreen" to "chakra_petch",
        "orbitron" to "bai_jamjuree",
        "bebas_neue" to "kanit",
        "archivo_black" to "kanit",
        "playfair_display" to "trirong",
        "dm_serif_display" to "chonburi",
        "major_mono_display" to "k2d",
        "monoton" to "mitr",
        "audiowide" to "mitr",
        "oswald" to "kanit",
    )

    /**
     * Heavy display faces exist in a single weight; their Thai partner is shifted up so Thai text
     * is as bold as the Latin glyphs (Archivo Black at 400 pairs with Kanit 700).
     */
    val partnerWeightShift: Map<String, Int> = mapOf(
        "archivo_black" to 300,
        "bebas_neue" to 300,
        "press_start_2p" to 300,
        "audiowide" to 200,
    )

    /** The bundled Thai partner family of a Latin-only font, or null. */
    fun thaiPartner(id: String): FontFamily? = thaiPartners[id]?.let { bundled(it) }

    private val cache = ConcurrentHashMap<String, FontFamily>()

    /** Ids of every bundled family (equals the FontCatalog ids). */
    val bundledIds: Set<String> get() = BundledFontFiles.byFamily.keys

    /** True if [id] names a bundled family. */
    fun isBundled(id: String): Boolean = BundledFontFiles.byFamily.containsKey(id)

    /** Weights bundled for [id] (empty if unknown). */
    fun bundledWeights(id: String): List<Int> = BundledFontFiles.byFamily[id].orEmpty().map { it.weight }

    /**
     * Resolves [id] to a font family. [themeDefaultId] is used for [FontIds.THEME_DEFAULT] and as
     * the fallback for ids that no longer exist (e.g. a deleted custom font).
     */
    fun resolve(context: Context?, id: String, themeDefaultId: String): FontFamily = when {
        id == FontIds.SYSTEM -> FontFamily.Default
        id == FontIds.THEME_DEFAULT -> bundled(themeDefaultId) ?: FontFamily.Default
        id.startsWith(FontIds.CUSTOM_PREFIX) -> custom(context, id) ?: resolve(context, FontIds.THEME_DEFAULT, themeDefaultId)
        else -> bundled(id) ?: resolve(context, FontIds.THEME_DEFAULT, themeDefaultId)
    }

    /** Resolved font id: THEME_DEFAULT and unknown ids map to [themeDefaultId]. */
    fun effectiveId(context: Context?, id: String, themeDefaultId: String): String = when {
        id == FontIds.SYSTEM -> id
        id == FontIds.THEME_DEFAULT -> themeDefaultId
        id.startsWith(FontIds.CUSTOM_PREFIX) -> if (CustomFonts.fileFor(context, id)?.exists() == true) id else themeDefaultId
        isBundled(id) -> id
        else -> themeDefaultId
    }

    /** The bundled family for a FontCatalog id, or null. Thread-safe and cached. */
    fun bundled(id: String): FontFamily? {
        cache[id]?.let { return it }
        val files = BundledFontFiles.byFamily[id] ?: return null
        val partnerFiles = thaiPartners[id]?.let { BundledFontFiles.byFamily[it] }
        val supportsThai = FontCatalog.byId(id)?.supportsThai ?: (id in BundledFontFiles.thaiCapable)
        val family = if (partnerFiles == null || supportsThai) {
            FontFamily(files.map { Font(it.resId, FontWeight(it.weight)) })
        } else {
            val shift = partnerWeightShift[id] ?: 0
            val partner = partnerFiles.map { it.resId to (it.weight - shift).coerceIn(1, 1000) }
            FontFamily(files.map { PairedFont(it.resId, FontWeight(it.weight), partner) })
        }
        return cache.putIfAbsent(id, family) ?: family
    }

    private fun custom(context: Context?, id: String): FontFamily? {
        val file: File = CustomFonts.fileFor(context, id) ?: return null
        if (!file.exists()) return null
        return cache.getOrPut(id) { FontFamily(Font(file, FontWeight.Normal)) }
    }

    /** Forgets cached families (after a custom font was deleted or replaced). */
    fun invalidate(id: String) {
        cache.remove(id)
    }

    /**
     * A bundled Latin font plus its Thai partner as per-glyph fallback, loaded synchronously like a
     * resource font.
     */
    private class PairedFont(
        val resId: Int,
        override val weight: FontWeight,
        val partner: List<Pair<Int, Int>>,
    ) : AndroidFont(FontLoadingStrategy.Blocking, PairedTypefaceLoader, FontVariation.Settings()) {
        override val style: FontStyle = FontStyle.Normal

        override fun equals(other: Any?): Boolean =
            other is PairedFont && other.resId == resId && other.weight == weight && other.partner == partner

        override fun hashCode(): Int = (resId * 31 + weight.hashCode()) * 31 + partner.hashCode()

        override fun toString(): String = "PairedFont(resId=$resId, weight=$weight)"
    }

    private object PairedTypefaceLoader : AndroidFont.TypefaceLoader {
        private val typefaces = ConcurrentHashMap<Pair<Int, Int>, Typeface>()

        override fun loadBlocking(context: Context, font: AndroidFont): Typeface? {
            val paired = font as? PairedFont ?: return null
            val key = paired.resId to paired.weight.weight
            typefaces[key]?.let { return it }
            return try {
                build(context.resources, paired).also { typefaces[key] = it }
            } catch (e: Exception) {
                // Fall back to the plain Latin font (system Thai fallback) rather than failing.
                Log.w(TAG, "Could not build paired typeface", e)
                runCatching { context.resources.getFont(paired.resId) }.getOrNull()
            }
        }

        override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface? =
            loadBlocking(context, font)

        private fun build(res: Resources, font: PairedFont): Typeface {
            val latin = PlatformFontFamily.Builder(
                PlatformFont.Builder(res, font.resId).setWeight(font.weight.weight).build(),
            ).build()
            var thai: PlatformFontFamily.Builder? = null
            for ((resId, weight) in font.partner) {
                val f = PlatformFont.Builder(res, resId).setWeight(weight).build()
                thai = thai?.apply { addFont(f) } ?: PlatformFontFamily.Builder(f)
            }
            return Typeface.CustomFallbackBuilder(latin)
                .apply { thai?.let { addCustomFallback(it.build()) } }
                .setSystemFallback("sans-serif")
                .setStyle(PlatformFontStyle(font.weight.weight, PlatformFontStyle.FONT_SLANT_UPRIGHT))
                .build()
        }
    }
}
