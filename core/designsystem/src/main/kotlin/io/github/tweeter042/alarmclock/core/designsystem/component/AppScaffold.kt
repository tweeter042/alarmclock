package io.github.tweeter042.alarmclock.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.tweeter042.alarmclock.core.designsystem.R
import io.github.tweeter042.alarmclock.core.designsystem.icon.AppIcons
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import io.github.tweeter042.alarmclock.core.designsystem.theme.CardFill
import io.github.tweeter042.alarmclock.core.designsystem.theme.DividerStyle
import io.github.tweeter042.alarmclock.core.designsystem.theme.HeaderDecor
import io.github.tweeter042.alarmclock.core.designsystem.theme.ShadowSpec
import io.github.tweeter042.alarmclock.core.designsystem.theme.ThemeTokens

/**
 * One UI style screen scaffold: a large title in a tall header (content starts in the reachable
 * lower part of the screen) that collapses into a regular top bar as the content scrolls, plus
 * bottom bar, FAB and snackbar slots over the theme background. Edge-to-edge: the header handles
 * the status bar; pass the content [PaddingValues] to your scrolling container.
 *
 * The content must scroll (LazyColumn / verticalScroll) for the header to collapse.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    largeTitle: Boolean = true,
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    animatedBackground: Boolean = false,
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {
    val tokens = AppTheme.tokens
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    ThemedBackground(modifier.fillMaxSize(), animated = animatedBackground) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                CollapsingHeader(
                    title = title,
                    subtitle = subtitle,
                    navigationIcon = navigationIcon,
                    actions = actions,
                    scrollBehavior = scrollBehavior,
                    largeTitle = largeTitle,
                )
            },
            bottomBar = bottomBar,
            floatingActionButton = floatingActionButton,
            snackbarHost = { if (snackbarHostState != null) AppSnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent,
            contentColor = tokens.colors.onBackground,
            contentWindowInsets = contentWindowInsets,
            content = content,
        )
    }
}

/** Expanded header height: about 30 % of the screen like One UI, within sensible bounds. */
@Composable
private fun expandedHeaderHeight(): Dp {
    val screen = LocalConfiguration.current.screenHeightDp
    return (screen * 0.30f).dp.coerceIn(176.dp, 300.dp)
}

private val CollapsedHeight = 64.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollapsingHeader(
    title: String,
    subtitle: String?,
    navigationIcon: (@Composable () -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    largeTitle: Boolean,
) {
    val tokens = AppTheme.tokens
    val c = tokens.colors
    val density = LocalDensity.current
    val expanded = if (largeTitle) expandedHeaderHeight() else CollapsedHeight
    val limitPx = with(density) { (CollapsedHeight - expanded).toPx() }
    val state = scrollBehavior.state
    SideEffect {
        if (state.heightOffsetLimit != limitPx) state.heightOffsetLimit = limitPx
    }
    val offsetDp = with(density) { state.heightOffset.coerceIn(limitPx, 0f).toDp() }
    val height = expanded + offsetDp
    val fraction = if (largeTitle) state.collapsedFraction else 1f
    val overlapped = state.overlappedFraction > 0.01f
    val showBar = !largeTitle && overlapped || largeTitle && fraction > 0.97f && overlapped

    Column(
        Modifier
            .fillMaxWidth()
            .then(if (showBar) Modifier.background(barFill(tokens)) else Modifier)
            .windowInsetsPadding(WindowInsets.statusBars)
            .then(if (showBar) Modifier.barEdge(tokens, top = false) else Modifier),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .clipToBounds(),
        ) {
            if (largeTitle) {
                LargeTitle(
                    title = title,
                    subtitle = subtitle,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = tokens.spacing.screen, end = tokens.spacing.screen, bottom = 18.dp)
                        .graphicsLayer { alpha = (1f - fraction * 1.8f).coerceIn(0f, 1f) },
                )
            }
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(CollapsedHeight)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompositionLocalProvider(LocalContentColor provides c.onBackground) {
                    if (navigationIcon != null) navigationIcon() else Spacer(Modifier.width(tokens.spacing.screen - 4.dp))
                    Text(
                        text = barTitle(title, tokens),
                        style = tokens.typography.barTitle,
                        color = c.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = if (navigationIcon != null) 4.dp else 0.dp)
                            .alpha(((fraction - 0.55f) / 0.45f).coerceIn(0f, 1f))
                            .semantics { if (!largeTitle) heading() },
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, content = actions)
                }
            }
        }
    }
}

private fun barTitle(title: String, tokens: ThemeTokens) =
    if (tokens.traits.headerDecor == HeaderDecor.Prompt) "> $title" else title

