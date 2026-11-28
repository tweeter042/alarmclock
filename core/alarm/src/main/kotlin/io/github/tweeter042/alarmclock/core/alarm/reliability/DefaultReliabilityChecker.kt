package io.github.tweeter042.alarmclock.core.alarm.reliability

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.IntentCompat
import androidx.core.content.PackageManagerCompat
import androidx.core.content.UnusedAppRestrictionsConstants
import io.github.tweeter042.alarmclock.core.alarm.DefaultAlarmEngine
import io.github.tweeter042.alarmclock.core.alarm.EngineLog
import io.github.tweeter042.alarmclock.core.alarm.guard.AlarmGuardService
import io.github.tweeter042.alarmclock.core.alarm.internal.guardedOrNull
import io.github.tweeter042.alarmclock.core.alarm.notify.EngineChannels
import io.github.tweeter042.alarmclock.core.data.DataGraph
import io.github.tweeter042.alarmclock.core.model.engine.HealthItem
import io.github.tweeter042.alarmclock.core.model.engine.HealthItemId
import io.github.tweeter042.alarmclock.core.model.engine.HealthStatus
import io.github.tweeter042.alarmclock.core.model.engine.ReliabilityChecker
import io.github.tweeter042.alarmclock.core.model.service.WearBridge
import io.github.tweeter042.alarmclock.core.model.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * The health check behind Settings › Health check and the Samsung setup wizard.
 *
 * [check] reports every [HealthItemId] (in enum order). Required items can stop alarms from ringing
 * when they fail. Samsung settings that apps cannot read (Never sleeping apps, Put unused apps to
 * sleep, Device care auto optimization, Recents lock) are MANUAL_* from
 * `settings.reliability.manualChecks`, confirmed by the user with [setManualConfirmed], and
 * NOT_APPLICABLE on other brands.
 *
 * [fixIntents] returns candidates most specific first; start the first that resolves. The Samsung
 * component names are best-effort candidates from earlier One UI versions and are UNVERIFIED on
 * One UI 9 (Samsung moves these screens); Android settings screens follow as fallbacks.
 * APP_HIBERNATION's first intent must be started with startActivityForResult (no NEW_TASK flag).
 */
