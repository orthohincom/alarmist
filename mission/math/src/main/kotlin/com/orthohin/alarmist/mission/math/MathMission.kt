package com.orthohin.alarmist.mission.math

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orthohin.alarmist.core.model.MissionSpec
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
import org.json.JSONObject

/** Solve arithmetic problems on an on-screen keypad (no system keyboard over the lock screen). */
internal class MathMission @Inject constructor() : Mission {
    override val type = TYPE
    override val name = R.string.math_name
    override val icon = R.drawable.ic_mission_math
    override val description = R.string.math_description
    override val category = MissionCategory.Brain
    override val configVersion = 1

    override fun defaultConfig(): String = MathConfig().toJson()

    override fun summary(spec: MissionSpec, resources: Resources): String {
        val config = MathConfig.parse(spec.config)
        return resources.getQuantityString(
            R.plurals.math_summary,
            config.problems,
            resources.getString(config.difficulty.label),
            config.problems
        )
    }

    @Composable
    override fun ConfigEditor(spec: MissionSpec, onChange: (config: String) -> Unit) {
        val config = MathConfig.parse(spec.config)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RadioOptions(
                title = stringResource(R.string.math_difficulty),
                options = MathDifficulty.entries,
                selected = config.difficulty,
                label = { stringResource(it.label) },
                onSelect = { onChange(config.copy(difficulty = it).toJson()) },
                // A fixed seed: the example doesn't change on every recomposition.
                detail = { remember(it) { it.problem(Random(it.ordinal)).text } }
            )
            IntSlider(
                pluralStringResource(R.plurals.math_problems, config.problems, config.problems),
                config.problems,
                MathConfig.MIN_PROBLEMS..MathConfig.MAX_PROBLEMS
            ) { onChange(config.copy(problems = it).toJson()) }
        }
    }

    @Composable
    override fun Runner(spec: MissionSpec, host: MissionHost) {
        val config = remember(spec.config) { MathConfig.parse(spec.config) }
        // Saved with the screen, so rotating it keeps the same problems and progress.
        val seed = rememberSaveable { Random.nextLong() }
        val problems = remember(config, seed) { mathProblems(config.difficulty, config.problems, seed) }
        var solved by rememberSaveable { mutableIntStateOf(0) }
        var input by rememberSaveable { mutableStateOf("") }
        var wrong by rememberSaveable { mutableStateOf(false) }
        if (solved >= problems.size) return
        val problem = problems[solved]

        fun check() {
            if (input.isEmpty()) return
            if (input.toIntOrNull() == problem.answer) {
                input = ""
                wrong = false
                solved++
                if (solved == problems.size) host.onComplete()
            } else {
                input = ""
                wrong = true
            }
        }

        val progress = if (problems.size > 1) {
            stringResource(R.string.math_progress, solved + 1, problems.size)
        } else {
            null
        }
        val onDigit = { digit: Int ->
            wrong = false
            if (input.length < MAX_DIGITS && !(input.isEmpty() && digit == 0)) input += digit
        }
        val onDelete = { input = input.dropLast(1) }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth > maxHeight) {
                // Landscape: side by side, or the keypad gets too flat to use.
                Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) { Problem(progress, problem.text, input, wrong) }
                    Keypad(
                        onDigit,
                        onDelete,
                        ::check,
                        Modifier.weight(1f).heightIn(max = KEYPAD_HEIGHT).fillMaxHeight()
                    )
                }
            } else {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
                ) {
                    Problem(progress, problem.text, input, wrong)
                    Keypad(
                        onDigit,
                        onDelete,
                        ::check,
                        // Shrinks on short screens, up to 4 rows of 72 dp.
                        Modifier
                            .weight(1f, fill = false)
                            .heightIn(max = KEYPAD_HEIGHT)
                            .widthIn(max = 360.dp)
                            .fillMaxWidth()
                    )
                }
            }
        }
    }

    companion object {
        const val TYPE = "math"
        private const val MAX_DIGITS = 6
        private val KEYPAD_HEIGHT = 288.dp
    }
}

@Composable
private fun Problem(progress: String?, text: String, input: String, wrong: Boolean) {
    if (progress != null) Text(progress, style = MaterialTheme.typography.titleSmall)
    Text("$text =", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
    val answerLabel = stringResource(R.string.math_answer)
    Surface(
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .widthIn(min = 160.dp)
            .semantics { contentDescription = answerLabel }
    ) {
        Text(
            input.ifEmpty { "?" },
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
            color = with(MaterialTheme.colorScheme) { if (input.isEmpty()) onSurfaceVariant else onSurface },
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
    }
    Text(
        if (wrong) stringResource(R.string.math_wrong) else "",
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun Keypad(onDigit: (Int) -> Unit, onDelete: () -> Unit, onCheck: () -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))) {
            KeyRow {
                for (digit in row) DigitKey(digit, onDigit)
            }
        }
        KeyRow {
            FilledTonalButton(onClick = onDelete, modifier = keyModifier(), contentPadding = PaddingValues(0.dp)) {
                Icon(painterResource(R.drawable.ic_math_backspace), stringResource(R.string.math_delete))
            }
            DigitKey(0, onDigit)
            Button(onClick = onCheck, modifier = keyModifier(), contentPadding = PaddingValues(0.dp)) {
                Icon(painterResource(R.drawable.ic_math_check), stringResource(R.string.math_check))
            }
        }
    }
}

@Composable
private fun ColumnScope.KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
private fun RowScope.DigitKey(digit: Int, onDigit: (Int) -> Unit) {
    FilledTonalButton(onClick = { onDigit(digit) }, modifier = keyModifier(), contentPadding = PaddingValues(0.dp)) {
        Text(digit.toString(), style = MaterialTheme.typography.headlineSmall)
    }
}

private fun RowScope.keyModifier(): Modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 40.dp)

internal data class MathConfig(val difficulty: MathDifficulty = MathDifficulty.NORMAL, val problems: Int = 3) {
    fun toJson(): String = JSONObject().put("difficulty", difficulty.key).put("problems", problems).toString()

    companion object {
        const val MIN_PROBLEMS = 1
        const val MAX_PROBLEMS = 10

        /** Never fails: a broken config gives the defaults, so the alarm still has a mission to solve. */
        fun parse(json: String): MathConfig = runCatching {
            val o = JSONObject(json)
            MathConfig(
                MathDifficulty.fromKey(o.optString("difficulty")),
                o.optInt("problems", 3).coerceIn(MIN_PROBLEMS, MAX_PROBLEMS)
            )
        }.getOrDefault(MathConfig())
    }
}

@get:StringRes
private val MathDifficulty.label: Int
    get() = when (this) {
        MathDifficulty.VERY_EASY -> R.string.math_very_easy
        MathDifficulty.EASY -> R.string.math_easy
        MathDifficulty.NORMAL -> R.string.math_normal
        MathDifficulty.HARD -> R.string.math_hard
        MathDifficulty.VERY_HARD -> R.string.math_very_hard
    }

@Module
@InstallIn(SingletonComponent::class)
internal abstract class MathMissionModule {
    @Binds
    @IntoMap
    @StringKey(MathMission.TYPE)
    abstract fun bind(mission: MathMission): Mission
}
