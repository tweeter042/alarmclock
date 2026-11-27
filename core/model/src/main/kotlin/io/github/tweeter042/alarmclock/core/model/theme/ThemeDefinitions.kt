package io.github.tweeter042.alarmclock.core.model.theme

import kotlin.math.roundToInt

/**
 * Palette id of the Material You palette that follows the wallpaper. core:designsystem replaces
 * its colours with `dynamicLight/DarkColorScheme` at runtime; the static colours stored here are
 * the fallback (devices without dynamic colour, the Wear OS app, previews).
 */
const val DYNAMIC_PALETTE_ID: String = "dynamic"

/**
 * The full theme catalogue: 12 design languages, each with ≥ 4 palettes in light and dark.
 *
 * Rules every palette follows (enforced by the contrast unit test in core:designsystem):
 * - text pairs (on* / *) have a WCAG contrast ratio of at least 4.5:1,
 * - clockDigit on background has at least 3:1 (large text),
 * - all tested roles are opaque. clockGhost / shadow may carry alpha.
 *
 * Per-theme builders below encode each design language (e.g. Brutal derives outline and hard
 * shadow from one "line" colour, Terminal derives everything from one phosphor colour) so
 * palettes only state the colours that make them distinct.
 */
internal object ThemeDefinitions {

    val all: List<ThemeInfo> by lazy {
        listOf(
            minimal(), brutal(), retroLcd(), flipClock(), glass(), materialYou(),
            terminal(), neon(), paper(), pixel(), analog(), amoled(),
        )
    }

    // -----------------------------------------------------------------------------------------
    // MINIMAL: airy, thin type, soft corners, no shadows.
    // -----------------------------------------------------------------------------------------

    private fun minimal() = ThemeInfo(
        id = ThemeId.MINIMAL,
        nameEn = "Minimal",
        nameTh = "มินิมอล",
        descriptionEn = "Airy space, thin type and soft corners.",
        descriptionTh = "โล่ง สบายตา ตัวอักษรบาง มุมโค้งนุ่ม",
        clockFace = ClockFace.PLAIN,
        defaultBodyFontId = "ibm_plex_sans_thai",
        defaultClockFontId = "inter",
        palettes = listOf(
            PaletteSpec(
                id = "mono", nameEn = "Mono", nameTh = "โมโน",
                light = minimalColors(
                    dark = false, background = 0xFFF7F7F5, surface = 0xFFFFFFFF, surfaceVariant = 0xFFEDEDEA,
                    ink = 0xFF141414, inkMuted = 0xFF5F5F5C,
                    primary = 0xFF141414, onPrimary = 0xFFFFFFFF,
                    secondary = 0xFFE4E4E0, onSecondary = 0xFF141414,
                    accent = 0xFFE5512D, onAccent = 0xFF141414,
                ),
                dark = minimalColors(
                    dark = true, background = 0xFF0F0F0F, surface = 0xFF181818, surfaceVariant = 0xFF232323,
                    ink = 0xFFF0F0EE, inkMuted = 0xFFA6A6A3,
                    primary = 0xFFF0F0EE, onPrimary = 0xFF141414,
                    secondary = 0xFF2C2C2C, onSecondary = 0xFFF0F0EE,
                    accent = 0xFFFF7650, onAccent = 0xFF141414,
                ),
            ),
            PaletteSpec(
                id = "sand", nameEn = "Sand", nameTh = "ทราย",
                light = minimalColors(
                    dark = false, background = 0xFFF6F1E9, surface = 0xFFFFFCF7, surfaceVariant = 0xFFECE4D7,
                    ink = 0xFF2A241C, inkMuted = 0xFF665A4A,
                    primary = 0xFF7D5F3C, onPrimary = 0xFFFFFFFF,
                    secondary = 0xFFE6DBC9, onSecondary = 0xFF2A241C,
                    accent = 0xFFA9572F, onAccent = 0xFFFFFFFF,
                ),
                dark = minimalColors(
                    dark = true, background = 0xFF181512, surface = 0xFF221E19, surfaceVariant = 0xFF2D2822,
                    ink = 0xFFF1E9DD, inkMuted = 0xFFBBAE9B,
                    primary = 0xFFDDBF95, onPrimary = 0xFF2A2016,
                    secondary = 0xFF3A332A, onSecondary = 0xFFF1E9DD,
                    accent = 0xFFE5946B, onAccent = 0xFF2A1A10,
                ),
            ),
            PaletteSpec(
                id = "sage", nameEn = "Sage", nameTh = "เขียวเซจ",
                light = minimalColors(
                    dark = false, background = 0xFFF3F5F0, surface = 0xFFFFFFFF, surfaceVariant = 0xFFE6EBE2,
                    ink = 0xFF1D2820, inkMuted = 0xFF56635A,
                    primary = 0xFF46664F, onPrimary = 0xFFFFFFFF,
                    secondary = 0xFFDCE5D8, onSecondary = 0xFF1D2820,
                    accent = 0xFF8E5F2A, onAccent = 0xFFFFFFFF,
                ),
                dark = minimalColors(
                    dark = true, background = 0xFF121612, surface = 0xFF1A201B, surfaceVariant = 0xFF242C25,
                    ink = 0xFFE6EDE4, inkMuted = 0xFFA4B2A6,
                    primary = 0xFFA9C7AE, onPrimary = 0xFF142018,
                    secondary = 0xFF2E382F, onSecondary = 0xFFE6EDE4,
                    accent = 0xFFE0B07A, onAccent = 0xFF2A1C0C,
                ),
            ),
            PaletteSpec(
                id = "ocean", nameEn = "Ocean", nameTh = "มหาสมุทร",
                light = minimalColors(
                    dark = false, background = 0xFFF1F5F8, surface = 0xFFFFFFFF, surfaceVariant = 0xFFE1EAF0,
                    ink = 0xFF0F2130, inkMuted = 0xFF4B6070,
                    primary = 0xFF1D5A85, onPrimary = 0xFFFFFFFF,
                    secondary = 0xFFD6E4EE, onSecondary = 0xFF0F2130,
                    accent = 0xFF0E7C76, onAccent = 0xFFFFFFFF,
                ),
                dark = minimalColors(
                    dark = true, background = 0xFF0D141A, surface = 0xFF142029, surfaceVariant = 0xFF1D2B36,
                    ink = 0xFFE3EDF4, inkMuted = 0xFF9DB2C1,
                    primary = 0xFF8CC4EC, onPrimary = 0xFF0B2236,
                    secondary = 0xFF26374A, onSecondary = 0xFFE3EDF4,
                    accent = 0xFF6FD6CC, onAccent = 0xFF062A27,
                ),
            ),
            PaletteSpec(
                id = "rose", nameEn = "Rose", nameTh = "กุหลาบ",
                light = minimalColors(
                    dark = false, background = 0xFFFAF4F3, surface = 0xFFFFFFFF, surfaceVariant = 0xFFF2E4E3,
                    ink = 0xFF2E1D20, inkMuted = 0xFF735A5E,
                    primary = 0xFF9C4A57, onPrimary = 0xFFFFFFFF,
                    secondary = 0xFFEFDCDC, onSecondary = 0xFF2E1D20,
                    accent = 0xFFA2533A, onAccent = 0xFFFFFFFF,
                ),
                dark = minimalColors(
                    dark = true, background = 0xFF171112, surface = 0xFF21181A, surfaceVariant = 0xFF2D2124,
                    ink = 0xFFF5E6E7, inkMuted = 0xFFC2A7AB,
                    primary = 0xFFF0A9B4, onPrimary = 0xFF3A1820,
                    secondary = 0xFF3B2B2F, onSecondary = 0xFFF5E6E7,
                    accent = 0xFFE8A089, onAccent = 0xFF3A1A10,
                ),
            ),
            PaletteSpec(
                id = "slate", nameEn = "Slate", nameTh = "หินชนวน",
                light = minimalColors(
                    dark = false, background = 0xFFF4F5F7, surface = 0xFFFFFFFF, surfaceVariant = 0xFFE5E8EC,
                    ink = 0xFF111827, inkMuted = 0xFF4B5563,
                    primary = 0xFF334155, onPrimary = 0xFFFFFFFF,
                    secondary = 0xFFDDE2E9, onSecondary = 0xFF111827,
                    accent = 0xFF4F46E5, onAccent = 0xFFFFFFFF,
                ),
                dark = minimalColors(
                    dark = true, background = 0xFF0E1116, surface = 0xFF161B22, surfaceVariant = 0xFF212833,
                    ink = 0xFFE6EAF0, inkMuted = 0xFF9AA4B2,
                    primary = 0xFFCBD5E1, onPrimary = 0xFF111827,
                    secondary = 0xFF2A3340, onSecondary = 0xFFE6EAF0,
                    accent = 0xFFA5B4FC, onAccent = 0xFF1E1B4B,
                ),
            ),
        ),
    )

