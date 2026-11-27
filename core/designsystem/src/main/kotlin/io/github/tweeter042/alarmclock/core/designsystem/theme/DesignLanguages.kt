package io.github.tweeter042.alarmclock.core.designsystem.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.tweeter042.alarmclock.core.model.settings.AccessibilitySettings
import io.github.tweeter042.alarmclock.core.model.theme.PaletteColors
import io.github.tweeter042.alarmclock.core.model.theme.ThemeCatalog
import io.github.tweeter042.alarmclock.core.model.theme.ThemeId

/** Fonts after resolving the user's choices (body/clock) and the theme's heading font. */
data class ResolvedFonts(val body: FontFamily, val display: FontFamily, val clock: FontFamily)

/** Shadow description before palette colours are applied. */
internal sealed interface ShadowKind {
    data object None : ShadowKind
    data class Soft(val radius: Dp, val offsetY: Dp) : ShadowKind
    data class Hard(val x: Dp, val y: Dp) : ShadowKind
    data class Glow(val radius: Dp, val alpha: Float) : ShadowKind
}

/**
 * The palette-independent half of a theme: shapes, borders, elevation, type personality, motion,
 * spacing, icons, background and component traits. One instance per [ThemeId].
 */
internal data class DesignLanguage(
    val shapes: AppShapes,
    val borderWidth: Dp,
    /** Multiplier of the palette outline alpha for card borders. */
    val borderAlpha: Float,
    val outlineFilledControls: Boolean = false,
    val borderStyle: BorderStyle = BorderStyle.Solid,
    val shadow: ShadowKind,
    val type: TypeCharacter,
    /** FontCatalog id of the heading font; null = headings use the body font. */
    val displayFontId: String? = null,
    val motion: MotionSpec,
    val spacing: Spacing,
    val iconStyle: IconStyle,
    val background: BackgroundStyle,
    val traits: DesignTraits,
)

internal object DesignLanguages {

    fun of(id: ThemeId): DesignLanguage = when (id) {
        ThemeId.MINIMAL -> minimal
        ThemeId.BRUTAL -> brutal
        ThemeId.RETRO_LCD -> retroLcd
        ThemeId.FLIP_CLOCK -> flipClock
        ThemeId.GLASS -> glass
        ThemeId.MATERIAL_YOU -> materialYou
        ThemeId.TERMINAL -> terminal
        ThemeId.NEON -> neon
        ThemeId.PAPER -> paper
        ThemeId.PIXEL -> pixel
        ThemeId.ANALOG -> analog
        ThemeId.AMOLED -> amoled
    }

    private val minimal = DesignLanguage(
        shapes = AppShapes.rounded(
            card = 22.dp, button = PillShape, chip = PillShape, field = 14.dp, dialog = 28.dp, sheet = 28.dp,
            fab = RoundedCornerShape(20.dp), checkbox = 6.dp,
        ),
        borderWidth = 1.dp,
        borderAlpha = 0.55f,
        shadow = ShadowKind.None,
        type = TypeCharacter(
            displayWeight = FontWeight.Light, headlineWeight = FontWeight.Light, titleWeight = FontWeight.Normal,
            bodyWeight = FontWeight.Normal, labelWeight = FontWeight.Normal, clockWeight = FontWeight.Thin,
            displayLetterSpacing = (-0.02).em, clockLetterSpacing = (-0.02).em, largeTitleSize = 38f,
        ),
        motion = MotionSpec(240, 340, 520, MotionSpec.Standard),
        spacing = Spacing(screen = 24.dp, card = 20.dp, gutter = 16.dp, listItem = 14.dp),
        iconStyle = IconStyle.Outlined,
        background = BackgroundStyle.Plain,
        traits = DesignTraits(),
    )

