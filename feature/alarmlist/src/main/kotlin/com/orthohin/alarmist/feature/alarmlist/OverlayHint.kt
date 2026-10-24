package com.orthohin.alarmist.feature.alarmlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orthohin.alarmist.core.ringing.OverlayPermission

/**
 * Asks for "display over other apps" until it's allowed or the user taps "Don't show again". Optional: alarms
 * ring without it; it lets the wake-up check and anti-cheat bring their screen to the front.
 */
@Composable
internal fun OverlayHint(viewModel: AlarmListViewModel) {
    val context = LocalContext.current
    val dismissed by viewModel.overlayHintDismissed.collectAsStateWithLifecycle()
    // Re-read on resume: it's granted in system settings.
    var granted by remember { mutableStateOf(OverlayPermission.isGranted(context)) }
    LifecycleResumeEffect(Unit) {
        granted = OverlayPermission.isGranted(context)
        onPauseOrDispose {}
    }
    if (dismissed || granted) return
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 8.dp)) {
            Text(stringResource(R.string.list_overlay_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.list_overlay_text),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, end = 8.dp)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = viewModel::dismissOverlayHint) {
                    Text(stringResource(R.string.list_overlay_dismiss))
                }
                Button(onClick = { context.startActivity(OverlayPermission.requestIntent(context)) }) {
                    Text(stringResource(R.string.list_overlay_allow))
                }
            }
        }
    }
}
