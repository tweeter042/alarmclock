package io.github.tweeter042.alarmclock.core.alarm.work

import android.content.Context
import android.os.UserManager
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.tweeter042.alarmclock.core.alarm.EngineLog
import io.github.tweeter042.alarmclock.core.alarm.alarmEngineOrNull
import java.util.concurrent.TimeUnit

/**
 * The watchdog: every 15 minutes verifies that every expected trigger is still registered with
 * AlarmManager (and that the system's next alarm clock is ours) and repairs the schedule if not.
 * Created by WorkManager, so it must stay public with this constructor.
 */
class IntegrityWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val engine = applicationContext.alarmEngineOrNull() ?: return Result.success()
        return try {
            val report = engine.integrityReport(repair = true)
            if (report.repaired) EngineLog.reliability.w("Watchdog repaired the schedule (${report.registered}/${report.expected} were registered)")
            Result.success()
        } catch (t: Throwable) {
            EngineLog.reliability.e("Watchdog check failed", t)
            Result.retry()
        }
    }
}

/**
 * Enqueues [IntegrityWorker] (unique periodic work, KEEP). WorkManager lives in credential-encrypted
 * storage, so this only happens once the user has unlocked the device; WorkManager is initialized
 * on demand (the default startup initializer is removed in this module's manifest).
 */
internal object Watchdog {
    const val UNIQUE_WORK = "alarmclock.engine.integrity"
    const val INTERVAL_MINUTES = 15L

    fun enqueueIfUnlocked(context: Context) {
        val unlocked = runCatching { context.getSystemService(UserManager::class.java)?.isUserUnlocked == true }.getOrDefault(false)
        if (!unlocked) return
        try {
            val workManager = workManager(context) ?: return
            workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<IntegrityWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES).build(),
            )
        } catch (t: Throwable) {
            EngineLog.reliability.w("Watchdog not enqueued", t)
        }
    }

    private fun workManager(context: Context): WorkManager? = try {
        WorkManager.getInstance(context)
    } catch (notInitialized: IllegalStateException) {
        try {
            WorkManager.initialize(context.applicationContext, Configuration.Builder().build())
            WorkManager.getInstance(context)
        } catch (t: Throwable) {
            EngineLog.reliability.w("WorkManager unavailable", t)
            null
        }
    }
}
