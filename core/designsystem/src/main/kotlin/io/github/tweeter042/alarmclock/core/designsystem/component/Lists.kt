package io.github.tweeter042.alarmclock.core.designsystem.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.tweeter042.alarmclock.core.designsystem.icon.AppIcons
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import io.github.tweeter042.alarmclock.core.designsystem.theme.CardFill
import io.github.tweeter042.alarmclock.core.designsystem.theme.ColorContrast
import io.github.tweeter042.alarmclock.core.designsystem.theme.HeaderDecor
import io.github.tweeter042.alarmclock.core.designsystem.theme.ShadowSpec
import io.github.tweeter042.alarmclock.core.designsystem.util.rememberAppHaptics
import java.util.Locale

/**
 * A list row: optional leading icon (in a themed container) or custom [leading] slot,
 * headline, supporting text and a [trailing] slot. Clickable when [onClick] is set.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppListItem(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    overline: String? = null,
    leadingIcon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    supportingColor: Color = Color.Unspecified,
) {
    val tokens = AppTheme.tokens
    val c = tokens.colors
    val interaction = remember { MutableInteractionSource() }
    val clickable = if (onClick != null) {
        Modifier.combinedClickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            enabled = enabled,
            role = Role.Button,
            onLongClick = onLongClick,
            onClick = onClick,
        )
    } else {
        Modifier
    }
    ListRowLayout(
        modifier = modifier.then(clickable),
        headline = headline,
        supporting = supporting,
        overline = overline,
        leadingIcon = leadingIcon,
        leading = leading,
        trailing = trailing,
        enabled = enabled,
        supportingColor = if (supportingColor == Color.Unspecified) c.onSurfaceVariant else supportingColor,
    )
}

@Composable
private fun ListRowLayout(
    modifier: Modifier,
    headline: String,
    supporting: String?,
    overline: String?,
    leadingIcon: ImageVector?,
    leading: (@Composable () -> Unit)?,
    trailing: (@Composable () -> Unit)?,
    enabled: Boolean,
    supportingColor: Color,
) {
    val tokens = AppTheme.tokens
    val c = tokens.colors
    val content = LocalContentColor.current.let { if (it == Color.Unspecified) c.onSurface else it }
    val alpha = if (enabled) 1f else 0.45f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (supporting != null) 64.dp else 56.dp)
            .padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.listItem),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            leading != null -> {
                leading()
                Spacer(Modifier.width(tokens.spacing.lg))
            }
            leadingIcon != null -> {
                LeadingIcon(leadingIcon, enabled)
                Spacer(Modifier.width(tokens.spacing.lg))
            }
        }
        Column(Modifier.weight(1f)) {
            if (overline != null) {
                Text(
                    text = overline.let { if (tokens.traits.uppercaseLabels) it.uppercase(Locale.getDefault()) else it },
                    style = tokens.typography.labelMedium,
                    color = c.onSurfaceVariant.dim(alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = headline,
                style = tokens.typography.titleMedium,
                color = content.dim(alpha),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = tokens.typography.bodyMedium,
                    color = supportingColor.copy(alpha = supportingColor.alpha * alpha),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(tokens.spacing.md))
            trailing()
        }
    }
}

/** The themed icon container used in list rows and settings. */
@Composable
fun LeadingIcon(icon: ImageVector, enabled: Boolean = true, tint: Color = Color.Unspecified) {
    val tokens = AppTheme.tokens
    val c = tokens.colors
    val plain = tokens.traits.bracketedLabels || tokens.traits.headerDecor == HeaderDecor.Masthead
    val outlined = tokens.traits.cardFill == CardFill.Outline
    val iconTint = when {
        tint != Color.Unspecified -> tint
        plain || outlined -> c.primary
        tokens.border.outlineFilledControls -> c.onAccent
        else -> c.onPrimaryContainer
    }.let { if (enabled) it else c.disabledContent }
    val containerModifier = when {
        plain -> Modifier
        outlined -> Modifier.themedContainer(tokens.shapes.iconContainer, null, ContainerBorder(1.dp, c.outline), ShadowSpec.None)
        tokens.border.outlineFilledControls -> Modifier.themedContainer(
            tokens.shapes.iconContainer,
            SolidColor(c.accent),
            ContainerBorder(maxOf(tokens.border.width - 0.5.dp, 1.5.dp), tokens.border.color),
            ShadowSpec.None,
        )
        else -> Modifier.themedContainer(tokens.shapes.iconContainer, SolidColor(c.primaryContainer), null, ShadowSpec.None)
    }
    Box(Modifier.size(40.dp).then(containerModifier), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(if (plain) 26.dp else 22.dp))
    }
}

