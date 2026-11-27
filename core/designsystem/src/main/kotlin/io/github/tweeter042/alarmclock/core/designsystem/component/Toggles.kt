package io.github.tweeter042.alarmclock.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tweeter042.alarmclock.core.designsystem.R
import io.github.tweeter042.alarmclock.core.designsystem.icon.AppIcons
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import io.github.tweeter042.alarmclock.core.designsystem.theme.CardFill
import io.github.tweeter042.alarmclock.core.designsystem.theme.NavIndicator
import io.github.tweeter042.alarmclock.core.designsystem.theme.ShadowSpec
import io.github.tweeter042.alarmclock.core.designsystem.util.rememberAppHaptics
import java.time.DayOfWeek
import java.time.temporal.WeekFields
import java.util.Locale

/** An assist chip: a compact action ("+5 min", "Weekdays"). */
@Composable
fun AppChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
    ChipImpl(text = text, selected = false, onClick = onClick, modifier = modifier, leadingIcon = leadingIcon, enabled = enabled, role = Role.Button)
}

/** A filter chip that toggles [selected]; selected chips show a check (or theme equivalent). */
@Composable
fun AppFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
    ChipImpl(text = text, selected = selected, onClick = onClick, modifier = modifier, leadingIcon = leadingIcon, enabled = enabled, role = Role.Checkbox, toggle = true)
}

@Composable
private fun ChipImpl(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    leadingIcon: ImageVector?,
    enabled: Boolean,
    role: Role,
    toggle: Boolean = false,
) {
    val tokens = AppTheme.tokens
    val c = tokens.colors
    val traits = tokens.traits
    val haptics = rememberAppHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by rememberPressFraction(interaction, enabled)
    val outlinedControls = tokens.border.outlineFilledControls

    val fill: Color
    val content: Color
    val border: ContainerBorder?
    when {
        selected && traits.bracketedLabels -> { fill = c.primary; content = c.onPrimary; border = null }
        selected && traits.outlinedPrimary -> { fill = c.background; content = c.primary; border = ContainerBorder(1.5.dp, c.primary) }
        selected && outlinedControls -> { fill = c.accent; content = c.onAccent; border = filledControlBorder() }
        selected -> { fill = c.primaryContainer; content = c.onPrimaryContainer; border = ContainerBorder(1.dp, c.primary.copy(alpha = 0.6f)) }
        outlinedControls -> { fill = c.surface; content = c.onSurface; border = filledControlBorder() }
        traits.cardFill == CardFill.Frosted -> { fill = c.card; content = c.onSurface; border = ContainerBorder(1.dp, c.cardBorder) }
        else -> { fill = Color.Transparent; content = c.onSurface; border = ContainerBorder(1.dp, c.outline, dashed = traits.bracketedLabels) }
    }
    val label = text.let { if (traits.uppercaseLabels) it.uppercase(Locale.getDefault()) else it }
    val clickModifier = if (toggle) {
        Modifier.toggleable(
            value = selected,
            enabled = enabled,
            role = role,
            interactionSource = interaction,
            indication = LocalIndication.current,
            onValueChange = {
                haptics.toggle(it)
                onClick()
            },
        )
    } else {
        Modifier.clickable(interaction, LocalIndication.current, enabled = enabled, role = role, onClick = onClick)
    }
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 36.dp)
            .themedContainer(
                tokens.shapes.chip,
                SolidColor(if (enabled) fill else fill.copy(alpha = fill.alpha * 0.5f)),
                border,
                if (enabled && (outlinedControls || selected && tokens.shadow is ShadowSpec.Glow)) tokens.smallShadow else ShadowSpec.None,
                traits.pressEffect,
                pressed,
            )
            .then(clickModifier)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val icon = when {
                selected && leadingIcon == null && !traits.bracketedLabels -> AppIcons.Check
                else -> leadingIcon
            }
            if (icon != null) {
                Icon(icon, null, tint = if (enabled) content else c.disabledContent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = tokens.typography.labelLarge.forText(label),
                color = if (enabled) content else c.disabledContent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = tokens.typography.labelLarge.fontSize * 0.75f,
                    maxFontSize = tokens.typography.labelLarge.fontSize,
                    stepSize = 0.5.sp,
                ),
            )
        }
    }
}

