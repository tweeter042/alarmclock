package io.github.tweeter042.alarmclock.core.designsystem.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tweeter042.alarmclock.core.designsystem.R
import io.github.tweeter042.alarmclock.core.designsystem.clock.ClockDisplay
import io.github.tweeter042.alarmclock.core.designsystem.clock.ClockSize
import io.github.tweeter042.alarmclock.core.designsystem.component.AppButton
import io.github.tweeter042.alarmclock.core.designsystem.component.AppButtonSize
import io.github.tweeter042.alarmclock.core.designsystem.component.AppCard
import io.github.tweeter042.alarmclock.core.designsystem.component.AppSwitch
import io.github.tweeter042.alarmclock.core.designsystem.component.ThemedBackground
import io.github.tweeter042.alarmclock.core.designsystem.icon.AppIcons
import io.github.tweeter042.alarmclock.core.designsystem.theme.AlarmClockTheme
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppColors
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import io.github.tweeter042.alarmclock.core.model.settings.AppearanceSettings
import io.github.tweeter042.alarmclock.core.model.theme.DYNAMIC_PALETTE_ID
import io.github.tweeter042.alarmclock.core.model.theme.PaletteSpec
import io.github.tweeter042.alarmclock.core.model.theme.ThemeCatalog
import io.github.tweeter042.alarmclock.core.model.theme.ThemeId
import io.github.tweeter042.alarmclock.core.model.theme.ThemeInfo
import java.time.LocalTime

/** True when the app currently shows Thai (per-app language). */
@Composable
@ReadOnlyComposable
fun isThaiLocale(): Boolean = LocalConfiguration.current.locales[0]?.language == "th"

/** Theme name in the current app language. */
@Composable
@ReadOnlyComposable
fun ThemeInfo.localizedName(): String = if (isThaiLocale()) nameTh else nameEn

/** Theme description in the current app language. */
@Composable
@ReadOnlyComposable
fun ThemeInfo.localizedDescription(): String = if (isThaiLocale()) descriptionTh else descriptionEn

/** Palette name in the current app language. */
@Composable
@ReadOnlyComposable
fun PaletteSpec.localizedName(): String = if (isThaiLocale()) nameTh else nameEn

/**
 * A miniature, live preview of a theme + palette for the appearance gallery: the real themed
 * background, clock, card, switch and button rendered at reduced scale. Tapping selects it.
 */
