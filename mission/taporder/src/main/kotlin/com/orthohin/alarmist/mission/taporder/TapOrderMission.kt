package com.orthohin.alarmist.mission.taporder

import android.content.res.Resources
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orthohin.alarmist.core.model.MissionSpec
import com.orthohin.alarmist.mission.api.BoardLayout
import com.orthohin.alarmist.mission.api.IntSlider
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
import kotlin.random.Random
import kotlinx.coroutines.delay
import org.json.JSONObject

/** Numbers scattered on the board; the user taps them from 1 up. */
internal class TapOrderMission @Inject constructor() : Mission {
    override val type = TYPE
    override val name = R.string.taporder_name
    override val icon = R.drawable.ic_mission_taporder
    override val description = R.string.taporder_description
    override val category = MissionCategory.Brain
    override val configVersion = 1

    override fun defaultConfig(): String = TapOrderConfig().toJson()

    override fun summary(spec: MissionSpec, resources: Resources): String {
        val config = TapOrderConfig.parse(spec.config)
        return resources.getQuantityString(
            R.plurals.taporder_summary,
            config.rounds,
            resources.getQuantityString(R.plurals.taporder_count, config.count, config.count),
            config.rounds
        )
    }

    @Composable
    override fun ConfigEditor(spec: MissionSpec, onChange: (config: String) -> Unit) {
        val config = TapOrderConfig.parse(spec.config)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IntSlider(
                pluralStringResource(R.plurals.taporder_count, config.count, config.count),
                config.count,
                TapOrderConfig.COUNT
            ) { onChange(config.copy(count = it).toJson()) }
            IntSlider(
                pluralStringResource(R.plurals.taporder_rounds, config.rounds, config.rounds),
                config.rounds,
                TapOrderConfig.ROUNDS
            ) { onChange(config.copy(rounds = it).toJson()) }
        }
    }

    @Composable
    override fun Runner(spec: MissionSpec, host: MissionHost) {
        val config = remember(spec.config) { TapOrderConfig.parse(spec.config) }
        // Saved with the screen, so rotating it keeps the board and progress.
        val seed = rememberSaveable { Random.nextLong() }
        var round by rememberSaveable { mutableIntStateOf(0) }
        var found by rememberSaveable { mutableIntStateOf(0) }
        val board = remember(config.count, seed, round) { tapOrderBoard(config.count, Random(seed + round)) }
        val progress = TapProgress(config.count, found)
        // A wrong number flashes red; it costs nothing else.
        var wrong by remember { mutableStateOf<Int?>(null) }

        LaunchedEffect(wrong) {
            if (wrong != null) {
                delay(WRONG_MILLIS)
                wrong = null
            }
        }
        LaunchedEffect(round, progress.solved) {
            if (progress.solved) {
                delay(RESULT_MILLIS)
                if (round + 1 >= config.rounds) {
                    host.onComplete()
                } else {
                    round++
                    found = 0
                }
            }
        }

        BoardLayout(
            status = {
                if (config.rounds > 1) {
                    Text(
                        stringResource(R.string.taporder_round, round + 1, config.rounds),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Text(
                    if (progress.solved) {
                        stringResource(R.string.taporder_correct)
                    } else {
                        stringResource(R.string.taporder_prompt, config.count)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Text(
                    if (progress.solved) "" else stringResource(R.string.taporder_next, progress.next),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        ) { modifier ->
            Board(board, progress, wrong, modifier) { number ->
                val next = progress.tap(number)
                if (next == progress) wrong = number else found = next.found
            }
        }
    }

    companion object {
        const val TYPE = "taporder"
        private const val RESULT_MILLIS = 700L
        private const val WRONG_MILLIS = 400L
    }
}

@Composable
private fun Board(
    board: List<TapTarget>,
    progress: TapProgress,
    wrong: Int?,
    modifier: Modifier,
    onTap: (Int) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(modifier) {
        val cell = maxWidth / TAP_ORDER_GRID
        val size = cell * TARGET_SIZE
        val slack = cell - size
        val fontSize = with(LocalDensity.current) { (size * 0.42f).toSp() }
        for (target in board) {
            val done = target.number < progress.next
            val color by animateColorAsState(
                when {
                    target.number == wrong -> colors.error
                    done -> colors.surfaceVariant
                    else -> colors.primaryContainer
                },
                label = "target"
            )
            Surface(
                onClick = { onTap(target.number) },
                enabled = !done && !progress.solved,
                shape = CircleShape,
                color = color,
                contentColor = when {
                    target.number == wrong -> colors.onError
                    done -> colors.onSurfaceVariant.copy(alpha = 0.4f)
                    else -> colors.onPrimaryContainer
                },
                modifier = Modifier
                    .offset(cell * target.column + slack * target.dx, cell * target.row + slack * target.dy)
                    .size(size)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(target.number.toString(), fontSize = fontSize, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Share of a cell a number's circle takes; the rest is room to scatter it. */
private const val TARGET_SIZE = 0.82f

internal data class TapOrderConfig(val count: Int = 10, val rounds: Int = 2) {
    fun toJson(): String = JSONObject()
        .put("count", count)
        .put("rounds", rounds)
        .toString()

    companion object {
        val COUNT = 5..20
        val ROUNDS = 1..5

        /** Never fails: a broken config gives the defaults, so the alarm still has a mission to solve. */
        fun parse(json: String): TapOrderConfig = runCatching {
            val o = JSONObject(json)
            TapOrderConfig(o.optInt("count", 10).coerceIn(COUNT), o.optInt("rounds", 2).coerceIn(ROUNDS))
        }.getOrDefault(TapOrderConfig())
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class TapOrderMissionModule {
    @Binds
    @IntoMap
    @StringKey(TapOrderMission.TYPE)
    abstract fun bind(mission: TapOrderMission): Mission
}
