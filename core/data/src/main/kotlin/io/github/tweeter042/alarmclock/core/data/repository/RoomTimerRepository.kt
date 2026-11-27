package io.github.tweeter042.alarmclock.core.data.repository

import androidx.room.withTransaction
import io.github.tweeter042.alarmclock.core.data.db.AlarmClockDatabase
import io.github.tweeter042.alarmclock.core.data.db.idOrNew
import io.github.tweeter042.alarmclock.core.data.db.toEntity
import io.github.tweeter042.alarmclock.core.data.db.toModel
import io.github.tweeter042.alarmclock.core.model.entity.TimerModel
import io.github.tweeter042.alarmclock.core.model.entity.TimerPreset
import io.github.tweeter042.alarmclock.core.model.repository.TimerRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Timers (listed in creation order) and timer presets (listed by sortOrder). Timers get
 * `createdAt` stamped on first save; new presets with the default sortOrder 0 are appended.
 */
internal class RoomTimerRepository(
    private val db: AlarmClockDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : TimerRepository {

    private val timerDao = db.timers()
    private val presetDao = db.timerPresets()

    override fun observeTimers(): Flow<List<TimerModel>> =
        timerDao.observeAll()
            .map { rows -> rows.mapNotNull { it.toModel() } }
            .distinctUntilChanged()
            .flowOn(io)

    override suspend fun getTimer(id: Long): TimerModel? = withContext(io) { timerDao.get(id)?.toModel() }

    override suspend fun upsert(timer: TimerModel): Long = withContext(io) {
        db.withTransaction {
            val id = timer.id.idOrNew()
            val createdAt = timer.createdAt.takeIf { it != 0L }
                ?: (if (id != 0L) timerDao.get(id)?.toModel()?.createdAt?.takeIf { it != 0L } else null)
                ?: clock()
            timerDao.upsert(timer.copy(id = id, createdAt = createdAt).toEntity())
        }
    }

    override suspend fun delete(id: Long) {
        withContext(io) { timerDao.delete(id) }
    }

    override fun observePresets(): Flow<List<TimerPreset>> =
        presetDao.observeAll()
            .map { rows -> rows.mapNotNull { it.toModel() } }
            .distinctUntilChanged()
            .flowOn(io)

    override suspend fun upsertPreset(preset: TimerPreset): Long = withContext(io) {
        db.withTransaction {
            val id = preset.id.idOrNew()
            val sortOrder = if (id == 0L) appendSortOrder(preset.sortOrder, presetDao.maxSortOrder()) else preset.sortOrder
            presetDao.upsert(preset.copy(id = id, sortOrder = sortOrder).toEntity())
        }
    }

    override suspend fun deletePreset(id: Long) {
        withContext(io) { presetDao.delete(id) }
    }
}
