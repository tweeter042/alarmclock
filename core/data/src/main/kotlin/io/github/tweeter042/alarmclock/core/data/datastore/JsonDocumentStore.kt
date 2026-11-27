package io.github.tweeter042.alarmclock.core.data.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import io.github.tweeter042.alarmclock.core.data.DataLog
import io.github.tweeter042.alarmclock.core.model.util.ModelJson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** DataStore [Serializer] that stores one model document as UTF-8 JSON encoded with [ModelJson]. */
internal class JsonSerializer<T>(
    private val serializer: KSerializer<T>,
    override val defaultValue: T,
) : Serializer<T> {

    override suspend fun readFrom(input: InputStream): T {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) throw CorruptionException("Empty ${serializer.descriptor.serialName} document")
        return try {
            ModelJson.decodeFromString(serializer, bytes.decodeToString())
        } catch (e: RuntimeException) {
            // SerializationException, IllegalArgumentException from model init blocks, DateTimeException…
            throw CorruptionException("Unreadable ${serializer.descriptor.serialName} document", e)
        }
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(ModelJson.encodeToString(serializer, t).encodeToByteArray())
    }
}

/** Creates a JSON DataStore whose unreadable file is replaced by [defaultValue] (logged). */
internal fun <T> jsonDataStore(
    name: String,
    serializer: KSerializer<T>,
    defaultValue: T,
    scope: CoroutineScope,
    produceFile: () -> File,
): DataStore<T> = DataStoreFactory.create(
    serializer = JsonSerializer(serializer, defaultValue),
    corruptionHandler = ReplaceFileCorruptionHandler { error ->
        DataLog.w("$name was unreadable and has been reset to defaults (${DataLog.describe(error)})")
        defaultValue
    },
    scope = scope,
    produceFile = produceFile,
)

/**
 * A single JSON document backed by DataStore: a distinct-until-changed [data] flow and atomic
 * read-modify-write [update]s.
 *
 * Content that cannot be parsed is replaced by defaults by the corruption handler. If the file
 * cannot be read at all (I/O error), [data] emits [defaultValue] instead of failing and keeps
 * retrying in the background with a capped backoff, so long-lived observers (the ring engine)
 * never crash and pick the real values up as soon as the file is readable again.
 */
internal class JsonDocumentStore<T>(
    private val store: DataStore<T>,
    private val defaultValue: T,
    private val name: String,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    val data: Flow<T> = store.data
        .retryWhen { error, attempt ->
            if (error !is IOException) return@retryWhen false
            if (attempt == 0L) {
                DataLog.e("$name could not be read; using defaults until it can be (${DataLog.describe(error)})")
                emit(defaultValue)
            }
            delay(retryDelayMillis(attempt))
            true
        }
        .distinctUntilChanged()

    suspend fun current(): T = data.first()

    /** Atomic: DataStore serializes updates and [transform] always sees the latest value. */
    suspend fun update(transform: (T) -> T) {
        withContext(io) { store.updateData { transform(it) } }
    }

    private companion object {
        const val FIRST_RETRY_MILLIS = 1_000L
        const val MAX_RETRY_MILLIS = 60_000L

        fun retryDelayMillis(attempt: Long): Long =
            (FIRST_RETRY_MILLIS shl attempt.coerceAtMost(6L).toInt()).coerceAtMost(MAX_RETRY_MILLIS)
    }
}
