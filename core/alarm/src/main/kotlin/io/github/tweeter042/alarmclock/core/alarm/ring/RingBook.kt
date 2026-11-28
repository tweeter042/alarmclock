package io.github.tweeter042.alarmclock.core.alarm.ring

import io.github.tweeter042.alarmclock.core.alarm.schedule.OccurrenceKey
import io.github.tweeter042.alarmclock.core.model.alarm.ActiveRing
import io.github.tweeter042.alarmclock.core.model.alarm.RingState

/**
 * Every ring the engine holds, persisted through RingStateStore (active slot = [current],
 * queue = [others]).
 *
 * - A ring is *ready* when it wants the speaker: RINGING, MISSION, or an AWAKE_CHECK whose prompt is
 *   due. It is *parked* when it waits for a trigger: SNOOZED, or AWAKE_CHECK before its prompt.
 * - [current] is the ring that is sounding (the first ready ring, FIFO); when nothing is ready it is
 *   the parked ring that is due first, so the UI can show "Snoozed until 06:40".
 * - [others]: waiting ready rings in arrival order, then parked rings by due time.
 *   Rings are queued, never dropped.
 * - [pendingSnooze]: rings whose user asked to snooze but must finish the missions first
 *   (SnoozeMode.MISSION_TO_SNOOZE); completing the last mission snoozes instead of dismissing.
 */
internal data class RingBook(
    val current: ActiveRing? = null,
    val others: List<ActiveRing> = emptyList(),
    val pendingSnooze: Set<String> = emptySet(),
) {
    val all: List<ActiveRing> get() = listOfNotNull(current) + others

    /** The ring that is playing right now, if any. */
    val sounding: ActiveRing? get() = current?.takeIf { it.isReady }

    fun find(ringId: String): ActiveRing? = all.firstOrNull { it.ringId == ringId }

    fun contains(key: OccurrenceKey): Boolean = all.any { it.occurrenceKey() == key }

    fun without(ringId: String): RingBook = copy(
        current = current?.takeIf { it.ringId != ringId },
        others = others.filter { it.ringId != ringId },
        pendingSnooze = pendingSnooze - ringId,
    )

    /** Replaces the ring with the same id wherever it is. */
    fun replace(ring: ActiveRing): RingBook = copy(
        current = if (current?.ringId == ring.ringId) ring else current,
        others = others.map { if (it.ringId == ring.ringId) ring else it },
    )

    /** Moves [ring] to the end of the queue (it became ready after the rings already waiting). */
    fun requeue(ring: ActiveRing): RingBook = without(ring.ringId).let {
        it.copy(others = it.others + ring, pendingSnooze = pendingSnooze)
    }

    /** Puts [ring] in front. Only valid when nothing else is sounding. */
    fun makeCurrent(ring: ActiveRing): RingBook {
        val rest = without(ring.ringId)
        return RingBook(current = ring, others = listOfNotNull(rest.current) + rest.others, pendingSnooze = pendingSnooze)
    }

    companion object {
        val EMPTY = RingBook()

        /**
         * Canonical form (idempotent): duplicates by ringId removed (first wins), a ready ring is
         * current whenever one exists, waiting rings keep FIFO order, parked rings sorted by due time.
         */
        fun normalize(book: RingBook): RingBook {
            val all = book.all.distinctBy { it.ringId }
            val ready = all.filter { it.isReady }
            val parked = all.filterNot { it.isReady }.sortedWith(compareBy({ it.dueAt ?: Long.MAX_VALUE }, { it.ringId }))
            val current = when {
                book.current?.isReady == true -> book.current
                ready.isNotEmpty() -> ready.first()
                else -> parked.firstOrNull()
            }
            val others = (ready + parked).filter { it.ringId != current?.ringId }
            val ids = all.mapTo(HashSet()) { it.ringId }
            return RingBook(current, others, book.pendingSnooze.filterTo(LinkedHashSet()) { it in ids })
        }
    }
}

internal val ActiveRing.isReady: Boolean
    get() = state == RingState.RINGING || state == RingState.MISSION ||
        (state == RingState.AWAKE_CHECK && awakePromptShowing)

/** When a parked ring's trigger is due (null for ready rings). */
internal val ActiveRing.dueAt: Long?
    get() = when {
        state == RingState.SNOOZED -> snoozedUntil
        state == RingState.AWAKE_CHECK && !awakePromptShowing -> awakeCheckDueAt
        else -> null
    }

internal fun ActiveRing.occurrenceKey(): OccurrenceKey = OccurrenceKey(kind, sourceId, occurrenceAt)

/** What the speaker plays for the current ring. */
internal enum class SoundMode { ALARM, AWAKE_PING }

internal val ActiveRing.soundMode: SoundMode?
    get() = when {
        state == RingState.RINGING || state == RingState.MISSION -> SoundMode.ALARM
        state == RingState.AWAKE_CHECK && awakePromptShowing -> SoundMode.AWAKE_PING
        else -> null
    }
