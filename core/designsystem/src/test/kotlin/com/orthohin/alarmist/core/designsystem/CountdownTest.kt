package com.orthohin.alarmist.core.designsystem

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class CountdownTest {
    private val t0 = Instant.parse("2026-10-16T06:00:00Z")

    private fun countdown(d: Duration) = Countdown.between(t0, t0 + d)

    @Test
    fun `rounds up to whole minutes`() {
        assertEquals(Countdown(0, 0, 1), countdown(Duration.ofSeconds(30)))
        assertEquals(Countdown(0, 0, 1), countdown(Duration.ofSeconds(60)))
        assertEquals(Countdown(0, 0, 2), countdown(Duration.ofSeconds(61)))
    }

    @Test
    fun `splits into days, hours and minutes`() {
        assertEquals(Countdown(0, 7, 12), countdown(Duration.ofMinutes(7 * 60 + 12)))
        assertEquals(Countdown(2, 3, 0), countdown(Duration.ofHours(51)))
    }

    @Test
    fun `the past counts as zero`() {
        assertEquals(Countdown(0, 0, 0), countdown(Duration.ofMinutes(-5)))
    }

    @Test
    fun `the week starts on the locale's first day`() {
        assertEquals(DayOfWeek.SUNDAY, weekOrder(Locale.US).first())
        assertEquals(DayOfWeek.MONDAY, weekOrder(Locale.UK).first())
        assertEquals(DayOfWeek.entries.toSet(), weekOrder(Locale.US).toSet())
    }
}
