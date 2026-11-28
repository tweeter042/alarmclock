package io.github.tweeter042.alarmclock.core.ui.picker

import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.tweeter042.alarmclock.core.designsystem.component.ContainerBorder
import io.github.tweeter042.alarmclock.core.designsystem.component.themedContainer
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import io.github.tweeter042.alarmclock.core.designsystem.theme.ShadowSpec
import io.github.tweeter042.alarmclock.core.designsystem.util.rememberAppHaptics
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Sizes shared by every wheel. */
internal object WheelDefaults {
    const val VisibleItems = 5
    const val LoopRepeats = 400
    val MinItemHeight = 48.dp
}

/**
 * One scrolling column of a wheel picker (hours, minutes, AM/PM, …): snaps to the centre row,
 * optionally loops, ticks haptically, fades rows away from the centre. Screen readers see a
 * single adjustable control ("Hour, 7"): swipe up/down or use the volume keys to change it.
 *
 * @param selectedIndex the value shown in the centre; external changes scroll the wheel.
 * @param onSelectedIndexChange called whenever the centre row changes while scrolling.
 */
@Composable
internal fun WheelColumn(
    count: Int,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    label: (Int) -> String,
    contentDescription: String,
    textStyle: TextStyle,
    itemHeight: Dp,
    width: Dp,
    modifier: Modifier = Modifier,
    stateDescription: (Int) -> String = label,
    loop: Boolean = true,
    enabled: Boolean = true,
    visibleItems: Int = WheelDefaults.VisibleItems,
) {
    require(count > 0) { "A wheel needs at least one value" }
    val loops = loop && count > 2
    val virtualCount = if (loops) count * WheelDefaults.LoopRepeats else count
    val middle = if (loops) (virtualCount / 2) - (virtualCount / 2) % count else 0
    val safeSelected = selectedIndex.coerceIn(0, count - 1)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = middle + safeSelected)
    val itemPx = with(LocalDensity.current) { itemHeight.toPx() }
    val centerVirtual by remember(state, itemPx, virtualCount) {
        derivedStateOf {
            (state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > itemPx / 2) 1 else 0).coerceIn(0, virtualCount - 1)
        }
    }
    val haptics = rememberAppHaptics()
    val currentOnChange by rememberUpdatedState(onSelectedIndexChange)
    val currentSelected by rememberUpdatedState(safeSelected)

    // User scrolling → report the centre value.
    LaunchedEffect(state, count) {
        snapshotFlow { centerVirtual % count }
            .distinctUntilChanged()
            .collect { index ->
                if (index != currentSelected) {
                    haptics.frequentTick()
                    currentOnChange(index)
                }
            }
    }
    // Value changed from outside (keyboard entry, TalkBack) → bring it to the centre.
    LaunchedEffect(safeSelected, count) {
        if (state.isScrollInProgress) return@LaunchedEffect
        val current = centerVirtual % count
        if (current != safeSelected) {
            val target = if (loops) nearestVirtualIndex(centerVirtual, current, safeSelected, count) else safeSelected
            state.scrollToItem(target.coerceIn(0, virtualCount - 1))
        }
    }

    val flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Center)
    val color = AppTheme.colors.onSurface
    val centerIndex = centerVirtual % count
    LazyColumn(
        state = state,
        flingBehavior = flingBehavior,
        userScrollEnabled = enabled,
        contentPadding = PaddingValues(vertical = itemHeight * (visibleItems / 2)),
        modifier = modifier
            .width(width)
            .height(itemHeight * visibleItems)
            .clearAndSetSemantics {
                this.contentDescription = contentDescription
                this.stateDescription = stateDescription(centerIndex)
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = centerIndex.toFloat(),
                    range = 0f..max(1, count - 1).toFloat(),
                    steps = max(0, count - 2),
                )
                if (enabled) {
                    setProgress { target ->
                        val index = target.roundToInt().coerceIn(0, count - 1)
                        if (index != centerIndex) currentOnChange(index)
                        true
                    }
                } else {
                    disabled()
                }
            },
    ) {
        items(virtualCount) { virtualIndex ->
            val index = virtualIndex % count
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .graphicsLayer {
                        val center = state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset / itemPx
                        val distance = abs(virtualIndex - center)
                        alpha = (1f - 0.3f * distance).coerceIn(0.12f, 1f) * if (enabled) 1f else 0.5f
                        val scale = (1f - 0.07f * distance).coerceAtLeast(0.78f)
                        scaleX = scale
                        scaleY = scale
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = label(index), style = textStyle, color = color, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

/** The virtual (looping) index showing [target] that is closest to [centerVirtual]. */
internal fun nearestVirtualIndex(centerVirtual: Int, current: Int, target: Int, count: Int): Int {
    val forward = Math.floorMod(target - current, count)
    val delta = if (forward <= count / 2) forward else forward - count
    return centerVirtual + delta
}

/** The highlighted row behind the centre of a set of wheels. */
@Composable
internal fun WheelSelectionBand(height: Dp, modifier: Modifier = Modifier) {
    val tokens = AppTheme.tokens
    val c = tokens.colors
    val border = if (tokens.border.isVisible) ContainerBorder(tokens.border.width, tokens.border.color) else null
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .themedContainer(tokens.shapes.field, SolidColor(c.surfaceVariant), border, ShadowSpec.None),
    )
}

/** Row height that keeps wheel text readable at large font scales. */
@Composable
internal fun wheelItemHeight(style: TextStyle): Dp {
    val density = LocalDensity.current
    val fromText = with(density) { (style.fontSize * 1.6f).toDp() }
    return max(WheelDefaults.MinItemHeight.value, fromText.value).dp
}

/** Width that fits the widest of [labels] in [style], plus padding. */
@Composable
internal fun rememberWheelWidth(labels: List<String>, style: TextStyle, padding: Dp = 16.dp): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(labels, style, density) {
        val widest = labels.maxOfOrNull { measurer.measure(it, style, maxLines = 1).size.width } ?: 0
        with(density) { widest.toDp() } + padding * 2
    }
}
