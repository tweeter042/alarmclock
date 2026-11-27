package io.github.tweeter042.alarmclock.core.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.withTransaction
import io.github.tweeter042.alarmclock.core.data.DataLog
import io.github.tweeter042.alarmclock.core.data.db.AlarmClockDatabase
import io.github.tweeter042.alarmclock.core.data.db.toEntity
import io.github.tweeter042.alarmclock.core.data.db.toModel
import io.github.tweeter042.alarmclock.core.model.alarm.Alarm
import io.github.tweeter042.alarmclock.core.model.alarm.MissionConfig
import io.github.tweeter042.alarmclock.core.model.alarm.MissionType
import io.github.tweeter042.alarmclock.core.model.entity.AlarmGroup
import io.github.tweeter042.alarmclock.core.model.entity.HolidaySource
import io.github.tweeter042.alarmclock.core.model.entity.MorningRoutineItem
import io.github.tweeter042.alarmclock.core.model.entity.TimerModel
import io.github.tweeter042.alarmclock.core.model.entity.TimerPreset
import io.github.tweeter042.alarmclock.core.model.entity.TimerState
import io.github.tweeter042.alarmclock.core.model.entity.WorldClock
import io.github.tweeter042.alarmclock.core.model.repository.SettingsRepository
import io.github.tweeter042.alarmclock.core.model.service.BackupManager
import io.github.tweeter042.alarmclock.core.model.service.BackupSummary
import io.github.tweeter042.alarmclock.core.model.settings.AppSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException

/**
 * JSON backup and restore of everything the user configured (see [BackupDocument] for the
 * contents). Secrets are never written. Photo targets are backed up as metadata only; their
 * reference image files are not part of the backup.
 *
 * Restore modes:
 * - `replace = true`: in ONE database transaction, wipes the backed-up tables (BUILT_IN holidays are
 *   kept, they are generated) and restores every item with its original id; then replaces the
 *   settings, keeping this device's own state (see [keepingDeviceStateOf]).
 * - `replace = false`: merges in ONE transaction: every item is inserted as a new row, appended
 *   after the existing items, with group ids and mission target ids (QR codes / photo targets)
 *   remapped to the new rows. Settings are left untouched.
 *
 * In both modes references that point nowhere (deleted groups, unknown mission targets) are dropped,
 * and timers are restored as IDLE. The input is fully validated before anything is changed, so a
 * rejected file never touches the data. Settings are written after the database transaction has
 * committed. All failures come back as `Result.failure(BackupException)`.
 */
