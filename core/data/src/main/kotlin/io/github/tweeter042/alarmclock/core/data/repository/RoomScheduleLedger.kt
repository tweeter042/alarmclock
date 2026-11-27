package io.github.tweeter042.alarmclock.core.data.repository

import androidx.room.withTransaction
import io.github.tweeter042.alarmclock.core.data.db.AlarmClockDatabase
import io.github.tweeter042.alarmclock.core.data.db.toEntity
import io.github.tweeter042.alarmclock.core.data.db.toModel
import io.github.tweeter042.alarmclock.core.model.entity.ScheduledTrigger
import io.github.tweeter042.alarmclock.core.model.repository.ScheduleLedger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Every trigger registered with AlarmManager, keyed by PendingIntent request code (device-protected
 * storage). [all] is ordered by trigger time, then request code.
 */
internal class RoomScheduleLedger(
    private val db: AlarmClockDatabase,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ScheduleLedger {

    private val dao = db.scheduleLedger()

    override suspend fun all(): List<ScheduledTrigger> = withContext(io) { dao.getAll().mapNotNull { it.toModel() } }

    /** Atomically replaces the whole ledger. If request codes repeat, the last trigger wins. */
    override suspend fun replaceAll(triggers: List<ScheduledTrigger>) {
        withContext(io) {
            db.withTransaction {
                dao.clear()
                dao.putAll(triggers.map { it.toEntity() })
            }
        }
    }

    override suspend fun put(trigger: ScheduledTrigger) {
        withContext(io) { dao.put(trigger.toEntity()) }
    }

    override suspend fun remove(requestCode: Int) {
        withContext(io) { dao.remove(requestCode) }
    }

    override suspend fun get(requestCode: Int): ScheduledTrigger? = withContext(io) { dao.get(requestCode)?.toModel() }
}
