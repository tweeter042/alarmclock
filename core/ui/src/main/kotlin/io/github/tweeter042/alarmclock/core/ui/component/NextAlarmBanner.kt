package io.github.tweeter042.alarmclock.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.tweeter042.alarmclock.core.designsystem.component.AppCard
import io.github.tweeter042.alarmclock.core.designsystem.component.AppCardVariant
import io.github.tweeter042.alarmclock.core.designsystem.component.LeadingIcon
import io.github.tweeter042.alarmclock.core.designsystem.icon.AppIcons
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import io.github.tweeter042.alarmclock.core.model.alarm.NextAlarm
import io.github.tweeter042.alarmclock.core.model.alarm.RingKind
import io.github.tweeter042.alarmclock.core.ui.R
import io.github.tweeter042.alarmclock.core.ui.format.DurationStyle
import io.github.tweeter042.alarmclock.core.ui.format.rememberUiFormatters
import io.github.tweeter042.alarmclock.core.ui.time.CountdownStyle
import io.github.tweeter042.alarmclock.core.ui.time.LocalUiClock
import io.github.tweeter042.alarmclock.core.ui.time.countdownText
import io.github.tweeter042.alarmclock.core.ui.time.rememberRemainingMillis
import io.github.tweeter042.alarmclock.core.ui.time.rememberZone

/**
 * The "next alarm" card at the top of the alarm list (and on other screens): kind, day and time
 * ("Tomorrow at 7:00 AM"), a live "Rings in 7 h 24 min" and the label. With [next] = null it
 * says that no alarm is on. TalkBack reads the card as one item.
 */
@Composable
fun NextAlarmBanner(
    next: NextAlarm?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    if (next == null) {
        BannerCard(onClick, AppCardVariant.Default, modifier) {
            BannerRow(AppIcons.AlarmOff) {
                Text(
                    text = stringResource(R.string.ui_no_alarms_title),
                    style = AppTheme.typography.titleMedium,
                    color = LocalContentColor.current,
                )
                Text(
                    text = stringResource(R.string.ui_no_alarms_body),
                    style = AppTheme.typography.bodyMedium,
                    color = LocalContentColor.current.copy(alpha = 0.8f),
                )
            }
        }
        return
    }
    val formatters = rememberUiFormatters()
    val zone = rememberZone()
    val clock = LocalUiClock.current
    val remaining = rememberRemainingMillis(next.triggerAt, unitMillis = 60_000L)
    val now = next.triggerAt - remaining
    val dayAndTime = formatters.time.dayAndTime(next.triggerAt, zone, if (remaining > 0) now else clock.now())
    val ringsIn = stringResource(R.string.ui_rings_in, countdownText(formatters.duration, remaining, CountdownStyle.Relative, DurationStyle.SHORT))
    val title = stringResource(next.kind.titleRes())
    // Screen readers hear whole words: "rings in 7 hours and 24 minutes".
    val ringsInSpoken = stringResource(R.string.ui_rings_in, countdownText(formatters.duration, remaining, CountdownStyle.Relative, DurationStyle.LONG))
    val spoken = listOf(title, dayAndTime, ringsInSpoken, next.label).filter { it.isNotBlank() }.joinToString(", ")
    BannerCard(onClick, AppCardVariant.Emphasis, modifier.semantics { contentDescription = spoken }) {
        BannerRow(next.kind.icon()) {
            Text(
                text = title,
                style = AppTheme.typography.labelLarge,
                color = LocalContentColor.current.copy(alpha = 0.8f),
            )
            Text(
                text = dayAndTime,
                style = AppTheme.typography.titleLarge,
                color = LocalContentColor.current,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(AppTheme.spacing.xxs))
            Text(
                text = ringsIn,
                style = AppTheme.typography.bodyMedium,
                color = LocalContentColor.current,
            )
            if (next.label.isNotBlank()) {
                Text(
                    text = next.label,
                    style = AppTheme.typography.bodyMedium,
                    color = LocalContentColor.current.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BannerCard(
    onClick: (() -> Unit)?,
    variant: AppCardVariant,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val merged = modifier.fillMaxWidth().semantics(mergeDescendants = true) { }
    if (onClick != null) {
        AppCard(onClick = onClick, modifier = merged, variant = variant) { content() }
    } else {
        AppCard(modifier = merged, variant = variant) { content() }
    }
}

@Composable
private fun BannerRow(icon: ImageVector, text: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LeadingIcon(icon)
        Spacer(Modifier.width(AppTheme.spacing.lg))
        Column(Modifier.weight(1f)) { text() }
    }
}

@Composable
private fun RingKind.icon(): ImageVector = when (this) {
    RingKind.NAP -> AppIcons.Nap
    RingKind.CALENDAR -> AppIcons.Calendar
    RingKind.LOCATION -> AppIcons.Location
    RingKind.TIMER -> AppIcons.Hourglass
    RingKind.TEST, RingKind.ALARM -> AppIcons.Alarm
}

private fun RingKind.titleRes(): Int = when (this) {
    RingKind.ALARM -> R.string.ui_next_alarm
    RingKind.NAP -> R.string.ui_next_nap
    RingKind.CALENDAR -> R.string.ui_next_calendar
    RingKind.LOCATION -> R.string.ui_next_location
    RingKind.TEST -> R.string.ui_next_test
    RingKind.TIMER -> R.string.ui_next_timer
}
