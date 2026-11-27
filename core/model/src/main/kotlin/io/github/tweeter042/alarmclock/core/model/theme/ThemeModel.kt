package io.github.tweeter042.alarmclock.core.model.theme

import kotlinx.serialization.Serializable

/** The twelve design languages. Each changes shapes, borders, shadows, motion and clock face, not just colours. */
@Serializable
enum class ThemeId {
    MINIMAL, BRUTAL, RETRO_LCD, FLIP_CLOCK, GLASS, MATERIAL_YOU,
    TERMINAL, NEON, PAPER, PIXEL, ANALOG, AMOLED,
}

/** How the big clock is drawn. */
@Serializable
enum class ClockFace { PLAIN, SEVEN_SEGMENT, FLIP, ANALOG, PIXEL, TERMINAL, NEON_GLOW, SERIF }

/**
 * Palette colours as ARGB (0xAARRGGBB). Shared with the Wear OS app, so it lives in core:model
 * (no Compose types). core:designsystem maps this to Compose colour schemes.
 */
@Serializable
data class PaletteColors(
    val background: Long,
    val onBackground: Long,
    val surface: Long,
    val onSurface: Long,
    val surfaceVariant: Long,
    val onSurfaceVariant: Long,
    val primary: Long,
    val onPrimary: Long,
    val secondary: Long,
    val onSecondary: Long,
    val accent: Long,
    val onAccent: Long,
    val outline: Long,
    val error: Long,
    val onError: Long,
    val success: Long,
    val warning: Long,
    /** Lit digits of the big clock. */
    val clockDigit: Long,
    /** Unlit segments (Retro LCD) / flip-card back / analog tick marks. */
    val clockGhost: Long,
    /** Hard shadow (Brutal) or soft shadow tint. */
    val shadow: Long,
    /** Glow colour (Neon, Glass highlights). */
    val glow: Long,
)

@Serializable
data class PaletteSpec(
    val id: String,
    val nameEn: String,
    val nameTh: String,
    val light: PaletteColors,
    val dark: PaletteColors,
)

data class ThemeInfo(
    val id: ThemeId,
    val nameEn: String,
    val nameTh: String,
    val descriptionEn: String,
    val descriptionTh: String,
    val clockFace: ClockFace,
    /** At least 4 palettes per theme; first is the default. MATERIAL_YOU uses dynamic colour at runtime. */
    val palettes: List<PaletteSpec>,
    /** FontCatalog ids. */
    val defaultBodyFontId: String,
    val defaultClockFontId: String,
    /** False for themes that are inherently dark (e.g. AMOLED, TERMINAL, NEON): light mode is ignored. */
    val supportsLight: Boolean = true,
) {
    val defaultPaletteId: String get() = palettes.first().id
}

/**
 * The theme catalogue.
 *
 * OWNER: core:designsystem agent replaces [themes] with the full catalogue (all 12 themes,
 * ≥4 palettes each, contrast ≥ 4.5:1 for text pairs). The API of this object is frozen.
 */
object ThemeCatalog {

    val themes: List<ThemeInfo> get() = ThemeDefinitions.all

    fun theme(id: ThemeId): ThemeInfo = themes.first { it.id == id }

    fun palette(themeId: ThemeId, paletteId: String?): PaletteSpec {
        val t = theme(themeId)
        return t.palettes.firstOrNull { it.id == paletteId } ?: t.palettes.first()
    }
}
