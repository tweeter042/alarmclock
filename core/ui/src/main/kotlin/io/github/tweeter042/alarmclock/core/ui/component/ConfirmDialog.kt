package io.github.tweeter042.alarmclock.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import io.github.tweeter042.alarmclock.core.designsystem.component.AppConfirmDialog
import io.github.tweeter042.alarmclock.core.designsystem.R as DsR

/**
 * "Are you sure?" dialog. [onConfirm] runs first, then [onDismiss] closes the dialog, so the caller
 * only has to hide it in one place.
 *
 * @param destructive styles the confirm button as a danger action ("Delete").
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String = stringResource(DsR.string.ds_cancel),
    destructive: Boolean = false,
    icon: ImageVector? = null,
) {
    AppConfirmDialog(
        title = title,
        message = message,
        confirmLabel = confirmLabel,
        onConfirm = {
            onConfirm()
            onDismiss()
        },
        onDismiss = onDismiss,
        modifier = modifier,
        dismissLabel = dismissLabel,
        destructive = destructive,
        icon = icon,
    )
}

/**
 * Holds what a [ConfirmDialog] is asking about ("delete *this* alarm?").
 *
 * ```
 * val confirmDelete = rememberConfirmState<Alarm>()
 * … onLongClick = { confirmDelete.ask(alarm) } …
 * ConfirmDialog(confirmDelete, title = { stringResource(R.string.alarms_delete_title) }, …) { alarm -> vm.delete(alarm) }
 * ```
 */
@Stable
class ConfirmState<T : Any> internal constructor() {
    /** The item awaiting confirmation; null while no dialog is shown. */
    var pending: T? by mutableStateOf(null)
        private set

    fun ask(item: T) {
        pending = item
    }

    fun dismiss() {
        pending = null
    }
}

@Composable
fun <T : Any> rememberConfirmState(): ConfirmState<T> = remember { ConfirmState() }

/** Shows a [ConfirmDialog] while [state] has a pending item and passes it to [onConfirm]. */
@Composable
fun <T : Any> ConfirmDialog(
    state: ConfirmState<T>,
    title: @Composable (T) -> String,
    message: @Composable (T) -> String,
    confirmLabel: String,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    icon: ImageVector? = null,
    onConfirm: (T) -> Unit,
) {
    val item = state.pending ?: return
    ConfirmDialog(
        title = title(item),
        message = message(item),
        confirmLabel = confirmLabel,
        onConfirm = { onConfirm(item) },
        onDismiss = state::dismiss,
        modifier = modifier,
        destructive = destructive,
        icon = icon,
    )
}
