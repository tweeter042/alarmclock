package io.github.tweeter042.alarmclock.core.alarm.testing

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.tweeter042.alarmclock.core.alarm.AlarmEngineHost
import io.github.tweeter042.alarmclock.core.alarm.DefaultAlarmEngine
import io.github.tweeter042.alarmclock.core.alarm.EngineClock
import io.github.tweeter042.alarmclock.core.alarm.EngineDependencies
import io.github.tweeter042.alarmclock.core.data.DataGraph
import io.github.tweeter042.alarmclock.core.model.alarm.ActiveRing
import io.github.tweeter042.alarmclock.core.model.alarm.Alarm
import io.github.tweeter042.alarmclock.core.model.alarm.NextAlarm
import io.github.tweeter042.alarmclock.core.model.alarm.PreAlarmEvent
import io.github.tweeter042.alarmclock.core.model.alarm.RingOutcome
import io.github.tweeter042.alarmclock.core.model.engine.AlarmEngineHooks
import io.github.tweeter042.alarmclock.core.model.engine.RingUiLauncher
import io.github.tweeter042.alarmclock.core.model.service.BuildInfo
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Collections

/** A wall clock the test controls. */
class TestClock(var now: Long, var zoneId: ZoneId = ZoneId.of("Asia/Bangkok")) : EngineClock {
    override fun now(): Long = now
    override fun zone(): ZoneId = zoneId

    fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zoneId).toInstant().toEpochMilli()

    fun local(): ZonedDateTime = Instant.ofEpochMilli(now).atZone(zoneId)
}

/** Records every hook call. */
class RecordingHooks : AlarmEngineHooks {
    val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val started: MutableList<ActiveRing> = Collections.synchronizedList(mutableListOf())
    val finished: MutableList<Pair<ActiveRing, RingOutcome>> = Collections.synchronizedList(mutableListOf())
    @Volatile var lastNext: NextAlarm? = null
    @Volatile var scheduleChanges = 0

    override suspend fun onPreAlarm(event: PreAlarmEvent, alarm: Alarm) {
        calls += "preAlarm:${event.kind}"
    }

    override suspend fun onRingStarted(ring: ActiveRing) {
        calls += "started:${ring.ringId}"
        started += ring
    }

    override suspend fun onRingUpdated(ring: ActiveRing) {
        calls += "updated:${ring.ringId}:${ring.state}"
    }

    override suspend fun onRingFinished(ring: ActiveRing, outcome: RingOutcome) {
        calls += "finished:${ring.ringId}:$outcome"
        finished += ring to outcome
    }

    override suspend fun onScheduleChanged(next: NextAlarm?, alarms: List<Alarm>) {
        lastNext = next
        scheduleChanges++
    }
}

object TestRingUi : RingUiLauncher {
    const val RING_ACTIVITY = "io.github.tweeter042.alarmclock.test.RingActivity"
    const val MAIN_ACTIVITY = "io.github.tweeter042.alarmclock.test.MainActivity"
    const val EXTRA_RING_ID = "test.ringId"

    override fun ringScreenIntent(context: Context, ringId: String): Intent =
        Intent(Intent.ACTION_MAIN).setClassName(context.packageName, RING_ACTIVITY).putExtra(EXTRA_RING_ID, ringId)

    override fun sunriseIntent(context: Context, alarmId: Long, occurrenceAt: Long): Intent =
        Intent(Intent.ACTION_MAIN).setClassName(context.packageName, RING_ACTIVITY).putExtra("sunrise", alarmId)

    override fun mainIntent(context: Context): Intent = Intent(Intent.ACTION_MAIN).setClassName(context.packageName, MAIN_ACTIVITY)

    override fun alarmEditorIntent(context: Context, alarmId: Long): Intent = mainIntent(context).putExtra("alarmId", alarmId)
}

/** The Robolectric Application: owns the DataGraph and the engine, like :app does. */
class TestEngineApp : Application(), AlarmEngineHost {
    val hooks = RecordingHooks()
    var clock = TestClock(ZonedDateTime.of(2026, 11, 27, 8, 0, 0, 0, ZoneId.of("Asia/Bangkok")).toInstant().toEpochMilli())
    var dependencies: (EngineDependencies) -> EngineDependencies = { it }

    val data: DataGraph by lazy { DataGraph(this, "test") }

    private var engine: DefaultAlarmEngine? = null

    override val alarmEngine: DefaultAlarmEngine
        get() = engine ?: synchronized(this) {
            engine ?: DefaultAlarmEngine(
                this,
                data,
                dependencies(EngineDependencies(hooks, TestRingUi, BuildInfo("test", "1.0", 1, "2026-11-28", "", true))),
                clock,
            ).also { engine = it }
        }

    /** A second engine instance (simulates a new process) sharing the same storage. */
    fun replaceEngine(): DefaultAlarmEngine {
        engine?.scope?.cancel()
        engine = null
        return alarmEngine
    }

    fun shutdown() {
        engine?.scope?.cancel()
        engine = null
    }
}

/**
 * Fresh storage per test: core:data keeps the database and the DataStores as process singletons,
 * so they are closed between tests (through reflection: their reset helpers are module-internal).
 */
class EngineTestRule : ExternalResource() {
    val app: TestEngineApp get() = ApplicationProvider.getApplicationContext()
    val alarmManager: AlarmManager get() = app.getSystemService(AlarmManager::class.java)
    val shadowAlarms: ShadowAlarmManager get() = shadowOf(alarmManager)

    override fun before() {
        resetDataSingletons()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
    }

    override fun after() {
        app.shutdown()
        resetDataSingletons()
    }

    fun startEngine(): DefaultAlarmEngine {
        val engine = app.alarmEngine
        engine.start()
        runBlocking { engine.awaitStartup() }
        return engine
    }

    companion object {
        fun resetDataSingletons() {
            runCatching {
                val database = Class.forName("io.github.tweeter042.alarmclock.core.data.db.AlarmClockDatabase")
                val companion = database.getField("Companion").get(null)
                companion.javaClass.getMethod("closeInstance").invoke(companion)
            }.onFailure { throw IllegalStateException("Cannot reset the database singleton", it) }
            runCatching {
                val stores = Class.forName("io.github.tweeter042.alarmclock.core.data.datastore.ProcessDataStores")
                stores.getMethod("reset").invoke(stores.getField("INSTANCE").get(null))
            }.onFailure { throw IllegalStateException("Cannot reset the DataStore singletons", it) }
        }
    }
}

/** Retries [block] until it stops throwing AssertionError (background coroutines, debounces). */
fun <T> eventually(timeoutMillis: Long = 8_000, block: () -> T): T {
    val deadline = System.currentTimeMillis() + timeoutMillis
    var last: Throwable? = null
    while (System.currentTimeMillis() < deadline) {
        try {
            return block()
        } catch (e: AssertionError) {
            last = e
        } catch (e: RuntimeException) {
            last = e
        }
        Thread.sleep(50)
    }
    throw AssertionError("Condition not met within $timeoutMillis ms", last)
}
