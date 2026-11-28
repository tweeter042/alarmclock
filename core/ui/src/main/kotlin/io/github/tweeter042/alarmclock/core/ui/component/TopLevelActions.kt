package io.github.tweeter042.alarmclock.core.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import io.github.tweeter042.alarmclock.core.designsystem.component.AppIconButton
import io.github.tweeter042.alarmclock.core.designsystem.icon.AppIcons
import io.github.tweeter042.alarmclock.core.ui.R
import io.github.tweeter042.alarmclock.core.ui.navigation.navigateToSettings

/**
 * The actions of every top-level screen's top bar (alarms, world clock, timer, stopwatch, sleep):
 * the screen's own [extraActions] followed by the Settings gear, which opens `SettingsRoute`.
 *
 * ```
 * AppScaffold(title = …, actions = { TopLevelActions(navController) }) { … }
 * ```
 */
@Composable
fun TopLevelActions(
    navController: NavController,
    modifier: Modifier = Modifier,
    extraActions: @Composable RowScope.() -> Unit = {},
) {
    TopLevelActions(onOpenSettings = { navController.navigateToSettings() }, modifier = modifier, extraActions = extraActions)
}

/** [TopLevelActions] with an explicit Settings callback (tests, previews). */
@Composable
fun TopLevelActions(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    extraActions: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        extraActions()
        AppIconButton(
            icon = AppIcons.Settings,
            contentDescription = stringResource(R.string.ui_settings),
            onClick = onOpenSettings,
        )
    }
}