    private fun minimalColors(
        dark: Boolean, background: Long, surface: Long, surfaceVariant: Long,
        ink: Long, inkMuted: Long, primary: Long, onPrimary: Long,
        secondary: Long, onSecondary: Long, accent: Long, onAccent: Long,
    ) = colors(
        dark = dark, background = background, onBackground = ink, surface = surface,
        surfaceVariant = surfaceVariant, onSurfaceVariant = inkMuted,
        primary = primary, onPrimary = onPrimary, secondary = secondary, onSecondary = onSecondary,
        accent = accent, onAccent = onAccent,
        outline = mix(background, ink, if (dark) 0.24f else 0.18f),
        clockGhost = withAlpha(ink, 0.07f),
        shadow = withAlpha(BLACK, if (dark) 0.40f else 0.06f),
        glow = accent,
    )

    // -----------------------------------------------------------------------------------------
    // BRUTAL: neo-brutalism. One "line" colour drives borders and the hard offset shadow.
    // -----------------------------------------------------------------------------------------

    private fun brutal() = ThemeInfo(
        id = ThemeId.BRUTAL,
        nameEn = "Brutal",
        nameTh = "บรูทัล",
        descriptionEn = "Thick black outlines, hard shadows and loud colours.",
        descriptionTh = "เส้นขอบดำหนา เงาทึบ สีสดจัดจ้าน",
        clockFace = ClockFace.PLAIN,
        defaultBodyFontId = "bai_jamjuree",
        defaultClockFontId = "space_mono",
        palettes = listOf(
            PaletteSpec(
                id = "yellow_black", nameEn = "Yellow / Black", nameTh = "เหลือง / ดำ",
                light = brutalColors(
                    dark = false, background = 0xFFFFD23F, surface = 0xFFFFFFFF, surfaceVariant = 0xFFFFF1B8,
                    ink = 0xFF0A0A0A, inkMuted = 0xFF3D3D3D, line = 0xFF0A0A0A,
                    primary = 0xFF0A0A0A, onPrimary = 0xFFFFD23F,
                    secondary = 0xFFFFFFFF, onSecondary = 0xFF0A0A0A,
                    accent = 0xFFFF5C8A, onAccent = 0xFF0A0A0A,
                    clockDigit = 0xFF0A0A0A,
                ),
                dark = brutalColors(
                    dark = true, background = 0xFF121212, surface = 0xFF1E1E1E, surfaceVariant = 0xFF2A2A2A,
                    ink = 0xFFFAFAFA, inkMuted = 0xFFC8C8C8, line = 0xFFFAFAFA,
                    primary = 0xFFFFD23F, onPrimary = 0xFF0A0A0A,
                    secondary = 0xFF2A2A2A, onSecondary = 0xFFFAFAFA,
                    accent = 0xFFFF5C8A, onAccent = 0xFF0A0A0A,
                    clockDigit = 0xFFFFD23F,
                ),
            ),
            PaletteSpec(
                id = "pink_black", nameEn = "Pink / Black", nameTh = "ชมพู / ดำ",
                light = brutalColors(
                    dark = false, background = 0xFFFF90E8, surface = 0xFFFFFFFF, surfaceVariant = 0xFFFFD6F6,
                    ink = 0xFF000000, inkMuted = 0xFF3A3A3A, line = 0xFF000000,
                    primary = 0xFF000000, onPrimary = 0xFFFF90E8,
                    secondary = 0xFFFFFFFF, onSecondary = 0xFF000000,
                    accent = 0xFFFFC900, onAccent = 0xFF000000,
                    clockDigit = 0xFF000000,
                ),
                dark = brutalColors(
                    dark = true, background = 0xFF141014, surface = 0xFF201A20, surfaceVariant = 0xFF2C232C,
                    ink = 0xFFFFF5FD, inkMuted = 0xFFD6C3D3, line = 0xFFFFF5FD,
                    primary = 0xFFFF90E8, onPrimary = 0xFF000000,
                    secondary = 0xFF2C232C, onSecondary = 0xFFFFF5FD,
                    accent = 0xFFFFC900, onAccent = 0xFF000000,
                    clockDigit = 0xFFFF90E8,
                ),
            ),
            PaletteSpec(
                id = "lime_purple", nameEn = "Lime / Purple", nameTh = "เขียวมะนาว / ม่วง",
                light = brutalColors(
                    dark = false, background = 0xFFCFFF4A, surface = 0xFFFFFFFF, surfaceVariant = 0xFFEEFFC2,
                    ink = 0xFF1A0B33, inkMuted = 0xFF4A3A66, line = 0xFF1A0B33,
                    primary = 0xFF6A2BFF, onPrimary = 0xFFFFFFFF,
                    secondary = 0xFFFFFFFF, onSecondary = 0xFF1A0B33,
                    accent = 0xFFFF6B35, onAccent = 0xFF1A0B33,
                    clockDigit = 0xFF1A0B33,
                ),
                dark = brutalColors(
                    dark = true, background = 0xFF1A0B33, surface = 0xFF26124A, surfaceVariant = 0xFF331A5E,
                    ink = 0xFFF4FFE0, inkMuted = 0xFFCFC2E6, line = 0xFFF4FFE0,
                    primary = 0xFFCFFF4A, onPrimary = 0xFF1A0B33,
                    secondary = 0xFF331A5E, onSecondary = 0xFFF4FFE0,
                    accent = 0xFFFF8A5C, onAccent = 0xFF1A0B33,
                    clockDigit = 0xFFCFFF4A,
                ),
            ),
            PaletteSpec(
                id = "blue_orange", nameEn = "Blue / Orange", nameTh = "น้ำเงิน / ส้ม",
                light = brutalColors(
                    dark = false, background = 0xFF7FB2FF, surface = 0xFFFFFFFF, surfaceVariant = 0xFFDCEAFF,
                    ink = 0xFF0A0F1F, inkMuted = 0xFF33405C, line = 0xFF0A0F1F,
                    primary = 0xFFFF6A13, onPrimary = 0xFF0A0F1F,
                    secondary = 0xFFFFFFFF, onSecondary = 0xFF0A0F1F,
                    accent = 0xFF1F3FFF, onAccent = 0xFFFFFFFF,
                    clockDigit = 0xFF0A0F1F,
                ),
                dark = brutalColors(
                    dark = true, background = 0xFF0B1A3D, surface = 0xFF13275A, surfaceVariant = 0xFF1B3470,
                    ink = 0xFFF0F5FF, inkMuted = 0xFFC2D0EE, line = 0xFFF0F5FF,
                    primary = 0xFFFF7A1F, onPrimary = 0xFF0A0F1F,
                    secondary = 0xFF1B3470, onSecondary = 0xFFF0F5FF,
                    accent = 0xFF7FB2FF, onAccent = 0xFF0A0F1F,
                    clockDigit = 0xFFFF7A1F,
                ),
            ),
            PaletteSpec(
                id = "red_cream", nameEn = "Red / Cream", nameTh = "แดง / ครีม",
                light = brutalColors(
                    dark = false, background = 0xFFFFF0D6, surface = 0xFFFFFFFF, surfaceVariant = 0xFFFFE2B3,
                    ink = 0xFF1C0A0A, inkMuted = 0xFF5A3A32, line = 0xFF1C0A0A,
                    primary = 0xFFD7263D, onPrimary = 0xFFFFFFFF,
                    secondary = 0xFFFFFFFF, onSecondary = 0xFF1C0A0A,
                    accent = 0xFFFFB703, onAccent = 0xFF1C0A0A,
                    clockDigit = 0xFFD7263D,
                ),
                dark = brutalColors(
                    dark = true, background = 0xFF240B0E, surface = 0xFF33121A, surfaceVariant = 0xFF431925,
                    ink = 0xFFFFF0D6, inkMuted = 0xFFE0C3AE, line = 0xFFFFF0D6,
                    primary = 0xFFFF5A5F, onPrimary = 0xFF1C0A0A,
                    secondary = 0xFF431925, onSecondary = 0xFFFFF0D6,
                    accent = 0xFFFFB703, onAccent = 0xFF1C0A0A,
                    clockDigit = 0xFFFF5A5F,
                ),
            ),
        ),
    )

    private fun brutalColors(
        dark: Boolean, background: Long, surface: Long, surfaceVariant: Long,
        ink: Long, inkMuted: Long, line: Long, primary: Long, onPrimary: Long,
        secondary: Long, onSecondary: Long, accent: Long, onAccent: Long, clockDigit: Long,
    ) = colors(
        dark = dark, background = background, onBackground = ink, surface = surface,
        surfaceVariant = surfaceVariant, onSurfaceVariant = inkMuted,
        primary = primary, onPrimary = onPrimary, secondary = secondary, onSecondary = onSecondary,
        accent = accent, onAccent = onAccent,
        outline = line,
        error = if (dark) 0xFFFF6B6B else 0xFFA50E1A,
        onError = if (dark) 0xFF0A0A0A else 0xFFFFFFFF,
        success = if (dark) 0xFF4ADE80 else 0xFF14532D,
        warning = if (dark) 0xFFFFC94A else 0xFF7A4B00,
        clockDigit = clockDigit,
        clockGhost = withAlpha(line, 0.10f),
        shadow = line,
        glow = accent,
    )

