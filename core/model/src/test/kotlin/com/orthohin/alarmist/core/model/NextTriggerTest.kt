package com.orthohin.alarmist.core.model

import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

// 2026-10-16 is a Friday. New York DST 2026: gap on Mar 8 (02:00→03:00), overlap on Nov 1 (02:00→01:00).
class NextTriggerTest {
    private val newYork = ZoneId.of("America/New_York")
    private val dhaka = ZoneId.of("Asia/Dhaka") // UTC+6, no DST

    private fun at(local: String, zone: ZoneId = newYork): Instant = LocalDateTime.parse(local).atZone(zone).toInstant()

    private fun alarm(time: String, schedule: Schedule = Schedule.Once(), skippedDate: String? = null) =
        Alarm(time = LocalTime.parse(time), schedule = schedule, skippedDate = skippedDate?.let(LocalDate::parse))

    private fun weekly(vararg days: DayOfWeek) = Schedule.Weekly(days.toSet())

    private val everyDay = Schedule.Weekly(DayOfWeek.entries.toSet())

    // One-off

    @Test
    fun `disabled alarm never rings`() {
        val disabled = alarm("07:00").copy(enabled = false)
        assertNull(disabled.nextTrigger(at("2026-10-16T06:00"), newYork))
    }

    @Test
    fun `once rings later today`() {
        assertEquals(at("2026-10-16T07:00"), alarm("07:00").nextTrigger(at("2026-10-16T06:00"), newYork))
    }

    @Test
    fun `once rings tomorrow when the time has passed`() {
        assertEquals(at("2026-10-17T07:00"), alarm("07:00").nextTrigger(at("2026-10-16T08:00"), newYork))
    }

    @Test
    fun `next trigger is strictly after the given instant`() {
        assertEquals(at("2026-10-17T07:00"), alarm("07:00").nextTrigger(at("2026-10-16T07:00"), newYork))
    }

    @Test
    fun `midnight alarm rings at the start of the next day`() {
        assertEquals(at("2026-10-17T00:00"), alarm("00:00").nextTrigger(at("2026-10-16T23:59"), newYork))
    }

    @Test
    fun `once on a date rings on that date`() {
        val onDate = alarm("07:00", Schedule.Once(LocalDate.parse("2026-12-25")))
        assertEquals(at("2026-12-25T07:00"), onDate.nextTrigger(at("2026-10-16T06:00"), newYork))
    }

    @Test
    fun `once on a past date never rings`() {
        val onDate = alarm("07:00", Schedule.Once(LocalDate.parse("2026-10-15")))
        assertNull(onDate.nextTrigger(at("2026-10-16T06:00"), newYork))
    }

    @Test
    fun `once on today's date never rings after the time has passed`() {
        val onDate = alarm("07:00", Schedule.Once(LocalDate.parse("2026-10-16")))
        assertNull(onDate.nextTrigger(at("2026-10-16T07:30"), newYork))
    }

    @Test
    fun `skipped once on a date never rings`() {
        val onDate = alarm("07:00", Schedule.Once(LocalDate.parse("2026-12-25")), skippedDate = "2026-12-25")
        assertNull(onDate.nextTrigger(at("2026-10-16T06:00"), newYork))
    }

    @Test
    fun `skipped once without a date rings the day after`() {
        val once = alarm("07:00", skippedDate = "2026-10-16")
        assertEquals(at("2026-10-17T07:00"), once.nextTrigger(at("2026-10-16T06:00"), newYork))
    }

    // Weekly

    @Test
    fun `weekly rings today when today is a chosen day and the time is ahead`() {
        val a = alarm("07:00", weekly(FRIDAY, MONDAY))
        assertEquals(at("2026-10-16T07:00"), a.nextTrigger(at("2026-10-16T06:00"), newYork))
    }

    @Test
    fun `weekly rings on the next chosen day`() {
        val a = alarm("07:00", weekly(FRIDAY, MONDAY))
        assertEquals(at("2026-10-19T07:00"), a.nextTrigger(at("2026-10-16T08:00"), newYork))
    }

