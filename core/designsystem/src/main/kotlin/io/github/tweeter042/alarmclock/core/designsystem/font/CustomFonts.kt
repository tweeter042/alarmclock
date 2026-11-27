package io.github.tweeter042.alarmclock.core.designsystem.font

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import io.github.tweeter042.alarmclock.core.model.font.FontIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * User-imported fonts (.ttf / .otf), stored in `filesDir/fonts/`. The settings value of an
 * imported font is `"custom:<file name>"` ([FontIds.CUSTOM_PREFIX]).
 *
 * Fonts live in credential-encrypted storage; before the first unlock after a reboot they are
 * unavailable and [FontFamilies] falls back to the theme font (the ring screen keeps working).
 */
object CustomFonts {

    const val DIRECTORY: String = "fonts"

    /** Larger files are rejected (fonts with full CJK coverage are ~20 MB). */
    const val MAX_BYTES: Long = 32L * 1024 * 1024

    data class CustomFont(
        /** Settings id: "custom:<fileName>". */
        val id: String,
        val fileName: String,
        /** Human-readable name derived from the original file name. */
        val displayName: String,
        val sizeBytes: Long,
    )

    /** Why an import failed (map to a localized message in the UI). */
    sealed class ImportException(message: String, cause: Throwable? = null) : Exception(message, cause) {
        class Unreadable(cause: Throwable? = null) : ImportException("Font file could not be read", cause)
        class TooLarge(val bytes: Long) : ImportException("Font file is too large: $bytes bytes")
        class NotAFont : ImportException("File is not a TrueType or OpenType font")
    }

    fun directory(context: Context): File = File(context.filesDir, DIRECTORY)

    fun idFor(fileName: String): String = FontIds.CUSTOM_PREFIX + fileName

    /** File name inside [directory] for a "custom:" id, or null if [id] is not a custom font id. */
    fun fileNameOf(id: String): String? =
        if (id.startsWith(FontIds.CUSTOM_PREFIX)) id.removePrefix(FontIds.CUSTOM_PREFIX).takeIf { isSafeName(it) } else null

    fun fileFor(context: Context?, id: String): File? {
        val name = fileNameOf(id) ?: return null
        return context?.let { File(directory(it), name) }
    }

    /**
     * Copies the font behind [uri] (from a SAF picker) into [directory], verifying that Android can
     * load it. Never overwrites an existing font.
     */
    suspend fun import(context: Context, uri: Uri): Result<CustomFont> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val originalName = queryDisplayName(context, uri) ?: "font"
            val dir = directory(context).apply { mkdirs() }
            val temp = File.createTempFile("import", ".tmp", context.cacheDir)
            try {
                val copied = try {
                    resolver.openInputStream(uri)?.use { input ->
                        temp.outputStream().use { output -> copyLimited(input, output) }
                    } ?: throw ImportException.Unreadable()
                } catch (e: ImportException) {
                    throw e
                } catch (e: IOException) {
                    throw ImportException.Unreadable(e)
                } catch (e: SecurityException) {
                    throw ImportException.Unreadable(e)
                }
                val kind = sniff(temp) ?: throw ImportException.NotAFont()
                if (!loads(temp)) throw ImportException.NotAFont()
                val target = uniqueFile(dir, sanitize(originalName.substringBeforeLast('.')), kind)
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = false)
                }
                CustomFont(idFor(target.name), target.name, displayName(target.name), copied)
            } finally {
                temp.delete()
            }
        }
    }

    /** Imported fonts, sorted by display name. */
    suspend fun list(context: Context): List<CustomFont> = withContext(Dispatchers.IO) {
        directory(context).listFiles()
            .orEmpty()
            .filter { it.isFile && isSafeName(it.name) && (it.name.endsWith(".ttf") || it.name.endsWith(".otf")) }
            .map { CustomFont(idFor(it.name), it.name, displayName(it.name), it.length()) }
            .sortedBy { it.displayName.lowercase(Locale.ROOT) }
    }

    /** Deletes an imported font. Returns true if a file was removed. */
    suspend fun delete(context: Context, id: String): Boolean = withContext(Dispatchers.IO) {
        val file = fileFor(context, id) ?: return@withContext false
        val deleted = file.exists() && file.delete()
        if (deleted) FontFamilies.invalidate(id)
        deleted
    }

    /** "my_cool-font.ttf" -> "My cool font". */
    fun displayName(fileName: String): String =
        fileName.substringBeforeLast('.')
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(Regex(" \\d+$"), "")
            .trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            .ifEmpty { fileName }

    internal fun sanitize(name: String): String =
        name.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9._-]+"), "_")
            .trim('_', '.', '-')
            .take(60)
            .ifEmpty { "font" }

    internal fun isSafeName(name: String): Boolean =
        name.isNotEmpty() && !name.contains('/') && !name.contains('\\') && name != "." && name != ".." &&
            name.matches(Regex("[a-z0-9._-]+"))

    /** Returns "ttf" / "otf" from the file header, or null if it is not a single font. */
    internal fun sniff(file: File): String? {
        val header = ByteArray(4)
        val read = file.inputStream().use { it.read(header) }
        if (read < 4) return null
        return when {
            header.contentEquals(byteArrayOf(0, 1, 0, 0)) -> "ttf"
            String(header, Charsets.US_ASCII) == "true" -> "ttf"
            String(header, Charsets.US_ASCII) == "OTTO" -> "otf"
            else -> null
        }
    }

    private fun loads(file: File): Boolean =
        runCatching { Typeface.Builder(file).build() }.getOrNull() != null

    private fun uniqueFile(dir: File, base: String, extension: String): File {
        var candidate = File(dir, "$base.$extension")
        var n = 2
        while (candidate.exists()) {
            candidate = File(dir, "${base}_$n.$extension")
            n++
        }
        return candidate
    }

    private fun copyLimited(input: java.io.InputStream, output: java.io.OutputStream): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > MAX_BYTES) throw ImportException.TooLarge(total)
            output.write(buffer, 0, n)
        }
        return total
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
}
