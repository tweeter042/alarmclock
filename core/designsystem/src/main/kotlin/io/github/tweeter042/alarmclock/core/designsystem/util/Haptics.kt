package io.github.tweeter042.alarmclock.core.designsystem.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme

/**
 * Haptic feedback that respects the user's "haptic feedback" accessibility setting
 * (AccessibilitySettings.hapticFeedback). Get one with [rememberAppHaptics].
 */
@Stable
class AppHaptics internal constructor(
    private val feedback: HapticFeedback,
    /** False when the user turned haptics off; every call is then a no-op. */
    val enabled: Boolean,
) {
    private fun perform(type: HapticFeedbackType) {
        if (enabled) feedback.performHapticFeedback(type)
    }

    /** A light tap for buttons and list rows. */
    fun click() = perform(HapticFeedbackType.ContextClick)

    /** Switch, checkbox or chip toggled. */
    fun toggle(on: Boolean) = perform(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)

    /** A detent passed: slider step, picker value, segmented button. */
    fun tick() = perform(HapticFeedbackType.SegmentTick)

    /** Fine-grained continuous movement (wheel pickers). */
    fun frequentTick() = perform(HapticFeedbackType.SegmentFrequentTick)

    /** Action completed (alarm saved, mission solved). */
    fun confirm() = perform(HapticFeedbackType.Confirm)

    /** Action refused (wrong answer, invalid input). */
    fun reject() = perform(HapticFeedbackType.Reject)

    fun longPress() = perform(HapticFeedbackType.LongPress)

    /** A drag gesture ended (reorder, swipe-to-dismiss). */
    fun gestureEnd() = perform(HapticFeedbackType.GestureEnd)
}

/** Haptics bound to the current theme's accessibility settings. */
@Composable
fun rememberAppHaptics(): AppHaptics {
    val feedback = LocalHapticFeedback.current
    val enabled = AppTheme.accessibility.hapticFeedback
    return remember(feedback, enabled) { AppHaptics(feedback, enabled) }
}
