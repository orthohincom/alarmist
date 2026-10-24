package com.orthohin.alarmist.feature.ringing

import com.orthohin.alarmist.core.model.Alarm
import com.orthohin.alarmist.core.model.MissionSpec
import com.orthohin.alarmist.core.model.RingSession
import com.orthohin.alarmist.core.model.SnoozeConfig
import com.orthohin.alarmist.core.model.WakeUpCheckConfig
import java.time.Duration
import java.time.Instant

internal sealed interface RingingUiState {
    data object Loading : RingingUiState

    /** Nothing is ringing (snoozed, dismissed, timed out): the screen closes. */
    data object Idle : RingingUiState

    data class Ringing(
        val alarmId: Long,
        val firedAt: Instant,
        /** Blank means the default title. */
        val label: String,
        /** Null when it may not snooze. */
        val snooze: SnoozeOption?,
        /** Solved in order before the alarm stops; empty means a plain Dismiss button. */
        val missions: List<MissionSpec> = emptyList(),
        /** The mission in progress (earlier ones are solved), or null before the first one is opened. */
        val missionIndex: Int? = null,
        /** Anti-cheat: the volume keys do nothing. */
        val lockVolume: Boolean = false
    ) : RingingUiState {
        val canDismiss: Boolean get() = missions.isEmpty()
    }

    /** Dismissed with the wake-up check on: "Stay awake", it asks at [askAt]. Leaving the screen doesn't skip it. */
    data class CheckWaiting(val alarmId: Long, val label: String, val since: Instant, val askAt: Instant) :
        RingingUiState

    /** "Still awake?": confirming before [until] ends the alarm, otherwise it rings again. */
    data class CheckAsking(val alarmId: Long, val label: String, val since: Instant, val until: Instant) :
        RingingUiState
}

/** [left] null means unlimited. */
internal data class SnoozeOption(val minutes: Int, val left: Int?)

/**
 * [session] is what rings or, if nothing does, the wake-up check (`RingSessions.checking`). [alarm] null means it
 * couldn't be read: defaults apply, as in core:ringing.
 */
internal fun ringingUiState(session: RingSession?, alarm: Alarm?): RingingUiState {
    if (session == null) return RingingUiState.Idle
    val label = alarm?.label.orEmpty()
    val check = alarm?.wakeUpCheck ?: WakeUpCheckConfig()
    when (val state = session.state) {
        is RingSession.State.CheckPending -> return RingingUiState.CheckWaiting(
            session.alarmId,
            label,
            since = state.askAt - Duration.ofMinutes(check.delayMinutes.toLong()),
            askAt = state.askAt
        )

        is RingSession.State.Checking -> return RingingUiState.CheckAsking(
            session.alarmId,
            label,
            since = state.until - Duration.ofSeconds(check.responseSeconds.toLong()),
            until = state.until
        )

        is RingSession.State.Snoozed -> return RingingUiState.Idle

        is RingSession.State.Ringing -> Unit
    }
    val config = alarm?.snooze ?: SnoozeConfig()
    return RingingUiState.Ringing(
        alarmId = session.alarmId,
        firedAt = session.firedAt,
        label = label,
        snooze = if (session.canSnooze(config)) {
            SnoozeOption(config.minutes, config.maxCount?.let { it - session.snoozeCount })
        } else {
            null
        },
        missions = alarm?.missions.orEmpty(),
        missionIndex = session.mission?.index,
        lockVolume = alarm?.antiCheat?.lockVolume == true
    )
}
