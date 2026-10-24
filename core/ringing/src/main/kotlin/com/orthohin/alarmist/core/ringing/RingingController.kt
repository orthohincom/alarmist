package com.orthohin.alarmist.core.ringing

import com.orthohin.alarmist.core.model.Alarm
import com.orthohin.alarmist.core.model.RingSessions
import java.time.Instant
import kotlinx.coroutines.flow.StateFlow

/** What the ringing screen (feature:ringing) drives. Alarms start ringing through `AlarmRinger`, not here. */
interface RingingController {
    /** Every alarm that is ringing or snoozed. `current` is the one heard and shown. */
    val sessions: StateFlow<RingSessions>

    /**
     * Snoozes per the alarm's `SnoozeConfig` (defaults if the database can't be read). Returns false if it
     * may not snooze: snooze is off, the limit is reached, or it isn't ringing.
     */
    suspend fun snooze(alarmId: Long): Boolean

    /**
     * Stops the alarm, including a pending snooze. Callers enforce missions first. With the alarm's wake-up
     * check on, it asks "still awake?" later and rings again if nobody answers ([awake]).
     */
    suspend fun dismiss(alarmId: Long)

    /**
     * The ringing screen became visible or went away (not for a rotation). With the alarm's
     * `AntiCheatConfig.relaunchIfLeft`, the ringing service brings it back while the screen is on.
     */
    fun screenVisible(visible: Boolean)

    /** The user answered the wake-up check: the alarm is done. No effect outside a check. */
    suspend fun awake(alarmId: Long)

    /**
     * The mission screen opened for a ringing alarm: sound and vibration stop for 30 s (`RingSessions.MISSION_MUTE`).
     * Progress made earlier in this ring is kept (`RingSession.mission`).
     */
    suspend fun openMissions(alarmId: Long)

    /** The user did something in the mission (tap, digit, shake, step): stays muted. Cheap enough for every input. */
    suspend fun missionActivity(alarmId: Long)

    /** Mission [index] is solved: the next one starts, or after the alarm's last mission it is dismissed. */
    suspend fun missionCompleted(alarmId: Long, index: Int)

    /** The mission screen went away (back, home, screen off): it rings again at once. */
    suspend fun leaveMissions(alarmId: Long)

    /**
     * Rings [alarm]'s settings at [at] through the real path (system alarm, receiver, service, full screen)
     * without saving anything. It rings as [PREVIEW_ID] and replaces an earlier preview. The settings are kept
     * in memory only: if the process dies before it rings, it rings with defaults.
     */
    suspend fun preview(alarm: Alarm, at: Instant)

    /** The settings [alarmId] rings with (the preview's for [PREVIEW_ID]), or null if unknown or unreadable. */
    suspend fun alarm(alarmId: Long): Alarm?

    companion object {
        /** The id a preview rings as. Saved alarms never get it (Room ids count up from 1). */
        const val PREVIEW_ID = Long.MAX_VALUE

        /**
         * The ringing notification's full-screen intent: an activity in this app that handles this action
         * (feature:ringing). Until one exists, it opens the launcher activity.
         */
        const val ACTION_SHOW_RINGING = "com.orthohin.alarmist.action.SHOW_RINGING"

        /** Opens the reliability check (handled by the app's main activity); the missed-ring notification uses it. */
        const val ACTION_SHOW_RELIABILITY = "com.orthohin.alarmist.action.SHOW_RELIABILITY"
    }
}
