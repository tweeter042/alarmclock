package io.github.tweeter042.alarmclock.core.ui.time

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.ZoneId

/** The clock the UI reads. Tests and screenshots provide a fixed one through [LocalUiClock]. */
@Stable
interface UiClock {
    /** Epoch milliseconds. */
    fun now(): Long

    fun zone(): ZoneId = ZoneId.systemDefault()

    /** The device clock. */
    object System : UiClock {
        override fun now(): Long = java.lang.System.currentTimeMillis()
    }
}

/** A clock frozen at [epochMillis] (previews, screenshot tests). */
class FixedUiClock(private val epochMillis: Long, private val zone: ZoneId = ZoneId.systemDefault()) : UiClock {
    override fun now(): Long = epochMillis
    override fun zone(): ZoneId = zone
}

val LocalUiClock = staticCompositionLocalOf<UiClock> { UiClock.System }

/**
 * Counts "the user changed the clock or the time zone" events. Provided once per activity by
 * [ProvideTimeChanges] (part of `AlarmClockAppRoot`) so every [rememberNow] shares one receiver.
 */
private val LocalTimeChanges = staticCompositionLocalOf<State<Int>?> { null }

/** Registers one time/time-zone change receiver for [content] (done by `AlarmClockAppRoot`). */
@Composable
fun ProvideTimeChanges(content: @Composable () -> Unit) {
    val changes = rememberTimeChangeCounter()
    CompositionLocalProvider(LocalTimeChanges provides changes, content = content)
}

/**
 * The current time (epoch millis), updated every [intervalMillis] aligned to the wall clock
 * (every full second, every full minute, …) while the screen is at least STARTED, and at once
 * when the user changes the time or time zone.
 *
 * ```
 * val now = rememberNow(60_000)   // for "rings in 7 h 24 min"
 * ```
 */
@Composable
fun rememberNow(intervalMillis: Long = 1_000L): Long {
    require(intervalMillis > 0) { "intervalMillis must be positive" }
    val clock = LocalUiClock.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var now by remember(clock) { mutableLongStateOf(clock.now()) }
    val timeChanges = timeChangeCount()
    LaunchedEffect(clock, intervalMillis, lifecycleOwner, timeChanges) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val current = clock.now()
                now = current
                // A frozen clock never advances: no need to keep waking up.
                if (clock is FixedUiClock) break
                delay(intervalMillis - Math.floorMod(current, intervalMillis))
            }
        }
    }
    return now
}

/** The system time zone, updated when the user changes it. */
@Composable
fun rememberZone(): ZoneId {
    val clock = LocalUiClock.current
    val changes = timeChangeCount()
    return remember(clock, changes) { clock.zone() }
}

@Composable
private fun timeChangeCount(): Int {
    val shared = LocalTimeChanges.current
    return if (shared != null) shared.value else rememberTimeChangeCounter().value
}

@Composable
private fun rememberTimeChangeCounter(): State<Int> {
    val context = LocalContext.current
    val count: MutableIntState = remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                count.intValue++
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        // System broadcasts reach non-exported receivers.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return count
}