/**
 * Header above a group of rows. Styled per theme: Paper prints a newspaper section rule,
 * Terminal a "#" comment, Brutal a bold label with an accent bar, Neon a glowing caption.
 */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val tokens = AppTheme.tokens
    val c = tokens.colors
    val traits = tokens.traits
    val label = when {
        traits.bracketedLabels -> "# $text"
        traits.uppercaseLabels -> text.uppercase(Locale.getDefault())
        else -> text
    }
    val color = when {
        traits.textGlow && !traits.bracketedLabels -> c.accent
        traits.bracketedLabels -> c.onSurfaceVariant
        c.isDynamic || tokens.border.width == 0.dp -> c.primary
        else -> c.onSurfaceVariant
    }
    val decoration: Modifier = when (traits.headerDecor) {
        HeaderDecor.Masthead -> Modifier.drawBehind {
            val ink = c.onBackground
            drawRect(ink, size = Size(size.width, 2.dp.toPx()))
            drawRect(ink, topLeft = Offset(0f, 4.dp.toPx()), size = Size(size.width, 0.75.dp.toPx()))
        }
        HeaderDecor.Rule -> Modifier.drawBehind {
            drawRect(c.accent.takeIf { tokens.border.outlineFilledControls } ?: c.outline, topLeft = Offset(0f, size.height - 4.dp.toPx()), size = Size(28.dp.toPx(), 4.dp.toPx()))
        }
        else -> Modifier
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = tokens.spacing.lg, end = tokens.spacing.sm, top = tokens.spacing.lg, bottom = tokens.spacing.xs)
            .then(decoration)
            .padding(top = if (traits.headerDecor == HeaderDecor.Masthead) 10.dp else 0.dp, bottom = if (traits.headerDecor == HeaderDecor.Rule) 8.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = tokens.typography.sectionLabel.forText(text).let {
                if (traits.textGlow && !traits.bracketedLabels) it.copy(shadow = glowShadow(color, 14f)) else it
            },
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (action != null) action()
    }
}

/** A settings row with a switch; the whole row toggles (single TalkBack target). */
@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val haptics = rememberAppHaptics()
    ListRowLayout(
        modifier = modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = {
                haptics.toggle(it)
                onCheckedChange(it)
            },
        ),
        headline = title,
        supporting = summary,
        overline = null,
        leadingIcon = icon,
        leading = null,
        trailing = { AppSwitch(checked = checked, onCheckedChange = null, enabled = enabled) },
        enabled = enabled,
        supportingColor = AppTheme.colors.onSurfaceVariant,
    )
}

/**
 * A settings row showing its current [value] under the title in the accent colour (One UI style),
 * opening a picker on click.
 */
@Composable
fun SettingsValueRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    AppListItem(
        headline = title,
        supporting = value,
        supportingColor = readableAccent(),
        leadingIcon = icon,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
    )
}

/**
 * The primary colour adjusted (if needed) to stay readable as text on both cards and the page.
 */
@Composable
fun readableAccent(): Color {
    val c = AppTheme.colors
    val card = if (c.card.alpha < 1f) c.surface else c.card
    val worse = if (ColorContrast.ratio(c.primary, card) < ColorContrast.ratio(c.primary, c.background)) card else c.background
    return ColorContrast.ensure(c.primary, worse, 4.5f)
}

/** A settings row that navigates to another screen. */
@Composable
fun SettingsNavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    trailingText: String? = null,
) {
    val tokens = AppTheme.tokens
    AppListItem(
        headline = title,
        supporting = summary,
        leadingIcon = icon,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (trailingText != null) {
                    Text(trailingText, style = tokens.typography.bodyMedium, color = tokens.colors.onSurfaceVariant, maxLines = 1)
                    Spacer(Modifier.width(4.dp))
                }
                if (tokens.traits.bracketedLabels) {
                    Text(">", style = tokens.typography.titleMedium, color = tokens.colors.onSurfaceVariant)
                } else {
                    Icon(AppIcons.ChevronRight, contentDescription = null, tint = tokens.colors.onSurfaceVariant)
                }
            }
        },
    )
}
