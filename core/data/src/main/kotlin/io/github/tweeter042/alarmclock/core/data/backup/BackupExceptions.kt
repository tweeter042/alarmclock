package io.github.tweeter042.alarmclock.core.data.backup

import androidx.annotation.StringRes
import io.github.tweeter042.alarmclock.core.data.R
import java.io.IOException

/**
 * Every failure returned by the backup manager (as `Result.failure`) is one of these.
 *
 * [message] is a technical English description for logs; [messageRes] is a localized, user-facing
 * explanation (English and Thai) that UI code can show directly.
 */
sealed class BackupException(message: String, cause: Throwable? = null) : IOException(message, cause) {
    @get:StringRes
    abstract val messageRes: Int
}

/** The input is not an Alarm Clock backup: not JSON, wrong `format`, or damaged content. */
class InvalidBackupException(message: String, cause: Throwable? = null) : BackupException(message, cause) {
    override val messageRes: Int get() = R.string.data_backup_error_invalid
}

/** The backup was written by a newer app version whose format this version cannot read. */
class UnsupportedBackupVersionException(
    val version: Int,
    val supportedVersion: Int,
) : BackupException("Backup format version $version is newer than the supported version $supportedVersion") {
    override val messageRes: Int get() = R.string.data_backup_error_version
}

/** No backup folder is configured, or its access permission was revoked / the folder is gone. */
class BackupFolderUnavailableException(message: String, cause: Throwable? = null) : BackupException(message, cause) {
    override val messageRes: Int get() = R.string.data_backup_error_folder
}

/** Reading or writing the backup (or the local database) failed. */
class BackupIoException(message: String, cause: Throwable? = null) : BackupException(message, cause) {
    override val messageRes: Int get() = R.string.data_backup_error_io
}
