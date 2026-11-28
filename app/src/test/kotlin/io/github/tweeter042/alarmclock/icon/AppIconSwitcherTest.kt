package io.github.tweeter042.alarmclock.icon

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.tweeter042.alarmclock.MainActivity
import io.github.tweeter042.alarmclock.TestApplication
import io.github.tweeter042.alarmclock.core.model.theme.AppIconVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = TestApplication::class)
class AppIconSwitcherTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val pm: PackageManager get() = context.packageManager

    private fun launcherEntries(): List<String> =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName), 0)
            .map { it.activityInfo.name }

    @Suppress("DEPRECATION")
    @Test
    fun everyVariantHasADeclaredAliasOfMainActivity() {
        val switcher = AppIconSwitcher(context)
        assertEquals(AppIconVariant.entries, switcher.available)
        AppIconVariant.entries.forEach { variant ->
            val info = pm.getActivityInfo(switcher.componentOf(variant), PackageManager.MATCH_DISABLED_COMPONENTS)
            assertEquals("io.github.tweeter042.alarmclock.launcher.${variant.aliasSuffix}", info.name)
            assertEquals(MainActivity::class.java.name, info.targetActivity)
            assertTrue(info.exported)
            assertEquals("only Default is enabled in the manifest", variant == AppIconVariant.DEFAULT, info.enabled)
        }
    }

    @Test
    fun defaultIconIsActiveAfterInstall() {
        assertEquals(AppIconVariant.DEFAULT, AppIconSwitcher(context).current())
        assertEquals(listOf(AppIconSwitcher.aliasClassName(AppIconVariant.DEFAULT)), launcherEntries())
    }

    @Test
    fun switchingEnablesExactlyOneAlias() {
        val switcher = AppIconSwitcher(context)
        switcher.set(AppIconVariant.NEON)
        assertEquals(AppIconVariant.NEON, switcher.current())
        AppIconVariant.entries.forEach { variant ->
            val expected = if (variant == AppIconVariant.NEON) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            assertEquals(variant.name, expected, pm.getComponentEnabledSetting(switcher.componentOf(variant)))
        }
        assertEquals(listOf(AppIconSwitcher.aliasClassName(AppIconVariant.NEON)), launcherEntries())

        switcher.set(AppIconVariant.MIDNIGHT)
        assertEquals(AppIconVariant.MIDNIGHT, switcher.current())
        switcher.set(AppIconVariant.DEFAULT)
        assertEquals(AppIconVariant.DEFAULT, switcher.current())
        assertEquals(listOf(AppIconSwitcher.aliasClassName(AppIconVariant.DEFAULT)), launcherEntries())
    }

    @Test
    fun aStateLeftAtDefaultFollowsTheManifest() {
        val switcher = AppIconSwitcher(context)
        switcher.set(AppIconVariant.DAYLIGHT)
        // Back to "as declared": Default is enabled by the manifest, Daylight is not.
        AppIconVariant.entries.forEach {
            pm.setComponentEnabledSetting(switcher.componentOf(it), PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP)
        }
        assertEquals(AppIconVariant.DEFAULT, switcher.current())
    }
}