/**
 * Seven day toggles (ISO days: 1 = Monday … 7 = Sunday) with localized short names.
 *
 * @param firstDayOfWeek ISO day shown first; 0 = the locale's first day (Sunday in Thailand).
 */
@Composable
fun DayOfWeekSelector(
    selectedDays: Set<Int>,
    onSelectedDaysChange: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
    firstDayOfWeek: Int = 0,
    enabled: Boolean = true,
) {
    val tokens = AppTheme.tokens
    val c = tokens.colors
    val haptics = rememberAppHaptics()
    val first = if (firstDayOfWeek in 1..7) firstDayOfWeek else WeekFields.of(Locale.getDefault()).firstDayOfWeek.value
    val days = (0 until 7).map { ((first - 1 + it) % 7) + 1 }
    val narrow = days.map { stringResource(dayNarrowRes(it)) }
    val full = days.map { stringResource(dayFullRes(it)) }
    val selectedText = stringResource(R.string.ds_selected)
    val notSelectedText = stringResource(R.string.ds_not_selected)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        days.forEachIndexed { index, day ->
            val selected = day in selectedDays
            val interaction = remember { MutableInteractionSource() }
            val pressed by rememberPressFraction(interaction, enabled)
            val fill by animateColorAsState(
                when {
                    selected && tokens.traits.outlinedPrimary -> c.background
                    selected -> c.primary
                    tokens.border.outlineFilledControls -> c.surface
                    tokens.traits.cardFill == CardFill.Frosted -> c.card
                    tokens.traits.navIndicator == NavIndicator.Underline -> Color.Transparent
                    else -> c.surfaceVariant
                },
                tokens.motion.fade(),
                label = "dayFill",
            )
            val content = when {
                selected && tokens.traits.outlinedPrimary -> c.primary
                selected -> c.onPrimary
                tokens.traits.cardFill == CardFill.Frosted -> c.onSurface
                else -> c.onSurfaceVariant
            }
            val border = when {
                tokens.border.outlineFilledControls -> filledControlBorder()
                selected && tokens.traits.outlinedPrimary -> ContainerBorder(1.5.dp, c.primary)
                tokens.traits.navIndicator == NavIndicator.Underline && !selected -> ContainerBorder(1.dp, c.outline)
                tokens.traits.bracketedLabels && !selected -> ContainerBorder(1.dp, c.outline, dashed = true)
                else -> null
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f, matchHeightConstraintsFirst = false)
                    .heightIn(min = 40.dp)
                    .themedContainer(
                        tokens.shapes.dayToggle,
                        SolidColor(if (enabled) fill else fill.dim(0.4f)),
                        border,
                        ShadowSpec.None,
                        tokens.traits.pressEffect,
                        pressed,
                    )
                    .toggleable(
                        value = selected,
                        enabled = enabled,
                        role = Role.Checkbox,
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        onValueChange = { on ->
                            haptics.toggle(on)
                            onSelectedDaysChange(if (on) selectedDays + day else selectedDays - day)
                        },
                    )
                    .semantics { contentDescription = full[index] + ", " + if (selected) selectedText else notSelectedText },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = narrow[index],
                    style = tokens.typography.labelLarge.copy(
                        fontWeight = if (selected) FontWeight.Bold else tokens.typography.labelLarge.fontWeight,
                        fontSize = 15.sp,
                    ),
                    color = if (enabled) content else c.disabledContent,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

internal fun dayNarrowRes(isoDay: Int): Int = when (isoDay) {
    1 -> R.string.ds_day_narrow_1
    2 -> R.string.ds_day_narrow_2
    3 -> R.string.ds_day_narrow_3
    4 -> R.string.ds_day_narrow_4
    5 -> R.string.ds_day_narrow_5
    6 -> R.string.ds_day_narrow_6
    else -> R.string.ds_day_narrow_7
}

internal fun dayFullRes(isoDay: Int): Int = when (isoDay) {
    1 -> R.string.ds_day_full_1
    2 -> R.string.ds_day_full_2
    3 -> R.string.ds_day_full_3
    4 -> R.string.ds_day_full_4
    5 -> R.string.ds_day_full_5
    6 -> R.string.ds_day_full_6
    else -> R.string.ds_day_full_7
}

/** Localized full day name for an ISO day (1 = Monday). */
@Composable
fun dayOfWeekName(isoDay: Int): String = stringResource(dayFullRes(DayOfWeek.of(isoDay.coerceIn(1, 7)).value))

/**
 * A single-choice segmented control ("12 h | 24 h", "Easy | Hard"). The selected segment slides
 * with the theme's motion.
 */
@Composable
fun AppSegmentedButtons(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icons: List<ImageVector?>? = null,
) {
    if (options.isEmpty()) return
    val tokens = AppTheme.tokens
    val c = tokens.colors
    val traits = tokens.traits
    val haptics = rememberAppHaptics()
    val outlinedControls = tokens.border.outlineFilledControls
    val containerBorder = when {
        outlinedControls -> filledControlBorder()
        traits.bracketedLabels -> ContainerBorder(1.dp, c.outline, dashed = true)
        traits.navIndicator == NavIndicator.Underline -> null
        tokens.traits.cardFill == CardFill.Frosted -> ContainerBorder(1.dp, c.cardBorder)
        else -> ContainerBorder(1.dp, c.outline)
    }
    val containerFill: Brush? = when {
        outlinedControls -> SolidColor(c.surface)
        tokens.traits.cardFill == CardFill.Frosted -> SolidColor(c.card)
        else -> null
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .themedContainer(tokens.shapes.button, containerFill, containerBorder, if (outlinedControls) tokens.smallShadow else ShadowSpec.None)
            .selectableGroup(),
    ) {
        val count = options.size
        val segmentWidth = maxWidth / count
        val index = selectedIndex.coerceIn(0, count - 1)
        val indicatorX by animateDpAsState(segmentWidth * index, tokens.motion.spec(), label = "segment")
        val underline = traits.navIndicator == NavIndicator.Underline
        // Sliding selection indicator.
        Box(
            Modifier
                .offset(x = indicatorX)
                .width(segmentWidth)
                .fillMaxHeight()
                .padding(if (outlinedControls || underline || traits.bracketedLabels) 0.dp else 4.dp)
                .then(
                    if (underline) {
                        Modifier.drawBehind {
                            val h = 3.dp.toPx()
                            drawRect(c.primary, topLeft = Offset(0f, size.height - h), size = androidx.compose.ui.geometry.Size(size.width, h))
                        }
                    } else {
                        Modifier.themedContainer(
                            if (outlinedControls || traits.bracketedLabels) RectangleShape else tokens.shapes.button,
                            SolidColor(if (traits.outlinedPrimary) c.primary.copy(alpha = 0.18f) else c.primary),
                            null,
                            ShadowSpec.None,
                        )
                    },
                ),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { i, option ->
                val selected = i == index
                val content = when {
                    !enabled -> c.disabledContent
                    selected && underline -> c.primary
                    selected && traits.outlinedPrimary -> c.primary
                    selected -> c.onPrimary
                    else -> c.onSurface
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = selected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = {
                                if (!selected) {
                                    haptics.tick()
                                    onSelect(i)
                                }
                            },
                        )
                        .then(
                            if (outlinedControls && i > 0) {
                                Modifier.drawBehind {
                                    drawRect(tokens.border.color, size = androidx.compose.ui.geometry.Size(tokens.border.width.toPx(), size.height))
                                }
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        icons?.getOrNull(i)?.let {
                            Icon(it, null, tint = content, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = option.let { if (traits.uppercaseLabels) it.uppercase(Locale.getDefault()) else it },
                            style = tokens.typography.labelLarge.forText(option).copy(fontWeight = if (selected) FontWeight.Bold else tokens.typography.labelLarge.fontWeight),
                            color = content,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        )
                    }
                }
            }
        }
    }
}
