package io.github.tweeter042.alarmclock.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.tweeter042.alarmclock.core.designsystem.component.AppLinearProgress
import io.github.tweeter042.alarmclock.core.designsystem.component.EmptyState
import io.github.tweeter042.alarmclock.core.designsystem.icon.AppIcons
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import io.github.tweeter042.alarmclock.core.model.util.LocalizedError
import io.github.tweeter042.alarmclock.core.ui.R

/**
 * Centered "loading" placeholder: the theme's indeterminate progress bar and an optional
 * [message] (defaults to "Loading…"). Place it where the content will appear.
 */
@Composable
fun LoadingState(modifier: Modifier = Modifier, message: String? = null) {
    val text = message ?: stringResource(R.string.ui_loading)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppTheme.spacing.xl, vertical = AppTheme.spacing.xxl)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            AppLinearProgress(progress = null, modifier = Modifier.width(160.dp))
            Spacer(Modifier.height(AppTheme.spacing.md))
            Text(text, style = AppTheme.typography.bodyMedium, color = AppTheme.colors.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/**
 * A failure placeholder with an optional retry button.
 *
 * @param title defaults to "Something went wrong".
 * @param message defaults to a generic "please try again" hint.
 */
@Composable
fun ErrorState(
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.ui_error_title),
    message: String? = stringResource(R.string.ui_error_generic),
    onRetry: (() -> Unit)? = null,
) {
    EmptyState(
        icon = AppIcons.Error,
        title = title,
        body = message,
        actionLabel = if (onRetry != null) stringResource(R.string.ui_retry) else null,
        onAction = onRetry,
        modifier = modifier.semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * [ErrorState] for an exception: errors implementing [LocalizedError] (e.g. backup failures from
 * core:data) show their own explanation, anything else the generic message.
 */
@Composable
fun ErrorState(
    error: Throwable,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val message = (error as? LocalizedError)?.let { stringResource(it.messageRes) } ?: stringResource(R.string.ui_error_generic)
    ErrorState(modifier = modifier, message = message, onRetry = onRetry)
}
