package com.orthohin.alarmist.mission.camera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

fun hasCamera(context: Context): Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/**
 * Shows [content] once the camera permission is granted. Asks once by itself; after a refusal, [rationale] and a
 * button ask again, and the app settings are the way back once Android stops showing the dialog.
 */
@Composable
fun CameraPermissionGate(rationale: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    var refusals by rememberSaveable { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        if (!it) refusals++
    }
    // Back from the app settings.
    LifecycleResumeEffect(Unit) {
        granted = hasCameraPermission(context)
        onPauseOrDispose {}
    }
    LaunchedEffect(Unit) { if (!granted && refusals == 0) launcher.launch(Manifest.permission.CAMERA) }
    if (granted) {
        content()
        return
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text(rationale, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (refusals < 2) {
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                Text(stringResource(R.string.camera_allow))
            }
        } else {
            Button(onClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }) { Text(stringResource(R.string.camera_settings)) }
        }
    }
}

/** One camera frame, valid only during the `analyze` call that received it. */
class CameraFrame internal constructor(
    /** The Y (brightness) plane, [width]×[height], as the sensor delivers it. */
    val luma: ByteArray,
    val width: Int,
    val height: Int,
    /** Turn the frame clockwise by this much to see it upright. */
    val rotationDegrees: Int,
    private val image: ImageProxy
) {
    /** The frame in colour, turned upright. */
    fun toBitmap(): Bitmap {
        val bitmap = image.toBitmap()
        if (rotationDegrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}

/**
 * Camera preview with frame analysis: [analyze] runs on a background thread for each frame (dropping frames while
 * it's busy), and its non-null results go to [onResult] on the main thread. [onMotion] is called at most once a
 * second while the image changes (the phone is carried). Back camera, else front; a torch button if there's a
 * flash; [overlay] is drawn over the preview. Frames are never kept unless [analyze] keeps them.
 */
@Composable
fun <R : Any> CameraView(
    analyze: (CameraFrame) -> R?,
    onResult: (R) -> Unit,
    modifier: Modifier = Modifier,
    onMotion: () -> Unit = {},
    analysisSize: android.util.Size = android.util.Size(1280, 720),
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentAnalyze by rememberUpdatedState(analyze)
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnMotion by rememberUpdatedState(onMotion)
    val controller = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            imageAnalysisResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(analysisSize, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                )
                .build()
        }
    }
    DisposableEffect(controller, lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val main = ContextCompat.getMainExecutor(context)
        val motion = MotionDetector()
        var lastMotion = Long.MIN_VALUE
        controller.setImageAnalysisAnalyzer(executor) { image ->
            image.use {
                val plane = it.planes[0]
                val luma = lumaFromPlane(plane.buffer, it.width, it.height, plane.rowStride, plane.pixelStride)
                // The frame's own timestamp, so no wall clock is needed for the throttle.
                val time = it.imageInfo.timestamp
                if (motion.moved(luma, it.width, it.height) && time - lastMotion >= MOTION_GAP_NANOS) {
                    lastMotion = time
                    main.execute { currentOnMotion() }
                }
                val frame = CameraFrame(luma, it.width, it.height, it.imageInfo.rotationDegrees, it)
                currentAnalyze(frame)?.let { result -> main.execute { currentOnResult(result) } }
            }
        }
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.unbind()
            controller.clearImageAnalysisAnalyzer()
            executor.shutdown()
        }
    }
    var torch by remember { mutableStateOf(false) }
    LaunchedEffect(torch) { controller.enableTorch(torch) }
    Box(modifier) {
        AndroidView(
            factory = {
                PreviewView(it).apply {
                    // A TextureView, so the preview clips to the rounded shape like any other view.
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    this.controller = controller
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        overlay()
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            FilledTonalIconToggleButton(
                checked = torch,
                onCheckedChange = { torch = it },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
            ) {
                Icon(
                    painterResource(if (torch) R.drawable.ic_torch_on else R.drawable.ic_torch_off),
                    stringResource(R.string.camera_torch)
                )
            }
        }
    }
}

/** A full-screen dialog with a close button and [title], for registering what a camera mission looks for. */
@Composable
fun CameraDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.camera_close))
                    }
                    Text(title, style = MaterialTheme.typography.titleLarge)
                }
                content()
            }
        }
    }
}

private const val MOTION_GAP_NANOS = 1_000_000_000L
