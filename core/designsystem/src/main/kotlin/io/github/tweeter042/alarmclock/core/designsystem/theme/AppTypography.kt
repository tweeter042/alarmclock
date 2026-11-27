package io.github.tweeter042.alarmclock.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Text styles of the active theme: the Material 3 type scale (so stock widgets match) plus the
 * One UI large title, bar title, section labels, buttons and the clock styles.
 *
 * Clock styles carry family/weight/letter spacing only; sizes come from `ClockSize`.
 * Line heights are generous because Thai stacks vowels and tone marks above and below.
 */
@Immutable
data class AppTypography(
    val displayLarge: TextStyle,
    val displayMedium: TextStyle,
    val displaySmall: TextStyle,
    val headlineLarge: TextStyle,
    val headlineMedium: TextStyle,
    val headlineSmall: TextStyle,
    val titleLarge: TextStyle,
    val titleMedium: TextStyle,
    val titleSmall: TextStyle,
    val bodyLarge: TextStyle,
    val bodyMedium: TextStyle,
    val bodySmall: TextStyle,
    val labelLarge: TextStyle,
    val labelMedium: TextStyle,
    val labelSmall: TextStyle,
    /** Expanded One UI header title. */
    val largeTitle: TextStyle,
    /** Collapsed top bar title. */
    val barTitle: TextStyle,
    /** Section headers above groups of rows. */
    val sectionLabel: TextStyle,
    /** Button labels (Medium size; other sizes scale it). */
    val button: TextStyle,
    /** Big clock digits (no size). */
    val clock: TextStyle,
    /** AM/PM and other small clock captions (no size). */
    val clockLabel: TextStyle,
    val bodyFamily: FontFamily,
    val displayFamily: FontFamily,
    val clockFamily: FontFamily,
) {
    val material: Typography
        get() = Typography(
            displayLarge = displayLarge, displayMedium = displayMedium, displaySmall = displaySmall,
            headlineLarge = headlineLarge, headlineMedium = headlineMedium, headlineSmall = headlineSmall,
            titleLarge = titleLarge, titleMedium = titleMedium, titleSmall = titleSmall,
            bodyLarge = bodyLarge, bodyMedium = bodyMedium, bodySmall = bodySmall,
            labelLarge = labelLarge, labelMedium = labelMedium, labelSmall = labelSmall,
        )

    internal companion object {
        private val lineStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        )
        private val platform = PlatformTextStyle(includeFontPadding = false)

        fun build(
            body: FontFamily,
            display: FontFamily,
            clock: FontFamily,
            c: TypeCharacter,
        ): AppTypography {
            fun style(
                family: FontFamily,
                size: Float,
                weight: FontWeight,
                lineFactor: Float,
                letterSpacing: TextUnit = 0.sp,
                scale: Float = 1f,
            ) = TextStyle(
                fontFamily = family,
                fontWeight = weight,
                fontSize = (size * scale).sp,
                lineHeight = (size * scale * lineFactor).sp,
                letterSpacing = letterSpacing,
                lineHeightStyle = lineStyle,
                platformStyle = platform,
            )

            val displayFamily = if (c.displayRoles.contains(TypeRole.Display)) display else body
            val headlineFamily = if (c.displayRoles.contains(TypeRole.Headline)) display else body
            val titleFamily = if (c.displayRoles.contains(TypeRole.Title)) display else body
            val labelFamily = if (c.displayRoles.contains(TypeRole.Label)) display else body
            val ds = if (c.displayRoles.contains(TypeRole.Display)) c.displayScale else c.bodyScale
            val hs = if (c.displayRoles.contains(TypeRole.Headline)) c.displayScale else c.bodyScale
            val ts = if (c.displayRoles.contains(TypeRole.Title)) c.titleScale else c.bodyScale
            val ls = if (c.displayRoles.contains(TypeRole.Label)) c.labelScale else c.bodyScale
            val bs = c.bodyScale
            val dls = c.displayLetterSpacing

            return AppTypography(
                displayLarge = style(displayFamily, 57f, c.displayWeight, 1.18f, dls, ds),
                displayMedium = style(displayFamily, 45f, c.displayWeight, 1.2f, dls, ds),
                displaySmall = style(displayFamily, 36f, c.displayWeight, 1.25f, dls, ds),
                headlineLarge = style(headlineFamily, 32f, c.headlineWeight, 1.3f, dls, hs),
                headlineMedium = style(headlineFamily, 28f, c.headlineWeight, 1.32f, dls, hs),
                headlineSmall = style(headlineFamily, 24f, c.headlineWeight, 1.36f, 0.sp, hs),
                titleLarge = style(titleFamily, 22f, c.titleWeight, 1.4f, 0.sp, ts),
                titleMedium = style(titleFamily, 16f, c.titleWeight, 1.5f, 0.01.em, ts),
                titleSmall = style(titleFamily, 14f, c.titleWeight, 1.5f, 0.01.em, ts),
                bodyLarge = style(body, 16f, c.bodyWeight, 1.5f, 0.sp, bs),
                bodyMedium = style(body, 14f, c.bodyWeight, 1.5f, 0.sp, bs),
                bodySmall = style(body, 12f, c.bodyWeight, 1.5f, 0.01.em, bs),
                labelLarge = style(labelFamily, 14f, c.labelWeight, 1.45f, c.labelLetterSpacing, ls),
                labelMedium = style(labelFamily, 12f, c.labelWeight, 1.45f, c.labelLetterSpacing, ls),
                labelSmall = style(labelFamily, 11f, c.labelWeight, 1.45f, c.labelLetterSpacing, ls),
                largeTitle = style(displayFamily, c.largeTitleSize, c.displayWeight, 1.22f, dls, ds),
                barTitle = style(titleFamily, 20f, c.titleWeight, 1.35f, 0.sp, ts),
                sectionLabel = style(labelFamily, 13f, c.labelWeight, 1.45f, c.labelLetterSpacing, ls),
                button = style(labelFamily, 15f, c.buttonWeight, 1.35f, c.labelLetterSpacing, ls),
                clock = TextStyle(
                    fontFamily = clock,
                    fontWeight = c.clockWeight,
                    letterSpacing = c.clockLetterSpacing,
                    fontFeatureSettings = "tnum",
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                    platformStyle = platform,
                ),
                clockLabel = TextStyle(
                    fontFamily = labelFamily,
                    fontWeight = c.labelWeight,
                    letterSpacing = 0.04.em,
                    platformStyle = platform,
                ),
                bodyFamily = body,
                displayFamily = display,
                clockFamily = clock,
            )
        }
    }
}

