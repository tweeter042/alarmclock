package io.github.tweeter042.alarmclock.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import io.github.tweeter042.alarmclock.core.designsystem.theme.CardFill
import io.github.tweeter042.alarmclock.core.designsystem.theme.ColorContrast
import io.github.tweeter042.alarmclock.core.designsystem.theme.ShadowSpec

/**
 * Placeholder for empty lists ("No alarms yet"). The icon sits in the theme's icon container;
 * the action (if any) is a primary button.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val tokens = AppTheme.tokens
    val c = tokens.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.xl, vertical = tokens.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val outline = tokens.traits.cardFill == CardFill.Outline || tokens.traits.bracketedLabels
        Box(
            modifier = Modifier
                .size(104.dp)
                .themedContainer(
                    tokens.shapes.iconContainer,
                    if (outline) null else SolidColor(if (tokens.border.outlineFilledControls) c.accent else c.primaryContainer),
                    filledControlBorder() ?: if (outline) ContainerBorder(1.dp, c.outline, dashed = tokens.traits.bracketedLabels) else null,
                    tokens.smallShadow,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = when {
                    outline -> c.primary
                    tokens.border.outlineFilledControls -> c.onAccent
                    else -> c.onPrimaryContainer
                },
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(tokens.spacing.xl))
        Text(
            text = title,
            style = tokens.typography.titleLarge,
            color = c.onBackground,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Spacer(Modifier.height(tokens.spacing.sm))
            Text(
                text = body,
                style = tokens.typography.bodyMedium,
                color = c.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 360.dp),
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(tokens.spacing.xl))
            AppButton(text = actionLabel, onClick = onAction)
        }
    }
}

/** Snackbar host that renders themed snackbars; pass it to AppScaffold or place it yourself. */
@Composable
fun AppSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState, modifier) { data -> AppSnackbar(data) }
}

/** A themed snackbar: inverse colours (ink on paper becomes paper on ink), theme shape and edge. */
@Composable
fun AppSnackbar(data: SnackbarData, modifier: Modifier = Modifier) {
    val tokens = AppTheme.tokens
    val c = tokens.colors
    val fill = when {
        tokens.traits.cardFill == CardFill.Outline -> c.background
        tokens.traits.bracketedLabels -> c.surfaceVariant
        else -> c.onSurface
    }
    val content = when {
        tokens.traits.cardFill == CardFill.Outline -> c.onBackground
        tokens.traits.bracketedLabels -> c.onSurface
        else -> c.surface.copy(alpha = 1f)
    }
    val action = ColorContrast.ensure(c.primary, fill, 4.5f)
    Row(
        modifier = modifier
            .padding(12.dp)
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .themedContainer(
                tokens.shapes.medium,
                SolidColor(fill),
                filledControlBorder() ?: if (tokens.traits.cardFill == CardFill.Outline) ContainerBorder(1.dp, c.primary) else null,
                if (tokens.shadow == ShadowSpec.None) ShadowSpec.None else tokens.smallShadow,
            )
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = data.visuals.message,
            style = tokens.typography.bodyMedium,
            color = content,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
        )
        val label = data.visuals.actionLabel
        if (label != null) {
            Text(
                text = label.let { if (tokens.traits.uppercaseLabels) it.uppercase(java.util.Locale.getDefault()) else it },
                style = tokens.typography.labelLarge,
                color = action,
                modifier = Modifier
                    .clickable(role = Role.Button) { data.performAction() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}
