package com.orthohin.alarmist.feature.alarmedit

import android.content.res.Resources
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orthohin.alarmist.core.model.AlarmSound
import com.orthohin.alarmist.core.model.SoundConfig
import com.orthohin.alarmist.core.sounds.BundledSounds
import com.orthohin.alarmist.core.sounds.DeviceRingtone
import com.orthohin.alarmist.core.sounds.ImportResult
import com.orthohin.alarmist.core.sounds.ImportedSound
import com.orthohin.alarmist.core.sounds.ImportedSounds
import com.orthohin.alarmist.core.sounds.SoundCategory
import kotlinx.coroutines.launch

/**
 * Full-screen sound list: bundled categories, "My sounds" (import, delete) and device sounds. Chips jump to a
 * section; tapping a row selects it and plays it, tapping the playing selection stops it.
 */
@Composable
internal fun SoundPicker(config: SoundConfig, onSelect: (AlarmSound) -> Unit, onClose: () -> Unit) {
    val viewModel: SoundPickerViewModel = hiltViewModel()
    // Outside the Dialog, so these follow the screen's lifecycle (the file picker opening stops it too).
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.stopPreview() }
    DisposableEffect(viewModel) {
        viewModel.refresh()
        onDispose { viewModel.stopPreview() }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SoundPickerContent(viewModel, config, onSelect, onClose)
    }
}

private sealed interface PickerRow {
    val key: String

    data class Header(val title: String, val note: String? = null) : PickerRow {
        override val key = "header:$title"
    }

    data class Sound(val sound: AlarmSound, val title: String, val imported: ImportedSound? = null) : PickerRow {
        override val key = sound.toString()
    }

    data object Import : PickerRow {
        override val key = "import"
    }

    data class Message(val text: String) : PickerRow {
        override val key = "message:$text"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoundPickerContent(
    viewModel: SoundPickerViewModel,
    config: SoundConfig,
    onSelect: (AlarmSound) -> Unit,
    onClose: () -> Unit
) {
    val resources = LocalResources.current
    val imported by viewModel.importedSounds.collectAsStateWithLifecycle()
    val device by viewModel.deviceRingtones.collectAsStateWithLifecycle()
    val playing by viewModel.playing.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf<ImportedSound?>(null) }

    val backupTitle = BundledSounds.find(config.backup.id)?.let { resources.getString(it.name) }.orEmpty()
    val rows =
        remember(imported, device, backupTitle, resources) { pickerRows(resources, imported, device, backupTitle) }
    val sections = remember(rows) { rows.indices.filter { rows[it] is PickerRow.Header } }
    val listState = rememberLazyListState()
    val currentSection by remember(sections) {
        derivedStateOf { sections.lastOrNull { it <= listState.firstVisibleItemIndex } ?: sections.first() }
    }

    fun tap(sound: AlarmSound) {
        if (sound == config.sound && playing == sound) {
            viewModel.stopPreview()
        } else {
            onSelect(sound)
            viewModel.play(sound)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.import(uri) { tap(it) }
    }
    LaunchedEffect(viewModel) {
        viewModel.importFailures.collect { failure ->
            val message = when (failure) {
                ImportResult.TooLarge -> resources.getString(
                    R.string.picker_import_too_large,
                    (ImportedSounds.MAX_BYTES / (1024 * 1024)).toInt()
                )

                ImportResult.NotAudio -> resources.getString(R.string.picker_import_not_audio)

                else -> resources.getString(R.string.picker_import_failed)
            }
            snackbar.showSnackbar(message)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.picker_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.picker_close))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(sections, key = { _, index -> index }) { _, index ->
                    FilterChip(
                        selected = index == currentSection,
                        onClick = { scope.launch { listState.animateScrollToItem(index) } },
                        label = { Text((rows[index] as PickerRow.Header).title) }
                    )
                }
            }
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(rows, key = { _, row -> row.key }) { _, row ->
                    when (row) {
                        is PickerRow.Header -> PickerHeader(row)

                        is PickerRow.Sound -> SoundRow(
                            row = row,
                            selected = row.sound == config.sound,
                            playing = row.sound == playing,
                            onClick = { tap(row.sound) },
                            onDelete = { deleting = row.imported }
                        )

                        PickerRow.Import -> ListItem(
                            headlineContent = { Text(stringResource(R.string.picker_import)) },
                            leadingContent = { Icon(painterResource(R.drawable.ic_add), null) },
                            modifier = Modifier.clickable {
                                viewModel.stopPreview()
                                importLauncher.launch(arrayOf("audio/*"))
                            }
                        )

                        is PickerRow.Message -> ListItem(headlineContent = { Text(row.text) })
                    }
                }
            }
        }
    }

    deleting?.let { sound ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text(stringResource(R.string.picker_delete_confirm, sound.title)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    if (config.sound == sound.sound) onSelect(config.backup)
                    viewModel.delete(sound)
                }) { Text(stringResource(R.string.picker_delete_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.edit_cancel)) }
            }
        )
    }
}

private fun pickerRows(
    resources: Resources,
    imported: List<ImportedSound>,
    device: List<DeviceRingtone>?,
    backupTitle: String
): List<PickerRow> = buildList {
    val bundled = BundledSounds.all.groupBy { it.category }
    for (category in SoundCategory.entries) {
        add(PickerRow.Header(resources.getString(category.title)))
        bundled[category].orEmpty().forEach { add(PickerRow.Sound(it.sound, resources.getString(it.name))) }
    }
    add(
        PickerRow.Header(
            resources.getString(R.string.picker_my_sounds),
            resources.getString(R.string.picker_my_sounds_note)
        )
    )
    imported.forEach { add(PickerRow.Sound(it.sound, it.title, imported = it)) }
    add(PickerRow.Import)
    add(
        PickerRow.Header(
            resources.getString(R.string.picker_device),
            resources.getString(R.string.picker_device_note, backupTitle)
        )
    )
    when {
        device == null -> add(PickerRow.Message(resources.getString(R.string.picker_loading)))
        device.isEmpty() -> add(PickerRow.Message(resources.getString(R.string.picker_no_device)))
        else -> device.forEach { add(PickerRow.Sound(it.sound, it.title)) }
    }
}

@Composable
private fun PickerHeader(header: PickerRow.Header) {
    Column(Modifier.padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 4.dp)) {
        Text(header.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        header.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun SoundRow(
    row: PickerRow.Sound,
    selected: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    ListItem(
        headlineContent = { Text(row.title) },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        trailingContent = {
            Row {
                if (playing) {
                    Icon(
                        painterResource(R.drawable.ic_volume),
                        stringResource(R.string.picker_playing),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                if (row.imported != null) {
                    IconButton(onClick = onDelete) {
                        Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.picker_delete, row.title))
                    }
                }
            }
        },
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    )
}
