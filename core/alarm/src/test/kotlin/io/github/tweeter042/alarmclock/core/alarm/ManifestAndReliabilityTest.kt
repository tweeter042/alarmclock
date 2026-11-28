package io.github.tweeter042.alarmclock.core.alarm

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.tweeter042.alarmclock.core.alarm.guard.AlarmGuardService
import io.github.tweeter042.alarmclock.core.alarm.receiver.AlarmTriggerReceiver
import io.github.tweeter042.alarmclock.core.alarm.receiver.BootReceiver
import io.github.tweeter042.alarmclock.core.alarm.receiver.NotificationActionReceiver
import io.github.tweeter042.alarmclock.core.alarm.reliability.DefaultReliabilityChecker
import io.github.tweeter042.alarmclock.core.alarm.ring.AlarmRingService
import io.github.tweeter042.alarmclock.core.alarm.testing.EngineTestRule
import io.github.tweeter042.alarmclock.core.alarm.testing.TestEngineApp
import io.github.tweeter042.alarmclock.core.model.engine.HealthItemId
import io.github.tweeter042.alarmclock.core.model.engine.HealthStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = TestEngineApp::class)
class ManifestAndReliabilityTest {

    @get:Rule
    val rule = EngineTestRule()

    private val app get() = rule.app
    private val pm get() = app.packageManager