    @Test
    fun `weekly with only today rings next week once today has passed`() {
        val a = alarm("07:00", weekly(FRIDAY))
        assertEquals(at("2026-10-23T07:00"), a.nextTrigger(at("2026-10-16T08:00"), newYork))
    }

    @Test
    fun `weekend alarm skips weekdays`() {
        val a = alarm("09:00", weekly(SATURDAY, SUNDAY))
        assertEquals(at("2026-10-17T09:00"), a.nextTrigger(at("2026-10-16T10:00"), newYork))
        assertEquals(at("2026-10-24T09:00"), a.nextTrigger(at("2026-10-18T10:00"), newYork))
    }

    @Test
    fun `weekly just after midnight belongs to the new day`() {
        val a = alarm("00:30", weekly(MONDAY))
        assertEquals(at("2026-10-19T00:30"), a.nextTrigger(at("2026-10-18T23:00"), newYork))
    }

    // Skip next

    @Test
    fun `skipped weekly occurrence rings at the following one`() {
        val a = alarm("07:00", weekly(FRIDAY, MONDAY), skippedDate = "2026-10-16")
        assertEquals(at("2026-10-19T07:00"), a.nextTrigger(at("2026-10-16T06:00"), newYork))
    }

    @Test
    fun `skipping the only weekday rings two weeks out`() {
        val a = alarm("07:00", weekly(FRIDAY), skippedDate = "2026-10-23")
        assertEquals(at("2026-10-30T07:00"), a.nextTrigger(at("2026-10-16T08:00"), newYork))
    }

    @Test
    fun `stale skipped date has no effect`() {
        val a = alarm("07:00", everyDay, skippedDate = "2026-10-01")
        assertEquals(at("2026-10-16T07:00"), a.nextTrigger(at("2026-10-16T06:00"), newYork))
    }

    @Test
    fun `skippingNext skips only the next occurrence`() {
        val now = at("2026-10-16T08:00")
        val skipped = alarm("07:00", everyDay).skippingNext(now, newYork)
        assertEquals(LocalDate.parse("2026-10-17"), skipped.skippedDate)
        assertEquals(at("2026-10-18T07:00"), skipped.nextTrigger(now, newYork))
    }

    @Test
    fun `skippingNext twice still skips one occurrence`() {
        val now = at("2026-10-16T08:00")
        val twice = alarm("07:00", everyDay).skippingNext(now, newYork).skippingNext(now, newYork)
        assertEquals(LocalDate.parse("2026-10-17"), twice.skippedDate)
    }

    @Test
    fun `skippingNext leaves an alarm without a next occurrence unchanged`() {
        val disabled = alarm("07:00").copy(enabled = false)
        assertSame(disabled, disabled.skippingNext(at("2026-10-16T08:00"), newYork))
    }

    // Time zones

    @Test
    fun `alarm follows wall-clock time in the current zone`() {
        val now = Instant.parse("2026-10-16T00:00:00Z")
        val a = alarm("07:00", everyDay)
        assertEquals(Instant.parse("2026-10-16T11:00:00Z"), a.nextTrigger(now, newYork))
        assertEquals(Instant.parse("2026-10-16T01:00:00Z"), a.nextTrigger(now, dhaka))
    }

    @Test
    fun `weekday is decided by the local date in the current zone`() {
        // 02:00 UTC on Friday is Thursday 22:00 in New York and Friday 08:00 in Dhaka.
        val now = Instant.parse("2026-10-16T02:00:00Z")
        val a = alarm("07:00", weekly(FRIDAY))
        assertEquals(at("2026-10-16T07:00", newYork), a.nextTrigger(now, newYork))
        assertEquals(at("2026-10-23T07:00", dhaka), a.nextTrigger(now, dhaka))
    }

    // DST gap: New York, 2026-03-08, 02:00 EST → 03:00 EDT at 07:00Z

    @Test
    fun `time inside the DST gap rings when the gap ends`() {
        val a = alarm("02:30", everyDay)
        assertEquals(Instant.parse("2026-03-08T07:00:00Z"), a.nextTrigger(at("2026-03-08T00:00"), newYork))
    }

