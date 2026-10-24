package com.orthohin.alarmist

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.orthohin.alarmist.core.common.Clock
import com.orthohin.alarmist.core.model.Alarm
import com.orthohin.alarmist.core.model.AutoSilenceConfig
import com.orthohin.alarmist.core.model.Schedule
import com.orthohin.alarmist.core.model.SnoozeConfig
import com.orthohin.alarmist.core.model.SoundConfig
import com.orthohin.alarmist.core.scheduling.AlarmEditor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.temporal.ChronoUnit
import java.util.Date
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface DebugEntryPoint {
    fun alarmEditor(): AlarmEditor

    fun clock(): Clock
}

/**
 * Debug builds only (release has an empty one). "Test alarm" saves a one-off alarm at the next whole minute
 * at least 30 s away, through the real path: AlarmManager → receiver → service → full-screen screen.
 * "Quick test alarm" uses 1-minute rings, quiet periods and snoozes, so a full auto-silence cycle takes ~5 min.
 */
@Composable
fun DebugTools() {
    val context = LocalContext.current
    val resources = LocalResources.current
    val tools =
        remember { EntryPointAccessors.fromApplication(context.applicationContext, DebugEntryPoint::class.java) }
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(false to R.string.debug_test_alarm, true to R.string.debug_quick_test_alarm).forEach { (quick, text) ->
            Button(onClick = {
                scope.launch {
                    val clock = tools.clock()
                    val at = clock.now().atZone(clock.zone()).plusSeconds(90).truncatedTo(ChronoUnit.MINUTES)
                    val alarm = Alarm(
                        time = at.toLocalTime(),
                        schedule = Schedule.Once(at.toLocalDate()),
                        label = resources.getString(
                            if (quick) R.string.debug_quick_test_label else R.string.debug_test_label
                        )
                    )
                    tools.alarmEditor().save(if (quick) alarm.quick() else alarm)
                    val time = DateFormat.getTimeFormat(context).format(Date(at.toInstant().toEpochMilli()))
                    message = resources.getString(R.string.debug_test_scheduled, time)
                }
            }) { Text(stringResource(text)) }
        }
        message?.let { Text(it) }
    }
}

private fun Alarm.quick() = copy(
    sound = SoundConfig(rampUpSeconds = 10),
    snooze = SnoozeConfig(minutes = 1),
    autoSilence = AutoSilenceConfig(ringMinutes = 1, snoozeMinutes = 1)
)