    private val brutal = DesignLanguage(
        shapes = AppShapes.square(),
        borderWidth = 2.5.dp,
        borderAlpha = 1f,
        outlineFilledControls = true,
        shadow = ShadowKind.Hard(5.dp, 5.dp),
        type = TypeCharacter(
            displayWeight = FontWeight.Normal, headlineWeight = FontWeight.Normal, titleWeight = FontWeight.Bold,
            bodyWeight = FontWeight.Normal, labelWeight = FontWeight.Bold, clockWeight = FontWeight.Bold,
            displayLetterSpacing = (-0.02).em, labelLetterSpacing = 0.06.em, clockLetterSpacing = (-0.04).em,
            largeTitleSize = 36f,
        ),
        displayFontId = "archivo_black",
        motion = MotionSpec(90, 140, 220, MotionSpec.Snappy),
        spacing = Spacing(screen = 16.dp, card = 16.dp, gutter = 18.dp, listItem = 12.dp),
        iconStyle = IconStyle.Sharp,
        background = BackgroundStyle.DotGrid,
        traits = DesignTraits(
            pressEffect = PressEffect.ShiftIntoShadow, dividerStyle = DividerStyle.Thick,
            navIndicator = NavIndicator.Block, selectionStyle = SelectionStyle.Square,
            headerDecor = HeaderDecor.Rule, uppercaseLabels = true, labelLetterSpacing = 0.06.em,
        ),
    )

    private val retroLcd = DesignLanguage(
        shapes = AppShapes.rounded(
            card = 6.dp, button = RoundedCornerShape(6.dp), chip = RoundedCornerShape(4.dp), field = 4.dp,
            dialog = 8.dp, sheet = 10.dp, fab = RoundedCornerShape(10.dp),
            indicator = RoundedCornerShape(4.dp), dayToggle = RoundedCornerShape(4.dp), checkbox = 2.dp,
            iconContainer = RoundedCornerShape(6.dp),
        ).copy(track = RoundedCornerShape(4.dp), thumb = RoundedCornerShape(2.dp), badge = RoundedCornerShape(3.dp)),
        borderWidth = 2.dp,
        borderAlpha = 1f,
        shadow = ShadowKind.Hard(0.dp, 3.dp),
        type = TypeCharacter(
            displayWeight = FontWeight.Bold, headlineWeight = FontWeight.Bold, titleWeight = FontWeight.Bold,
            bodyWeight = FontWeight.Normal, labelWeight = FontWeight.Bold, clockWeight = FontWeight.Normal,
            displayLetterSpacing = 0.02.em, labelLetterSpacing = 0.08.em, largeTitleSize = 34f,
        ),
        motion = MotionSpec(60, 90, 140, LinearEasing),
        spacing = Spacing(screen = 16.dp, card = 14.dp, gutter = 12.dp, listItem = 12.dp),
        iconStyle = IconStyle.Sharp,
        background = BackgroundStyle.LcdPanel,
        traits = DesignTraits(
            pressEffect = PressEffect.Invert, dividerStyle = DividerStyle.Dashed,
            navIndicator = NavIndicator.Block, selectionStyle = SelectionStyle.Square,
            headerDecor = HeaderDecor.Rule, uppercaseLabels = true, labelLetterSpacing = 0.08.em,
            bevel = true, segmentedProgress = true,
        ),
    )

    private val flipClock = DesignLanguage(
        shapes = AppShapes.rounded(
            card = 14.dp, button = RoundedCornerShape(10.dp), chip = RoundedCornerShape(8.dp), field = 10.dp,
            dialog = 18.dp, sheet = 22.dp, fab = RoundedCornerShape(16.dp),
            indicator = RoundedCornerShape(10.dp), dayToggle = RoundedCornerShape(10.dp), checkbox = 4.dp,
            iconContainer = RoundedCornerShape(10.dp),
        ),
        borderWidth = 0.dp,
        borderAlpha = 0f,
        shadow = ShadowKind.Soft(12.dp, 4.dp),
        type = TypeCharacter(
            displayWeight = FontWeight.Bold, headlineWeight = FontWeight.Medium, titleWeight = FontWeight.Normal,
            bodyWeight = FontWeight.Light, labelWeight = FontWeight.Normal, buttonWeight = FontWeight.Normal,
            clockWeight = FontWeight.Bold, displayLetterSpacing = 0.01.em, labelLetterSpacing = 0.06.em,
            largeTitleSize = 40f,
        ),
        displayFontId = "oswald",
        motion = MotionSpec(200, 320, 600, MotionSpec.Standard),
        spacing = Spacing(screen = 20.dp, card = 18.dp, gutter = 14.dp, listItem = 13.dp),
        iconStyle = IconStyle.Rounded,
        background = BackgroundStyle.FlipDesk,
        traits = DesignTraits(
            pressEffect = PressEffect.Scale, navIndicator = NavIndicator.Block,
            uppercaseLabels = true, labelLetterSpacing = 0.06.em,
        ),
    )

