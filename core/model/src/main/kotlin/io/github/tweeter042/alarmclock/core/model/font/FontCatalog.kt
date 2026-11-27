package io.github.tweeter042.alarmclock.core.model.font

@Suppress("unused")
enum class FontCategory { SANS, SERIF, MONO, DISPLAY, HANDWRITING, PIXEL }

/**
 * A font the user can choose. Files are bundled in core:designsystem `res/font`
 * (see FontFamilies there). [supportsThai] = the font itself contains Thai glyphs; other fonts
 * fall back to the system Thai font for Thai text.
 */
data class FontInfo(
    val id: String,
    val displayName: String,
    val category: FontCategory,
    val supportsThai: Boolean,
    /** Suitable for the big clock digits. */
    val forClock: Boolean,
    /** Suitable for body/UI text. */
    val forBody: Boolean,
    /** SPDX licence id, shown on the licences screen. */
    val license: String = "OFL-1.1",
)

object FontIds {
    /** Use the font the current theme recommends. */
    const val THEME_DEFAULT = "theme"
    /** The device/system font (Samsung One UI font on Galaxy devices). */
    const val SYSTEM = "system"
    /** Prefix for user-imported fonts: "custom:<fileName>". */
    const val CUSTOM_PREFIX = "custom:"
}

/**
 * OWNER: core:designsystem agent fills [fonts] with every bundled font. API is frozen.
 */
object FontCatalog {
    val fonts: List<FontInfo> get() = FontDefinitions.all

    fun byId(id: String): FontInfo? = fonts.firstOrNull { it.id == id }
}
