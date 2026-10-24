package com.orthohin.alarmist.feature.alarmedit

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orthohin.alarmist.core.data.TimeInput
import com.orthohin.alarmist.core.designsystem.ringsInText
import com.orthohin.alarmist.core.designsystem.secondsText
import com.orthohin.alarmist.core.designsystem.weekOrder
import com.orthohin.alarmist.core.model.Alarm
import com.orthohin.alarmist.core.ringing.PowerMenuGuard
import com.orthohin.alarmist.mission.api.MissionRegistry
import java.time.DayOfWeek
import java.time.format.TextStyle
import kotlinx.coroutines.launch

/** The navigation argument [AlarmEditScreen] reads: the alarm to edit, or `Alarm.NEW_ID` for a new one. */
const val ALARM_ID_ARG = "alarmId"

/** Edits the alarm in the [ALARM_ID_ARG] navigation argument. [onClose] runs after save, delete or close. */
@Composable
fun AlarmEditScreen(onClose: () -> Unit) {
    val viewModel: AlarmEditViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AlarmEditEvent.Saved -> {
                    event.nextRing?.let {
                        Toast.makeText(context, resources.ringsInText(event.now, it), Toast.LENGTH_SHORT).show()
                    }
                    onClose()
                }

                AlarmEditEvent.Deleted -> onClose()

                AlarmEditEvent.PreviewSet -> launch {
                    snackbar.showSnackbar(resources.getString(R.string.edit_preview_set))
                }
            }
        }
    }
    val current = state ?: return
    AlarmEditContent(
        state = current,
        missions = viewModel.missions,
        powerMenuGuard = viewModel.powerMenuGuard,
        snackbar = snackbar,
        onEdit = viewModel::edit,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onPreview = viewModel::preview,
        onClose = onClose
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmEditContent(
    state: AlarmEditUiState,
    missions: MissionRegistry,
    powerMenuGuard: PowerMenuGuard?,
    snackbar: SnackbarHostState,
    onEdit: ((Alarm) -> Alarm) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onPreview: () -> Unit,
    onClose: () -> Unit
) {
    val alarm = state.alarm
    val resources = LocalResources.current
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.edit_title_new else R.string.edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.edit_close))
                    }
                },
                actions = { TextButton(onClick = onSave) { Text(stringResource(R.string.edit_save)) } }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp)
        ) {
            when (state.timeInput) {
                TimeInput.WHEELS -> TimeWheel(alarm.time, onEdit = { change ->
                    onEdit { it.copy(time = change(it.time)) }
                })

                TimeInput.DIAL -> TimeDial(
                    alarm.time,
                    onTime = { time -> onEdit { it.copy(time = time) } },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
            Text(
                text = state.nextRing?.let { resources.ringsInText(state.now, it) }
                    ?: stringResource(R.string.edit_never_rings),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp)
            )
            DaysSection(alarm, onEdit)
            LabelField(alarm, onEdit)
            MissionsCard(alarm, missions, onEdit)
            SoundCard(alarm, state.soundTitle, onEdit)
            SnoozeCard(alarm, onEdit)
            AntiCheatCard(alarm, powerMenuGuard, onEdit)
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onPreview, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.edit_preview))
                }
                if (!state.isNew) {
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.edit_delete)) }
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.edit_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.edit_delete_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.edit_cancel)) }
            }
        )
    }
}

/** "Repeat" with a "Daily" box (all seven days, or none), then one toggle per day. No day rings once. */
@Composable
private fun DaysSection(alarm: Alarm, onEdit: ((Alarm) -> Alarm) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val days = alarm.days
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (days.isEmpty()) R.string.edit_rings_once else R.string.edit_repeat),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .toggleable(value = alarm.isDaily, role = Role.Checkbox) { on -> onEdit { it.withDaily(on) } }
                    .padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.edit_daily), style = MaterialTheme.typography.bodyMedium)
                Checkbox(checked = alarm.isDaily, onCheckedChange = null, modifier = Modifier.padding(start = 8.dp))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (day in weekOrder(locale)) {
                DayToggle(day, selected = day in days, onClick = { onEdit { it.toggleDay(day) } })
            }
        }
    }
}

