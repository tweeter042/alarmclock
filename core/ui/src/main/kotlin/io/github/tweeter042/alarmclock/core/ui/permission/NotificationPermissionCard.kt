package io.github.tweeter042.alarmclock.core.ui.permission

import android.Manifest
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.tweeter042.alarmclock.core.designsystem.component.AppButton
import io.github.tweeter042.alarmclock.core.designsystem.component.AppButtonSize
import io.github.tweeter042.alarmclock.core.designsystem.component.AppCard
import io.github.tweeter042.alarmclock.core.designsystem.component.AppCardVariant
import io.github.tweeter042.alarmclock.core.designsystem.component.LeadingIcon
import io.github.tweeter042.alarmclock.core.designsystem.icon.AppIcons
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import io.github.tweeter042.alarmclock.core.ui.R

/**
 * A card that appears only while the app may not post notifications (permission not granted, or
 * notifications switched off for the app). Without notifications an alarm cannot show its
 * full-screen ring screen on the lock screen, so the alarm list shows this card at the top.
 * "Turn on" asks for the permission, or opens the notification settings when that is the only way.
 */
@Composable
fun NotificationPermissionCard(modifier: Modifier = Modifier, onEnabledChange: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(notificationsEnabled(context)) }
    val requester = rememberPermissionRequester(
        permissions = listOf(Manifest.permission.POST_NOTIFICATIONS),
        settingsIntent = ::appNotificationSettingsIntent,
        onResult = {
            enabled = notificationsEnabled(context)
            onEnabledChange(enabled)
        },
    )
    LifecycleResumeEffect(context) {
        val now = notificationsEnabled(context)
        if (now != enabled) {
            enabled = now
            onEnabledChange(now)
        }
        onPauseOrDispose { }
    }
    if (enabled) return
    val title = stringResource(R.string.ui_notifications_off_title)
    val body = stringResource(R.string.ui_notifications_off_body)
    PermissionPrompts(requester, rationaleTitle = title, rationaleMessage = body, icon = AppIcons.NotificationsOff)
    NotificationPermissionCardContent(
        title = title,
        body = body,
        onTurnOn = {
            // Permission granted but notifications switched off for the app: only Settings helps.
            if (requester.isGranted) requester.openSettings() else requester.request()
        },
        modifier = modifier,
    )
}

@Composable
internal fun NotificationPermissionCardContent(title: String, body: String, onTurnOn: () -> Unit, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier.fillMaxWidth(), variant = AppCardVariant.Emphasis) {
        Row(verticalAlignment = Alignment.Top) {
            LeadingIcon(AppIcons.NotificationsOff)
            Spacer(Modifier.width(AppTheme.spacing.lg))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
                Text(title, style = AppTheme.typography.titleMedium, color = LocalContentColor.current)
                Text(body, style = AppTheme.typography.bodyMedium, color = LocalContentColor.current.copy(alpha = 0.85f))
            }
        }
        Spacer(Modifier.height(AppTheme.spacing.md))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            AppButton(text = stringResource(R.string.ui_notifications_turn_on), onClick = onTurnOn, size = AppButtonSize.Small)
        }
    }
}

/** True if the app may post notifications (permission granted and not switched off). */
fun notificationsEnabled(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()