    private val glass = DesignLanguage(
        shapes = AppShapes.rounded(
            card = 28.dp, button = PillShape, chip = PillShape, field = 18.dp, dialog = 32.dp, sheet = 32.dp,
            fab = RoundedCornerShape(22.dp), checkbox = 8.dp,
        ),
        borderWidth = 1.dp,
        borderAlpha = 1f,
        shadow = ShadowKind.Soft(28.dp, 10.dp),
        type = TypeCharacter(
            displayWeight = FontWeight.Light, headlineWeight = FontWeight.Light, titleWeight = FontWeight.Normal,
            bodyWeight = FontWeight.Normal, labelWeight = FontWeight.Normal, clockWeight = FontWeight.ExtraLight,
            displayLetterSpacing = (-0.01).em, clockLetterSpacing = (-0.01).em, largeTitleSize = 38f,
        ),
        motion = MotionSpec(320, 450, 700, MotionSpec.Gentle),
        spacing = Spacing(screen = 20.dp, card = 20.dp, gutter = 14.dp, listItem = 14.dp),
        iconStyle = IconStyle.Rounded,
        background = BackgroundStyle.GlassBlobs,
        traits = DesignTraits(
            cardFill = CardFill.Frosted, pressEffect = PressEffect.Scale, gradientButtons = true,
        ),
    )

    private val materialYou = DesignLanguage(
        shapes = AppShapes.rounded(
            card = 16.dp, button = PillShape, chip = RoundedCornerShape(8.dp), field = 4.dp, dialog = 28.dp,
            sheet = 28.dp, fab = RoundedCornerShape(16.dp), checkbox = 2.dp,
        ),
        borderWidth = 0.dp,
        borderAlpha = 0f,
        shadow = ShadowKind.None,
        type = TypeCharacter(
            displayWeight = FontWeight.Normal, headlineWeight = FontWeight.Normal, titleWeight = FontWeight.Normal,
            bodyWeight = FontWeight.Normal, labelWeight = FontWeight.Normal, clockWeight = FontWeight.Normal,
            largeTitleSize = 36f,
        ),
        motion = MotionSpec(250, 400, 600, MotionSpec.Emphasized),
        spacing = Spacing(screen = 16.dp, card = 16.dp, gutter = 12.dp, listItem = 12.dp),
        iconStyle = IconStyle.Rounded,
        background = BackgroundStyle.Tonal,
        traits = DesignTraits(),
    )

    private val terminal = DesignLanguage(
        shapes = AppShapes.square(),
        borderWidth = 1.dp,
        borderAlpha = 1f,
        borderStyle = BorderStyle.Dashed,
        shadow = ShadowKind.None,
        type = TypeCharacter(
            displayWeight = FontWeight.Bold, headlineWeight = FontWeight.Bold, titleWeight = FontWeight.Bold,
            bodyWeight = FontWeight.Normal, labelWeight = FontWeight.Normal, buttonWeight = FontWeight.Bold,
            clockWeight = FontWeight.Bold, largeTitleSize = 30f, bodyScale = 0.94f, displayScale = 0.94f,
        ),
        motion = MotionSpec(0, 60, 100, LinearEasing),
        spacing = Spacing(screen = 14.dp, card = 12.dp, gutter = 10.dp, listItem = 10.dp),
        iconStyle = IconStyle.Sharp,
        background = BackgroundStyle.Scanlines,
        traits = DesignTraits(
            pressEffect = PressEffect.Invert, dividerStyle = DividerStyle.Dashed,
            navIndicator = NavIndicator.Bracket, selectionStyle = SelectionStyle.Text,
            headerDecor = HeaderDecor.Prompt, bracketedLabels = true, textGlow = true, segmentedProgress = true,
            labelLetterSpacing = 0.sp,
        ),
    )

