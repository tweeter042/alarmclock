package io.github.tweeter042.alarmclock.core.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.tweeter042.alarmclock.core.data.DeviceStorage
import io.github.tweeter042.alarmclock.core.data.InMemoryDatabaseRule
import io.github.tweeter042.alarmclock.core.data.db.AlarmClockDatabase
import io.github.tweeter042.alarmclock.core.data.db.RingStateEntity
import io.github.tweeter042.alarmclock.core.data.ring
import io.github.tweeter042.alarmclock.core.model.alarm.RingState
import io.github.tweeter042.alarmclock.core.model.entity.ScheduledTrigger
import io.github.tweeter042.alarmclock.core.model.entity.TriggerKind
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class EngineStoresTest {

    @get:Rule
    val database = InMemoryDatabaseRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        val file = DeviceStorage.databaseFile(context)
        listOf(file, File(file.path + "-wal"), File(file.path + "-shm"), File(file.path + "-journal")).forEach { it.delete() }
    }

    // --- RingStateStore ----------------------------------------------------------------------

    @Test
    fun queueIsStrictlyFifo() = runTest {
        val store = RoomRingStateStore(database.db)
        listOf("a", "b", "c", "d").forEach { store.enqueue(ring(it)) }

        assertEquals(listOf("a", "b", "c", "d"), store.queue().map { it.ringId })
        assertEquals("a", store.dequeue()?.ringId)
        store.enqueue(ring("e"))
        assertEquals(listOf("b", "c", "d", "e"), store.queue().map { it.ringId })
        assertEquals(listOf("b", "c", "d", "e"), listOf(store.dequeue(), store.dequeue(), store.dequeue(), store.dequeue()).map { it?.ringId })
        assertNull(store.dequeue())
        assertTrue(store.queue().isEmpty())
    }

    @Test
    fun reEnqueueingAWaitingRingUpdatesItInPlace() = runTest {
        val store = RoomRingStateStore(database.db)
        store.enqueue(ring("a"))
        store.enqueue(ring("b"))
        store.enqueue(ring("a").copy(snoozeCount = 2))

        val queue = store.queue()
        assertEquals(listOf("a", "b"), queue.map { it.ringId })
        assertEquals(2, queue.first().snoozeCount)
    }

    @Test
    fun concurrentDequeuesNeverReturnTheSameRingTwice() = runTest {
        val store = RoomRingStateStore(database.db)
        (1..20).forEach { store.enqueue(ring("r$it")) }

        val taken = (1..30).map { async { store.dequeue()?.ringId } }.awaitAll().filterNotNull()

        assertEquals((1..20).map { "r$it" }.toSet(), taken.toSet())
        assertEquals(20, taken.size)
    }

    @Test
    fun clearQueueKeepsTheActiveRing() = runTest {
        val store = RoomRingStateStore(database.db)
        store.setActive(ring("active"))
        store.enqueue(ring("q1"))
        store.enqueue(ring("q2"))

        store.clearQueue()

        assertTrue(store.queue().isEmpty())
        assertEquals("active", store.getActive()?.ringId)
    }

    @Test
    fun activeSlotHoldsOneRingAndIsObservable() = runTest {
        val store = RoomRingStateStore(database.db)
        store.active.test {
            assertNull(awaitItem())

            store.setActive(ring("first"))
            assertEquals("first", awaitItem()?.ringId)

            store.setActive(ring("first").copy(state = RingState.SNOOZED, snoozeCount = 1, snoozedUntil = 42L))
            val snoozed = awaitItem()
            assertEquals(RingState.SNOOZED, snoozed?.state)
            assertEquals(42L, snoozed?.snoozedUntil)

            store.enqueue(ring("waiting")) // queue changes do not re-emit the same active ring
            store.setActive(ring("second"))
            assertEquals("second", awaitItem()?.ringId)

            store.setActive(null)
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, database.db.ringState().queued().size)
        assertNull(database.db.ringState().getActive())
    }

    @Test
    fun ringStateSurvivesProcessDeath() = runTest {
        val first = AlarmClockDatabase.open(context)
        RoomRingStateStore(first).apply {
            setActive(ring("ringing").copy(state = RingState.MISSION, currentMissionIndex = 1))
            enqueue(ring("next-1"))
            enqueue(ring("next-2"))
        }
        first.close()

        val reopened = AlarmClockDatabase.open(context)
        try {
            val store = RoomRingStateStore(reopened)
            assertEquals(RingState.MISSION, store.getActive()?.state)
            assertEquals(1, store.getActive()?.currentMissionIndex)
            assertEquals(listOf("next-1", "next-2"), store.queue().map { it.ringId })
            assertEquals("next-1", store.dequeue()?.ringId)
        } finally {
            reopened.close()
        }
    }

    @Test
    fun unreadableQueuedRowsAreDiscardedInsteadOfBlockingTheQueue() = runTest {
        val store = RoomRingStateStore(database.db)
        database.db.ringState().insert(RingStateEntity(slot = RingStateEntity.SLOT_QUEUED, ringId = "broken", json = "{oops"))
        store.enqueue(ring("good"))

        assertEquals(listOf("good"), store.queue().map { it.ringId })
        assertEquals("good", store.dequeue()?.ringId)
        assertNull(store.dequeue())
        assertTrue(database.db.ringState().queued().isEmpty())
    }

    // --- ScheduleLedger ------------------------------------------------------------------------

    private fun trigger(requestCode: Int, at: Long, kind: TriggerKind = TriggerKind.RING, extra: String? = null) =
        ScheduledTrigger(requestCode = requestCode, kind = kind, sourceId = requestCode.toLong(), triggerAt = at, occurrenceAt = at, extra = extra)

    @Test
    fun ledgerPutGetRemove() = runTest {
        val ledger = RoomScheduleLedger(database.db)
        ledger.put(trigger(10, 5_000, TriggerKind.PRE_ALARM, extra = "UPCOMING_NOTICE"))
        ledger.put(trigger(11, 1_000))

        assertEquals(trigger(10, 5_000, TriggerKind.PRE_ALARM, extra = "UPCOMING_NOTICE"), ledger.get(10))
        assertEquals(listOf(11, 10), ledger.all().map { it.requestCode })

        ledger.put(trigger(10, 500, TriggerKind.SNOOZE_END)) // same request code: replaced
        assertEquals(listOf(10, 11), ledger.all().map { it.requestCode })
        assertEquals(TriggerKind.SNOOZE_END, ledger.get(10)?.kind)

        ledger.remove(10)
        ledger.remove(999) // unknown: no-op
        assertNull(ledger.get(10))
        assertEquals(listOf(11), ledger.all().map { it.requestCode })
    }

    @Test
    fun ledgerReplaceAllIsAtomicAndLastDuplicateWins() = runTest {
        val ledger = RoomScheduleLedger(database.db)
        ledger.put(trigger(1, 100))
        ledger.put(trigger(2, 200))

        ledger.replaceAll(listOf(trigger(3, 300), trigger(4, 50), trigger(3, 400, TriggerKind.TIMER)))

        val all = ledger.all()
        assertEquals(listOf(4, 3), all.map { it.requestCode })
        assertEquals(TriggerKind.TIMER, all.last().kind)
        assertNull(ledger.get(1))

        ledger.replaceAll(emptyList())
        assertTrue(ledger.all().isEmpty())
    }
}
