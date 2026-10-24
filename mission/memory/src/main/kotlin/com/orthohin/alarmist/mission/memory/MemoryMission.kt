package com.orthohin.alarmist.mission.memory

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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

/** Alarmy's "find the tiles": some tiles light up, then the user taps the same ones. */
internal class MemoryMission @Inject constructor() : Mission {
    override val type = TYPE
    override val name = R.string.memory_name
    override val icon = R.drawable.ic_mission_memory
    override val description = R.string.memory_description
    override val category = MissionCategory.Brain
    override val configVersion = 1

    override fun defaultConfig(): String = MemoryConfig().toJson()

    override fun summary(spec: MissionSpec, resources: Resources): String {
        val config = MemoryConfig.parse(spec.config)
        return resources.getQuantityString(
            R.plurals.memory_summary,
            config.rounds,
            resources.getString(config.difficulty.label),
            config.rounds
        )
    }

    @Composable
    override fun ConfigEditor(spec: MissionSpec, onChange: (config: String) -> Unit) {
        val config = MemoryConfig.parse(spec.config)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RadioOptions(
                title = stringResource(R.string.memory_difficulty),
                options = MemoryDifficulty.entries,
                selected = config.difficulty,
                label = { stringResource(it.label) },
                onSelect = { onChange(config.copy(difficulty = it).toJson()) },
                detail = { pluralStringResource(R.plurals.memory_detail, it.tiles, it.gridSize, it.tiles) }
            )
            IntSlider(
                pluralStringResource(R.plurals.memory_rounds, config.rounds, config.rounds),
                config.rounds,
                MemoryConfig.ROUNDS
            ) { onChange(config.copy(rounds = it).toJson()) }
            IntSlider(
                if (config.mistakes == 0) {
                    stringResource(R.string.memory_no_mistakes)
                } else {
                    pluralStringResource(R.plurals.memory_mistakes, config.mistakes, config.mistakes)
                },
                config.mistakes,
                MemoryConfig.MISTAKES
            ) { onChange(config.copy(mistakes = it).toJson()) }
        }
    }

    @Composable
    override fun Runner(spec: MissionSpec, host: MissionHost) {
        val config = remember(spec.config) { MemoryConfig.parse(spec.config) }
        val difficulty = config.difficulty
        // All saved with the screen, so rotating it keeps the pattern and progress.
        val seed = rememberSaveable { Random.nextLong() }
        var round by rememberSaveable { mutableIntStateOf(0) }
        var attempt by rememberSaveable { mutableIntStateOf(0) }
        var taps by rememberSaveable { mutableStateOf(emptyList<Int>()) }
        var phase by rememberSaveable { mutableStateOf(Phase.SHOW) }
        val pattern = remember(difficulty, seed, round, attempt) {
            memoryPattern(difficulty, Random(seed + round * 1000L + attempt))
        }
        val state = MemoryRound.replay(pattern, taps)

        LaunchedEffect(phase, round, attempt) {
            when (phase) {
                Phase.SHOW -> {
                    delay(difficulty.showMillis)
                    phase = Phase.INPUT
                }

                Phase.INPUT -> Unit

                Phase.DONE -> {
                    delay(RESULT_MILLIS)
                    if (round + 1 >= config.rounds) {
                        host.onComplete()
                    } else {
                        round++
                        taps = emptyList()
                        phase = Phase.SHOW
                    }
                }

                Phase.FAILED -> {
                    delay(FAILED_MILLIS)
                    attempt++
                    taps = emptyList()
                    phase = Phase.SHOW
                }
            }
        }

        val onTap = { tile: Int ->
            if (phase == Phase.INPUT) {
                taps = taps + tile
                val next = MemoryRound.replay(pattern, taps)
                if (next.solved) {
                    phase = Phase.DONE
                } else if (next.failed(config.mistakes)) {
                    phase = Phase.FAILED
                }
            }
        }
        val colors = MaterialTheme.colorScheme
        val color = { tile: Int ->
            when {
                phase == Phase.SHOW -> if (tile in pattern) colors.primary else colors.surfaceVariant

                tile in state.found -> colors.primary

                tile in state.misses -> colors.error

                // Shows what was missed before the new pattern.
                phase == Phase.FAILED && tile in pattern -> colors.tertiary

                else -> colors.surfaceVariant
            }
        }
        BoardLayout(
            status = {
                if (config.rounds > 1) {
                    Text(
                        stringResource(R.string.memory_round, round + 1, config.rounds),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Text(
                    stringResource(phase.message),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                val left = config.mistakes - state.misses.size
                Text(
                    if (phase == Phase.INPUT && config.mistakes > 0) {
                        pluralStringResource(R.plurals.memory_mistakes_left, left, left)
                    } else {
                        ""
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        ) { Grid(difficulty.gridSize, phase == Phase.INPUT, color, onTap, it) }
    }

    companion object {
        const val TYPE = "memory"
        private const val RESULT_MILLIS = 700L
        private const val FAILED_MILLIS = 2000L
    }
}

private enum class Phase(@StringRes val message: Int) {
    SHOW(R.string.memory_watch),
    INPUT(R.string.memory_tap),
    DONE(R.string.memory_correct),
    FAILED(R.string.memory_failed)
}

@Composable
private fun Grid(size: Int, enabled: Boolean, color: (Int) -> Color, onTap: (Int) -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in 0 until size) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (column in 0 until size) {
                    val tile = row * size + column
                    val tileColor by animateColorAsState(color(tile), label = "tile")
                    Surface(
                        onClick = { onTap(tile) },
                        enabled = enabled,
                        shape = MaterialTheme.shapes.medium,
                        color = tileColor,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {}
                }
            }
        }
    }
}

internal data class MemoryConfig(
    val difficulty: MemoryDifficulty = MemoryDifficulty.NORMAL,
    val rounds: Int = 2,
    val mistakes: Int = 1
) {
    fun toJson(): String = JSONObject()
        .put("difficulty", difficulty.key)
        .put("rounds", rounds)
        .put("mistakes", mistakes)
        .toString()

    companion object {
        val ROUNDS = 1..5
        val MISTAKES = 0..3

        /** Never fails: a broken config gives the defaults, so the alarm still has a mission to solve. */
        fun parse(json: String): MemoryConfig = runCatching {
            val o = JSONObject(json)
            MemoryConfig(
                MemoryDifficulty.fromKey(o.optString("difficulty")),
                o.optInt("rounds", 2).coerceIn(ROUNDS),
                o.optInt("mistakes", 1).coerceIn(MISTAKES)
            )
        }.getOrDefault(MemoryConfig())
    }
}

@get:StringRes
private val MemoryDifficulty.label: Int
    get() = when (this) {
        MemoryDifficulty.EASY -> R.string.memory_easy
        MemoryDifficulty.NORMAL -> R.string.memory_normal
        MemoryDifficulty.HARD -> R.string.memory_hard
        MemoryDifficulty.VERY_HARD -> R.string.memory_very_hard
    }

@Module
@InstallIn(SingletonComponent::class)
internal abstract class MemoryMissionModule {
    @Binds
    @IntoMap
    @StringKey(MemoryMission.TYPE)
    abstract fun bind(mission: MemoryMission): Mission
}
