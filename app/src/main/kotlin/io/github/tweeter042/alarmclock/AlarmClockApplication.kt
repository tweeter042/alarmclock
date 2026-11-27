package io.github.tweeter042.alarmclock

import android.app.Application
import io.github.tweeter042.alarmclock.core.model.AppGraph
import io.github.tweeter042.alarmclock.core.model.AppGraphProvider

/**
 * Application entry point. Builds the manual dependency graph lazily.
 *
 * Direct Boot: this class also runs before the first unlock after a reboot. Nothing here may touch
 * credential-encrypted storage eagerly (WorkManager is initialized on demand, after unlock).
 */
class AlarmClockApplication : Application(), AppGraphProvider {
    override val appGraph: AppGraph by lazy { AppGraphFactory.create(this) }
}
