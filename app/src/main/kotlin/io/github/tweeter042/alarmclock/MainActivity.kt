package io.github.tweeter042.alarmclock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import io.github.tweeter042.alarmclock.core.ui.navigation.AlarmsRoute
import io.github.tweeter042.alarmclock.feature.alarms.alarmsGraph
import io.github.tweeter042.alarmclock.feature.clock.clockGraph
import io.github.tweeter042.alarmclock.feature.connected.connectedGraph
import io.github.tweeter042.alarmclock.feature.integrations.integrationsGraph
import io.github.tweeter042.alarmclock.feature.missions.missionsGraph
import io.github.tweeter042.alarmclock.feature.settings.settingsGraph
import io.github.tweeter042.alarmclock.feature.sleep.sleepGraph

/** Single-activity host. SKELETON: theming, bottom bar, onboarding and What's New come later. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = AlarmsRoute) {
                    alarmsGraph(navController)
                    clockGraph(navController)
                    sleepGraph(navController)
                    settingsGraph(navController)
                    integrationsGraph(navController)
                    connectedGraph(navController)
                    missionsGraph(navController)
                }
            }
        }
    }
}
