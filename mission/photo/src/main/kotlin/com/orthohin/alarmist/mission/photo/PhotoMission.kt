package com.orthohin.alarmist.mission.photo

import android.Manifest
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.util.Size
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orthohin.alarmist.core.model.MissionSpec
import com.orthohin.alarmist.mission.api.Mission
import com.orthohin.alarmist.mission.api.MissionCategory
import com.orthohin.alarmist.mission.api.MissionHost
import com.orthohin.alarmist.mission.api.RadioOptions
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
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.delay
import org.json.JSONObject

/** Find the view of a registered photo (e.g. the bathroom sink), or take any photo if none is registered. */
internal class PhotoMission @Inject constructor() : Mission {
    override val type = TYPE
    override val name = R.string.photo_name
    override val icon = R.drawable.ic_mission_photo
    override val description = R.string.photo_description
    override val category = MissionCategory.GetUp
    override val configVersion = 1
    override val permissions = listOf(Manifest.permission.CAMERA)

    override fun defaultConfig(): String = PhotoConfig().toJson()

    override fun summary(spec: MissionSpec, resources: Resources): String {
        val config = PhotoConfig.parse(spec.config)
        if (config.photo == null) return resources.getString(R.string.photo_any)
        if (config.name.isBlank()) return resources.getString(R.string.photo_summary_unnamed)
        return resources.getString(R.string.photo_summary, config.name)
    }

    override fun isAvailable(context: Context): Boolean = hasCamera(context)

    // The ringing screen can't ask for the permission over the lock screen.
    override fun isReady(context: Context): Boolean = hasCamera(context) && hasCameraPermission(context)