@Composable
fun ThemePreviewCard(
    themeId: ThemeId,
    paletteId: String,
    dark: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 148.dp,
    showName: Boolean = true,
    time: LocalTime = LocalTime.of(7, 30),
) {
    val outer = AppTheme.colors
    val outerTokens = AppTheme.tokens
    val info = ThemeCatalog.theme(themeId)
    val palette = ThemeCatalog.palette(themeId, paletteId)
    val themeName = info.localizedName()
    val paletteName = palette.localizedName()
    val mode = stringResource(if (dark || !info.supportsLight) R.string.ds_mode_dark else R.string.ds_mode_light)
    val description = stringResource(R.string.ds_theme_preview_description, themeName, paletteName, mode)
    val shape = RoundedCornerShape(18.dp)
    val appearance = remember(themeId, paletteId) {
        AppearanceSettings(themeId = themeId, paletteByTheme = mapOf(themeId to paletteId))
    }
    Column(
        modifier = modifier
            .width(width)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                this.selected = selected
                role = Role.RadioButton
            }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.6f)
                .border(if (selected) 3.dp else 1.dp, if (selected) outer.primary else outer.outlineVariant, shape)
                .padding(if (selected) 5.dp else 0.dp)
                .clip(RoundedCornerShape(if (selected) 14.dp else 18.dp))
                .clearAndSetSemantics { },
        ) {
            AlarmClockTheme(
                appearance = appearance,
                accessibility = outerTokens.accessibility.copy(reduceMotion = true),
                forceDark = dark,
                themeOverride = themeId,
                paletteOverride = paletteId,
            ) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density * 0.42f, 1f)) {
                    MiniScreen(themeName = themeName, paletteName = paletteName, time = time)
                }
            }
            if (selected) {
                CheckBadge(outer, Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
        }
        if (showName) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = themeName,
                style = AppTheme.typography.labelLarge,
                color = if (selected) outer.primary else outer.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = AppTheme.typography.labelLarge.fontSize * 0.7f,
                    maxFontSize = AppTheme.typography.labelLarge.fontSize,
                    stepSize = 0.25.sp,
                ),
            )
            Text(
                text = paletteName,
                style = AppTheme.typography.labelSmall,
                color = outer.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MiniScreen(themeName: String, paletteName: String, time: LocalTime) {
    val tokens = AppTheme.tokens
    ThemedBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 22.dp)) {
            Text(
                themeName,
                style = tokens.typography.headlineMedium,
                color = tokens.colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = tokens.typography.headlineMedium.fontSize * 0.6f,
                    maxFontSize = tokens.typography.headlineMedium.fontSize,
                ),
            )
            Spacer(Modifier.height(18.dp))
            ClockDisplay(time = time, use24h = true, size = ClockSize.Large, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.weight(1f))
            AppCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        SkeletonLine(0.85f, tokens.colors.onSurface.copy(alpha = 0.75f))
                        Spacer(Modifier.height(10.dp))
                        SkeletonLine(0.55f, tokens.colors.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                    Spacer(Modifier.width(12.dp))
                    AppSwitch(checked = true, onCheckedChange = null)
                }
            }
            Spacer(Modifier.height(18.dp))
            AppButton(text = paletteName, onClick = {}, size = AppButtonSize.Medium, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CheckBadge(outer: AppColors, modifier: Modifier) {
    Box(
        modifier
            .size(24.dp)
            .background(outer.primary, CircleShape)
            .border(2.dp, outer.onPrimary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons.Check, null, tint = outer.onPrimary, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun SkeletonLine(fraction: Float, color: Color) {
    Canvas(Modifier.fillMaxWidth(fraction).height(12.dp)) {
        drawRoundRect(color, cornerRadius = CornerRadius(size.height / 2))
    }
}

/**
 * The palettes of a theme as swatches (page / primary / accent) with names; the selected one
 * is ringed. Material You's wallpaper palette shows the live wallpaper colours.
 */
@Composable
fun PaletteSwatches(
    themeId: ThemeId,
    selectedPaletteId: String,
    dark: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val info = ThemeCatalog.theme(themeId)
    val outer = AppTheme.colors
    val context = LocalContext.current
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        info.palettes.forEach { palette ->
            val selected = palette.id == selectedPaletteId
            val isDark = dark || !info.supportsLight
            val colors = remember(palette, isDark, context) {
                val dynamic = palette.id == DYNAMIC_PALETTE_ID && android.os.Build.VERSION.SDK_INT >= 31
                val base = if (isDark) palette.dark else palette.light
                val p = if (dynamic) {
                    runCatching {
                        AppColors.paletteFromScheme(
                            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context),
                            isDark,
                        )
                    }.getOrDefault(base)
                } else {
                    base
                }
                Triple(Color(p.background), Color(p.primary), Color(p.accent))
            }
            val name = palette.localizedName()
            val description = stringResource(R.string.ds_palette_description, name)
            Column(
                modifier = Modifier
                    .width(68.dp)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(palette.id) })
                    .semantics { contentDescription = description }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .border(if (selected) 3.dp else 1.dp, if (selected) outer.primary else outer.outlineVariant, CircleShape)
                        .padding(if (selected) 5.dp else 2.dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    val (bg, primary, accent) = colors
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(bg)
                        drawRect(primary, topLeft = Offset(0f, size.height / 2f), size = Size(size.width / 2f, size.height / 2f))
                        drawRect(accent, topLeft = Offset(size.width / 2f, size.height / 2f), size = Size(size.width / 2f, size.height / 2f))
                    }
                    if (palette.id == DYNAMIC_PALETTE_ID) {
                        Icon(AppIcons.Wallpaper, null, tint = colors.second, modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp).size(16.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = name,
                    style = AppTheme.typography.labelSmall,
                    color = if (selected) outer.primary else outer.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
