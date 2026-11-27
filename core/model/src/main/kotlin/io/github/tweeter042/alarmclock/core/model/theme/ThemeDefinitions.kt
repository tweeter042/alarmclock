package io.github.tweeter042.alarmclock.core.model.theme

import io.github.tweeter042.alarmclock.core.model.font.FontIds

/**
 * PLACEHOLDER catalogue so the project compiles. The core:designsystem agent rewrites this file
 * with the real 12 themes and their palettes.
 */
internal object ThemeDefinitions {

    private fun simple(bg: Long, fg: Long, primary: Long, onPrimary: Long) = PaletteColors(
        background = bg, onBackground = fg,
        surface = bg, onSurface = fg,
        surfaceVariant = bg, onSurfaceVariant = fg,
        primary = primary, onPrimary = onPrimary,
        secondary = primary, onSecondary = onPrimary,
        accent = primary, onAccent = onPrimary,
        outline = fg,
        error = 0xFFB3261E, onError = 0xFFFFFFFF,
        success = 0xFF1B7F3B, warning = 0xFF8A5A00,
        clockDigit = fg, clockGhost = 0x22000000,
        shadow = 0xFF000000, glow = primary,
    )

    private val mono = PaletteSpec(
        id = "mono", nameEn = "Mono", nameTh = "โมโน",
        light = simple(0xFFFFFFFF, 0xFF111111, 0xFF111111, 0xFFFFFFFF),
        dark = simple(0xFF111111, 0xFFF2F2F2, 0xFFF2F2F2, 0xFF111111),
    )

    val all: List<ThemeInfo> = ThemeId.entries.map { id ->
        ThemeInfo(
            id = id,
            nameEn = id.name,
            nameTh = id.name,
            descriptionEn = "",
            descriptionTh = "",
            clockFace = ClockFace.PLAIN,
            palettes = listOf(mono),
            defaultBodyFontId = FontIds.SYSTEM,
            defaultClockFontId = FontIds.SYSTEM,
        )
    }
}