    @Composable
    override fun ConfigEditor(spec: MissionSpec, onChange: (config: String) -> Unit) {
        val config = PhotoConfig.parse(spec.config)
        val photo = remember(config.photo) { config.photo?.let(PhotoCodec::decode) }
        var registering by rememberSaveable { mutableStateOf(false) }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.photo_photo), style = MaterialTheme.typography.titleSmall)
            if (photo == null) {
                Text(stringResource(R.string.photo_any_detail), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { registering = true }) { Text(stringResource(R.string.photo_register)) }
            } else {
                Image(
                    photo.asImageBitmap(),
                    stringResource(R.string.photo_registered),
                    Modifier
                        .heightIn(max = 240.dp)
                        .clip(MaterialTheme.shapes.medium)
                )
                OutlinedTextField(
                    value = config.name,
                    onValueChange = { onChange(config.copy(name = it.take(PhotoConfig.NAME_LENGTH)).toJson()) },
                    label = { Text(stringResource(R.string.photo_name_label)) },
                    placeholder = { Text(stringResource(R.string.photo_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { registering = true }) { Text(stringResource(R.string.photo_retake)) }
                    TextButton(onClick = { onChange(PhotoConfig().toJson()) }) {
                        Text(stringResource(R.string.photo_use_any))
                    }
                }
                RadioOptions(
                    title = stringResource(R.string.photo_level),
                    options = PhotoMatcher.Level.entries,
                    selected = config.level,
                    label = { stringResource(it.label) },
                    onSelect = { onChange(config.copy(level = it).toJson()) },
                    detail = { level -> level.detail?.let { stringResource(it) } }
                )
            }
        }
        if (registering) {
            RegisterDialog(
                onPhoto = {
                    registering = false
                    onChange(config.copy(photo = it).toJson())
                },
                onDismiss = { registering = false }
            )
        }
    }

    @Composable
    override fun Runner(spec: MissionSpec, host: MissionHost) {
        val config = remember(spec.config) { PhotoConfig.parse(spec.config) }
        // A photo that doesn't decode (a broken config) means any photo, so the alarm stays solvable.
        val photo = remember(config.photo) { config.photo?.let(PhotoCodec::decode) }
        var solved by rememberSaveable { mutableStateOf(false) }
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
                stringResource(if (photo == null) R.string.photo_prompt_any else R.string.photo_prompt),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            if (photo != null && config.name.isNotBlank()) {
                Text(config.name, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
            var progress by remember { mutableFloatStateOf(0f) }
            var dark by remember { mutableStateOf(false) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                CameraPermissionGate(stringResource(R.string.photo_camera_needed)) {
                    val cameraModifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.large)
                    if (photo == null) {
                        CameraView<Unit>(
                            analyze = { null },
                            onResult = {},
                            onMotion = host::onActivity,
                            analysisSize = ANALYSIS_SIZE,
                            modifier = cameraModifier,
                            overlay = { Shutter(enabled = !solved) { solved = true } }
                        )
                    } else {
                        val matcher = remember(photo) { LiveMatcher(photo) }
                        var hits by remember { mutableIntStateOf(0) }
                        CameraView(
                            analyze = matcher::analyze,
                            onResult = { result ->
                                if (!solved) {
                                    progress = result.score.toFloat() / config.level.minScore
                                    dark = result.dark
                                    // Two frames in a row, so one lucky frame doesn't count.
                                    hits = if (result.score >= config.level.minScore) hits + 1 else 0
                                    if (hits >= MATCHING_FRAMES) solved = true
                                }
                            },
                            onMotion = host::onActivity,
                            analysisSize = ANALYSIS_SIZE,
                            modifier = cameraModifier,
                            overlay = { Thumbnail(photo) }
                        )
                    }
                }
            }
            if (photo != null) {
                LinearProgressIndicator(
                    progress = { if (solved) 1f else progress.coerceIn(0f, 1f) },
                    modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()
                )
            }
            Text(
                when {
                    solved -> stringResource(R.string.photo_correct)
                    photo == null -> ""
                    dark -> stringResource(R.string.photo_dark)
                    else -> stringResource(R.string.photo_hint)
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (dark && !solved) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
    }

    companion object {
        const val TYPE = "photo"
        private const val RESULT_MILLIS = 600L
        private const val MATCHING_FRAMES = 2
    }
}

// 4:3, the sensor's full view; frames are scaled down to PhotoMatcher.WORKING_SIZE anyway.
private val ANALYSIS_SIZE = Size(640, 480)

/** The registered photo in a corner of the preview, so the user knows what to look for. */
@Composable
private fun BoxScope.Thumbnail(photo: Bitmap) {
    Image(
        photo.asImageBitmap(),
        stringResource(R.string.photo_registered),
        Modifier
            .align(Alignment.TopStart)
            .padding(12.dp)
            .fillMaxWidth(THUMBNAIL_WIDTH)
            .clip(MaterialTheme.shapes.medium)
            .border(2.dp, Color.White, MaterialTheme.shapes.medium),
        contentScale = ContentScale.FillWidth
    )
}

private const val THUMBNAIL_WIDTH = 0.3f

@Composable
private fun BoxScope.Shutter(enabled: Boolean, onClick: () -> Unit) {
    LargeFloatingActionButton(
        onClick = { if (enabled) onClick() },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(16.dp)
    ) {
        Icon(painterResource(R.drawable.ic_mission_photo), stringResource(R.string.photo_take))
    }
}

/** Full-screen camera for registering a photo; closes once it has one with enough detail. */
@Composable
private fun RegisterDialog(onPhoto: (String) -> Unit, onDismiss: () -> Unit) {
    CameraDialog(stringResource(R.string.photo_register), onDismiss) {
        Text(stringResource(R.string.photo_register_prompt), style = MaterialTheme.typography.bodyLarge)
        val capture = remember { AtomicBoolean(false) }
        var busy by remember { mutableStateOf(false) }
        var tooPlain by remember { mutableStateOf(false) }
        Column(Modifier.weight(1f)) {
            CameraPermissionGate(stringResource(R.string.photo_camera_needed)) {
                CameraView(
                    // The next frame after the button, encoded on the analysis thread.
                    analyze = { if (capture.getAndSet(false)) Registration.of(it) else null },
                    onResult = {
                        busy = false
                        if (it.recognisable) onPhoto(it.photo) else tooPlain = true
                    },
                    analysisSize = ANALYSIS_SIZE,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.large),
                    overlay = {
                        Shutter(enabled = !busy) {
                            busy = true
                            tooPlain = false
                            capture.set(true)
                        }
                    }
                )
            }
        }
        if (tooPlain) {
            Text(
                stringResource(R.string.photo_too_plain),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@get:StringRes
private val PhotoMatcher.Level.label: Int
    get() = when (this) {
        PhotoMatcher.Level.EASY -> R.string.photo_level_easy
        PhotoMatcher.Level.NORMAL -> R.string.photo_level_normal
        PhotoMatcher.Level.STRICT -> R.string.photo_level_strict
    }

private val PhotoMatcher.Level.detail: Int?
    get() = when (this) {
        PhotoMatcher.Level.EASY -> R.string.photo_level_easy_detail
        PhotoMatcher.Level.NORMAL -> null
        PhotoMatcher.Level.STRICT -> R.string.photo_level_strict_detail
    }

/** [photo] is a Base64 JPEG ([PhotoCodec]); none means any photo dismisses. */
internal data class PhotoConfig(
    val photo: String? = null,
    val name: String = "",
    val level: PhotoMatcher.Level = PhotoMatcher.Level.NORMAL
) {
    fun toJson(): String = JSONObject().apply {
        photo?.let { put("photo", it) }
        put("name", name)
        put("level", level.name.lowercase())
    }.toString()

    companion object {
        const val NAME_LENGTH = 40

        /** Never fails: a broken config means "any photo", so the alarm still has a mission to solve. */
        fun parse(json: String): PhotoConfig = runCatching {
            val o = JSONObject(json)
            PhotoConfig(
                o.optString("photo").ifEmpty { null },
                o.optString("name").take(NAME_LENGTH),
                PhotoMatcher.Level.entries.firstOrNull { it.name.equals(o.optString("level"), ignoreCase = true) }
                    ?: PhotoMatcher.Level.NORMAL
            )
        }.getOrDefault(PhotoConfig())
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PhotoMissionModule {
    @Binds
    @IntoMap
    @StringKey(PhotoMission.TYPE)
    abstract fun bind(mission: PhotoMission): Mission
}