    // -----------------------------------------------------------------------------------------
    // RETRO LCD: light = reflective LCD panel with dark segments, dark = backlit display.
    // -----------------------------------------------------------------------------------------

    private fun retroLcd() = ThemeInfo(
        id = ThemeId.RETRO_LCD,
        nameEn = "Retro LCD",
        nameTh = "จอ LCD ย้อนยุค",
        descriptionEn = "Seven-segment digits from an 80s digital watch.",
        descriptionTh = "ตัวเลข 7 ส่วนแบบนาฬิกาดิจิทัลยุค 80",
        clockFace = ClockFace.SEVEN_SEGMENT,
        defaultBodyFontId = "chakra_petch",
        defaultClockFontId = "share_tech_mono",
        palettes = listOf(
            PaletteSpec(
                id = "classic_green", nameEn = "Classic Green", nameTh = "เขียวคลาสสิก",
                light = lcdColors(
                    dark = false, background = 0xFFC2CCA7, surface = 0xFFCED7B6, surfaceVariant = 0xFFB3BE95,
                    ink = 0xFF1F2A17, inkMuted = 0xFF3A472E,
                    primary = 0xFF2B3820, onPrimary = 0xFFCED7B6,
                    accent = 0xFFA33B20, onAccent = 0xFFF1EBD9,
                    digit = 0xFF1C2614,
                ),
                dark = lcdColors(
                    dark = true, background = 0xFF08140C, surface = 0xFF0F1F14, surfaceVariant = 0xFF16291B,
                    ink = 0xFFA8F2B0, inkMuted = 0xFF6FBF7C,
                    primary = 0xFF7CE38B, onPrimary = 0xFF08140C,
                    accent = 0xFFE3C76B, onAccent = 0xFF08140C,
                    digit = 0xFF8BF59A,
                ),
            ),
            PaletteSpec(
                id = "amber", nameEn = "Amber", nameTh = "อำพัน",
                light = lcdColors(
                    dark = false, background = 0xFFF2C66D, surface = 0xFFF7D58F, surfaceVariant = 0xFFE6B456,
                    ink = 0xFF2A1A00, inkMuted = 0xFF573B00,
                    primary = 0xFF2A1A00, onPrimary = 0xFFF7D58F,
                    accent = 0xFF8C2F00, onAccent = 0xFFF7D58F,
                    digit = 0xFF2A1A00,
                ),
                dark = lcdColors(
                    dark = true, background = 0xFF120B02, surface = 0xFF1C1205, surfaceVariant = 0xFF271A08,
                    ink = 0xFFFFC24A, inkMuted = 0xFFCC9330,
                    primary = 0xFFFFB000, onPrimary = 0xFF120B02,
                    accent = 0xFFFF7A1A, onAccent = 0xFF120B02,
                    digit = 0xFFFFB000,
                ),
            ),
            PaletteSpec(
                id = "blue_backlight", nameEn = "Blue Backlight", nameTh = "แบ็กไลต์สีน้ำเงิน",
                light = lcdColors(
                    dark = false, background = 0xFFB9D3EE, surface = 0xFFC9DDF3, surfaceVariant = 0xFFA7C4E4,
                    ink = 0xFF0B2547, inkMuted = 0xFF2C4A72,
                    primary = 0xFF0B2547, onPrimary = 0xFFC9DDF3,
                    accent = 0xFFB33A0B, onAccent = 0xFFFFFFFF,
                    digit = 0xFF0B2547,
                ),
                dark = lcdColors(
                    dark = true, background = 0xFF0A2A66, surface = 0xFF0F3478, surfaceVariant = 0xFF15408C,
                    ink = 0xFFE6F3FF, inkMuted = 0xFFB4D4F5,
                    primary = 0xFFE6F3FF, onPrimary = 0xFF0A2A66,
                    accent = 0xFF7FE7FF, onAccent = 0xFF0A2A66,
                    digit = 0xFFEAF6FF,
                ),
            ),
            PaletteSpec(
                id = "grey", nameEn = "Grey", nameTh = "เทา",
                light = lcdColors(
                    dark = false, background = 0xFFC8CBC3, surface = 0xFFD5D8D0, surfaceVariant = 0xFFB9BDB3,
                    ink = 0xFF1B1D1A, inkMuted = 0xFF3F433C,
                    primary = 0xFF1B1D1A, onPrimary = 0xFFD5D8D0,
                    accent = 0xFF9E2B25, onAccent = 0xFFF2F2EE,
                    digit = 0xFF1B1D1A,
                ),
                dark = lcdColors(
                    dark = true, background = 0xFF121311, surface = 0xFF1B1C1A, surfaceVariant = 0xFF252723,
                    ink = 0xFFD6D9D1, inkMuted = 0xFF9EA299,
                    primary = 0xFFD6D9D1, onPrimary = 0xFF121311,
                    accent = 0xFFFF8A65, onAccent = 0xFF121311,
                    digit = 0xFFDADDD4,
                ),
            ),
        ),
    )

    private fun lcdColors(
        dark: Boolean, background: Long, surface: Long, surfaceVariant: Long,
        ink: Long, inkMuted: Long, primary: Long, onPrimary: Long,
        accent: Long, onAccent: Long, digit: Long,
    ) = colors(
        dark = dark, background = background, onBackground = ink, surface = surface,
        surfaceVariant = surfaceVariant, onSurfaceVariant = inkMuted,
        primary = primary, onPrimary = onPrimary,
        secondary = surfaceVariant, onSecondary = ink,
        accent = accent, onAccent = onAccent,
        outline = mix(background, ink, if (dark) 0.40f else 0.50f),
        error = if (dark) 0xFFFF7A6B else 0xFF8E1B12,
        onError = if (dark) 0xFF120B02 else 0xFFFFFFFF,
        success = if (dark) ink else 0xFF1F4D1A,
        warning = if (dark) 0xFFFFC24A else 0xFF6B4300,
        clockDigit = digit,
        // Unlit segments: barely visible on a reflective panel, slightly stronger when backlit.
        clockGhost = withAlpha(digit, if (dark) 0.09f else 0.11f),
        shadow = withAlpha(BLACK, if (dark) 0.55f else 0.18f),
        glow = digit,
    )

    // -----------------------------------------------------------------------------------------
    // FLIP CLOCK: clockGhost is the opaque colour of the flip cards.
    // -----------------------------------------------------------------------------------------

    private fun flipClock() = ThemeInfo(
        id = ThemeId.FLIP_CLOCK,
        nameEn = "Flip Clock",
        nameTh = "นาฬิกาแผ่นพลิก",
        descriptionEn = "Split-flap cards that flip as the minutes pass.",
        descriptionTh = "ตัวเลขบนแผ่นพลิกที่พลิกเปลี่ยนทุกนาที",
        clockFace = ClockFace.FLIP,
        defaultBodyFontId = "kanit",
        defaultClockFontId = "oswald",
        palettes = listOf(
            PaletteSpec(
                id = "classic_black", nameEn = "Classic Black", nameTh = "ดำคลาสสิก",
                light = flipColors(
                    dark = false, background = 0xFFE9E6E1, surface = 0xFFF5F3EF, surfaceVariant = 0xFFDCD8D1,
                    ink = 0xFF161616, inkMuted = 0xFF55524D,
                    primary = 0xFF161616, onPrimary = 0xFFF5F3EF,
                    accent = 0xFFC4441F, onAccent = 0xFFFFFFFF,
                    card = 0xFF1B1B1B,
                ),
                dark = flipColors(
                    dark = true, background = 0xFF0B0B0B, surface = 0xFF161616, surfaceVariant = 0xFF222222,
                    ink = 0xFFEDEDED, inkMuted = 0xFFA3A3A3,
                    primary = 0xFFEDEDED, onPrimary = 0xFF161616,
                    accent = 0xFFFF7043, onAccent = 0xFF161616,
                    card = 0xFF1E1E1E,
                ),
            ),
            PaletteSpec(
                id = "white", nameEn = "White", nameTh = "ขาว",
                light = flipColors(
                    dark = false, background = 0xFFD9D9D6, surface = 0xFFF2F2F0, surfaceVariant = 0xFFCACAC6,
                    ink = 0xFF151515, inkMuted = 0xFF4E4E4B,
                    primary = 0xFF151515, onPrimary = 0xFFFFFFFF,
                    accent = 0xFF2A64D8, onAccent = 0xFFFFFFFF,
                    card = 0xFFFDFDFB,
                ),
                dark = flipColors(
                    dark = true, background = 0xFF1A1A1A, surface = 0xFF242424, surfaceVariant = 0xFF303030,
                    ink = 0xFFF2F2F2, inkMuted = 0xFFABABAB,
                    primary = 0xFFF2F2F2, onPrimary = 0xFF151515,
                    accent = 0xFF7FA8FF, onAccent = 0xFF151515,
                    card = 0xFFEDEDEA,
                ),
            ),
            PaletteSpec(
                id = "walnut", nameEn = "Walnut", nameTh = "ไม้วอลนัท",
                light = flipColors(
                    dark = false, background = 0xFFEFE6DA, surface = 0xFFF8F2EA, surfaceVariant = 0xFFE3D6C5,
                    ink = 0xFF2B1D12, inkMuted = 0xFF645040,
                    primary = 0xFF5C3A21, onPrimary = 0xFFFFF7EE,
                    accent = 0xFFA3561A, onAccent = 0xFFFFFFFF,
                    card = 0xFF4A2F1D,
                ),
                dark = flipColors(
                    dark = true, background = 0xFF1A120C, surface = 0xFF241912, surfaceVariant = 0xFF30221A,
                    ink = 0xFFF3E6D6, inkMuted = 0xFFC2AD96,
                    primary = 0xFFD9A86C, onPrimary = 0xFF2B1D12,
                    accent = 0xFFE8915A, onAccent = 0xFF2B1D12,
                    card = 0xFF3A2618,
                ),
            ),
            PaletteSpec(
                id = "midnight_blue", nameEn = "Midnight Blue", nameTh = "น้ำเงินเที่ยงคืน",
                light = flipColors(
                    dark = false, background = 0xFFE6EAF2, surface = 0xFFF4F6FA, surfaceVariant = 0xFFD6DCE8,
                    ink = 0xFF0E1A33, inkMuted = 0xFF48546E,
                    primary = 0xFF13254D, onPrimary = 0xFFFFFFFF,
                    accent = 0xFFE0A526, onAccent = 0xFF0E1A33,
                    card = 0xFF13254D,
                ),
                dark = flipColors(
                    dark = true, background = 0xFF070D1C, surface = 0xFF0E1730, surfaceVariant = 0xFF152140,
                    ink = 0xFFE6ECF8, inkMuted = 0xFFA4B0CC,
                    primary = 0xFF8FB0FF, onPrimary = 0xFF0E1A33,
                    accent = 0xFFF2C14E, onAccent = 0xFF0E1A33,
                    card = 0xFF14224A,
                ),
            ),
        ),
    )

