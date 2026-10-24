package com.orthohin.alarmist.mission.api

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// Building blocks for missions that count something from a sensor (shake, steps, squats).

fun hasSensor(context: Context, type: Int): Boolean =
    context.getSystemService(SensorManager::class.java)?.getDefaultSensor(type) != null

/**
 * Listens to the default sensor of [type] while in composition (main thread, `SENSOR_DELAY_GAME`). Re-registers
 * when [type] or [key] change. Does nothing if the sensor is missing.
 */
@Composable
fun SensorEffect(type: Int, key: Any?, onEvent: (SensorEvent) -> Unit) {
    val context = LocalContext.current
    val currentOnEvent by rememberUpdatedState(onEvent)
    DisposableEffect(type, key) {
        val sensors = context.getSystemService(SensorManager::class.java)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) = currentOnEvent(event)

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        sensors?.getDefaultSensor(type)?.let { sensors.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        onDispose { sensors?.unregisterListener(listener) }
    }
}

/** Runner layout for counting missions: [prompt], a ring filling up around [icon], then [left] ("12 steps to go"). */
@Composable
fun CountingProgress(prompt: String, icon: Painter, count: Int, total: Int, left: String, hint: String? = null) {
    val progress by animateFloatAsState(count.toFloat() / total, label = "count")
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)
    ) {
        Text(prompt, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(progress = { progress }, strokeWidth = 12.dp, modifier = Modifier.size(200.dp))
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp))
        }
        Text(left, style = MaterialTheme.typography.titleLarge)
        hint?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
