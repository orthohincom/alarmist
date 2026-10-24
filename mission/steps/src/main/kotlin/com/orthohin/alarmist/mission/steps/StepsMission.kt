package com.orthohin.alarmist.mission.steps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.hardware.Sensor
import android.os.Build
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.delay
import org.json.JSONObject

/**
 * Walk a number of steps. Uses the phone's step detector when it has one and ACTIVITY_RECOGNITION is granted
 * (Android 10+), and always counts from the accelerometer too ([StepSources]), so a denied permission or a silent
 * detector never blocks it. [GaitCheck] drops steps that look like shaking.
 */
internal class StepsMission @Inject constructor() : Mission {
    override val type = TYPE
    override val name = R.string.steps_name
    override val icon = R.drawable.ic_mission_steps
    override val description = R.string.steps_description
    override val category = MissionCategory.Body
    override val configVersion = 1

    override val permissions: List<String> =
        if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.Q
        ) {
            listOf(Manifest.permission.ACTIVITY_RECOGNITION)
        } else {
            emptyList()
        }

    override fun defaultConfig(): String = StepsConfig().toJson()

    override fun summary(spec: MissionSpec, resources: Resources): String {
        val steps = StepsConfig.parse(spec.config).steps
        return resources.getQuantityString(R.plurals.steps_count, steps, steps)
    }

    override fun isAvailable(context: Context): Boolean =
        hasSensor(context, Sensor.TYPE_STEP_DETECTOR) || hasSensor(context, Sensor.TYPE_ACCELEROMETER)

    @Composable
    override fun ConfigEditor(spec: MissionSpec, onChange: (config: String) -> Unit) {
        val config = StepsConfig.parse(spec.config)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IntSlider(
                pluralStringResource(R.plurals.steps_count, config.steps, config.steps),
                config.steps,
                StepsConfig.STEPS,
                StepsConfig.STEP
            ) { onChange(config.copy(steps = it).toJson()) }
            Text(
                stringResource(R.string.steps_cheat_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    @Composable
    override fun Runner(spec: MissionSpec, host: MissionHost) {
        val config = remember(spec.config) { StepsConfig.parse(spec.config) }
        val context = LocalContext.current
        val detector = remember { hasSensor(context, Sensor.TYPE_STEP_DETECTOR) && canUseDetector(context) }
        // Saved with the screen: walking with the phone can rotate it.
        var count by rememberSaveable { mutableIntStateOf(0) }
        val onStep = {
            if (count < config.steps) {
                count++
                // A step isn't a touch: this keeps the alarm muted.
                host.onActivity()
                if (count == config.steps) host.onComplete()
            }
        }
        // Every candidate step, from either source, must look like walking.
        val hasGyro = remember { hasSensor(context, Sensor.TYPE_GYROSCOPE) }
        // Debug builds record every reading, to replay real walks in tests (SensorRecording).
        val recording = remember { SensorRecording.start(context, detector, hasGyro) }
        DisposableEffect(recording) { onDispose { recording?.close() } }
        var shaking by remember { mutableIntStateOf(0) }
        val sources = remember {
            StepSources(hasGyro) { source, verdict, leading ->
                Log.d(TAG, "$source: ${verdict.describe()}")
                // Only the source that's counting warns: the other one's rejections don't matter.
                if (verdict.shaking != null && leading) shaking++
            }
        }
        SensorEffect(Sensor.TYPE_ACCELEROMETER, sources) { event ->
            recording?.accel(event)
            val (x, y, z) = event.values
            repeat(sources.onAccel(x, y, z, event.timestamp)) { onStep() }
        }
        SensorEffect(Sensor.TYPE_GYROSCOPE, sources) { event ->
            recording?.gyro(event)
            val (x, y, z) = event.values
            sources.onGyro(x, y, z, event.timestamp)
        }
        // One event per step.
        if (detector) {
            SensorEffect(Sensor.TYPE_STEP_DETECTOR, sources) { event ->
                recording?.step(event)
                repeat(sources.onDetectorStep(event.timestamp)) { onStep() }
            }
        }
        // The warning stays a few seconds after the last rejected step.
        var warning by remember { mutableStateOf(false) }
        LaunchedEffect(shaking) {
            if (shaking == 0) return@LaunchedEffect
            warning = true
            delay(WARNING_MILLIS)
            warning = false
        }
        val left = config.steps - count
        CountingProgress(
            prompt = stringResource(R.string.steps_prompt),
            icon = painterResource(R.drawable.ic_mission_steps),
            count = count,
            total = config.steps,
            left = pluralStringResource(R.plurals.steps_left, left, left),
            hint = stringResource(if (warning) R.string.steps_shaking else R.string.steps_hint)
        )
    }

    private fun canUseDetector(context: Context): Boolean = permissions.all {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val TYPE = "steps"
        private const val TAG = "Alarmist"
        private const val WARNING_MILLIS = 3_000L
    }
}

internal data class StepsConfig(val steps: Int = 30) {
    fun toJson(): String = JSONObject().put("steps", steps).toString()

    companion object {
        val STEPS = 10..300
        const val STEP = 10

        /** Never fails: a broken config gives the defaults, so the alarm still has a mission to solve. */
        fun parse(json: String): StepsConfig = runCatching {
            StepsConfig(JSONObject(json).optInt("steps", 30).coerceIn(STEPS))
        }.getOrDefault(StepsConfig())
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class StepsMissionModule {
    @Binds
    @IntoMap
    @StringKey(StepsMission.TYPE)
    abstract fun bind(mission: StepsMission): Mission
}
