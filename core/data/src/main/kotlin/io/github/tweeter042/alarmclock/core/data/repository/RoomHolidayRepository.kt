package io.github.tweeter042.alarmclock.core.data.repository

import androidx.room.withTransaction
import io.github.tweeter042.alarmclock.core.data.db.AlarmClockDatabase
import io.github.tweeter042.alarmclock.core.data.db.toEntity
import io.github.tweeter042.alarmclock.core.data.db.toModel
import io.github.tweeter042.alarmclock.core.model.entity.Holiday
import io.github.tweeter042.alarmclock.core.model.entity.HolidaySource
import io.github.tweeter042.alarmclock.core.model.repository.HolidayRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Holidays of every source, ordered by date then id. */
internal class RoomHolidayRepository(
    private val db: AlarmClockDatabase,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : HolidayRepository {

    private val dao = db.holidays()

    override fun observeAll(): Flow<List<Holiday>> =
        dao.observeAll()
            .map { rows -> rows.mapNotNull { it.toModel() } }
            .distinctUntilChanged()
            .flowOn(io)

    /** Holidays whose date is in [from]..[to] (both inclusive). */
    override suspend fun between(from: LocalDate, to: LocalDate): List<Holiday> = withContext(io) {
        if (to.isBefore(from)) emptyList() else dao.between(from.toString(), to.toString()).mapNotNull { it.toModel() }
    }

    override suspend fun upsert(holiday: Holiday): Long = withContext(io) { dao.upsert(holiday.toEntity()) }

    override suspend fun delete(id: Long) {
        withContext(io) { dao.delete(id) }
    }

    /**
     * Atomically replaces every holiday of [source] with [holidays] (inserted as new rows, forced to
     * [source], duplicates by date + English name dropped).
     *
     * A holiday the user had switched off stays off when the same holiday (same date and English
     * name) comes back in a re-import, so periodic calendar re-imports never undo that choice.
     */
    override suspend fun replaceSource(source: HolidaySource, holidays: List<Holiday>) {
        withContext(io) {
            db.withTransaction {
                val sources = listOf(source.name)
                val disabledBefore = dao.bySources(sources)
                    .mapNotNull { it.toModel() }
                    .filterNot { it.enabled }
                    .mapTo(HashSet()) { it.date to it.nameEn }
                dao.deleteSources(sources)
                holidays
                    .distinctBy { it.date to it.nameEn }
                    .forEach { holiday ->
                        val keepDisabled = (holiday.date to holiday.nameEn) in disabledBefore
                        val row = holiday.copy(
                            id = 0L,
                            source = source,
                            enabled = holiday.enabled && !keepDisabled,
                        )
                        dao.upsert(row.toEntity())
                    }
            }
        }
    }
}
