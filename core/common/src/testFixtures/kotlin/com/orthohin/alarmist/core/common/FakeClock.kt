package com.orthohin.alarmist.core.common

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** A [Clock] that tests set and move by hand. Use with `testImplementation(testFixtures(project(":core:common")))`. */
class FakeClock(var instant: Instant = Instant.parse("2026-01-01T00:00:00Z"), var zoneId: ZoneId = ZoneId.of("UTC")) :
    Clock {
    override fun now(): Instant = instant

    override fun zone(): ZoneId = zoneId

    fun advanceBy(duration: Duration) {
        instant += duration
    }
}
