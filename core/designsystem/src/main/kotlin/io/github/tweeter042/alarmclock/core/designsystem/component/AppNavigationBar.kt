package io.github.tweeter042.alarmclock.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import io.github.tweeter042.alarmclock.core.designsystem.theme.CardFill
import io.github.tweeter042.alarmclock.core.designsystem.theme.NavIndicator
import io.github.tweeter042.alarmclock.core.designsystem.theme.ShadowSpec
import io.github.tweeter042.alarmclock.core.designsystem.util.rememberAppHaptics
import io.github.tweeter042.alarmclock.core.model.theme.ThemeId
import java.util.Locale

/** Bottom navigation bar. Put [AppNavigationItem]s inside. Handles the navigation-bar inset. */
@Composable
fun AppNavigationBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val tokens = AppTheme.tokens
    val c = tokens.colors
    val fill = when {
        tokens.traits.cardFill == CardFill.Frosted -> c.surface.copy(alpha = 0.86f)
        tokens.traits.cardFill == CardFill.Outline -> Color.Black
        c.isDynamic || tokens.themeId == ThemeId.MATERIAL_YOU -> c.material.surfaceContainer
        tokens.border.outlineFilledControls -> c.surface
        else -> c.background
    }
    Box(
        modifier
            .fillMaxWidth()
            .background(fill)
            .barEdge(tokens, top = true)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(76.dp)
                .padding(horizontal = 8.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** One destination of [AppNavigationBar]. */
@Composable
fun RowScope.AppNavigationItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    selectedIcon: ImageVector? = null,
    badgeCount: Int? = null,
    enabled: Boolean = true,
) {
    val tokens = AppTheme.tokens
    val c = tokens.colors
    val traits = tokens.traits
    val haptics = rememberAppHaptics()
    val interaction = remember { MutableInteractionSource() }
    val shown by animateFloatAsState(if (selected) 1f else 0f, tokens.motion.spec(), label = "navIndicator")
    val text = label.let { if (traits.uppercaseLabels) it.uppercase(Locale.getDefault()) else it }

    val iconTint by animateColorAsState(
        when {
            !enabled -> c.disabledContent
            !selected -> c.onSurfaceVariant
            traits.navIndicator == NavIndicator.Pill -> c.onPrimaryContainer
            traits.navIndicator == NavIndicator.Block -> if (tokens.themeId == ThemeId.FLIP_CLOCK) c.flipDigit else c.onPrimary
            else -> c.primary
        },
        tokens.motion.fade(),
        label = "navTint",
    )
    val labelColor = when {
        !enabled -> c.disabledContent
        selected && (traits.navIndicator == NavIndicator.GlowLine || traits.navIndicator == NavIndicator.Dot || traits.navIndicator == NavIndicator.Underline) -> c.primary
        selected -> c.onSurface
        else -> c.onSurfaceVariant
    }

    Column(
        modifier = modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.Tab,
                interactionSource = interaction,
                indication = ripple(bounded = false, radius = 36.dp),
                onClick = {
                    if (!selected) haptics.tick()
                    onClick()
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            val indicator: Modifier = when (traits.navIndicator) {
                NavIndicator.Pill -> Modifier
                    .graphicsLayer { alpha = shown; scaleX = 0.6f + 0.4f * shown }
                    .themedContainer(CircleShape, SolidColor(c.primaryContainer), null, ShadowSpec.None)
                NavIndicator.Block -> Modifier
                    .graphicsLayer { alpha = shown }
                    .themedContainer(
                        tokens.shapes.indicator,
                        SolidColor(if (tokens.themeId == ThemeId.FLIP_CLOCK) c.flipCard else c.primary),
                        filledControlBorder(),
                        ShadowSpec.None,
                    )
                NavIndicator.Bracket -> Modifier
                    .graphicsLayer { alpha = shown }
                    .background(c.primary.copy(alpha = 0.16f))
                else -> Modifier
            }
            Box(Modifier.size(width = 60.dp, height = 32.dp).then(indicator))
            if (traits.navIndicator == NavIndicator.GlowLine && selected) {
                Box(
                    Modifier
                        .offset(y = (-22).dp)
                        .size(width = 28.dp, height = 3.dp)
                        .themedContainer(CircleShape, SolidColor(c.primary), null, ShadowSpec.Glow(8.dp, c.glow.copy(alpha = 0.8f))),
                )
            }
            Icon(
                imageVector = if (selected && selectedIcon != null) selectedIcon else icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp),
            )
            if (badgeCount != null) {
                AppBadge(count = badgeCount, modifier = Modifier.align(Alignment.TopEnd).offset(x = (-8).dp, y = (-2).dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (traits.navIndicator == NavIndicator.Bracket && selected) "[$text]" else text,
            style = tokens.typography.labelMedium.forText(text).copy(fontWeight = if (selected) FontWeight.Bold else tokens.typography.labelMedium.fontWeight),
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            autoSize = TextAutoSize.StepBased(
                minFontSize = tokens.typography.labelMedium.fontSize * 0.72f,
                maxFontSize = tokens.typography.labelMedium.fontSize,
                stepSize = 0.25.sp,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (selected && traits.navIndicator == NavIndicator.Underline) {
                        Modifier.drawBehind {
                            val h = 2.dp.toPx()
                            val w = size.width * 0.4f
                            drawRect(c.primary, topLeft = Offset((size.width - w) / 2, size.height + 1.dp.toPx()), size = Size(w, h))
                        }
                    } else {
                        Modifier
                    },
                ),
        )
        if (traits.navIndicator == NavIndicator.Dot) {
            Spacer(Modifier.height(3.dp))
            Box(Modifier.size(4.dp).graphicsLayer { alpha = shown }.background(c.primary, CircleShape))
        }
    }
}
