package io.github.tweeter042.alarmclock.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.tweeter042.alarmclock.core.data.InMemoryDatabaseRule
import io.github.tweeter042.alarmclock.core.data.TestClock
import io.github.tweeter042.alarmclock.core.model.entity.PhotoTarget
import io.github.tweeter042.alarmclock.core.model.entity.RegisteredCode
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
class MissionAssetRepositoryTest {

    @get:Rule
    val database = InMemoryDatabaseRule()

    private val clock = TestClock()
    private lateinit var repository: RoomMissionAssetRepository

    @Before
    fun setUp() {
        repository = RoomMissionAssetRepository(database.db, clock)
    }

    @Test
    fun codesKeepRegistrationOrderAndCreatedAt() = runTest {
        repository.observeCodes().test {
            assertTrue(awaitItem().isEmpty())
            val bathroom = repository.upsertCode(RegisteredCode(label = "Bathroom", value = "QR-1", format = 256))
            assertEquals(listOf(bathroom), awaitItem().map { it.id })
            val kitchen = repository.upsertCode(RegisteredCode(label = "Kitchen", value = "4006381333931", format = 32))
            val codes = awaitItem()
            assertEquals(listOf(bathroom, kitchen), codes.map { it.id })
            assertTrue(codes.all { it.createdAt == clock.now })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getCodesFollowsRequestedOrderAndSkipsUnknownIds() = runTest {
        val a = repository.upsertCode(RegisteredCode(label = "a", value = "A"))
        val b = repository.upsertCode(RegisteredCode(label = "b", value = "B"))
        val c = repository.upsertCode(RegisteredCode(label = "c", value = "C"))

        assertEquals(listOf(c, a), repository.getCodes(listOf(c, 404L, a, c)).map { it.id })
        assertTrue(repository.getCodes(emptyList()).isEmpty())
        assertEquals(listOf(b), repository.getCodes(listOf(b)).map { it.id })
    }

    @Test
    fun getCodesHandlesMoreIdsThanSqliteVariables() = runTest {
        val ids = (1..5).map { repository.upsertCode(RegisteredCode(label = "c$it", value = "$it")) }
        val request = (1L..1_500L).toList().reversed()

        assertEquals(ids.reversed(), repository.getCodes(request).map { it.id })
    }

    @Test
    fun updatingACodeKeepsCreatedAt() = runTest {
        val id = repository.upsertCode(RegisteredCode(label = "Desk", value = "X"))
        val created = clock.now
        clock.advance(60_000)
        repository.upsertCode(RegisteredCode(id = id, label = "Desk drawer", value = "X"))

        val stored = repository.getCodes(listOf(id)).single()
        assertEquals("Desk drawer", stored.label)
        assertEquals(created, stored.createdAt)
        repository.deleteCode(id)
        assertTrue(repository.getCodes(listOf(id)).isEmpty())
    }

    @Test
    fun photoTargetsRoundTripWithLabels() = runTest {
        val mug = repository.upsertPhotoTarget(PhotoTarget(label = "Mug", imagePath = "/data/photo/mug.jpg", labels = mapOf("Cup" to 0.91f, "Tableware" to 0.7f)))
        val plant = repository.upsertPhotoTarget(PhotoTarget(label = "Plant", imagePath = "/data/photo/plant.jpg"))

        repository.observePhotoTargets().test {
            val targets = awaitItem()
            assertEquals(listOf(mug, plant), targets.map { it.id })
            assertEquals(0.91f, targets.first().labels.getValue("Cup"), 0.0001f)
            assertTrue(targets.all { it.createdAt == clock.now })
            repository.deletePhotoTarget(mug)
            assertEquals(listOf(plant), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf("Plant"), repository.getPhotoTargets(listOf(mug, plant)).map { it.label })
    }
}
