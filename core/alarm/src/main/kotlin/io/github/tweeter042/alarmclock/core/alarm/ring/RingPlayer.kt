package io.github.tweeter042.alarmclock.core.alarm.ring

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.github.tweeter042.alarmclock.core.alarm.EngineLog

/**
 * Plays the alarm sound with Media3 ExoPlayer on the alarm stream (USAGE_ALARM /
 * CONTENT_TYPE_SONIFICATION: plays in silent mode and Do Not Disturb), looping (REPEAT_MODE_ONE).
 *
 * Fallback chain: each URI of the plan in turn (an error, or no playback within
 * [START_TIMEOUT_MILLIS], moves to the next), the last one being the bundled fallback tone; if even
 * that fails, ToneGenerator beeps ([Listener.onBeeper]: the caller maximizes the volume and makes
 * sure the phone vibrates). Requests transient audio focus so music pauses, and keeps ringing if
 * focus is lost. Main thread only.
 */
internal class RingPlayer(private val context: Context, private val listener: Listener) {

    interface Listener {
        /** Audio is audible; [candidate] is the index in the plan (0 = the configured sound). */
        fun onPlaying(candidate: Int)

        /** No file could be played; ToneGenerator beeps now ([working] = false if even that failed). */
        fun onBeeper(working: Boolean)
    }

    private val log = EngineLog.audio
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager: AudioManager? = context.getSystemService(AudioManager::class.java)
    private val beeper = FallbackBeeper()

    private var player: ExoPlayer? = null
    private var candidates: List<Uri> = emptyList()
    private var index = 0
    private var gain = 0f
    private var confirmed = false
    private var released = false
    private var beeping = false
    private var focusRequest: AudioFocusRequest? = null

    fun start(uris: List<Uri>, initialGain: Float) {
        candidates = uris
        index = 0
        gain = initialGain.coerceIn(0f, 1f)
        requestFocus()
        playCurrent()
    }

    fun setGain(value: Float) {
        gain = value.coerceIn(0f, 1f)
        runCatching { player?.volume = gain }
    }

    /** Another app (Spotify) took over the sound: stop our tone but keep the audio focus. */
    fun stopOwnAudio() {
        releasePlayer()
        if (beeping) beeper.stop()
    }

    fun release() {
        released = true
        handler.removeCallbacksAndMessages(null)
        releasePlayer()
        beeper.stop()
        abandonFocus()
    }

    private fun playCurrent() {
        if (released) return
        val uri = candidates.getOrNull(index) ?: return startBeeper()
        confirmed = false
        try {
            val exo = player ?: createPlayer()
            exo.setMediaItem(MediaItem.fromUri(uri))
            exo.volume = gain
            exo.prepare()
            exo.playWhenReady = true
        } catch (t: Throwable) {
            log.w("Could not start sound #$index", t)
            next()
            return
        }
        val attempt = index
        handler.postDelayed({
            if (!released && !confirmed && !beeping && index == attempt) {
                log.w("Sound #$attempt did not start in time; trying the next")
                next()
            }
        }, START_TIMEOUT_MILLIS)
    }

    private fun next() {
        index++
        playCurrent()
    }

    private fun createPlayer(): ExoPlayer = ExoPlayer.Builder(context).build().also { exo ->
        exo.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_ALARM)
                .setContentType(C.AUDIO_CONTENT_TYPE_SONIFICATION)
                .build(),
            /* handleAudioFocus = */ false,
        )
        exo.repeatMode = Player.REPEAT_MODE_ONE
        exo.addListener(
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    log.w("Sound #$index failed (${error.errorCodeName})")
                    if (!released) handler.post { next() }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying && !confirmed) {
                        confirmed = true
                        listener.onPlaying(index)
                    }
                }
            },
        )
        player = exo
    }

    private fun startBeeper() {
        if (beeping || released) return
        releasePlayer()
        beeping = true
        val working = beeper.start()
        log.e(if (working) "All sounds failed; beeping with ToneGenerator" else "All sounds failed, ToneGenerator too")
        listener.onBeeper(working)
    }

    private fun releasePlayer() {
        val exo = player ?: return
        player = null
        runCatching {
            exo.stop()
            exo.release()
        }
    }

    private fun requestFocus() {
        val manager = audioManager ?: return
        try {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setWillPauseWhenDucked(false)
                .setAcceptsDelayedFocusGain(false)
                // The alarm keeps ringing whatever happens to the focus.
                .setOnAudioFocusChangeListener { }
                .build()
            manager.requestAudioFocus(request)
            focusRequest = request
        } catch (t: Throwable) {
            log.w("Audio focus request failed", t)
        }
    }

    private fun abandonFocus() {
        val request = focusRequest ?: return
        focusRequest = null
        runCatching { audioManager?.abandonAudioFocusRequest(request) }
    }

    private companion object {
        const val START_TIMEOUT_MILLIS = 4_000L
    }
}