    private val neon = DesignLanguage(
        shapes = AppShapes.cut(card = 14.dp, button = 10.dp, chip = 6.dp),
        borderWidth = 1.5.dp,
        borderAlpha = 1f,
        outlineFilledControls = true,
        shadow = ShadowKind.Glow(18.dp, 0.55f),
        type = TypeCharacter(
            displayWeight = FontWeight.Normal, headlineWeight = FontWeight.Normal, titleWeight = FontWeight.Normal,
            bodyWeight = FontWeight.Light, labelWeight = FontWeight.Normal, clockWeight = FontWeight.Bold,
            displayLetterSpacing = 0.02.em, labelLetterSpacing = 0.08.em, largeTitleSize = 32f,
        ),
        displayFontId = "audiowide",
        motion = MotionSpec(200, 300, 480, MotionSpec.Standard),
        spacing = Spacing(screen = 18.dp, card = 18.dp, gutter = 16.dp, listItem = 13.dp),
        iconStyle = IconStyle.Outlined,
        background = BackgroundStyle.NeonGrid,
        traits = DesignTraits(
            pressEffect = PressEffect.Glow, dividerStyle = DividerStyle.Glow,
            navIndicator = NavIndicator.GlowLine, headerDecor = HeaderDecor.Glow,
            uppercaseLabels = true, labelLetterSpacing = 0.08.em, textGlow = true,
        ),
    )

    private val paper = DesignLanguage(
        shapes = AppShapes.rounded(
            card = 2.dp, button = RoundedCornerShape(2.dp), chip = RoundedCornerShape(2.dp), field = 0.dp,
            dialog = 4.dp, sheet = 6.dp, fab = RoundedCornerShape(4.dp), indicator = RectangleShape,
            dayToggle = CircleShape, checkbox = 0.dp, iconContainer = CircleShape,
        ),
        borderWidth = 1.dp,
        borderAlpha = 1f,
        shadow = ShadowKind.None,
        type = TypeCharacter(
            displayWeight = FontWeight.Bold, headlineWeight = FontWeight.Bold, titleWeight = FontWeight.Bold,
            bodyWeight = FontWeight.Normal, labelWeight = FontWeight.Bold, clockWeight = FontWeight.Normal,
            displayLetterSpacing = (-0.01).em, labelLetterSpacing = 0.12.em, largeTitleSize = 40f,
            displayRoles = setOf(TypeRole.Display, TypeRole.Headline, TypeRole.Title),
        ),
        displayFontId = "playfair_display",
        motion = MotionSpec(260, 380, 560, MotionSpec.Gentle),
        spacing = Spacing(screen = 20.dp, card = 16.dp, gutter = 12.dp, listItem = 13.dp),
        iconStyle = IconStyle.Outlined,
        background = BackgroundStyle.PaperGrain,
        traits = DesignTraits(
            dividerStyle = DividerStyle.Double, navIndicator = NavIndicator.Underline,
            headerDecor = HeaderDecor.Masthead, uppercaseLabels = true, labelLetterSpacing = 0.12.em,
        ),
    )

    private val pixel = DesignLanguage(
        shapes = AppShapes.pixel(4.dp),
        borderWidth = 3.dp,
        borderAlpha = 1f,
        outlineFilledControls = true,
        shadow = ShadowKind.Hard(4.dp, 4.dp),
        type = TypeCharacter(
            displayWeight = FontWeight.Normal, headlineWeight = FontWeight.Normal, titleWeight = FontWeight.Normal,
            bodyWeight = FontWeight.Normal, labelWeight = FontWeight.Normal, clockWeight = FontWeight.Normal,
            labelLetterSpacing = 0.02.em, largeTitleSize = 34f, bodyScale = 1.3f, displayScale = 0.62f,
            titleScale = 0.7f, labelScale = 0.64f,
            displayRoles = setOf(TypeRole.Display, TypeRole.Headline, TypeRole.Title, TypeRole.Label),
        ),
        displayFontId = "press_start_2p",
        motion = MotionSpec(120, 200, 320, LinearEasing, steps = 4),
        spacing = Spacing(screen = 16.dp, card = 14.dp, gutter = 16.dp, listItem = 12.dp),
        iconStyle = IconStyle.Sharp,
        background = BackgroundStyle.PixelGrid,
        traits = DesignTraits(
            pressEffect = PressEffect.ShiftIntoShadow, dividerStyle = DividerStyle.Dotted,
            navIndicator = NavIndicator.Block, selectionStyle = SelectionStyle.Pixel,
            headerDecor = HeaderDecor.Pixel, uppercaseLabels = true, labelLetterSpacing = 0.02.em,
            bevel = true, segmentedProgress = true,
        ),
    )

