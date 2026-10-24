package com.orthohin.alarmist.feature.alarmedit

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.orthohin.alarmist.core.model.Alarm
import com.orthohin.alarmist.core.ringing.OverlayPermission
import com.orthohin.alarmist.core.ringing.PowerMenuGuard

/**
 * Anti-cheat switches (all opt-in). Keeping the screen in front needs "display over other apps"; the power-menu
 * guard exists only in builds with [guard] and is an accessibility service, so turning it on explains that first.
 */
@Composable
internal fun AntiCheatCard(alarm: Alarm, guard: PowerMenuGuard?, onEdit: ((Alarm) -> Alarm) -> Unit) {
    val context = LocalContext.current
    val antiCheat = alarm.antiCheat
    // Both can change in system settings while this screen is away.
    var overlayAllowed by remember { mutableStateOf(OverlayPermission.isGranted(context)) }
    var guardEnabled by remember { mutableStateOf(guard?.isEnabled() == true) }
    LifecycleResumeEffect(Unit) {
        overlayAllowed = OverlayPermission.isGranted(context)
        guardEnabled = guard?.isEnabled() == true
        onPauseOrDispose {}
    }
    var disclosure by remember { mutableStateOf(false) }

    SettingsCard(stringResource(R.string.edit_anti_cheat_header)) {
        val needsOverlay = antiCheat.relaunchIfLeft && !overlayAllowed
        SwitchRow(
            title = stringResource(R.string.edit_relaunch),
            hint = stringResource(R.string.edit_relaunch_summary),
            summary = if (needsOverlay) stringResource(R.string.edit_relaunch_needs_overlay) else null,
            checked = antiCheat.relaunchIfLeft,
            onChange = { on ->
                onEdit { it.copy(antiCheat = it.antiCheat.copy(relaunchIfLeft = on)) }
                if (on && !overlayAllowed) context.startActivity(OverlayPermission.requestIntent(context))
            }
        )
        if (needsOverlay) {
            TextButton(
                onClick = { context.startActivity(OverlayPermission.requestIntent(context)) },
                modifier = Modifier.padding(start = 4.dp)
            ) { Text(stringResource(R.string.edit_allow_overlay)) }
        }
        SwitchRow(
            title = stringResource(R.string.edit_lock_volume),
            hint = stringResource(R.string.edit_lock_volume_summary),
            checked = antiCheat.lockVolume,
            onChange = { on -> onEdit { it.copy(antiCheat = it.antiCheat.copy(lockVolume = on)) } }
        )
        if (guard != null) {
            val guardOff = antiCheat.powerMenuGuard && !guardEnabled
            SwitchRow(
                title = stringResource(R.string.edit_power_guard),
                hint = stringResource(R.string.edit_power_guard_summary),
                summary = if (guardOff) stringResource(R.string.edit_power_guard_off) else null,
                checked = antiCheat.powerMenuGuard,
                onChange = { on ->
                    if (on && !guardEnabled) {
                        disclosure = true
                    } else {
                        onEdit { it.copy(antiCheat = it.antiCheat.copy(powerMenuGuard = on)) }
                    }
                }
            )
            if (guardOff) {
                TextButton(onClick = { disclosure = true }, modifier = Modifier.padding(start = 4.dp)) {
                    Text(stringResource(R.string.edit_power_guard_turn_on))
                }
            }
        }
    }
    if (disclosure && guard != null) {
        AlertDialog(
            onDismissRequest = { disclosure = false },
            title = { Text(stringResource(R.string.edit_power_guard_disclosure_title)) },
            text = {
                Text(stringResource(R.string.edit_power_guard_disclosure), style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                TextButton(onClick = {
                    disclosure = false
                    onEdit { it.copy(antiCheat = it.antiCheat.copy(powerMenuGuard = true)) }
                    context.startActivity(guard.settingsIntent())
                }) { Text(stringResource(R.string.edit_power_guard_open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { disclosure = false }) { Text(stringResource(R.string.edit_cancel)) }
            }
        )
    }
}
