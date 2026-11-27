package io.github.tweeter042.alarmclock.core.data.changelog

import android.content.Context
import io.github.tweeter042.alarmclock.core.data.DataLog
import io.github.tweeter042.alarmclock.core.model.service.ChangelogEntry
import io.github.tweeter042.alarmclock.core.model.service.ChangelogRepository
import io.github.tweeter042.alarmclock.core.model.util.ModelJson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import java.io.IOException
import java.io.InputStream

/**
 * The changelog bundled as the app asset [ASSET_NAME]: a JSON array of [ChangelogEntry].
 *
 * Parsed once and cached (assets cannot change at runtime), newest version first. A missing or
 * unreadable file yields an empty list; a single malformed entry is skipped (both logged), so a
 * typo in an old entry can never hide the whole changelog.
 */
internal class AssetChangelogRepository(
    private val openAsset: () -> InputStream,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ChangelogRepository {

    constructor(context: Context) : this(openAsset = { (context.applicationContext ?: context).assets.open(ASSET_NAME) })

    private val mutex = Mutex()

    @Volatile
    private var cache: List<ChangelogEntry>? = null

    override suspend fun entries(): List<ChangelogEntry> =
        cache ?: mutex.withLock { cache ?: load().also { cache = it } }

    override suspend fun entryFor(versionCode: Int): ChangelogEntry? = entries().firstOrNull { it.versionCode == versionCode }

    private suspend fun load(): List<ChangelogEntry> = withContext(io) {
        val text = try {
            openAsset().use { it.readBytes().decodeToString() }
        } catch (e: IOException) {
            DataLog.w("Changelog asset is missing or unreadable (${DataLog.describe(e)})")
            return@withContext emptyList()
        }
        parseChangelog(text)
    }

    companion object {
        const val ASSET_NAME = "changelog.json"
    }
}

/** Parses the changelog JSON array, skipping malformed entries; newest (highest versionCode) first. */
internal fun parseChangelog(text: String): List<ChangelogEntry> {
    val root = try {
        ModelJson.parseToJsonElement(text)
    } catch (e: RuntimeException) {
        DataLog.w("Changelog is not valid JSON (${DataLog.describe(e)})")
        return emptyList()
    }
    val array = root as? JsonArray ?: run {
        DataLog.w("Changelog root must be a JSON array")
        return emptyList()
    }
    return array
        .mapIndexedNotNull { index, element ->
            try {
                ModelJson.decodeFromJsonElement(ChangelogEntry.serializer(), element)
            } catch (e: RuntimeException) {
                DataLog.w("Skipping malformed changelog entry #$index (${DataLog.describe(e)})")
                null
            }
        }
        .sortedByDescending { it.versionCode }
        .distinctBy { it.versionCode }
}
