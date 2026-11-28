package io.github.tweeter042.alarmclock.core.alarm.schedule

import android.content.Context
import android.content.Intent
import io.github.tweeter042.alarmclock.core.alarm.receiver.AlarmTriggerReceiver
import io.github.tweeter042.alarmclock.core.model.alarm.RingKind
import io.github.tweeter042.alarmclock.core.model.entity.ScheduledTrigger
import io.github.tweeter042.alarmclock.core.model.entity.TriggerKind

/** A fired trigger, as carried by the AlarmManager broadcast and the ring-service start intent. */
internal data class TriggerPayload(
    val kind: TriggerKind,
    val requestCode: Int,
    val sourceId: Long,
    val triggerAt: Long,
    val occurrenceAt: Long,
    val extra: TriggerExtra?,
    /** Label for the first notification, before the ring has loaded its alarm. */
    val label: String?,
) {
    val ringId: String? get() = extra?.ringId

    /** The "start the ring service now" kick for a ring that is already ringing. */
    val isKick: Boolean get() = kind == TriggerKind.SNOOZE_END && extra?.purpose == TriggerExtra.PURPOSE_KICK

    /** True if the trigger creates a new ring (as opposed to resuming one). */
    val createsRing: Boolean get() = kind.newRingKind != null

    /** Kind of the ring this trigger creates or resumes. */
    val ringKind: RingKind get() = kind.newRingKind ?: extra?.ringKind ?: RingKind.ALARM

    fun toTrigger(): ScheduledTrigger = ScheduledTrigger(requestCode, kind, sourceId, triggerAt, occurrenceAt, extra?.encode())

    companion object {
        fun of(trigger: ScheduledTrigger, label: String? = null) = TriggerPayload(
            kind = trigger.kind,
            requestCode = trigger.requestCode,
            sourceId = trigger.sourceId,
            triggerAt = trigger.triggerAt,
            occurrenceAt = trigger.occurrenceAt,
            extra = trigger.extraPayload,
            label = label,
        )
    }
}

/**
 * Intents of the trigger broadcasts. PendingIntent identity = (request code, this action, the
 * receiver component); extras are payload only, so FLAG_UPDATE_CURRENT refreshes them in place.
 */
internal object TriggerIntents {
    const val ACTION_TRIGGER = "io.github.tweeter042.alarmclock.engine.action.TRIGGER"

    private const val EXTRA_KIND = "io.github.tweeter042.alarmclock.engine.extra.KIND"
    private const val EXTRA_REQUEST_CODE = "io.github.tweeter042.alarmclock.engine.extra.REQUEST_CODE"
    private const val EXTRA_SOURCE_ID = "io.github.tweeter042.alarmclock.engine.extra.SOURCE_ID"
    private const val EXTRA_TRIGGER_AT = "io.github.tweeter042.alarmclock.engine.extra.TRIGGER_AT"
    private const val EXTRA_OCCURRENCE_AT = "io.github.tweeter042.alarmclock.engine.extra.OCCURRENCE_AT"
    private const val EXTRA_PAYLOAD = "io.github.tweeter042.alarmclock.engine.extra.PAYLOAD"
    private const val EXTRA_LABEL = "io.github.tweeter042.alarmclock.engine.extra.LABEL"

    /** Large payloads (alarm snapshots of naps/tests) are read from the ledger instead. */
    private const val MAX_INLINE_PAYLOAD = 32_000
    private const val MAX_LABEL = 120

    /** Base intent (identity only) for FLAG_NO_CREATE lookups and cancellation. */
    fun base(context: Context): Intent = Intent(context, AlarmTriggerReceiver::class.java).setAction(ACTION_TRIGGER)

    fun broadcast(context: Context, trigger: ScheduledTrigger, label: String): Intent =
        base(context)
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            .also { put(it, TriggerPayload.of(trigger, label)) }

    fun put(intent: Intent, payload: TriggerPayload) {
        intent.putExtra(EXTRA_KIND, payload.kind.name)
        intent.putExtra(EXTRA_REQUEST_CODE, payload.requestCode)
        intent.putExtra(EXTRA_SOURCE_ID, payload.sourceId)
        intent.putExtra(EXTRA_TRIGGER_AT, payload.triggerAt)
        intent.putExtra(EXTRA_OCCURRENCE_AT, payload.occurrenceAt)
        payload.extra?.encode()?.takeIf { it.length <= MAX_INLINE_PAYLOAD }?.let { intent.putExtra(EXTRA_PAYLOAD, it) }
        payload.label?.takeIf { it.isNotBlank() }?.let { intent.putExtra(EXTRA_LABEL, it.take(MAX_LABEL)) }
    }

    fun parse(intent: Intent?): TriggerPayload? {
        if (intent == null) return null
        return try {
            val kind = intent.getStringExtra(EXTRA_KIND)?.let { name -> TriggerKind.entries.firstOrNull { it.name == name } }
                ?: return null
            TriggerPayload(
                kind = kind,
                requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, 0),
                sourceId = intent.getLongExtra(EXTRA_SOURCE_ID, 0L),
                triggerAt = intent.getLongExtra(EXTRA_TRIGGER_AT, 0L),
                occurrenceAt = intent.getLongExtra(EXTRA_OCCURRENCE_AT, 0L),
                extra = TriggerExtra.decode(intent.getStringExtra(EXTRA_PAYLOAD)),
                label = intent.getStringExtra(EXTRA_LABEL),
            )
        } catch (t: Throwable) {
            null
        }
    }
}
