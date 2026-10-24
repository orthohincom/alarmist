package com.orthohin.alarmist.feature.settings

import android.content.ActivityNotFoundException
import android.content.res.Resources
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orthohin.alarmist.core.data.ThemeMode
import com.orthohin.alarmist.core.data.TimeInput
import com.orthohin.alarmist.core.designsystem.supportsDynamicColor

/** The settings icon for the alarm list's top bar. */
@Composable
fun SettingsAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.set_open)) }
}

/** App settings: theme, Material You colours, backup and restore. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.results.collect { result ->
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(resources.resultText(result))
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME_TYPE)) { uri ->
        uri?.let(viewModel::export)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::restore)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.set_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_back), stringResource(R.string.set_back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val current = settings ?: return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SectionHeader(stringResource(R.string.set_appearance))
            ListItem(
                headlineContent = { Text(stringResource(R.string.set_theme)) },
                supportingContent = {
                    SingleChoiceSegmentedButtonRow(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = current.theme == mode,
                                onClick = { viewModel.setTheme(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size)
                            ) { Text(stringResource(mode.label)) }
                        }
                    }
                }
            )
            if (supportsDynamicColor) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.set_dynamic_color)) },
                    supportingContent = { Text(stringResource(R.string.set_dynamic_color_detail)) },
                    trailingContent = { Switch(checked = current.dynamicColor, onCheckedChange = null) },
                    modifier = Modifier.clickable { viewModel.setDynamicColor(!current.dynamicColor) }
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.set_time_input)) },
                supportingContent = {
                    SingleChoiceSegmentedButtonRow(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        TimeInput.entries.forEachIndexed { index, input ->
                            SegmentedButton(
                                selected = current.timeInput == input,
                                onClick = { viewModel.setTimeInput(input) },
                                shape = SegmentedButtonDefaults.itemShape(index, TimeInput.entries.size)
                            ) { Text(stringResource(input.label)) }
                        }
                    }
                }
            )

            SectionHeader(stringResource(R.string.set_backup_header))
            ListItem(
                headlineContent = { Text(stringResource(R.string.set_backup)) },
                supportingContent = { Text(stringResource(R.string.set_backup_detail)) },
                modifier = Modifier.clickable(enabled = !busy) {
                    try {
                        exportLauncher.launch(viewModel.backupFileName())
                    } catch (_: ActivityNotFoundException) {
                        viewModel.noFileApp()
                    }
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.set_restore)) },
                supportingContent = { Text(stringResource(R.string.set_restore_detail)) },
                modifier = Modifier.clickable(enabled = !busy) {
                    try {
                        // Some file managers call a .json file octet-stream or plain text.
                        restoreLauncher.launch(arrayOf(MIME_TYPE, "application/octet-stream", "text/plain"))
                    } catch (_: ActivityNotFoundException) {
                        viewModel.noFileApp()
                    }
                }
            )
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 4.dp)
    )
}

private val ThemeMode.label: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.string.set_theme_system
        ThemeMode.LIGHT -> R.string.set_theme_light
        ThemeMode.DARK -> R.string.set_theme_dark
    }

private val TimeInput.label: Int
    get() = when (this) {
        TimeInput.WHEELS -> R.string.set_time_input_wheels
        TimeInput.DIAL -> R.string.set_time_input_dial
    }

private fun Resources.resultText(result: BackupResult): String = when (result) {
    is BackupResult.Exported -> getQuantityString(R.plurals.set_exported, result.alarms, result.alarms)

    is BackupResult.Restored -> listOfNotNull(
        getQuantityString(R.plurals.set_restored, result.added, result.added),
        result.skipped.takeIf { it > 0 }?.let { getQuantityString(R.plurals.set_restored_skipped, it, it) },
        result.failed.takeIf { it > 0 }?.let { getQuantityString(R.plurals.set_restored_failed, it, it) }
    ).reduce { text, part -> getString(R.string.set_restored_join, text, part) }

    is BackupResult.Failed -> getString(
        when (result.reason) {
            BackupResult.Reason.WRITE -> R.string.set_failed_write
            BackupResult.Reason.READ -> R.string.set_failed_read
            BackupResult.Reason.TOO_LARGE -> R.string.set_failed_too_large
            BackupResult.Reason.NOT_A_BACKUP -> R.string.set_failed_not_backup
            BackupResult.Reason.NEWER_VERSION -> R.string.set_failed_newer
            BackupResult.Reason.INVALID -> R.string.set_failed_invalid
            BackupResult.Reason.NO_FILE_APP -> R.string.set_failed_no_file_app
            BackupResult.Reason.OTHER -> R.string.set_failed_other
        }
    )
}

private const val MIME_TYPE = "application/json"
