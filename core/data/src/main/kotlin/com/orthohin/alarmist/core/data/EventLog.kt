package com.orthohin.alarmist.core.data

import com.orthohin.alarmist.core.model.AlarmEvent
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** The local event log (device-protected, kept for [RETENTION_DAYS] days). */
interface EventLog {
    /** Fire and forget: never blocks or throws, so logging can't get in the way of ringing. */
    fun add(event: AlarmEvent)

    /** Newest first. */
    fun observeRecent(limit: Int = 200): Flow<List<AlarmEvent>>

    companion object {
        const val RETENTION_DAYS = 30L
    }
}

/** The ring each enabled alarm is booked for. Kept by core:scheduling to detect rings that never came. */
interface ExpectedRings {
    suspend fun all(): Map<Long, Instant>

    suspend fun set(alarmId: Long, at: Instant)

    suspend fun clear(alarmId: Long)
}
