package io.github.tweeter042.alarmclock.core.alarm.ring

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import io.github.tweeter042.alarmclock.core.alarm.EngineLog
import io.github.tweeter042.alarmclock.core.alarm.internal.EnginePrefs
import io.github.tweeter042.alarmclock.core.model.alarm.BuiltInTones
import io.github.tweeter042.alarmclock.core.model.alarm.PlaylistItem
import io.github.tweeter042.alarmclock.core.model.alarm.SoundSource

/** What the player should try, in order; the bundled fallback tone always comes last. */
internal data class AudioPlan(
    /** Playable URIs, best first. Never empty: ends with the fallback tone. */
    val uris: List<Uri>,
    /** Spotify URI to hand to the Spotify app while the fallback tone plays (experimental). */
    val spotifyUri: String? = null,
)

/**
 * Turns a [SoundSource] into an [AudioPlan] (pure decision logic in [order]; file checks here).
 *
 * - BuiltIn → its bundled tone. SystemRingtone / LocalFile → the URI if it can be opened (missing
 *   file, revoked SAF permission or Direct Boot fall back to the bundled tone).
 * - Playlist → one item per ring, shuffled or in order (position remembered per alarm), unreadable
 *   items skipped.
 * - Spotify → the fallback tone; the Spotify app is asked to play on top (see RingSession).
 */
internal class SoundSourceResolver(private val context: Context, private val prefs: EnginePrefs) {
    private val log = EngineLog.audio

    fun plan(source: SoundSource, sourceId: Long): AudioPlan {
        val fallback = ToneResources.fallbackUri(context)
        val ordered: List<Uri> = when (source) {
            is SoundSource.BuiltIn -> listOfNotNull(ToneResources.uri(context, source.toneId))
            is SoundSource.SystemRingtone -> listOfNotNull(parse(source.uri)?.takeIf(::isReadable))
            is SoundSource.LocalFile -> listOfNotNull(parse(source.uri)?.takeIf(::isReadable))
            is SoundSource.Playlist -> playlist(source, sourceId)
            is SoundSource.Spotify -> emptyList()
        }
        val spotify = (source as? SoundSource.Spotify)?.uri?.takeIf { it.isNotBlank() }
        return AudioPlan((ordered + fallback).distinct(), spotify)
    }

    private fun playlist(source: SoundSource.Playlist, sourceId: Long): List<Uri> {
        if (source.items.isEmpty()) return emptyList()
        val start = prefs.playlistPosition(sourceId)
        val order = order(source.items, source.shuffle, start, kotlin.random.Random.Default)
        val readable = order.mapNotNull { index -> parse(source.items[index].uri)?.takeIf(::isReadable)?.let { index to it } }
        readable.firstOrNull()?.let { (index, _) -> if (!source.shuffle) prefs.setPlaylistPosition(sourceId, index + 1) }
        return readable.map { it.second }.take(MAX_CANDIDATES)
    }

    private fun parse(uri: String): Uri? = runCatching { uri.toUri() }.getOrNull()?.takeIf { it.scheme != null }

    /** True if the content can be opened right now (permission, file present, provider available). */
    private fun isReadable(uri: Uri): Boolean = try {
        when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT, ContentResolver.SCHEME_ANDROID_RESOURCE, ContentResolver.SCHEME_FILE ->
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
            else -> false
        }
    } catch (t: Throwable) {
        log.w("Sound not readable; using the next one", t)
        false
    }

    companion object {
        private const val MAX_CANDIDATES = 4

        /**
         * Indices of [items] in the order to try: shuffled, or starting at [start] (wrapping), so the
         * next ring plays the next item.
         */
        fun order(items: List<PlaylistItem>, shuffle: Boolean, start: Int, random: kotlin.random.Random): List<Int> {
            if (items.isEmpty()) return emptyList()
            return if (shuffle) {
                items.indices.shuffled(random)
            } else {
                val first = Math.floorMod(start, items.size)
                items.indices.map { (first + it) % items.size }
            }
        }

        /** True if [toneId] names a bundled tone (alarm or system tone). */
        fun isBundled(toneId: String): Boolean = BuiltInTones.byId(toneId) != null || toneId in BuiltInTones.SYSTEM_TONES
    }
}