/** Which type roles use the theme's display (heading) font instead of the body font. */
internal enum class TypeRole { Display, Headline, Title, Label }

/** The typographic personality of a design language. */
@Immutable
internal data class TypeCharacter(
    val displayWeight: FontWeight = FontWeight.Normal,
    val headlineWeight: FontWeight = FontWeight.Normal,
    val titleWeight: FontWeight = FontWeight.Normal,
    val bodyWeight: FontWeight = FontWeight.Normal,
    val labelWeight: FontWeight = FontWeight.Normal,
    val buttonWeight: FontWeight = labelWeight,
    val clockWeight: FontWeight = FontWeight.Normal,
    val displayLetterSpacing: TextUnit = 0.sp,
    val labelLetterSpacing: TextUnit = 0.01.em,
    val clockLetterSpacing: TextUnit = 0.sp,
    val largeTitleSize: Float = 36f,
    /** Scales every body-font style (VT323 is optically small). */
    val bodyScale: Float = 1f,
    /** Scales display/headline styles drawn with the display font (Press Start 2P is very wide). */
    val displayScale: Float = 1f,
    val titleScale: Float = displayScale,
    val labelScale: Float = displayScale,
    val displayRoles: Set<TypeRole> = setOf(TypeRole.Display, TypeRole.Headline),
)

/** True when a type scale deviates enough to be an optical correction for a specific font. */
internal fun Float.isRescaled(): Boolean = kotlin.math.abs(this - 1f) > 0.1f

/** Thai UI: optical rescaling for Latin display fonts does not apply to their Thai partners. */
internal fun TypeCharacter.forThaiUi(): TypeCharacter {
    fun normal(scale: Float) = if (scale.isRescaled()) 1f else scale
    return copy(
        bodyScale = normal(bodyScale),
        displayScale = normal(displayScale),
        titleScale = normal(titleScale),
        labelScale = normal(labelScale),
    )
}
