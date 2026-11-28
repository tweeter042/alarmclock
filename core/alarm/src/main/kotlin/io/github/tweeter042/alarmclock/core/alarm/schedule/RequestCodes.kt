package io.github.tweeter042.alarmclock.core.alarm.schedule

import io.github.tweeter042.alarmclock.core.model.alarm.PreAlarmKind
import io.github.tweeter042.alarmclock.core.model.entity.ScheduledTrigger
import io.github.tweeter042.alarmclock.core.model.entity.TriggerKind

/**
 * Blocks of the request-code space. A request code is a positive Int:
 * `block.index << 26 | slot` (5 bits of block, 26 bits of slot).
 *
 * Blocks 1–15 are AlarmManager trigger broadcasts (the [io.github.tweeter042.alarmclock.core.model.repository.ScheduleLedger]
 * primary key); blocks 16–31 are activity PendingIntents used by notifications.
 */
internal enum class CodeBlock(val index: Int) {
    RING(1),
    PRE_ALARM(2),
    CALENDAR(3),
    SNOOZE_END(4),
    AWAKE_CHECK(5),
    TIMER(6),
    NAP(7),
    TEST(8),
    MIDNIGHT(9),
    KICK(10),

    UI_RING_SCREEN(16),
    UI_SUNRISE(17),
    UI_MAIN(18),
    UI_EDITOR(19),
    UI_SHOW_ALARM(20),
}

/**
 * What a trigger *is*, independent of when it fires: two triggers with the same identity are the
 * same AlarmManager registration (a later one replaces the earlier one).
 */
internal data class TriggerIdentity(val block: CodeBlock, val key: String) : Comparable<TriggerIdentity> {
    override fun compareTo(other: TriggerIdentity): Int =
        compareValuesBy(this, other, { it.block.index }, { it.key })

    override fun toString(): String = "${block.name}[$key]"
}

/** Identity of every trigger the engine registers. */
internal object TriggerIdentities {
    fun ring(alarmId: Long) = TriggerIdentity(CodeBlock.RING, alarmId.toString())
    fun preAlarm(alarmId: Long, kind: PreAlarmKind) = TriggerIdentity(CodeBlock.PRE_ALARM, "$alarmId:${kind.name}")
    fun calendar(ruleId: Long) = TriggerIdentity(CodeBlock.CALENDAR, ruleId.toString())
    fun snoozeEnd(ringId: String) = TriggerIdentity(CodeBlock.SNOOZE_END, ringId)
    fun awakeCheck(ringId: String) = TriggerIdentity(CodeBlock.AWAKE_CHECK, ringId)
    fun timer(timerId: Long) = TriggerIdentity(CodeBlock.TIMER, timerId.toString())
    fun nap(sourceId: Long) = TriggerIdentity(CodeBlock.NAP, sourceId.toString())
    fun test(sourceId: Long, triggerAt: Long) = TriggerIdentity(CodeBlock.TEST, "$triggerAt:$sourceId")
    fun kick(ringId: String) = TriggerIdentity(CodeBlock.KICK, ringId)
    val midnight = TriggerIdentity(CodeBlock.MIDNIGHT, "")

    /** Identity of a ledger entry, or null if the entry is unreadable (it is then dropped). */
    fun of(trigger: ScheduledTrigger): TriggerIdentity? {
        val extra = trigger.extraPayload
        return when (trigger.kind) {
            TriggerKind.RING -> ring(trigger.sourceId)
            TriggerKind.CALENDAR -> calendar(trigger.sourceId)
            TriggerKind.PRE_ALARM -> when {
                extra?.purpose == TriggerExtra.PURPOSE_MIDNIGHT -> midnight
                extra?.preAlarm != null -> preAlarm(trigger.sourceId, extra.preAlarm)
                else -> null
            }
            TriggerKind.SNOOZE_END -> extra?.ringId?.let { if (extra.purpose == TriggerExtra.PURPOSE_KICK) kick(it) else snoozeEnd(it) }
            TriggerKind.AWAKE_CHECK -> extra?.ringId?.let(::awakeCheck)
            TriggerKind.TIMER -> timer(trigger.sourceId)
            TriggerKind.NAP -> nap(trigger.sourceId)
            TriggerKind.TEST -> test(trigger.sourceId, trigger.triggerAt)
        }
    }
}

