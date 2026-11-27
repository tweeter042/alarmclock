package io.github.tweeter042.alarmclock.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.tweeter042.alarmclock.core.data.InMemoryDatabaseRule
import io.github.tweeter042.alarmclock.core.model.alarm.RingKind
import io.github.tweeter042.alarmclock.core.model.alarm.RingOutcome
import io.github.tweeter042.alarmclock.core.model.entity.AlarmEvent
import io.github.tweeter042.alarmclock.core.model.entity.SleepLog
import io.github.tweeter042.alarmclock.core.model.entity.SleepSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class EventLogRepositoryTest {

    @get:Rule
    val database = InMemoryDatabaseRule()

    private lateinit var repository: RoomEventLogRepository

    @Before
    fun setUp() {
        repository = RoomEventLogRepository(database.db)
    }

    private fun event(at: Long, sourceId: Long = 1L, kind: RingKind = RingKind.ALARM) =
        AlarmEvent(kind = kind, sourceId = sourceId, label = "e$at", occurrenceAt = at)

    @Test
    fun insertReturnsIdAndUpdateChangesTheEvent() = runTest {
        val id = repository.insert(event(1_000))
        assertTrue(id > 0L)

        val stored = repository.between(0, 2_000).single()
        assertEquals(id, stored.id)

        repository.update(stored.copy(outcome = RingOutcome.DISMISSED, snoozeCount = 2, endedAt = 1_500))
        val updated = repository.between(0, 2_000).single()
        assertEquals(RingOutcome.DISMISSED, updated.outcome)
        assertEquals(2, updated.snoozeCount)
        assertEquals(1_500L, updated.endedAt)
    }

    @Test
    fun updateOfUnsavedOrMissingEventIsIgnored() = runTest {
        repository.update(event(1_000)) // id 0
        repository.update(event(1_000).copy(id = 77L)) // unknown id: not inserted
        assertTrue(repository.between(Long.MIN_VALUE, Long.MAX_VALUE).isEmpty())
    }

    @Test
    fun betweenIsHalfOpenAndOrdered() = runTest {
        listOf(3_000L, 1_000L, 2_000L, 4_000L).forEach { repository.insert(event(it)) }

        assertEquals(listOf(1_000L, 2_000L, 3_000L), repository.between(1_000, 4_000).map { it.occurrenceAt })
        assertTrue(repository.between(5_000, 6_000).isEmpty())
        assertTrue(repository.between(2_000, 2_000).isEmpty())
    }

    @Test
    fun observeBetweenEmitsOnlyForChanges() = runTest {
        repository.observeBetween(1_000, 10_000).test {
            assertTrue(awaitItem().isEmpty())
            repository.insert(event(5_000, kind = RingKind.TIMER))
            assertEquals(listOf(RingKind.TIMER), awaitItem().map { it.kind })
            repository.insert(event(20_000)) // outside the window: same list, filtered by distinctUntilChanged
            repository.insert(event(6_000))
            assertEquals(listOf(5_000L, 6_000L), awaitItem().map { it.occurrenceAt })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun sleepLogsByWakeTime() = runTest {
        repository.observeSleepLogs(10_000, 20_000).test {
            assertTrue(awaitItem().isEmpty())
            val id = repository.insertSleepLog(SleepLog(bedtimeAt = 5_000, wakeAt = 15_000, source = SleepSource.HEALTH_CONNECT))
            val logs = awaitItem()
            assertEquals(id, logs.single().id)
            assertEquals(SleepSource.HEALTH_CONNECT, logs.single().source)
            repository.insertSleepLog(SleepLog(wakeAt = 20_000)) // excluded (half-open)
            repository.insertSleepLog(SleepLog(wakeAt = 10_000))
            assertEquals(listOf(10_000L, 15_000L), awaitItem().map { it.wakeAt })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun pruneDeletesOldEventsAndSleepLogs() = runTest {
        listOf(1_000L, 2_000L, 3_000L).forEach { repository.insert(event(it)) }
        listOf(1_500L, 2_500L).forEach { repository.insertSleepLog(SleepLog(wakeAt = it)) }

        repository.prune(beforeMillis = 2_000)

        assertEquals(listOf(2_000L, 3_000L), repository.between(0, Long.MAX_VALUE).map { it.occurrenceAt })
        repository.observeSleepLogs(0, Long.MAX_VALUE).test {
            assertEquals(listOf(2_500L), awaitItem().map { it.wakeAt })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
