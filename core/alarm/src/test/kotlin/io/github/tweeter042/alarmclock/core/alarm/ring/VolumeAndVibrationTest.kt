package io.github.tweeter042.alarmclock.core.alarm.ring

import io.github.tweeter042.alarmclock.core.alarm.R
import io.github.tweeter042.alarmclock.core.model.alarm.BuiltInTones
import io.github.tweeter042.alarmclock.core.model.alarm.PlaylistItem
import io.github.tweeter042.alarmclock.core.model.alarm.SoundConfig
import io.github.tweeter042.alarmclock.core.model.alarm.VibrationPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log10
import kotlin.random.Random

class VolumeMathTest {

    @Test fun streamIndexRoundsAndNeverMutes() {
        assertEquals(6, VolumeMath.streamIndexFor(80, minIndex = 0, maxIndex = 7))
        assertEquals(7, VolumeMath.streamIndexFor(100, 0, 7))
        assertEquals(1, VolumeMath.streamIndexFor(1, 0, 7))
        assertEquals("0 % still rings", 1, VolumeMath.streamIndexFor(0, 0, 7))
        assertEquals("device minimum respected", 2, VolumeMath.streamIndexFor(5, 2, 15))
        assertEquals(8, VolumeMath.streamIndexFor(50, 1, 15))
        assertEquals(15, VolumeMath.streamIndexFor(250, 1, 15))
        assertEquals(0, VolumeMath.streamIndexFor(50, 0, 0))
    }

    @Test fun targetGainDependsOnTheOverride() {
        assertEquals(1f, VolumeMath.targetGain(SoundConfig(volumePercent = 30, overrideSystemVolume = true)))
        assertEquals(0.3f, VolumeMath.targetGain(SoundConfig(volumePercent = 30, overrideSystemVolume = false)), 1e-6f)
        assertEquals(VolumeMath.FADE_START_GAIN, VolumeMath.targetGain(SoundConfig(volumePercent = 0, overrideSystemVolume = false)), 1e-6f)
    }

    @Test fun fadeInRunsFromMinus26DbToFullInEqualDecibelSteps() {
        val fade = 30_000L
        assertEquals(VolumeMath.FADE_START_GAIN, VolumeMath.fadeFactor(0, fade), 1e-6f)
        assertEquals(1f, VolumeMath.fadeFactor(fade, fade), 1e-6f)
        assertEquals(1f, VolumeMath.fadeFactor(fade * 2, fade), 1e-6f)
        assertEquals("no fade", 1f, VolumeMath.fadeFactor(0, 0), 1e-6f)
        val gains = (0..30).map { VolumeMath.fadeFactor(it * 1_000L, fade) }
        assertTrue("monotonic", gains.zipWithNext().all { (a, b) -> b >= a })
        val db = gains.map { 20 * log10(it.toDouble()) }
        val steps = db.zipWithNext { a, b -> b - a }
        assertTrue("perceptual (log) curve: equal dB per second", steps.all { abs(it - steps.first()) < 1e-3 })
        assertEquals(kotlin.math.sqrt(0.05).toFloat(), VolumeMath.fadeFactor(fade / 2, fade), 1e-4f)
    }

    @Test fun playerGainCombinesTargetFadeAndDucking() {
        assertEquals(1f, VolumeMath.playerGain(1f, 60_000, 30_000), 1e-6f)
        assertEquals(VolumeMath.MISSION_DUCK, VolumeMath.playerGain(1f, 60_000, 30_000, VolumeMath.MISSION_DUCK), 1e-6f)
        assertEquals(0.5f * VolumeMath.BRIEFING_DUCK, VolumeMath.playerGain(0.5f, 60_000, 0, VolumeMath.BRIEFING_DUCK), 1e-6f)
        assertEquals(0.8f * VolumeMath.FADE_START_GAIN, VolumeMath.playerGain(0.8f, 0, 10_000), 1e-6f)
        assertEquals(0f, VolumeMath.playerGain(1f, 0, 0, duck = 0f), 0f)
        assertEquals(1f, VolumeMath.playerGain(3f, 0, 0), 0f)
    }
}