    private fun flipColors(
        dark: Boolean, background: Long, surface: Long, surfaceVariant: Long,
        ink: Long, inkMuted: Long, primary: Long, onPrimary: Long,
        accent: Long, onAccent: Long, card: Long,
    ) = colors(
        dark = dark, background = background, onBackground = ink, surface = surface,
        surfaceVariant = surfaceVariant, onSurfaceVariant = inkMuted,
        primary = primary, onPrimary = onPrimary,
        secondary = surfaceVariant, onSecondary = ink,
        accent = accent, onAccent = onAccent,
        clockDigit = ink,
        clockGhost = card,
        shadow = withAlpha(BLACK, if (dark) 0.60f else 0.22f),
        glow = accent,
    )

    // -----------------------------------------------------------------------------------------
    // GLASS: frosted surfaces over blurred colour blobs (primary, accent and glow).
    // `surface` is the opaque equivalent of the frosted card, used for contrast checks.
    // -----------------------------------------------------------------------------------------

    private fun glass() = ThemeInfo(
        id = ThemeId.GLASS,
        nameEn = "Glass",
        nameTh = "กระจก",
        descriptionEn = "Frosted glass over soft, glowing colour.",
        descriptionTh = "กระจกฝ้าโปร่งแสงบนพื้นสีไล่เฉดนุ่มนวล",
        clockFace = ClockFace.PLAIN,
        defaultBodyFontId = "anuphan",
        defaultClockFontId = "prompt",
        palettes = listOf(
            PaletteSpec(
                id = "aurora", nameEn = "Aurora", nameTh = "แสงเหนือ",
                light = glassColors(
                    dark = false, background = 0xFFEEF2FB, surface = 0xFFF7F8FD, surfaceVariant = 0xFFE3E8F6,
                    ink = 0xFF141A33, inkMuted = 0xFF4A5275,
                    primary = 0xFF5B47E0, onPrimary = 0xFFFFFFFF,
                    secondary = 0xFFD9F2EE, onSecondary = 0xFF10302B,
                    accent = 0xFF0B7A6A, onAccent = 0xFFFFFFFF,
                    glow = 0xFFFF7EB6,
                ),
                dark = glassColors(
                    dark = true, background = 0xFF070B1A, surface = 0xFF141A2E, surfaceVariant = 0xFF1C2440,
                    ink = 0xFFEEF1FF, inkMuted = 0xFFA9B1D6,
                    primary = 0xFF8B7BFF, onPrimary = 0xFF0A0C24,
                    secondary = 0xFF1E3A3A, onSecondary = 0xFFCFF5EE,
                    accent = 0xFF2FE0C0, onAccent = 0xFF04201B,
                    glow = 0xFFFF6FB5,
                ),
            ),
            PaletteSpec(
                id = "sunset", nameEn = "Sunset", nameTh = "อาทิตย์อัสดง",
                light = glassColors(
                    dark = false, background = 0xFFFFF3EC, surface = 0xFFFFF9F5, surfaceVariant = 0xFFFBE6DA,
                    ink = 0xFF33140E, inkMuted = 0xFF7A4A3C,
                    primary = 0xFFC2410C, onPrimary = 0xFFFFFFFF,
                    secondary = 0xFFFFE0CC, onSecondary = 0xFF33140E,
                    accent = 0xFFC2185B, onAccent = 0xFFFFFFFF,
                    glow = 0xFFFFB347,
                ),
                dark = glassColors(
                    dark = true, background = 0xFF1A0A10, surface = 0xFF2A141B, surfaceVariant = 0xFF381C25,
                    ink = 0xFFFFEDE6, inkMuted = 0xFFDDB0A4,
                    primary = 0xFFFF8A5B, onPrimary = 0xFF2A0E05,
                    secondary = 0xFF4A2430, onSecondary = 0xFFFFE5DA,
                    accent = 0xFFFF6F9C, onAccent = 0xFF2A0712,
                    glow = 0xFFFFC371,
                ),
            ),
            PaletteSpec(
                id = "ocean", nameEn = "Ocean", nameTh = "มหาสมุทร",
                light = glassColors(
                    dark = false, background = 0xFFECF6FB, surface = 0xFFF6FBFE, surfaceVariant = 0xFFDDEEF6,
                    ink = 0xFF082433, inkMuted = 0xFF3C5F70,
                    primary = 0xFF0A6E9E, onPrimary = 0xFFFFFFFF,
                    secondary = 0xFFD3F1F2, onSecondary = 0xFF082433,
                    accent = 0xFF00796B, onAccent = 0xFFFFFFFF,
                    glow = 0xFF4FC3F7,
                ),
                dark = glassColors(
                    dark = true, background = 0xFF03111C, surface = 0xFF0D1F2C, surfaceVariant = 0xFF13293A,
                    ink = 0xFFE4F4FB, inkMuted = 0xFF9CC0D1,
                    primary = 0xFF4FC3F7, onPrimary = 0xFF03111C,
                    secondary = 0xFF14363F, onSecondary = 0xFFD6F4F7,
                    accent = 0xFF2DE2C5, onAccent = 0xFF03111C,
                    glow = 0xFF2F6BFF,
                ),
            ),
            PaletteSpec(
                id = "lavender", nameEn = "Lavender", nameTh = "ลาเวนเดอร์",
                light = glassColors(
                    dark = false, background = 0xFFF5F1FD, surface = 0xFFFBF9FF, surfaceVariant = 0xFFECE5FA,
                    ink = 0xFF24173D, inkMuted = 0xFF5E4F7D,
                    primary = 0xFF7048C8, onPrimary = 0xFFFFFFFF,
                    secondary = 0xFFF6DFF5, onSecondary = 0xFF3A1538,
                    accent = 0xFFB0367F, onAccent = 0xFFFFFFFF,
                    glow = 0xFF8EC5FC,
                ),
                dark = glassColors(
                    dark = true, background = 0xFF110B1E, surface = 0xFF1E1630, surfaceVariant = 0xFF2A1F42,
                    ink = 0xFFF2ECFF, inkMuted = 0xFFBDB0DA,
                    primary = 0xFFB79CFF, onPrimary = 0xFF1E0F3D,
                    secondary = 0xFF3A2347, onSecondary = 0xFFF6DFF5,
                    accent = 0xFFF28CCB, onAccent = 0xFF2E0B22,
                    glow = 0xFF6B8CFF,
                ),
            ),
        ),
    )