    private val analog = DesignLanguage(
        shapes = AppShapes.rounded(
            card = 20.dp, button = PillShape, chip = PillShape, field = 12.dp, dialog = 26.dp, sheet = 28.dp,
            fab = CircleShape, checkbox = 5.dp,
        ),
        borderWidth = 1.dp,
        borderAlpha = 0.45f,
        shadow = ShadowKind.Soft(16.dp, 5.dp),
        type = TypeCharacter(
            displayWeight = FontWeight.Normal, headlineWeight = FontWeight.Normal, titleWeight = FontWeight.Bold,
            bodyWeight = FontWeight.Normal, labelWeight = FontWeight.Bold, buttonWeight = FontWeight.Normal,
            clockWeight = FontWeight.Normal, displayLetterSpacing = 0.01.em, labelLetterSpacing = 0.1.em,
            largeTitleSize = 38f,
        ),
        displayFontId = "playfair_display",
        motion = MotionSpec(280, 420, 650, MotionSpec.Gentle),
        spacing = Spacing(screen = 20.dp, card = 18.dp, gutter = 14.dp, listItem = 14.dp),
        iconStyle = IconStyle.Outlined,
        background = BackgroundStyle.WatchDial,
        traits = DesignTraits(pressEffect = PressEffect.Scale, labelLetterSpacing = 0.1.em),
    )

    private val amoled = DesignLanguage(
        shapes = AppShapes.rounded(
            card = 20.dp, button = PillShape, chip = PillShape, field = 14.dp, dialog = 24.dp, sheet = 24.dp,
            fab = CircleShape, checkbox = 6.dp,
        ),
        borderWidth = 1.dp,
        borderAlpha = 1f,
        shadow = ShadowKind.None,
        type = TypeCharacter(
            displayWeight = FontWeight.ExtraLight, headlineWeight = FontWeight.ExtraLight, titleWeight = FontWeight.Light,
            bodyWeight = FontWeight.Light, labelWeight = FontWeight.Normal, clockWeight = FontWeight.Light,
            displayLetterSpacing = 0.em, largeTitleSize = 38f,
        ),
        motion = MotionSpec(200, 300, 450, MotionSpec.Standard),
        spacing = Spacing(screen = 20.dp, card = 18.dp, gutter = 12.dp, listItem = 14.dp),
        iconStyle = IconStyle.Outlined,
        background = BackgroundStyle.PureBlack,
        traits = DesignTraits(
            cardFill = CardFill.Outline, navIndicator = NavIndicator.Dot, outlinedPrimary = true,
        ),
    )

}

