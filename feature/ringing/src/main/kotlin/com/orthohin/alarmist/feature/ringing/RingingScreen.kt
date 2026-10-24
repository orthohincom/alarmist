package com.orthohin.alarmist.feature.ringing

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import com.orthohin.alarmist.core.model.MissionSpec
import com.orthohin.alarmist.mission.api.Mission
import com.orthohin.alarmist.mission.api.MissionHost
import java.time.Duration
import java.time.Instant
import java.util.Date
import kotlinx.coroutines.delay

/** What the screen can do; the activity wires it to [RingingViewModel]. */
internal interface RingingActions {
    fun snooze(alarmId: Long)

    fun dismiss(alarmId: Long)

    fun openMissions(alarmId: Long)

    fun missionActivity(alarmId: Long)

    fun missionCompleted(alarmId: Long, index: Int)

    fun leaveMissions(alarmId: Long)

    /** Null if no module provides [type]. */
    fun mission(type: String): Mission?

    /** Answers the wake-up check. */
    fun awake(alarmId: Long)

    fun now(): Instant
}

/** [onClose] leaves the screen (only offered while waiting for the wake-up check). */
@Composable
internal fun RingingScreen(state: RingingUiState, actions: RingingActions, onClose: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        when (state) {
            is RingingUiState.Ringing -> RingingOrMissions(state, actions)
            is RingingUiState.CheckWaiting -> StayAwake(state, actions, onClose)
            is RingingUiState.CheckAsking -> AwakeCheck(state, actions)
            RingingUiState.Idle, RingingUiState.Loading -> Unit
        }
    }
}

/** The time, every quarter second, for countdowns. */
@Composable
private fun rememberNow(actions: RingingActions): Instant {
    val now by produceState(actions.now(), actions) {
        while (true) {
            delay(TICK_MILLIS)
            value = actions.now()
        }
    }
    return now
}

