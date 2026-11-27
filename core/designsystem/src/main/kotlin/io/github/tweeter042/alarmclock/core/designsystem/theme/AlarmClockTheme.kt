package io.github.tweeter042.alarmclock.core.designsystem.theme

import android.content.Context
import android.os.Build
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import io.github.tweeter042.alarmclock.core.designsystem.font.FontFamilies
import io.github.tweeter042.alarmclock.core.model.font.FontCatalog
import io.github.tweeter042.alarmclock.core.model.font.FontIds
import io.github.tweeter042.alarmclock.core.model.settings.AccessibilitySettings
import io.github.tweeter042.alarmclock.core.model.settings.AppearanceSettings
import io.github.tweeter042.alarmclock.core.model.settings.DarkModePref
import io.github.tweeter042.alarmclock.core.model.theme.DYNAMIC_PALETTE_ID
import io.github.tweeter042.alarmclock.core.model.theme.PaletteColors
import io.github.tweeter042.alarmclock.core.model.theme.ThemeCatalog
import io.github.tweeter042.alarmclock.core.model.theme.ThemeId
import io.github.tweeter042.alarmclock.core.model.theme.ThemeInfo

/**
 * Applies a theme to [content]: one of the 12 design languages with a palette, light/dark,
 * fonts, font scale and accessibility options. Also wraps [MaterialTheme] with the mapped
 * colour scheme, typography and shapes so stock Material 3 widgets fit the theme.
 *
 * @param appearance the user's appearance settings (theme, palettes, dark mode, fonts, scale).
 * @param accessibility high contrast, large buttons, reduce motion, haptics.
 * @param forceDark overrides [AppearanceSettings.darkMode] (previews, ring screen at night).
 * @param themeOverride render a different theme (ring-screen theme, appearance gallery).
 * @param paletteOverride palette id for [themeOverride] (or the current theme).
 */
@Composable
fun AlarmClockTheme(
    appearance: AppearanceSettings,
    accessibility: AccessibilitySettings = AccessibilitySettings(),
    forceDark: Boolean? = null,
    themeOverride: ThemeId? = null,
    paletteOverride: String? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val themeId = themeOverride ?: appearance.themeId
    val info = ThemeCatalog.theme(themeId)
    val palette = ThemeCatalog.palette(themeId, paletteOverride ?: appearance.paletteIdFor(themeId))
    val systemDark = isSystemInDarkTheme()
    val thaiUi = LocalConfiguration.current.locales[0]?.language == "th"
    val dark = resolveDark(info, appearance.darkMode, forceDark, systemDark)
    val dynamic = themeId == ThemeId.MATERIAL_YOU && palette.id == DYNAMIC_PALETTE_ID &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val paletteColors: PaletteColors = remember(palette, dark, dynamic, context) {
        if (dynamic) dynamicPalette(context, dark) ?: (if (dark) palette.dark else palette.light)
        else if (dark) palette.dark else palette.light
    }
    val fonts = remember(themeId, appearance.bodyFontId, appearance.clockFontId, context, thaiUi) {
        resolveFonts(context, info, appearance.bodyFontId, appearance.clockFontId, thaiUi)
    }
    val tokens = remember(themeId, palette.id, paletteColors, dark, dynamic, fonts, accessibility, thaiUi) {
        buildThemeTokens(
            themeId = themeId,
            paletteId = palette.id,
            palette = paletteColors,
            isDark = dark,
            isDynamic = dynamic,
            fonts = fonts,
            accessibility = accessibility,
            thaiUi = thaiUi,
        )
    }
    ProvideThemeTokens(tokens, appearance.fontScale, content)
}

/**
 * Provides already-built [tokens] (used by [AlarmClockTheme] and the theme preview gallery).
 * [fontScale] multiplies the system font scale (0.85–1.4).
 */
