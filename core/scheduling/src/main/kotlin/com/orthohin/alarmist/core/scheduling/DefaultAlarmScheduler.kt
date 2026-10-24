package com.orthohin.alarmist.core.scheduling

import android.util.Log
import com.orthohin.alarmist.core.common.Clock
import com.orthohin.alarmist.core.data.AlarmRepository
import com.orthohin.alarmist.core.data.EventLog
import com.orthohin.alarmist.core.data.ExpectedRings
import com.orthohin.alarmist.core.model.Alarm
import com.orthohin.alarmist.core.model.AlarmEvent
import com.orthohin.alarmist.core.model.MissReason
import com.orthohin.alarmist.core.model.Schedule
import com.orthohin.alarmist.core.model.nextTrigger
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Every read-modify-schedule runs under one [mutex], so a reschedule that read the database before an edit
 * can't re-schedule an alarm the edit just turned off. Each booked ring is also kept in [expected], so a
 * ring that never came (phone off, app stopped) is noticed at the next [rescheduleAll].
 */
@Singleton
internal class DefaultAlarmScheduler @Inject constructor(
    private val repository: AlarmRepository,
    private val systemAlarms: SystemAlarms,
    private val ringer: AlarmRinger,
    private val clock: Clock,
    private val expected: ExpectedRings,
    private val events: EventLog,
    private val bootClock: BootClock
) : AlarmEditor,
    AlarmScheduler {
    private val mutex = Mutex()

    override suspend fun save(alarm: Alarm): Long = mutex.withLock {
        val id = repository.save(alarm)
        sync(alarm.copy(id = id), clock.now(), expectedOrEmpty())
        id
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) = mutex.withLock {
        repository.setEnabled(id, enabled)
        val alarm = repository.getAlarm(id)
        if (alarm == null) cancel(id) else sync(alarm, clock.now(), expectedOrEmpty())
    }

    override suspend fun delete(id: Long) = mutex.withLock {
        cancel(id)
        repository.delete(id)
    }

    override suspend fun rescheduleAll(reason: RescheduleReason) {
        val late = mutableListOf<Pair<Long, Instant>>()
        mutex.withLock {
            val now = clock.now()
            val known = expectedOrEmpty()
            val alarms = repository.observeAlarms().first().associateBy { it.id }
            // When the clock jumps past a ring, Android delivers it itself: no catch-up then, or it rings twice.
            val catchUp = reason != RescheduleReason.TimeChanged
            // Due moments ago (or skipped by a clock jump): the fire may still be on its way. Its expected ring
            // stays until the fire clears it, so if it never comes the next reschedule notices.
            val inFlight = known.filterValues { it <= now && (!catchUp || it > now - FIRE_GRACE) }.keys
            for ((id, due) in known) {
                if (!catchUp || due > now - FIRE_GRACE) continue
                clearExpected(id)
                val alarm = alarms[id]?.takeIf { it.enabled } ?: continue
                if (Duration.between(due, now) <= LATE_RING) {
                    late += id to due
                } else {
                    missed(alarm, due)
                }
            }
            // App start happens on every process start (each fire, too): logging it would flood the log.
            if (reason != RescheduleReason.AppStart) {
                events.add(AlarmEvent(now, AlarmEvent.Type.Rescheduled, detail = reason.name))
            }
            for (alarm in repository.observeAlarms().first()) {
                // A late one-off ring is handled by onFired below, which turns the alarm off.
                if (alarm.schedule is Schedule.Once && late.any { it.first == alarm.id }) continue
                sync(alarm, now, known, updateExpected = alarm.id !in inFlight)
            }
        }
        for ((id, due) in late) {
            Log.w(TAG, "Alarm $id ringing late (was due $due)")
            onFired(id, due)
        }
        ringer.restore()
    }

    /** Call with [mutex] held. A one-off alarm is turned off, as if it had rung. */
    private suspend fun missed(alarm: Alarm, due: Instant) {
        val reason = if (due < bootClock.bootTime()) MissReason.PhoneOff else MissReason.AppStopped
        Log.w(TAG, "Alarm ${alarm.id} missed its ring at $due: $reason")
        events.add(AlarmEvent(clock.now(), AlarmEvent.Type.Missed, alarm.id, due, reason.name))
        if (alarm.schedule is Schedule.Once) repository.setEnabled(alarm.id, false)
        try {
            ringer.missed(alarm.id, due, reason)
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't report missed alarm ${alarm.id}", e)
        }
    }

    /**
     * An alarm went off. Rings unless the alarm is gone or turned off (a stale system alarm), then books the
     * next ring: a one-off alarm is turned off, a weekly one is scheduled for its next day. If the database
     * can't be read, it rings anyway: a ring with default settings beats a silent alarm.
     */
    suspend fun onFired(alarmId: Long, triggerAt: Instant?) {
        val alarm = try {
            repository.getAlarm(alarmId)
        } catch (e: Exception) {
            ringer.ring(alarmId)
            throw e
        }
        if (alarm == null || !alarm.enabled) return
        ringer.ring(alarmId)
        events.add(AlarmEvent(clock.now(), AlarmEvent.Type.Fired, alarmId, triggerAt))
        mutex.withLock {
            val current = repository.getAlarm(alarmId) ?: return
            if (current.schedule is Schedule.Once) {
                repository.setEnabled(alarmId, false)
                cancel(alarmId)
            } else {
                // Never before the ring that just happened, in case the system delivered it a little early.
                val after = triggerAt?.let { maxOf(it, clock.now()) } ?: clock.now()
                sync(current, after, expectedOrEmpty())
            }
        }
    }

    /** Books [alarm]'s next ring after [after] (or cancels it). [known] holds the expected rings before this. */
    private suspend fun sync(alarm: Alarm, after: Instant, known: Map<Long, Instant>, updateExpected: Boolean = true) {
        val next = alarm.nextTrigger(after, clock.zone())
        if (next == null) {
            cancel(alarm.id)
            return
        }
        systemAlarms.set(alarm.id, next)
        if (!updateExpected || known[alarm.id] == next) return
        try {
            expected.set(alarm.id, next)
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't store the expected ring of alarm ${alarm.id}", e)
        }
        events.add(AlarmEvent(clock.now(), AlarmEvent.Type.Scheduled, alarm.id, next))
    }

    private suspend fun cancel(alarmId: Long) {
        systemAlarms.cancel(alarmId)
        clearExpected(alarmId)
    }

    // The expected rings are diagnostics: failing to read or write them must never stop an alarm.
    private suspend fun expectedOrEmpty(): Map<Long, Instant> = try {
        expected.all()
    } catch (e: Exception) {
        Log.e(TAG, "Couldn't read expected rings", e)
        emptyMap()
    }

    private suspend fun clearExpected(alarmId: Long) {
        try {
            expected.clear(alarmId)
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't clear the expected ring of alarm $alarmId", e)
        }
    }

    private companion object {
        val FIRE_GRACE: Duration = Duration.ofMinutes(2)
        val LATE_RING: Duration = Duration.ofMinutes(AlarmScheduler.LATE_RING_MINUTES)
    }
}