/** After dismissing: a countdown to the wake-up check. No skip; closing the screen doesn't cancel it. */
@Composable
private fun StayAwake(state: RingingUiState.CheckWaiting, actions: RingingActions, onClose: () -> Unit) {
    val now = rememberNow(actions)
    val total = Duration.between(state.since, state.askAt).toMillis().coerceAtLeast(1)
    val left = Duration.between(now, state.askAt).toMillis().coerceIn(0, total)
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            state.label.ifBlank { stringResource(R.string.ringing_check_title) },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            stringResource(R.string.ringing_stay_awake),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        LinearProgressIndicator(
            progress = { 1f - left.toFloat() / total },
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth().height(8.dp)
        )
        Text(
            stringResource(R.string.ringing_check_in, DateUtils.formatElapsedTime((left + 999) / 1000)),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            stringResource(R.string.ringing_check_explain),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onClose, Modifier.fillMaxWidth().height(56.dp)) {
            Text(stringResource(R.string.ringing_close), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** "Still awake?": a card with the seconds left and one big button. */
@Composable
private fun AwakeCheck(state: RingingUiState.CheckAsking, actions: RingingActions) {
    val now = rememberNow(actions)
    val seconds = ((Duration.between(now, state.until).toMillis() + 999) / 1000).coerceAtLeast(0).toInt()
    val secondsLeft = pluralStringResource(R.plurals.ringing_check_seconds_left, seconds, seconds)
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    state.label.ifBlank { stringResource(R.string.ringing_check_title) },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    seconds.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { contentDescription = secondsLeft }
                )
                Text(
                    stringResource(R.string.ringing_check_question),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Button(onClick = { actions.awake(state.alarmId) }, Modifier.fillMaxWidth().height(64.dp)) {
                    Text(stringResource(R.string.ringing_awake), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
private fun RingingOrMissions(state: RingingUiState.Ringing, actions: RingingActions) {
    // Per ring, so a second alarm taking over starts on its own ringing screen.
    var solving by rememberSaveable(state.alarmId, state.firedAt) { mutableStateOf(false) }
    // Opening (and coming back to) the missions mutes; leaving the screen in any way rings again at once.
    LifecycleStartEffect(solving, state.alarmId) {
        // The value this effect started with: `solving` itself is already false when Back disposes it.
        val open = solving
        if (open) actions.openMissions(state.alarmId)
        onStopOrDispose { if (open) actions.leaveMissions(state.alarmId) }
    }
    if (solving && state.missions.isNotEmpty()) {
        Missions(state, actions, onBack = { solving = false })
    } else {
        Ringing(state, onSnooze = actions::snooze, onDismiss = {
            if (state.canDismiss) actions.dismiss(state.alarmId) else solving = true
        })
    }
}

@Composable
private fun Ringing(state: RingingUiState.Ringing, onSnooze: (Long) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val time = remember(state.firedAt) { DateFormat.getTimeFormat(context).format(Date(state.firedAt.toEpochMilli())) }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.weight(1f))
        Text(time, style = MaterialTheme.typography.displayLarge)
        Text(
            state.label.ifBlank { stringResource(R.string.ringing_default_label) },
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.weight(1f))
        state.snooze?.let { snooze ->
            OutlinedButton(onClick = { onSnooze(state.alarmId) }, Modifier.fillMaxWidth().height(64.dp)) {
                Text(
                    stringResource(R.string.ringing_snooze, snooze.minutes),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            snooze.left?.let { left ->
                Text(
                    pluralStringResource(R.plurals.ringing_snoozes_left, left, left),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Button(onClick = onDismiss, Modifier.fillMaxWidth().height(72.dp)) {
            Text(
                stringResource(if (state.canDismiss) R.string.ringing_dismiss else R.string.ringing_start_missions),
                style = MaterialTheme.typography.titleLarge
            )
        }
        if (!state.canDismiss) {
            val left = state.missions.size - (state.missionIndex ?: 0)
            Text(
                pluralStringResource(R.plurals.ringing_missions_count, left, left),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun Missions(state: RingingUiState.Ringing, actions: RingingActions, onBack: () -> Unit) {
    val index = (state.missionIndex ?: 0).coerceIn(0, state.missions.lastIndex)
    val spec = state.missions[index]
    val context = LocalContext.current
    val mission = actions.mission(spec.type)?.takeIf { it.isReady(context) }
    val host = remember(state.alarmId, index) {
        object : MissionHost {
            override fun onActivity() = actions.missionActivity(state.alarmId)

            override fun onComplete() = actions.missionCompleted(state.alarmId, index)
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            // Any touch is activity, so missions only report other input (shake, steps).
            .pointerInput(host) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        host.onActivity()
                    }
                }
            }
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.ringing_back_to_alarm)) }
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.ringing_mission_progress, index + 1, state.missions.size),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(end = 16.dp)
            )
        }
        LinearProgressIndicator(
            progress = { index.toFloat() / state.missions.size },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
            // Keyed by position: each mission starts with fresh state.
            key(index) {
                if (mission != null) mission.Runner(spec, host) else UnavailableMission(spec, onSkip = host::onComplete)
            }
        }
    }
}

/** Never trap the user: a mission this phone or app version can't run can be skipped. */
@Composable
private fun UnavailableMission(spec: MissionSpec, onSkip: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text(stringResource(R.string.ringing_mission_unavailable), textAlign = TextAlign.Center)
        Text(spec.type, style = MaterialTheme.typography.bodySmall)
        Button(onClick = onSkip) { Text(stringResource(R.string.ringing_mission_skip)) }
    }
}

@Preview
@Composable
private fun RingingScreenPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        RingingScreen(
            RingingUiState.Ringing(1, Instant.parse("2026-10-16T07:00:00Z"), "Gym", SnoozeOption(5, 2)),
            object : RingingActions {
                override fun snooze(alarmId: Long) = Unit

                override fun dismiss(alarmId: Long) = Unit

                override fun openMissions(alarmId: Long) = Unit

                override fun missionActivity(alarmId: Long) = Unit

                override fun missionCompleted(alarmId: Long, index: Int) = Unit

                override fun leaveMissions(alarmId: Long) = Unit

                override fun mission(type: String): Mission? = null

                override fun awake(alarmId: Long) = Unit

                override fun now(): Instant = Instant.parse("2026-10-16T07:00:00Z")
            },
            onClose = {}
        )
    }
}

private const val TICK_MILLIS = 250L
