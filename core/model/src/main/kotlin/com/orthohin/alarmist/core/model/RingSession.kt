package com.orthohin.alarmist.core.model

import java.time.Duration
import java.time.Instant

/**
 * One alarm between going off and being dismissed. [firedAt] is when this occurrence first went off;
 * [snoozeCount] counts the user's snoozes of this occurrence, [unansweredRings] the rings that timed out
 * (see [AutoSilenceConfig]). [mission] is set while the user works through the alarm's missions (only while
 * ringing). After a dismissal with the wake-up check on, the session lives on in [State.CheckPending] and
 * [State.Checking] until the user confirms they're awake.
 */
data class RingSession(
    val alarmId: Long,
    val firedAt: Instant,
    val state: State,
    val snoozeCount: Int = 0,
    val unansweredRings: Int = 0,
    val mission: MissionProgress? = null
) {
    init {
        require(snoozeCount >= 0) { "snoozeCount must not be negative: $snoozeCount" }
        require(unansweredRings >= 0) { "unansweredRings must not be negative: $unansweredRings" }
        require(mission == null || state is State.Ringing) { "A mission needs a ringing session: $state" }
    }

    /** Sound and vibration are on: ringing, and not muted by a mission in progress. */
    fun isAudible(now: Instant): Boolean = state is State.Ringing && (mission == null || mission.mutedUntil <= now)

    sealed interface State {
        /** Ringing since [since]: when it went off, or when its last snooze ended. */
        data class Ringing(val since: Instant) : State

        data class Snoozed(val until: Instant) : State

        /** Dismissed with the wake-up check on: quiet until [askAt], then asks for [respondSeconds]. */
        data class CheckPending(val askAt: Instant, val respondSeconds: Int) : State {
            init {
                require(respondSeconds > 0) { "respondSeconds must be positive: $respondSeconds" }
            }
        }

        /** Asking "still awake?": rings again at [until] unless the user answers. */
        data class Checking(val until: Instant) : State
    }

    /** When this session's timer (the alarm's snooze system alarm) must go off next, or null while ringing. */
    val wakeAt: Instant?
        get() = when (state) {
            is State.Ringing -> null
            is State.Snoozed -> state.until
            is State.CheckPending -> state.askAt
            is State.Checking -> state.until
        }

    /** In a wake-up check (waiting to ask, or asking). */
    val isChecking: Boolean get() = state is State.CheckPending || state is State.Checking

    fun canSnooze(config: SnoozeConfig): Boolean =
        state is State.Ringing && config.enabled && (config.maxCount == null || snoozeCount < config.maxCount)
}

/**
 * The alarm's missions in progress: [index] is the one being solved (earlier ones are done). Muted until
 * [mutedUntil]: opening a mission and every input push it [RingSessions.MISSION_MUTE] ahead, so a user who falls
 * asleep on a mission hears the alarm again.
 */
data class MissionProgress(val index: Int, val mutedUntil: Instant) {
    init {
        require(index >= 0) { "index must not be negative: $index" }
    }
}

/**
 * Every alarm that is ringing or snoozed, at most one session per alarm. Transitions are pure; persisting the
 * result and acting on it (sound, system alarms) is up to the caller.
 *
 * Only one alarm rings at a time: [current]. If a second alarm goes off meanwhile, it waits until the first
 * is snoozed or dismissed, so a ring screen (or mission) in progress is never swapped out under the user.
 */
data class RingSessions(val sessions: List<RingSession> = emptyList()) {
    init {
        require(sessions.distinctBy { it.alarmId }.size == sessions.size) { "Duplicate sessions: $sessions" }
    }

    /** The session that should be heard and shown: the one ringing the longest. */
    val current: RingSession?
        get() = sessions
            .filter { it.state is RingSession.State.Ringing }
            .minWithOrNull(compareBy({ (it.state as RingSession.State.Ringing).since }, { it.alarmId }))

    /**
     * The wake-up check to show when nothing rings: one that is asking now (the soonest deadline), else the next
     * one to ask.
     */
    val checking: RingSession?
        get() = sessions.filter { it.state is RingSession.State.Checking }.minByOrNull { it.wakeAt!! }
            ?: sessions.filter { it.state is RingSession.State.CheckPending }.minByOrNull { it.wakeAt!! }

    operator fun get(alarmId: Long): RingSession? = sessions.firstOrNull { it.alarmId == alarmId }

    /** The alarm went off: a fresh session, replacing any earlier one of the same alarm (snoozed or not). */
    fun fired(alarmId: Long, now: Instant): RingSessions =
        replace(RingSession(alarmId, firedAt = now, state = RingSession.State.Ringing(now)))

    /** Snoozes a ringing alarm until [config]`.minutes` from [now], or returns null if it may not snooze. */
    fun snooze(alarmId: Long, now: Instant, config: SnoozeConfig): RingSessions? {
        val session = get(alarmId)?.takeIf { it.canSnooze(config) } ?: return null
        val until = now + Duration.ofMinutes(config.minutes.toLong())
        // Snoozing drops mission progress: after the snooze, the missions start again.
        return replace(
            session.copy(
                state = RingSession.State.Snoozed(until),
                snoozeCount = session.snoozeCount + 1,
                mission = null
            )
        )
    }

