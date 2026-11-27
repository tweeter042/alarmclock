package io.github.tweeter042.alarmclock.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.tweeter042.alarmclock.core.data.InMemoryDatabaseRule
import io.github.tweeter042.alarmclock.core.data.TestClock
import io.github.tweeter042.alarmclock.core.data.alarm
import io.github.tweeter042.alarmclock.core.data.db.AlarmEntity
import io.github.tweeter042.alarmclock.core.model.alarm.Alarm
import io.github.tweeter042.alarmclock.core.model.entity.AlarmGroup
import io.github.tweeter042.alarmclock.core.model.time.AlarmTime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AlarmRepositoryTest {

    @get:Rule
    val database = InMemoryDatabaseRule()

    private val clock = TestClock()
    private lateinit var repository: RoomAlarmRepository

    @Before
    fun setUp() {
        repository = RoomAlarmRepository(database.db, clock)
    }

    @Test
    fun insertAssignsIdAndTimestamps() = runTest {
        val id = repository.upsert(alarm(7, 30, "Work"))

        val stored = repository.getAlarm(id)!!
        assertTrue(id > 0L)
        assertEquals(id, stored.id)
        assertEquals("Work", stored.label)
        assertEquals(clock.now, stored.createdAt)
        assertEquals(clock.now, stored.updatedAt)
    }

    @Test
    fun updateKeepsIdAndCreatedAtAndBumpsUpdatedAt() = runTest {
        val id = repository.upsert(alarm(7, 30, "Work"))
        val created = clock.now
        clock.advance(60_000)

        // Callers often rebuild the model without createdAt: the stored creation time is kept.
        val returned = repository.upsert(alarm(8, 0, "Later", id = id))

        val stored = repository.getAlarm(id)!!
        assertEquals(id, returned)
        assertEquals(AlarmTime(8, 0), stored.time)
        assertEquals(created, stored.createdAt)
        assertEquals(clock.now, stored.updatedAt)
        assertEquals(1, repository.getAlarms().size)
    }

    @Test
    fun explicitCreatedAtIsKept() = runTest {
        val id = repository.upsert(Alarm(createdAt = 42L))
        assertEquals(42L, repository.getAlarm(id)!!.createdAt)
    }

    @Test
    fun negativeIdIsTreatedAsNew() = runTest {
        val id = repository.upsert(Alarm(id = -5L, label = "new"))
        assertTrue(id > 0L)
        assertEquals("new", repository.getAlarm(id)!!.label)
    }

    @Test
    fun observeAlarmsSortsByTimeOfDayThenId() = runTest {
        repository.observeAlarms().test {
            assertEquals(emptyList<Alarm>(), awaitItem())

            val late = repository.upsert(alarm(22, 0, "late"))
            assertEquals(listOf("late"), awaitItem().map { it.label })

            val early = repository.upsert(alarm(5, 0, "early"))
            assertEquals(listOf("early", "late"), awaitItem().map { it.label })

            val alsoEarly = repository.upsert(alarm(5, 0, "also early"))
            val items = awaitItem()
            assertEquals(listOf(early, alsoEarly, late), items.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeAlarmEmitsChangesAndNullAfterDelete() = runTest {
        val id = repository.upsert(alarm(6, 0, "a"))
        repository.observeAlarm(id).test {
            assertEquals("a", awaitItem()?.label)
            repository.setEnabled(id, false)
            assertFalse(awaitItem()!!.enabled)
            repository.delete(id)
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeAlarmsIsDistinct() = runTest {
        val id = repository.upsert(alarm(6, 0, "a"))
        repository.observeAlarms().test {
            assertEquals(1, awaitItem().size)
            // Touching another table invalidates nothing relevant; an identical update is skipped.
            repository.update(id) { it }
            repository.upsertGroup(AlarmGroup(name = "g"))
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun updateIsTransactionalAndAtomic() = runTest {
        val id = repository.upsert(Alarm(label = ""))

        (1..25).map { async { repository.update(id) { it.copy(label = it.label + "x") } } }.awaitAll()

        assertEquals("x".repeat(25), repository.getAlarm(id)!!.label)
    }

    @Test
    fun updateForcesIdAndStampsUpdatedAt() = runTest {
        val id = repository.upsert(alarm(6, 0))
        val created = repository.getAlarm(id)!!.createdAt
        clock.advance(1_000)

        repository.update(id) { it.copy(id = 999L, label = "changed", createdAt = 1L) }

        val stored = repository.getAlarm(id)!!
        assertEquals("changed", stored.label)
        assertEquals(created, stored.createdAt)
        assertEquals(clock.now, stored.updatedAt)
        assertNull(repository.getAlarm(999L))
    }

    @Test
    fun updateOfMissingAlarmIsNoOp() = runTest {
        var called = false
        repository.update(12345L) { called = true; it }
        assertFalse(called)
        assertTrue(repository.getAlarms().isEmpty())
    }

    @Test
    fun setEnabledTogglesBothColumnAndModel() = runTest {
        val id = repository.upsert(alarm(6, 0))
        repository.setEnabled(id, false)
        assertFalse(repository.getAlarm(id)!!.enabled)
        assertFalse(database.db.alarms().get(id)!!.enabled)
        repository.setEnabled(id, true)
        assertTrue(repository.getAlarm(id)!!.enabled)
    }

    @Test
    fun deleteRemovesOnlyThatAlarm() = runTest {
        val a = repository.upsert(alarm(6, 0))
        val b = repository.upsert(alarm(7, 0))
        repository.delete(a)
        repository.delete(98765L) // unknown id: no-op
        assertEquals(listOf(b), repository.getAlarms().map { it.id })
    }

    @Test
    fun groupsAreSortedAndNewGroupsAppended() = runTest {
        val first = repository.upsertGroup(AlarmGroup(name = "first"))
        val second = repository.upsertGroup(AlarmGroup(name = "second"))
        val pinned = repository.upsertGroup(AlarmGroup(name = "pinned", sortOrder = -1))

        val groups = repository.getGroups()
        assertEquals(listOf(pinned, first, second), groups.map { it.id })
        assertEquals(listOf(-1, 0, 1), groups.map { it.sortOrder })

        repository.observeGroups().test {
            assertEquals(listOf("pinned", "first", "second"), awaitItem().map { it.name })
            repository.upsertGroup(groups.first { it.id == second }.copy(sortOrder = -5, name = "top"))
            assertEquals(listOf("top", "pinned", "first"), awaitItem().map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deleteGroupKeepsItsAlarmsOutsideAnyGroup() = runTest {
        val work = repository.upsertGroup(AlarmGroup(name = "Work"))
        val gym = repository.upsertGroup(AlarmGroup(name = "Gym"))
        val inWork1 = repository.upsert(alarm(6, 0, groupId = work))
        val inWork2 = repository.upsert(alarm(6, 30, groupId = work))
        val inGym = repository.upsert(alarm(18, 0, groupId = gym))
        clock.advance(5_000)

        repository.deleteGroup(work)

        assertEquals(listOf(gym), repository.getGroups().map { it.id })
        val alarms = repository.getAlarms().associateBy { it.id }
        assertEquals(3, alarms.size)
        assertNull(alarms.getValue(inWork1).groupId)
        assertNull(alarms.getValue(inWork2).groupId)
        assertEquals(clock.now, alarms.getValue(inWork1).updatedAt)
        assertEquals(gym, alarms.getValue(inGym).groupId)
        assertNull("column updated together with the JSON", database.db.alarms().get(inWork1)!!.groupId)
        assertTrue(database.db.alarms().getInGroup(work).isEmpty())
    }

    @Test
    fun deleteGroupAlsoDetachesUnreadableRows() = runTest {
        val group = repository.upsertGroup(AlarmGroup(name = "g"))
        val broken = database.db.alarms().upsert(
            AlarmEntity(enabled = true, groupId = group, minuteOfDay = 60, updatedAt = 0L, json = "{broken"),
        )

        repository.deleteGroup(group)

        assertNull(database.db.alarms().get(broken)!!.groupId)
        assertTrue(repository.getGroups().isEmpty())
    }

    @Test
    fun deleteGroupNotifiesAlarmObserversOnce() = runTest {
        val group = repository.upsertGroup(AlarmGroup(name = "g"))
        repository.upsert(alarm(6, 0, groupId = group))
        repository.upsert(alarm(7, 0, groupId = group))

        repository.observeAlarms().test {
            assertTrue(awaitItem().all { it.groupId == group })
            repository.deleteGroup(group)
            assertTrue(awaitItem().all { it.groupId == null })
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun setGroupEnabledDoesNotTouchAlarms() = runTest {
        val group = repository.upsertGroup(AlarmGroup(name = "g"))
        val id = repository.upsert(alarm(6, 0, groupId = group))

        repository.setGroupEnabled(group, false)
        assertFalse(repository.getGroups().single().enabled)
        assertTrue(repository.getAlarm(id)!!.enabled)

        repository.setGroupEnabled(group, true)
        assertTrue(repository.getGroups().single().enabled)
        repository.setGroupEnabled(4242L, false) // unknown: no-op
    }

    @Test
    fun alarmsNeverReferenceMissingGroups() = runTest {
        val group = repository.upsertGroup(AlarmGroup(name = "g"))
        val id = repository.upsert(alarm(6, 0, groupId = 777L))
        assertNull(repository.getAlarm(id)!!.groupId)

        repository.update(id) { it.copy(groupId = group) }
        assertEquals(group, repository.getAlarm(id)!!.groupId)

        repository.update(id) { it.copy(groupId = 888L) }
        assertNull(repository.getAlarm(id)!!.groupId)
    }

    @Test
    fun upsertWithUnknownIdInsertsWithThatId() = runTest {
        val id = repository.upsert(alarm(9, 0, "restored", id = 50L))
        assertEquals(50L, id)
        assertNotEquals(0L, repository.getAlarm(50L)!!.createdAt)
        assertEquals(repository.observeAlarms().first().single().id, 50L)
    }
}
