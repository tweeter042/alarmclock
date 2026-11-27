package io.github.tweeter042.alarmclock.core.data.repository

import androidx.room.withTransaction
import io.github.tweeter042.alarmclock.core.data.db.AlarmClockDatabase
import io.github.tweeter042.alarmclock.core.data.db.idOrNew
import io.github.tweeter042.alarmclock.core.data.db.toEntity
import io.github.tweeter042.alarmclock.core.data.db.toModel
import io.github.tweeter042.alarmclock.core.model.alarm.Alarm
import io.github.tweeter042.alarmclock.core.model.entity.AlarmGroup
import io.github.tweeter042.alarmclock.core.model.repository.AlarmRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Alarms and alarm groups.
 *
 * - Alarms are listed by time of day (minute of day), then id; groups by sortOrder, then id.
 * - Writes stamp `createdAt` (kept from the stored row, or now for new alarms) and `updatedAt`.
 * - A `groupId` that does not refer to an existing group is stored as `null`, so an alarm can never
 *   point at a deleted group (e.g. an editor that was open while the group was deleted).
 */
internal class RoomAlarmRepository(
    private val db: AlarmClockDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : AlarmRepository {

    private val alarmDao = db.alarms()
    private val groupDao = db.groups()

    override fun observeAlarms(): Flow<List<Alarm>> =
        alarmDao.observeAll()
            .map { rows -> rows.mapNotNull { it.toModel() } }
            .distinctUntilChanged()
            .flowOn(io)

    override fun observeAlarm(id: Long): Flow<Alarm?> =
        alarmDao.observe(id)
            .map { it?.toModel() }
            .distinctUntilChanged()
            .flowOn(io)

    override suspend fun getAlarm(id: Long): Alarm? = withContext(io) { alarmDao.get(id)?.toModel() }

    override suspend fun getAlarms(): List<Alarm> = withContext(io) { alarmDao.getAll().mapNotNull { it.toModel() } }

    override suspend fun upsert(alarm: Alarm): Long = withContext(io) {
        db.withTransaction {
            val now = clock()
            val id = alarm.id.idOrNew()
            val stored = if (id != 0L) alarmDao.get(id)?.toModel() else null
            val createdAt = when {
                alarm.createdAt != 0L -> alarm.createdAt
                stored != null && stored.createdAt != 0L -> stored.createdAt
                else -> now
            }
            val toSave = alarm.copy(
                id = id,
                groupId = existingGroupOrNull(alarm.groupId),
                createdAt = createdAt,
                updatedAt = now,
            )
            alarmDao.upsert(toSave.toEntity())
        }
    }

    override suspend fun delete(id: Long) {
        withContext(io) { alarmDao.delete(id) }
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) = update(id) { it.copy(enabled = enabled) }

    override suspend fun update(id: Long, transform: (Alarm) -> Alarm) {
        withContext(io) {
            db.withTransaction {
                val current = alarmDao.get(id)?.toModel() ?: return@withTransaction
                val transformed = transform(current)
                if (transformed == current) return@withTransaction
                val toSave = transformed.copy(
                    id = id,
                    groupId = existingGroupOrNull(transformed.groupId),
                    createdAt = current.createdAt.takeIf { it != 0L } ?: transformed.createdAt,
                    updatedAt = clock(),
                )
                alarmDao.upsert(toSave.toEntity())
            }
        }
    }

    override fun observeGroups(): Flow<List<AlarmGroup>> =
        groupDao.observeAll()
            .map { rows -> rows.mapNotNull { it.toModel() } }
            .distinctUntilChanged()
            .flowOn(io)

    override suspend fun getGroups(): List<AlarmGroup> = withContext(io) { groupDao.getAll().mapNotNull { it.toModel() } }

    /** New groups with the default sortOrder 0 are appended after the existing ones. */
    override suspend fun upsertGroup(group: AlarmGroup): Long = withContext(io) {
        db.withTransaction {
            val id = group.id.idOrNew()
            val sortOrder = if (id == 0L) appendSortOrder(group.sortOrder, groupDao.maxSortOrder()) else group.sortOrder
            groupDao.upsert(group.copy(id = id, sortOrder = sortOrder).toEntity())
        }
    }

    override suspend fun deleteGroup(id: Long) {
        withContext(io) {
            db.withTransaction {
                val now = clock()
                alarmDao.getInGroup(id).forEach { row ->
                    val alarm = row.toModel()
                    if (alarm != null) {
                        alarmDao.upsert(alarm.copy(groupId = null, updatedAt = now).toEntity())
                    } else {
                        // Unreadable JSON: at least detach the row from the group being deleted.
                        alarmDao.clearGroupColumn(row.id)
                    }
                }
                groupDao.delete(id)
            }
        }
    }

    override suspend fun setGroupEnabled(id: Long, enabled: Boolean) {
        withContext(io) {
            db.withTransaction {
                val group = groupDao.get(id)?.toModel() ?: return@withTransaction
                if (group.enabled != enabled) groupDao.upsert(group.copy(enabled = enabled).toEntity())
            }
        }
    }

    private suspend fun existingGroupOrNull(groupId: Long?): Long? = groupId?.takeIf { groupDao.exists(it) }
}
