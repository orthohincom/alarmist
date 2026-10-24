package com.orthohin.alarmist.mission.shake

import android.content.Context
import android.content.res.Resources
import android.hardware.Sensor
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.orthohin.alarmist.mission.api.RadioOptions
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

/** Shake the phone a number of times (accelerometer). */
internal class ShakeMission @Inject constructor() : Mission {
    override val type = TYPE
    override val name = R.string.shake_name
    override val icon = R.drawable.ic_mission_shake
    override val description = R.string.shake_description
    override val category = MissionCategory.Body
    override val configVersion = 1

    override fun defaultConfig(): String = ShakeConfig().toJson()

    override fun summary(spec: MissionSpec, resources: Resources): String {
        val config = ShakeConfig.parse(spec.config)
        return resources.getQuantityString(
            R.plurals.shake_summary,
            config.shakes,
            resources.getString(config.strength.label),
            config.shakes
        )
    }

    override fun isAvailable(context: Context): Boolean = hasSensor(context, Sensor.TYPE_ACCELEROMETER)

    @Composable
    override fun ConfigEditor(spec: MissionSpec, onChange: (config: String) -> Unit) {
        val config = ShakeConfig.parse(spec.config)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RadioOptions(
                title = stringResource(R.string.shake_strength),
                options = ShakeStrength.entries,
                selected = config.strength,
                label = { stringResource(it.label) },
                onSelect = { onChange(config.copy(strength = it).toJson()) },
                detail = { stringResource(it.detail) }
            )
            IntSlider(
                pluralStringResource(R.plurals.shake_count, config.shakes, config.shakes),
                config.shakes,
                ShakeConfig.SHAKES,
                ShakeConfig.STEP
            ) { onChange(config.copy(shakes = it).toJson()) }
        }
    }

    @Composable
    override fun Runner(spec: MissionSpec, host: MissionHost) {
        val config = remember(spec.config) { ShakeConfig.parse(spec.config) }
        // Saved with the screen: shaking can rotate it.
        var count by rememberSaveable { mutableIntStateOf(0) }
        val counter = remember(config) { ShakeCounter(config.strength.threshold) }
        SensorEffect(Sensor.TYPE_ACCELEROMETER, counter) { event ->
            val (x, y, z) = event.values
            if (counter.onSample(x, y, z, event.timestamp) && count < config.shakes) {
                count++
                // Shaking isn't a touch: this keeps the alarm muted.
                host.onActivity()
                if (count == config.shakes) host.onComplete()
            }
        }
        val left = config.shakes - count
        CountingProgress(
            prompt = stringResource(R.string.shake_prompt),
            icon = painterResource(R.drawable.ic_mission_shake),
            count = count,
            total = config.shakes,
            left = pluralStringResource(R.plurals.shake_left, left, left)
        )
    }

    companion object {
        const val TYPE = "shake"
    }
}

internal data class ShakeConfig(val strength: ShakeStrength = ShakeStrength.NORMAL, val shakes: Int = 30) {
    fun toJson(): String = JSONObject().put("strength", strength.key).put("shakes", shakes).toString()

    companion object {
        val SHAKES = 10..150
        const val STEP = 10

        /** Never fails: a broken config gives the defaults, so the alarm still has a mission to solve. */
        fun parse(json: String): ShakeConfig = runCatching {
            val o = JSONObject(json)
            ShakeConfig(ShakeStrength.fromKey(o.optString("strength")), o.optInt("shakes", 30).coerceIn(SHAKES))
        }.getOrDefault(ShakeConfig())
    }
}

@get:StringRes
private val ShakeStrength.label: Int
    get() = when (this) {
        ShakeStrength.GENTLE -> R.string.shake_gentle
        ShakeStrength.NORMAL -> R.string.shake_normal
        ShakeStrength.STRONG -> R.string.shake_strong
    }

@get:StringRes
private val ShakeStrength.detail: Int
    get() = when (this) {
        ShakeStrength.GENTLE -> R.string.shake_gentle_detail
        ShakeStrength.NORMAL -> R.string.shake_normal_detail
        ShakeStrength.STRONG -> R.string.shake_strong_detail
    }

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ShakeMissionModule {
    @Binds
    @IntoMap
    @StringKey(ShakeMission.TYPE)
    abstract fun bind(mission: ShakeMission): Mission
}
