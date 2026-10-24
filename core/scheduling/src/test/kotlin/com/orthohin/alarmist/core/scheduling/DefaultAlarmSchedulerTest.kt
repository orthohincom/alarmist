package com.orthohin.alarmist.core.scheduling

import com.orthohin.alarmist.core.common.FakeClock
import com.orthohin.alarmist.core.data.FakeAlarmRepository
import com.orthohin.alarmist.core.data.FakeEventLog
import com.orthohin.alarmist.core.model.Alarm
import com.orthohin.alarmist.core.model.AlarmEvent
import com.orthohin.alarmist.core.model.MissReason
import com.orthohin.alarmist.core.model.Schedule
import java.io.IOException
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAlarmSchedulerTest {
    // Friday 2026-10-16, 06:00 UTC.
    private val clock = FakeClock(Instant.parse("2026-10-16T06:00:00Z"), ZoneId.of("UTC"))
    private val repository = FakeAlarmRepository()
    private val system = FakeSystemAlarms()
    private val ringer = FakeRinger()
    private val rung get() = ringer.rung
    private val log = FakeEventLog()
    private var bootTime = Instant.parse("2026-10-01T00:00:00Z")
    private val scheduler = DefaultAlarmScheduler(repository, system, ringer, clock, log, log) { bootTime }

    private val once = Alarm(time = LocalTime.of(7, 0))
    private val weekdays = Alarm(
        time = LocalTime.of(7, 0),
        schedule = Schedule.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
    )

    private fun at(text: String) = Instant.parse(text)

    @Test
    fun `saving an enabled alarm schedules its next ring`() = runTest {
        val id = scheduler.save(once)
        assertEquals(mapOf(id to at("2026-10-16T07:00:00Z")), system.pending)
    }

    @Test
    fun `saving a disabled alarm cancels it`() = runTest {
        val id = scheduler.save(once)
        scheduler.save(once.copy(id = id, enabled = false))
        assertEquals(emptyMap<Long, Instant>(), system.pending)
    }

    @Test
    fun `setEnabled cancels and re-schedules`() = runTest {
        val id = scheduler.save(weekdays)
        scheduler.setEnabled(id, false)
        assertEquals(emptyMap<Long, Instant>(), system.pending)
        scheduler.setEnabled(id, true)
        assertEquals(mapOf(id to at("2026-10-16T07:00:00Z")), system.pending)
    }

    @Test
    fun `delete cancels and removes`() = runTest {
        val id = scheduler.save(once)
        scheduler.delete(id)
        assertEquals(emptyMap<Long, Instant>(), system.pending)
        assertNull(repository.getAlarm(id))
    }

    @Test
    fun `rescheduleAll schedules enabled alarms and cancels the rest`() = runTest {
        val on = repository.save(weekdays)
        val off = repository.save(once.copy(enabled = false))
        system.pending[off] = at("2026-10-16T07:00:00Z")
        scheduler.rescheduleAll(RescheduleReason.AppStart)
        assertEquals(mapOf(on to at("2026-10-16T07:00:00Z")), system.pending)
    }

    @Test
    fun `rescheduleAll follows a time zone change`() = runTest {
        val id = scheduler.save(once)
        clock.zoneId = ZoneId.of("Asia/Dhaka") // UTC+6: 07:00 there is 01:00 UTC, already past today.
        scheduler.rescheduleAll(RescheduleReason.AppStart)
        assertEquals(mapOf(id to at("2026-10-17T01:00:00Z")), system.pending)
    }

    @Test
    fun `rescheduleAll restores ringing and leaves snoozes alone`() = runTest {
        val id = scheduler.save(once)
        DefaultSnoozeAlarms(system).set(id, at("2026-10-16T06:05:00Z"))
        scheduler.rescheduleAll(RescheduleReason.AppStart)
        assertEquals(1, ringer.restored)
        assertEquals(mapOf(id to at("2026-10-16T06:05:00Z")), system.snoozes)
        DefaultSnoozeAlarms(system).cancel(id)
        assertEquals(emptyMap<Long, Instant>(), system.snoozes)
        assertEquals(mapOf(id to at("2026-10-16T07:00:00Z")), system.pending)
    }

    @Test
    fun `a one-off alarm rings once and turns itself off`() = runTest {
        val id = scheduler.save(once)
        clock.instant = at("2026-10-16T07:00:00Z")
        scheduler.onFired(id, at("2026-10-16T07:00:00Z"))
        assertEquals(listOf(id), rung)
        assertFalse(repository.getAlarm(id)!!.enabled)
        assertEquals(emptyMap<Long, Instant>(), system.pending)
    }

    @Test
    fun `a weekly alarm rings and books its next day`() = runTest {
        val id = scheduler.save(weekdays)
        clock.instant = at("2026-10-16T07:00:00Z")
        scheduler.onFired(id, at("2026-10-16T07:00:00Z"))
        assertEquals(listOf(id), rung)
        assertEquals(mapOf(id to at("2026-10-19T07:00:00Z")), system.pending) // Monday
    }

    @Test
    fun `a ring delivered early does not book the same ring again`() = runTest {
        val id = scheduler.save(weekdays)
        clock.instant = at("2026-10-16T07:00:00Z") - Duration.ofMillis(5)
        scheduler.onFired(id, at("2026-10-16T07:00:00Z"))
        assertEquals(mapOf(id to at("2026-10-19T07:00:00Z")), system.pending)
    }

    @Test
    fun `a stale system alarm for a disabled or deleted alarm does not ring`() = runTest {
        val off = repository.save(once.copy(enabled = false))
        scheduler.onFired(off, null)
        scheduler.onFired(999, null)
        assertEquals(emptyList<Long>(), rung)
    }

    @Test
    fun `the expected ring follows saves and is cleared on disable and delete`() = runTest {
        val id = scheduler.save(weekdays)
        assertEquals(mapOf(id to at("2026-10-16T07:00:00Z")), log.expected)
        scheduler.setEnabled(id, false)
        assertEquals(emptyMap<Long, Instant>(), log.expected)
        scheduler.setEnabled(id, true)
        scheduler.delete(id)
        assertEquals(emptyMap<Long, Instant>(), log.expected)
    }

    @Test
    fun `scheduling is logged only when the ring changes`() = runTest {
        scheduler.save(weekdays)
        scheduler.rescheduleAll(RescheduleReason.AppStart)
        scheduler.rescheduleAll(RescheduleReason.Boot)
        // App start isn't logged: it happens on every process start.
        assertEquals(listOf(AlarmEvent.Type.Scheduled, AlarmEvent.Type.Rescheduled), log.types())
        assertEquals("Boot", log.events.last().detail)
    }

    @Test
    fun `a ring due moments ago is left to the system alarm`() = runTest {
        val id = scheduler.save(once)
        clock.instant = at("2026-10-16T07:01:00Z")
        scheduler.rescheduleAll(RescheduleReason.AppStart)
        assertEquals(emptyList<Long>(), rung)
        assertEquals(mapOf(id to at("2026-10-16T07:00:00Z")), log.expected)
        // If the fire never comes, the next reschedule catches it.
        clock.instant = at("2026-10-16T07:05:00Z")
        scheduler.rescheduleAll(RescheduleReason.AppStart)
        assertEquals(listOf(id), rung)
    }

    @Test
    fun `a ring that never came rings late within the window`() = runTest {
        val id = scheduler.save(once)
        clock.instant = at("2026-10-16T07:08:00Z")
        scheduler.rescheduleAll(RescheduleReason.Boot)
        assertEquals(listOf(id), rung)
        assertFalse(repository.getAlarm(id)!!.enabled)
        assertEquals(emptyMap<Long, Instant>(), system.pending)
        assertEquals(emptyMap<Long, Instant>(), log.expected)
        val fired = log.events.single { it.type == AlarmEvent.Type.Fired }
        assertEquals(at("2026-10-16T07:00:00Z"), fired.due)
    }

    @Test
    fun `a ring missed while the phone was off is reported and a one-off alarm turns off`() = runTest {
        val id = scheduler.save(once)
        clock.instant = at("2026-10-16T09:00:00Z")
        bootTime = at("2026-10-16T08:55:00Z")
        scheduler.rescheduleAll(RescheduleReason.Boot)
        assertEquals(emptyList<Long>(), rung)
        assertEquals(listOf(Triple(id, at("2026-10-16T07:00:00Z"), MissReason.PhoneOff)), ringer.missed)
        assertFalse(repository.getAlarm(id)!!.enabled)
        assertEquals(emptyMap<Long, Instant>(), system.pending)
        val missed = log.events.single { it.type == AlarmEvent.Type.Missed }
        assertEquals(MissReason.PhoneOff.name, missed.detail)
    }

    @Test
    fun `a ring missed while the phone was on blames the app being stopped and books the next one`() = runTest {
        val id = scheduler.save(weekdays)
        clock.instant = at("2026-10-16T09:00:00Z")
        scheduler.rescheduleAll(RescheduleReason.AppStart)
        assertEquals(MissReason.AppStopped, ringer.missed.single().third)
        assertTrue(repository.getAlarm(id)!!.enabled)
        assertEquals(mapOf(id to at("2026-10-19T07:00:00Z")), system.pending)
        assertEquals(mapOf(id to at("2026-10-19T07:00:00Z")), log.expected)
    }

    @Test
    fun `a clock jump past a ring leaves it to the system alarm`() = runTest {
        val id = scheduler.save(once)
        clock.instant = at("2026-10-16T09:00:00Z")
        scheduler.rescheduleAll(RescheduleReason.TimeChanged)
        assertEquals(emptyList<Long>(), rung)
        assertEquals(emptyList<Triple<Long, Instant, MissReason>>(), ringer.missed)
        assertEquals(mapOf(id to at("2026-10-16T07:00:00Z")), log.expected)
        scheduler.onFired(id, at("2026-10-16T07:00:00Z"))
        assertEquals(emptyMap<Long, Instant>(), log.expected)
    }

    @Test
    fun `it still rings when the database fails`() = runTest {
        val id = scheduler.save(once)
        repository.failure = IOException("disk")
        val failure = runCatching { scheduler.onFired(id, null) }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertEquals(listOf(id), rung)
    }
}

private class FakeSystemAlarms : SystemAlarms {
    val pending = mutableMapOf<Long, Instant>()
    val snoozes = mutableMapOf<Long, Instant>()

    private fun of(kind: AlarmKind) = if (kind == AlarmKind.Ring) pending else snoozes

    override fun set(alarmId: Long, triggerAt: Instant, kind: AlarmKind) {
        of(kind)[alarmId] = triggerAt
    }

    override fun cancel(alarmId: Long, kind: AlarmKind) {
        of(kind) -= alarmId
    }
}

private class FakeRinger : AlarmRinger {
    val rung = mutableListOf<Long>()
    val missed = mutableListOf<Triple<Long, Instant, MissReason>>()
    var restored = 0

    override suspend fun missed(alarmId: Long, due: Instant, reason: MissReason) {
        missed += Triple(alarmId, due, reason)
    }

    override suspend fun ring(alarmId: Long) {
        rung += alarmId
    }

    override suspend fun snoozeEnded(alarmId: Long) = Unit

    override suspend fun restore() {
        restored++
    }
}
