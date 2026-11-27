package io.github.tweeter042.alarmclock.core.data.repository

import androidx.room.withTransaction
import io.github.tweeter042.alarmclock.core.data.db.AlarmClockDatabase
import io.github.tweeter042.alarmclock.core.data.db.idOrNew
import io.github.tweeter042.alarmclock.core.data.db.toEntity
import io.github.tweeter042.alarmclock.core.data.db.toModel
import io.github.tweeter042.alarmclock.core.model.entity.MorningRoutineItem
import io.github.tweeter042.alarmclock.core.model.repository.RoutineRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Morning-routine checklist items in user order. New items with sortOrder 0 are appended. */
internal class RoomRoutineRepository(
    private val db: AlarmClockDatabase,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : RoutineRepository {

    private val dao = db.routineItems()

    override fun observeItems(): Flow<List<MorningRoutineItem>> =
        dao.observeAll()
            .map { rows -> rows.mapNotNull { it.toModel() } }
            .distinctUntilChanged()
            .flowOn(io)

    override suspend fun upsert(item: MorningRoutineItem): Long = withContext(io) {
        db.withTransaction {
            val id = item.id.idOrNew()
            val sortOrder = if (id == 0L) appendSortOrder(item.sortOrder, dao.maxSortOrder()) else item.sortOrder
            dao.upsert(item.copy(id = id, sortOrder = sortOrder).toEntity())
        }
    }

    override suspend fun delete(id: Long) {
        withContext(io) { dao.delete(id) }
    }

    /** [ids] first to last; items not listed keep their relative order after them. */
    override suspend fun reorder(ids: List<Long>) {
        withContext(io) {
            db.withTransaction {
                val current = dao.getAll().mapNotNull { it.toModel() }
                reorderedPositions(current, ids, MorningRoutineItem::id, MorningRoutineItem::sortOrder).forEach { (item, position) ->
                    dao.upsert(item.copy(sortOrder = position).toEntity())
                }
            }
        }
    }
}
