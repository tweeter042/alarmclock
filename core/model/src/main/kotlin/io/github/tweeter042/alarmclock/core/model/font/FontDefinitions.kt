package io.github.tweeter042.alarmclock.core.model.font

/**
 * Every font bundled in core:designsystem `res/font` (downloaded by tools/fonts/download_fonts.py,
 * which also verifies [FontInfo.supportsThai] by reading each file's cmap table).
 *
 * Ids are the lowercase family keys used for the resource files (`<id>_<weight>.ttf`).
 * Latin-only fonts are paired with a matching Thai font by core:designsystem, so Thai text never
 * falls back to a random system font.
 */
internal object FontDefinitions {

    private fun font(
        id: String,
        displayName: String,
        category: FontCategory,
        supportsThai: Boolean,
        forClock: Boolean,
        forBody: Boolean,
    ) = FontInfo(
        id = id,
        displayName = displayName,
        category = category,
        supportsThai = supportsThai,
        forClock = forClock,
        forBody = forBody,
        license = "OFL-1.1",
    )

    val all: List<FontInfo> = listOf(
        // --- Thai + Latin sans ------------------------------------------------------------------
        font("noto_sans_thai", "Noto Sans Thai", FontCategory.SANS, supportsThai = true, forClock = true, forBody = true),
        font("ibm_plex_sans_thai", "IBM Plex Sans Thai", FontCategory.SANS, supportsThai = true, forClock = true, forBody = true),
        font("ibm_plex_sans_thai_looped", "IBM Plex Sans Thai Looped", FontCategory.SANS, supportsThai = true, forClock = false, forBody = true),
        font("anuphan", "Anuphan", FontCategory.SANS, supportsThai = true, forClock = true, forBody = true),
        font("bai_jamjuree", "Bai Jamjuree", FontCategory.SANS, supportsThai = true, forClock = true, forBody = true),
        font("prompt", "Prompt", FontCategory.SANS, supportsThai = true, forClock = true, forBody = true),
        font("kanit", "Kanit", FontCategory.SANS, supportsThai = true, forClock = true, forBody = true),
        font("sarabun", "Sarabun", FontCategory.SANS, supportsThai = true, forClock = false, forBody = true),
        font("mitr", "Mitr", FontCategory.SANS, supportsThai = true, forClock = true, forBody = true),
        font("chakra_petch", "Chakra Petch", FontCategory.SANS, supportsThai = true, forClock = true, forBody = true),
        font("k2d", "K2D", FontCategory.SANS, supportsThai = true, forClock = true, forBody = true),
        font("krub", "Krub", FontCategory.SANS, supportsThai = true, forClock = false, forBody = true),
        font("niramit", "Niramit", FontCategory.SANS, supportsThai = true, forClock = false, forBody = true),
        font("athiti", "Athiti", FontCategory.SANS, supportsThai = true, forClock = true, forBody = true),
        // --- Thai + Latin handwriting -----------------------------------------------------------
        font("mali", "Mali", FontCategory.HANDWRITING, supportsThai = true, forClock = true, forBody = true),
        font("itim", "Itim", FontCategory.HANDWRITING, supportsThai = true, forClock = true, forBody = true),
        font("sriracha", "Sriracha", FontCategory.HANDWRITING, supportsThai = true, forClock = true, forBody = true),
        font("charm", "Charm", FontCategory.HANDWRITING, supportsThai = true, forClock = true, forBody = false),
        // --- Thai + Latin serif / display -------------------------------------------------------
        font("pridi", "Pridi", FontCategory.SERIF, supportsThai = true, forClock = true, forBody = true),
        font("taviraj", "Taviraj", FontCategory.SERIF, supportsThai = true, forClock = true, forBody = true),
        font("trirong", "Trirong", FontCategory.SERIF, supportsThai = true, forClock = true, forBody = true),
        font("noto_serif_thai", "Noto Serif Thai", FontCategory.SERIF, supportsThai = true, forClock = true, forBody = true),
        font("chonburi", "Chonburi", FontCategory.DISPLAY, supportsThai = true, forClock = true, forBody = false),
        font("pattaya", "Pattaya", FontCategory.DISPLAY, supportsThai = true, forClock = true, forBody = false),
        // --- Latin sans (paired with a Thai font) -----------------------------------------------
        font("inter", "Inter", FontCategory.SANS, supportsThai = false, forClock = true, forBody = true),
        font("space_grotesk", "Space Grotesk", FontCategory.SANS, supportsThai = false, forClock = true, forBody = true),
        // --- Latin monospace --------------------------------------------------------------------
        font("space_mono", "Space Mono", FontCategory.MONO, supportsThai = false, forClock = true, forBody = true),
        font("jetbrains_mono", "JetBrains Mono", FontCategory.MONO, supportsThai = false, forClock = true, forBody = true),
        font("ibm_plex_mono", "IBM Plex Mono", FontCategory.MONO, supportsThai = false, forClock = true, forBody = true),
        font("share_tech_mono", "Share Tech Mono", FontCategory.MONO, supportsThai = false, forClock = true, forBody = true),
        // --- Latin pixel ------------------------------------------------------------------------
        font("vt323", "VT323", FontCategory.PIXEL, supportsThai = false, forClock = true, forBody = true),
        font("press_start_2p", "Press Start 2P", FontCategory.PIXEL, supportsThai = false, forClock = true, forBody = false),
        font("silkscreen", "Silkscreen", FontCategory.PIXEL, supportsThai = false, forClock = true, forBody = false),
        // --- Latin display / serif (clock digits and headings) ----------------------------------
        font("orbitron", "Orbitron", FontCategory.DISPLAY, supportsThai = false, forClock = true, forBody = false),
        font("bebas_neue", "Bebas Neue", FontCategory.DISPLAY, supportsThai = false, forClock = true, forBody = false),
        font("archivo_black", "Archivo Black", FontCategory.DISPLAY, supportsThai = false, forClock = true, forBody = false),
        font("playfair_display", "Playfair Display", FontCategory.SERIF, supportsThai = false, forClock = true, forBody = true),
        font("dm_serif_display", "DM Serif Display", FontCategory.SERIF, supportsThai = false, forClock = true, forBody = false),
        font("major_mono_display", "Major Mono Display", FontCategory.DISPLAY, supportsThai = false, forClock = true, forBody = false),
        font("monoton", "Monoton", FontCategory.DISPLAY, supportsThai = false, forClock = true, forBody = false),
        font("audiowide", "Audiowide", FontCategory.DISPLAY, supportsThai = false, forClock = true, forBody = false),
        font("oswald", "Oswald", FontCategory.DISPLAY, supportsThai = false, forClock = true, forBody = false),
    )
}
