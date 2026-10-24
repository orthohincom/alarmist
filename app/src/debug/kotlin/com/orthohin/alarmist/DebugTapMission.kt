package com.orthohin.alarmist

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.orthohin.alarmist.core.model.MissionSpec
import com.orthohin.alarmist.mission.api.Mission
import com.orthohin.alarmist.mission.api.MissionCategory
import com.orthohin.alarmist.mission.api.MissionHost
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import javax.inject.Inject
import org.json.JSONObject

/** Debug builds only: "tap N times", to try the mission flow before real missions exist. */
class DebugTapMission @Inject constructor() : Mission {
    override val type = TYPE
    override val name = R.string.debug_tap_mission
    override val icon = R.drawable.ic_debug_tap
    override val description = R.string.debug_tap_summary_short
    override val category = MissionCategory.Other
    override val configVersion = 1

    override fun defaultConfig(): String = JSONObject().put("taps", 3).toString()

    override fun summary(spec: MissionSpec, resources: Resources): String =
        resources.getString(R.string.debug_tap_summary, taps(spec))

    @Composable
    override fun ConfigEditor(spec: MissionSpec, onChange: (config: String) -> Unit) {
        Column {
            Text(stringResource(R.string.debug_tap_summary, taps(spec)))
            Slider(
                value = taps(spec).toFloat(),
                onValueChange = { onChange(JSONObject().put("taps", it.toInt()).toString()) },
                valueRange = 1f..10f,
                steps = 8
            )
        }
    }

    @Composable
    override fun Runner(spec: MissionSpec, host: MissionHost) {
        var left by remember { mutableIntStateOf(taps(spec)) }
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            Button(onClick = {
                left--
                if (left == 0) host.onComplete()
            }) { Text(stringResource(R.string.debug_tap_button, left)) }
        }
    }

    private fun taps(spec: MissionSpec): Int = runCatching { JSONObject(spec.config).getInt("taps") }.getOrDefault(3)

    companion object {
        const val TYPE = "debug_tap"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DebugMissionModule {
    @Binds
    @IntoMap
    @StringKey(DebugTapMission.TYPE)
    abstract fun tap(mission: DebugTapMission): Mission
}
