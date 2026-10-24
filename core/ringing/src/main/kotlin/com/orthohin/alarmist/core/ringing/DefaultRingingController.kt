package com.orthohin.alarmist.core.ringing

import android.util.Log
import com.orthohin.alarmist.core.common.AppDispatchers
import com.orthohin.alarmist.core.common.Clock
import com.orthohin.alarmist.core.data.AlarmRepository
import com.orthohin.alarmist.core.data.EventLog
import com.orthohin.alarmist.core.model.Alarm
import com.orthohin.alarmist.core.model.AlarmEvent
import com.orthohin.alarmist.core.model.AutoSilenceConfig
import com.orthohin.alarmist.core.model.MissReason
import com.orthohin.alarmist.core.model.RingSession
import com.orthohin.alarmist.core.model.RingSessions
import com.orthohin.alarmist.core.model.SnoozeConfig
import com.orthohin.alarmist.core.model.WakeUpCheckConfig
import com.orthohin.alarmist.core.scheduling.AlarmRinger
import com.orthohin.alarmist.core.scheduling.SnoozeAlarms
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Owns the ring sessions: applies the pure [RingSessions] transitions under one mutex, persists every change
 * (so a reboot or process death resumes ringing) and acts on it. [sessions] lives in memory, so ringing
 * never waits on the disk and still works if saving fails.
 */
