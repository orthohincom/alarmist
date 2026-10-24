package com.orthohin.alarmist

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.orthohin.alarmist.core.data.SettingsRepository
import com.orthohin.alarmist.core.data.ThemeMode
import com.orthohin.alarmist.core.designsystem.AlarmistTheme
import com.orthohin.alarmist.core.model.Alarm
import com.orthohin.alarmist.core.ringing.RingingController
import com.orthohin.alarmist.feature.alarmedit.ALARM_ID_ARG
import com.orthohin.alarmist.feature.alarmedit.AlarmEditScreen
import com.orthohin.alarmist.feature.alarmlist.AlarmListScreen
import com.orthohin.alarmist.feature.reliability.ReliabilityAction
import com.orthohin.alarmist.feature.reliability.ReliabilityScreen
import com.orthohin.alarmist.feature.reliability.ReliabilitySetup
import com.orthohin.alarmist.feature.settings.SettingsAction
import com.orthohin.alarmist.feature.settings.SettingsScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var setup: ReliabilitySetup

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // First run: the setup screen explains and asks for the permissions alarms need.
        val start = if (setup.isDone()) LIST else SETUP
        val showReliability = savedInstanceState == null && intent?.action == RingingController.ACTION_SHOW_RELIABILITY
        setContent {
            Themed {
                val nav = rememberNavController()
                LaunchedEffect(nav) { if (showReliability && start == LIST) nav.navigate(RELIABILITY) }
                NavHost(nav, startDestination = start) {
                    composable(SETUP) {
                        ReliabilityScreen(setup = true, onDone = {
                            nav.navigate(LIST) { popUpTo(SETUP) { inclusive = true } }
                        })
                    }
                    composable(LIST) {
                        AlarmListScreen(
                            onAdd = { nav.navigate("$EDIT/${Alarm.NEW_ID}") },
                            onEdit = { nav.navigate("$EDIT/$it") },
                            header = { DebugTools() },
                            actions = {
                                Row {
                                    ReliabilityAction(onClick = { nav.navigate(RELIABILITY) })
                                    SettingsAction(onClick = { nav.navigate(SETTINGS) })
                                }
                            }
                        )
                    }
                    composable(RELIABILITY) { ReliabilityScreen(setup = false, onDone = { nav.popBackStack() }) }
                    composable(SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
                    composable(
                        "$EDIT/{$ALARM_ID_ARG}",
                        arguments = listOf(navArgument(ALARM_ID_ARG) { type = NavType.LongType })
                    ) { AlarmEditScreen(onClose = { nav.popBackStack(LIST, inclusive = false) }) }
                }
            }
        }
    }

    /** The theme from the settings, with matching system bar icons. Shows nothing until the settings are read (a few ms). */
    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
        val current = settings ?: return
        val dark = when (current.theme) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        DisposableEffect(dark) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark }
            )
            onDispose {}
        }
        AlarmistTheme(darkTheme = dark, dynamicColor = current.dynamicColor, content = content)
    }

    private companion object {
        // enableEdgeToEdge's default navigation bar scrims (private in androidx.activity).
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

        const val SETUP = "setup"
        const val LIST = "list"
        const val RELIABILITY = "reliability"
        const val EDIT = "edit"
        const val SETTINGS = "settings"
    }
}
