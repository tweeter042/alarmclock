package io.github.tweeter042.alarmclock.core.data.repository

import androidx.room.withTransaction
import io.github.tweeter042.alarmclock.core.data.db.AlarmClockDatabase
import io.github.tweeter042.alarmclock.core.data.db.idOrNew
import io.github.tweeter042.alarmclock.core.data.db.toEntity
import io.github.tweeter042.alarmclock.core.data.db.toModel
import io.github.tweeter042.alarmclock.core.model.entity.PhotoTarget
import io.github.tweeter042.alarmclock.core.model.entity.RegisteredCode
import io.github.tweeter042.alarmclock.core.model.repository.MissionAssetRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * QR/barcodes and photo targets registered for missions (listed in registration order).
 *
 * `getCodes(ids)` / `getPhotoTargets(ids)` return the stored items in the order of [ids]; unknown
 * ids are skipped. Image files of photo targets are owned by feature:missions: deleting a target
 * here only removes its metadata.
 */
internal class RoomMissionAssetRepository(
    private val db: AlarmClockDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : MissionAssetRepository {

    private val codeDao = db.registeredCodes()
    private val photoDao = db.photoTargets()

    override fun observeCodes(): Flow<List<RegisteredCode>> =
        codeDao.observeAll()
            .map { rows -> rows.mapNotNull { it.toModel() } }
            .distinctUntilChanged()
            .flowOn(io)

    override suspend fun getCodes(ids: List<Long>): List<RegisteredCode> = withContext(io) {
        if (ids.isEmpty()) return@withContext emptyList()
        ids.sqlChunks()
            .flatMap { chunk -> codeDao.getByIds(chunk) }
            .mapNotNull { it.toModel() }
            .inOrderOf(ids, RegisteredCode::id)
    }

    override suspend fun upsertCode(code: RegisteredCode): Long = withContext(io) {
        db.withTransaction {
            val id = code.id.idOrNew()
            val createdAt = code.createdAt.takeIf { it != 0L }
                ?: (if (id != 0L) codeDao.get(id)?.toModel()?.createdAt?.takeIf { it != 0L } else null)
                ?: clock()
            codeDao.upsert(code.copy(id = id, createdAt = createdAt).toEntity())
        }
    }

    override suspend fun deleteCode(id: Long) {
        withContext(io) { codeDao.delete(id) }
    }

    override fun observePhotoTargets(): Flow<List<PhotoTarget>> =
        photoDao.observeAll()
            .map { rows -> rows.mapNotNull { it.toModel() } }
            .distinctUntilChanged()
            .flowOn(io)

    override suspend fun getPhotoTargets(ids: List<Long>): List<PhotoTarget> = withContext(io) {
        if (ids.isEmpty()) return@withContext emptyList()
        ids.sqlChunks()
            .flatMap { chunk -> photoDao.getByIds(chunk) }
            .mapNotNull { it.toModel() }
            .inOrderOf(ids, PhotoTarget::id)
    }

    override suspend fun upsertPhotoTarget(target: PhotoTarget): Long = withContext(io) {
        db.withTransaction {
            val id = target.id.idOrNew()
            val createdAt = target.createdAt.takeIf { it != 0L }
                ?: (if (id != 0L) photoDao.get(id)?.toModel()?.createdAt?.takeIf { it != 0L } else null)
                ?: clock()
            photoDao.upsert(target.copy(id = id, createdAt = createdAt).toEntity())
        }
    }

    override suspend fun deletePhotoTarget(id: Long) {
        withContext(io) { photoDao.delete(id) }
    }
}