    @Test fun everyEngineComponentIsDirectBootAwareInTheDefaultProcess() {
        val receivers = listOf(AlarmTriggerReceiver::class.java, BootReceiver::class.java, NotificationActionReceiver::class.java)
        receivers.forEach { type ->
            val info = pm.getReceiverInfo(ComponentName(app, type), PackageManager.ComponentInfoFlags.of(0))
            assertTrue("${type.simpleName} directBootAware", info.directBootAware)
            assertEquals("${type.simpleName} in the default process", app.packageName, info.processName)
        }
        val services = listOf(AlarmRingService::class.java, AlarmGuardService::class.java)
        services.forEach { type ->
            val info = pm.getServiceInfo(ComponentName(app, type), PackageManager.ComponentInfoFlags.of(0))
            assertTrue("${type.simpleName} directBootAware", info.directBootAware)
            assertEquals(app.packageName, info.processName)
            assertFalse(info.exported)
        }
        assertFalse(pm.getReceiverInfo(ComponentName(app, AlarmTriggerReceiver::class.java), PackageManager.ComponentInfoFlags.of(0)).exported)
        assertFalse(pm.getReceiverInfo(ComponentName(app, NotificationActionReceiver::class.java), PackageManager.ComponentInfoFlags.of(0)).exported)
        assertTrue(pm.getReceiverInfo(ComponentName(app, BootReceiver::class.java), PackageManager.ComponentInfoFlags.of(0)).exported)

        val ring = pm.getServiceInfo(ComponentName(app, AlarmRingService::class.java), PackageManager.ComponentInfoFlags.of(0))
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            ring.foregroundServiceType,
        )
        val guard = pm.getServiceInfo(ComponentName(app, AlarmGuardService::class.java), PackageManager.ComponentInfoFlags.of(0))
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE, guard.foregroundServiceType)
    }

    @Test fun theBootReceiverListensToEverySystemEvent() {
        listOf(
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.TIME_SET",
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        ).forEach { action ->
            val receivers = pm.queryBroadcastReceivers(Intent(action).setPackage(app.packageName), PackageManager.ResolveInfoFlags.of(0))
            assertTrue(action, receivers.any { it.activityInfo.name == BootReceiver::class.java.name })
        }
    }

    @Test fun thePermissionsTheEngineNeedsAreDeclared() {
        val requested = pm.getPackageInfo(app.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
            .requestedPermissions.orEmpty().toSet()
        listOf(
            "android.permission.USE_EXACT_ALARM",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "android.permission.WAKE_LOCK",
            "android.permission.VIBRATE",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.USE_FULL_SCREEN_INTENT",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED",
            "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
            "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
            "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
            "android.permission.ACCESS_NOTIFICATION_POLICY",
            "android.permission.MODIFY_AUDIO_SETTINGS",
        ).forEach { assertTrue(it, it in requested) }
    }

    private fun checker() = DefaultReliabilityChecker(app, app.data, app.alarmEngine)

    @Test fun theHealthCheckReportsEveryItem() {
        rule.startEngine()
        val items = runBlocking { checker().check() }
        assertEquals(HealthItemId.entries.toList(), items.map { it.id })
        val byId = items.associateBy { it.id }
        assertEquals(HealthStatus.OK, byId.getValue(HealthItemId.ALARM_CHANNEL).status)
        assertEquals(HealthStatus.OK, byId.getValue(HealthItemId.EXACT_ALARMS).status)
        assertEquals(HealthStatus.OK, byId.getValue(HealthItemId.ALARMS_REGISTERED).status)
        assertEquals(HealthStatus.NOT_APPLICABLE, byId.getValue(HealthItemId.WATCH_APP).status)
        assertEquals(HealthStatus.NOT_APPLICABLE, byId.getValue(HealthItemId.GUARD_SERVICE).status)
        assertEquals(HealthStatus.WARNING, byId.getValue(HealthItemId.TEST_ALARM).status)
        assertTrue(byId.getValue(HealthItemId.NOTIFICATIONS).required)
        assertTrue(byId.getValue(HealthItemId.BATTERY_UNRESTRICTED).required)
        assertTrue(byId.getValue(HealthItemId.FULL_SCREEN_INTENT).fixable)
        assertFalse(byId.getValue(HealthItemId.DND_ACCESS).required)
        assertEquals(HealthStatus.NOT_APPLICABLE, byId.getValue(HealthItemId.SAMSUNG_NEVER_SLEEPING).status)
    }

    @Test fun samsungItemsAreConfirmedByTheUser() {
        ShadowBuild.setManufacturer("samsung")
        rule.startEngine()
        val checker = checker()
        assertTrue(checker.isSamsungDevice())
        val before = runBlocking { checker.check() }.associateBy { it.id }
        assertEquals(HealthStatus.MANUAL_UNCONFIRMED, before.getValue(HealthItemId.SAMSUNG_NEVER_SLEEPING).status)
        assertTrue(before.getValue(HealthItemId.SAMSUNG_NEVER_SLEEPING).required)
        assertFalse(before.getValue(HealthItemId.SAMSUNG_RECENTS_LOCK).fixable)

        runBlocking { checker.setManualConfirmed(HealthItemId.SAMSUNG_NEVER_SLEEPING, true) }
        val after = runBlocking { checker.check() }.associateBy { it.id }
        assertEquals(HealthStatus.MANUAL_CONFIRMED, after.getValue(HealthItemId.SAMSUNG_NEVER_SLEEPING).status)
        assertEquals(mapOf("SAMSUNG_NEVER_SLEEPING" to true), runBlocking { app.data.settings.current() }.reliability.manualChecks)
        assertEquals("samsung", Build.MANUFACTURER)
    }

    @Test fun fixIntentsPutSamsungScreensFirstAndAndroidScreensLast() {
        val intents = checker().fixIntents(app, HealthItemId.SAMSUNG_NEVER_SLEEPING)
        assertEquals("com.samsung.android.lool", intents.first().component?.packageName)
        assertEquals(Settings.ACTION_SETTINGS, intents.last().action)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intents[intents.size - 2].action)
        assertTrue(intents.all { it.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0 })

        val battery = checker().fixIntents(app, HealthItemId.BATTERY_UNRESTRICTED)
        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, battery.first().action)
        assertEquals("package:${app.packageName}", battery.first().dataString)
        assertEquals(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, checker().fixIntents(app, HealthItemId.FULL_SCREEN_INTENT).first().action)
        assertTrue(checker().fixIntents(app, HealthItemId.TEST_ALARM).isEmpty())
        assertNull(checker().fixIntents(app, HealthItemId.ALARM_VOLUME).first().component)
    }
}
