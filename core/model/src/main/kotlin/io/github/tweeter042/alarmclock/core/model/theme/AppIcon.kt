package io.github.tweeter042.alarmclock.core.model.theme

/**
 * Launcher icon variants the user can choose in Settings › Appearance › App icon.
 *
 * Each variant is an `<activity-alias>` of MainActivity in the app manifest
 * (component name = applicationId + ".launcher." + [aliasSuffix]); exactly one is enabled.
 * Icon resources (adaptive icon layers) live in core:designsystem so settings can preview them:
 * `ic_launcher_background/foreground` for DEFAULT and `ic_launcher_alt_<id>_background/foreground`
 * for the others, all sharing `ic_launcher_monochrome`.
 */
enum class AppIconVariant(val id: String, val aliasSuffix: String) {
    DEFAULT("default", "Default"),
    MIDNIGHT("midnight", "Midnight"),
    DAYLIGHT("daylight", "Daylight"),
    NEON("neon", "Neon");

    companion object {
        fun byId(id: String?): AppIconVariant = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** Switches the launcher icon. Implemented in :app (it owns the activity-aliases). */
interface AppIconController {
    /** Variants available in this build, in display order. */
    val available: List<AppIconVariant>

    /** The variant whose alias is currently enabled. */
    fun current(): AppIconVariant

    /**
     * Enables [variant]'s alias and disables the others (PackageManager, DONT_KILL_APP).
     * The launcher may take a few seconds to refresh; on One UI a home-screen shortcut can disappear
     * and must be re-added from the app drawer.
     */
    fun set(variant: AppIconVariant)
}
