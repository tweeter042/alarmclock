package io.github.tweeter042.alarmclock.core.data.repository

import io.github.tweeter042.alarmclock.core.data.db.AlarmClockDatabase
import io.github.tweeter042.alarmclock.core.data.db.toEntity
import io.github.tweeter042.alarmclock.core.data.db.toModel
import io.github.tweeter042.alarmclock.core.model.entity.CalendarAlarmRule
import io.github.tweeter042.alarmclock.core.model.repository.CalendarRuleRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Calendar alarm rules, listed in creation (id) order. */
internal class RoomCalendarRuleRepository(
    private val db: AlarmClockDatabase,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : CalendarRuleRepository {

    private val dao = db.calendarRules()

    override fun observe(): Flow<List<CalendarAlarmRule>> =
        dao.observeAll()
            .map { rows -> rows.mapNotNull { it.toModel() } }
            .distinctUntilChanged()
            .flowOn(io)

    override suspend fun getAll(): List<CalendarAlarmRule> = withContext(io) { dao.getAll().mapNotNull { it.toModel() } }

    override suspend fun get(id: Long): CalendarAlarmRule? = withContext(io) { dao.get(id)?.toModel() }

    override suspend fun upsert(rule: CalendarAlarmRule): Long = withContext(io) { dao.upsert(rule.toEntity()) }

    override suspend fun delete(id: Long) {
        withContext(io) { dao.delete(id) }
    }
}
