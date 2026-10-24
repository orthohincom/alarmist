package com.orthohin.alarmist.feature.alarmlist

import com.orthohin.alarmist.core.model.Alarm
import com.orthohin.alarmist.core.model.Schedule
import com.orthohin.alarmist.core.model.skippingNext
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SUNDAY
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmListUiStateTest {
    private val zone = ZoneOffset.UTC

    // Friday 2026-10-16, 06:00.
    private val now = Instant.parse("2026-10-16T06:00:00Z")
    private val weekly = Alarm(id = 1, time = LocalTime.of(7, 0), schedule = Schedule.Weekly(setOf(MONDAY, FRIDAY)))

    @Test
    fun `the soonest enabled alarm is next`() {
        val later = Alarm(id = 2, time = LocalTime.of(6, 30))
        val off = Alarm(id = 3, time = LocalTime.of(6, 10), enabled = false)
        val state = alarmListUiState(listOf(off, later, weekly), now, zone)
        assertEquals(Instant.parse("2026-10-16T06:30:00Z"), state.nextRing)
        assertNull(state.alarms.first { it.id == 3L }.nextRing)
    }

    @Test
    fun `a skip shows while it applies`() {
        val skipping = weekly.skippingNext(now, zone)
        val item = alarmListUiState(listOf(skipping), now, zone).alarms.single()
        assertEquals(LocalDate.of(2026, 10, 16), item.skipping)
        assertEquals(Instant.parse("2026-10-19T07:00:00Z"), item.nextRing)
    }

    @Test
    fun `a stale or disabled skip is hidden`() {
        val stale = weekly.copy(skippedDate = LocalDate.of(2026, 10, 12))
        assertNull(alarmListUiState(listOf(stale), now, zone).alarms.single().skipping)
        val off = weekly.skippingNext(now, zone).copy(enabled = false)
        assertNull(alarmListUiState(listOf(off), now, zone).alarms.single().skipping)
    }

    @Test
    fun `days follow the week order, all seven is every day`() {
        val order = listOf(SUNDAY) + DayOfWeek.entries.dropLast(1)
        assertEquals(listOf(SUNDAY, MONDAY), orderedDays(setOf(MONDAY, SUNDAY), order))
        assertNull(orderedDays(DayOfWeek.entries.toSet(), order))
    }

    @Test
    fun `turning on a one-off alarm with a past date rings at its next time`() {
        val past =
            Alarm(
                id = 4,
                time = LocalTime.of(7, 0),
                enabled = false,
                schedule = Schedule.Once(LocalDate.of(2026, 10, 1))
            )
        assertEquals(Schedule.Once(), past.turnedOn(now, zone).schedule)
        val future = past.copy(schedule = Schedule.Once(LocalDate.of(2026, 10, 20)))
        assertEquals(future.schedule, future.turnedOn(now, zone).schedule)
    }
}
