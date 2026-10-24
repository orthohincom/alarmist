package com.orthohin.alarmist.mission.squats

import android.content.Context
import android.content.res.Resources
import android.hardware.Sensor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.orthohin.alarmist.core.model.MissionSpec
import com.orthohin.alarmist.mission.api.CountingProgress
import com.orthohin.alarmist.mission.api.IntSlider
import com.orthohin.alarmist.mission.api.Mission
import com.orthohin.alarmist.mission.api.MissionCategory
import com.orthohin.alarmist.mission.api.MissionHost
import com.orthohin.alarmist.mission.api.SensorEffect
import com.orthohin.alarmist.mission.api.hasSensor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import javax.inject.Inject
import org.json.JSONObject

/** Squats while holding the phone (accelerometer). */
internal class SquatsMission @Inject constructor() : Mission {
    override val type = TYPE
    override val name = R.string.squats_name
    override val icon = R.drawable.ic_mission_squats
    override val description = R.string.squats_description
    override val category = MissionCategory.Body
    override val configVersion = 1

    override fun defaultConfig(): String = SquatsConfig().toJson()

    override fun summary(spec: MissionSpec, resources: Resources): String {
        val squats = SquatsConfig.parse(spec.config).squats
        return resources.getQuantityString(R.plurals.squats_count, squats, squats)
    }

    override fun isAvailable(context: Context): Boolean = hasSensor(context, Sensor.TYPE_ACCELEROMETER)

    @Composable
    override fun ConfigEditor(spec: MissionSpec, onChange: (config: String) -> Unit) {
        val config = SquatsConfig.parse(spec.config)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IntSlider(
                pluralStringResource(R.plurals.squats_count, config.squats, config.squats),
                config.squats,
                SquatsConfig.SQUATS,
                SquatsConfig.STEP
            ) { onChange(config.copy(squats = it).toJson()) }
            Text(
                stringResource(R.string.squats_cheat_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    @Composable
    override fun Runner(spec: MissionSpec, host: MissionHost) {
        val config = remember(spec.config) { SquatsConfig.parse(spec.config) }
        // Saved with the screen: moving the phone can rotate it.
        var count by rememberSaveable { mutableIntStateOf(0) }
        val counter = remember(config) { SquatCounter() }
        SensorEffect(Sensor.TYPE_ACCELEROMETER, counter) { event ->
            val (x, y, z) = event.values
            if (counter.onSample(x, y, z, event.timestamp) && count < config.squats) {
                count++
                // Squatting isn't a touch: this keeps the alarm muted.
                host.onActivity()
                if (count == config.squats) host.onComplete()
            }
        }
        val left = config.squats - count
        CountingProgress(
            prompt = stringResource(R.string.squats_prompt),
            icon = painterResource(R.drawable.ic_mission_squats),
            count = count,
            total = config.squats,
            left = pluralStringResource(R.plurals.squats_left, left, left),
            hint = stringResource(R.string.squats_hint)
        )
    }

    companion object {
        const val TYPE = "squats"
    }
}

internal data class SquatsConfig(val squats: Int = 10) {
    fun toJson(): String = JSONObject().put("squats", squats).toString()

    companion object {
        val SQUATS = 5..50
        const val STEP = 5

        /** Never fails: a broken config gives the defaults, so the alarm still has a mission to solve. */
        fun parse(json: String): SquatsConfig = runCatching {
            SquatsConfig(JSONObject(json).optInt("squats", 10).coerceIn(SQUATS))
        }.getOrDefault(SquatsConfig())
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SquatsMissionModule {
    @Binds
    @IntoMap
    @StringKey(SquatsMission.TYPE)
    abstract fun bind(mission: SquatsMission): Mission
}
