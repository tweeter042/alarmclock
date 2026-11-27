package io.github.tweeter042.alarmclock.core.data.backup

import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import io.github.tweeter042.alarmclock.core.data.DataLog
import java.io.FileNotFoundException
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Names of automatic backup files and which of them to keep. */
internal object BackupFileNames {
    const val PREFIX = "alarmclock-backup-"
    const val EXTENSION = ".json"
    const val MIME_TYPE = "application/json"

    /** Automatic backups kept in the folder (the newest ones). */
    const val KEEP = 10

    private val stampFormat = DateTimeFormatter.ofPattern("uuuuMMdd-HHmm", Locale.ROOT)

    /**
     * Only names this app produces are ever considered for deletion. The optional part covers the
     * " (1)" suffix document providers add when a name already exists.
     */
    private val ownName = Regex("""^${Regex.escape(PREFIX)}(\d{8}-\d{4})(?: ?\(\d+\))?${Regex.escape(EXTENSION)}$""")

    /** `alarmclock-backup-YYYYMMDD-HHmm.json` in local time (ASCII digits, ISO calendar). */
    fun forTime(epochMillis: Long, zone: ZoneId): String =
        PREFIX + stampFormat.format(Instant.ofEpochMilli(epochMillis).atZone(zone)) + EXTENSION

    /** The `YYYYMMDD-HHmm` stamp of one of our backup names, or null for any other file. */
    fun stampOf(displayName: String): String? = ownName.matchEntire(displayName)?.groupValues?.get(1)

    /**
     * Backups to delete so that at most [keep] remain, newest first by name stamp, then by
     * modification time. [keepDocumentId] (the backup just written) is never deleted and counts
     * towards [keep], even if the clock was wrong when older files were named.
     */
    fun toDelete(entries: List<FolderEntry>, keepDocumentId: String?, keep: Int = KEEP): List<FolderEntry> {
        val others = entries.filter { it.documentId != keepDocumentId && stampOf(it.displayName) != null }
        val keepOthers = (keep - if (keepDocumentId != null) 1 else 0).coerceAtLeast(0)
        return others
            .sortedWith(
                compareByDescending<FolderEntry> { stampOf(it.displayName) }
                    .thenByDescending { it.lastModified ?: Long.MIN_VALUE }
                    .thenByDescending { it.displayName },
            )
            .drop(keepOthers)
    }
}

internal data class FolderEntry(val documentId: String, val displayName: String, val lastModified: Long?)

/**
 * The user's backup folder: a Storage Access Framework tree URI with a persisted write permission
 * (for example a Google Drive folder), accessed with [DocumentsContract] directly.
 *
 * Failures to reach the folder are reported as [BackupFolderUnavailableException].
 */
internal class SafBackupFolder(private val resolver: ContentResolver) {

    /** Creates an empty document named [displayName] in the folder and returns its URI. */
    fun create(treeUri: Uri, displayName: String): Uri = folderAccess("create a backup file") {
        val folder = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        DocumentsContract.createDocument(resolver, folder, BackupFileNames.MIME_TYPE, displayName)
            ?: throw BackupFolderUnavailableException("The backup folder refused to create a file")
    }

    /**
     * Opens a document created by [create] for writing. Plain "w" is enough for a new, empty
     * document and is the mode every provider supports (some cloud providers reject "wt").
     */
    fun openOutput(document: Uri): OutputStream = folderAccess("open the backup file") {
        resolver.openOutputStream(document, "w")
            ?: throw BackupFolderUnavailableException("The backup file could not be opened")
    }

    /** Direct children of the folder. */
    fun list(treeUri: Uri): List<FolderEntry> = folderAccess("list the backup folder") {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        // Bundle-based query: the form DocumentsProvider implements (the SQL-style overload is legacy).
        val cursor = resolver.query(children, projection, null as Bundle?, null)
            ?: throw BackupFolderUnavailableException("The backup folder could not be listed")
        cursor.use {
            val idColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val modifiedColumn = it.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            buildList {
                while (it.moveToNext()) {
                    val id = it.getString(idColumn) ?: continue
                    val name = it.getString(nameColumn) ?: continue
                    val modified = if (modifiedColumn >= 0 && !it.isNull(modifiedColumn)) it.getLong(modifiedColumn) else null
                    add(FolderEntry(id, name, modified))
                }
            }
        }
    }

    /** Deletes a child of the folder; returns false (logged) if the provider refused. */
    fun delete(treeUri: Uri, documentId: String): Boolean = try {
        DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId))
    } catch (e: Exception) {
        DataLog.w("Could not delete an old backup (${DataLog.describe(e)})")
        false
    }

    /** Deletes [document] (best effort, used to clean up a partially written backup). */
    fun deleteQuietly(document: Uri) {
        try {
            DocumentsContract.deleteDocument(resolver, document)
        } catch (e: Exception) {
            DataLog.w("Could not remove an incomplete backup (${DataLog.describe(e)})")
        }
    }

    private inline fun <T> folderAccess(action: String, block: () -> T): T = try {
        block()
    } catch (e: BackupException) {
        throw e
    } catch (e: SecurityException) {
        throw BackupFolderUnavailableException("No permission to $action", e)
    } catch (e: FileNotFoundException) {
        throw BackupFolderUnavailableException("The backup folder is gone; could not $action", e)
    } catch (e: IllegalArgumentException) {
        throw BackupFolderUnavailableException("The backup folder address is invalid; could not $action", e)
    } catch (e: UnsupportedOperationException) {
        throw BackupFolderUnavailableException("The backup folder does not support this; could not $action", e)
    }
}
