package io.github.tweeter042.alarmclock.core.alarm.ring

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import io.github.tweeter042.alarmclock.core.alarm.DefaultAlarmEngine
import io.github.tweeter042.alarmclock.core.alarm.EngineLog
import io.github.tweeter042.alarmclock.core.alarm.internal.WakeLocks
import io.github.tweeter042.alarmclock.core.alarm.internal.guardedOrNull
import io.github.tweeter042.alarmclock.core.model.alarm.ActiveRing
import io.github.tweeter042.alarmclock.core.model.alarm.BriefingTiming
import io.github.tweeter042.alarmclock.core.model.alarm.BuiltInTones
import io.github.tweeter042.alarmclock.core.model.alarm.ButtonAction
import io.github.tweeter042.alarmclock.core.model.alarm.RingKind
import io.github.tweeter042.alarmclock.core.model.alarm.RingState
import io.github.tweeter042.alarmclock.core.model.alarm.VibrationPattern
import io.github.tweeter042.alarmclock.core.model.alarm.WatchRingMode
import io.github.tweeter042.alarmclock.core.model.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * Everything that makes one ring audible, from [start] to [stop]: wake lock, audio with fade-in
 * and ducking, vibration, gestures, the WATCH_ONLY silent window and the DURING_RING briefing.
 * Owned by AlarmRingService; runs on the main thread.
 */
