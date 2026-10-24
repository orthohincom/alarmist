package com.orthohin.alarmist.mission.nfc

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.hardware.Sensor
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orthohin.alarmist.core.model.MissionSpec
import com.orthohin.alarmist.mission.api.Mission
import com.orthohin.alarmist.mission.api.MissionCategory
import com.orthohin.alarmist.mission.api.MissionHost
import com.orthohin.alarmist.mission.api.SensorEffect
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import javax.inject.Inject
import kotlinx.coroutines.delay
import org.json.JSONObject

/** Scan an NFC tag: a registered one (e.g. a sticker on the bathroom mirror), or any tag if none is. */
internal class NfcMission @Inject constructor() : Mission {
    override val type = TYPE
    override val name = R.string.nfc_name
    override val icon = R.drawable.ic_mission_nfc
    override val description = R.string.nfc_description
    override val category = MissionCategory.GetUp
    override val configVersion = 1

    override fun defaultConfig(): String = NfcConfig().toJson()

    override fun summary(spec: MissionSpec, resources: Resources): String {
        val config = NfcConfig.parse(spec.config)
        if (config.tag == null) return resources.getString(R.string.nfc_any)
        if (config.name.isBlank()) return resources.getString(R.string.nfc_summary_unnamed)
        return resources.getString(R.string.nfc_summary, config.name)
    }

    override fun isAvailable(context: Context): Boolean = hasNfc(context)

    // NFC switched off can't be fixed from the lock screen, so the ringing screen offers a skip.
    override fun isReady(context: Context): Boolean = hasNfc(context) && nfcEnabled(context)

    @Composable
    override fun ConfigEditor(spec: MissionSpec, onChange: (config: String) -> Unit) {
        val config = NfcConfig.parse(spec.config)
        var registering by rememberSaveable { mutableStateOf(false) }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!rememberNfcEnabled()) NfcOffNotice()
            Text(stringResource(R.string.nfc_tag), style = MaterialTheme.typography.titleSmall)
            val tag = config.tag
            if (tag == null) {
                Text(stringResource(R.string.nfc_any_detail), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { registering = true }) { Text(stringResource(R.string.nfc_register)) }
            } else {
                Text(stringResource(R.string.nfc_registered, tag))
                OutlinedTextField(
                    value = config.name,
                    onValueChange = { onChange(config.copy(name = it.take(NfcConfig.NAME_LENGTH)).toJson()) },
                    label = { Text(stringResource(R.string.nfc_name_label)) },
                    placeholder = { Text(stringResource(R.string.nfc_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { registering = true }) { Text(stringResource(R.string.nfc_reregister)) }
                    TextButton(onClick = { onChange(NfcConfig().toJson()) }) {
                        Text(stringResource(R.string.nfc_use_any))
                    }
                }
            }
        }
        if (registering) {
            RegisterDialog(
                onTag = {
                    registering = false
                    onChange(config.copy(tag = it).toJson())
                },
                onDismiss = { registering = false }
            )
        }
    }

    @Composable
    override fun Runner(spec: MissionSpec, host: MissionHost) {
        val config = remember(spec.config) { NfcConfig.parse(spec.config) }
        val tag = config.tag
        val context = LocalContext.current
        val activity = LocalActivity.current
        var solved by rememberSaveable { mutableStateOf(false) }
        var wrong by remember { mutableStateOf(false) }
        var locked by remember { mutableStateOf(isLocked(context)) }
        val enabled = rememberNfcEnabled()
        LaunchedEffect(wrong) {
            if (wrong) {
                delay(WRONG_MILLIS)
                wrong = false
            }
        }
        LaunchedEffect(solved) {
            if (solved) {
                delay(RESULT_MILLIS)
                host.onComplete()
            }
        }
        NfcReaderEffect { bytes ->
            host.onActivity()
            if (!solved) {
                if (tag == null || tagId(bytes) == tag) solved = true else wrong = true
            }
        }
        // Walking to the tag has no touches: carrying the phone keeps the alarm muted.
        val motion = remember { MotionGate() }
        SensorEffect(Sensor.TYPE_ACCELEROMETER, Unit) {
            if (motion.moved(it.values[0], it.values[1], it.values[2], it.timestamp)) host.onActivity()
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            Text(
                stringResource(if (tag == null) R.string.nfc_prompt_any else R.string.nfc_prompt),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            if (tag != null && config.name.isNotBlank()) {
                Text(config.name, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
            Icon(
                painterResource(R.drawable.ic_mission_nfc),
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                stringResource(if (enabled) R.string.nfc_hold else R.string.nfc_off_now),
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            if (locked && !solved && activity != null) {
                Text(
                    stringResource(R.string.nfc_locked),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                OutlinedButton(onClick = { requestUnlock(activity) { locked = false } }) {
                    Text(stringResource(R.string.nfc_unlock))
                }
            }
            Text(
                when {
                    solved -> stringResource(R.string.nfc_correct)
                    wrong -> stringResource(R.string.nfc_wrong)
                    else -> ""
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (wrong && !solved) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
    }

    companion object {
        const val TYPE = "nfc"
        private const val RESULT_MILLIS = 600L
        private const val WRONG_MILLIS = 2000L
    }
}

/** NFC is off: a warning with a way to the NFC settings. */
@Composable
private fun NfcOffNotice() {
    val context = LocalContext.current
    Text(
        stringResource(R.string.nfc_off),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error
    )
    OutlinedButton(onClick = {
        context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }) { Text(stringResource(R.string.nfc_settings)) }
}

/** Registers a tag with two taps that agree; refuses tags whose ID changes. */
@Composable
private fun RegisterDialog(onTag: (String) -> Unit, onDismiss: () -> Unit) {
    val confirmer = remember { TagConfirmer() }
    var step by remember { mutableStateOf<TagConfirmer.Result?>(null) }
    var done by remember { mutableStateOf(false) }
    NfcReaderEffect { bytes ->
        if (done) return@NfcReaderEffect
        step = confirmer.onTag(bytes)
        if (step == TagConfirmer.Result.CONFIRMED) {
            done = true
            onTag(tagId(bytes))
        }
    }
    val enabled = rememberNfcEnabled()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_mission_nfc), contentDescription = null) },
        title = { Text(stringResource(R.string.nfc_register)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!enabled) NfcOffNotice()
                Text(
                    stringResource(
                        when (step) {
                            TagConfirmer.Result.FIRST -> R.string.nfc_again
                            else -> R.string.nfc_hold
                        }
                    )
                )
                if (step == TagConfirmer.Result.CHANGES) {
                    Text(stringResource(R.string.nfc_random), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.nfc_cancel)) } }
    )
}

/** [tag] is a [tagId]; none means any tag dismisses. */
internal data class NfcConfig(val tag: String? = null, val name: String = "") {
    fun toJson(): String = JSONObject().apply {
        tag?.let { put("tag", it) }
        put("name", name)
    }.toString()

    companion object {
        const val NAME_LENGTH = 40

        /** Never fails: a broken config means "any tag", so the alarm still has a mission to solve. */
        fun parse(json: String): NfcConfig = runCatching {
            val o = JSONObject(json)
            NfcConfig(o.optString("tag").ifEmpty { null }, o.optString("name").take(NAME_LENGTH))
        }.getOrDefault(NfcConfig())
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NfcMissionModule {
    @Binds
    @IntoMap
    @StringKey(NfcMission.TYPE)
    abstract fun bind(mission: NfcMission): Mission
}
