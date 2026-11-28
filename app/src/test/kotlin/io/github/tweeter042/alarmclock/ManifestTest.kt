package io.github.tweeter042.alarmclock

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import io.github.tweeter042.alarmclock.core.ui.navigation.MainIntents
import io.github.tweeter042.alarmclock.core.ui.navigation.TopLevelRoutes
import io.github.tweeter042.alarmclock.shell.TopLevelDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Checks of the merged app manifest and other wiring that must not regress. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = TestApplication::class)
class ManifestTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val pm: PackageManager get() = context.packageManager

    @Suppress("DEPRECATION")
    @Test
    fun workManagerIsNotStartedByAppStartup() {
        val provider = pm.getProviderInfo(ComponentName(context, "androidx.startup.InitializationProvider"), PackageManager.GET_META_DATA)
        val keys = provider.metaData?.keySet().orEmpty()
        assertFalse("WorkManagerInitializer must be removed (Direct Boot)", "androidx.work.WorkManagerInitializer" in keys)
        assertTrue(Configuration.Provider::class.java.isAssignableFrom(AlarmClockApplication::class.java))
    }

    @Test
    fun everyComponentRunsInTheDefaultProcess() {
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or
            PackageManager.GET_PROVIDERS or PackageManager.MATCH_DISABLED_COMPONENTS
        val info = pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        val components = info.activities.orEmpty().toList() + info.services.orEmpty() + info.receivers.orEmpty() + info.providers.orEmpty()
        assertTrue(components.isNotEmpty())
        components.forEach { component ->
            assertEquals("${component.name} must run in the default process", context.packageName, component.processName ?: context.packageName)
        }
    }

    @Suppress("DEPRECATION")
    @Test
    fun mainActivityStartsWithTheSplashThemeAndHasNoLauncherFilter() {
        val main = pm.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        assertEquals(R.style.Theme_AlarmClock_Splash, main.theme)
        assertEquals(MainIntents.MAIN_ACTIVITY_CLASS, MainActivity::class.java.name)
        val launchers = pm.queryIntentActivities(
            android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                .setPackage(context.packageName),
            0,
        )
        assertTrue(launchers.none { it.activityInfo.name == MainActivity::class.java.name })
    }

    @Test
    fun bottomBarFollowsTheTopLevelRoutes() {
        assertEquals(TopLevelRoutes.all, TopLevelDestination.entries.map { it.route })
    }
}
