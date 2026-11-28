package io.github.tweeter042.alarmclock.core.ui.permission

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import io.github.tweeter042.alarmclock.core.designsystem.component.AppButton
import io.github.tweeter042.alarmclock.core.designsystem.component.AppButtonSize
import io.github.tweeter042.alarmclock.core.designsystem.component.AppButtonVariant
import io.github.tweeter042.alarmclock.core.designsystem.component.AppDialog
import io.github.tweeter042.alarmclock.core.designsystem.component.EmptyState
import io.github.tweeter042.alarmclock.core.designsystem.icon.AppIcons
import io.github.tweeter042.alarmclock.core.ui.R

/**
 * The dialogs of a [PermissionRequester]: the explanation shown before asking again
 * ([rationaleTitle] / [rationaleMessage], buttons "Not now" / "Continue") and the "Allow it in
 * Settings" dialog for permissions denied for good. Place it next to the button that calls
 * `requester.request()`.
 */
@Composable
fun PermissionPrompts(
    requester: PermissionRequester,
    rationaleTitle: String,
    rationaleMessage: String,
    icon: ImageVector? = null,
) {
    DisposableEffect(requester) {
        requester.promptsAttached = true
        onDispose { requester.promptsAttached = false }
    }
    when (requester.prompt) {
        PermissionPrompt.Rationale -> AppDialog(
            onDismissRequest = requester::dismissPrompt,
            title = rationaleTitle,
            text = rationaleMessage,
            icon = icon,
            confirmButton = {
                AppButton(text = stringResource(R.string.ui_permission_continue), onClick = requester::launch, size = AppButtonSize.Small)
            },
            dismissButton = {
                AppButton(
                    text = stringResource(R.string.ui_permission_not_now),
                    onClick = requester::dismissPrompt,
                    variant = AppButtonVariant.Text,
                    size = AppButtonSize.Small,
                )
            },
        )
        PermissionPrompt.Settings -> AppDialog(
            onDismissRequest = requester::dismissPrompt,
            title = stringResource(R.string.ui_permission_settings_title),
            text = rationaleMessage + "\n\n" + stringResource(R.string.ui_permission_settings_message),
            icon = icon,
            confirmButton = {
                AppButton(text = stringResource(R.string.ui_permission_open_settings), onClick = requester::openSettings, size = AppButtonSize.Small)
            },
            dismissButton = {
                AppButton(
                    text = stringResource(R.string.ui_permission_not_now),
                    onClick = requester::dismissPrompt,
                    variant = AppButtonVariant.Text,
                    size = AppButtonSize.Small,
                )
            },
        )
        null -> Unit
    }
}

/**
 * Shows [content] only when all [permissions] are granted. Otherwise explains why they are needed
 * ([title], [message]) with an "Allow" button, or "Open settings" once they are denied for good.
 * The state is re-checked when the user comes back from the system settings.
 */
@Composable
fun PermissionGate(
    permissions: List<String>,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = AppIcons.Lock,
    onResult: (granted: Boolean) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val requester = rememberPermissionRequester(permissions, onResult = onResult)
    if (requester.isGranted) {
        content()
        return
    }
    PermissionPrompts(requester, rationaleTitle = title, rationaleMessage = message, icon = icon)
    val permanentlyDenied = requester.status == PermissionStatus.PermanentlyDenied
    EmptyState(
        icon = icon,
        title = title,
        body = message,
        actionLabel = stringResource(if (permanentlyDenied) R.string.ui_permission_open_settings else R.string.ui_permission_allow),
        onAction = if (permanentlyDenied) requester::openSettings else requester::request,
        modifier = modifier,
    )
}