@Composable
private fun RowScope.DayToggle(day: DayOfWeek, selected: Boolean, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .weight(1f)
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(if (selected) colors.primary else colors.surfaceContainerHigh)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
            .semantics { contentDescription = day.getDisplayName(TextStyle.FULL, locale) },
        contentAlignment = Alignment.Center
    ) {
        Text(
            day.getDisplayName(TextStyle.NARROW_STANDALONE, locale),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.onPrimary else colors.onSurfaceVariant
        )
    }
}

@Composable
private fun LabelField(alarm: Alarm, onEdit: ((Alarm) -> Alarm) -> Unit) {
    OutlinedTextField(
        value = alarm.label,
        onValueChange = { label -> onEdit { it.copy(label = label) } },
        placeholder = { Text(stringResource(R.string.edit_label)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_edit), contentDescription = null) },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = Color.Transparent,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

/** Sound, volume with a vibrate toggle, gradual volume. */
@Composable
private fun SoundCard(alarm: Alarm, soundTitle: String?, onEdit: ((Alarm) -> Alarm) -> Unit) {
    val resources = LocalResources.current
    val sound = alarm.sound
    var picking by rememberSaveable { mutableStateOf(false) }
    SettingsCard(stringResource(R.string.edit_sound_header)) {
        ValueRow(
            title = stringResource(R.string.edit_sound),
            value = soundTitle ?: stringResource(R.string.edit_sound_missing),
            onClick = { picking = true }
        )
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_volume), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = sound.volumePercent.toFloat(),
                onValueChange = { v -> onEdit { it.withVolume(volumeStep(v)) } },
                valueRange = 0f..100f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
                    .semantics { contentDescription = resources.getString(R.string.edit_volume) }
            )
            Text(
                if (sound.volumePercent == 0) {
                    stringResource(R.string.edit_vibrate_only)
                } else {
                    stringResource(R.string.edit_volume_percent, sound.volumePercent)
                },
                style = MaterialTheme.typography.labelMedium
            )
            IconToggleButton(
                checked = sound.vibrate,
                onCheckedChange = { on -> onEdit { it.withVibrate(on) } },
                enabled = alarm.canTurnOffVibration || !sound.vibrate
            ) {
                Icon(
                    painterResource(R.drawable.ic_vibrate),
                    contentDescription = stringResource(R.string.edit_vibrate),
                    tint = if (sound.vibrate) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_ALPHA)
                    }
                )
            }
        }
        ChoiceRow(
            title = stringResource(R.string.edit_ramp),
            value = sound.rampUpSeconds,
            options = (RAMP_UP_SECONDS + sound.rampUpSeconds).distinct().sorted(),
            label = { if (it == 0) resources.getString(R.string.edit_off) else resources.secondsText(it) },
            onSelect = { s -> onEdit { it.copy(sound = it.sound.copy(rampUpSeconds = s)) } }
        )
    }
    if (picking) {
        SoundPicker(
            config = sound,
            onSelect = { choice -> onEdit { it.withSound(choice) } },
            onClose = { picking = false }
        )
    }
}

