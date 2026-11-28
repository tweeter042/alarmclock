package io.github.tweeter042.alarmclock.core.alarm.ring

import android.content.Context
import android.media.AudioManager
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibratorManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.tweeter042.alarmclock.core.alarm.internal.EnginePrefs
import io.github.tweeter042.alarmclock.core.model.alarm.VibrationPattern
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToneGenerator

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RingHardwareTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val audio get() = context.getSystemService(AudioManager::class.java)

    @Before fun setUp() {
        ShadowToneGenerator.reset()
        audio.setStreamVolume(AudioManager.STREAM_ALARM, 2, 0)
    }

    @Test fun theAlarmVolumeOverrideIsRestoredAfterTheRing() {
        val prefs = EnginePrefs(context)
        val volume = AlarmVolumeController(context, prefs)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)

        volume.applyPercent(100)
        assertEquals(max, audio.getStreamVolume(AudioManager.STREAM_ALARM))
        assertEquals("the user's volume is remembered (survives process death)", 2, EnginePrefs(context).savedAlarmVolume)

        volume.applyPercent(50)
        assertEquals("the first saved value is kept across rings", 2, prefs.savedAlarmVolume)

        volume.restore()
        assertEquals(2, audio.getStreamVolume(AudioManager.STREAM_ALARM))
        assertNull(prefs.savedAlarmVolume)
        volume.restore()
        assertEquals("restore is idempotent", 2, audio.getStreamVolume(AudioManager.STREAM_ALARM))
    }

    @Test fun aMutedAlarmStreamCannotSilenceTheAlarm() {
        audio.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0)
        val volume = AlarmVolumeController(context, EnginePrefs(context))
        volume.ensureAudible()
        assertTrue(audio.getStreamVolume(AudioManager.STREAM_ALARM) >= 1)
        volume.restore()
        assertEquals(0, audio.getStreamVolume(AudioManager.STREAM_ALARM))

        audio.setStreamVolume(AudioManager.STREAM_ALARM, 3, 0)
        volume.ensureAudible()
        assertEquals("an audible stream is left alone", 3, audio.getStreamVolume(AudioManager.STREAM_ALARM))
        assertNull(EnginePrefs(context).savedAlarmVolume)
    }

    @Test fun vibrationUsesTheAlarmUsageAndTheChosenWaveform() {
        val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
        val ring = RingVibrator(context)
        ring.start(VibrationPattern.HEARTBEAT)
        val shadow = shadowOf(vibrator)
        assertTrue(shadow.isVibrating)
        val waveform = VibrationWaveforms.of(VibrationPattern.HEARTBEAT)
        assertArrayEquals(waveform.timings, shadow.pattern)
        assertEquals(waveform.repeatIndex, shadow.repeat)
        assertEquals(VibrationAttributes.USAGE_ALARM, (shadow.vibrationAttributesFromLastVibration as VibrationAttributes).usage)
        ring.stop()
        assertFalse(shadow.isVibrating)
    }

    @Test fun withNoPlayableSoundTheToneGeneratorBeepsOnTheAlarmStream() {
        var beeper: Boolean? = null
        val player = RingPlayer(
            context,
            object : RingPlayer.Listener {
                override fun onPlaying(candidate: Int) = Unit
                override fun onBeeper(working: Boolean) {
                    beeper = working
                }
            },
        )
        player.start(emptyList(), 1f)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(true, beeper)
        assertTrue(ShadowToneGenerator.getPlayedTones().isNotEmpty())
        player.release()
    }
}
