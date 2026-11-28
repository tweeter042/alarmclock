package io.github.tweeter042.alarmclock.core.ui.navigation

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The extras → route mapping is pure; the Intent round trip runs on Robolectric. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MainIntentsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun everyRouteValueMapsToItsScreen() {
        val expected = mapOf(
            MainIntents.ROUTE_NEW_ALARM to AlarmEditRoute(),
            MainIntents.ROUTE_TIMER to TimerRoute,
            MainIntents.ROUTE_STOPWATCH to StopwatchRoute,
            MainIntents.ROUTE_BEDSIDE to BedsideClockRoute,
            MainIntents.ROUTE_SLEEP to SleepRoute,
            MainIntents.ROUTE_WORLD_CLOCK to WorldClockRoute,
            MainIntents.ROUTE_SETTINGS to SettingsRoute,
            MainIntents.ROUTE_RELIABILITY to ReliabilityRoute,
        )
        assertEquals(MainIntents.ROUTES.toSet(), expected.keys)
        expected.forEach { (route, destination) ->
            assertEquals(route, destination, MainIntents.destinationOf(MainIntentExtras(route = route)))
        }
    }

    @Test
    fun alarmIdWinsOverTheRoute() {
        assertEquals(AlarmEditRoute(alarmId = 42), MainIntents.destinationOf(MainIntentExtras(alarmId = 42, route = MainIntents.ROUTE_TIMER)))
        // Unsaved / invalid ids fall back to the route.
        assertEquals(TimerRoute, MainIntents.destinationOf(MainIntentExtras(alarmId = 0, route = MainIntents.ROUTE_TIMER)))
        assertNull(MainIntents.destinationOf(MainIntentExtras(alarmId = -3)))
    }

    @Test
    fun newAlarmPresetIsValidated() {
        assertEquals(
            AlarmEditRoute(presetHour = 6, presetMinute = 45),
            MainIntents.destinationOf(MainIntentExtras(route = MainIntents.ROUTE_NEW_ALARM, presetHour = 6, presetMinute = 45)),
        )
        assertEquals(AlarmEditRoute(), MainIntents.destinationOf(MainIntentExtras(route = MainIntents.ROUTE_NEW_ALARM, presetHour = 25, presetMinute = 0)))
        assertEquals(AlarmEditRoute(), MainIntents.destinationOf(MainIntentExtras(route = MainIntents.ROUTE_NEW_ALARM, presetHour = 6)))
    }

    @Test
    fun unknownOrMissingValuesOpenNothing() {
        assertNull(MainIntents.destinationOf(MainIntentExtras()))
        assertNull(MainIntents.destinationOf(MainIntentExtras(route = "missions")))
        assertNull(MainIntents.destinationOf(MainIntentExtras(route = "")))
        // Tolerant about case and whitespace.
        assertEquals(TimerRoute, MainIntents.destinationOf(MainIntentExtras(route = " Timer ")))
    }

    @Test
    fun intentsRoundTrip() {
        val timer = MainIntents.openRoute(context, MainIntents.ROUTE_TIMER)
        assertEquals(MainIntents.MAIN_ACTIVITY_CLASS, timer.component?.className)
        assertEquals(context.packageName, timer.component?.packageName)
        assertTrue(timer.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals(TimerRoute, MainIntents.destinationOf(timer))

        assertEquals(AlarmEditRoute(alarmId = 7), MainIntents.destinationOf(MainIntents.openAlarm(context, 7)))
        assertEquals(AlarmEditRoute(presetHour = 22, presetMinute = 5), MainIntents.destinationOf(MainIntents.newAlarm(context, 22, 5)))
        assertNull(MainIntents.destinationOf(MainIntents.main(context)))
        assertNull(MainIntents.destinationOf(null as Intent?))
    }

    @Test
    fun consumedIntentsDoNotReopenTheScreen() {
        val intent = MainIntents.openAlarm(context, 7).putExtra(MainIntents.EXTRA_OPEN_ROUTE, MainIntents.ROUTE_SLEEP)
        MainIntents.consume(intent)
        assertNull(MainIntents.destinationOf(intent))
        assertFalse(intent.hasExtra(MainIntents.EXTRA_OPEN_ALARM_ID))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownRoutesAreRejectedWhenBuilding() {
        MainIntents.openRoute(context, "nowhere")
    }

    @Test
    fun topLevelDestinations() {
        assertEquals(listOf(AlarmsRoute, WorldClockRoute, TimerRoute, StopwatchRoute, SleepRoute), TopLevelRoutes.all)
    }
}