    private fun glassColors(
        dark: Boolean, background: Long, surface: Long, surfaceVariant: Long,
        ink: Long, inkMuted: Long, primary: Long, onPrimary: Long,
        secondary: Long, onSecondary: Long, accent: Long, onAccent: Long, glow: Long,
    ) = colors(
        dark = dark, background = background, onBackground = ink, surface = surface,
        surfaceVariant = surfaceVariant, onSurfaceVariant = inkMuted,
        primary = primary, onPrimary = onPrimary, secondary = secondary, onSecondary = onSecondary,
        accent = accent, onAccent = onAccent,
        outline = mix(background, ink, if (dark) 0.30f else 0.22f),
        clockGhost = withAlpha(ink, 0.08f),
        shadow = withAlpha(if (dark) BLACK else mix(primary, BLACK, 0.6f), if (dark) 0.50f else 0.14f),
        glow = glow,
    )

    // -----------------------------------------------------------------------------------------
    // MATERIAL YOU: "dynamic" follows the wallpaper; the others are static tonal schemes.
    // background = surface, surface = surfaceContainerLow, surfaceVariant = surfaceContainerHighest,
    // secondary = secondaryContainer, accent = tertiary.
    // -----------------------------------------------------------------------------------------

    private fun materialYou() = ThemeInfo(
        id = ThemeId.MATERIAL_YOU,
        nameEn = "Material You",
        nameTh = "Material You",
        descriptionEn = "Colours picked from your wallpaper, One UI style.",
        descriptionTh = "ใช้สีจากวอลเปเปอร์ของคุณ เข้ากับ One UI",
        clockFace = ClockFace.PLAIN,
        defaultBodyFontId = "noto_sans_thai",
        defaultClockFontId = "anuphan",
        palettes = listOf(
            PaletteSpec(
                id = DYNAMIC_PALETTE_ID, nameEn = "Wallpaper", nameTh = "ตามวอลเปเปอร์",
                light = youColors(
                    dark = false, background = 0xFFFEF7FF, onBackground = 0xFF1D1B20,
                    surface = 0xFFF7F2FA, surfaceVariant = 0xFFE6E0E9, onSurfaceVariant = 0xFF49454F,
                    primary = 0xFF6750A4, onPrimary = 0xFFFFFFFF,
                    secondary = 0xFFE8DEF8, onSecondary = 0xFF1D192B,
                    accent = 0xFF7D5260, onAccent = 0xFFFFFFFF, outline = 0xFF79747E,
                ),
                dark = youColors(
                    dark = true, background = 0xFF141218, onBackground = 0xFFE6E0E9,
                    surface = 0xFF1D1B20, surfaceVariant = 0xFF36343B, onSurfaceVariant = 0xFFCAC4D0,
                    primary = 0xFFD0BCFF, onPrimary = 0xFF381E72,
                    secondary = 0xFF4A4458, onSecondary = 0xFFE8DEF8,
                    accent = 0xFFEFB8C8, onAccent = 0xFF492532, outline = 0xFF938F99,
                ),
            ),
            PaletteSpec(
                id = "blue", nameEn = "Blue", nameTh = "น้ำเงิน",
                light = youColors(
                    dark = false, background = 0xFFF9F9FF, onBackground = 0xFF191C20,
                    surface = 0xFFF3F3FA, surfaceVariant = 0xFFE2E2E9, onSurfaceVariant = 0xFF44474E,
                    primary = 0xFF2559BD, onPrimary = 0xFFFFFFFF,
                    secondary = 0xFFD7E3F8, onSecondary = 0xFF101C2B,
                    accent = 0xFF705575, onAccent = 0xFFFFFFFF, outline = 0xFF74777F,
                ),
                dark = youColors(
                    dark = true, background = 0xFF111318, onBackground = 0xFFE2E2E9,
                    surface = 0xFF191C20, surfaceVariant = 0xFF33353A, onSurfaceVariant = 0xFFC4C6D0,
                    primary = 0xFFAAC7FF, onPrimary = 0xFF0A305F,
                    secondary = 0xFF3E4759, onSecondary = 0xFFD7E3F8,
                    accent = 0xFFDDBCE0, onAccent = 0xFF3F2844, outline = 0xFF8E9099,
                ),
            ),
            PaletteSpec(
                id = "green", nameEn = "Green", nameTh = "เขียว",
                light = youColors(
                    dark = false, background = 0xFFF7FBF2, onBackground = 0xFF181D17,
                    surface = 0xFFF1F5EC, surfaceVariant = 0xFFE0E4DB, onSurfaceVariant = 0xFF424940,
                    primary = 0xFF3A6A30, onPrimary = 0xFFFFFFFF,
                    secondary = 0xFFD7E8CD, onSecondary = 0xFF121F0E,
                    accent = 0xFF386568, onAccent = 0xFFFFFFFF, outline = 0xFF72796F,
                ),
                dark = youColors(
                    dark = true, background = 0xFF10140F, onBackground = 0xFFE0E4DB,
                    surface = 0xFF181D17, surfaceVariant = 0xFF323630, onSurfaceVariant = 0xFFC2C9BD,
                    primary = 0xFFA0D494, onPrimary = 0xFF0D3910,
                    secondary = 0xFF3B4B36, onSecondary = 0xFFD7E8CD,
                    accent = 0xFFA0CFD2, onAccent = 0xFF003739, outline = 0xFF8C9388,
                ),
            ),
            PaletteSpec(
                id = "violet", nameEn = "Violet", nameTh = "ม่วง",
                light = youColors(
                    dark = false, background = 0xFFFFF7FC, onBackground = 0xFF1F1A1F,
                    surface = 0xFFFAF1F8, surfaceVariant = 0xFFEAE0E8, onSurfaceVariant = 0xFF4D444C,
                    primary = 0xFF7B3F9E, onPrimary = 0xFFFFFFFF,
                    secondary = 0xFFF4DBF4, onSecondary = 0xFF2B122E,
                    accent = 0xFF9A4157, onAccent = 0xFFFFFFFF, outline = 0xFF7F737D,
                ),
                dark = youColors(
                    dark = true, background = 0xFF171217, onBackground = 0xFFEBE0E8,
                    surface = 0xFF1F1A1F, surfaceVariant = 0xFF3A333A, onSurfaceVariant = 0xFFD0C3CD,
                    primary = 0xFFE7B3FF, onPrimary = 0xFF4A0F6B,
                    secondary = 0xFF553A57, onSecondary = 0xFFF4DBF4,
                    accent = 0xFFFFB1C1, onAccent = 0xFF5E1129, outline = 0xFF998D97,
                ),
            ),
        ),
    )

    private fun youColors(
        dark: Boolean, background: Long, onBackground: Long, surface: Long,
        surfaceVariant: Long, onSurfaceVariant: Long, primary: Long, onPrimary: Long,
        secondary: Long, onSecondary: Long, accent: Long, onAccent: Long, outline: Long,
    ) = colors(
        dark = dark, background = background, onBackground = onBackground, surface = surface,
        surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
        primary = primary, onPrimary = onPrimary, secondary = secondary, onSecondary = onSecondary,
        accent = accent, onAccent = onAccent, outline = outline,
        clockDigit = primary,
        clockGhost = withAlpha(primary, 0.12f),
        shadow = withAlpha(BLACK, if (dark) 0.45f else 0.16f),
        glow = primary,
    )

    // -----------------------------------------------------------------------------------------
    // TERMINAL (dark only): everything derives from one phosphor colour.
    // -----------------------------------------------------------------------------------------

    private fun terminal() = ThemeInfo(
        id = ThemeId.TERMINAL,
        nameEn = "Terminal",
        nameTh = "เทอร์มินัล",
        descriptionEn = "Phosphor text on black and a blinking cursor.",
        descriptionTh = "ตัวอักษรเรืองแสงบนจอดำ พร้อมเคอร์เซอร์กะพริบ",
        clockFace = ClockFace.TERMINAL,
        defaultBodyFontId = "ibm_plex_mono",
        defaultClockFontId = "jetbrains_mono",
        supportsLight = false,
        palettes = listOf(
            terminalPalette("green_phosphor", "Green Phosphor", "ฟอสฟอร์สีเขียว",
                background = 0xFF030B05, phosphor = 0xFF46F27A, dim = 0xFF34B35C, highlight = 0xFFB4FF5C),
            terminalPalette("amber", "Amber", "อำพัน",
                background = 0xFF0D0802, phosphor = 0xFFFFB641, dim = 0xFFC48A2A, highlight = 0xFFFF8C3B),
            terminalPalette("cyan", "Cyan", "ฟ้าไซแอน",
                background = 0xFF011014, phosphor = 0xFF3FE6F2, dim = 0xFF27A9B5, highlight = 0xFF7CFFC4),
            terminalPalette("white", "White", "ขาว",
                background = 0xFF0B0C0C, phosphor = 0xFFE3E6E3, dim = 0xFF9AA09B, highlight = 0xFF9AD1FF),
        ),
    )

