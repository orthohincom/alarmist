package com.orthohin.alarmist.core.ringing

import com.orthohin.alarmist.core.common.AppDispatchers
import com.orthohin.alarmist.core.common.FakeClock
import com.orthohin.alarmist.core.data.FakeAlarmRepository
import com.orthohin.alarmist.core.data.FakeEventLog
import com.orthohin.alarmist.core.model.Alarm
import com.orthohin.alarmist.core.model.AlarmEvent
import com.orthohin.alarmist.core.model.AutoSilenceConfig
import com.orthohin.alarmist.core.model.MissReason
import com.orthohin.alarmist.core.model.MissionProgress
import com.orthohin.alarmist.core.model.MissionSpec
import com.orthohin.alarmist.core.model.RingSession
import com.orthohin.alarmist.core.model.RingSession.State.CheckPending
import com.orthohin.alarmist.core.model.RingSession.State.Checking
import com.orthohin.alarmist.core.model.RingSession.State.Ringing
import com.orthohin.alarmist.core.model.RingSession.State.Snoozed
import com.orthohin.alarmist.core.model.RingSessions
import com.orthohin.alarmist.core.model.SnoozeConfig
import com.orthohin.alarmist.core.model.WakeUpCheckConfig
import com.orthohin.alarmist.core.scheduling.SnoozeAlarms
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultRingingControllerTest {
    private val t0 = Instant.parse("2026-10-16T07:00:00Z")
    private val clock = FakeClock(t0)
    private val repository = FakeAlarmRepository(
        listOf(
            Alarm(id = 1, time = LocalTime.of(7, 0), label = "Gym", snooze = SnoozeConfig(minutes = 5, maxCount = 1)),
            Alarm(id = 2, time = LocalTime.of(7, 0), missions = listOf(MissionSpec("math"), MissionSpec("shake")))
        )
    )
    private val store = FakeStore()
    private val snoozes = FakeSnoozeAlarms()
    private val outputs = FakeOutputs()
    private val log = FakeEventLog()

    private fun controller() =
        DefaultRingingController(store, repository, snoozes, outputs, clock, TestDispatchers, log)

    private fun minutes(n: Long): Instant = t0 + Duration.ofMinutes(n)

    @Test
    fun `ring persists the session and starts ringing`() = runTest {
        val c = controller()
        c.ring(1)
        val session = RingSession(1, t0, Ringing(t0))
        assertEquals(session, c.sessions.value.current)
        assertEquals(RingSessions(listOf(session)), store.saved)
        assertEquals(listOf(1L), outputs.started)
    }

    @Test
    fun `ringing again replaces a pending snooze`() = runTest {
        val c = controller()
        c.ring(1)
        c.snooze(1)
        clock.instant = minutes(2)
        c.ring(1)
        assertEquals(emptyMap<Long, Instant>(), snoozes.pending)
        assertEquals(RingSession(1, minutes(2), Ringing(minutes(2))), c.sessions.value[1])
    }

    @Test
    fun `snooze arms a system alarm and shows a notification`() = runTest {
        val c = controller()
        c.ring(1)
        clock.instant = minutes(1)
        assertTrue(c.snooze(1))
        assertEquals(mapOf(1L to minutes(6)), snoozes.pending)
        assertEquals(mapOf(1L to "Gym"), outputs.snoozed)
        assertNull(c.sessions.value.current)
        assertEquals(Snoozed(minutes(6)), store.saved!![1]!!.state)
    }

    @Test
    fun `snooze is refused at the alarm's limit`() = runTest {
        val c = controller()
        c.ring(1)
        c.snooze(1)
        c.snoozeEnded(1)
        snoozes.pending.clear()
        assertFalse(c.snooze(1))
        assertEquals(emptyMap<Long, Instant>(), snoozes.pending)
        assertTrue(c.sessions.value[1]!!.state is Ringing)
    }

    @Test
    fun `snooze uses defaults when the database fails`() = runTest {
        val c = controller()
        c.ring(1)
        repository.failure = IOException("disk")
        assertTrue(c.snooze(1))
        assertEquals(mapOf(1L to minutes(SnoozeConfig().minutes.toLong())), snoozes.pending)
    }

    @Test
    fun `it keeps ringing if the snooze alarm can't be set`() = runTest {
        val c = controller()
        c.ring(1)
        snoozes.failure = SecurityException("no")
        assertTrue(runCatching { c.snooze(1) }.isFailure)
        assertEquals(1L, c.sessions.value.current!!.alarmId)
    }

    @Test
    fun `snooze ending rings again`() = runTest {
        val c = controller()
        c.ring(1)
        c.snooze(1)
        clock.instant = minutes(5)
        c.snoozeEnded(1)
        assertEquals(RingSession(1, t0, Ringing(minutes(5)), snoozeCount = 1), c.sessions.value.current)
        assertEquals(listOf(1L, 1L), outputs.started)
        assertEquals(emptyMap<Long, String>(), outputs.snoozed)
    }

    @Test
    fun `a preview rings its unsaved settings via a snooze alarm`() = runTest {
        val c = controller()
        val draft = Alarm(time = LocalTime.of(7, 0), label = "Draft")
        c.preview(draft, minutes(1))
        assertEquals(mapOf(RingingController.PREVIEW_ID to minutes(1)), snoozes.pending)
        c.snoozeEnded(RingingController.PREVIEW_ID)
        assertEquals(RingingController.PREVIEW_ID, c.sessions.value.current!!.alarmId)
        assertEquals("Draft", c.alarm(RingingController.PREVIEW_ID)!!.label)
        assertEquals(RingingController.PREVIEW_ID, c.alarm(RingingController.PREVIEW_ID)!!.id)
    }

    @Test
    fun `a new preview replaces one that is ringing`() = runTest {
        val c = controller()
        c.preview(Alarm(time = LocalTime.of(7, 0)), minutes(1))
        c.snoozeEnded(RingingController.PREVIEW_ID)
        c.preview(Alarm(time = LocalTime.of(8, 0)), minutes(2))
        assertNull(c.sessions.value.current)
        assertEquals(mapOf(RingingController.PREVIEW_ID to minutes(2)), snoozes.pending)
    }

    @Test
    fun `a snooze without a session rings anyway`() = runTest {
        val c = controller()
        c.snoozeEnded(7)
        assertEquals(7L, c.sessions.value.current!!.alarmId)
        assertEquals(listOf(7L), outputs.started)
    }

    @Test
    fun `an unanswered ring goes quiet until the next one`() = runTest {
        val c = controller()
        c.ring(1)
        clock.instant = minutes(3)
        c.timedOut(1)
        assertEquals(mapOf(1L to minutes(8)), snoozes.pending)
        assertEquals(mapOf(1L to "Gym"), outputs.snoozed)
        assertEquals(RingSession(1, t0, Snoozed(minutes(8)), unansweredRings = 1), store.saved!![1])
    }

    @Test
    fun `the last unanswered ring is reported as missed`() = runTest {
        val c = controller()
        c.ring(1)
        repeat(2) {
            c.timedOut(1)
            c.snoozeEnded(1)
        }
        c.timedOut(1)
        assertEquals(RingSessions(), c.sessions.value)
        assertEquals(listOf(1L), outputs.missed)
        assertEquals(List(2) { AlarmEvent.Type.AutoSilenced } + AlarmEvent.Type.Unanswered, log.types())
    }

    @Test
    fun `a ring that never came is shown with the alarm's label`() = runTest {
        controller().missed(1, t0, MissReason.AppStopped)
        assertEquals(listOf("Gym"), outputs.missedRings)
    }

    @Test
    fun `with auto-silence off it keeps ringing`() = runTest {
        repository.save(repository.getAlarm(1)!!.copy(autoSilence = AutoSilenceConfig(enabled = false)))
        val c = controller()
        c.ring(1)
        c.timedOut(1)
        assertEquals(1L, c.sessions.value.current!!.alarmId)
        assertEquals(emptyMap<Long, Instant>(), snoozes.pending)
    }

    @Test
    fun `dismiss ends the session and its snooze`() = runTest {
        val c = controller()
        c.ring(1)
        c.snooze(1)
        c.dismiss(1)
        assertEquals(RingSessions(), c.sessions.value)
        assertEquals(RingSessions(), store.saved)
        assertEquals(emptyMap<Long, Instant>(), snoozes.pending)
        assertEquals(emptyMap<Long, String>(), outputs.snoozed)
        assertEquals(listOf(AlarmEvent.Type.Snoozed, AlarmEvent.Type.Dismissed), log.types())
        c.dismiss(1)
        assertEquals(2, log.events.size) // Nothing to dismiss: not logged.
    }

    @Test
    fun `missions mute, advance and dismiss after the last one`() = runTest {
        val c = controller()
        c.ring(2)
        c.openMissions(2)
        assertEquals(MissionProgress(0, t0.plusSeconds(30)), store.saved!![2]!!.mission)
        clock.instant = t0.plusSeconds(10)
        c.missionCompleted(2, 0)
        c.missionCompleted(2, 0) // Repeated report: ignored.
        assertEquals(MissionProgress(1, t0.plusSeconds(40)), c.sessions.value[2]!!.mission)
        c.missionCompleted(2, 1)
        assertNull(c.sessions.value[2])
        assertEquals(AlarmEvent.Type.Dismissed, log.events.last().type)
    }

    @Test
    fun `small mission activity isn't saved, leaving rings again`() = runTest {
        val c = controller()
        c.ring(2)
        c.openMissions(2)
        val saved = store.saved
        clock.instant = t0.plusSeconds(2)
        c.missionActivity(2)
        assertSame(saved, store.saved)
        c.leaveMissions(2)
        assertTrue(c.sessions.value[2]!!.isAudible(clock.now()))
    }

    private suspend fun withCheck(id: Long) = repository.save(
        repository.getAlarm(id)!!.copy(wakeUpCheck = WakeUpCheckConfig(true, delayMinutes = 5, responseSeconds = 60))
    )

    @Test
    fun `a dismissal with the wake-up check asks later and rings again if unanswered`() = runTest {
        withCheck(1)
        val c = controller()
        c.ring(1)
        c.dismiss(1)
        assertEquals(mapOf(1L to minutes(5)), snoozes.pending)
        assertEquals(CheckPending(minutes(5), 60), outputs.checks[1])
        assertNull(c.sessions.value.current)
        clock.instant = minutes(5)
        c.snoozeEnded(1) // the check's timer
        assertEquals(mapOf(1L to minutes(6)), snoozes.pending)
        assertEquals(Checking(minutes(6)), outputs.checks[1])
        clock.instant = minutes(6)
        c.snoozeEnded(1)
        assertEquals(RingSession(1, t0, Ringing(minutes(6))), c.sessions.value.current)
        assertEquals(listOf(1L, 1L), outputs.started)
        assertEquals(emptyMap<Long, RingSession.State>(), outputs.checks)
        assertEquals(listOf(AlarmEvent.Type.Dismissed, AlarmEvent.Type.NotAwake), log.types())
    }

    @Test
    fun `answering the wake-up check ends it`() = runTest {
        withCheck(1)
        val c = controller()
        c.ring(1)
        c.dismiss(1)
        clock.instant = minutes(5)
        c.snoozeEnded(1)
        c.awake(1)
        assertEquals(RingSessions(), store.saved)
        assertEquals(emptyMap<Long, Instant>(), snoozes.pending)
        assertEquals(emptyMap<Long, RingSession.State>(), outputs.checks)
        assertEquals(listOf(AlarmEvent.Type.Dismissed, AlarmEvent.Type.Awake), log.types())
        c.awake(1)
        assertEquals(2, log.events.size)
    }

    @Test
    fun `the last mission starts the wake-up check`() = runTest {
        withCheck(2)
        val c = controller()
        c.ring(2)
        c.openMissions(2)
        c.missionCompleted(2, 0)
        assertTrue(snoozes.pending.isEmpty())
        c.missionCompleted(2, 1)
        assertEquals(CheckPending(minutes(5), 60), c.sessions.value[2]!!.state)
        assertEquals(mapOf(2L to minutes(5)), snoozes.pending)
    }

    @Test
    fun `without a timer for the check, dismissing still works`() = runTest {
        withCheck(1)
        val c = controller()
        c.ring(1)
        snoozes.failure = SecurityException("no")
        c.dismiss(1)
        assertEquals(RingSessions(), c.sessions.value)
    }

    @Test
    fun `restore resumes a wake-up check`() = runTest {
        store.saved = RingSessions(
            listOf(
                RingSession(1, t0, CheckPending(minutes(5), 60)),
                RingSession(2, t0, CheckPending(minutes(1), 60)),
                RingSession(3, t0, Checking(minutes(1)))
            )
        )
        clock.instant = minutes(2)
        val c = controller()
        c.restore()
        assertEquals(mapOf(1L to minutes(5), 2L to minutes(3)), snoozes.pending)
        assertEquals(Checking(minutes(3)), outputs.checks[2]) // a late ask still gets its full time
        assertEquals(listOf(3L), outputs.started)
    }

    @Test
    fun `restore picks up persisted sessions`() = runTest {
        store.saved = RingSessions(
            listOf(
                RingSession(1, t0, Snoozed(minutes(5)), 1),
                RingSession(2, t0, Snoozed(minutes(1)), 1),
                RingSession(3, t0, Ringing(t0))
            )
        )
        clock.instant = minutes(2)
        val c = controller()
        c.restore()
        assertEquals(mapOf(1L to minutes(5)), snoozes.pending) // future snooze re-armed
        assertEquals(mapOf(1L to "Gym"), outputs.snoozed)
        assertEquals(Ringing(minutes(2)), c.sessions.value[2]!!.state) // missed snooze rings now
        assertEquals(setOf(2L, 3L), outputs.started.toSet())
        assertEquals(3L, c.sessions.value.current!!.alarmId)
    }

    @Test
    fun `it rings even if the sessions can't be saved or loaded`() = runTest {
        store.failure = IOException("disk")
        val c = controller()
        c.ring(1)
        assertEquals(1L, c.sessions.value.current!!.alarmId)
        assertEquals(listOf(1L), outputs.started)
    }
}

