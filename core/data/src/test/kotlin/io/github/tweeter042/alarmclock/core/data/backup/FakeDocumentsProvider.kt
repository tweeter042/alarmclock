package io.github.tweeter042.alarmclock.core.data.backup

import android.Manifest
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import org.robolectric.Robolectric
import java.io.File
import java.io.FileNotFoundException

/**
 * A flat, file-backed Storage Access Framework provider for tests: one folder (document id
 * [ROOT_ID]) whose children are plain files (document id `root/<name>`).
 */
class FakeDocumentsProvider : DocumentsProvider() {

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE)).apply {
            newRow().add(Root.COLUMN_ROOT_ID, "fake").add(Root.COLUMN_DOCUMENT_ID, ROOT_ID).add(Root.COLUMN_TITLE, "Fake")
        }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: DOCUMENT_COLUMNS).apply { addDocument(documentId) }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor =
        MatrixCursor(projection ?: DOCUMENT_COLUMNS).apply {
            folder.listFiles().orEmpty().sortedBy { it.name }.forEach { addDocument(documentIdOf(it)) }
        }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        if (failOpen) throw FileNotFoundException("open refused")
        return ParcelFileDescriptor.open(fileOf(documentId), ParcelFileDescriptor.parseMode(mode))
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        if (denyCreate) throw SecurityException("permission revoked")
        var file = File(folder, displayName)
        var copy = 1
        while (file.exists()) {
            file = File(folder, displayName.removeSuffix(".json") + " ($copy).json")
            copy++
        }
        check(file.createNewFile())
        return documentIdOf(file)
    }

    override fun deleteDocument(documentId: String) {
        if (!fileOf(documentId).delete()) throw FileNotFoundException(documentId)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        parentDocumentId == ROOT_ID && documentId.startsWith("$ROOT_ID/")

    private fun MatrixCursor.addDocument(documentId: String) {
        val file = fileOf(documentId)
        val row = newRow()
        row.add(Document.COLUMN_DOCUMENT_ID, documentId)
        row.add(Document.COLUMN_DISPLAY_NAME, if (documentId == ROOT_ID) "Backups" else file.name)
        row.add(Document.COLUMN_MIME_TYPE, if (documentId == ROOT_ID) Document.MIME_TYPE_DIR else "application/json")
        row.add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
        row.add(Document.COLUMN_SIZE, file.length())
        row.add(Document.COLUMN_FLAGS, Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_WRITE or Document.FLAG_DIR_SUPPORTS_CREATE)
    }

    private fun documentIdOf(file: File) = "$ROOT_ID/${file.name}"

    private fun fileOf(documentId: String): File = when {
        documentId == ROOT_ID -> folder
        documentId.startsWith("$ROOT_ID/") -> File(folder, documentId.removePrefix("$ROOT_ID/"))
        else -> throw FileNotFoundException(documentId)
    }

    companion object {
        const val AUTHORITY = "io.github.tweeter042.alarmclock.core.data.test.documents"
        const val ROOT_ID = "root"

        lateinit var folder: File
        var denyCreate = false
        var failOpen = false

        private val DOCUMENT_COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_SIZE,
            Document.COLUMN_FLAGS,
        )

        /** Registers the provider with Robolectric and returns the tree URI of its folder. */
        fun install(context: Context, folder: File): Uri {
            this.folder = folder
            denyCreate = false
            failOpen = false
            val info = ProviderInfo().apply {
                authority = AUTHORITY
                name = FakeDocumentsProvider::class.java.name
                packageName = context.packageName
                applicationInfo = context.applicationInfo
                exported = true
                grantUriPermissions = true
                readPermission = Manifest.permission.MANAGE_DOCUMENTS
                writePermission = Manifest.permission.MANAGE_DOCUMENTS
            }
            Robolectric.buildContentProvider(FakeDocumentsProvider::class.java).create(info)
            return DocumentsContract.buildTreeDocumentUri(AUTHORITY, ROOT_ID)
        }
    }
}
