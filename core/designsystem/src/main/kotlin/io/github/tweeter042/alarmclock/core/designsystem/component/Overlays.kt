package io.github.tweeter042.alarmclock.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.tweeter042.alarmclock.core.designsystem.R
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import io.github.tweeter042.alarmclock.core.designsystem.theme.CardFill
import io.github.tweeter042.alarmclock.core.designsystem.theme.HeaderDecor
import io.github.tweeter042.alarmclock.core.designsystem.theme.ShadowSpec

/**
 * A themed dialog. Buttons are laid out end-aligned; for long Thai labels they wrap to two rows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    text: String? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    BasicAlertDialog(onDismissRequest = onDismissRequest, modifier = modifier) {
        DialogContainer(title, confirmButton, icon = icon, text = text, dismissButton = dismissButton, content = content)
    }
}

/** The themed dialog panel (without the window), shared by [AppDialog] and previews. */
@Composable
internal fun DialogContainer(
    title: String,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    text: String? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val tokens = AppTheme.tokens
    val c = tokens.colors
    val fill = if (tokens.traits.cardFill == CardFill.Frosted) c.surface.copy(alpha = 0.94f) else if (tokens.traits.cardFill == CardFill.Outline) c.background else c.surface
    Column(
        modifier = modifier
            .widthIn(min = 280.dp, max = 560.dp)
            .themedContainer(
                tokens.shapes.dialog,
                SolidColor(fill),
                cardBorder() ?: if (tokens.traits.cardFill == CardFill.Outline) ContainerBorder(1.dp, c.outline) else null,
                tokens.shadow,
            )
            .padding(24.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides c.onSurface) {
            if (icon != null) {
                Icon(icon, null, tint = c.primary, modifier = Modifier.size(28.dp).align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(16.dp))
            }
            Text(
                text = if (tokens.traits.bracketedLabels) "> $title" else title,
                style = tokens.typography.headlineSmall,
                color = c.onSurface,
                textAlign = if (icon != null) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
            if (tokens.traits.headerDecor == HeaderDecor.Masthead) {
                Spacer(Modifier.height(10.dp))
                AppDivider()
            }
            if (text != null) {
                Spacer(Modifier.height(12.dp))
                Text(text, style = tokens.typography.bodyMedium, color = c.onSurfaceVariant)
            }
            if (content != null) {
                Spacer(Modifier.height(16.dp))
                content()
            }
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (dismissButton != null) dismissButton()
                confirmButton()
            }
        }
    }
}

/** "Are you sure?" dialog. [destructive] styles the confirm button as Danger. */
@Composable
fun AppConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String = stringResource(R.string.ds_cancel),
    destructive: Boolean = false,
    icon: ImageVector? = null,
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = title,
        text = message,
        icon = icon,
        modifier = modifier,
        confirmButton = {
            AppButton(
                text = confirmLabel,
                onClick = onConfirm,
                variant = if (destructive) AppButtonVariant.Danger else AppButtonVariant.Primary,
                size = AppButtonSize.Small,
            )
        },
        dismissButton = {
            AppButton(text = dismissLabel, onClick = onDismiss, variant = AppButtonVariant.Text, size = AppButtonSize.Small)
        },
    )
}

/** An informational dialog with a single button. */
@Composable
fun AppAlertDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    buttonLabel: String = stringResource(R.string.ds_ok),
    icon: ImageVector? = null,
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = title,
        text = message,
        icon = icon,
        modifier = modifier,
        confirmButton = { AppButton(text = buttonLabel, onClick = onDismiss, size = AppButtonSize.Small) },
    )
}

/**
 * A themed modal bottom sheet (pickers live here, in the reachable lower half of the screen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = AppTheme.tokens
    val c = tokens.colors
    val border = cardBorder()
    val container = when (tokens.traits.cardFill) {
        CardFill.Frosted -> c.surface.copy(alpha = 0.96f)
        CardFill.Outline -> c.background
        CardFill.Solid -> c.surface
    }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.then(
            if (border != null) Modifier.themedContainer(tokens.shapes.sheet, null, border, ShadowSpec.None) else Modifier,
        ),
        sheetState = sheetState,
        shape = tokens.shapes.sheet,
        containerColor = container,
        contentColor = c.onSurface,
        tonalElevation = 0.dp,
        scrimColor = c.scrim,
        dragHandle = { SheetDragHandle() },
    ) {
        SheetBody(title, Modifier.navigationBarsPadding(), content)
    }
}

/** The themed drag handle of [AppBottomSheet]. */
@Composable
internal fun SheetDragHandle() {
    val tokens = AppTheme.tokens
    val description = stringResource(R.string.ds_drag_handle)
    Box(
        Modifier
            .padding(top = 12.dp, bottom = 8.dp)
            .semantics { contentDescription = description },
    ) {
        Box(
            Modifier
                .size(width = 36.dp, height = 4.dp)
                .themedContainer(tokens.shapes.track, SolidColor(tokens.colors.onSurfaceVariant.copy(alpha = 0.5f)), null, ShadowSpec.None),
        )
    }
}

/** Title + content column of [AppBottomSheet]. */
@Composable
internal fun SheetBody(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val tokens = AppTheme.tokens
    Column(modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        if (title != null) {
            Text(
                text = if (tokens.traits.bracketedLabels) "> $title" else title,
                style = tokens.typography.titleLarge,
                color = tokens.colors.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
        content()
    }
}
