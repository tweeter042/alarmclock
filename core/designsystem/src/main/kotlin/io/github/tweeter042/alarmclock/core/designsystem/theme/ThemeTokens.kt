package io.github.tweeter042.alarmclock.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import io.github.tweeter042.alarmclock.core.model.settings.AccessibilitySettings
import io.github.tweeter042.alarmclock.core.model.theme.ClockFace
import io.github.tweeter042.alarmclock.core.model.theme.ThemeCatalog
import io.github.tweeter042.alarmclock.core.model.theme.ThemeId

/**
 * Everything that defines how the app looks right now. Provided by [AlarmClockTheme];
 * read it through [AppTheme].
 */
@Immutable
data class ThemeTokens(
    val themeId: ThemeId,
    val paletteId: String,
    val isDark: Boolean,
    val colors: AppColors,
    val shapes: AppShapes,
    val border: BorderSpec,
    val shadow: ShadowSpec,
    /** Smaller elements (chips, small buttons, switches) use this lighter shadow. */
    val smallShadow: ShadowSpec,
    val typography: AppTypography,
    val clockFace: ClockFace,
    val background: BackgroundStyle,
    val motion: MotionSpec,
    val spacing: Spacing,
    val iconStyle: IconStyle,
    val traits: DesignTraits,
    val accessibility: AccessibilitySettings,
) {
    val highContrast: Boolean get() = accessibility.highContrast
    val largeButtons: Boolean get() = accessibility.largeButtons
}

/** Tokens used when no [AlarmClockTheme] is in the composition (previews, isolated tests). */
internal val DefaultThemeTokens: ThemeTokens by lazy {
    val info = ThemeCatalog.theme(ThemeId.MINIMAL)
    val palette = info.palettes.first()
    buildThemeTokens(
        themeId = info.id,
        paletteId = palette.id,
        palette = palette.light,
        isDark = false,
        isDynamic = false,
        fonts = ResolvedFonts(FontFamily.Default, FontFamily.Default, FontFamily.Default),
        accessibility = AccessibilitySettings(),
    )
}

val LocalThemeTokens = staticCompositionLocalOf { DefaultThemeTokens }

/**
 * Entry point for theme values in composables:
 * `AppTheme.colors.primary`, `AppTheme.typography.bodyLarge`, `AppTheme.shapes.card`,
 * `AppTheme.spacing.screen`, `AppTheme.motion.spec()`, …
 */
object AppTheme {
    val tokens: ThemeTokens
        @Composable @ReadOnlyComposable get() = LocalThemeTokens.current

    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalThemeTokens.current.colors

    val typography: AppTypography
        @Composable @ReadOnlyComposable get() = LocalThemeTokens.current.typography

    val shapes: AppShapes
        @Composable @ReadOnlyComposable get() = LocalThemeTokens.current.shapes

    val border: BorderSpec
        @Composable @ReadOnlyComposable get() = LocalThemeTokens.current.border

    val shadow: ShadowSpec
        @Composable @ReadOnlyComposable get() = LocalThemeTokens.current.shadow

    val motion: MotionSpec
        @Composable @ReadOnlyComposable get() = LocalThemeTokens.current.motion

    val spacing: Spacing
        @Composable @ReadOnlyComposable get() = LocalThemeTokens.current.spacing

    val traits: DesignTraits
        @Composable @ReadOnlyComposable get() = LocalThemeTokens.current.traits

    val clockFace: ClockFace
        @Composable @ReadOnlyComposable get() = LocalThemeTokens.current.clockFace

    val iconStyle: IconStyle
        @Composable @ReadOnlyComposable get() = LocalThemeTokens.current.iconStyle

    val background: BackgroundStyle
        @Composable @ReadOnlyComposable get() = LocalThemeTokens.current.background

    val themeId: ThemeId
        @Composable @ReadOnlyComposable get() = LocalThemeTokens.current.themeId

    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalThemeTokens.current.isDark

    val accessibility: AccessibilitySettings
        @Composable @ReadOnlyComposable get() = LocalThemeTokens.current.accessibility
}
