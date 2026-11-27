package io.github.tweeter042.alarmclock.feature.ringing

import android.content.Context
import android.content.Intent

/*
 * PUBLIC API of :feature:ringing. Signatures are FROZEN.
 * The app's RingUiLauncher delegates here; core:alarm uses those intents for full-screen
 * notifications. Activities are declared in this module's manifest.
 */
object RingScreens {
    const val EXTRA_RING_ID = "io.github.tweeter042.alarmclock.extra.RING_ID"
    const val EXTRA_ALARM_ID = "io.github.tweeter042.alarmclock.extra.ALARM_ID"
    const val EXTRA_OCCURRENCE_AT = "io.github.tweeter042.alarmclock.extra.OCCURRENCE_AT"

    /** Full-screen ring UI (also used for timers and the awake check). */
    fun ringIntent(context: Context, ringId: String): Intent =
        Intent(Intent.ACTION_MAIN).setPackage(context.packageName).putExtra(EXTRA_RING_ID, ringId)

    /** Full-screen sunrise (gradual brightness) UI shown before an alarm. */
    fun sunriseIntent(context: Context, alarmId: Long, occurrenceAt: Long): Intent =
        Intent(Intent.ACTION_MAIN).setPackage(context.packageName)
            .putExtra(EXTRA_ALARM_ID, alarmId)
            .putExtra(EXTRA_OCCURRENCE_AT, occurrenceAt)
}
