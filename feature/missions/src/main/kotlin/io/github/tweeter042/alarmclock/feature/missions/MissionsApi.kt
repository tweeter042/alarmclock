package io.github.tweeter042.alarmclock.feature.missions

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.tweeter042.alarmclock.core.model.alarm.MissionConfig
import io.github.tweeter042.alarmclock.core.model.alarm.MissionDifficulty

/*
 * PUBLIC API of :feature:missions used by other modules. Signatures are FROZEN
 * (other agents code against them in parallel). Implementations may change freely.
 */

/**
 * Editor for an alarm's mission list (add / remove / reorder / configure / "try it").
 * Used inside the alarm editor (:feature:alarms).
 * @param onManageAssets open the screen to register QR codes / photo objects (MissionAssetsRoute).
 * @param onTryMission open MissionPreviewRoute for one mission.
 */
@Composable
fun MissionsEditor(
    missions: List<MissionConfig>,
    onChange: (List<MissionConfig>) -> Unit,
    onManageAssets: () -> Unit,
    onTryMission: (MissionConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    Text("MissionsEditor (${missions.size})", modifier)
}

/**
 * Runs ONE mission full-screen-ish inside the ring screen (:feature:ringing) or the preview.
 * @param difficulty effective difficulty (may be raised by snoozes).
 * @param onInteraction call on every user interaction / sensor progress (resets the engine idle timer).
 * @param onSolved call exactly once when the mission is completed.
 */
@Composable
fun MissionRunner(
    mission: MissionConfig,
    difficulty: MissionDifficulty,
    onInteraction: () -> Unit,
    onSolved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text("MissionRunner ${mission.type} $difficulty", modifier)
}

/** Runtime permissions a mission needs (e.g. CAMERA for BARCODE/PHOTO, ACTIVITY_RECOGNITION for STEPS). */
fun requiredPermissions(mission: MissionConfig): List<String> = emptyList()
