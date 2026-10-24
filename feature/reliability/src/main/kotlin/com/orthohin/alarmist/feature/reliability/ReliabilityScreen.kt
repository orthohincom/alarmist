package com.orthohin.alarmist.feature.reliability

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orthohin.alarmist.core.designsystem.bestFormatter
import com.orthohin.alarmist.core.designsystem.rememberTimeFormatter
import com.orthohin.alarmist.core.model.Alarm
import com.orthohin.alarmist.core.model.AlarmEvent
import com.orthohin.alarmist.core.model.MissReason
import com.orthohin.alarmist.core.ringing.RingingController
import com.orthohin.alarmist.core.scheduling.RescheduleReason
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The reliability check: settings that can stop alarms (with a button to fix each), what happens when the
 * phone is off, tips for the phone maker and the event log. With [setup] it is the first-run screen: no log,
 * and a Done button that runs [onDone]; otherwise [onDone] is the back button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReliabilityScreen(setup: Boolean, onDone: () -> Unit) {
    val viewModel: ReliabilityViewModel = hiltViewModel()
    val checks by viewModel.checks.collectAsStateWithLifecycle()
    val log by viewModel.log.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Denied twice, Android stops asking: the settings screen is the only way left.
        if (!it) context.open(viewModel.checker.fixIntent(CheckKind.Notifications))
        viewModel.refresh()
    }
    val fix = { kind: CheckKind ->
        if (kind == CheckKind.Notifications && viewModel.checker.canRequestNotificationPermission()) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.open(viewModel.checker.fixIntent(kind))
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (setup) R.string.rel_setup_title else R.string.rel_title)) },
                navigationIcon = {
                    if (!setup) {
                        IconButton(onClick = onDone) {
                            Icon(painterResource(R.drawable.ic_back), stringResource(R.string.rel_back))
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (setup) {
                Button(
                    onClick = {
                        viewModel.finishSetup()
                        onDone()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) { Text(stringResource(R.string.rel_setup_done)) }
            }
        }
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            if (setup) {
                item {
                    Text(
                        stringResource(R.string.rel_setup_intro),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            item { SectionHeader(stringResource(R.string.rel_checks_header)) }
            items(checks, key = { it.kind }) { CheckRow(it, onFix = { fix(it.kind) }) }
            item { PowerOffCard() }
            item { OemCard(onAppInfo = { context.open(viewModel.checker.appInfoIntent()) }) }
            if (!setup) {
                item { SectionHeader(stringResource(R.string.rel_log_header), stringResource(R.string.rel_log_note)) }
                val state = log
                when {
                    state == null -> Unit

                    state.events.isEmpty() -> item {
                        ListItem(headlineContent = { Text(stringResource(R.string.rel_log_empty)) })
                    }

                    else -> items(state.events) { EventRow(it, state.alarms) }
                }
            }
        }
    }
}

/** The alarm list's top-bar entry to the reliability check, with a badge while a required setting is off. */
@Composable
fun ReliabilityAction(onClick: () -> Unit) {
    val viewModel: ReliabilityViewModel = hiltViewModel()
    val problems by viewModel.hasProblems.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    IconButton(onClick = onClick) {
        BadgedBox(badge = { if (problems) Badge() }) {
            Icon(
                painterResource(R.drawable.ic_shield),
                stringResource(if (problems) R.string.rel_open_problems else R.string.rel_open)
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, note: String? = null) {
    Column(Modifier.padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun CheckRow(check: Check, onFix: () -> Unit) {
    val (title, why) = when (check.kind) {
        CheckKind.Notifications -> R.string.rel_check_notifications to R.string.rel_check_notifications_why
        CheckKind.FullScreen -> R.string.rel_check_full_screen to R.string.rel_check_full_screen_why
        CheckKind.ExactAlarms -> R.string.rel_check_exact to R.string.rel_check_exact_why
        CheckKind.Background -> R.string.rel_check_background to R.string.rel_check_background_why
        CheckKind.Battery -> R.string.rel_check_battery to R.string.rel_check_battery_why
        CheckKind.Overlay -> R.string.rel_check_overlay to R.string.rel_check_overlay_why
    }
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(why)) },
        leadingContent = {
            if (check.ok) {
                Icon(
                    painterResource(R.drawable.ic_check),
                    stringResource(R.string.rel_ok),
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    painterResource(R.drawable.ic_warning),
                    null,
                    tint = with(MaterialTheme.colorScheme) { if (check.kind.required) error else onSurfaceVariant }
                )
            }
        },
        trailingContent = if (check.ok) {
            null
        } else {
            {
                FilledTonalButton(onClick = onFix) {
                    Text(stringResource(if (check.kind.required) R.string.rel_allow else R.string.rel_fix))
                }
            }
        }
    )
}

@Composable
private fun PowerOffCard() {
    InfoCard(stringResource(R.string.rel_power_header), stringResource(R.string.rel_power_text))
}

@Composable
private fun OemCard(onAppInfo: () -> Unit) {
    val context = LocalContext.current
    val tip = oemTip(Build.MANUFACTURER)
    val title = if (tip.known) {
        stringResource(R.string.rel_oem_header, Build.MANUFACTURER.replaceFirstChar { it.titlecase() })
    } else {
        stringResource(R.string.rel_oem_header_generic)
    }
    InfoCard(title, stringResource(tip.text)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onAppInfo) { Text(stringResource(R.string.rel_oem_app_info)) }
            val help = Intent(Intent.ACTION_VIEW, Uri.parse(dontKillMyAppUrl(tip.slug)))
            OutlinedButton(onClick = { context.open(help) }) {
                Text(stringResource(R.string.rel_oem_more))
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, text: String, actions: @Composable () -> Unit = {}) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium)
            actions()
        }
    }
}

@Composable
private fun EventRow(event: AlarmEvent, alarms: Map<Long, Alarm>) {
    val resources = LocalResources.current
    val time = rememberTimeFormatter()
    val locale = resources.configuration.locales[0]
    val dayTime = bestFormatter(locale, "EEEMMMd")
    val zone = ZoneId.systemDefault()
    val warn = event.type in WARNINGS ||
        (event.type == AlarmEvent.Type.Fired && event.minutesLate() > 0)
    ListItem(
        headlineContent = {
            Text(
                resources.describe(event, alarms, time, dayTime, zone),
                color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = { Text("${dayTime.format(event.at.atZone(zone))} ${time.format(event.at.atZone(zone))}") }
    )
    HorizontalDivider()
}

private fun Resources.describe(
    event: AlarmEvent,
    alarms: Map<Long, Alarm>,
    time: DateTimeFormatter,
    dayTime: DateTimeFormatter,
    zone: ZoneId
): String {
    val name = alarmName(event.alarmId, alarms, time)
    val due = event.due?.atZone(zone)?.let { "${dayTime.format(it)} ${time.format(it)}" }.orEmpty()
    return when (event.type) {
        AlarmEvent.Type.Scheduled -> getString(R.string.rel_event_scheduled, name, due)

        AlarmEvent.Type.Fired -> event.minutesLate().toInt().let { late ->
            if (late > 0) {
                getQuantityString(R.plurals.rel_event_fired_late, late, name, late)
            } else {
                getString(R.string.rel_event_fired, name)
            }
        }

        AlarmEvent.Type.Snoozed -> getString(R.string.rel_event_snoozed, name)

        AlarmEvent.Type.Dismissed -> getString(R.string.rel_event_dismissed, name)

        AlarmEvent.Type.AutoSilenced -> getString(R.string.rel_event_auto_silenced, name)

        AlarmEvent.Type.Unanswered -> getString(R.string.rel_event_unanswered, name)

        AlarmEvent.Type.Missed -> getString(
            if (event.detail == MissReason.PhoneOff.name) {
                R.string.rel_event_missed_phone_off
            } else {
                R.string.rel_event_missed_app_stopped
            },
            name,
            due
        )

        AlarmEvent.Type.Rescheduled -> getString(
            R.string.rel_event_rescheduled,
            getString(
                when (RescheduleReason.entries.firstOrNull { it.name == event.detail }) {
                    RescheduleReason.Boot -> R.string.rel_reason_boot
                    RescheduleReason.TimeChanged -> R.string.rel_reason_time
                    RescheduleReason.AppUpdated -> R.string.rel_reason_update
                    RescheduleReason.PermissionChanged -> R.string.rel_reason_permission
                    RescheduleReason.AppStart, null -> R.string.rel_reason_start
                }
            )
        )

        AlarmEvent.Type.RingingFallback -> getString(R.string.rel_event_ringing_fallback, name)

        AlarmEvent.Type.SoundFallback -> getString(R.string.rel_event_sound_fallback, name)

        AlarmEvent.Type.Awake -> getString(R.string.rel_event_awake, name)

        AlarmEvent.Type.NotAwake -> getString(R.string.rel_event_not_awake, name)
    }
}

/** The alarm's label, else "7:00 alarm"; previews and deleted alarms say so. */
private fun Resources.alarmName(id: Long?, alarms: Map<Long, Alarm>, time: DateTimeFormatter): String {
    if (id == RingingController.PREVIEW_ID) return getString(R.string.rel_alarm_preview)
    val alarm = id?.let(alarms::get) ?: return getString(R.string.rel_alarm_deleted)
    return alarm.label.ifBlank { getString(R.string.rel_alarm_at, time.format(alarm.time)) }
}

private val WARNINGS = setOf(AlarmEvent.Type.Missed, AlarmEvent.Type.RingingFallback, AlarmEvent.Type.SoundFallback)

private fun Context.open(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w("Alarmist", "No screen for $intent", e)
    }
}
