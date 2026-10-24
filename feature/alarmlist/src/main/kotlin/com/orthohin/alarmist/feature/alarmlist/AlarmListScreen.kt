package com.orthohin.alarmist.feature.alarmlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orthohin.alarmist.core.designsystem.Countdown
import com.orthohin.alarmist.core.designsystem.bestFormatter
import com.orthohin.alarmist.core.designsystem.countdownText
import com.orthohin.alarmist.core.designsystem.rememberTimeFormatter
import com.orthohin.alarmist.core.designsystem.ringsInText
import com.orthohin.alarmist.core.designsystem.weekOrder
import java.time.format.TextStyle
import kotlinx.coroutines.launch

/**
 * The home screen: every alarm with an on/off switch. [onEdit] gets the alarm's id; [header] is shown above
 * the list (the app puts its debug tools there); [actions] go in the top bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmListScreen(
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    header: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    val viewModel: AlarmListViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            // A new event replaces the snackbar on screen.
            snackbar.currentSnackbarData?.dismiss()
            launch {
                when (event) {
                    is AlarmListEvent.TurnedOff -> {
                        val result = snackbar.showSnackbar(
                            message = resources.getString(R.string.list_turned_off),
                            actionLabel = resources.getString(R.string.list_skip_instead),
                            duration = SnackbarDuration.Long
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.skipOnce(event.alarmId)
                    }

                    is AlarmListEvent.TurnedOn -> event.nextRing?.let {
                        snackbar.showSnackbar(resources.ringsInText(event.now, it))
                    }

                    is AlarmListEvent.Skipped -> snackbar.showSnackbar(
                        resources.getString(R.string.list_skipped, bestFormatter(locale, "EEEEMMMd").format(event.date))
                    )
                }
            }
        }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.list_title)) }, actions = actions) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(painterResource(R.drawable.ic_add), stringResource(R.string.list_add))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val current = state ?: return@Scaffold
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp)
        ) {
            item { header() }
            item { OverlayHint(viewModel) }
            item {
                Text(
                    text = current.nextRing?.let {
                        stringResource(R.string.list_next, resources.countdownText(Countdown.between(current.now, it)))
                    } ?: stringResource(R.string.list_none_on),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
            if (current.alarms.isEmpty()) {
                item { Text(stringResource(R.string.list_empty), modifier = Modifier.padding(vertical = 24.dp)) }
            }
            items(current.alarms, key = { it.id }) { item ->
                AlarmRow(
                    item = item,
                    onClick = { onEdit(item.id) },
                    onToggle = { viewModel.setEnabled(item.id, it) },
                    onUndoSkip = { viewModel.undoSkip(item.id) }
                )
            }
        }
    }
}

@Composable
private fun AlarmRow(item: AlarmItem, onClick: () -> Unit, onToggle: (Boolean) -> Unit, onUndoSkip: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val timeFormatter = rememberTimeFormatter()
    val days = orderedDays(item.days, weekOrder(locale))
    val daysText = when {
        item.days.isEmpty() -> stringResource(R.string.list_once)
        days == null -> stringResource(R.string.list_every_day)
        else -> days.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
    }
    val colors = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (item.enabled) colors.secondaryContainer else colors.surfaceContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (item.label.isNotBlank()) Text(item.label, style = MaterialTheme.typography.labelLarge)
                Text(
                    timeFormatter.format(item.time),
                    style = MaterialTheme.typography.displaySmall,
                    color = if (item.enabled) colors.onSecondaryContainer else colors.onSurfaceVariant
                )
                Text(daysText, style = MaterialTheme.typography.bodyMedium)
                item.skipping?.let { date ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.list_skipping, bestFormatter(locale, "EEEMMMd").format(date)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.tertiary
                        )
                        TextButton(onClick = onUndoSkip) { Text(stringResource(R.string.list_undo)) }
                    }
                }
            }
            Switch(checked = item.enabled, onCheckedChange = onToggle)
        }
    }
}
