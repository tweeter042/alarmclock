package io.github.tweeter042.alarmclock.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.tweeter042.alarmclock.core.data.InMemoryDatabaseRule
import io.github.tweeter042.alarmclock.core.model.entity.Holiday
import io.github.tweeter042.alarmclock.core.model.entity.HolidaySource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class HolidayRepositoryTest {

    @get:Rule
    val database = InMemoryDatabaseRule()

    private lateinit var repository: RoomHolidayRepository

    @Before
    fun setUp() {
        repository = RoomHolidayRepository(database.db)
    }

    private fun holiday(date: String, name: String, source: HolidaySource = HolidaySource.CUSTOM, enabled: Boolean = true) =
        Holiday(date = LocalDate.parse(date), nameEn = name, nameTh = "$name (th)", source = source, enabled = enabled)

    @Test
    fun betweenIsInclusiveAndSortedByDate() = runTest {
        repository.upsert(holiday("2026-12-31", "New Year's Eve"))
        repository.upsert(holiday("2026-12-05", "Father's Day"))
        repository.upsert(holiday("2026-12-10", "Constitution Day"))
        repository.upsert(holiday("2027-01-01", "New Year's Day"))
        repository.upsert(holiday("2026-11-30", "Before"))

        val december = repository.between(LocalDate.parse("2026-12-05"), LocalDate.parse("2026-12-31"))

        assertEquals(listOf("Father's Day", "Constitution Day", "New Year's Eve"), december.map { it.nameEn })
        assertTrue(december.all { it.id > 0L })
        assertTrue(repository.between(LocalDate.parse("2026-12-31"), LocalDate.parse("2026-12-01")).isEmpty())
        assertEquals(1, repository.between(LocalDate.parse("2027-01-01"), LocalDate.parse("2027-01-01")).size)
    }

    @Test
    fun observeAllEmitsOnChanges() = runTest {
        repository.observeAll().test {
            assertTrue(awaitItem().isEmpty())
            val id = repository.upsert(holiday("2026-04-13", "Songkran"))
            assertEquals(listOf("Songkran"), awaitItem().map { it.nameEn })
            repository.upsert(holiday("2026-04-13", "Songkran", enabled = false).copy(id = id))
            assertFalse(awaitItem().single().enabled)
            repository.delete(id)
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun replaceSourceOnlyTouchesThatSource() = runTest {
        repository.upsert(holiday("2026-12-05", "Built-in", HolidaySource.BUILT_IN))
        repository.upsert(holiday("2026-12-24", "Custom", HolidaySource.CUSTOM))
        repository.upsert(holiday("2026-02-17", "Old calendar entry", HolidaySource.CALENDAR))

        repository.replaceSource(
            HolidaySource.CALENDAR,
            listOf(
                holiday("2026-05-01", "Labour Day", HolidaySource.CALENDAR),
                // Wrong source and an id: forced to CALENDAR and inserted as a new row.
                holiday("2026-07-28", "King's Birthday", HolidaySource.CUSTOM).copy(id = 1L),
            ),
        )

        val all = repository.between(LocalDate.parse("2000-01-01"), LocalDate.parse("2030-01-01"))
        assertEquals(
            setOf("Built-in" to HolidaySource.BUILT_IN, "Custom" to HolidaySource.CUSTOM, "Labour Day" to HolidaySource.CALENDAR, "King's Birthday" to HolidaySource.CALENDAR),
            all.map { it.nameEn to it.source }.toSet(),
        )
        assertEquals("ids stay unique", all.size, all.map { it.id }.toSet().size)
    }

    @Test
    fun replaceSourceKeepsUserDisabledHolidaysOffAndDropsDuplicates() = runTest {
        repository.replaceSource(
            HolidaySource.CALENDAR,
            listOf(holiday("2026-02-17", "Chinese New Year", HolidaySource.CALENDAR), holiday("2026-05-01", "Labour Day", HolidaySource.CALENDAR)),
        )
        val chinese = repository.between(LocalDate.parse("2026-02-17"), LocalDate.parse("2026-02-17")).single()
        repository.upsert(chinese.copy(enabled = false))

        // Periodic re-import: the calendar knows nothing about "disabled" and may repeat events.
        repository.replaceSource(
            HolidaySource.CALENDAR,
            listOf(
                holiday("2026-02-17", "Chinese New Year", HolidaySource.CALENDAR),
                holiday("2026-02-17", "Chinese New Year", HolidaySource.CALENDAR),
                holiday("2026-05-01", "Labour Day", HolidaySource.CALENDAR),
            ),
        )

        val after = repository.between(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"))
        assertEquals(listOf("Chinese New Year", "Labour Day"), after.map { it.nameEn })
        assertFalse(after[0].enabled)
        assertTrue(after[1].enabled)
    }

    @Test
    fun replaceSourceWithEmptyListClearsIt() = runTest {
        repository.replaceSource(HolidaySource.CALENDAR, listOf(holiday("2026-05-01", "Labour Day", HolidaySource.CALENDAR)))
        repository.upsert(holiday("2026-12-24", "Custom"))

        repository.replaceSource(HolidaySource.CALENDAR, emptyList())

        assertEquals(listOf("Custom"), repository.between(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31")).map { it.nameEn })
    }
}
