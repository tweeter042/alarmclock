package io.github.tweeter042.alarmclock.core.alarm.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import io.github.tweeter042.alarmclock.core.alarm.EngineLog
import io.github.tweeter042.alarmclock.core.model.entity.ScheduledTrigger
import io.github.tweeter042.alarmclock.core.model.repository.ScheduleLedger

/**
 * The only class that talks to AlarmManager. Every registration is recorded in the
 * [ScheduleLedger] (device-protected storage). Callers serialize access (ScheduleCoordinator's mutex).
 *
 * - Ring triggers: `setAlarmClock` (exact, Doze-exempt, shown as the next alarm on the status bar
 *   and AOD) with a `showIntent` that opens the app.
 * - Pre-alarm events: `setExactAndAllowWhileIdle`.
 * - Without exact-alarm permission (should not happen with USE_EXACT_ALARM) both fall back to
 *   `setAndAllowWhileIdle`, which still wakes the device, just less precisely.
 * - Every PendingIntent is FLAG_IMMUTABLE.
 */
internal class AlarmScheduler(
    private val context: Context,
    private val ledger: ScheduleLedger,
    private val showIntent: () -> PendingIntent,
) {
    private val alarmManager: AlarmManager? = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean = runCatching { alarmManager?.canScheduleExactAlarms() ?: false }.getOrDefault(false)

    fun register(trigger: ScheduledTrigger, method: RegisterMethod, label: String) {
        val manager = alarmManager ?: return
        val operation = PendingIntent.getBroadcast(
            context,
            trigger.requestCode,
            TriggerIntents.broadcast(context, trigger, label),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            when (method) {
                RegisterMethod.ALARM_CLOCK -> manager.setAlarmClock(AlarmManager.AlarmClockInfo(trigger.triggerAt, showIntent()), operation)
                RegisterMethod.EXACT_WHILE_IDLE ->
                    if (canScheduleExact()) {
                        manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.triggerAt, operation)
                    } else {
                        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.triggerAt, operation)
                    }
            }
        } catch (e: SecurityException) {
            EngineLog.schedule.e("Exact alarm refused for ${trigger.kind} #${trigger.requestCode}; using an inexact alarm", e)
            try {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.triggerAt, operation)
            } catch (t: Throwable) {
                EngineLog.schedule.e("Could not register ${trigger.kind} #${trigger.requestCode}", t)
            }
        } catch (t: Throwable) {
            EngineLog.schedule.e("Could not register ${trigger.kind} #${trigger.requestCode}", t)
        }
    }

    fun cancel(requestCode: Int) {
        try {
            existing(requestCode)?.let { pendingIntent ->
                alarmManager?.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        } catch (t: Throwable) {
            EngineLog.schedule.w("Could not cancel trigger #$requestCode", t)
        }
    }

    /** True while AlarmManager still holds the trigger's PendingIntent (FLAG_NO_CREATE). */
    fun isRegistered(requestCode: Int): Boolean = existing(requestCode) != null

    /** AlarmManager.getNextAlarmClock(): (trigger time, is it ours?), or null if there is none. */
    fun systemNextAlarm(): Pair<Long, Boolean>? = try {
        alarmManager?.nextAlarmClock?.let { info ->
            info.triggerTime to (info.showIntent?.creatorPackage == context.packageName)
        }
    } catch (t: Throwable) {
        null
    }

    private fun existing(requestCode: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        requestCode,
        TriggerIntents.base(context),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * Makes AlarmManager and the ledger match [planned]: registers every planned trigger
     * (replacing registrations with the same request code), records the plan, then cancels
     * triggers of [previous] that are no longer planned. At every moment the union of the old and
     * new triggers is registered, so a crash mid-way can only leave extra triggers, never fewer.
     */
    suspend fun apply(planned: List<PlannedTrigger>, previous: List<ScheduledTrigger>): List<ScheduledTrigger> {
        val previousCodes = previous.mapNotNull { entry -> TriggerIdentities.of(entry)?.let { it to entry.requestCode } }.toMap()
        val codes = RequestCodes.assign(planned.map { it.identity }, previousCodes)
        val triggers = planned.map { it to it.toScheduled(codes.getValue(it.identity)) }
        triggers.forEach { (plan, trigger) -> register(trigger, plan.method, plan.label) }
        val scheduled = triggers.map { it.second }
        ledger.replaceAll(scheduled)
        val kept = scheduled.mapTo(HashSet()) { it.requestCode }
        previous.filter { it.requestCode !in kept }.forEach { cancel(it.requestCode) }
        return scheduled
    }

    /** Registers one trigger outside a full reschedule (snooze end, awake check, timer, nap, test, kick). */
    suspend fun registerOne(planned: PlannedTrigger): ScheduledTrigger {
        val taken = ledger.all().mapNotNull { entry -> TriggerIdentities.of(entry)?.let { entry.requestCode to it } }.toMap()
        val trigger = planned.toScheduled(RequestCodes.assignOne(planned.identity, taken))
        register(trigger, planned.method, planned.label)
        ledger.put(trigger)
        return trigger
    }

    /** Cancels and forgets every ledger entry with one of [identities]. */
    suspend fun cancelIdentities(identities: Set<TriggerIdentity>) {
        if (identities.isEmpty()) return
        ledger.all().filter { TriggerIdentities.of(it) in identities }.forEach {
            cancel(it.requestCode)
            ledger.remove(it.requestCode)
        }
    }
}