/** Snooze (a sheet with its length and count) and "Stop if unanswered". */
@Composable
private fun SnoozeCard(alarm: Alarm, onEdit: ((Alarm) -> Alarm) -> Unit) {
    val resources = LocalResources.current
    val snooze = alarm.snooze
    var editing by rememberSaveable { mutableStateOf(false) }
    val count = { n: Int? ->
        if (n ==
            null
        ) {
            resources.getString(R.string.edit_unlimited)
        } else {
            resources.getQuantityString(R.plurals.edit_times, n, n)
        }
    }
    SettingsCard(stringResource(R.string.edit_snooze_card)) {
        ValueRow(
            title = stringResource(R.string.edit_snooze_header),
            value = if (snooze.enabled) {
                stringResource(
                    R.string.edit_two_values,
                    resources.secondsText(snooze.minutes * 60),
                    count(snooze.maxCount)
                )
            } else {
                stringResource(R.string.edit_off)
            },
            onClick = { editing = true }
        )
        SwitchRow(
            title = stringResource(R.string.edit_auto_silence),
            hint = with(alarm.autoSilence) {
                stringResource(R.string.edit_auto_silence_on, ringMinutes, snoozeMinutes, maxRings)
            },
            checked = alarm.autoSilence.enabled,
            onChange = { on -> onEdit { it.copy(autoSilence = it.autoSilence.copy(enabled = on)) } }
        )
    }
    if (editing) {
        OptionsSheet(stringResource(R.string.edit_snooze_header), onDismiss = { editing = false }) {
            SwitchRow(
                title = stringResource(R.string.edit_snooze),
                checked = snooze.enabled,
                onChange = { on -> onEdit { it.copy(snooze = it.snooze.copy(enabled = on)) } }
            )
            ChoiceRow(
                title = stringResource(R.string.edit_snooze_length),
                value = snooze.minutes,
                options = (SNOOZE_MINUTES + snooze.minutes).distinct().sorted(),
                label = { resources.secondsText(it * 60) },
                enabled = snooze.enabled,
                onSelect = { m -> onEdit { it.copy(snooze = it.snooze.copy(minutes = m)) } }
            )
            ChoiceRow(
                title = stringResource(R.string.edit_snooze_max),
                value = snooze.maxCount,
                options = (SNOOZE_MAX_COUNTS + snooze.maxCount).distinct().sortedBy { it ?: Int.MAX_VALUE },
                label = count,
                enabled = snooze.enabled,
                onSelect = { n -> onEdit { it.copy(snooze = it.snooze.copy(maxCount = n)) } }
            )
        }
    }
}

/** The wake-up check as one row ("After 5 minutes"); its sheet has the switch and both times. */
@Composable
internal fun WakeUpCheckRow(alarm: Alarm, onEdit: ((Alarm) -> Alarm) -> Unit) {
    val resources = LocalResources.current
    val check = alarm.wakeUpCheck
    var editing by rememberSaveable { mutableStateOf(false) }
    ValueRow(
        title = stringResource(R.string.edit_check_header),
        value = if (check.enabled) {
            stringResource(R.string.edit_check_value, resources.secondsText(check.delayMinutes * 60))
        } else {
            stringResource(R.string.edit_off)
        },
        onClick = { editing = true }
    )
    if (editing) {
        OptionsSheet(
            stringResource(R.string.edit_check_header),
            description = stringResource(R.string.edit_check_summary),
            onDismiss = { editing = false }
        ) {
            SwitchRow(
                title = stringResource(R.string.edit_check),
                checked = check.enabled,
                onChange = { on -> onEdit { it.copy(wakeUpCheck = it.wakeUpCheck.copy(enabled = on)) } }
            )
            ChoiceRow(
                title = stringResource(R.string.edit_check_delay),
                value = check.delayMinutes,
                options = (CHECK_DELAY_MINUTES + check.delayMinutes).distinct().sorted(),
                label = { resources.secondsText(it * 60) },
                enabled = check.enabled,
                onSelect = { m -> onEdit { it.copy(wakeUpCheck = it.wakeUpCheck.copy(delayMinutes = m)) } }
            )
            ChoiceRow(
                title = stringResource(R.string.edit_check_response),
                value = check.responseSeconds,
                options = (CHECK_RESPONSE_SECONDS + check.responseSeconds).distinct().sorted(),
                label = { resources.secondsText(it) },
                enabled = check.enabled,
                onSelect = { n -> onEdit { it.copy(wakeUpCheck = it.wakeUpCheck.copy(responseSeconds = n)) } }
            )
        }
    }
}
