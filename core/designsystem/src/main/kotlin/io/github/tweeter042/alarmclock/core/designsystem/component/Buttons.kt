package io.github.tweeter042.alarmclock.core.designsystem.component

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import io.github.tweeter042.alarmclock.core.designsystem.theme.CardFill
import io.github.tweeter042.alarmclock.core.designsystem.theme.HeaderDecor
import io.github.tweeter042.alarmclock.core.designsystem.theme.PixelCornerShape
import io.github.tweeter042.alarmclock.core.designsystem.theme.SelectionStyle
import io.github.tweeter042.alarmclock.core.designsystem.theme.ShadowSpec
import io.github.tweeter042.alarmclock.core.designsystem.theme.mix
import io.github.tweeter042.alarmclock.core.model.theme.ThemeId
import java.util.Locale

/** Emphasis of an [AppButton]. */
enum class AppButtonVariant {
    /** The one main action of a screen. */
    Primary,
    /** Neutral, outlined action. */
    Secondary,
    /** Softer filled action (secondary container). */
    Tonal,
    /** Destructive action (delete, dismiss forever). */
    Danger,
    /** Low-emphasis inline action. */
    Text,
}

/** Height and type size of an [AppButton]. [ExtraLarge] is for the ring screen. */
enum class AppButtonSize(val height: Dp, val horizontalPadding: Dp, val iconSize: Dp, val fontSize: TextUnit) {
    Small(36.dp, 14.dp, 18.dp, 13.sp),
    Medium(48.dp, 20.dp, 20.dp, 15.sp),
    Large(60.dp, 26.dp, 24.dp, 17.sp),
    ExtraLarge(84.dp, 32.dp, 30.dp, 22.sp),
    ;

    /** One step bigger (used when "large buttons" is on). */
    internal fun larger(): AppButtonSize = entries[(ordinal + 1).coerceAtMost(entries.lastIndex)]
}

/**
 * A button that looks native in every design language (pill in Minimal, black-bordered block
 * with a hard shadow in Brutal, inverse-video block in Terminal, stepped 8-bit key in Pixel, …).
 * Honours "large buttons" from the accessibility settings.
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AppButtonVariant = AppButtonVariant.Primary,
    size: AppButtonSize = AppButtonSize.Medium,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
) {
    val tokens = AppTheme.tokens
    val traits = tokens.traits
    val effectiveSize = if (tokens.largeButtons) size.larger() else size
    val interaction = remember { MutableInteractionSource() }
    val pressed by rememberPressFraction(interaction, enabled)

    val style = buttonStyle(variant, enabled)
    val shape = tokens.shapes.button
    // Shadows only under filled buttons (an outline-only button would show the shadow through).
    val shadow = when {
        !enabled || style.fill == null -> ShadowSpec.None
        effectiveSize == AppButtonSize.Small -> tokens.smallShadow
        else -> tokens.shadow
    }

    val label = when {
        traits.bracketedLabels && style.fill == null -> "[$text]"
        else -> text
    }.let { if (traits.uppercaseLabels) it.uppercase(Locale.getDefault()) else it }

    var m = modifier
        .minimumInteractiveComponentSize()
        .defaultMinSize(minWidth = if (variant == AppButtonVariant.Text) 48.dp else 64.dp)
        .height(effectiveSize.height)
        .themedContainer(shape, style.fill, style.border, shadow, traits.pressEffect, pressed)
    if (traits.bevel && style.fill != null && enabled && variant != AppButtonVariant.Text) {
        m = m.bevel(
            width = if (tokens.shapes.button is PixelCornerShape) 3.dp else 2.dp,
            light = Color.White.copy(alpha = 0.28f),
            dark = Color.Black.copy(alpha = 0.22f),
        )
    }
    Box(
        modifier = m
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(PaddingValues(horizontal = effectiveSize.horizontalPadding)),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides style.content) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (leadingIcon != null) {
                    Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(effectiveSize.iconSize))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = label,
                    style = tokens.typography.button.forText(label).merge(
                        TextStyle(
                            fontSize = effectiveSize.fontSize,
                            lineHeight = (effectiveSize.fontSize.value * 1.3f).sp,
                            // Paper's text buttons are underlined like links in print.
                            textDecoration = if (variant == AppButtonVariant.Text && traits.headerDecor == HeaderDecor.Masthead) {
                                TextDecoration.Underline
                            } else {
                                null
                            },
                            shadow = if (traits.textGlow && variant != AppButtonVariant.Primary) glowShadow(style.content) else null,
                        ),
                    ),
                    color = style.content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = effectiveSize.fontSize * 0.72f,
                        maxFontSize = effectiveSize.fontSize,
                        stepSize = 0.5.sp,
                    ),
                )
                if (trailingIcon != null) {
                    Spacer(Modifier.width(8.dp))
                    Icon(trailingIcon, contentDescription = null, modifier = Modifier.size(effectiveSize.iconSize))
                }
            }
        }
    }
}

internal data class ButtonColors(val fill: Brush?, val content: Color, val border: ContainerBorder?)

@Composable
internal fun buttonStyle(variant: AppButtonVariant, enabled: Boolean): ButtonColors {
    val tokens = AppTheme.tokens
    val c = tokens.colors
    val traits = tokens.traits
    val lineBorder = filledControlBorder()
    if (!enabled) {
        return ButtonColors(
            fill = if (variant == AppButtonVariant.Text) null else SolidColor(c.disabledContainer),
            content = c.disabledContent,
            border = lineBorder?.copy(color = c.disabledContent),
        )
    }
    return when (variant) {
        AppButtonVariant.Primary -> when {
            traits.outlinedPrimary -> ButtonColors(null, c.primary, ContainerBorder(1.5.dp, c.primary))
            traits.gradientButtons -> ButtonColors(
                Brush.verticalGradient(listOf(c.primary.mix(Color.White, if (c.isDark) 0.10f else 0.16f), c.primary)),
                c.onPrimary,
                ContainerBorder(1.dp, Color.White.copy(alpha = if (c.isDark) 0.22f else 0.45f)),
            )
            else -> ButtonColors(SolidColor(c.primary), c.onPrimary, lineBorder)
        }
        AppButtonVariant.Secondary -> when {
            tokens.border.outlineFilledControls -> ButtonColors(SolidColor(c.surface), c.onSurface, lineBorder)
            traits.cardFill == CardFill.Frosted -> ButtonColors(SolidColor(c.card), c.onSurface, ContainerBorder(1.dp, c.cardBorder))
            traits.bracketedLabels -> ButtonColors(null, c.primary, null)
            else -> ButtonColors(null, if (c.isDynamic || tokens.border.width == 0.dp) c.primary else c.onSurface, ContainerBorder(1.dp, c.outline))
        }
        AppButtonVariant.Tonal -> when {
            traits.bracketedLabels -> ButtonColors(SolidColor(c.secondary), c.onSecondary, null)
            else -> ButtonColors(SolidColor(c.secondary), c.onSecondary, lineBorder)
        }
        AppButtonVariant.Danger -> when {
            traits.outlinedPrimary -> ButtonColors(null, c.error, ContainerBorder(1.5.dp, c.error))
            else -> ButtonColors(SolidColor(c.error), c.onError, lineBorder)
        }
        AppButtonVariant.Text -> ButtonColors(null, c.primary, null)
    }
}

/** Icon-only button styles. */
enum class AppIconButtonVariant { Standard, Filled, Tonal, Outlined }

