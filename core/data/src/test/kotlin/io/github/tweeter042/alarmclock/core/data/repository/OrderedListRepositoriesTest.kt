package io.github.tweeter042.alarmclock.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.tweeter042.alarmclock.core.data.InMemoryDatabaseRule
import io.github.tweeter042.alarmclock.core.model.entity.MorningRoutineItem
import io.github.tweeter042.alarmclock.core.model.entity.WorldClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** World clocks and morning-routine items share the "user order" behaviour. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class OrderedListRepositoriesTest {

    @get:Rule
    val database = InMemoryDatabaseRule()

    @Test
    fun worldClocksAppendAndReorder() = runTest {
        val repository = RoomWorldClockRepository(database.db)
        val tokyo = repository.upsert(WorldClock(zoneId = "Asia/Tokyo"))
        val london = repository.upsert(WorldClock(zoneId = "Europe/London"))
        val newYork = repository.upsert(WorldClock(zoneId = "America/New_York", label = "NYC"))

        repository.observe().test {
            assertEquals(listOf(tokyo, london, newYork), awaitItem().map { it.id })

            repository.reorder(listOf(newYork, tokyo, london))
            val reordered = awaitItem()
            assertEquals(listOf(newYork, tokyo, london), reordered.map { it.id })
            assertEquals(listOf(0, 1, 2), reordered.map { it.sortOrder })

            // Unknown and duplicate ids are ignored; unmentioned clocks keep their order after the listed ones.
            repository.reorder(listOf(london, 999L, london))
            assertEquals(listOf(london, newYork, tokyo), awaitItem().map { it.id })

            repository.delete(newYork)
            assertEquals(listOf(london, tokyo), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun reorderWithTheCurrentOrderWritesNothing() = runTest {
        val repository = RoomWorldClockRepository(database.db)
        val a = repository.upsert(WorldClock(zoneId = "Asia/Bangkok"))
        val b = repository.upsert(WorldClock(zoneId = "Asia/Seoul"))
        repository.observe().test {
            awaitItem()
            repository.reorder(listOf(a, b))
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun updatingAClockKeepsItsPosition() = runTest {
        val repository = RoomWorldClockRepository(database.db)
        repository.upsert(WorldClock(zoneId = "Asia/Tokyo"))
        val second = repository.upsert(WorldClock(zoneId = "Europe/Paris"))
        val stored = repository.observe().first().last()

        repository.upsert(stored.copy(label = "Paris"))

        val clocks = repository.observe().first()
        assertEquals(second, clocks.last().id)
        assertEquals("Paris", clocks.last().label)
    }

    @Test
    fun routineItemsAppendReorderAndDelete() = runTest {
        val repository = RoomRoutineRepository(database.db)
        val water = repository.upsert(MorningRoutineItem(text = "Drink water"))
        val stretch = repository.upsert(MorningRoutineItem(text = "Stretch"))
        val plan = repository.upsert(MorningRoutineItem(text = "Plan the day", enabled = false))

        repository.observeItems().test {
            val items = awaitItem()
            assertEquals(listOf(water, stretch, plan), items.map { it.id })
            assertEquals(listOf(0, 1, 2), items.map { it.sortOrder })

            repository.reorder(listOf(plan))
            assertEquals(listOf(plan, water, stretch), awaitItem().map { it.id })

            repository.delete(water)
            val remaining = awaitItem()
            assertEquals(listOf(plan, stretch), remaining.map { it.id })
            assertFalse(remaining.first().enabled)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