@Composable
private fun LargeTitle(title: String, subtitle: String?, modifier: Modifier) {
    val tokens = AppTheme.tokens
    val c = tokens.colors
    val decor = tokens.traits.headerDecor
    Column(modifier.semantics(mergeDescendants = false) { heading() }) {
        if (decor == HeaderDecor.Masthead && subtitle != null) {
            Text(
                text = subtitle.uppercase(java.util.Locale.getDefault()),
                style = tokens.typography.sectionLabel.forText(subtitle),
                color = c.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (decor == HeaderDecor.Prompt) {
                Text("> ", style = tokens.typography.largeTitle, color = c.onSurfaceVariant)
            }
            Text(
                text = title,
                style = tokens.typography.largeTitle.forText(title).let {
                    if (tokens.traits.textGlow) it.copy(shadow = glowShadow(c.glow, if (decor == HeaderDecor.Prompt) 16f else 28f)) else it
                },
                color = c.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (decor == HeaderDecor.Prompt) {
                BlinkingBlockCursor(height = 30.dp, color = c.primary)
            }
        }
        when (decor) {
            HeaderDecor.Masthead -> {
                Spacer(Modifier.height(10.dp))
                AppDivider()
            }
            HeaderDecor.Rule -> if (subtitle != null) {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .themedContainer(
                            tokens.shapes.chip,
                            SolidColor(if (tokens.border.outlineFilledControls) c.accent else c.surfaceVariant),
                            filledControlBorder() ?: ContainerBorder(1.dp, c.outline),
                            tokens.smallShadow,
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        subtitle.uppercase(java.util.Locale.getDefault()),
                        style = tokens.typography.labelLarge.forText(subtitle),
                        color = if (tokens.border.outlineFilledControls) c.onAccent else c.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            else -> if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    style = tokens.typography.bodyLarge,
                    color = c.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A blinking block cursor (Terminal). Static with reduce motion. */
@Composable
fun BlinkingBlockCursor(height: Dp, color: Color, modifier: Modifier = Modifier) {
    val motion = AppTheme.motion
    val alpha = if (motion.ambientEnabled) {
        val t = rememberInfiniteTransition(label = "cursor")
        val v by t.animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(1060, easing = BlinkEasing), RepeatMode.Restart),
            label = "blink",
        )
        v
    } else {
        1f
    }
    Box(
        modifier
            .padding(start = 4.dp)
            .size(width = height * 0.5f, height = height)
            .alpha(alpha)
            .background(color),
    )
}

/** On for the first half of the cycle, off for the second (classic 530 ms cursor blink). */
private val BlinkEasing = androidx.compose.animation.core.Easing { f -> if (f < 0.5f) 0f else 1f }

/** A compact top bar (sub-screens, full-screen pickers). */
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    showBackground: Boolean = false,
) {
    val tokens = AppTheme.tokens
    val c = tokens.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (showBackground) Modifier.background(barFill(tokens)).barEdge(tokens, top = false) else Modifier)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(CollapsedHeight)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides c.onBackground) {
            if (navigationIcon != null) navigationIcon() else Spacer(Modifier.width(tokens.spacing.screen - 4.dp))
            Text(
                text = barTitle(title, tokens),
                style = tokens.typography.barTitle,
                color = c.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (navigationIcon != null) 4.dp else 0.dp)
                    .semantics { heading() },
            )
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

/** Standard "navigate up" button for [AppScaffold] / [AppTopBar]. */
@Composable
fun AppBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    AppIconButton(
        icon = AppIcons.Back,
        contentDescription = stringResource(R.string.ds_navigate_up),
        onClick = onClick,
        modifier = modifier,
    )
}

/** Fill of top/bottom bars when content is underneath. */
internal fun barFill(tokens: ThemeTokens) = when (tokens.traits.cardFill) {
    CardFill.Frosted -> tokens.colors.surface.copy(alpha = 0.92f)
    else -> tokens.colors.bar
}

/** The theme's edge line between a bar and the content ([top] = line at the top of the bar). */
internal fun Modifier.barEdge(tokens: ThemeTokens, top: Boolean): Modifier = drawBehind {
    val c = tokens.colors
    val y = if (top) 0f else size.height
    when (tokens.traits.dividerStyle) {
        DividerStyle.Thick -> {
            val h = tokens.border.width.toPx()
            drawRect(tokens.border.color, topLeft = Offset(0f, if (top) 0f else size.height - h), size = Size(size.width, h))
        }
        DividerStyle.Dotted -> {
            val h = tokens.border.width.toPx()
            drawRect(tokens.border.color, topLeft = Offset(0f, if (top) 0f else size.height - h), size = Size(size.width, h))
        }
        DividerStyle.Double -> {
            val thick = 1.5.dp.toPx()
            val thin = 0.75.dp.toPx()
            val gap = 2.5.dp.toPx()
            if (top) {
                drawRect(c.onBackground, size = Size(size.width, thick))
                drawRect(c.onBackground, topLeft = Offset(0f, thick + gap), size = Size(size.width, thin))
            } else {
                drawRect(c.onBackground, topLeft = Offset(0f, size.height - thick), size = Size(size.width, thick))
                drawRect(c.onBackground, topLeft = Offset(0f, size.height - thick - gap - thin), size = Size(size.width, thin))
            }
        }
        DividerStyle.Dashed -> drawLine(
            c.outline, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
        )
        DividerStyle.Glow -> drawGlowLine(c.glow, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), 6.dp.toPx())
        DividerStyle.Hairline -> if (tokens.traits.cardFill == CardFill.Frosted) {
            drawLine(tokens.colors.cardBorder, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        } else if (tokens.shadow == ShadowSpec.None || tokens.traits.cardFill == CardFill.Outline) {
            drawLine(c.outlineVariant, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
    }
}