private object TestDispatchers : AppDispatchers {
    override val io = Dispatchers.Unconfined
    override val default = Dispatchers.Unconfined
}

private class FakeStore : RingSessionStore {
    var saved: RingSessions? = null
    var failure: Exception? = null

    override fun load(): RingSessions {
        failure?.let { throw it }
        return saved ?: RingSessions()
    }

    override fun save(sessions: RingSessions) {
        failure?.let { throw it }
        saved = sessions
    }
}

private class FakeSnoozeAlarms : SnoozeAlarms {
    val pending = mutableMapOf<Long, Instant>()
    var failure: Exception? = null

    override fun set(alarmId: Long, at: Instant) {
        failure?.let { throw it }
        pending[alarmId] = at
    }

    override fun cancel(alarmId: Long) {
        pending -= alarmId
    }
}

private class FakeOutputs : RingingOutputs {
    val started = mutableListOf<Long>()
    val snoozed = mutableMapOf<Long, String>()
    val missed = mutableListOf<Long>()
    val missedRings = mutableListOf<String>()
    val checks = mutableMapOf<Long, RingSession.State>()

    override fun showCheck(session: RingSession, label: String) {
        checks[session.alarmId] = session.state
    }

    override fun showMissedRing(alarmId: Long, label: String, due: Instant, reason: MissReason) {
        missedRings += label
    }

    override fun startRinging(session: RingSession) {
        started += session.alarmId
    }

    override fun showSnoozed(session: RingSession, alarm: Alarm?) {
        snoozed[session.alarmId] = alarm?.label.orEmpty()
    }

    override fun showMissed(session: RingSession, label: String) {
        missed += session.alarmId
    }

    override fun clear(alarmId: Long) {
        snoozed -= alarmId
        checks -= alarmId
    }
}