    private fun terminalPalette(
        id: String, nameEn: String, nameTh: String,
        background: Long, phosphor: Long, dim: Long, highlight: Long,
    ): PaletteSpec {
        val surfaceVariant = mix(background, phosphor, 0.10f)
        val c = colors(
            dark = true, background = background, onBackground = phosphor,
            surface = mix(background, phosphor, 0.045f),
            surfaceVariant = surfaceVariant, onSurfaceVariant = dim,
            primary = phosphor, onPrimary = background,
            secondary = surfaceVariant, onSecondary = phosphor,
            accent = highlight, onAccent = background,
            outline = mix(background, phosphor, 0.50f),
            error = 0xFFFF6E5E, onError = background,
            success = phosphor, warning = 0xFFFFD166,
            clockDigit = phosphor,
            clockGhost = withAlpha(phosphor, 0.08f),
            shadow = withAlpha(BLACK, 0.6f),
            glow = phosphor,
        )
        return PaletteSpec(id = id, nameEn = nameEn, nameTh = nameTh, light = c, dark = c)
    }

    // -----------------------------------------------------------------------------------------
    // NEON (dark only): glowing tubes. glow = tube glow, clockDigit = tube colour.
    // -----------------------------------------------------------------------------------------

    private fun neon() = ThemeInfo(
        id = ThemeId.NEON,
        nameEn = "Neon",
        nameTh = "นีออน",
        descriptionEn = "Glowing lines and type on a night-city dark.",
        descriptionTh = "เส้นและตัวอักษรเรืองแสง บรรยากาศเมืองยามค่ำคืน",
        clockFace = ClockFace.NEON_GLOW,
        defaultBodyFontId = "mitr",
        defaultClockFontId = "orbitron",
        supportsLight = false,
        palettes = listOf(
            neonPalette("cyberpunk", "Cyberpunk", "ไซเบอร์พังก์",
                background = 0xFF070417, surface = 0xFF110B26, surfaceVariant = 0xFF1A1236,
                ink = 0xFFF4F0FF, inkMuted = 0xFFB3A8D9,
                primary = 0xFFFF2A6D, secondaryText = 0xFF05D9E8, accent = 0xFF05D9E8,
                glow = 0xFFFF2A6D, digit = 0xFFFF4F8B),
            neonPalette("synthwave", "Synthwave", "ซินธ์เวฟ",
                background = 0xFF140A26, surface = 0xFF1F1138, surfaceVariant = 0xFF2B1850,
                ink = 0xFFFFF0FB, inkMuted = 0xFFC9A9E0,
                primary = 0xFFFF4FD8, secondaryText = 0xFFFFB86C, accent = 0xFFFFB86C,
                glow = 0xFFFF4FD8, digit = 0xFFFF7AE5),
            neonPalette("toxic", "Toxic", "สารพิษ",
                background = 0xFF050A05, surface = 0xFF0C150C, surfaceVariant = 0xFF142114,
                ink = 0xFFEFFFE9, inkMuted = 0xFFA3C29B,
                primary = 0xFF39FF14, secondaryText = 0xFFC77DFF, accent = 0xFFC77DFF,
                glow = 0xFF39FF14, digit = 0xFF5CFF3A),
            neonPalette("electric", "Electric", "ไฟฟ้า",
                background = 0xFF02040F, surface = 0xFF0A1024, surfaceVariant = 0xFF111A38,
                ink = 0xFFEAF6FF, inkMuted = 0xFF9DB4DA,
                primary = 0xFF00B3FF, secondaryText = 0xFF7DF9FF, accent = 0xFF8C7BFF,
                glow = 0xFF00B3FF, digit = 0xFF4DD2FF),
        ),
    )

    private fun neonPalette(
        id: String, nameEn: String, nameTh: String,
        background: Long, surface: Long, surfaceVariant: Long, ink: Long, inkMuted: Long,
        primary: Long, secondaryText: Long, accent: Long, glow: Long, digit: Long,
    ): PaletteSpec {
        val c = colors(
            dark = true, background = background, onBackground = ink, surface = surface,
            surfaceVariant = surfaceVariant, onSurfaceVariant = inkMuted,
            primary = primary, onPrimary = background,
            secondary = surfaceVariant, onSecondary = secondaryText,
            accent = accent, onAccent = background,
            outline = mix(background, glow, 0.55f),
            error = 0xFFFF5C7A, onError = background,
            success = 0xFF5CFFB0, warning = 0xFFFFD84D,
            clockDigit = digit,
            clockGhost = withAlpha(digit, 0.07f),
            shadow = withAlpha(glow, 0.55f),
            glow = glow,
        )
        return PaletteSpec(id = id, nameEn = nameEn, nameTh = nameTh, light = c, dark = c)
    }

    // -----------------------------------------------------------------------------------------
    // PAPER: ink on paper, editorial accents.
    // -----------------------------------------------------------------------------------------

    private fun paper() = ThemeInfo(
        id = ThemeId.PAPER,
        nameEn = "Paper",
        nameTh = "กระดาษ",
        descriptionEn = "Serif type and a newspaper layout on textured paper.",
        descriptionTh = "ฟอนต์มีเชิง จัดหน้าแบบหนังสือพิมพ์บนกระดาษมีลาย",
        clockFace = ClockFace.SERIF,
        defaultBodyFontId = "taviraj",
        defaultClockFontId = "dm_serif_display",
        palettes = listOf(
            PaletteSpec(
                id = "newsprint", nameEn = "Newsprint", nameTh = "กระดาษหนังสือพิมพ์",
                light = paperColors(
                    dark = false, background = 0xFFF2EFE6, surface = 0xFFF9F7F1, surfaceVariant = 0xFFE6E1D3,
                    ink = 0xFF1A1A18, inkMuted = 0xFF55524A,
                    primary = 0xFF1A1A18, onPrimary = 0xFFF9F7F1,
                    accent = 0xFFB22A1E, onAccent = 0xFFFFFFFF,
                ),
                dark = paperColors(
                    dark = true, background = 0xFF1B1A18, surface = 0xFF23221F, surfaceVariant = 0xFF2E2C28,
                    ink = 0xFFECE8DE, inkMuted = 0xFFADA89B,
                    primary = 0xFFECE8DE, onPrimary = 0xFF1B1A18,
                    accent = 0xFFF07A62, onAccent = 0xFF1B1A18,
                ),
            ),
            PaletteSpec(
                id = "cream", nameEn = "Cream", nameTh = "ครีม",
                light = paperColors(
                    dark = false, background = 0xFFFAF3E3, surface = 0xFFFFFAEF, surfaceVariant = 0xFFF0E5CC,
                    ink = 0xFF2A2217, inkMuted = 0xFF645640,
                    primary = 0xFF5B4126, onPrimary = 0xFFFFFAEF,
                    accent = 0xFF9C3D23, onAccent = 0xFFFFFFFF,
                ),
                dark = paperColors(
                    dark = true, background = 0xFF1E1A13, surface = 0xFF28231A, surfaceVariant = 0xFF342D22,
                    ink = 0xFFF4EAD5, inkMuted = 0xFFC2B497,
                    primary = 0xFFE6C99A, onPrimary = 0xFF2A2217,
                    accent = 0xFFF0957A, onAccent = 0xFF2A1810,
                ),
            ),
            PaletteSpec(
                id = "sepia", nameEn = "Sepia", nameTh = "ซีเปีย",
                light = paperColors(
                    dark = false, background = 0xFFEADBC0, surface = 0xFFF2E6CF, surfaceVariant = 0xFFDECBA9,
                    ink = 0xFF3A2817, inkMuted = 0xFF5E4529,
                    primary = 0xFF5A3B1E, onPrimary = 0xFFF2E6CF,
                    accent = 0xFF8E2F1A, onAccent = 0xFFF7EBD8,
                ),
                dark = paperColors(
                    dark = true, background = 0xFF1F1810, surface = 0xFF2A2116, surfaceVariant = 0xFF362A1C,
                    ink = 0xFFEBD9BA, inkMuted = 0xFFBFA680,
                    primary = 0xFFD9B47A, onPrimary = 0xFF2A1C0C,
                    accent = 0xFFE8896A, onAccent = 0xFF2A1208,
                ),
            ),
            PaletteSpec(
                id = "ink_blue", nameEn = "Ink Blue", nameTh = "หมึกน้ำเงิน",
                light = paperColors(
                    dark = false, background = 0xFFF2F1EB, surface = 0xFFFAF9F5, surfaceVariant = 0xFFE4E3DA,
                    ink = 0xFF14223D, inkMuted = 0xFF4A5670,
                    primary = 0xFF1B3566, onPrimary = 0xFFFFFFFF,
                    accent = 0xFFB0412A, onAccent = 0xFFFFFFFF,
                ),
                dark = paperColors(
                    dark = true, background = 0xFF0E1524, surface = 0xFF151E31, surfaceVariant = 0xFF1D283F,
                    ink = 0xFFDCE4F2, inkMuted = 0xFF9AA9C4,
                    primary = 0xFF9DB8EC, onPrimary = 0xFF0E1A33,
                    accent = 0xFFF09A80, onAccent = 0xFF2A1008,
                ),
            ),
        ),
    )

