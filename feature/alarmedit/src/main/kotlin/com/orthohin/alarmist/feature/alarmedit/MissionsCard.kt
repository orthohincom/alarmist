package com.orthohin.alarmist.feature.alarmedit

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.orthohin.alarmist.core.model.Alarm
import com.orthohin.alarmist.core.model.MissionSpec
import com.orthohin.alarmist.mission.api.Mission
import com.orthohin.alarmist.mission.api.MissionCategory
import com.orthohin.alarmist.mission.api.MissionHost
import com.orthohin.alarmist.mission.api.MissionRegistry
import com.orthohin.alarmist.mission.api.newSpec

/** A mission being edited: [index] null means it's new and is added on save. */
private data class MissionEdit(val index: Int?, val mission: Mission, val spec: MissionSpec)

/**
 * The alarm's missions as a row of [Alarm.MAX_MISSIONS] slots, in the order they run (tap one to edit it, an empty
 * one to add), then the wake-up check. Without installed missions only the wake-up check shows.
 */
@Composable
internal fun MissionsCard(alarm: Alarm, registry: MissionRegistry, onEdit: ((Alarm) -> Alarm) -> Unit) {
    val showMissions = registry.all.isNotEmpty() || alarm.missions.isNotEmpty()
    var choosing by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MissionEdit?>(null) }
    SettingsCard(
        title = stringResource(if (showMissions) R.string.edit_missions_header else R.string.edit_wake_up_header),
        trailing = if (showMissions) {
            stringResource(R.string.edit_missions_count, alarm.missions.size, Alarm.MAX_MISSIONS)
        } else {
            null
        },
        hint = if (showMissions) stringResource(R.string.edit_missions_hint) else null
    ) {
        if (showMissions) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (index in 0 until Alarm.MAX_MISSIONS) {
                    val spec = alarm.missions.getOrNull(index)
                    when {
                        spec != null -> MissionSlot(index, spec, registry[spec.type], onOpen = { mission ->
                            editing = MissionEdit(index, mission, spec)
                        })

                        else -> AddSlot(first = index == alarm.missions.size, enabled = registry.all.isNotEmpty()) {
                            choosing = true
                        }
                    }
                }
            }
            if (alarm.missions.isEmpty()) {
                Text(
                    stringResource(R.string.edit_missions_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
        WakeUpCheckRow(alarm, onEdit)
    }
    if (choosing) {
        MissionPicker(registry, onChoose = { mission ->
            choosing = false
            editing = MissionEdit(null, mission, mission.newSpec())
        }, onDismiss = { choosing = false })
    }
    editing?.let { edit ->
        MissionEditor(
            edit,
            onSave = { spec ->
                editing = null
                onEdit { it.withMission(edit.index, spec) }
            },
            onRemove = edit.index?.let { index ->
                {
                    editing = null
                    onEdit { it.withoutMission(index) }
                }
            },
            onClose = { editing = null }
        )
    }
}

/** A set mission: its icon and name. A mission this version doesn't have can't be edited, only removed. */
@Composable
private fun RowScope.MissionSlot(index: Int, spec: MissionSpec, mission: Mission?, onOpen: (Mission) -> Unit) {
    val resources = LocalResources.current
    val name = mission?.let { stringResource(it.name) } ?: spec.type
    val summary = mission?.summary(spec, resources) ?: stringResource(R.string.edit_mission_missing)
    val colors = categoryColors(mission?.category ?: MissionCategory.Other)
    Column(
        Modifier
            .weight(1f)
            .aspectRatio(SLOT_ASPECT)
            .clip(MaterialTheme.shapes.large)
            .background(colors.first)
            .clickable(enabled = mission != null) { onOpen(mission!!) }
            .semantics(mergeDescendants = true) {
                contentDescription = resources.getString(R.string.edit_mission_slot, index + 1, name, summary)
            }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (mission != null) Icon(painterResource(mission.icon), null, tint = colors.second)
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = colors.second,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/** An empty slot: the first one is the "+" button, the others are faint but also add. */
@Composable
private fun RowScope.AddSlot(first: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .weight(1f)
            .aspectRatio(SLOT_ASPECT)
            .clip(MaterialTheme.shapes.large)
            .background(if (first) colors.surfaceContainerHighest else colors.surfaceContainerHigh)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(R.drawable.ic_add),
            contentDescription = if (first) stringResource(R.string.edit_mission_add) else null,
            tint = if (first) colors.onSurface else colors.onSurfaceVariant.copy(alpha = DISABLED_ALPHA)
        )
    }
}

/** Container and content colours per category, from the theme so Material You applies. */
@Composable
private fun categoryColors(category: MissionCategory): Pair<Color, Color> = with(MaterialTheme.colorScheme) {
    when (category) {
        MissionCategory.Brain -> tertiaryContainer to onTertiaryContainer
        MissionCategory.Body -> secondaryContainer to onSecondaryContainer
        MissionCategory.GetUp -> primaryContainer to onPrimaryContainer
        MissionCategory.Other -> surfaceContainerHighest to onSurfaceVariant
    }
}

private fun MissionCategory.title(): Int = when (this) {
    MissionCategory.Brain -> R.string.edit_category_brain
    MissionCategory.Body -> R.string.edit_category_body
    MissionCategory.GetUp -> R.string.edit_category_get_up
    MissionCategory.Other -> R.string.edit_category_other
}

/** A sheet of missions grouped by category; ones this phone can't run are greyed out with the reason. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MissionPicker(registry: MissionRegistry, onChoose: (Mission) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val groups = remember(registry) {
        registry.all.sortedBy {
            resources.getString(it.name)
        }.groupBy { it.category }.toSortedMap(compareBy { it.ordinal })
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Text(
            stringResource(R.string.edit_mission_choose),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.navigationBarsPadding()) {
            for ((category, missions) in groups) {
                item(key = category) {
                    Text(
                        stringResource(category.title()),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 4.dp)
                    )
                }
                items(missions, key = { it.type }) { mission ->
                    val available = remember(mission) { mission.isAvailable(context) }
                    val colors = categoryColors(category)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .alpha(if (available) 1f else DISABLED_ALPHA)
                            .clickable(enabled = available) { onChoose(mission) }
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(colors.first),
                            contentAlignment = Alignment.Center
                        ) { Icon(painterResource(mission.icon), null, tint = colors.second) }
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(stringResource(mission.name), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(
                                    if (available) mission.description else R.string.edit_mission_unavailable
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Full-screen config editor with "Try it". Asks for the mission's permissions on save (it's added either way). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MissionEditor(
    edit: MissionEdit,
    onSave: (MissionSpec) -> Unit,
    onRemove: (() -> Unit)?,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var spec by remember(edit) { mutableStateOf(edit.spec) }
    var trying by remember { mutableStateOf(false) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onSave(spec)
    }
    val save = {
        val missing = edit.mission.permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) onSave(spec) else permissions.launch(missing.toTypedArray())
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(edit.mission.name)) },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.edit_mission_close))
                        }
                    },
                    actions = {
                        onRemove?.let { remove ->
                            IconButton(onClick = remove) {
                                Icon(
                                    painterResource(R.drawable.ic_delete),
                                    stringResource(R.string.edit_mission_remove, stringResource(edit.mission.name))
                                )
                            }
                        }
                        TextButton(onClick = save) {
                            Text(
                                stringResource(
                                    if (edit.index ==
                                        null
                                    ) {
                                        R.string.edit_mission_add_confirm
                                    } else {
                                        R.string.edit_mission_save
                                    }
                                )
                            )
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                edit.mission.ConfigEditor(spec) { config -> spec = spec.copy(config = config) }
                OutlinedButton(onClick = { trying = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.edit_mission_try))
                }
            }
        }
    }
    if (trying) MissionTrial(edit.mission, spec, onClose = { trying = false })
}

/** Runs the mission as it would on the ringing screen, with nothing ringing. */
@Composable
private fun MissionTrial(mission: Mission, spec: MissionSpec, onClose: () -> Unit) {
    var solved by remember { mutableStateOf(false) }
    val host = remember {
        object : MissionHost {
            override fun onActivity() = Unit

            override fun onComplete() {
                solved = true
            }
        }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.edit_mission_close))
                    }
                    Text(stringResource(mission.name), style = MaterialTheme.typography.titleMedium)
                }
                Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (solved) {
                        Text(
                            stringResource(R.string.edit_mission_solved),
                            style = MaterialTheme.typography.headlineMedium
                        )
                        TextButton(onClick = onClose) { Text(stringResource(R.string.edit_mission_close)) }
                    } else {
                        mission.Runner(spec, host)
                    }
                }
            }
        }
    }
}

private const val SLOT_ASPECT = 0.9f
