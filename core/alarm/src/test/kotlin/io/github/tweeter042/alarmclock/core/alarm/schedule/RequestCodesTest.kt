package io.github.tweeter042.alarmclock.core.alarm.schedule

import io.github.tweeter042.alarmclock.core.model.alarm.Alarm
import io.github.tweeter042.alarmclock.core.model.alarm.PreAlarmKind
import io.github.tweeter042.alarmclock.core.model.alarm.RingKind
import io.github.tweeter042.alarmclock.core.model.entity.ScheduledTrigger
import io.github.tweeter042.alarmclock.core.model.entity.TriggerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class RequestCodesTest {

    private val ringBase = RequestCodes.base(CodeBlock.RING)

    @Test fun numericIdentitiesUseTheIdAsSlot() {
        assertEquals(ringBase + 42, RequestCodes.preferred(TriggerIdentities.ring(42)))
        assertEquals(RequestCodes.base(CodeBlock.TIMER) + 7, RequestCodes.preferred(TriggerIdentities.timer(7)))
        assertEquals(RequestCodes.base(CodeBlock.CALENDAR) + 3, RequestCodes.preferred(TriggerIdentities.calendar(3)))
        assertEquals(
            RequestCodes.base(CodeBlock.PRE_ALARM) + 42 * 8 + PreAlarmKind.SUNRISE_START.ordinal,
            RequestCodes.preferred(TriggerIdentities.preAlarm(42, PreAlarmKind.SUNRISE_START)),
        )
        assertEquals(RequestCodes.base(CodeBlock.MIDNIGHT), RequestCodes.preferred(TriggerIdentities.midnight))
    }

    @Test fun codesArePositiveAndBlocksAreDisjoint() {
        CodeBlock.entries.forEach { block ->
            val base = RequestCodes.base(block)
            assertTrue(base > 0)
            assertTrue(base.toLong() + RequestCodes.SLOTS - 1 <= Int.MAX_VALUE)
            assertEquals(block, RequestCodes.blockOf(base))
            assertEquals(block, RequestCodes.blockOf(base + RequestCodes.SLOTS - 1))
        }
        assertNull(RequestCodes.blockOf(-5))
        assertNull(RequestCodes.blockOf(0))
    }

    @Test fun aRealisticScheduleGetsUniqueCodes() {
        val identities = buildList {
            (1L..2_000L).forEach { id ->
                add(TriggerIdentities.ring(id))
                PreAlarmKind.entries.forEach { add(TriggerIdentities.preAlarm(id, it)) }
            }
            (1L..50L).forEach { add(TriggerIdentities.calendar(it)); add(TriggerIdentities.timer(it)) }
            repeat(500) {
                val ringId = UUID.randomUUID().toString()
                add(TriggerIdentities.snoozeEnd(ringId))
                add(TriggerIdentities.awakeCheck(ringId))
                add(TriggerIdentities.kick(ringId))
            }
            (0L until 200L).forEach { add(TriggerIdentities.nap(1_795_000_000_000L + it * 60_000)) }
            (0L until 200L).forEach { add(TriggerIdentities.test(it % 5, 1_795_000_000_000L + it)) }
            add(TriggerIdentities.midnight)
        }
        val codes = RequestCodes.assign(identities)
        assertEquals(identities.size, codes.size)
        assertEquals("collision-free", identities.size, codes.values.toSet().size)
        codes.forEach { (identity, code) ->
            assertTrue(code > 0)
            assertEquals("each code stays in its block", identity.block, RequestCodes.blockOf(code))
        }
    }

    @Test fun collisionsAreResolvedInsideTheBlock() {
        val a = TriggerIdentities.ring(5)
        val b = TriggerIdentities.ring(5 + RequestCodes.SLOTS.toLong())
        assertEquals(RequestCodes.preferred(a), RequestCodes.preferred(b))
        val codes = RequestCodes.assign(listOf(b, a))
        assertNotEquals(codes.getValue(a), codes.getValue(b))
        assertEquals(CodeBlock.RING, RequestCodes.blockOf(codes.getValue(b)))
        assertEquals("deterministic whatever the input order", codes, RequestCodes.assign(listOf(a, b)))
    }

    @Test fun previousCodesAreKeptForStablePendingIntents() {
        val a = TriggerIdentities.ring(1)
        val b = TriggerIdentities.ring(2)
        val previous = mapOf(a to ringBase + 900, b to RequestCodes.base(CodeBlock.TIMER) + 2)
        val codes = RequestCodes.assign(listOf(a, b), previous)
        assertEquals(ringBase + 900, codes.getValue(a))
        assertEquals("a code from another block is not reused", ringBase + 2, codes.getValue(b))
    }

    @Test fun aPreviousCodeOwnedTwiceIsGivenToOnlyOneIdentity() {
        val a = TriggerIdentities.ring(1)
        val b = TriggerIdentities.ring(2)
        val codes = RequestCodes.assign(listOf(a, b), mapOf(a to ringBase + 7, b to ringBase + 7))
        assertEquals(2, codes.values.toSet().size)
    }

    @Test fun assignOneReusesOrProbes() {
        val a = TriggerIdentities.ring(3)
        val taken = mapOf(ringBase + 3 to TriggerIdentities.ring(3 + RequestCodes.SLOTS.toLong()), ringBase + 4 to TriggerIdentities.ring(4))
        assertEquals("probes past taken codes", ringBase + 5, RequestCodes.assignOne(a, taken))
        assertEquals("an identity keeps its recorded code", ringBase + 4, RequestCodes.assignOne(TriggerIdentities.ring(4), taken))
    }

    @Test fun uiCodesLiveInTheirOwnBlocks() {
        val code = RequestCodes.ui(CodeBlock.UI_RING_SCREEN, "ring-1")
        assertEquals(CodeBlock.UI_RING_SCREEN, RequestCodes.blockOf(code))
        assertEquals(code, RequestCodes.ui(CodeBlock.UI_RING_SCREEN, "ring-1"))
        assertNotEquals(code, RequestCodes.ui(CodeBlock.UI_RING_SCREEN, "ring-2"))
    }

    @Test fun ledgerEntriesMapBackToTheirIdentity() {
        fun entry(kind: TriggerKind, sourceId: Long, extra: TriggerExtra?, at: Long = 100L) =
            ScheduledTrigger(1, kind, sourceId, at, at, extra?.encode())
        assertEquals(TriggerIdentities.ring(4), TriggerIdentities.of(entry(TriggerKind.RING, 4, null)))
        assertEquals(TriggerIdentities.preAlarm(4, PreAlarmKind.PREFETCH), TriggerIdentities.of(entry(TriggerKind.PRE_ALARM, 4, TriggerExtra(preAlarm = PreAlarmKind.PREFETCH))))
        assertEquals(TriggerIdentities.midnight, TriggerIdentities.of(entry(TriggerKind.PRE_ALARM, 0, TriggerExtra(purpose = TriggerExtra.PURPOSE_MIDNIGHT))))
        assertEquals(TriggerIdentities.snoozeEnd("x"), TriggerIdentities.of(entry(TriggerKind.SNOOZE_END, 4, TriggerExtra(ringId = "x", ringKind = RingKind.ALARM))))
        assertEquals(TriggerIdentities.kick("x"), TriggerIdentities.of(entry(TriggerKind.SNOOZE_END, 4, TriggerExtra(ringId = "x", purpose = TriggerExtra.PURPOSE_KICK))))
        assertEquals(TriggerIdentities.awakeCheck("y"), TriggerIdentities.of(entry(TriggerKind.AWAKE_CHECK, 4, TriggerExtra(ringId = "y"))))
        assertEquals(TriggerIdentities.timer(9), TriggerIdentities.of(entry(TriggerKind.TIMER, 9, null)))
        assertEquals(TriggerIdentities.nap(55), TriggerIdentities.of(entry(TriggerKind.NAP, 55, TriggerExtra(alarm = Alarm()))))
        assertEquals(TriggerIdentities.test(2, 77), TriggerIdentities.of(entry(TriggerKind.TEST, 2, null, at = 77)))
        assertEquals(TriggerIdentities.calendar(6), TriggerIdentities.of(entry(TriggerKind.CALENDAR, 6, TriggerExtra(title = "t"))))
        assertNull("unreadable pre-alarm", TriggerIdentities.of(entry(TriggerKind.PRE_ALARM, 4, null)))
        assertNull("snooze end without ring id", TriggerIdentities.of(entry(TriggerKind.SNOOZE_END, 4, null)))
    }

    @Test fun triggerExtraRoundTripsAndToleratesGarbage() {
        val extra = TriggerExtra(ringId = "r", ringKind = RingKind.TIMER, preAlarm = PreAlarmKind.WEATHER_CHECK, alarm = Alarm(label = "L"), title = "T", purpose = "p")
        assertEquals(extra, TriggerExtra.decode(extra.encode()))
        assertNull(TriggerExtra.decode("{not json"))
        assertNull(TriggerExtra.decode(""))
        assertNull(TriggerExtra.decode(null))
        assertEquals(TriggerExtra(ringId = "r"), TriggerExtra.decode("""{"ringId":"r","unknown":1}"""))
    }
}
