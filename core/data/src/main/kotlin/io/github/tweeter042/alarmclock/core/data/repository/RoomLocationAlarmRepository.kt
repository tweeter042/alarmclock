package io.github.tweeter042.alarmclock.core.data.repository

import io.github.tweeter042.alarmclock.core.data.db.AlarmClockDatabase
import io.github.tweeter042.alarmclock.core.data.db.toEntity
import io.github.tweeter042.alarmclock.core.data.db.toModel
import io.github.tweeter042.alarmclock.core.model.entity.LocationAlarm
import io.github.tweeter042.alarmclock.core.model.repository.LocationAlarmRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Location (geofence) alarms, listed in creation (id) order. */
internal class RoomLocationAlarmRepository(
    private val db: AlarmClockDatabase,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : LocationAlarmRepository {

    private val dao = db.locationAlarms()

    override fun observe(): Flow<List<LocationAlarm>> =
        dao.observeAll()
            .map { rows -> rows.mapNotNull { it.toModel() } }
            .distinctUntilChanged()
            .flowOn(io)

    override suspend fun getAll(): List<LocationAlarm> = withContext(io) { dao.getAll().mapNotNull { it.toModel() } }

    override suspend fun get(id: Long): LocationAlarm? = withContext(io) { dao.get(id)?.toModel() }

    override suspend fun upsert(alarm: LocationAlarm): Long = withContext(io) { dao.upsert(alarm.toEntity()) }

    override suspend fun delete(id: Long) {
        withContext(io) { dao.delete(id) }
    }
}
