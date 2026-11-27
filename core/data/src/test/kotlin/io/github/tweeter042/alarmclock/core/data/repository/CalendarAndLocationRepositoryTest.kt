package io.github.tweeter042.alarmclock.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.tweeter042.alarmclock.core.data.InMemoryDatabaseRule
import io.github.tweeter042.alarmclock.core.data.barcodeMission
import io.github.tweeter042.alarmclock.core.model.alarm.Alarm
import io.github.tweeter042.alarmclock.core.model.entity.CalendarAlarmRule
import io.github.tweeter042.alarmclock.core.model.entity.GeofenceTrigger
import io.github.tweeter042.alarmclock.core.model.entity.LocationAlarm
import io.github.tweeter042.alarmclock.core.model.time.AlarmTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CalendarAndLocationRepositoryTest {

    @get:Rule
    val database = InMemoryDatabaseRule()

    @Test
    fun calendarRulesCrud() = runTest {
        val repository = RoomCalendarRuleRepository(database.db)
        val rule = CalendarAlarmRule(
            name = "Work meetings",
            calendarIds = listOf(3L, 9L),
            minutesBefore = 90,
            earliest = AlarmTime(5, 30),
            keyword = "standup",
            template = Alarm(label = "", missions = listOf(barcodeMission(1L))),
        )

        repository.observe().test {
            assertTrue(awaitItem().isEmpty())
            val id = repository.upsert(rule)
            val stored = awaitItem().single()
            assertEquals(rule.copy(id = id), stored)
            assertEquals(stored, repository.get(id))

            repository.upsert(stored.copy(enabled = false))
            assertFalse(awaitItem().single().enabled)
            assertFalse(database.db.calendarRules().get(id)!!.enabled)

            repository.delete(id)
            assertTrue(awaitItem().isEmpty())
            assertNull(repository.get(id))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun locationAlarmsCrudInIdOrder() = runTest {
        val repository = RoomLocationAlarmRepository(database.db)
        val station = repository.upsert(LocationAlarm(label = "Home station", latitude = 13.7563, longitude = 100.5018, radiusMeters = 300))
        val office = repository.upsert(LocationAlarm(label = "Office", latitude = 13.73, longitude = 100.52, trigger = GeofenceTrigger.EXIT, oneShot = false))

        assertEquals(listOf(station, office), repository.getAll().map { it.id })
        assertEquals(GeofenceTrigger.EXIT, repository.get(office)!!.trigger)
        assertEquals(13.7563, repository.get(station)!!.latitude, 1e-9)

        repository.upsert(repository.get(station)!!.copy(enabled = false))
        assertFalse(repository.get(station)!!.enabled)

        repository.delete(station)
        assertEquals(listOf(office), repository.getAll().map { it.id })
        repository.observe().test {
            assertEquals(listOf("Office"), awaitItem().map { it.label })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
