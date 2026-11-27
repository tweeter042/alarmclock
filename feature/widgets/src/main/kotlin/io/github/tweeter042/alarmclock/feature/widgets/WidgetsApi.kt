package io.github.tweeter042.alarmclock.feature.widgets

import android.content.Context

/* PUBLIC API of :feature:widgets. Signatures are FROZEN. */
object WidgetsApi {
    /** Refreshes every home-screen widget and Quick Settings tile (call after schedule/theme changes). */
    suspend fun refreshAll(context: Context) {}
}
