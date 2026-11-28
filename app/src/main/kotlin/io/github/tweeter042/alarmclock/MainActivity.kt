package io.github.tweeter042.alarmclock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.tweeter042.alarmclock.core.ui.AlarmClockAppRoot
import io.github.tweeter042.alarmclock.core.ui.appGraph
import io.github.tweeter042.alarmclock.core.ui.locale.AppLocales
import io.github.tweeter042.alarmclock.core.ui.navigation.MainIntents
import io.github.tweeter042.alarmclock.shell.AppShell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The single activity of the phone UI.
 *
 * - The splash screen stays until the user's settings are loaded and the first frame is composed
 *   with the right theme and start destination (onboarding or alarms), so nothing flickers.
 * - Opens the screen requested through [MainIntents] extras, in `onCreate` and `onNewIntent`.
 * - Keeps the app language in sync (English by default on first run, mirrored into the settings).
 */
class MainActivity : ComponentActivity() {

    private val shell: MainShellViewModel by viewModels()

    /** Read by the splash screen on every pre-draw; set once the UI has been composed. */
    @Volatile
    private var contentReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        splash.setKeepOnScreenCondition { !contentReady }
        if (savedInstanceState == null) offer(intent)

        val graph = appGraph()
        lifecycleScope.launch { AppLocales.syncWithSettings(this@MainActivity, graph.settings) }

        setContent {
            AlarmClockAppRoot(graph) {
                SideEffect { contentReady = true }
                val pending by shell.pendingDestination.collectAsStateWithLifecycle()
                AppShell(pendingDestination = pending, onPendingHandled = shell::handled)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        offer(intent)
    }

    private fun offer(intent: Intent?) {
        val destination = MainIntents.destinationOf(intent) ?: return
        shell.open(destination)
        intent?.let(MainIntents::consume)
    }
}

/** Survives configuration changes: a requested screen is opened exactly once. */
internal class MainShellViewModel : ViewModel() {
    private val pending = MutableStateFlow<Any?>(null)

    /** A navigation route requested through [MainIntents], until the shell has opened it. */
    val pendingDestination: StateFlow<Any?> = pending.asStateFlow()

    fun open(destination: Any) {
        pending.value = destination
    }

    fun handled(destination: Any) {
        pending.compareAndSet(destination, null)
    }
}