    private fun paperColors(
        dark: Boolean, background: Long, surface: Long, surfaceVariant: Long,
        ink: Long, inkMuted: Long, primary: Long, onPrimary: Long, accent: Long, onAccent: Long,
    ) = colors(
        dark = dark, background = background, onBackground = ink, surface = surface,
        surfaceVariant = surfaceVariant, onSurfaceVariant = inkMuted,
        primary = primary, onPrimary = onPrimary,
        secondary = surfaceVariant, onSecondary = ink,
        accent = accent, onAccent = onAccent,
        // Newspaper rules are printed in ink.
        outline = mix(background, ink, if (dark) 0.55f else 0.70f),
        clockGhost = withAlpha(ink, 0.10f),
        shadow = withAlpha(mix(ink, BLACK, 0.3f), if (dark) 0.40f else 0.12f),
        glow = accent,
    )

    // -----------------------------------------------------------------------------------------
    // PIXEL: 8-bit. "line" draws pixel borders, "drop" is the blocky drop shadow.
    // -----------------------------------------------------------------------------------------

    private fun pixel() = ThemeInfo(
        id = ThemeId.PIXEL,
        nameEn = "Pixel",
        nameTh = "พิกเซล",
        descriptionEn = "Chunky 8-bit pixels and stepped motion.",
        descriptionTh = "พิกเซลแบบเกม 8 บิต เคลื่อนไหวเป็นจังหวะ",
        clockFace = ClockFace.PIXEL,
        defaultBodyFontId = "vt323",
        defaultClockFontId = "press_start_2p",
        palettes = listOf(
            PaletteSpec(
                id = "game_boy", nameEn = "Game Boy", nameTh = "เกมบอย",
                light = pixelColors(
                    dark = false, background = 0xFF9BBC0F, surface = 0xFF8BAC0F, surfaceVariant = 0xFFA9C93A,
                    ink = 0xFF0F380F, inkMuted = 0xFF173F17, line = 0xFF0F380F, drop = 0xFF306230,
                    primary = 0xFF0F380F, onPrimary = 0xFF9BBC0F,
                    secondary = 0xFF306230, onSecondary = 0xFFE0F8CF,
                    accent = 0xFF306230, onAccent = 0xFFE0F8CF,
                    digit = 0xFF0F380F,
                    // The Game Boy has four greens and no red: keep status colours dark and in style.
                    error = 0xFF5A1010, onError = 0xFFE0F8CF, success = 0xFF0F380F, warning = 0xFF4A3A00,
                ),
                dark = pixelColors(
                    dark = true, background = 0xFF0F380F, surface = 0xFF183F18, surfaceVariant = 0xFF204820,
                    ink = 0xFF9BBC0F, inkMuted = 0xFFABCC3F, line = 0xFF8BAC0F, drop = 0xFF071E07,
                    primary = 0xFF9BBC0F, onPrimary = 0xFF0F380F,
                    secondary = 0xFF306230, onSecondary = 0xFFE0F8CF,
                    accent = 0xFF8BAC0F, onAccent = 0xFF0F380F,
                    digit = 0xFF9BBC0F,
                ),
            ),
            PaletteSpec(
                id = "nes", nameEn = "NES", nameTh = "แฟมิคอม",
                light = pixelColors(
                    dark = false, background = 0xFFE4E4E4, surface = 0xFFFCFCFC, surfaceVariant = 0xFFC9C9C9,
                    ink = 0xFF000000, inkMuted = 0xFF383838, line = 0xFF000000, drop = 0xFF000000,
                    primary = 0xFFC81E00, onPrimary = 0xFFFCFCFC,
                    secondary = 0xFF000000, onSecondary = 0xFFFCFCFC,
                    accent = 0xFF0058F8, onAccent = 0xFFFCFCFC,
                    digit = 0xFF000000,
                ),
                dark = pixelColors(
                    dark = true, background = 0xFF000000, surface = 0xFF141414, surfaceVariant = 0xFF242424,
                    ink = 0xFFFCFCFC, inkMuted = 0xFFBCBCBC, line = 0xFFFCFCFC, drop = 0xFF545454,
                    primary = 0xFFF83800, onPrimary = 0xFF000000,
                    secondary = 0xFF242424, onSecondary = 0xFFFCFCFC,
                    accent = 0xFF3CBCFC, onAccent = 0xFF000000,
                    digit = 0xFFFCFCFC,
                ),
            ),
            PaletteSpec(
                id = "arcade", nameEn = "Arcade", nameTh = "ตู้เกม",
                light = pixelColors(
                    dark = false, background = 0xFFFFF4D6, surface = 0xFFFFFFFF, surfaceVariant = 0xFFFFE6A8,
                    ink = 0xFF1A0F3D, inkMuted = 0xFF4A3E73, line = 0xFF1A0F3D, drop = 0xFF1A0F3D,
                    primary = 0xFFD2102A, onPrimary = 0xFFFFFFFF,
                    secondary = 0xFF2D2DFF, onSecondary = 0xFFFFFFFF,
                    accent = 0xFFFFD000, onAccent = 0xFF1A0F3D,
                    digit = 0xFFD2102A,
                ),
                dark = pixelColors(
                    dark = true, background = 0xFF0B0B2B, surface = 0xFF15154A, surfaceVariant = 0xFF1F1F63,
                    ink = 0xFFFFFFFF, inkMuted = 0xFFB9B9F0, line = 0xFF4D4DFF, drop = 0xFF000000,
                    primary = 0xFFFFD000, onPrimary = 0xFF0B0B2B,
                    secondary = 0xFF1F1F63, onSecondary = 0xFFFF8CC4,
                    accent = 0xFFFF4FA3, onAccent = 0xFF0B0B2B,
                    digit = 0xFFFFD000,
                ),
            ),
            PaletteSpec(
                id = "pastel", nameEn = "Pastel", nameTh = "พาสเทล",
                light = pixelColors(
                    dark = false, background = 0xFFFDF0F5, surface = 0xFFFFFFFF, surfaceVariant = 0xFFF5E1EC,
                    ink = 0xFF3B2A4A, inkMuted = 0xFF6A5679, line = 0xFF3B2A4A, drop = 0xFFC9B3E0,
                    primary = 0xFF7B5CC4, onPrimary = 0xFFFFFFFF,
                    secondary = 0xFFD5F2EA, onSecondary = 0xFF1F3D35,
                    accent = 0xFFFF8FB8, onAccent = 0xFF3B1028,
                    digit = 0xFF7B5CC4,
                ),
                dark = pixelColors(
                    dark = true, background = 0xFF241E33, surface = 0xFF2E2742, surfaceVariant = 0xFF3A3152,
                    ink = 0xFFFBF0FF, inkMuted = 0xFFCDBDE6, line = 0xFFC7B3FF, drop = 0xFF120E1A,
                    primary = 0xFFC7B3FF, onPrimary = 0xFF241E33,
                    secondary = 0xFF2F4A44, onSecondary = 0xFFD5F2EA,
                    accent = 0xFFFFB3CF, onAccent = 0xFF3B1028,
                    digit = 0xFFFFB3CF,
                ),
            ),
        ),
    )

    private fun pixelColors(
        dark: Boolean, background: Long, surface: Long, surfaceVariant: Long,
        ink: Long, inkMuted: Long, line: Long, drop: Long,
        primary: Long, onPrimary: Long, secondary: Long, onSecondary: Long,
        accent: Long, onAccent: Long, digit: Long,
        error: Long = if (dark) 0xFFFFB4AB else 0xFFBA1A1A,
        onError: Long = if (dark) 0xFF690005 else 0xFFFFFFFF,
        success: Long = if (dark) 0xFF7DD99B else 0xFF1B7F3B,
        warning: Long = if (dark) 0xFFF2C14E else 0xFF8A5A00,
    ) = colors(
        dark = dark, background = background, onBackground = ink, surface = surface,
        surfaceVariant = surfaceVariant, onSurfaceVariant = inkMuted,
        primary = primary, onPrimary = onPrimary, secondary = secondary, onSecondary = onSecondary,
        accent = accent, onAccent = onAccent,
        outline = line,
        error = error, onError = onError, success = success, warning = warning,
        clockDigit = digit,
        clockGhost = withAlpha(digit, 0.10f),
        shadow = drop,
        glow = accent,
    )

    // -----------------------------------------------------------------------------------------
    // ANALOG: watch dials. clockDigit = hands and numerals, clockGhost = minute ticks,
    // accent = seconds hand.
    // -----------------------------------------------------------------------------------------

