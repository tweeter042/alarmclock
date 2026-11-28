package io.github.tweeter042.alarmclock.core.model.util

/**
 * An error that carries a localized, user-facing explanation as an Android string resource id.
 *
 * Resource ids are global in the merged app, so UI modules can show the message with
 * `stringResource(error.messageRes)` without depending on the module that defined the string.
 * Example: core:data's BackupException subclasses implement this.
 */
interface LocalizedError {
    val messageRes: Int
}
