package io.github.tweeter042.alarmclock.core.data.repository

import androidx.room.withTransaction
import io.github.tweeter042.alarmclock.core.data.db.AlarmClockDatabase
import io.github.tweeter042.alarmclock.core.data.db.idOrNew
import io.github.tweeter042.alarmclock.core.data.db.toEntity
import io.github.tweeter042.alarmclock.core.data.db.toModel
import io.github.tweeter042.alarmclock.core.model.entity.WorldClock
import io.github.tweeter042.alarmclock.core.model.repository.WorldClockRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** World clocks in user order. New clocks with the default sortOrder 0 are appended at the end. */
internal class RoomWorldClockRepository(
    private val db: AlarmClockDatabase,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : WorldClockRepository {

    private val dao = db.worldClocks()

    override fun observe(): Flow<List<WorldClock>> =
        dao.observeAll()
            .map { rows -> rows.mapNotNull { it.toModel() } }
            .distinctUntilChanged()
            .flowOn(io)

    override suspend fun upsert(clock: WorldClock): Long = withContext(io) {
        db.withTransaction {
            val id = clock.id.idOrNew()
            val sortOrder = if (id == 0L) appendSortOrder(clock.sortOrder, dao.maxSortOrder()) else clock.sortOrder
            dao.upsert(clock.copy(id = id, sortOrder = sortOrder).toEntity())
        }
    }

    override suspend fun delete(id: Long) {
        withContext(io) { dao.delete(id) }
    }

    /** [ids] first to last; clocks not listed keep their relative order after them. */
    override suspend fun reorder(ids: List<Long>) {
        withContext(io) {
            db.withTransaction {
                val current = dao.getAll().mapNotNull { it.toModel() }
                reorderedPositions(current, ids, WorldClock::id, WorldClock::sortOrder).forEach { (clock, position) ->
                    dao.upsert(clock.copy(sortOrder = position).toEntity())
                }
            }
        }
    }
}
