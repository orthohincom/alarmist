package com.orthohin.alarmist.mission.barcode

import android.Manifest
import android.content.Context
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orthohin.alarmist.core.model.MissionSpec
import com.orthohin.alarmist.mission.api.Mission
import com.orthohin.alarmist.mission.api.MissionCategory
import com.orthohin.alarmist.mission.api.MissionHost
import com.orthohin.alarmist.mission.camera.CameraDialog
import com.orthohin.alarmist.mission.camera.CameraPermissionGate
import com.orthohin.alarmist.mission.camera.CameraView
import com.orthohin.alarmist.mission.camera.hasCamera
import com.orthohin.alarmist.mission.camera.hasCameraPermission
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import javax.inject.Inject
import kotlinx.coroutines.delay
import org.json.JSONObject

/** Scan a barcode or QR code: a registered one (e.g. a product in the bathroom), or any code if none is. */
internal class BarcodeMission @Inject constructor() : Mission {
    override val type = TYPE
    override val name = R.string.barcode_name
    override val icon = R.drawable.ic_mission_barcode
    override val description = R.string.barcode_description
    override val category = MissionCategory.GetUp
    override val configVersion = 1
    override val permissions = listOf(Manifest.permission.CAMERA)

    override fun defaultConfig(): String = BarcodeConfig().toJson()

    override fun summary(spec: MissionSpec, resources: Resources): String {
        val config = BarcodeConfig.parse(spec.config)
        val code = config.code ?: return resources.getString(R.string.barcode_any)
        return resources.getString(R.string.barcode_summary, config.name.ifBlank { resources.getString(code.label) })
    }

    override fun isAvailable(context: Context): Boolean = hasCamera(context)

    // The ringing screen can't ask for the permission over the lock screen.
    override fun isReady(context: Context): Boolean = hasCamera(context) && hasCameraPermission(context)

    @Composable
    override fun ConfigEditor(spec: MissionSpec, onChange: (config: String) -> Unit) {
        val config = BarcodeConfig.parse(spec.config)
        var registering by rememberSaveable { mutableStateOf(false) }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.barcode_code), style = MaterialTheme.typography.titleSmall)
            val code = config.code
            if (code == null) {
                Text(stringResource(R.string.barcode_any_detail), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { registering = true }) { Text(stringResource(R.string.barcode_register)) }
            } else {
                Text(stringResource(R.string.barcode_registered, stringResource(code.label)))
                Text(
                    code.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                OutlinedTextField(
                    value = config.name,
                    onValueChange = { onChange(config.copy(name = it.take(BarcodeConfig.NAME_LENGTH)).toJson()) },
                    label = { Text(stringResource(R.string.barcode_name_label)) },
                    placeholder = { Text(stringResource(R.string.barcode_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { registering = true }) { Text(stringResource(R.string.barcode_rescan)) }
                    TextButton(onClick = { onChange(BarcodeConfig().toJson()) }) {
                        Text(stringResource(R.string.barcode_use_any))
                    }
                }
            }
        }
        if (registering) {
            RegisterDialog(
                onScan = {
                    registering = false
                    onChange(config.copy(code = it).toJson())
                },
                onDismiss = { registering = false }
            )
        }
    }

    @Composable
    override fun Runner(spec: MissionSpec, host: MissionHost) {
        val config = remember(spec.config) { BarcodeConfig.parse(spec.config) }
        val code = config.code
        var solved by rememberSaveable { mutableStateOf(false) }
        var wrong by remember { mutableStateOf(false) }
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
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(if (code == null) R.string.barcode_prompt_any else R.string.barcode_prompt),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            if (code != null && config.name.isNotBlank()) {
                Text(config.name, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                CameraPermissionGate(stringResource(R.string.barcode_camera_needed)) {
                    val decoder = remember { BarcodeDecoder() }
                    CameraView(
                        analyze = { decoder.decode(it.luma, it.width, it.height) },
                        onResult = { scan ->
                            if (!solved) {
                                if (code == null || codesMatch(code, scan)) solved = true else wrong = true
                            }
                        },
                        onMotion = host::onActivity,
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxSize()
                            .clip(MaterialTheme.shapes.large)
                    )
                }
            }
            Text(
                when {
                    solved -> stringResource(R.string.barcode_correct)
                    wrong -> stringResource(R.string.barcode_wrong)
                    else -> ""
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (wrong && !solved) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
    }

    companion object {
        const val TYPE = "barcode"
        private const val RESULT_MILLIS = 600L
        private const val WRONG_MILLIS = 2000L
    }
}

/** Full-screen camera for registering a code; closes with the first code it reads. */
@Composable
private fun RegisterDialog(onScan: (Scan) -> Unit, onDismiss: () -> Unit) {
    CameraDialog(stringResource(R.string.barcode_register), onDismiss) {
        Text(stringResource(R.string.barcode_register_prompt), style = MaterialTheme.typography.bodyLarge)
        var scanned by remember { mutableStateOf(false) }
        val confirmer = remember { ScanConfirmer() }
        CameraPermissionGate(stringResource(R.string.barcode_camera_needed)) {
            val decoder = remember { BarcodeDecoder() }
            CameraView(
                analyze = { decoder.decode(it.luma, it.width, it.height) },
                onResult = {
                    // Codes keep coming while one is in view: take the first confirmed one.
                    if (!scanned && confirmer.onScan(it)) {
                        scanned = true
                        onScan(it)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.large)
            )
        }
    }
}

@get:StringRes
private val Scan.label: Int
    get() = if (format == "QR_CODE") R.string.barcode_qr else R.string.barcode_barcode

internal data class BarcodeConfig(val code: Scan? = null, val name: String = "") {
    fun toJson(): String = JSONObject().apply {
        code?.let { put("code", it.text).put("format", it.format) }
        put("name", name)
    }.toString()

    companion object {
        const val NAME_LENGTH = 40

        /** Never fails: a broken config means "any code", so the alarm still has a mission to solve. */
        fun parse(json: String): BarcodeConfig = runCatching {
            val o = JSONObject(json)
            val text = o.optString("code")
            BarcodeConfig(
                if (text.isEmpty()) null else Scan(text, o.optString("format")),
                o.optString("name").take(NAME_LENGTH)
            )
        }.getOrDefault(BarcodeConfig())
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class BarcodeMissionModule {
    @Binds
    @IntoMap
    @StringKey(BarcodeMission.TYPE)
    abstract fun bind(mission: BarcodeMission): Mission
}
