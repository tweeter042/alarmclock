package io.github.tweeter042.alarmclock.stub

import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.tweeter042.alarmclock.core.model.alarm.ActiveRing
import io.github.tweeter042.alarmclock.core.model.alarm.Alarm
import io.github.tweeter042.alarmclock.core.model.alarm.NextAlarm
import io.github.tweeter042.alarmclock.core.model.alarm.RingKind
import io.github.tweeter042.alarmclock.core.model.alarm.RingOutcome
import io.github.tweeter042.alarmclock.core.model.engine.AlarmEngine
import io.github.tweeter042.alarmclock.core.model.engine.HealthItem
import io.github.tweeter042.alarmclock.core.model.engine.HealthItemId
import io.github.tweeter042.alarmclock.core.model.engine.IntegrityReport
import io.github.tweeter042.alarmclock.core.model.engine.ReliabilityChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * TEMPORARY stand-ins so the app runs end to end before the engine (core:alarm) is wired in.
 * AppGraphFactory replaces them with the real implementations in the integration wave.
 */

/** Nothing rings and nothing is scheduled: no active ring, no next alarm, every call a no-op. */
internal class NoOpAlarmEngine(private val clock: () -> Long = System::currentTimeMillis) : AlarmEngine {
    override val activeRing: StateFlow<ActiveRing?> = MutableStateFlow<ActiveRing?>(null).asStateFlow()
    override val nextAlarm: StateFlow<NextAlarm?> = MutableStateFlow<NextAlarm?>(null).asStateFlow()

    override suspend fun rescheduleAll(reason: String) = Unit
    override suspend fun nextOccurrenceOf(alarmId: Long): Long? = null
    override suspend fun skipNext(alarmId: Long) = Unit
    override suspend fun clearSkip(alarmId: Long) = Unit
    override suspend fun dismissUpcoming(alarmId: Long) = Unit

    /** Returns the time the nap would ring (nothing is scheduled). */
    override suspend fun startNap(minutes: Int, template: Alarm?): Long = clock() + minutes.coerceAtLeast(0) * 60_000L

    override suspend fun testRing(alarm: Alarm, delaySeconds: Int) = Unit
    override suspend fun snooze(ringId: String): Boolean = false
    override suspend fun dismiss(ringId: String, outcome: RingOutcome) = Unit
    override suspend fun startMission(ringId: String) = Unit
    override fun onMissionInteraction(ringId: String) = Unit
    override suspend fun completeMission(ringId: String) = Unit
    override suspend fun abandonMission(ringId: String) = Unit
    override suspend fun confirmAwake(ringId: String) = Unit
    override suspend fun scheduleTimer(timerId: Long, endAt: Long) = Unit
    override suspend fun cancelTimer(timerId: Long) = Unit
    override suspend fun triggerNow(kind: RingKind, sourceId: Long, alarm: Alarm) = Unit

    override suspend fun integrityReport(repair: Boolean): IntegrityReport = IntegrityReport(
        expected = 0,
        registered = 0,
        missingSourceIds = emptyList(),
        repaired = false,
        systemNextAlarmMatches = true,
        checkedAt = clock(),
    )
}

/** No health items, no fix intents. */
internal class NoOpReliabilityChecker : ReliabilityChecker {
    override suspend fun check(): List<HealthItem> = emptyList()
    override fun fixIntents(context: Context, id: HealthItemId): List<Intent> = emptyList()
    override suspend fun setManualConfirmed(id: HealthItemId, confirmed: Boolean) = Unit
    override fun isSamsungDevice(): Boolean = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
}
