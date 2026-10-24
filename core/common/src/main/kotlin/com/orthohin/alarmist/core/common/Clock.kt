package com.orthohin.alarmist.core.common

import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Source of the current time and time zone. Inject it and never call `System.currentTimeMillis()` directly,
 * so tests can control time.
 */
interface Clock {
    fun now(): Instant

    /** Read it on every use: the user or the network can change the time zone at any moment. */
    fun zone(): ZoneId
}

/** The device's wall clock and its current default time zone. */
object DeviceClock : Clock {
    override fun now(): Instant = Instant.now()

    override fun zone(): ZoneId = ZoneId.systemDefault()
}

/**
 * Emits [Clock.now] at once and then at the start of every minute, for "rings in" countdowns. Only the
 * emitted values come from the clock; the waiting uses the coroutine's delay.
 */
fun Clock.minuteTicks(): Flow<Instant> = flow {
    while (true) {
        val now = now()
        emit(now)
        delay(MILLIS_PER_MINUTE - Math.floorMod(now.toEpochMilli(), MILLIS_PER_MINUTE))
    }
}

private const val MILLIS_PER_MINUTE = 60_000L