@Singleton
internal class DefaultRingingController @Inject constructor(
    private val store: RingSessionStore,
    private val repository: AlarmRepository,
    private val snoozeAlarms: SnoozeAlarms,
    private val outputs: RingingOutputs,
    private val clock: Clock,
    private val dispatchers: AppDispatchers,
    private val events: EventLog
) : RingingController,
    AlarmRinger {
    private val mutex = Mutex()
    private val state = MutableStateFlow(load())
    override val sessions: StateFlow<RingSessions> = state.asStateFlow()

    @Volatile private var previewAlarm: Alarm? = null

    /** Whether the ringing screen is in front (memory only: after process death it isn't). */
    @Volatile var isScreenVisible = false
        private set

    override fun screenVisible(visible: Boolean) {
        isScreenVisible = visible
    }

    override suspend fun ring(alarmId: Long) {
        val session = update { it.fired(alarmId, clock.now()) }[alarmId]!!
        outputs.clear(alarmId)
        outputs.startRinging(session)
        // A fresh ring replaces a pending snooze of the same alarm.
        snoozeAlarms.cancel(alarmId)
    }

    override suspend fun snoozeEnded(alarmId: Long) {
        // The same system alarm times the wake-up check.
        if (state.value[alarmId]?.isChecking == true) return checkTimer(alarmId)
        val now = clock.now()
        // A snooze only exists for an alarm that rang, so if the session is lost it rings anyway.
        val next = update { if (it[alarmId] == null) it.fired(alarmId, now) else it.snoozeEnded(alarmId, now) }
        outputs.clear(alarmId)
        next[alarmId]?.takeIf { it.state is RingSession.State.Ringing }?.let(outputs::startRinging)
    }

    /** The wake-up check's timer: start asking, or ring again if nobody answered. */
    private suspend fun checkTimer(alarmId: Long) {
        val session = mutex.withLock {
            val next = state.value.checkTimer(alarmId, clock.now())
            val session = next[alarmId] ?: return
            // Armed before it changes: if this throws, the next restore tries again.
            session.wakeAt?.let { snoozeAlarms.set(alarmId, it) }
            commit(next)
            session
        }
        if (session.state is RingSession.State.Ringing) {
            outputs.clear(alarmId)
            outputs.startRinging(session)
            log(AlarmEvent.Type.NotAwake, alarmId)
            Log.i(TAG, "Alarm $alarmId: wake-up check unanswered, ringing again")
        } else {
            outputs.showCheck(session, alarmOrNull(alarmId)?.label.orEmpty())
        }
    }

    override suspend fun restore() {
        val now = clock.now()
        val next = update { sessions ->
            sessions.sessions.fold(sessions) { acc, s ->
                val due = s.wakeAt?.let { it <= now } == true
                when {
                    !due -> acc
                    s.isChecking -> acc.checkTimer(s.alarmId, now)
                    else -> acc.snoozeEnded(s.alarmId, now)
                }
            }
        }
        for (session in next.sessions) {
            when (val st = session.state) {
                is RingSession.State.Snoozed -> {
                    snoozeAlarms.set(session.alarmId, st.until)
                    outputs.showSnoozed(session, alarmOrNull(session.alarmId))
                }

                is RingSession.State.CheckPending, is RingSession.State.Checking -> {
                    snoozeAlarms.set(session.alarmId, session.wakeAt!!)
                    outputs.showCheck(session, alarmOrNull(session.alarmId)?.label.orEmpty())
                }

                is RingSession.State.Ringing -> {
                    outputs.clear(session.alarmId)
                    outputs.startRinging(session)
                }
            }
        }
    }

    override suspend fun snooze(alarmId: Long): Boolean {
        val alarm = alarmOrNull(alarmId)
        val config = alarm?.snooze ?: SnoozeConfig()
        val session = mutex.withLock {
            val next = state.value.snooze(alarmId, clock.now(), config) ?: return false
            val session = next[alarmId]!!
            // Armed before going quiet: if this throws, the alarm keeps ringing.
            snoozeAlarms.set(alarmId, (session.state as RingSession.State.Snoozed).until)
            commit(next)
            session
        }
        outputs.showSnoozed(session, alarm)
        log(AlarmEvent.Type.Snoozed, alarmId)
        Log.i(TAG, "Alarm $alarmId snoozed: ${session.state}")
        return true
    }

    /**
     * Nobody answered within the alarm's `ringMinutes` ([RingingService] times it): go quiet until the next
     * ring, or stop for good after the last one and report it as missed.
     */
    suspend fun timedOut(alarmId: Long) {
        val alarm = alarmOrNull(alarmId)
        val config = alarm?.autoSilence ?: AutoSilenceConfig()
        if (!config.enabled) return
        val (before, after) = mutex.withLock {
            val before = state.value[alarmId]?.takeIf { it.state is RingSession.State.Ringing } ?: return
            val next = state.value.timedOut(alarmId, clock.now(), config)
            val after = next[alarmId]
            // Armed before going quiet: if this throws, the alarm keeps ringing.
            (after?.state as? RingSession.State.Snoozed)?.let { snoozeAlarms.set(alarmId, it.until) }
            commit(next)
            before to after
        }
        if (after == null) {
            outputs.showMissed(before, alarm?.label.orEmpty())
            log(AlarmEvent.Type.Unanswered, alarmId)
            Log.i(TAG, "Alarm $alarmId missed: nobody answered")
        } else {
            outputs.showSnoozed(after, alarm)
            log(AlarmEvent.Type.AutoSilenced, alarmId)
            Log.i(TAG, "Alarm $alarmId unanswered, quiet until ${after.state}")
        }
    }

    override suspend fun dismiss(alarmId: Long) {
        if (state.value[alarmId] == null) {
            // Nothing to dismiss, but a stale notification or snooze alarm may be left over.
            snoozeAlarms.cancel(alarmId)
            outputs.clear(alarmId)
            return
        }
        val alarm = alarmOrNull(alarmId)
        endByUser(alarmId, alarm) { it.dismissed(alarmId, clock.now(), alarm.wakeUpCheckOrOff()) }
    }

    override suspend fun awake(alarmId: Long) {
        var answered = false
        update { sessions ->
            sessions.awake(alarmId).also { answered = it !== sessions }
        }
        if (!answered) return
        snoozeAlarms.cancel(alarmId)
        outputs.clear(alarmId)
        log(AlarmEvent.Type.Awake, alarmId)
        Log.i(TAG, "Alarm $alarmId: awake")
    }

    /**
     * Applies a dismissal by the user ([transform]): the session ends, or waits for the wake-up check. Logs
     * Dismissed only if something was ringing or snoozed. A transform that leaves it ringing (a mission solved,
     * more to go) is just saved.
     */
    private suspend fun endByUser(alarmId: Long, alarm: Alarm?, transform: (RingSessions) -> RingSessions) {
        val (before, after) = mutex.withLock {
            val before = state.value[alarmId]
            val next = transform(state.value)
            val after = next[alarmId]
            if (next === state.value) return
            if (after?.state is RingSession.State.Ringing) {
                commit(next)
                return
            }
            try {
                after?.wakeAt?.let { snoozeAlarms.set(alarmId, it) } ?: snoozeAlarms.cancel(alarmId)
                commit(next)
            } catch (e: Exception) {
                // No timer for the check: better no check than an alarm that can't be dismissed.
                Log.e(TAG, "Couldn't arm the wake-up check of alarm $alarmId", e)
                commit(next.dismiss(alarmId))
                snoozeAlarms.cancel(alarmId)
            }
            before to state.value[alarmId]
        }
        outputs.clear(alarmId)
        if (before?.isChecking == false) log(AlarmEvent.Type.Dismissed, alarmId)
        if (after?.isChecking == true) {
            outputs.showCheck(after, alarm?.label.orEmpty())
            Log.i(TAG, "Alarm $alarmId dismissed, wake-up check at ${after.wakeAt}")
        } else {
            Log.i(TAG, "Alarm $alarmId dismissed")
        }
    }

    /** Ends the session without a wake-up check (a preview replaced by a new one). */
    private suspend fun end(alarmId: Long) {
        update { it.dismiss(alarmId) }
        snoozeAlarms.cancel(alarmId)
        outputs.clear(alarmId)
    }

    override suspend fun openMissions(alarmId: Long) {
        update { it.missionsOpened(alarmId, clock.now()) }
    }

    override suspend fun missionActivity(alarmId: Long) {
        update { it.missionActivity(alarmId, clock.now()) }
    }

    override suspend fun missionCompleted(alarmId: Long, index: Int) {
        // Unknown alarm (database failure): no missions to wait for, so the first report dismisses.
        val alarm = alarmOrNull(alarmId)
        val total = alarm?.missions?.size ?: 0
        Log.i(TAG, "Alarm $alarmId: mission ${index + 1} of $total solved")
        // After the last one it is dismissed like the button would.
        endByUser(alarmId, alarm) { it.missionCompleted(alarmId, index, total, clock.now(), alarm.wakeUpCheckOrOff()) }
    }

    override suspend fun leaveMissions(alarmId: Long) {
        update { it.missionsLeft(alarmId, clock.now()) }
    }

    override suspend fun preview(alarm: Alarm, at: Instant) {
        end(RingingController.PREVIEW_ID)
        previewAlarm = alarm.copy(id = RingingController.PREVIEW_ID)
        // A snooze without a session rings anyway, so the snooze alarm is the preview's trigger.
        snoozeAlarms.set(RingingController.PREVIEW_ID, at)
        Log.i(TAG, "Preview rings at $at")
    }

    override suspend fun alarm(alarmId: Long): Alarm? = alarmOrNull(alarmId)

    override suspend fun missed(alarmId: Long, due: Instant, reason: MissReason) {
        outputs.showMissedRing(alarmId, alarmOrNull(alarmId)?.label.orEmpty(), due, reason)
    }

    private fun Alarm?.wakeUpCheckOrOff(): WakeUpCheckConfig = this?.wakeUpCheck ?: WakeUpCheckConfig()

    private fun log(type: AlarmEvent.Type, alarmId: Long) = events.add(AlarmEvent(clock.now(), type, alarmId))

    /** The alarm's settings, or null if it's gone or the database fails: ringing then uses defaults. */
    suspend fun alarmOrNull(alarmId: Long): Alarm? = if (alarmId == RingingController.PREVIEW_ID) {
        previewAlarm
    } else {
        try {
            repository.getAlarm(alarmId)
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't read alarm $alarmId, using defaults", e)
            null
        }
    }

    /** Skips saving when [transform] returns the sessions unchanged (the same object). */
    private suspend fun update(transform: (RingSessions) -> RingSessions): RingSessions = mutex.withLock {
        val before = state.value
        transform(before).also { if (it !== before) commit(it) }
    }

    /** Call with [mutex] held. */
    private suspend fun commit(next: RingSessions) {
        state.value = next
        try {
            withContext(dispatchers.io) { store.save(next) }
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't save ring sessions", e)
        }
    }

    private fun load(): RingSessions = try {
        store.load()
    } catch (e: Exception) {
        Log.e(TAG, "Couldn't load ring sessions", e)
        RingSessions()
    }
}
