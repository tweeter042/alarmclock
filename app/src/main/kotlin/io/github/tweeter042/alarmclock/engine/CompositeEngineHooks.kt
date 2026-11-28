package io.github.tweeter042.alarmclock.engine

import android.content.Context
import android.util.Log
import io.github.tweeter042.alarmclock.core.model.alarm.ActiveRing
import io.github.tweeter042.alarmclock.core.model.alarm.Alarm
import io.github.tweeter042.alarmclock.core.model.alarm.NextAlarm
import io.github.tweeter042.alarmclock.core.model.alarm.PreAlarmEvent
import io.github.tweeter042.alarmclock.core.model.alarm.RingOutcome
import io.github.tweeter042.alarmclock.core.model.engine.AlarmEngineHooks
import io.github.tweeter042.alarmclock.feature.widgets.WidgetsApi
import kotlinx.coroutines.CancellationException

/**
 * Fans every engine event out to the feature hooks. One failing hook never affects the others
 * (and the engine additionally bounds each call with a timeout).
 */
internal class CompositeEngineHooks(private val delegates: List<AlarmEngineHooks>) : AlarmEngineHooks {

    override suspend fun onPreAlarm(event: PreAlarmEvent, alarm: Alarm) = each("onPreAlarm") { it.onPreAlarm(event, alarm) }

    override suspend fun onRingStarted(ring: ActiveRing) = each("onRingStarted") { it.onRingStarted(ring) }

    override suspend fun onRingUpdated(ring: ActiveRing) = each("onRingUpdated") { it.onRingUpdated(ring) }

    override suspend fun onRingFinished(ring: ActiveRing, outcome: RingOutcome) =
        each("onRingFinished") { it.onRingFinished(ring, outcome) }

    override suspend fun onScheduleChanged(next: NextAlarm?, alarms: List<Alarm>) =
        each("onScheduleChanged") { it.onScheduleChanged(next, alarms) }

    private suspend inline fun each(event: String, block: (AlarmEngineHooks) -> Unit) {
        for (hook in delegates) {
            try {
                block(hook)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "$event failed in ${hook.javaClass.simpleName}", t)
            }
        }
    }

    private companion object {
        const val TAG = "AlarmClock/Hooks"
    }
}

/** Keeps widgets, tiles and dynamic shortcuts in sync with the schedule. */
internal class WidgetRefreshHooks(private val context: Context) : AlarmEngineHooks {
    override suspend fun onScheduleChanged(next: NextAlarm?, alarms: List<Alarm>) = WidgetsApi.refreshAll(context)
    override suspend fun onRingFinished(ring: ActiveRing, outcome: RingOutcome) = WidgetsApi.refreshAll(context)
}
