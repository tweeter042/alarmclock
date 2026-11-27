package io.github.tweeter042.alarmclock.core.data

import android.content.Context
import java.io.File

/**
 * Locations of everything the ring path reads. All of it lives in DEVICE-PROTECTED storage so
 * alarms keep working in Direct Boot (after a reboot, before the first unlock).
 *
 * Pitfall avoided here: `androidx.datastore.dataStoreFile(context, name)` resolves against
 * `context.applicationContext`, which is the credential-encrypted context even when called on a
 * device-protected one. Files are therefore built from the device-protected context's own
 * `filesDir`.
 */
object DeviceStorage {
    /** Room database file (in the device-protected `databases/` directory). */
    const val DATABASE_FILE = "alarmclock.db"

    /** DataStore file holding [io.github.tweeter042.alarmclock.core.model.settings.AppSettings]. */
    const val SETTINGS_FILE = "settings.json"

    /** DataStore file holding [io.github.tweeter042.alarmclock.core.model.entity.StopwatchState]. */
    const val STOPWATCH_FILE = "stopwatch.json"

    private const val DATASTORE_DIR = "datastore"

    /** The device-protected storage context for [context] (idempotent). */
    fun deviceProtectedContext(context: Context): Context =
        if (context.isDeviceProtectedStorage) context else context.createDeviceProtectedStorageContext()

    /** `<device-protected files dir>/datastore/<fileName>`. */
    fun dataStoreFile(context: Context, fileName: String): File =
        File(File(deviceProtectedContext(context).filesDir, DATASTORE_DIR), fileName)

    /** Absolute path of the Room database file. */
    fun databaseFile(context: Context): File = deviceProtectedContext(context).getDatabasePath(DATABASE_FILE)
}