/** Builds the full token set for a theme + palette + resolved fonts + accessibility. */
internal fun buildThemeTokens(
    themeId: ThemeId,
    paletteId: String,
    palette: PaletteColors,
    isDark: Boolean,
    isDynamic: Boolean,
    fonts: ResolvedFonts,
    accessibility: AccessibilitySettings,
    thaiUi: Boolean = false,
): ThemeTokens {
    val lang = DesignLanguages.of(themeId)
    val info = ThemeCatalog.theme(themeId)
    val hc = accessibility.highContrast

    val frostAlpha = when {
        lang.traits.cardFill != CardFill.Frosted -> 1f
        hc -> 0.94f
        isDark -> 0.14f
        else -> 0.62f
    }
    val glassBorder = when {
        lang.traits.cardFill != CardFill.Frosted -> null
        isDark -> Color.White.copy(alpha = if (hc) 0.5f else 0.16f)
        else -> Color.White.copy(alpha = if (hc) 0.9f else 0.7f)
    }
    var colors = AppColors.fromPalette(
        p = palette,
        isDark = isDark,
        cardAlpha = if (lang.traits.cardFill == CardFill.Outline) 1f else frostAlpha,
        cardBorderAlpha = lang.borderAlpha,
        cardBorderOverride = glassBorder,
        isDynamic = isDynamic,
    )
    if (lang.traits.cardFill == CardFill.Outline) {
        colors = colors.copy(card = colors.background)
    }
    if (hc) colors = colors.highContrast()

    val borderWidth = if (hc) maxOf(lang.borderWidth, 1.5.dp) else lang.borderWidth
    val border = BorderSpec(
        width = borderWidth,
        color = colors.cardBorder,
        outlineFilledControls = lang.outlineFilledControls,
        style = lang.borderStyle,
    )

    val shadowKind = if (lang.traits.cardFill == CardFill.Frosted && isDark) ShadowKind.None else lang.shadow
    val shadow: ShadowSpec = when (val s = shadowKind) {
        ShadowKind.None -> ShadowSpec.None
        is ShadowKind.Soft -> ShadowSpec.Soft(s.radius, s.offsetY, colors.shadow)
        is ShadowKind.Hard -> ShadowSpec.HardOffset(s.x, s.y, colors.shadow)
        is ShadowKind.Glow -> ShadowSpec.Glow(s.radius, colors.glow.copy(alpha = s.alpha))
    }
    val smallShadow: ShadowSpec = when (shadow) {
        ShadowSpec.None -> ShadowSpec.None
        is ShadowSpec.Soft -> shadow.copy(radius = shadow.radius * 0.5f, offsetY = shadow.offsetY * 0.5f)
        is ShadowSpec.HardOffset -> shadow.copy(x = shadow.x * 0.6f, y = shadow.y * 0.6f)
        is ShadowSpec.Glow -> shadow.copy(radius = shadow.radius * 0.55f)
    }

    val typography = AppTypography.build(fonts.body, fonts.display, fonts.clock, if (thaiUi) lang.type.forThaiUi() else lang.type)

    return ThemeTokens(
        themeId = themeId,
        paletteId = paletteId,
        isDark = isDark,
        colors = colors,
        shapes = lang.shapes,
        border = border,
        shadow = shadow,
        smallShadow = smallShadow,
        typography = typography,
        clockFace = info.clockFace,
        background = lang.background,
        motion = lang.motion.withReducedMotion(accessibility.reduceMotion),
        spacing = lang.spacing,
        iconStyle = lang.iconStyle,
        traits = lang.traits.copy(frostAlpha = frostAlpha),
        accessibility = accessibility,
    )
}

/**
 * High contrast: text reaches WCAG AAA (7:1) where the palette allows, secondary text becomes
 * primary text and outlines become solid ink.
 */
internal fun AppColors.highContrast(): AppColors {
    val ink = ColorContrast.ensure(onBackground, background, 7f)
    val surfaceInk = ColorContrast.ensure(onSurface, surface, 7f)
    val variantInk = ColorContrast.ensure(onSurface, surfaceVariant, 7f)
    val strongOutline = outline.mix(ink, 0.65f).copy(alpha = 1f)
    return copy(
        onBackground = ink,
        onSurface = surfaceInk,
        onCard = surfaceInk,
        onSurfaceVariant = variantInk,
        outline = strongOutline,
        outlineVariant = strongOutline,
        cardBorder = cardBorder.copy(alpha = 1f).let { if (ColorContrast.ratio(it, background) < 3f) strongOutline else it },
        clockDigit = ColorContrast.ensure(clockDigit, background, 4.5f),
        material = material.copy(
            onBackground = ink,
            onSurface = surfaceInk,
            onSurfaceVariant = variantInk,
            outline = strongOutline,
            outlineVariant = strongOutline,
        ),
    )
}
