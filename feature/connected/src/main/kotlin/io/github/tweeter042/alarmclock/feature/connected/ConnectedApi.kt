package io.github.tweeter042.alarmclock.feature.connected

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.tweeter042.alarmclock.core.model.alarm.OpenAppTarget
import io.github.tweeter042.alarmclock.core.model.service.SmartScene

/*
 * PUBLIC API of :feature:connected used by other modules. Signatures are FROZEN.
 */

/** Pick a SmartThings scene (or none). Shows a "Set up SmartThings" link if not configured. */
@Composable
fun SmartThingsScenePicker(
    selectedSceneId: String?,
    onSelected: (SmartScene?) -> Unit,
    onOpenSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text("SmartThingsScenePicker", modifier)
}

/**
 * Pick SmartThings devices that have [requiredCapability] (e.g. "switchLevel" for sunrise lights).
 */
@Composable
fun SmartThingsDevicePicker(
    selectedDeviceIds: List<String>,
    requiredCapability: String,
    onChange: (List<String>) -> Unit,
    onOpenSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text("SmartThingsDevicePicker", modifier)
}

/** Pick an installed music app (+ optional playlist link) to open after dismissing. */
@Composable
fun MusicAppPicker(
    selected: OpenAppTarget?,
    onSelected: (OpenAppTarget?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Text("MusicAppPicker", modifier)
}
