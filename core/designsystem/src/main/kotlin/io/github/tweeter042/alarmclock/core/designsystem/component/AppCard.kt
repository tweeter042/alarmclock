package io.github.tweeter042.alarmclock.core.designsystem.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import io.github.tweeter042.alarmclock.core.designsystem.theme.CardFill
import io.github.tweeter042.alarmclock.core.designsystem.theme.ShadowSpec

/** Emphasis of an [AppCard]. */
enum class AppCardVariant {
    /** Regular content card. */
    Default,
    /** Highlighted card (next alarm, active timer). */
    Emphasis,
    /** No fill, outline only. */
    Outlined,
}

/** Colours of a card variant in the current theme. */
internal data class CardStyle(val fill: Brush?, val content: Color, val border: ContainerBorder?, val shadow: ShadowSpec) {
    /** Solid colour behind the content, for [LocalContainerColor]. */
    fun containerColor(page: Color, surface: Color): Color = when (val f = fill) {
        is SolidColor -> if (f.value.alpha < 1f) surface else f.value
        else -> page
    }
}

@Composable
internal fun cardStyle(variant: AppCardVariant): CardStyle {
    val tokens = AppTheme.tokens
    val c = tokens.colors
    val border = cardBorder()
    return when (variant) {
        AppCardVariant.Default -> CardStyle(SolidColor(c.card), c.onCard, border, tokens.shadow)
        AppCardVariant.Emphasis -> when {
            tokens.traits.bracketedLabels -> CardStyle(SolidColor(c.primary), c.onPrimary, null, ShadowSpec.None)
            tokens.shadow is ShadowSpec.Glow -> CardStyle(
                SolidColor(c.surfaceVariant),
                c.onSurface,
                ContainerBorder(2.dp, c.accent),
                ShadowSpec.Glow((tokens.shadow as ShadowSpec.Glow).radius, c.accent.copy(alpha = 0.6f)),
            )
            tokens.border.outlineFilledControls -> CardStyle(SolidColor(c.accent), c.onAccent, border, tokens.shadow)
            tokens.traits.cardFill == CardFill.Outline -> CardStyle(SolidColor(c.background), c.primary, ContainerBorder(1.5.dp, c.primary), ShadowSpec.None)
            else -> CardStyle(SolidColor(c.primaryContainer), c.onPrimaryContainer, border?.copy(color = c.primary.copy(alpha = 0.3f)), tokens.shadow)
        }
        AppCardVariant.Outlined -> CardStyle(
            null,
            c.onBackground,
            border ?: ContainerBorder(1.dp, c.outline),
            ShadowSpec.None,
        )
    }
}

/** A themed card. Content color is set for children (`LocalContentColor`). */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    variant: AppCardVariant = AppCardVariant.Default,
    contentPadding: PaddingValues = PaddingValues(AppTheme.spacing.card),
    content: @Composable ColumnScope.() -> Unit,
) {
    val style = cardStyle(variant)
    val tokens = AppTheme.tokens
    Column(
        modifier = modifier
            .themedContainer(tokens.shapes.card, style.fill, style.border, style.shadow)
            .padding(contentPadding),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides style.content,
            LocalContainerColor provides style.containerColor(tokens.colors.background, tokens.colors.surface),
        ) { content() }
    }
}

/** A clickable themed card with the theme's press effect (slides onto its shadow in Brutal, …). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    variant: AppCardVariant = AppCardVariant.Default,
    contentPadding: PaddingValues = PaddingValues(AppTheme.spacing.card),
    content: @Composable ColumnScope.() -> Unit,
) {
    val style = cardStyle(variant)
    val tokens = AppTheme.tokens
    val interaction = remember { MutableInteractionSource() }
    val pressed by rememberPressFraction(interaction, enabled)
    Column(
        modifier = modifier
            .themedContainer(tokens.shapes.card, style.fill, style.border, style.shadow, tokens.traits.pressEffect, pressed)
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                onClickLabel = onClickLabel,
                role = Role.Button,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .padding(contentPadding),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (enabled) style.content else style.content.dim(0.5f),
            LocalContainerColor provides style.containerColor(tokens.colors.background, tokens.colors.surface),
        ) { content() }
    }
}
