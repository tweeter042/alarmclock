package io.github.tweeter042.alarmclock.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.tweeter042.alarmclock.core.data.db.AlarmClockDatabase
import io.github.tweeter042.alarmclock.core.model.alarm.ActiveRing
import io.github.tweeter042.alarmclock.core.model.alarm.Alarm
import io.github.tweeter042.alarmclock.core.model.alarm.MissionConfig
import io.github.tweeter042.alarmclock.core.model.alarm.MissionType
import io.github.tweeter042.alarmclock.core.model.alarm.RepeatRule
import io.github.tweeter042.alarmclock.core.model.alarm.RingKind
import io.github.tweeter042.alarmclock.core.model.repository.SettingsRepository
import io.github.tweeter042.alarmclock.core.model.settings.AppSettings
import io.github.tweeter042.alarmclock.core.model.time.AlarmTime
import io.github.tweeter042.alarmclock.core.model.time.IsoDays
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.rules.ExternalResource

/** A controllable clock for `() -> Long` parameters. */
class TestClock(var now: Long = 1_795_000_000_000L) : () -> Long {
    override fun invoke(): Long = now

    fun advance(millis: Long) {
        now += millis
    }
}

/** Fresh in-memory database per test (same callbacks as production, so seeds are present). */
class InMemoryDatabaseRule : ExternalResource() {
    internal lateinit var db: AlarmClockDatabase
        private set

    val context: Context get() = ApplicationProvider.getApplicationContext()

    override fun before() {
        db = AlarmClockDatabase.inMemory(context)
    }

    override fun after() {
        db.close()
    }
}

/** In-memory [SettingsRepository] for components that only need settings as a dependency. */
class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> get() = state
    override suspend fun current(): AppSettings = state.value
    override suspend fun update(transform: (AppSettings) -> AppSettings) = state.update(transform)
}

fun alarm(
    hour: Int,
    minute: Int,
    label: String = "",
    groupId: Long? = null,
    missions: List<MissionConfig> = emptyList(),
    id: Long = 0L,
): Alarm = Alarm(
    id = id,
    time = AlarmTime(hour, minute),
    label = label,
    repeat = RepeatRule.Weekly(IsoDays.WEEKDAYS),
    groupId = groupId,
    missions = missions,
)

fun barcodeMission(vararg codeIds: Long) = MissionConfig(type = MissionType.BARCODE, targetIds = codeIds.toList())

fun photoMission(vararg photoIds: Long) = MissionConfig(type = MissionType.PHOTO, targetIds = photoIds.toList())

fun ring(ringId: String, sourceId: Long = 1L, label: String = ringId): ActiveRing = ActiveRing(
    ringId = ringId,
    kind = RingKind.ALARM,
    sourceId = sourceId,
    alarm = Alarm(id = sourceId, label = label),
    occurrenceAt = 1_795_000_000_000L,
    startedAt = 1_795_000_000_000L,
)
