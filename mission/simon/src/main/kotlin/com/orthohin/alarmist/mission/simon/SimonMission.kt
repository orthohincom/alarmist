package com.orthohin.alarmist.mission.simon

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orthohin.alarmist.core.model.MissionSpec
import com.orthohin.alarmist.mission.api.BoardLayout
import com.orthohin.alarmist.mission.api.IntSlider
import com.orthohin.alarmist.mission.api.Mission
import com.orthohin.alarmist.mission.api.MissionCategory
import com.orthohin.alarmist.mission.api.MissionHost
import com.orthohin.alarmist.mission.api.RadioOptions
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.delay
import org.json.JSONObject

/** Simon says: pads flash a pattern, the user repeats it; each pattern is one pad longer than the last. */
internal class SimonMission @Inject constructor() : Mission {
    override val type = TYPE
    override val name = R.string.simon_name
    override val icon = R.drawable.ic_mission_simon
    override val description = R.string.simon_description
    override val category = MissionCategory.Brain
    override val configVersion = 1

    override fun defaultConfig(): String = SimonConfig().toJson()

    override fun summary(spec: MissionSpec, resources: Resources): String {
        val config = SimonConfig.parse(spec.config)
        return resources.getQuantityString(
            R.plurals.simon_summary,
            config.length,
            resources.getString(config.speed.label),
            config.length
        )
    }

    @Composable
    override fun ConfigEditor(spec: MissionSpec, onChange: (config: String) -> Unit) {
        val config = SimonConfig.parse(spec.config)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IntSlider(
                pluralStringResource(R.plurals.simon_length, config.length, config.length),
                config.length,
                SimonConfig.LENGTH
            ) { onChange(config.copy(length = it).toJson()) }
            RadioOptions(
                title = stringResource(R.string.simon_speed),
                options = SimonSpeed.entries,
                selected = config.speed,
                label = { stringResource(it.label) },
                onSelect = { onChange(config.copy(speed = it).toJson()) }
            )
        }
    }

    @Composable
    override fun Runner(spec: MissionSpec, host: MissionHost) {
        val config = remember(spec.config) { SimonConfig.parse(spec.config) }
        val start = simonStart(config.length)
        // Saved with the screen, so rotating it keeps the sequence and progress (a pattern being shown starts over).
        val seed = rememberSaveable { Random.nextLong() }
        var shown by rememberSaveable { mutableIntStateOf(start) }
        var attempt by rememberSaveable { mutableIntStateOf(0) }
        var taps by rememberSaveable { mutableStateOf(emptyList<Int>()) }
        var phase by rememberSaveable { mutableStateOf(Phase.SHOW) }
        var flashing by remember { mutableStateOf<Int?>(null) }
        val sequence = remember(config.length, seed, attempt) {
            simonSequence(config.length, Random(seed + attempt))
        }

        LaunchedEffect(phase, shown, attempt) {
            when (phase) {
                Phase.SHOW -> {
                    delay(SHOW_DELAY_MILLIS)
                    for (pad in sequence.take(shown)) {
                        flashing = pad
                        delay(config.speed.flashMillis)
                        flashing = null
                        delay(config.speed.gapMillis)
                    }
                    phase = Phase.INPUT
                }

                Phase.INPUT -> Unit

                Phase.CORRECT -> {
                    delay(RESULT_MILLIS)
                    if (shown >= config.length) {
                        host.onComplete()
                    } else {
                        shown++
                        taps = emptyList()
                        phase = Phase.SHOW
                    }
                }

                Phase.WRONG -> {
                    delay(WRONG_MILLIS)
                    attempt++
                    taps = emptyList()
                    phase = Phase.SHOW
                }
            }
        }

        val onTap = { pad: Int ->
            if (phase == Phase.INPUT) {
                taps = taps + pad
                when (simonCheck(sequence, shown, taps)) {
                    SimonResult.PARTIAL -> Unit
                    SimonResult.CORRECT -> phase = Phase.CORRECT
                    SimonResult.WRONG -> phase = Phase.WRONG
                }
            }
        }
        // After a mistake, the pad that was due stays lit.
        val lit = when (phase) {
            Phase.SHOW -> flashing
            Phase.WRONG -> sequence[taps.lastIndex]
            else -> null
        }

        BoardLayout(
            status = {
                val patterns = config.length - start + 1
                if (patterns > 1) {
                    Text(
                        stringResource(R.string.simon_pattern, shown - start + 1, patterns),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Text(
                    stringResource(phase.message),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Text(
                    if (phase == Phase.INPUT) stringResource(R.string.simon_progress, taps.size, shown) else "",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        ) { Pads(lit, phase == Phase.INPUT, onTap, it) }
    }

    companion object {
        const val TYPE = "simon"
        private const val SHOW_DELAY_MILLIS = 600L
        private const val RESULT_MILLIS = 600L
        private const val WRONG_MILLIS = 2000L
    }
}

private enum class Phase(@StringRes val message: Int) {
    SHOW(R.string.simon_watch),
    INPUT(R.string.simon_repeat),
    CORRECT(R.string.simon_correct),
    WRONG(R.string.simon_wrong)
}

/** Fixed game colours (not the theme's), so the four pads are always told apart; the position tells them apart too. */
private val PadColors = listOf(Color(0xFF43A047), Color(0xFFE53935), Color(0xFFFDD835), Color(0xFF1E88E5))

private val PadNames = listOf(R.string.simon_green, R.string.simon_red, R.string.simon_yellow, R.string.simon_blue)

@Composable
private fun Pads(lit: Int?, enabled: Boolean, onTap: (Int) -> Unit, modifier: Modifier) {
    val surface = MaterialTheme.colorScheme.surface
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (row in 0 until 2) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (column in 0 until 2) {
                    val pad = row * 2 + column
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val on = pad == lit || (enabled && pressed)
                    val color by animateColorAsState(
                        if (on) PadColors[pad] else PadColors[pad].copy(alpha = 0.25f).compositeOver(surface),
                        animationSpec = tween(80),
                        label = "pad"
                    )
                    val description = stringResource(PadNames[pad])
                    Surface(
                        onClick = { onTap(pad) },
                        enabled = enabled,
                        shape = MaterialTheme.shapes.large,
                        color = color,
                        interactionSource = interaction,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .semantics { contentDescription = description }
                    ) {}
                }
            }
        }
    }
}

internal data class SimonConfig(val length: Int = 6, val speed: SimonSpeed = SimonSpeed.NORMAL) {
    fun toJson(): String = JSONObject()
        .put("length", length)
        .put("speed", speed.key)
        .toString()

    companion object {
        val LENGTH = 3..12

        /** Never fails: a broken config gives the defaults, so the alarm still has a mission to solve. */
        fun parse(json: String): SimonConfig = runCatching {
            val o = JSONObject(json)
            SimonConfig(o.optInt("length", 6).coerceIn(LENGTH), SimonSpeed.fromKey(o.optString("speed")))
        }.getOrDefault(SimonConfig())
    }
}

@get:StringRes
private val SimonSpeed.label: Int
    get() = when (this) {
        SimonSpeed.SLOW -> R.string.simon_slow
        SimonSpeed.NORMAL -> R.string.simon_normal
        SimonSpeed.FAST -> R.string.simon_fast
    }

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SimonMissionModule {
    @Binds
    @IntoMap
    @StringKey(SimonMission.TYPE)
    abstract fun bind(mission: SimonMission): Mission
}
