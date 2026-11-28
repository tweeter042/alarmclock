package io.github.tweeter042.alarmclock.whatsnew

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.tweeter042.alarmclock.R
import io.github.tweeter042.alarmclock.core.designsystem.component.AppBottomSheet
import io.github.tweeter042.alarmclock.core.designsystem.component.AppButton
import io.github.tweeter042.alarmclock.core.designsystem.component.AppButtonVariant
import io.github.tweeter042.alarmclock.core.designsystem.component.LeadingIcon
import io.github.tweeter042.alarmclock.core.designsystem.icon.AppIcons
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import io.github.tweeter042.alarmclock.core.model.service.ChangelogEntry
import io.github.tweeter042.alarmclock.core.ui.LocalAppGraph
import io.github.tweeter042.alarmclock.core.ui.LocalAppSettings
import io.github.tweeter042.alarmclock.core.ui.format.rememberUiFormatters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val TAG = "AlarmClock/WhatsNew"

/**
 * Shows the "What's new" sheet once after an update (see [whatsNewAction]) and records the
 * version as seen when it closes. On the first install the version is recorded silently once
 * onboarding is done.
 */
@Composable
internal fun WhatsNewHost(onSeeAllChanges: () -> Unit) {
    val graph = LocalAppGraph.current
    val settings = LocalAppSettings.current
    val versionCode = graph.buildInfo.versionCode
    val action = whatsNewAction(settings, versionCode)
    var entry by remember { mutableStateOf<ChangelogEntry?>(null) }
    val markSeen: () -> Unit = {
        graph.appScope.launch {
            try {
                graph.settings.update { it.copy(lastSeenVersionCode = maxOf(it.lastSeenVersionCode, versionCode)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not store the seen version (${e.javaClass.simpleName})")
            }
        }
    }
    LaunchedEffect(action) {
        when (action) {
            WhatsNewAction.Show -> {
                val loaded = runCatching { graph.changelog.entryFor(versionCode) }.getOrNull()
                if (loaded != null && loaded.whatsNewLines().isNotEmpty()) entry = loaded else markSeen()
            }
            WhatsNewAction.MarkSeen -> markSeen()
            WhatsNewAction.None -> Unit
        }
    }
    val shown = entry ?: return
    WhatsNewSheet(
        entry = shown,
        aiModelName = graph.buildInfo.aiModelName,
        onDismiss = {
            entry = null
            markSeen()
        },
        onSeeAllChanges = {
            entry = null
            markSeen()
            onSeeAllChanges()
        },
    )
}

/**
 * The sheet itself: version, release date, up to [MAX_WHATS_NEW_LINES] highlights in the app's
 * language, the AI-development note, "See all changes" and "Got it". Exactly one of the callbacks
 * runs, after the sheet has closed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WhatsNewSheet(
    entry: ChangelogEntry,
    aiModelName: String,
    onDismiss: () -> Unit,
    onSeeAllChanges: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(then: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { then() }
    }
    AppBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        WhatsNewContent(
            entry = entry,
            aiModelName = aiModelName,
            onGotIt = { close(onDismiss) },
            onSeeAllChanges = { close(onSeeAllChanges) },
        )
    }
}

@Composable
internal fun WhatsNewContent(
    entry: ChangelogEntry,
    aiModelName: String,
    onGotIt: () -> Unit,
    onSeeAllChanges: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatters = rememberUiFormatters()
    val language = formatters.strings.language.tag
    val spacing = AppTheme.spacing
    val released = remember(entry.date, formatters) {
        runCatching { LocalDate.parse(entry.date) }.getOrNull()?.let { formatters.time.dayMonth(it) }
    }
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.xl),
    ) {
        Text(
            text = stringResource(R.string.app_whats_new_title, entry.versionName),
            style = AppTheme.typography.headlineSmall,
            color = AppTheme.colors.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        if (released != null) {
            Text(
                text = stringResource(R.string.app_whats_new_released, released),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(spacing.lg))
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            entry.whatsNewLines().forEach { line ->
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.semantics(mergeDescendants = true) { }) {
                    LeadingIcon(AppIcons.Sparkle)
                    Spacer(Modifier.width(spacing.lg))
                    Text(
                        text = line.get(language),
                        style = AppTheme.typography.bodyLarge,
                        color = AppTheme.colors.onSurface,
                        // Centres the first line on the 40 dp icon.
                        modifier = Modifier.weight(1f).padding(top = spacing.sm),
                    )
                }
            }
        }
        Spacer(Modifier.height(spacing.xl))
        Text(
            text = stringResource(R.string.app_ai_disclaimer, aiModelName.ifBlank { stringResource(R.string.app_ai_model_not_specified) }),
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.xl))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            AppButton(
                text = stringResource(R.string.app_whats_new_see_all),
                onClick = onSeeAllChanges,
                variant = AppButtonVariant.Secondary,
                modifier = Modifier.weight(1f),
            )
            AppButton(
                text = stringResource(R.string.app_whats_new_got_it),
                onClick = onGotIt,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