/** A 48 dp icon button. [contentDescription] is required for accessibility unless purely decorative. */
@Composable
fun AppIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: AppIconButtonVariant = AppIconButtonVariant.Standard,
    tint: Color = Color.Unspecified,
) {
    val tokens = AppTheme.tokens
    val c = tokens.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by rememberPressFraction(interaction, enabled)
    val (fill, content, border) = when (variant) {
        AppIconButtonVariant.Standard -> Triple(null, LocalContentColor.current, null)
        AppIconButtonVariant.Filled ->
            if (tokens.traits.outlinedPrimary) Triple(null, c.primary, ContainerBorder(1.5.dp, c.primary))
            else Triple(SolidColor(c.primary), c.onPrimary, filledControlBorder())
        AppIconButtonVariant.Tonal -> Triple(SolidColor(c.secondary), c.onSecondary, filledControlBorder())
        AppIconButtonVariant.Outlined -> Triple(
            null,
            c.onSurface,
            ContainerBorder(if (tokens.border.outlineFilledControls) tokens.border.width else 1.dp, if (tokens.border.outlineFilledControls) tokens.border.color else c.outline),
        )
    }
    val shape = when (tokens.traits.selectionStyle) {
        SelectionStyle.Rounded -> CircleShape
        else -> tokens.shapes.indicator
    }
    val shadow = if (fill == null || !enabled) ShadowSpec.None else tokens.smallShadow
    val finalTint = when {
        !enabled -> c.disabledContent
        tint != Color.Unspecified -> tint
        else -> content
    }
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(if (variant == AppIconButtonVariant.Standard) 48.dp else 44.dp)
            .themedContainer(shape, fill, border, shadow, tokens.traits.pressEffect, pressed)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = finalTint, modifier = Modifier.size(24.dp))
    }
}

/**
 * Floating action button (bottom-right, One UI reachable zone). With [text] it becomes an
 * extended FAB; [expanded] = false collapses it to the icon (e.g. while scrolling).
 */
@Composable
fun AppFab(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    expanded: Boolean = true,
) {
    val tokens = AppTheme.tokens
    val c = tokens.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by rememberPressFraction(interaction)
    val outlined = tokens.traits.outlinedPrimary
    val fill: Brush? = when {
        outlined -> SolidColor(c.background)
        tokens.traits.gradientButtons -> Brush.verticalGradient(listOf(c.primary.mix(Color.White, 0.14f), c.primary))
        else -> SolidColor(c.primary)
    }
    val content = if (outlined) c.primary else c.onPrimary
    val border = when {
        outlined -> ContainerBorder(1.5.dp, c.primary)
        tokens.traits.gradientButtons -> ContainerBorder(1.dp, Color.White.copy(alpha = 0.4f))
        else -> filledControlBorder()
    }
    val shadow = when {
        tokens.shadow != ShadowSpec.None -> tokens.shadow
        tokens.themeId == ThemeId.MATERIAL_YOU -> ShadowSpec.Soft(8.dp, 3.dp, Color.Black.copy(alpha = if (c.isDark) 0.5f else 0.22f))
        else -> ShadowSpec.None
    }
    val showText = text != null && expanded
    Box(
        modifier = modifier
            .height(60.dp)
            .defaultMinSize(minWidth = 60.dp)
            .themedContainer(tokens.shapes.fab, fill, border, shadow, tokens.traits.pressEffect, pressed)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = if (showText) 20.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = if (showText) null else contentDescription, tint = content, modifier = Modifier.size(26.dp))
            if (showText) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (tokens.traits.uppercaseLabels) text!!.uppercase(Locale.getDefault()) else text!!,
                    style = tokens.typography.button,
                    color = content,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Soft text glow for Neon / Terminal labels. */
internal fun glowShadow(color: Color, radius: Float = 18f): androidx.compose.ui.graphics.Shadow =
    androidx.compose.ui.graphics.Shadow(color = color.copy(alpha = 0.75f), offset = Offset.Zero, blurRadius = radius)
