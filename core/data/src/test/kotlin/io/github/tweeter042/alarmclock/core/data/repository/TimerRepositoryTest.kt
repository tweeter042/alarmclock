package io.github.tweeter042.alarmclock.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.tweeter042.alarmclock.core.data.InMemoryDatabaseRule
import io.github.tweeter042.alarmclock.core.data.TestClock
import io.github.tweeter042.alarmclock.core.model.entity.TimerModel
import io.github.tweeter042.alarmclock.core.model.entity.TimerPreset
import io.github.tweeter042.alarmclock.core.model.entity.TimerState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TimerRepositoryTest {

    @get:Rule
    val database = InMemoryDatabaseRule()

    private val clock = TestClock()
    private lateinit var repository: RoomTimerRepository

    @Before
    fun setUp() {
        repository = RoomTimerRepository(database.db, clock)
    }

    @Test
    fun timersAreListedInCreationOrderWithCreatedAtStamped() = runTest {
        repository.observeTimers().test {
            assertTrue(awaitItem().isEmpty())

            val first = repository.upsert(TimerModel(label = "Tea", durationMs = 180_000))
            assertEquals(listOf("Tea"), awaitItem().map { it.label })
            clock.advance(1_000)
            val second = repository.upsert(TimerModel(label = "Pasta", durationMs = 600_000))
            val items = awaitItem()
            assertEquals(listOf(first, second), items.map { it.id })
            assertEquals(listOf(clock.now - 1_000, clock.now), items.map { it.createdAt })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun updatingATimerKeepsCreatedAt() = runTest {
        val id = repository.upsert(TimerModel(label = "Tea", durationMs = 180_000))
        val created = repository.getTimer(id)!!.createdAt
        clock.advance(10_000)

        val running = TimerModel(id = id, label = "Tea", durationMs = 180_000, state = TimerState.RUNNING, endAt = clock.now + 180_000)
        assertEquals(id, repository.upsert(running))

        val stored = repository.getTimer(id)!!
        assertEquals(TimerState.RUNNING, stored.state)
        assertEquals(clock.now + 180_000, stored.endAt)
        assertEquals(created, stored.createdAt)
        assertEquals(TimerState.RUNNING.name, database.db.timers().get(id)!!.state)
    }

    @Test
    fun deleteTimer() = runTest {
        val id = repository.upsert(TimerModel(durationMs = 1_000))
        repository.delete(id)
        assertNull(repository.getTimer(id))
    }

    @Test
    fun presetsAreSeededSortedAndNewOnesAppended() = runTest {
        repository.observePresets().test {
            val seeded = awaitItem()
            assertEquals(6, seeded.size)

            val added = repository.upsertPreset(TimerPreset(label = "Eggs", durationMs = 420_000))
            val withNew = awaitItem()
            assertEquals(added, withNew.last().id)
            assertEquals(seeded.maxOf { it.sortOrder } + 1, withNew.last().sortOrder)

            repository.deletePreset(seeded.first().id)
            assertEquals(6, awaitItem().size)

            repository.upsertPreset(withNew.last().copy(label = "Soft eggs", sortOrder = -1))
            assertEquals("Soft eggs", awaitItem().first().label)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun firstPresetIntoAnEmptyTableGetsSortOrderZero() = runTest {
        repository.observePresets().first().forEach { repository.deletePreset(it.id) }
        repository.upsertPreset(TimerPreset(label = "Only", durationMs = 60_000))
        assertEquals(0, repository.observePresets().first().single().sortOrder)
    }
}