@Composable
fun ProvideThemeTokens(tokens: ThemeTokens, fontScale: Float = 1f, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val scale = fontScale.coerceIn(0.85f, 1.4f)
    val themedDensity = remember(density, scale) {
        if (scale == 1f) density else ScaledFontDensity(density, scale)
    }
    val colors = tokens.colors
    val indication = when (tokens.traits.pressEffect) {
        PressEffect.Invert, PressEffect.ShiftIntoShadow -> highlightIndication(colors.onSurface.copy(alpha = 0.14f))
        PressEffect.Glow -> ripple(color = colors.glow)
        else -> ripple()
    }
    val selection = remember(colors.primary) {
        TextSelectionColors(handleColor = colors.primary, backgroundColor = colors.primary.copy(alpha = 0.3f))
    }
    CompositionLocalProvider(
        LocalThemeTokens provides tokens,
        LocalDensity provides themedDensity,
    ) {
        MaterialTheme(
            colorScheme = colors.material,
            motionScheme = if (tokens.motion.reduceMotion) ReducedMotionScheme else MotionScheme.standard(),
            shapes = tokens.shapes.material,
            typography = tokens.typography.material,
        ) {
            CompositionLocalProvider(
                LocalContentColor provides colors.onBackground,
                LocalTextStyle provides tokens.typography.bodyLarge,
                LocalIndication provides indication,
                LocalTextSelectionColors provides selection,
                content = content,
            )
        }
    }
}

/** Dark mode decision: dark-only themes always dark, then [forceDark], then the user preference. */
fun resolveDark(info: ThemeInfo, pref: DarkModePref, forceDark: Boolean?, systemDark: Boolean): Boolean = when {
    !info.supportsLight -> true
    forceDark != null -> forceDark
    else -> when (pref) {
        DarkModePref.SYSTEM -> systemDark
        DarkModePref.LIGHT -> false
        DarkModePref.DARK -> true
    }
}

/**
 * Resolves body, heading and clock font families for a theme and the user's font ids.
 *
 * With a Thai UI ([thaiUi]), theme fonts that are Latin-only *and* optically rescaled (Pixel's
 * Press Start 2P / VT323) are replaced by their Thai partner, so Thai labels keep a normal size.
 */
fun resolveFonts(
    context: Context?,
    info: ThemeInfo,
    bodyFontId: String,
    clockFontId: String,
    thaiUi: Boolean = false,
): ResolvedFonts {
    val type = DesignLanguages.of(info.id).type
    val themeFonts = bodyFontId == FontIds.THEME_DEFAULT
    fun latinOnly(id: String) = FontCatalog.byId(id)?.supportsThai == false
    var body = FontFamilies.resolve(context, bodyFontId, info.defaultBodyFontId)
    if (thaiUi && themeFonts && type.bodyScale.isRescaled() && latinOnly(info.defaultBodyFontId)) {
        FontFamilies.thaiPartner(info.defaultBodyFontId)?.let { body = it }
    }
    val clock = FontFamilies.resolve(context, clockFontId, info.defaultClockFontId)
    val headingId = DesignLanguages.of(info.id).displayFontId
    // A font picked by the user replaces the theme's heading font too.
    val display = when {
        headingId == null || !themeFonts -> body
        thaiUi && type.displayScale.isRescaled() && latinOnly(headingId) -> FontFamilies.thaiPartner(headingId) ?: body
        else -> FontFamilies.bundled(headingId) ?: body
    }
    return ResolvedFonts(body = body, display = display, clock = clock)
}

private fun dynamicPalette(context: Context, dark: Boolean): PaletteColors? = runCatching {
    val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    AppColors.paletteFromScheme(scheme, dark)
}.getOrNull()

/** Material motion that snaps, for "reduce motion". */
private object ReducedMotionScheme : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}

/**
 * Multiplies the font scale while keeping the platform's (non-linear, Android 14+) sp conversion.
 */
private class ScaledFontDensity(private val base: Density, private val extra: Float) : Density {
    override val density: Float get() = base.density
    override val fontScale: Float get() = base.fontScale * extra

    override fun TextUnit.toDp(): Dp = with(base) { (this@toDp * extra).toDp() }

    override fun Dp.toSp(): TextUnit = with(base) { this@toSp.toSp() / extra }
}
