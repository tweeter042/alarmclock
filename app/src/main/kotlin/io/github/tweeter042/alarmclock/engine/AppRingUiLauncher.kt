package io.github.tweeter042.alarmclock.engine

import android.content.Context
import android.content.Intent
import io.github.tweeter042.alarmclock.core.model.engine.RingUiLauncher
import io.github.tweeter042.alarmclock.core.ui.navigation.MainIntents
import io.github.tweeter042.alarmclock.feature.ringing.RingScreens

/** Screens the alarm engine opens: ring/sunrise activities (feature:ringing) and the main activity. */
internal object AppRingUiLauncher : RingUiLauncher {
    override fun ringScreenIntent(context: Context, ringId: String): Intent =
        RingScreens.ringIntent(context, ringId)

    override fun sunriseIntent(context: Context, alarmId: Long, occurrenceAt: Long): Intent =
        RingScreens.sunriseIntent(context, alarmId, occurrenceAt)

    override fun mainIntent(context: Context): Intent = MainIntents.main(context)

    override fun alarmEditorIntent(context: Context, alarmId: Long): Intent =
        MainIntents.openAlarm(context, alarmId)
}
