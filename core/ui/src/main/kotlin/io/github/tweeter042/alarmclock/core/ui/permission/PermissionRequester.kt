package io.github.tweeter042.alarmclock.core.ui.permission

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.compose.LifecycleResumeEffect

/** Where a set of runtime permissions stands. */
enum class PermissionStatus {
    /** Every permission is granted. */
    Granted,

    /** Not granted yet; asking shows the system dialog. */
    Denied,

    /** Denied once: explain why before asking again (the system dialog will still show). */
    RationaleNeeded,

    /** Denied for good ("Don't allow" twice): only the app's system settings can grant it. */
    PermanentlyDenied,
}

/** One permission's state, as read from the system. */
@Immutable
data class PermissionState(
    val permission: String,
    val granted: Boolean,
    val shouldShowRationale: Boolean,
    val requestedBefore: Boolean,
)

/** Pure status rules (unit tested). */
object PermissionRules {
    /**
     * Android reports a permission that was never requested and one that is denied for good the
     * same way (not granted, no rationale). The difference is whether the app asked before.
     */
    fun status(states: List<PermissionState>): PermissionStatus {
        val denied = states.filterNot { it.granted }
        return when {
            denied.isEmpty() -> PermissionStatus.Granted
            denied.any { it.shouldShowRationale } -> PermissionStatus.RationaleNeeded
            denied.all { it.requestedBefore } -> PermissionStatus.PermanentlyDenied
            else -> PermissionStatus.Denied
        }
    }
}

/**
 * Requests runtime [permissions] the Android way: explain first when Android says so
 * (rationale), then the system dialog, and when the permission is denied for good, offer the app
 * settings instead. Create it with [rememberPermissionRequester] and place [PermissionPrompts]
 * (or use [PermissionGate], which does both).
 */
@Stable
class PermissionRequester internal constructor(
    val permissions: List<String>,
    private val context: Context,
    private val store: PermissionRequestStore,
    private val settingsIntent: (Context) -> Intent,
) {
    internal var activity: Activity? = null
    internal var launcher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>? = null
    internal var onResult: (Boolean) -> Unit = {}

    /** Set while [PermissionPrompts] is in the composition; without it no dialogs are shown. */
    internal var promptsAttached = false

    var status: PermissionStatus by mutableStateOf(PermissionStatus.Denied)
        private set

    val isGranted: Boolean get() = status == PermissionStatus.Granted

    /** The dialog [PermissionPrompts] should show. */
    internal var prompt: PermissionPrompt? by mutableStateOf(null)

    init {
        refresh()
    }

    /**
     * Asks for the permissions: reports success at once if granted, shows the rationale or the
     * "allow it in Settings" dialog when needed (with [PermissionPrompts] in place), otherwise the
     * system dialog. The result arrives in the `onResult` callback of [rememberPermissionRequester].
     */
    fun request() {
        refresh()
        when (status) {
            PermissionStatus.Granted -> onResult(true)
            PermissionStatus.RationaleNeeded -> if (promptsAttached) prompt = PermissionPrompt.Rationale else launch()
            PermissionStatus.PermanentlyDenied -> if (promptsAttached) prompt = PermissionPrompt.Settings else openSettings()
            PermissionStatus.Denied -> launch()
        }
    }

    /** Shows the system permission dialog right away. */
    fun launch() {
        prompt = null
        val launcher = launcher ?: return
        store.markRequested(permissions)
        try {
            launcher.launch(permissions.toTypedArray())
        } catch (e: ActivityNotFoundException) {
            onResult(false)
        }
    }

    /** Opens the app's system settings page (where denied permissions can be allowed). */
    fun openSettings() {
        prompt = null
        val primary = settingsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(primary)
        } catch (e: ActivityNotFoundException) {
            runCatching { context.startActivity(appDetailsSettingsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    /** Re-reads the permission state (done automatically whenever the screen resumes). */
    fun refresh() {
        status = PermissionRules.status(
            permissions.map { permission ->
                val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                PermissionState(
                    permission = permission,
                    granted = granted,
                    shouldShowRationale = !granted && activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, permission) } == true,
                    requestedBefore = store.wasRequested(permission),
                )
            },
        )
    }

    internal fun dismissPrompt() {
        prompt = null
    }

    internal fun onSystemResult() {
        refresh()
        onResult(isGranted)
    }
}

internal enum class PermissionPrompt { Rationale, Settings }

/**
 * Remembers a [PermissionRequester] for [permissions].
 *
 * @param settingsIntent the settings page for permanently denied permissions (default: the
 *   app's details page; notifications use the app notification settings).
 * @param onResult called after each request with true if everything is granted.
 */
@Composable
fun rememberPermissionRequester(
    permissions: List<String>,
    settingsIntent: (Context) -> Intent = ::appDetailsSettingsIntent,
    onResult: (granted: Boolean) -> Unit = {},
): PermissionRequester {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val currentOnResult by rememberUpdatedState(onResult)
    val requester = remember(permissions, context) {
        PermissionRequester(permissions, context, PermissionRequestStore(context), settingsIntent)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        requester.onSystemResult()
    }
    requester.activity = activity
    requester.launcher = launcher
    requester.onResult = { currentOnResult(it) }
    LifecycleResumeEffect(requester) {
        requester.refresh()
        onPauseOrDispose { }
    }
    return requester
}

/** Settings › Apps › Alarm Clock. */
fun appDetailsSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

/** Settings › Apps › Alarm Clock › Notifications. */
fun appNotificationSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

/**
 * Remembers which permissions the app has requested before, to tell "never asked" from "denied
 * for good". Kept in device-protected storage so it is readable at any time.
 */
internal class PermissionRequestStore(context: Context) {
    private val prefs = runCatching {
        context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }.getOrNull()

    fun wasRequested(permission: String): Boolean = prefs?.getStringSet(KEY, emptySet())?.contains(permission) == true

    fun markRequested(permissions: List<String>) {
        val prefs = prefs ?: return
        val updated = prefs.getStringSet(KEY, emptySet()).orEmpty() + permissions
        prefs.edit { putStringSet(KEY, updated) }
    }

    private companion object {
        const val PREFS = "ui_permission_requests"
        const val KEY = "requested"
    }
}