class DefaultReliabilityChecker(
    context: Context,
    private val data: DataGraph,
    private val engine: DefaultAlarmEngine,
    private val wear: WearBridge? = null,
) : ReliabilityChecker {

    private val appContext: Context = context.applicationContext ?: context
    private val log = EngineLog.reliability

    override suspend fun check(): List<HealthItem> = withContext(Dispatchers.IO) {
        val settings = guardedOrNull("Reading settings", TIMEOUT_MILLIS, log) { data.settings.current() } ?: AppSettings()
        val samsung = isSamsungDevice()
        HealthItemId.entries.map { id ->
            try {
                checkItem(id, settings, samsung)
            } catch (t: Throwable) {
                log.w("Health check $id failed", t)
                item(id, HealthStatus.WARNING, "check failed")
            }
        }
    }

    private suspend fun checkItem(id: HealthItemId, settings: AppSettings, samsung: Boolean): HealthItem = when (id) {
        HealthItemId.NOTIFICATIONS ->
            item(id, if (NotificationManagerCompat.from(appContext).areNotificationsEnabled()) HealthStatus.OK else HealthStatus.ERROR)
        HealthItemId.ALARM_CHANNEL -> alarmChannel()
        HealthItemId.FULL_SCREEN_INTENT -> {
            val manager = appContext.getSystemService(NotificationManager::class.java)
            item(id, if (manager?.canUseFullScreenIntent() == true) HealthStatus.OK else HealthStatus.ERROR)
        }
        HealthItemId.EXACT_ALARMS -> {
            val manager = appContext.getSystemService(AlarmManager::class.java)
            item(id, if (manager?.canScheduleExactAlarms() == true) HealthStatus.OK else HealthStatus.ERROR)
        }
        HealthItemId.BATTERY_UNRESTRICTED -> {
            val power = appContext.getSystemService(PowerManager::class.java)
            item(id, if (power?.isIgnoringBatteryOptimizations(appContext.packageName) == true) HealthStatus.OK else HealthStatus.ERROR)
        }
        HealthItemId.BACKGROUND_RESTRICTION -> {
            val activity = appContext.getSystemService(ActivityManager::class.java)
            item(id, if (activity?.isBackgroundRestricted == true) HealthStatus.ERROR else HealthStatus.OK)
        }
        HealthItemId.STANDBY_BUCKET -> standbyBucket()
        HealthItemId.APP_HIBERNATION -> hibernation()
        HealthItemId.DND_ACCESS -> {
            val manager = appContext.getSystemService(NotificationManager::class.java)
            item(id, if (manager?.isNotificationPolicyAccessGranted == true) HealthStatus.OK else HealthStatus.WARNING)
        }
        HealthItemId.ALARM_VOLUME -> {
            val audio = appContext.getSystemService(AudioManager::class.java)
            val volume = audio?.getStreamVolume(AudioManager.STREAM_ALARM) ?: -1
            val max = audio?.getStreamMaxVolume(AudioManager.STREAM_ALARM) ?: -1
            item(id, if (volume == 0) HealthStatus.WARNING else HealthStatus.OK, "volume=$volume/$max")
        }
        HealthItemId.ALARMS_REGISTERED -> {
            val report = guardedOrNull("Integrity check", INTEGRITY_TIMEOUT_MILLIS, log) { engine.integrityReport(repair = true) }
            when {
                report == null -> item(id, HealthStatus.WARNING, "check timed out")
                report.missingSourceIds.isEmpty() && report.systemNextAlarmMatches ->
                    item(id, HealthStatus.OK, "registered=${report.registered}/${report.expected}")
                report.repaired -> item(id, HealthStatus.WARNING, "repaired; registered=${report.registered}/${report.expected}")
                else -> item(id, HealthStatus.ERROR, "registered=${report.registered}/${report.expected}")
            }
        }
        HealthItemId.SAMSUNG_NEVER_SLEEPING, HealthItemId.SAMSUNG_UNUSED_APPS_SLEEP,
        HealthItemId.SAMSUNG_AUTO_OPTIMIZATION, HealthItemId.SAMSUNG_RECENTS_LOCK ->
            if (!samsung) {
                item(id, HealthStatus.NOT_APPLICABLE)
            } else {
                val confirmed = settings.reliability.manualChecks[id.name] == true
                item(id, if (confirmed) HealthStatus.MANUAL_CONFIRMED else HealthStatus.MANUAL_UNCONFIRMED)
            }
        HealthItemId.GUARD_SERVICE -> when {
            !settings.reliability.guardServiceEnabled -> item(id, HealthStatus.NOT_APPLICABLE, "off")
            AlarmGuardService.isRunning -> item(id, HealthStatus.OK, "running")
            else -> item(id, HealthStatus.WARNING, "enabled but not running")
        }
        HealthItemId.WATCH_APP -> {
            val bridge = wear
            if (bridge == null) {
                item(id, HealthStatus.NOT_APPLICABLE)
            } else {
                val nodes = guardedOrNull("Finding the watch", WATCH_TIMEOUT_MILLIS, log) { bridge.refreshNodes() }
                when {
                    nodes == null -> item(id, HealthStatus.WARNING, "no answer")
                    nodes.isEmpty() -> item(id, HealthStatus.WARNING, "nodes=0")
                    else -> item(id, HealthStatus.OK, "nodes=${nodes.size} nearby=${nodes.count { it.nearby }}")
                }
            }
        }
        HealthItemId.TEST_ALARM -> {
            val reliability = settings.reliability
            when (reliability.lastTestOk) {
                true -> item(id, HealthStatus.OK, reliability.lastTestAt?.let { "lastTestAt=$it" })
                false -> item(id, HealthStatus.ERROR, reliability.lastTestAt?.let { "lastTestAt=$it" })
                null -> item(id, HealthStatus.WARNING, "never tested")
            }
        }
    }

    private fun alarmChannel(): HealthItem {
        val manager = appContext.getSystemService(NotificationManager::class.java)
            ?: return item(HealthItemId.ALARM_CHANNEL, HealthStatus.ERROR, "no notification manager")
        var channel = manager.getNotificationChannel(EngineChannels.ALARMS)
        if (channel == null) {
            EngineChannels.createAll(appContext)
            channel = manager.getNotificationChannel(EngineChannels.ALARMS)
        }
        val importance = channel?.importance ?: NotificationManager.IMPORTANCE_NONE
        return item(
            HealthItemId.ALARM_CHANNEL,
            if (importance >= NotificationManager.IMPORTANCE_HIGH) HealthStatus.OK else HealthStatus.ERROR,
            "importance=$importance",
        )
    }

    private fun standbyBucket(): HealthItem {
        val usage = appContext.getSystemService(UsageStatsManager::class.java)
            ?: return item(HealthItemId.STANDBY_BUCKET, HealthStatus.NOT_APPLICABLE)
        val bucket = usage.appStandbyBucket
        val status = when {
            bucket >= UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> HealthStatus.ERROR
            bucket >= UsageStatsManager.STANDBY_BUCKET_RARE -> HealthStatus.WARNING
            else -> HealthStatus.OK
        }
        return item(HealthItemId.STANDBY_BUCKET, status, "bucket=${bucketName(bucket)}")
    }

    private fun hibernation(): HealthItem {
        val status = try {
            PackageManagerCompat.getUnusedAppRestrictionsStatus(appContext).get(HIBERNATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (t: Throwable) {
            log.w("Unused-app restrictions unknown", t)
            UnusedAppRestrictionsConstants.ERROR
        }
        return when (status) {
            UnusedAppRestrictionsConstants.API_30_BACKPORT, UnusedAppRestrictionsConstants.API_30, UnusedAppRestrictionsConstants.API_31 ->
                item(HealthItemId.APP_HIBERNATION, HealthStatus.WARNING, "enabled")
            UnusedAppRestrictionsConstants.DISABLED -> item(HealthItemId.APP_HIBERNATION, HealthStatus.OK, "disabled")
            else -> item(HealthItemId.APP_HIBERNATION, HealthStatus.NOT_APPLICABLE)
        }
    }

    private fun item(id: HealthItemId, status: HealthStatus, detail: String? = null) = HealthItem(
        id = id,
        status = status,
        detail = detail,
        fixable = fixIntents(appContext, id).isNotEmpty(),
        required = id in REQUIRED,
    )

    // Sideloaded alarm clock: asking for the battery-optimization exemption is the point of this item.
    @SuppressLint("BatteryLife")
    override fun fixIntents(context: Context, id: HealthItemId): List<Intent> {
        val packageName = context.packageName
        val packageUri = Uri.fromParts("package", packageName, null)
        val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        val appNotifications = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        val intents: List<Intent> = when (id) {
            HealthItemId.NOTIFICATIONS -> listOf(appNotifications, appDetails)
            HealthItemId.ALARM_CHANNEL -> listOf(
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, EngineChannels.ALARMS),
                appNotifications,
                appDetails,
            )
            HealthItemId.FULL_SCREEN_INTENT -> listOf(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri), appNotifications, appDetails)
            HealthItemId.EXACT_ALARMS -> listOf(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri), appDetails)
            HealthItemId.BATTERY_UNRESTRICTED -> listOf(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri),
                appDetails,
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            )
            HealthItemId.BACKGROUND_RESTRICTION, HealthItemId.STANDBY_BUCKET -> listOf(appDetails)
            HealthItemId.APP_HIBERNATION -> listOfNotNull(
                runCatching { IntentCompat.createManageUnusedAppRestrictionsIntent(context, packageName) }.getOrNull(),
                appDetails,
            )
            HealthItemId.DND_ACCESS -> listOf(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            HealthItemId.ALARM_VOLUME -> listOf(Intent(Settings.ACTION_SOUND_SETTINGS))
            HealthItemId.SAMSUNG_NEVER_SLEEPING, HealthItemId.SAMSUNG_UNUSED_APPS_SLEEP ->
                SamsungSettings.batteryCandidates() + listOf(appDetails, Intent(Settings.ACTION_SETTINGS))
            HealthItemId.SAMSUNG_AUTO_OPTIMIZATION ->
                SamsungSettings.deviceCareCandidates() + listOf(appDetails, Intent(Settings.ACTION_SETTINGS))
            // Recents lock is done in the Recents screen itself; there is no settings page to open.
            HealthItemId.SAMSUNG_RECENTS_LOCK, HealthItemId.ALARMS_REGISTERED, HealthItemId.GUARD_SERVICE,
            HealthItemId.WATCH_APP, HealthItemId.TEST_ALARM,
            -> emptyList()
        }
        return intents.map { intent ->
            if (id == HealthItemId.APP_HIBERNATION && intent !== intents.lastOrNull()) intent else intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    override suspend fun setManualConfirmed(id: HealthItemId, confirmed: Boolean) {
        data.settings.update { settings ->
            settings.copy(
                reliability = settings.reliability.copy(manualChecks = settings.reliability.manualChecks + (id.name to confirmed)),
            )
        }
    }

    override fun isSamsungDevice(): Boolean = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    private companion object {
        const val TIMEOUT_MILLIS = 3_000L
        const val INTEGRITY_TIMEOUT_MILLIS = 20_000L
        const val WATCH_TIMEOUT_MILLIS = 3_000L
        const val HIBERNATION_TIMEOUT_SECONDS = 3L

        val REQUIRED: Set<HealthItemId> = setOf(
            HealthItemId.NOTIFICATIONS,
            HealthItemId.ALARM_CHANNEL,
            HealthItemId.FULL_SCREEN_INTENT,
            HealthItemId.EXACT_ALARMS,
            HealthItemId.BATTERY_UNRESTRICTED,
            HealthItemId.BACKGROUND_RESTRICTION,
            HealthItemId.ALARMS_REGISTERED,
            HealthItemId.SAMSUNG_NEVER_SLEEPING,
            HealthItemId.SAMSUNG_UNUSED_APPS_SLEEP,
        )

        fun bucketName(bucket: Int): String = when (bucket) {
            UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "ACTIVE"
            UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "WORKING_SET"
            UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "FREQUENT"
            UsageStatsManager.STANDBY_BUCKET_RARE -> "RARE"
            UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> "RESTRICTED"
            else -> bucket.toString()
        }
    }
}

/**
 * Samsung Device care screens (package com.samsung.android.lool). UNVERIFIED on One UI 9: these are
 * the component names used by earlier One UI releases, tried in order; callers fall back to the
 * Android settings screens that follow them.
 */
internal object SamsungSettings {
    private const val DEVICE_CARE = "com.samsung.android.lool"
    private const val BATTERY = "com.samsung.android.sm.battery.ui.BatteryActivity"
    private const val BATTERY_LEGACY = "com.samsung.android.sm.ui.battery.BatteryActivity"
    private const val APP_LIST = "com.samsung.android.sm.battery.ui.usage.CheckableAppListActivity"
    private const val DASHBOARD = "com.samsung.android.sm.ui.dashboard.SmartManagerDashBoardActivity"

    /** Battery › Background usage limits (Never sleeping apps, Put unused apps to sleep). */
    fun batteryCandidates(): List<Intent> = listOf(BATTERY, BATTERY_LEGACY, APP_LIST, DASHBOARD).map(::component)

    /** Device care home (Auto optimization lives in its menu). */
    fun deviceCareCandidates(): List<Intent> = listOf(DASHBOARD, BATTERY, BATTERY_LEGACY, APP_LIST).map(::component)

    private fun component(className: String): Intent = Intent().setComponent(ComponentName(DEVICE_CARE, className))
}