/**
 * Stable, collision-free PendingIntent request codes.
 *
 * Scheme: `code = block.index << 26 | slot`, always a positive Int.
 * - Numeric identities (alarm, calendar rule and timer ids) use the id itself as the slot, so the
 *   code of alarm 42 is always `RING_BASE + 42` (readable in `dumpsys alarm`).
 * - Pre-alarms use `alarmId * 8 + PreAlarmKind.ordinal`.
 * - String identities (ring ids, nap/test times) use a mixed `String.hashCode()` (stable across
 *   processes and JVMs).
 * - The local-midnight trigger is slot 0 of its block.
 *
 * Collisions (two identities wanting the same code, e.g. hash clashes or ids beyond 2^26) are
 * resolved with the ledger: a code already recorded for an identity is kept for that identity,
 * and any other identity probes linearly to the next free slot of its block, in a deterministic
 * (sorted) order. Every trigger therefore has its own PendingIntent; re-using a code for a
 * different identity simply replaces the old AlarmManager registration.
 */
internal object RequestCodes {
    const val SLOT_BITS = 26
    const val SLOTS = 1 shl SLOT_BITS
    private const val SLOT_MASK = SLOTS - 1

    fun base(block: CodeBlock): Int = block.index shl SLOT_BITS

    fun blockOf(code: Int): CodeBlock? {
        if (code < 0) return null
        val index = code ushr SLOT_BITS
        return CodeBlock.entries.firstOrNull { it.index == index }
    }

    /** The code [identity] gets when nothing collides with it. */
    fun preferred(identity: TriggerIdentity): Int = base(identity.block) or preferredSlot(identity)

    /**
     * Assigns a unique code to every identity. [previous] (identity → code from the ledger) is kept
     * whenever it is still valid, so unchanged triggers keep their PendingIntent.
     */
    fun assign(
        identities: Collection<TriggerIdentity>,
        previous: Map<TriggerIdentity, Int> = emptyMap(),
    ): Map<TriggerIdentity, Int> {
        val sorted = identities.distinct().sorted()
        val result = LinkedHashMap<TriggerIdentity, Int>(sorted.size * 2)
        val used = HashSet<Int>(sorted.size * 2)
        for (identity in sorted) {
            val code = previous[identity] ?: continue
            if (blockOf(code) == identity.block && used.add(code)) result[identity] = code
        }
        for (identity in sorted) {
            if (identity in result) continue
            val code = probe(identity) { it in used }
            used.add(code)
            result[identity] = code
        }
        return result
    }

    /** Code for one more trigger, given the codes already [taken] (code → identity) in the ledger. */
    fun assignOne(identity: TriggerIdentity, taken: Map<Int, TriggerIdentity>): Int {
        taken.entries.firstOrNull { it.value == identity && blockOf(it.key) == identity.block }?.let { return it.key }
        return probe(identity) { it in taken }
    }

    /** Request code of an activity PendingIntent (notification content / full-screen intents). */
    fun ui(block: CodeBlock, key: String): Int {
        require(block.index >= CodeBlock.UI_RING_SCREEN.index) { "not a UI block: $block" }
        return base(block) or hashSlot(key)
    }

    private inline fun probe(identity: TriggerIdentity, isTaken: (Int) -> Boolean): Int {
        val base = base(identity.block)
        var slot = preferredSlot(identity)
        repeat(SLOTS) {
            val code = base or slot
            if (!isTaken(code)) return code
            slot = (slot + 1) and SLOT_MASK
        }
        error("No free request code in block ${identity.block}")
    }

    private fun preferredSlot(identity: TriggerIdentity): Int = when (identity.block) {
        CodeBlock.RING, CodeBlock.CALENDAR, CodeBlock.TIMER ->
            identity.key.toLongOrNull()?.let(::numericSlot) ?: hashSlot(identity.key)
        CodeBlock.PRE_ALARM -> {
            val alarmId = identity.key.substringBefore(':').toLongOrNull()
            val kind = PreAlarmKind.entries.firstOrNull { it.name == identity.key.substringAfter(':') }
            if (alarmId != null && kind != null) numericSlot(alarmId * PRE_ALARM_STRIDE + kind.ordinal) else hashSlot(identity.key)
        }
        CodeBlock.MIDNIGHT -> 0
        else -> hashSlot(identity.key)
    }

    private fun numericSlot(value: Long): Int = (value and SLOT_MASK.toLong()).toInt()

    /** Murmur3-style finalizer over String.hashCode(): stable everywhere, well spread. */
    private fun hashSlot(key: String): Int {
        var h = key.hashCode()
        h = h xor (h ushr 16)
        h *= -0x7a143595
        h = h xor (h ushr 13)
        h *= -0x3d4d51cb
        h = h xor (h ushr 16)
        return h and SLOT_MASK
    }

    private const val PRE_ALARM_STRIDE = 8L
}