class VibrationWaveformsTest {

    @Test fun everyPatternIsAValidRepeatingWaveform() {
        VibrationPattern.entries.forEach { pattern ->
            val waveform = VibrationWaveforms.of(pattern)
            assertEquals(pattern.name, waveform.timings.size, waveform.amplitudes.size)
            assertTrue(pattern.name, waveform.timings.isNotEmpty())
            assertTrue(pattern.name, waveform.timings.all { it > 0 })
            assertTrue(pattern.name, waveform.amplitudes.all { it in 0..255 })
            assertTrue(pattern.name, waveform.repeatIndex in waveform.timings.indices)
            val cycle = waveform.amplitudes.drop(waveform.repeatIndex)
            assertTrue("$pattern vibrates", cycle.any { it > 0 })
            assertTrue("$pattern pauses", cycle.any { it == 0 })
            assertTrue("$pattern cycle ${waveform.cycleMillis} ms", waveform.cycleMillis in 200..10_000)
            assertTrue("$pattern: no two adjacent pauses", waveform.amplitudes.toList().zipWithNext().none { (a, b) -> a == 0 && b == 0 })
        }
    }

    @Test fun escalatingGetsStrongerBeforeItRepeats() {
        val waveform = VibrationWaveforms.of(VibrationPattern.ESCALATING)
        assertTrue(waveform.repeatIndex > 0)
        val ramp = waveform.amplitudes.take(waveform.repeatIndex).filter { it > 0 }
        assertEquals(ramp.sorted(), ramp)
        assertEquals(255, waveform.amplitudes.drop(waveform.repeatIndex).max())
    }

    @Test fun sosSpellsDotDotDotDashDashDashDotDotDot() {
        val waveform = VibrationWaveforms.of(VibrationPattern.SOS)
        val pulses = waveform.timings.filterIndexed { i, _ -> waveform.amplitudes[i] > 0 }
        assertEquals(listOf(200L, 200L, 200L, 600L, 600L, 600L, 200L, 200L, 200L), pulses)
    }

    @Test fun waveformsAreStable() {
        assertEquals(VibrationWaveforms.of(VibrationPattern.WAVE), VibrationWaveforms.of(VibrationPattern.WAVE))
        assertEquals(VibrationWaveforms.of(VibrationPattern.BASIC).hashCode(), VibrationWaveforms.of(VibrationPattern.BASIC).hashCode())
    }
}

class SoundSelectionTest {

    private val items = (0 until 4).map { PlaylistItem("content://x/$it", "t$it") }

    @Test fun inOrderPlaylistsContinueWhereTheyStopped() {
        assertEquals(listOf(2, 3, 0, 1), SoundSourceResolver.order(items, shuffle = false, start = 2, random = Random(1)))
        assertEquals(listOf(1, 2, 3, 0), SoundSourceResolver.order(items, shuffle = false, start = 5, random = Random(1)))
        assertEquals(listOf(3, 0, 1, 2), SoundSourceResolver.order(items, shuffle = false, start = -1, random = Random(1)))
    }

    @Test fun shuffledPlaylistsTryEveryItemOnce() {
        val order = SoundSourceResolver.order(items, shuffle = true, start = 0, random = Random(42))
        assertEquals((0 until 4).toSet(), order.toSet())
        assertEquals(4, order.size)
        assertTrue(SoundSourceResolver.order(emptyList(), shuffle = true, start = 0, random = Random(0)).isEmpty())
    }

    @Test fun everyBundledToneHasItsResource() {
        (BuiltInTones.ALL.map { it.id } + BuiltInTones.SYSTEM_TONES).forEach { id ->
            assertNotNull("tone_$id", ToneResources.resId(id))
            assertTrue(SoundSourceResolver.isBundled(id))
        }
        assertEquals(R.raw.tone_classic_beep, ToneResources.resId(BuiltInTones.FALLBACK_ID))
        assertEquals(BuiltInTones.ALL.size + BuiltInTones.SYSTEM_TONES.size, ToneResources.ids.size)
        assertNotNull(ToneResources.resId(BuiltInTones.DEFAULT_ID))
    }
}