internal class RingSession(
    private val context: Context,
    ring: ActiveRing,
    val mode: SoundMode,
    private val engine: DefaultAlarmEngine,
    private val settings: AppSettings,
    private val host: Host,
) {
    interface Host {
        /** The phone is silent while the watch rings (WATCH_ONLY) — or not any more. */
        fun onWatchSilent(ringId: String, silent: Boolean)

        /** Show the ring screen again (re-post the full-screen notification). */
        fun reannounce(ringId: String)
    }

    var ring: ActiveRing = ring
        private set

    private val log = EngineLog.audio
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val deps = engine.env.deps
    private val vibrator = RingVibrator(context)
    private var player: RingPlayer? = null
    private var gestures: RingGestures? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var targetGain = 1f
    private var fadeMillis = 0L
    private var audibleSince = 0L
    private var missionDuck = 1f
    private var briefingDuck = 1f
    private var vibrating = false
    private var speaking = false
    private var spotifyPlaying = false
    private var testReported = false
    private var stopped = false

    fun start() {
        acquireWakeLock()
        when (mode) {
            SoundMode.AWAKE_PING -> startAwakePing()
            SoundMode.ALARM -> {
                startGestures()
                scope.launch { startAlarm() }
            }
        }
    }

    /** Same ring, new state (RINGING ⇄ MISSION, mission progress). */
    fun update(updated: ActiveRing) {
        ring = updated
        val duck = if (updated.state == RingState.MISSION && updated.alarm.dismiss.lowerVolumeDuringMission) VolumeMath.MISSION_DUCK else 1f
        if (duck != missionDuck) {
            missionDuck = duck
            applyGain()
        }
        if (updated.state == RingState.MISSION && speaking) deps.speaker?.stop()
    }

    fun stop() {
        if (stopped) return
        stopped = true
        scope.cancel()
        player?.release()
        player = null
        vibrator.stop()
        gestures?.stop()
        gestures = null
        if (speaking) runCatching { deps.speaker?.stop() }
        if (spotifyPlaying) runCatching { deps.music?.stopSpotifyPlayback() }
        wakeLock?.let { lock -> runCatching { if (lock.isHeld) lock.release() } }
        wakeLock = null
    }

    // --- alarm ----------------------------------------------------------------------------------

    private suspend fun startAlarm() {
        val silentMillis = watchOnlySilence()
        if (silentMillis > 0) {
            log.i("Watch nearby: phone silent for ${silentMillis / 1000} s (WATCH_ONLY)")
            host.onWatchSilent(ring.ringId, true)
            delay(silentMillis)
            host.onWatchSilent(ring.ringId, false)
        }
        if (stopped) return
        startAudible()
    }

    /**
     * WATCH_ONLY: while a nearby watch rings, the phone stays silent for [WATCH_ONLY_WINDOW_MILLIS]
     * after the ring started; no watch (or no answer from the bridge) → ring at once.
     */
    private suspend fun watchOnlySilence(): Long {
        if (ring.alarm.watch.mode != WatchRingMode.WATCH_ONLY) return 0L
        val wear = deps.wear ?: return 0L
        if (engine.env.clock.now() - ring.startedAt >= WATCH_ONLY_WINDOW_MILLIS) return 0L
        val nodes = withContext(Dispatchers.IO) {
            guardedOrNull("Finding the watch", WATCH_LOOKUP_TIMEOUT_MILLIS, log) { wear.refreshNodes() }
        } ?: return 0L
        if (nodes.none { it.nearby }) return 0L
        return (ring.startedAt + WATCH_ONLY_WINDOW_MILLIS - engine.env.clock.now()).coerceAtLeast(0L)
    }

    private suspend fun startAudible() {
        val sound = ring.alarm.sound
        val vibration = ring.alarm.vibration
        if (!sound.vibrateOnly) {
            applyVolumePolicy(sound.overrideSystemVolume, sound.volumePercent)
            targetGain = VolumeMath.targetGain(sound)
            fadeMillis = sound.fadeInSeconds.coerceIn(0, MAX_FADE_SECONDS) * 1_000L
            val plan = withContext(Dispatchers.IO) {
                withTimeoutOrNull(RESOLVE_TIMEOUT_MILLIS) { SoundSourceResolver(context, engine.ringPrefs).plan(sound.source, ring.sourceId) }
            } ?: AudioPlan(listOf(ToneResources.fallbackUri(context)))
            if (stopped) return
            audibleSince = SystemClock.elapsedRealtime()
            player = RingPlayer(context, playerListener).also { it.start(plan.uris, currentGain()) }
            if (fadeMillis > 0) launchFade()
            plan.spotifyUri?.let(::launchSpotify)
            launchBriefing()
        } else if (ring.kind == RingKind.TEST) {
            reportTest(true)
        }
        // Vibrate-only with vibration switched off would be a silent alarm: vibrate anyway.
        if (vibration.enabled || sound.vibrateOnly) {
            vibrator.start(vibration.pattern)
            vibrating = true
        }
    }

    /**
     * overrideSystemVolume: alarm stream to the alarm's level. Otherwise the user's own alarm volume
     * applies — undo an override left by a previous ring of this service — but never muted.
     */
    private fun applyVolumePolicy(override: Boolean, percent: Int) {
        if (override) {
            engine.volume.applyPercent(percent)
        } else {
            engine.volume.restore()
            engine.volume.ensureAudible()
        }
    }

    private val playerListener = object : RingPlayer.Listener {
        override fun onPlaying(candidate: Int) {
            if (candidate > 0) log.w("Configured sound unavailable; playing fallback #$candidate")
            reportTest(true)
        }

        override fun onBeeper(working: Boolean) {
            engine.volume.maximize()
            if (!vibrating) {
                vibrator.start(VibrationPattern.BASIC)
                vibrating = true
            }
            reportTest(false)
        }
    }

    private fun reportTest(ok: Boolean) {
        if (ring.kind != RingKind.TEST || testReported) return
        testReported = true
        engine.scope.launch { engine.recordTestResult(ok) }
    }

    private fun launchFade() {
        scope.launch {
            while (isActive && SystemClock.elapsedRealtime() - audibleSince < fadeMillis) {
                applyGain()
                delay(FADE_TICK_MILLIS)
            }
            applyGain()
        }
    }

    private fun currentGain(): Float {
        val elapsed = if (audibleSince == 0L) 0L else SystemClock.elapsedRealtime() - audibleSince
        return VolumeMath.playerGain(targetGain, elapsed, fadeMillis, missionDuck * briefingDuck)
    }

    private fun applyGain() {
        player?.setGain(currentGain())
    }

    /** Experimental Spotify: the fallback tone keeps playing unless Spotify confirms playback. */
    private fun launchSpotify(uri: String) {
        val music = deps.music ?: return
        scope.launch {
            val confirmed = withContext(Dispatchers.IO) {
                guardedOrNull("Spotify playback", SPOTIFY_TIMEOUT_MILLIS, log) { music.startSpotifyPlayback(uri) }
            } == true
            if (confirmed && !stopped) {
                spotifyPlaying = true
                player?.stopOwnAudio()
                log.i("Spotify confirmed playback; fallback tone stopped")
                reportTest(true)
            }
        }
    }

    /** DURING_RING briefing: after ringing for a while, duck the alarm and speak (bounded). */
    private fun launchBriefing() {
        val config = ring.alarm.briefing
        if (!config.enabled || config.timing != BriefingTiming.DURING_RING || ring.snoozeCount > 0) return
        val speaker = deps.speaker ?: return
        val composer = deps.briefing ?: return
        scope.launch {
            delay(BRIEFING_DELAY_MILLIS)
            if (ring.state != RingState.RINGING) return@launch
            briefingDuck = VolumeMath.BRIEFING_DUCK
            applyGain()
            speaking = true
            try {
                withTimeoutOrNull(BRIEFING_TIMEOUT_MILLIS) {
                    val language = settings.region.language.tag
                    val text = withContext(Dispatchers.IO) { composer.compose(ring.alarm, engine.env.clock.now(), language).spokenText }
                    if (text.isNotBlank()) speaker.speak(text, language, config.speechRatePercent, useAlarmStream = true)
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                log.w("Briefing failed", t)
            } finally {
                speaking = false
                briefingDuck = 1f
                applyGain()
            }
        }
    }

    // --- awake check ----------------------------------------------------------------------------

    private fun startAwakePing() {
        applyVolumePolicy(override = false, percent = 0)
        targetGain = VolumeMath.AWAKE_PING_GAIN
        fadeMillis = 0L
        audibleSince = SystemClock.elapsedRealtime()
        val uris = listOfNotNull(ToneResources.uri(context, BuiltInTones.AWAKE_CHECK_ID), ToneResources.fallbackUri(context))
        player = RingPlayer(context, playerListener).also { it.start(uris, currentGain()) }
        if (ring.alarm.vibration.enabled) {
            vibrator.start(VibrationPattern.TICK_TOCK, amplitudeScale = 0.5f)
            vibrating = true
        }
    }

    // --- gestures / wake lock -------------------------------------------------------------------

    private fun startGestures() {
        gestures = RingGestures(context, ring, settings) { action -> onGesture(action) }.also { it.start() }
    }

    private fun onGesture(action: ButtonAction) {
        val ringId = ring.ringId
        engine.scope.launch {
            val result = engine.onGesture(ringId, action)
            if (result == GestureResult.OPEN_RING_SCREEN) withContext(Dispatchers.Main) { host.reannounce(ringId) }
        }
    }

    private fun acquireWakeLock() {
        val minutes = when (mode) {
            SoundMode.ALARM -> ring.alarm.dismiss.ringDurationMinutes.coerceAtLeast(1) + WAKE_LOCK_EXTRA_MINUTES
            SoundMode.AWAKE_PING -> WAKE_LOCK_EXTRA_MINUTES + ring.alarm.awakeCheck.responseSeconds / 60 + 1
        }
        wakeLock = WakeLocks.newRingLock(context)?.also { lock ->
            runCatching { lock.acquire(minutes * 60_000L) }
        }
    }

    companion object {
        const val WATCH_ONLY_WINDOW_MILLIS = 60_000L
        private const val WATCH_LOOKUP_TIMEOUT_MILLIS = 3_000L
        private const val RESOLVE_TIMEOUT_MILLIS = 2_000L
        private const val SPOTIFY_TIMEOUT_MILLIS = 15_000L
        private const val BRIEFING_DELAY_MILLIS = 10_000L
        private const val BRIEFING_TIMEOUT_MILLIS = 60_000L
        private const val FADE_TICK_MILLIS = 200L
        private const val MAX_FADE_SECONDS = 600
        /** Wake lock timeout = ring duration + 2 min. */
        private const val WAKE_LOCK_EXTRA_MINUTES = 2
    }
}