internal class JsonBackupManager(
    context: Context,
    private val db: AlarmClockDatabase,
    private val settings: SettingsRepository,
    private val versionName: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : BackupManager {

    private val folder = SafBackupFolder((context.applicationContext ?: context).contentResolver)

    override suspend fun exportTo(out: OutputStream): Result<BackupSummary> = runBackup("export") {
        withContext(io) {
            val document = snapshot(createdAt = clock())
            write(document, out)
            document.summary()
        }
    }

    override suspend fun importFrom(input: InputStream, replace: Boolean): Result<BackupSummary> = runBackup("import") {
        withContext(io) {
            val document = BackupCodec.decode(readBackupText(input))
            val summary = if (replace) restoreReplacing(document) else restoreMerging(document)
            DataLog.i(
                "Restored backup (${if (replace) "replace" else "merge"}): ${summary.alarms} alarms, " +
                    "${summary.groups} groups, ${summary.holidays} holidays, ${summary.other} other items",
            )
            summary
        }
    }

    override suspend fun autoBackupNow(): Result<String> = runBackup("automatic backup") {
        val treeUri = settings.current().backup.treeUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
            ?: throw BackupFolderUnavailableException("No backup folder is selected")
        val now = clock()
        val document = withContext(io) {
            val created = folder.create(treeUri, BackupFileNames.forTime(now, zone()))
            try {
                folder.openOutput(created).use { out -> write(snapshot(createdAt = now), out) }
            } catch (e: Throwable) {
                folder.deleteQuietly(created)
                throw e
            }
            created
        }
        settings.update { it.copy(backup = it.backup.copy(lastBackupAt = now)) }
        withContext(io) { pruneOldBackups(treeUri, keepDocumentId = documentIdOrNull(document)) }
        document.toString()
    }

    // --- export ------------------------------------------------------------------------------

    /** A consistent snapshot: all tables are read in one transaction. */
    private suspend fun snapshot(createdAt: Long): BackupDocument {
        val currentSettings = settings.current()
        return db.withTransaction {
            BackupDocument(
                format = BackupCodec.FORMAT,
                version = BackupCodec.VERSION,
                createdAt = createdAt,
                appVersion = versionName,
                settings = currentSettings,
                alarms = db.alarms().getAll().mapNotNull { it.toModel() },
                groups = db.groups().getAll().mapNotNull { it.toModel() },
                holidays = db.holidays().bySources(BACKED_UP_HOLIDAY_SOURCES).mapNotNull { it.toModel() },
                timers = db.timers().getAll().mapNotNull { it.toModel()?.asIdle() },
                timerPresets = db.timerPresets().getAll().mapNotNull { it.toModel() },
                worldClocks = db.worldClocks().getAll().mapNotNull { it.toModel() },
                routineItems = db.routineItems().getAll().mapNotNull { it.toModel() },
                registeredCodes = db.registeredCodes().getAll().mapNotNull { it.toModel() },
                photoTargets = db.photoTargets().getAll().mapNotNull { it.toModel() },
                calendarRules = db.calendarRules().getAll().mapNotNull { it.toModel() },
                locationAlarms = db.locationAlarms().getAll().mapNotNull { it.toModel() },
            )
        }
    }

    private fun write(document: BackupDocument, out: OutputStream) {
        out.write(BackupCodec.encode(document).encodeToByteArray())
        out.flush()
    }

    // --- import ------------------------------------------------------------------------------

    private suspend fun restoreReplacing(doc: BackupDocument): BackupSummary {
        val holidays = doc.holidays.filter { it.source != HolidaySource.BUILT_IN }
        val references = db.withTransaction {
            wipeBackedUpData()
            val groupIds = insertTracked(doc.groups, AlarmGroup::id) { db.groups().insert(it.toEntity()) }
            val codeIds = insertTracked(doc.registeredCodes, { it.id }) { db.registeredCodes().insert(it.toEntity()) }
            val photoIds = insertTracked(doc.photoTargets, { it.id }) { db.photoTargets().insert(it.toEntity()) }
            val refs = References(groupIds, codeIds, photoIds)

            doc.alarms.forEach { db.alarms().insert(refs.alarm(it).toEntity()) }
            holidays.forEach { holiday ->
                // Ids are kept unless a (kept) BUILT_IN holiday already uses them; nothing references holiday ids.
                val keepId = holiday.id > 0L && !db.holidays().exists(holiday.id)
                db.holidays().insert(holiday.copy(id = if (keepId) holiday.id else 0L).toEntity())
            }
            doc.timers.forEach { db.timers().insert(it.asIdle().toEntity()) }
            doc.timerPresets.forEach { db.timerPresets().insert(it.toEntity()) }
            doc.worldClocks.forEach { db.worldClocks().insert(it.toEntity()) }
            doc.routineItems.forEach { db.routineItems().insert(it.toEntity()) }
            doc.calendarRules.forEach { db.calendarRules().insert(it.copy(template = refs.alarm(it.template)).toEntity()) }
            doc.locationAlarms.forEach { db.locationAlarms().insert(it.copy(template = refs.alarm(it.template)).toEntity()) }
            refs
        }
        doc.settings?.let { restored ->
            val sanitized = restored.copy(alarmDefaults = references.alarm(restored.alarmDefaults))
            settings.update { current -> sanitized.keepingDeviceStateOf(current) }
        }
        return doc.copy(holidays = holidays).summary()
    }

    private suspend fun restoreMerging(doc: BackupDocument): BackupSummary {
        val holidays = doc.holidays.filter { it.source != HolidaySource.BUILT_IN }
        val now = clock()
        db.withTransaction {
            val groupBase = nextSortOrder(db.groups().maxSortOrder())
            val groupIds = HashMap<Long, Long>()
            doc.groups.sortedWith(compareBy<AlarmGroup>({ it.sortOrder }, { it.id })).forEachIndexed { index, group ->
                val newId = db.groups().insert(group.copy(id = 0L, sortOrder = groupBase + index).toEntity())
                if (group.id > 0L) groupIds[group.id] = newId
            }
            val codeIds = insertTracked(doc.registeredCodes, { it.id }) { db.registeredCodes().insert(it.copy(id = 0L).toEntity()) }
            val photoIds = insertTracked(doc.photoTargets, { it.id }) { db.photoTargets().insert(it.copy(id = 0L).toEntity()) }
            val refs = References(groupIds, codeIds, photoIds)

            doc.alarms.forEach { alarm ->
                val merged = refs.alarm(alarm).copy(
                    id = 0L,
                    createdAt = alarm.createdAt.takeIf { it != 0L } ?: now,
                    updatedAt = now,
                )
                db.alarms().insert(merged.toEntity())
            }
            holidays.forEach { db.holidays().insert(it.copy(id = 0L).toEntity()) }
            doc.timers.forEach { timer ->
                val idle = timer.asIdle()
                db.timers().insert(idle.copy(id = 0L, createdAt = idle.createdAt.takeIf { it != 0L } ?: now).toEntity())
            }

            val presetBase = nextSortOrder(db.timerPresets().maxSortOrder())
            doc.timerPresets.sortedWith(compareBy<TimerPreset>({ it.sortOrder }, { it.id })).forEachIndexed { index, preset ->
                db.timerPresets().insert(preset.copy(id = 0L, sortOrder = presetBase + index).toEntity())
            }
            val clockBase = nextSortOrder(db.worldClocks().maxSortOrder())
            doc.worldClocks.sortedWith(compareBy<WorldClock>({ it.sortOrder }, { it.id })).forEachIndexed { index, worldClock ->
                db.worldClocks().insert(worldClock.copy(id = 0L, sortOrder = clockBase + index).toEntity())
            }
            val routineBase = nextSortOrder(db.routineItems().maxSortOrder())
            doc.routineItems.sortedWith(compareBy<MorningRoutineItem>({ it.sortOrder }, { it.id })).forEachIndexed { index, item ->
                db.routineItems().insert(item.copy(id = 0L, sortOrder = routineBase + index).toEntity())
            }
            doc.calendarRules.forEach { rule ->
                db.calendarRules().insert(rule.copy(id = 0L, template = refs.alarm(rule.template)).toEntity())
            }
            doc.locationAlarms.forEach { location ->
                db.locationAlarms().insert(location.copy(id = 0L, template = refs.alarm(location.template)).toEntity())
            }
        }
        return doc.copy(holidays = holidays).summary()
    }

    private suspend fun wipeBackedUpData() {
        db.alarms().deleteAll()
        db.groups().deleteAll()
        db.holidays().deleteSources(BACKED_UP_HOLIDAY_SOURCES)
        db.timers().deleteAll()
        db.timerPresets().deleteAll()
        db.worldClocks().deleteAll()
        db.routineItems().deleteAll()
        db.registeredCodes().deleteAll()
        db.photoTargets().deleteAll()
        db.calendarRules().deleteAll()
        db.locationAlarms().deleteAll()
    }

    /** Inserts [items] in order and returns old id → new id for the items that had a saved id. */
    private suspend fun <T> insertTracked(items: List<T>, oldId: (T) -> Long, insert: suspend (T) -> Long): Map<Long, Long> {
        val ids = HashMap<Long, Long>(items.size)
        for (item in items) {
            val newId = insert(item)
            val old = oldId(item)
            if (old > 0L) ids[old] = newId
        }
        return ids
    }

    // --- automatic backups ---------------------------------------------------------------------

    private fun pruneOldBackups(treeUri: Uri, keepDocumentId: String?) {
        val entries = try {
            folder.list(treeUri)
        } catch (e: BackupException) {
            DataLog.w("Old backups were not pruned (${DataLog.describe(e)}${e.cause?.let { " / ${DataLog.describe(it)}" } ?: ""})")
            return
        }
        val stale = BackupFileNames.toDelete(entries, keepDocumentId)
        val deleted = stale.count { folder.delete(treeUri, it.documentId) }
        if (deleted > 0) DataLog.i("Deleted $deleted old automatic backups")
    }

    private fun documentIdOrNull(document: Uri): String? = try {
        DocumentsContract.getDocumentId(document)
    } catch (e: IllegalArgumentException) {
        null
    }

    // --- helpers -----------------------------------------------------------------------------

    private suspend fun <T> runBackup(operation: String, block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: BackupException) {
        DataLog.w("Backup $operation failed: ${DataLog.describe(e)}${e.cause?.let { " (${DataLog.describe(it)})" } ?: ""}")
        Result.failure(e)
    } catch (e: Exception) {
        DataLog.e("Backup $operation failed unexpectedly: ${DataLog.describe(e)}")
        Result.failure(BackupIoException("Backup $operation failed", e))
    }

    private companion object {
        val BACKED_UP_HOLIDAY_SOURCES = listOf(HolidaySource.CUSTOM.name, HolidaySource.CALENDAR.name)

        fun nextSortOrder(currentMax: Int?): Int = (currentMax ?: -1) + 1
    }
}

/** Rewrites cross-references of restored items through old id → new id maps (unknown ids are dropped). */
internal class References(
    private val groups: Map<Long, Long>,
    private val codes: Map<Long, Long>,
    private val photos: Map<Long, Long>,
) {
    fun alarm(alarm: Alarm): Alarm = alarm.copy(
        groupId = alarm.groupId?.let { groups[it] },
        missions = alarm.missions.map(::mission),
    )

    private fun mission(mission: MissionConfig): MissionConfig = when (mission.type) {
        MissionType.BARCODE -> mission.copy(targetIds = mission.targetIds.mapNotNull { codes[it] }.distinct())
        MissionType.PHOTO -> mission.copy(targetIds = mission.targetIds.mapNotNull { photos[it] }.distinct())
        else -> mission
    }
}

/** A timer as it is backed up and restored: stopped, full duration remaining. */
internal fun TimerModel.asIdle(): TimerModel = copy(state = TimerState.IDLE, endAt = null, remainingMs = durationMs)

/**
 * Restored settings with the parts that describe THIS device/install kept from [current]:
 * the backup destination (its SAF permission is per install), Samsung settings the user confirmed
 * on this phone and the last self-test, the per-app language (LocaleManager is the source of
 * truth), token expiry (tokens are never backed up), onboarding and "What's new" state, and the
 * last calendar import time.
 */
internal fun AppSettings.keepingDeviceStateOf(current: AppSettings): AppSettings = copy(
    region = region.copy(language = current.region.language),
    reliability = reliability.copy(
        manualChecks = current.reliability.manualChecks,
        lastTestAt = current.reliability.lastTestAt,
        lastTestOk = current.reliability.lastTestOk,
    ),
    holidays = holidays.copy(lastCalendarImportAt = current.holidays.lastCalendarImportAt),
    smartThings = smartThings.copy(accessTokenExpiresAt = current.smartThings.accessTokenExpiresAt),
    backup = current.backup,
    onboardingCompleted = current.onboardingCompleted,
    lastSeenVersionCode = current.lastSeenVersionCode,
)

private const val BYTE_ORDER_MARK = "\uFEFF"

/** Reads at most [BackupCodec.MAX_BYTES] as UTF-8 (a leading byte-order mark is ignored). */
internal fun readBackupText(input: InputStream): String {
    val buffer = ByteArrayOutputStream()
    val chunk = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val read = input.read(chunk)
        if (read < 0) break
        total += read
        if (total > BackupCodec.MAX_BYTES) throw InvalidBackupException("The file is too large to be a backup")
        buffer.write(chunk, 0, read)
    }
    return buffer.toByteArray().decodeToString().removePrefix(BYTE_ORDER_MARK)
}

