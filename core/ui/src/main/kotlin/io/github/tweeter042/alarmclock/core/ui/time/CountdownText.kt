package io.github.tweeter042.alarmclock.core.ui.time

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import io.github.tweeter042.alarmclock.core.ui.format.DurationFormatter
import io.github.tweeter042.alarmclock.core.ui.format.DurationRounding
import io.github.tweeter042.alarmclock.core.ui.format.DurationStyle
import io.github.tweeter042.alarmclock.core.ui.format.rememberUiFormatters
import kotlinx.coroutines.delay
import java.util.Locale

/** What [CountdownText] shows. */
enum class CountdownStyle {
    /** "in 7 h 24 min" (minute precision, rounded up). */
    Relative,

    /** "7 h 24 min" (minute precision, rounded up). */
    Duration,

    /** "05:00" / "1:05:09" (second precision, rounded up). */
    Digits,
}

/**
 * Text that counts down to [targetAt] (epoch millis). It recomposes only when the shown value
 * changes (once a minute for [CountdownStyle.Relative] and [CountdownStyle.Duration], once a
 * second for [CountdownStyle.Digits]) and only while the screen is visible. Screen readers get the
 * long form ("in 7 hours and 24 minutes"), without announcements on every tick.
 *
 * @param capitalize "In 7 h 24 min" for text that starts a line (no effect in Thai).
 * @param onFinished called once when the target time is reached.
 */
@Composable
fun CountdownText(
    targetAt: Long,
    modifier: Modifier = Modifier,
    style: CountdownStyle = CountdownStyle.Relative,
    textStyle: TextStyle = AppTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    capitalize: Boolean = false,
    maxLines: Int = 1,
    onFinished: (() -> Unit)? = null,
) {
    val remaining = rememberRemainingMillis(targetAt, if (style == CountdownStyle.Digits) SECOND else MINUTE, onFinished)
    val formatters = rememberUiFormatters()
    val text = countdownText(formatters.duration, remaining, style, DurationStyle.SHORT)
        .let { if (capitalize) it.capitalizeFirst(formatters.time.locale) else it }
    val spoken = if (style == CountdownStyle.Digits) {
        formatters.duration.durationWithSeconds(remaining, DurationStyle.LONG)
    } else {
        countdownText(formatters.duration, remaining, style, DurationStyle.LONG)
    }
    Text(
        text = text,
        style = textStyle,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        // TalkBack reads the content description (whole words) instead of the abbreviated text.
        modifier = modifier.semantics { contentDescription = spoken },
    )
}

/**
 * Milliseconds left until [targetAt] (never negative), refreshed whenever a display value
 * rounded up to [unitMillis] would change.
 */
@Composable
fun rememberRemainingMillis(targetAt: Long, unitMillis: Long = SECOND, onFinished: (() -> Unit)? = null): Long {
    val clock = LocalUiClock.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val finished by rememberUpdatedState(onFinished)
    var now by remember(clock) { mutableLongStateOf(clock.now()) }
    LaunchedEffect(targetAt, unitMillis, clock, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = clock.now()
                val left = targetAt - now
                if (left <= 0) {
                    finished?.invoke()
                    break
                }
                if (clock is FixedUiClock) break
                val toNextChange = left % unitMillis
                delay(if (toNextChange == 0L) unitMillis else toNextChange)
            }
        }
    }
    return (targetAt - now).coerceAtLeast(0L)
}

internal fun countdownText(formatter: DurationFormatter, remaining: Long, style: CountdownStyle, length: DurationStyle): String = when (style) {
    CountdownStyle.Relative -> formatter.until(remaining, length)
    CountdownStyle.Duration -> formatter.duration(remaining, length, DurationRounding.UP)
    CountdownStyle.Digits -> formatter.countdown(remaining)
}

/** Upper-cases the first letter ("in 5 min" → "In 5 min"); Thai text is unchanged. */
fun String.capitalizeFirst(locale: Locale): String =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

private const val SECOND = 1_000L
private const val MINUTE = 60_000L