    private fun analog() = ThemeInfo(
        id = ThemeId.ANALOG,
        nameEn = "Analog",
        nameTh = "เข็มนาฬิกา",
        descriptionEn = "A classic watch dial with sweeping hands.",
        descriptionTh = "หน้าปัดนาฬิกาคลาสสิกพร้อมเข็มเดิน",
        clockFace = ClockFace.ANALOG,
        defaultBodyFontId = "sarabun",
        defaultClockFontId = "playfair_display",
        palettes = listOf(
            PaletteSpec(
                id = "classic", nameEn = "Classic", nameTh = "คลาสสิก",
                light = analogColors(
                    dark = false, background = 0xFFF4F1EA, surface = 0xFFFFFFFF, surfaceVariant = 0xFFE8E3D8,
                    ink = 0xFF151515, inkMuted = 0xFF56534C,
                    primary = 0xFF151515, onPrimary = 0xFFFFFFFF,
                    accent = 0xFFC8102E, onAccent = 0xFFFFFFFF,
                ),
                dark = analogColors(
                    dark = true, background = 0xFF101010, surface = 0xFF1A1A1A, surfaceVariant = 0xFF262626,
                    ink = 0xFFEEEAE2, inkMuted = 0xFFA9A59C,
                    primary = 0xFFEEEAE2, onPrimary = 0xFF151515,
                    accent = 0xFFFF5A5F, onAccent = 0xFF151515,
                ),
            ),
            PaletteSpec(
                id = "navy", nameEn = "Navy", nameTh = "กรมท่า",
                light = analogColors(
                    dark = false, background = 0xFFEDF0F5, surface = 0xFFFAFBFD, surfaceVariant = 0xFFDDE3EC,
                    ink = 0xFF0E1C38, inkMuted = 0xFF45526C,
                    primary = 0xFF172B57, onPrimary = 0xFFFFFFFF,
                    accent = 0xFF8A6410, onAccent = 0xFFFFFFFF,
                ),
                dark = analogColors(
                    dark = true, background = 0xFF0A1630, surface = 0xFF10203F, surfaceVariant = 0xFF182A4F,
                    ink = 0xFFEDE6D2, inkMuted = 0xFFB8B4A6,
                    primary = 0xFFD8B65E, onPrimary = 0xFF0A1630,
                    accent = 0xFFE3C26F, onAccent = 0xFF0A1630,
                ),
            ),
            PaletteSpec(
                id = "forest", nameEn = "Forest", nameTh = "เขียวป่า",
                light = analogColors(
                    dark = false, background = 0xFFEEF2EB, surface = 0xFFFAFCF8, surfaceVariant = 0xFFDDE5D8,
                    ink = 0xFF132A1B, inkMuted = 0xFF4A5E50,
                    primary = 0xFF1E4A2D, onPrimary = 0xFFFFFFFF,
                    accent = 0xFF8A5E16, onAccent = 0xFFFFFFFF,
                ),
                dark = analogColors(
                    dark = true, background = 0xFF0D2116, surface = 0xFF13291C, surfaceVariant = 0xFF1B3425,
                    ink = 0xFFEFE8D5, inkMuted = 0xFFB6B29F,
                    primary = 0xFFCFA75C, onPrimary = 0xFF0D2116,
                    accent = 0xFFE2BC6E, onAccent = 0xFF0D2116,
                ),
            ),
            PaletteSpec(
                id = "rose_gold", nameEn = "Rose Gold", nameTh = "โรสโกลด์",
                light = analogColors(
                    dark = false, background = 0xFFFAF0EC, surface = 0xFFFFFAF8, surfaceVariant = 0xFFF1E0DA,
                    ink = 0xFF3A2420, inkMuted = 0xFF74554E,
                    primary = 0xFF8E5548, onPrimary = 0xFFFFFFFF,
                    accent = 0xFF9E5248, onAccent = 0xFFFFFFFF,
                ),
                dark = analogColors(
                    dark = true, background = 0xFF1E1415, surface = 0xFF281B1C, surfaceVariant = 0xFF352425,
                    ink = 0xFFF6E1D8, inkMuted = 0xFFC9A99F,
                    primary = 0xFFE8B4A2, onPrimary = 0xFF2A1714,
                    accent = 0xFFF0A79A, onAccent = 0xFF2A1714,
                ),
            ),
        ),
    )

    private fun analogColors(
        dark: Boolean, background: Long, surface: Long, surfaceVariant: Long,
        ink: Long, inkMuted: Long, primary: Long, onPrimary: Long, accent: Long, onAccent: Long,
    ) = colors(
        dark = dark, background = background, onBackground = ink, surface = surface,
        surfaceVariant = surfaceVariant, onSurfaceVariant = inkMuted,
        primary = primary, onPrimary = onPrimary,
        secondary = surfaceVariant, onSecondary = ink,
        accent = accent, onAccent = onAccent,
        clockDigit = ink,
        clockGhost = withAlpha(ink, 0.32f),
        shadow = withAlpha(BLACK, if (dark) 0.55f else 0.16f),
        glow = primary,
    )

    // -----------------------------------------------------------------------------------------
    // AMOLED (dark only): pure black, one accent colour, as few lit pixels as possible.
    // -----------------------------------------------------------------------------------------

    private fun amoled() = ThemeInfo(
        id = ThemeId.AMOLED,
        nameEn = "AMOLED Black",
        nameTh = "ดำสนิท AMOLED",
        descriptionEn = "Pure black that saves power at night.",
        descriptionTh = "พื้นดำสนิท ประหยัดแบต เหมาะกับตอนกลางคืน",
        clockFace = ClockFace.PLAIN,
        defaultBodyFontId = "prompt",
        defaultClockFontId = "oswald",
        supportsLight = false,
        palettes = listOf(
            amoledPalette("red", "Red", "แดง", 0xFFFF453A),
            amoledPalette("cyan", "Cyan", "ฟ้าไซแอน", 0xFF33E1FF),
            amoledPalette("lime", "Lime", "เขียวมะนาว", 0xFFA8FF3E),
            amoledPalette("orange", "Orange", "ส้ม", 0xFFFF9F0A),
            amoledPalette("violet", "Violet", "ม่วง", 0xFFB892FF),
            amoledPalette("white", "White", "ขาว", 0xFFF2F2F2),
        ),
    )

    private fun amoledPalette(id: String, nameEn: String, nameTh: String, accent: Long): PaletteSpec {
        val c = colors(
            dark = true, background = BLACK, onBackground = 0xFFD9D9D9,
            surface = BLACK, surfaceVariant = 0xFF121212, onSurfaceVariant = 0xFF8F8F8F,
            primary = accent, onPrimary = BLACK,
            secondary = 0xFF121212, onSecondary = accent,
            accent = accent, onAccent = BLACK,
            outline = mix(BLACK, accent, 0.38f),
            error = 0xFFFF6B6B, onError = BLACK,
            success = 0xFF66E28A, warning = 0xFFFFC14D,
            clockDigit = accent,
            clockGhost = withAlpha(accent, 0.07f),
            shadow = BLACK,
            glow = accent,
        )
        return PaletteSpec(id = id, nameEn = nameEn, nameTh = nameTh, light = c, dark = c)
    }

    // -----------------------------------------------------------------------------------------
    // Shared builder and colour maths (ARGB longs; no Android/Compose types in core:model).
    // -----------------------------------------------------------------------------------------

    private const val BLACK: Long = 0xFF000000

    private fun colors(
        dark: Boolean,
        background: Long,
        onBackground: Long,
        surface: Long,
        onSurface: Long = onBackground,
        surfaceVariant: Long,
        onSurfaceVariant: Long,
        primary: Long,
        onPrimary: Long,
        secondary: Long,
        onSecondary: Long,
        accent: Long,
        onAccent: Long,
        outline: Long = mix(background, onBackground, if (dark) 0.28f else 0.22f),
        error: Long = if (dark) 0xFFFFB4AB else 0xFFBA1A1A,
        onError: Long = if (dark) 0xFF690005 else 0xFFFFFFFF,
        success: Long = if (dark) 0xFF7DD99B else 0xFF1B7F3B,
        warning: Long = if (dark) 0xFFF2C14E else 0xFF8A5A00,
        clockDigit: Long = onBackground,
        clockGhost: Long,
        shadow: Long,
        glow: Long,
    ) = PaletteColors(
        background = background, onBackground = onBackground,
        surface = surface, onSurface = onSurface,
        surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
        primary = primary, onPrimary = onPrimary,
        secondary = secondary, onSecondary = onSecondary,
        accent = accent, onAccent = onAccent,
        outline = outline,
        error = error, onError = onError,
        success = success, warning = warning,
        clockDigit = clockDigit, clockGhost = clockGhost,
        shadow = shadow, glow = glow,
    )

    /** Opaque linear blend in sRGB: t = 0 gives [from], t = 1 gives [to]. */
    private fun mix(from: Long, to: Long, t: Float): Long {
        fun channel(c: Long, shift: Int) = ((c shr shift) and 0xFF).toInt()
        fun blend(shift: Int): Long {
            val a = channel(from, shift)
            val b = channel(to, shift)
            return (a + (b - a) * t).roundToInt().coerceIn(0, 255).toLong()
        }
        return BLACK or (blend(16) shl 16) or (blend(8) shl 8) or blend(0)
    }

    private fun withAlpha(color: Long, alpha: Float): Long =
        ((alpha.coerceIn(0f, 1f) * 255).roundToInt().toLong() shl 24) or (color and 0xFFFFFF)
}
