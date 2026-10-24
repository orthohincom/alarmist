package com.orthohin.alarmist.mission.typing

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.orthohin.alarmist.core.model.MissionSpec
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
import org.json.JSONArray
import org.json.JSONObject

/** Type out a phrase (built-in or the user's own) with the system keyboard. */
internal class TypingMission @Inject constructor() : Mission {
    override val type = TYPE
    override val name = R.string.typing_name
    override val icon = R.drawable.ic_mission_typing
    override val description = R.string.typing_description
    override val category = MissionCategory.Brain
    override val configVersion = 1

    override fun defaultConfig(): String = TypingConfig().toJson()

    override fun summary(spec: MissionSpec, resources: Resources): String {
        val config = TypingConfig.parse(spec.config)
        val plurals = if (config.custom.isEmpty()) R.plurals.typing_phrases else R.plurals.typing_summary_custom
        return resources.getQuantityString(plurals, config.phrases, config.phrases)
    }

    @Composable
    override fun ConfigEditor(spec: MissionSpec, onChange: (config: String) -> Unit) {
        val config = TypingConfig.parse(spec.config)
        // Raw text, so blank lines survive while the user is still typing.
        var custom by remember { mutableStateOf(config.custom.joinToString("\n")) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IntSlider(
                pluralStringResource(R.plurals.typing_phrases, config.phrases, config.phrases),
                config.phrases,
                TypingConfig.MIN_PHRASES..TypingConfig.MAX_PHRASES
            ) { onChange(config.copy(phrases = it).toJson()) }
            OutlinedTextField(
                value = custom,
                onValueChange = {
                    custom = it
                    onChange(config.copy(custom = TypingConfig.lines(it.lines())).toJson())
                },
                label = { Text(stringResource(R.string.typing_custom)) },
                supportingText = { Text(stringResource(R.string.typing_custom_help)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    @Composable
    override fun Runner(spec: MissionSpec, host: MissionHost) {
        val config = remember(spec.config) { TypingConfig.parse(spec.config) }
        val builtIn = stringArrayResource(R.array.typing_phrases)
        // Saved with the screen, so rotating it keeps the same phrases and progress.
        val seed = rememberSaveable { Random.nextLong() }
        val phrases = remember(config, seed) {
            Typing.pick(config.custom.ifEmpty { builtIn.toList() }, config.phrases, Random(seed))
        }
        var done by rememberSaveable { mutableIntStateOf(0) }
        var typed by rememberSaveable { mutableStateOf("") }
        if (done >= phrases.size) return
        val phrase = phrases[done]
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }

        val matched = Typing.matched(phrase, typed)
        val error = Typing.hasError(phrase, typed)
        val colors = MaterialTheme.colorScheme
        val shown = buildAnnotatedString {
            withStyle(SpanStyle(color = colors.primary)) { append(phrase.take(matched)) }
            // Highlights the character to fix (none if the extra text is past the end).
            if (error && matched < phrase.length) {
                withStyle(SpanStyle(color = colors.onError, background = colors.error)) {
                    append(phrase.substring(matched, matched + 1))
                }
                append(phrase.substring(matched + 1))
            } else {
                append(phrase.substring(matched))
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (phrases.size > 1) {
                Text(
                    stringResource(R.string.typing_progress, done + 1, phrases.size),
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Text(stringResource(R.string.typing_prompt), style = MaterialTheme.typography.bodyMedium)
            Text(shown, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            OutlinedTextField(
                value = typed,
                onValueChange = {
                    // Keyboard input lands in the keyboard's window, not as a touch on this screen.
                    host.onActivity()
                    if (Typing.isDone(phrase, it)) {
                        typed = ""
                        done++
                        if (done == phrases.size) host.onComplete()
                    } else {
                        typed = it
                    }
                },
                label = { Text(stringResource(R.string.typing_input)) },
                isError = error,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
            )
        }
    }

    companion object {
        const val TYPE = "typing"
    }
}

internal data class TypingConfig(val phrases: Int = 1, val custom: List<String> = emptyList()) {
    fun toJson(): String = JSONObject().put("phrases", phrases).put("custom", JSONArray(custom)).toString()

    companion object {
        const val MIN_PHRASES = 1
        const val MAX_PHRASES = 5

        /** Cleaned, without empty ones. */
        fun lines(lines: List<String>): List<String> = lines.map(Typing::clean).filter(String::isNotEmpty)

        /** Never fails: a broken config gives the defaults, so the alarm still has a mission to solve. */
        fun parse(json: String): TypingConfig = runCatching {
            val o = JSONObject(json)
            val custom = o.optJSONArray("custom")
            TypingConfig(
                o.optInt("phrases", 1).coerceIn(MIN_PHRASES, MAX_PHRASES),
                lines(if (custom == null) emptyList() else List(custom.length()) { custom.optString(it) })
            )
        }.getOrDefault(TypingConfig())
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class TypingMissionModule {
    @Binds
    @IntoMap
    @StringKey(TypingMission.TYPE)
    abstract fun bind(mission: TypingMission): Mission
}
