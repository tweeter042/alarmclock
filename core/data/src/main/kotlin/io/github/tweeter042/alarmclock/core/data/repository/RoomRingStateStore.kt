package io.github.tweeter042.alarmclock.core.data.repository

import androidx.room.withTransaction
import io.github.tweeter042.alarmclock.core.data.DataLog
import io.github.tweeter042.alarmclock.core.data.db.AlarmClockDatabase
import io.github.tweeter042.alarmclock.core.data.db.Columns
import io.github.tweeter042.alarmclock.core.data.db.RingStateEntity
import io.github.tweeter042.alarmclock.core.data.db.toEntity
import io.github.tweeter042.alarmclock.core.data.db.toModel
import io.github.tweeter042.alarmclock.core.model.alarm.ActiveRing
import io.github.tweeter042.alarmclock.core.model.repository.RingStateStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The active ring and the FIFO queue of rings waiting behind it, persisted in the `ring_state`
 * table (device-protected storage) so both survive process death and reboots.
 *
 * - The queue is strictly first-in first-out (AUTOINCREMENT row ids, never reused).
 * - [enqueue] is idempotent per ringId: re-enqueueing a ring that is already waiting updates it in
 *   place and keeps its position, so a retried trigger can never make the same ring ring twice.
 * - [dequeue] atomically removes and returns the head; unreadable rows are discarded rather than
 *   blocking the queue forever.
 */
internal class RoomRingStateStore(
    private val db: AlarmClockDatabase,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : RingStateStore {

    private val dao = db.ringState()

    override val active: Flow<ActiveRing?> =
        dao.observeActive()
            .map { it?.toModel() }
            .distinctUntilChanged()
            .flowOn(io)

    override suspend fun getActive(): ActiveRing? = withContext(io) { dao.getActive()?.toModel() }

    override suspend fun setActive(ring: ActiveRing?) {
        withContext(io) {
            db.withTransaction {
                dao.clearActive()
                if (ring != null) dao.insert(ring.toEntity(RingStateEntity.SLOT_ACTIVE))
            }
        }
    }

    override suspend fun queue(): List<ActiveRing> = withContext(io) { dao.queued().mapNotNull { it.toModel() } }

    override suspend fun enqueue(ring: ActiveRing) {
        withContext(io) {
            db.withTransaction {
                val waiting = dao.findQueued(ring.ringId)
                if (waiting != null) {
                    dao.updateJson(waiting.id, Columns.activeRing.encode(ring))
                } else {
                    dao.insert(ring.toEntity(RingStateEntity.SLOT_QUEUED))
                }
            }
        }
    }

    override suspend fun dequeue(): ActiveRing? = withContext(io) {
        db.withTransaction {
            var next: ActiveRing? = null
            while (next == null) {
                val head = dao.headOfQueue() ?: break
                dao.delete(head.id)
                next = head.toModel()
                if (next == null) DataLog.w("Discarded unreadable queued ring #${head.id}")
            }
            next
        }
    }

    override suspend fun clearQueue() {
        withContext(io) { dao.clearQueue() }
    }
}
