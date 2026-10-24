package com.orthohin.alarmist.feature.settings

import com.orthohin.alarmist.core.model.Alarm
import com.orthohin.alarmist.core.model.Schedule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class RestorePlanTest {
    private val now = Instant.parse("2026-10-16T08:00:00Z")
    private val zone = ZoneOffset.UTC
    private val weekly = Alarm(time = LocalTime.of(7, 0), schedule = Schedule.Weekly(setOf(DayOfWeek.MONDAY)))
    private val once = Alarm(time = LocalTime.of(9, 0))

    private fun restore(existing: List<Alarm>, backup: List<Alarm>) = alarmsToRestore(existing, backup, now, zone)

    @Test
    fun `adds every alarm as new when nothing is here`() {
        assertEquals(listOf(weekly, once), restore(emptyList(), listOf(weekly.copy(id = 5), once)))
    }

    @Test
    fun `skips alarms already here, ignoring id, on-off and skip`() {
        val stored = weekly.copy(id = 3, enabled = false, skippedDate = LocalDate.of(2026, 10, 19))
        assertEquals(listOf(once), restore(listOf(stored), listOf(weekly, once)))
        assertEquals(emptyList<Alarm>(), restore(listOf(stored, once.copy(id = 4)), listOf(weekly, once)))
    }

    @Test
    fun `a different setting is a different alarm`() {
        assertEquals(listOf(weekly), restore(listOf(weekly.copy(id = 3, label = "Gym")), listOf(weekly)))
    }

    @Test
    fun `each stored alarm matches one backup alarm`() {
        assertEquals(listOf(weekly), restore(listOf(weekly.copy(id = 3)), listOf(weekly, weekly)))
    }

    @Test
    fun `an alarm that can't ring again is restored off`() {
        val past = once.copy(schedule = Schedule.Once(LocalDate.of(2026, 10, 1)))
        val today = once.copy(schedule = Schedule.Once(LocalDate.of(2026, 10, 16)))
        assertEquals(listOf(past.copy(enabled = false), today), restore(emptyList(), listOf(past, today)))
    }
}
