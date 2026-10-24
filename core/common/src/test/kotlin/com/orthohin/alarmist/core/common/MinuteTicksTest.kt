package com.orthohin.alarmist.core.common

import java.time.Instant
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MinuteTicksTest {
    @Test
    fun `emits now, then waits for the next whole minute`() = runTest {
        val clock = FakeClock(Instant.parse("2026-10-16T06:00:20Z"))
        val first = clock.minuteTicks().take(1).toList()
        assertEquals(listOf(clock.instant), first)
        assertEquals(0L, currentTime)
        clock.minuteTicks().take(2).toList()
        assertEquals(40_000L, currentTime)
    }
}
