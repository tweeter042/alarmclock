package io.github.tweeter042.alarmclock.core.alarm.ring

import android.content.Context
import android.util.AtomicFile
import io.github.tweeter042.alarmclock.core.alarm.EngineLog
import io.github.tweeter042.alarmclock.core.alarm.internal.EnginePrefs
import io.github.tweeter042.alarmclock.core.model.alarm.ActiveRing
import io.github.tweeter042.alarmclock.core.model.repository.RingStateStore
import io.github.tweeter042.alarmclock.core.model.util.ModelJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
internal data class RingBookSnapshot(
    val current: ActiveRing? = null,
    val others: List<ActiveRing> = emptyList(),
    val pendingSnooze: Set<String> = emptySet(),
) {
    fun toBook() = RingBook(current, others, pendingSnooze)

    companion object {
        fun of(book: RingBook) = RingBookSnapshot(book.current, book.others, book.pendingSnooze)
    }
}

/**
 * Persists the [RingBook] through RingStateStore (active slot = current, queue = others) so rings
 * survive process death and reboots (device-protected storage).
 *
 * RingStateStore writes the active slot and each queue entry in separate transactions, so a crash in
 * the middle of an update could lose a ring. Every change is therefore journaled first: the complete
 * book is written atomically to `engine_ring_journal.json`, the store is updated, then the journal is
 * deleted. A journal found at load time means an update was interrupted, and the journal wins.
 */
internal class RingBookStore(
    context: Context,
    private val store: RingStateStore,
    private val prefs: EnginePrefs,
) {
    private val journal = AtomicFile(
        File((if (context.isDeviceProtectedStorage) context else context.createDeviceProtectedStorageContext()).filesDir, JOURNAL_FILE),
    )
    private var persisted: RingBook? = null

    suspend fun load(): RingBook {
        readJournal()?.let { recovered ->
            EngineLog.ring.w("Recovering the ring state from an interrupted update")
            val book = RingBook.normalize(recovered)
            persisted = null
            persist(book)
            return book
        }
        val active = store.getActive()
        val queue = store.queue()
        val book = RingBook.normalize(RingBook(active, queue, prefs.pendingSnooze))
        persisted = RingBook(active, queue, prefs.pendingSnooze)
        if (book != persisted) persist(book)
        return book
    }

    suspend fun persist(book: RingBook) {
        val last = persisted
        if (last == book) return
        val activeChanged = last == null || last.current != book.current
        val queueChanged = last == null || last.others != book.others
        if (activeChanged || queueChanged) writeJournal(book)
        if (activeChanged) store.setActive(book.current)
        if (queueChanged) {
            store.clearQueue()
            book.others.forEach { store.enqueue(it) }
        }
        if (last?.pendingSnooze != book.pendingSnooze) prefs.pendingSnooze = book.pendingSnooze
        if (activeChanged || queueChanged) deleteJournal()
        persisted = book
    }

    private suspend fun writeJournal(book: RingBook) = withContext(Dispatchers.IO) {
        val stream = try {
            journal.startWrite()
        } catch (t: Throwable) {
            EngineLog.ring.w("Ring journal not writable", t)
            return@withContext
        }
        try {
            stream.write(ModelJson.encodeToString(RingBookSnapshot.serializer(), RingBookSnapshot.of(book)).encodeToByteArray())
            journal.finishWrite(stream)
        } catch (t: Throwable) {
            journal.failWrite(stream)
            EngineLog.ring.w("Ring journal write failed", t)
        }
    }

    private suspend fun readJournal(): RingBook? = withContext(Dispatchers.IO) {
        try {
            if (!journal.baseFile.exists()) return@withContext null
            ModelJson.decodeFromString(RingBookSnapshot.serializer(), journal.readFully().decodeToString()).toBook()
        } catch (t: Throwable) {
            EngineLog.ring.w("Ring journal unreadable; ignored", t)
            deleteJournal()
            null
        }
    }

    private fun deleteJournal() {
        runCatching { journal.delete() }
    }

    private companion object {
        const val JOURNAL_FILE = "engine_ring_journal.json"
    }
}
