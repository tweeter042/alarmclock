package io.github.tweeter042.alarmclock.core.alarm.internal

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import io.github.tweeter042.alarmclock.core.alarm.EngineLog

/**
 * Small engine-private state in DEVICE-PROTECTED storage (readable in Direct Boot).
 * Ring state itself lives in RingStateStore; this only holds bookkeeping that must survive a
 * process death: the alarm-stream volume to restore, pending "snooze after missions" flags, the
 * boot count of the last schedule (force-stop detection) and playlist positions.
 */
internal class EnginePrefs(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val deviceContext = if (context.isDeviceProtectedStorage) context else context.createDeviceProtectedStorageContext()
        deviceContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    /** Alarm-stream volume before the engine overrode it, or null if not overridden. */
    var savedAlarmVolume: Int?
        get() = read { getInt(KEY_SAVED_VOLUME, -1).takeIf { it >= 0 } }
        set(value) = write(commit = true) { if (value == null) remove(KEY_SAVED_VOLUME) else putInt(KEY_SAVED_VOLUME, value) }

    var pendingSnooze: Set<String>
        get() = read { getStringSet(KEY_PENDING_SNOOZE, null)?.toSet() } ?: emptySet()
        set(value) = write { putStringSet(KEY_PENDING_SNOOZE, value.toSet()) }

    /** Settings.Global.BOOT_COUNT when the schedule was last registered. */
    var lastScheduleBootCount: Int?
        get() = read { getInt(KEY_BOOT_COUNT, -1).takeIf { it >= 0 } }
        set(value) = write { if (value == null) remove(KEY_BOOT_COUNT) else putInt(KEY_BOOT_COUNT, value) }

    fun playlistPosition(alarmId: Long): Int = read { getInt(KEY_PLAYLIST + alarmId, 0) } ?: 0

    fun setPlaylistPosition(alarmId: Long, position: Int) = write { putInt(KEY_PLAYLIST + alarmId, position) }

    private inline fun <T> read(block: SharedPreferences.() -> T): T? =
        try {
            prefs.block()
        } catch (t: Throwable) {
            EngineLog.engine.w("Engine state unreadable", t)
            null
        }

    private inline fun write(commit: Boolean = false, block: SharedPreferences.Editor.() -> Unit) {
        try {
            val editor = prefs.edit().apply(block)
            if (commit) editor.commit() else editor.apply()
        } catch (t: Throwable) {
            EngineLog.engine.w("Engine state not saved", t)
        }
    }

    private companion object {
        const val FILE = "alarmclock_engine_state"
        const val KEY_SAVED_VOLUME = "saved_alarm_volume"
        const val KEY_PENDING_SNOOZE = "pending_snooze"
        const val KEY_BOOT_COUNT = "schedule_boot_count"
        const val KEY_PLAYLIST = "playlist_position_"
    }
}

/** The device boot counter (Settings.Global.BOOT_COUNT), or null if unavailable. */
internal object BootCount {
    fun current(context: Context): Int? =
        try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1).takeIf { it >= 0 }
        } catch (t: Throwable) {
            null
        }
}
