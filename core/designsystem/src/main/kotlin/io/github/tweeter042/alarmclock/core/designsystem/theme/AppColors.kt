package io.github.tweeter042.alarmclock.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import io.github.tweeter042.alarmclock.core.model.theme.PaletteColors

/**
 * The colours of the active theme: every palette role from core:model plus roles derived for
 * the design language (card fill, bars, flip cards, …) and the mapped Material 3 [material]
 * scheme so stock M3 widgets match.
 *
 * Feature code reads these through `AppTheme.colors`; never hard-code colours.
 */
@Immutable
data class AppColors(
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val primary: Color,
    val onPrimary: Color,
    /** Tonal version of primary for selected states and soft emphasis. */
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val accent: Color,
    val onAccent: Color,
    val outline: Color,
    val outlineVariant: Color,
    val error: Color,
    val onError: Color,
    val success: Color,
    val warning: Color,
    val clockDigit: Color,
    val clockGhost: Color,
    val shadow: Color,
    val glow: Color,
    /** Fill of cards, dialogs and sheets (translucent for Glass). */
    val card: Color,
    val onCard: Color,
    /** Border colour of cards and outlined controls. */
    val cardBorder: Color,
    /** Top/bottom bars once content scrolls beneath them. */
    val bar: Color,
    val scrim: Color,
    /** Flip-clock card and the digit colour printed on it. */
    val flipCard: Color,
    val flipDigit: Color,
    val isDark: Boolean,
    /** True when colours come from the wallpaper (Material You "dynamic"). */
    val isDynamic: Boolean,
    val material: ColorScheme,
) {
    /** Text/icon colour for disabled content. */
    val disabledContent: Color get() = onSurface.copy(alpha = 0.38f)

    /** Container colour for disabled controls. */
    val disabledContainer: Color get() = onSurface.copy(alpha = 0.12f)

    companion object {
        /**
         * Maps palette colours to [AppColors]. [cardAlpha] < 1 makes cards translucent (Glass);
         * [cardBorderAlpha] scales the outline for card borders.
         */
        fun fromPalette(
            p: PaletteColors,
            isDark: Boolean,
            cardAlpha: Float = 1f,
            cardBorderAlpha: Float = 1f,
            cardBorderOverride: Color? = null,
            isDynamic: Boolean = false,
        ): AppColors {
            val background = Color(p.background)
            val surface = Color(p.surface)
            val primary = Color(p.primary)
            val onSurface = Color(p.onSurface)
            val primaryContainer = primary.mix(surface, if (isDark) 0.72f else 0.86f)
            val onPrimaryContainer = ColorContrast.ensure(
                if (isDark) primary.mix(Color.White, 0.55f) else primary.mix(Color.Black, 0.45f),
                primaryContainer,
            )
            val outline = Color(p.outline)
            val clockGhost = Color(p.clockGhost)
            val clockDigit = Color(p.clockDigit)
            val flipCard = clockGhost.copy(alpha = 1f)
            val flipDigit = if (ColorContrast.ratio(clockDigit, flipCard) >= 4.5f) {
                clockDigit
            } else {
                ColorContrast.bestOn(
                    flipCard,
                    listOf(background, Color(p.onBackground), surface, onSurface, Color.White, Color.Black),
                )
            }
            val card = surface.copy(alpha = cardAlpha)
            return AppColors(
                background = background,
                onBackground = Color(p.onBackground),
                surface = surface,
                onSurface = onSurface,
                surfaceVariant = Color(p.surfaceVariant),
                onSurfaceVariant = Color(p.onSurfaceVariant),
                primary = primary,
                onPrimary = Color(p.onPrimary),
                primaryContainer = primaryContainer,
                onPrimaryContainer = onPrimaryContainer,
                secondary = Color(p.secondary),
                onSecondary = Color(p.onSecondary),
                accent = Color(p.accent),
                onAccent = Color(p.onAccent),
                outline = outline,
                outlineVariant = outline.mix(background, 0.55f),
                error = Color(p.error),
                onError = Color(p.onError),
                success = Color(p.success),
                warning = Color(p.warning),
                clockDigit = clockDigit,
                clockGhost = clockGhost,
                shadow = Color(p.shadow),
                glow = Color(p.glow),
                card = card,
                onCard = onSurface,
                cardBorder = cardBorderOverride ?: outline.copy(alpha = outline.alpha * cardBorderAlpha),
                bar = if (cardAlpha < 1f) background.copy(alpha = 0.82f) else background,
                scrim = Color.Black.copy(alpha = if (isDark) 0.62f else 0.42f),
                flipCard = flipCard,
                flipDigit = flipDigit,
                isDark = isDark,
                isDynamic = isDynamic,
                material = materialScheme(p, isDark, primaryContainer, onPrimaryContainer),
            )
        }

        /** Builds palette colours from a Material You (wallpaper) scheme. */
        fun paletteFromScheme(s: ColorScheme, isDark: Boolean): PaletteColors = PaletteColors(
            background = s.surface.toArgb(),
            onBackground = s.onSurface.toArgb(),
            surface = s.surfaceContainerLow.toArgb(),
            onSurface = s.onSurface.toArgb(),
            surfaceVariant = s.surfaceContainerHighest.toArgb(),
            onSurfaceVariant = s.onSurfaceVariant.toArgb(),
            primary = s.primary.toArgb(),
            onPrimary = s.onPrimary.toArgb(),
            secondary = s.secondaryContainer.toArgb(),
            onSecondary = s.onSecondaryContainer.toArgb(),
            accent = s.tertiary.toArgb(),
            onAccent = s.onTertiary.toArgb(),
            outline = s.outline.toArgb(),
            error = s.error.toArgb(),
            onError = s.onError.toArgb(),
            success = if (isDark) 0xFF7DD99B else 0xFF1B7F3B,
            warning = if (isDark) 0xFFF2C14E else 0xFF8A5A00,
            clockDigit = s.primary.toArgb(),
            clockGhost = s.primary.copy(alpha = 0.12f).toArgb(),
            shadow = Color.Black.copy(alpha = if (isDark) 0.45f else 0.16f).toArgb(),
            glow = s.primary.toArgb(),
        )

        private fun Color.toArgb(): Long = (
            ((alpha * 255f + 0.5f).toInt() shl 24) or
                ((red * 255f + 0.5f).toInt() shl 16) or
                ((green * 255f + 0.5f).toInt() shl 8) or
                (blue * 255f + 0.5f).toInt()
            ).toLong() and 0xFFFFFFFFL

        private fun materialScheme(
            p: PaletteColors,
            isDark: Boolean,
            primaryContainer: Color,
            onPrimaryContainer: Color,
        ): ColorScheme {
            val background = Color(p.background)
            val surface = Color(p.surface)
            val surfaceVariant = Color(p.surfaceVariant)
            val onSurface = Color(p.onSurface)
            val accent = Color(p.accent)
            val error = Color(p.error)
            val outline = Color(p.outline)
            val tertiaryContainer = accent.mix(surface, if (isDark) 0.70f else 0.80f)
            val errorContainer = error.mix(surface, if (isDark) 0.70f else 0.82f)
            val primary = Color(p.primary)
            val secondary = Color(p.secondary)
            val onSecondary = Color(p.onSecondary)
            val onTertiaryContainer = ColorContrast.ensure(onSurface, tertiaryContainer)
            // Every role is given explicitly, so light/dark defaults never leak in.
            return lightColorScheme(
                primary = primary,
                onPrimary = Color(p.onPrimary),
                primaryContainer = primaryContainer,
                onPrimaryContainer = onPrimaryContainer,
                inversePrimary = primary.mix(if (isDark) Color.Black else Color.White, 0.35f),
                secondary = secondary,
                onSecondary = onSecondary,
                secondaryContainer = secondary,
                onSecondaryContainer = onSecondary,
                tertiary = accent,
                onTertiary = Color(p.onAccent),
                tertiaryContainer = tertiaryContainer,
                onTertiaryContainer = onTertiaryContainer,
                background = background,
                onBackground = Color(p.onBackground),
                surface = surface,
                onSurface = onSurface,
                surfaceVariant = surfaceVariant,
                onSurfaceVariant = Color(p.onSurfaceVariant),
                surfaceTint = primary,
                inverseSurface = onSurface,
                inverseOnSurface = surface,
                error = error,
                onError = Color(p.onError),
                errorContainer = errorContainer,
                onErrorContainer = ColorContrast.ensure(error.mix(onSurface, 0.4f), errorContainer),
                outline = outline,
                outlineVariant = outline.mix(background, 0.55f),
                scrim = Color.Black,
                surfaceBright = surface.mix(Color.White, if (isDark) 0.06f else 0.4f),
                surfaceContainer = surface.mix(surfaceVariant, 0.35f),
                surfaceContainerHigh = surface.mix(surfaceVariant, 0.65f),
                surfaceContainerHighest = surfaceVariant,
                surfaceContainerLow = background.mix(surface, 0.6f),
                surfaceContainerLowest = background,
                surfaceDim = surface.mix(Color.Black, if (isDark) 0.25f else 0.06f),
                primaryFixed = primaryContainer,
                primaryFixedDim = primaryContainer.mix(primary, 0.25f),
                onPrimaryFixed = onPrimaryContainer,
                onPrimaryFixedVariant = onPrimaryContainer,
                secondaryFixed = secondary,
                secondaryFixedDim = secondary.mix(onSecondary, 0.15f),
                onSecondaryFixed = onSecondary,
                onSecondaryFixedVariant = onSecondary,
                tertiaryFixed = tertiaryContainer,
                tertiaryFixedDim = tertiaryContainer.mix(accent, 0.25f),
                onTertiaryFixed = onTertiaryContainer,
                onTertiaryFixedVariant = onTertiaryContainer,
            )
        }
    }
}
