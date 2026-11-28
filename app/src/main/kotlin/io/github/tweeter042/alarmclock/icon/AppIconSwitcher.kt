package io.github.tweeter042.alarmclock.icon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import io.github.tweeter042.alarmclock.MainActivity
import io.github.tweeter042.alarmclock.core.model.theme.AppIconController
import io.github.tweeter042.alarmclock.core.model.theme.AppIconVariant

/**
 * Switches the launcher icon by enabling one `<activity-alias>` of MainActivity
 * (`.launcher.<aliasSuffix>` in the app manifest) and disabling the others.
 *
 * The chosen alias is enabled before the others are disabled, so the app never has zero launcher
 * entries. `DONT_KILL_APP` keeps the app running; the launcher refreshes after a few seconds.
 */
class AppIconSwitcher(context: Context) : AppIconController {
    private val packageName = context.packageName
    private val packageManager = context.applicationContext?.packageManager ?: context.packageManager

    override val available: List<AppIconVariant> = AppIconVariant.entries

    override fun current(): AppIconVariant = available.firstOrNull(::isEnabled) ?: AppIconVariant.DEFAULT

    override fun set(variant: AppIconVariant) {
        try {
            packageManager.setComponentEnabledSetting(
                componentOf(variant),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            available.filter { it != variant }.forEach { other ->
                packageManager.setComponentEnabledSetting(
                    componentOf(other),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        } catch (e: RuntimeException) {
            // SecurityException / IllegalArgumentException: keep whatever icon is active.
            Log.w(TAG, "Could not switch the app icon (${e.javaClass.simpleName})")
        }
    }

    /** The alias component of [variant]. */
    fun componentOf(variant: AppIconVariant): ComponentName = ComponentName(packageName, aliasClassName(variant))

    private fun isEnabled(variant: AppIconVariant): Boolean {
        val component = componentOf(variant)
        return when (packageManager.getComponentEnabledSetting(component)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            // DEFAULT means "as declared in the manifest".
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> declaredEnabled(component, variant)
            else -> false
        }
    }

    // The Int-flags overload works on every API level (and in Robolectric).
    @Suppress("DEPRECATION")
    private fun declaredEnabled(component: ComponentName, variant: AppIconVariant): Boolean = try {
        packageManager.getActivityInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS).enabled
    } catch (e: PackageManager.NameNotFoundException) {
        variant == AppIconVariant.DEFAULT
    }

    companion object {
        private const val TAG = "AlarmClock/AppIcon"

        /**
         * Aliases are declared as ".launcher.<suffix>", which resolves against the app's namespace
         * (MainActivity's package), not the application id.
         */
        private val aliasPrefix: String = MainActivity::class.java.name.substringBeforeLast('.') + ".launcher."

        fun aliasClassName(variant: AppIconVariant): String = aliasPrefix + variant.aliasSuffix
    }
}
