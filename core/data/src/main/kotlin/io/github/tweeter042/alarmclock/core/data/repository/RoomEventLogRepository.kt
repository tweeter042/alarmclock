package io.github.tweeter042.alarmclock.core.data.repository

import androidx.room.withTransaction
import io.github.tweeter042.alarmclock.core.data.DataLog
import io.github.tweeter042.alarmclock.core.data.db.AlarmClockDatabase
import io.github.tweeter042.alarmclock.core.data.db.toEntity
import io.github.tweeter042.alarmclock.core.data.db.toModel
import io.github.tweeter042.alarmclock.core.model.entity.AlarmEvent
import io.github.tweeter042.alarmclock.core.model.entity.SleepLog
import io.github.tweeter042.alarmclock.core.model.repository.EventLogRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Ring history and sleep logs.
 *
 * Time ranges are half-open: `fromMillis <= t < toMillis`, where t is the event's occurrenceAt or
 * the sleep log's wakeAt. Results are ordered by that time, then id.
 */
internal class RoomEventLogRepository(
    private val db: AlarmClockDatabase,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : EventLogRepository {

    private val eventDao = db.events()
    private val sleepDao = db.sleepLogs()

    /** Inserts a new event (id 0) or overwrites the event with the same id. Returns the id. */
    override suspend fun insert(event: AlarmEvent): Long = withContext(io) { eventDao.upsert(event.toEntity()) }

    /** Updates a stored event; unsaved (id 0) or unknown events are ignored. */
    override suspend fun update(event: AlarmEvent) {
        withContext(io) {
            if (event.id <= 0L) {
                DataLog.w("Ignoring update of an unsaved alarm event")
                return@withContext
            }
            if (eventDao.update(event.toEntity()) == 0) DataLog.w("Alarm event #${event.id} no longer exists")
        }
    }

    override fun observeBetween(fromMillis: Long, toMillis: Long): Flow<List<AlarmEvent>> =
        eventDao.observeBetween(fromMillis, toMillis)
            .map { rows -> rows.mapNotNull { it.toModel() } }
            .distinctUntilChanged()
            .flowOn(io)

    override suspend fun between(fromMillis: Long, toMillis: Long): List<AlarmEvent> = withContext(io) {
        eventDao.between(fromMillis, toMillis).mapNotNull { it.toModel() }
    }

    override suspend fun insertSleepLog(log: SleepLog): Long = withContext(io) { sleepDao.upsert(log.toEntity()) }

    override fun observeSleepLogs(fromMillis: Long, toMillis: Long): Flow<List<SleepLog>> =
        sleepDao.observeBetween(fromMillis, toMillis)
            .map { rows -> rows.mapNotNull { it.toModel() } }
            .distinctUntilChanged()
            .flowOn(io)

    /** Deletes events that occurred, and sleep logs that ended, before [beforeMillis]. */
    override suspend fun prune(beforeMillis: Long) {
        withContext(io) {
            db.withTransaction {
                val events = eventDao.deleteBefore(beforeMillis)
                val logs = sleepDao.deleteBefore(beforeMillis)
                if (events + logs > 0) DataLog.i("Pruned $events alarm events and $logs sleep logs")
            }
        }
    }
}