    @Test
    fun `time at the start of the DST gap rings when the gap ends`() {
        val a = alarm("02:00", everyDay)
        assertEquals(Instant.parse("2026-03-08T07:00:00Z"), a.nextTrigger(at("2026-03-08T00:00"), newYork))
    }

    @Test
    fun `times around the DST gap are unaffected`() {
        val now = at("2026-03-08T00:00")
        assertEquals(Instant.parse("2026-03-08T06:59:00Z"), alarm("01:59", everyDay).nextTrigger(now, newYork))
        assertEquals(Instant.parse("2026-03-08T07:00:00Z"), alarm("03:00", everyDay).nextTrigger(now, newYork))
        assertEquals(Instant.parse("2026-03-08T11:00:00Z"), alarm("07:00", everyDay).nextTrigger(now, newYork))
    }

    @Test
    fun `gap time rings normally the day after the gap`() {
        val a = alarm("02:30", everyDay)
        val afterGapRing = Instant.parse("2026-03-08T07:00:00Z")
        assertEquals(at("2026-03-09T02:30"), a.nextTrigger(afterGapRing, newYork))
    }

    @Test
    fun `weekly alarm on a gap day still rings`() {
        val a = alarm("02:30", weekly(SUNDAY))
        assertEquals(Instant.parse("2026-03-08T07:00:00Z"), a.nextTrigger(at("2026-03-07T12:00"), newYork))
    }

    // DST overlap: New York, 2026-11-01, 02:00 EDT → 01:00 EST at 06:00Z. 01:30 is 05:30Z, then 06:30Z.

    @Test
    fun `time inside the DST overlap rings on the first pass`() {
        val a = alarm("01:30", everyDay)
        assertEquals(Instant.parse("2026-11-01T05:30:00Z"), a.nextTrigger(at("2026-11-01T00:00"), newYork))
    }

    @Test
    fun `overlap time does not ring again on the second pass`() {
        val a = alarm("01:30", everyDay)
        val firstPass = Instant.parse("2026-11-01T05:30:00Z")
        assertEquals(Instant.parse("2026-11-02T06:30:00Z"), a.nextTrigger(firstPass, newYork))
        // 01:45 EDT: after the first pass, before the clock goes back.
        val beforeTransition = Instant.parse("2026-11-01T05:45:00Z")
        assertEquals(Instant.parse("2026-11-02T06:30:00Z"), a.nextTrigger(beforeTransition, newYork))
    }

    @Test
    fun `alarm set during the repeated hour rings on the second pass`() {
        // 01:10 EST, after the clock went back.
        val a = alarm("01:30", everyDay)
        val inRepeatedHour = Instant.parse("2026-11-01T06:10:00Z")
        assertEquals(Instant.parse("2026-11-01T06:30:00Z"), a.nextTrigger(inRepeatedHour, newYork))
    }

    @Test
    fun `times around the DST overlap are unaffected`() {
        val now = at("2026-11-01T00:00")
        assertEquals(Instant.parse("2026-11-01T04:59:00Z"), alarm("00:59", everyDay).nextTrigger(now, newYork))
        assertEquals(Instant.parse("2026-11-01T07:00:00Z"), alarm("02:00", everyDay).nextTrigger(now, newYork))
        assertEquals(Instant.parse("2026-11-01T12:00:00Z"), alarm("07:00", everyDay).nextTrigger(now, newYork))
    }

    @Test
    fun `weekly alarm across the DST change keeps its wall-clock time`() {
        val a = alarm("07:00", weekly(WEDNESDAY))
        // EDT (UTC-4) before, EST (UTC-5) after.
        assertEquals(Instant.parse("2026-10-28T11:00:00Z"), a.nextTrigger(at("2026-10-27T12:00"), newYork))
        assertEquals(Instant.parse("2026-11-04T12:00:00Z"), a.nextTrigger(at("2026-10-28T12:00"), newYork))
    }
}