    /**
     * Nobody answered for `ringMinutes`: go quiet for `snoozeMinutes`, or end the session if this was the last
     * allowed ring (the caller reports it as missed). No change unless it is ringing and auto-silence is on.
     */
    fun timedOut(alarmId: Long, now: Instant, config: AutoSilenceConfig): RingSessions {
        val session = get(alarmId)?.takeIf { it.state is RingSession.State.Ringing && config.enabled } ?: return this
        val unanswered = session.unansweredRings + 1
        if (unanswered >= config.maxRings) return dismiss(alarmId)
        val until = now + Duration.ofMinutes(config.snoozeMinutes.toLong())
        return replace(
            session.copy(state = RingSession.State.Snoozed(until), unansweredRings = unanswered, mission = null)
        )
    }

    /** The snooze is over: ring again. No change unless the alarm is snoozed. */
    fun snoozeEnded(alarmId: Long, now: Instant): RingSessions {
        val session = get(alarmId)?.takeIf { it.state is RingSession.State.Snoozed } ?: return this
        return replace(session.copy(state = RingSession.State.Ringing(now)))
    }

    /** Ends the session for good (no wake-up check): auto-silence gave up, a preview was replaced. */
    fun dismiss(alarmId: Long): RingSessions = RingSessions(sessions.filterNot { it.alarmId == alarmId })

    /**
     * The user dismissed the alarm (button, notification, last mission). With [check] on, a ringing or snoozed
     * session waits for the wake-up check; otherwise, or if it was already in a check, it ends.
     */
    fun dismissed(alarmId: Long, now: Instant, check: WakeUpCheckConfig): RingSessions {
        val session = get(alarmId) ?: return this
        if (!check.enabled || session.isChecking) return dismiss(alarmId)
        val askAt = now + Duration.ofMinutes(check.delayMinutes.toLong())
        return replace(
            session.copy(state = RingSession.State.CheckPending(askAt, check.responseSeconds), mission = null)
        )
    }

    /**
     * The wake-up check's timer went off. At `askAt` it starts asking (the full response time from [now], so a
     * late timer or a reboot doesn't shorten it); at the end of the response time it rings again, with missions
     * from the start and a fresh auto-silence count. No change otherwise.
     */
    fun checkTimer(alarmId: Long, now: Instant): RingSessions {
        val session = get(alarmId) ?: return this
        val state = session.state
        return when {
            state is RingSession.State.CheckPending && now >= state.askAt -> replace(
                session.copy(
                    state = RingSession.State.Checking(now + Duration.ofSeconds(state.respondSeconds.toLong()))
                )
            )

            state is RingSession.State.Checking && now >= state.until ->
                replace(session.copy(state = RingSession.State.Ringing(now), unansweredRings = 0))

            else -> this
        }
    }

    /** The user answered the wake-up check: the session ends. No change unless it is in a check. */
    fun awake(alarmId: Long): RingSessions = if (get(alarmId)?.isChecking == true) dismiss(alarmId) else this

    /** The mission screen opened: mute, keeping any progress. No change unless it is ringing. */
    fun missionsOpened(alarmId: Long, now: Instant): RingSessions {
        val session = get(alarmId)?.takeIf { it.state is RingSession.State.Ringing } ?: return this
        val mutedUntil = now + MISSION_MUTE
        return replace(
            session.copy(
                mission =
                    session.mission?.copy(mutedUntil = mutedUntil) ?: MissionProgress(0, mutedUntil)
            )
        )
    }

    /**
     * The user did something in the mission: stay muted for [MISSION_MUTE] from [now]. Returns the same object
     * unless that moves the mute at least [MISSION_MUTE_STEP] later, so callers can skip saving.
     */
    fun missionActivity(alarmId: Long, now: Instant): RingSessions {
        val session = get(alarmId) ?: return this
        val mission = session.mission ?: return this
        val mutedUntil = now + MISSION_MUTE
        if (Duration.between(mission.mutedUntil, mutedUntil) < MISSION_MUTE_STEP) return this
        return replace(session.copy(mission = mission.copy(mutedUntil = mutedUntil)))
    }

    /**
     * Mission [index] is solved. The next one starts muted; after the last of [total] it is [dismissed].
     * No change if [index] isn't the mission in progress (a late or repeated report).
     */
    fun missionCompleted(
        alarmId: Long,
        index: Int,
        total: Int,
        now: Instant,
        check: WakeUpCheckConfig = WakeUpCheckConfig()
    ): RingSessions {
        val session = get(alarmId) ?: return this
        if (session.mission?.index != index) return this
        if (index + 1 >= total) return dismissed(alarmId, now, check)
        return replace(session.copy(mission = MissionProgress(index + 1, now + MISSION_MUTE)))
    }

    /** The user left the mission screen: it rings again at once, progress is kept. */
    fun missionsLeft(alarmId: Long, now: Instant): RingSessions {
        val session = get(alarmId) ?: return this
        val mission = session.mission?.takeIf { it.mutedUntil > now } ?: return this
        return replace(session.copy(mission = mission.copy(mutedUntil = now)))
    }

    private fun replace(session: RingSession): RingSessions =
        RingSessions(sessions.filterNot { it.alarmId == session.alarmId } + session)

    companion object {
        val MISSION_MUTE: Duration = Duration.ofSeconds(30)

        /** Mute extensions smaller than this are skipped, so every tap doesn't cost a disk write. */
        val MISSION_MUTE_STEP: Duration = Duration.ofSeconds(5)
    }
}
