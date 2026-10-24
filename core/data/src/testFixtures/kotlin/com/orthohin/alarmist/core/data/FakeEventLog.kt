package com.orthohin.alarmist.core.data

import com.orthohin.alarmist.core.model.AlarmEvent
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [EventLog] and [ExpectedRings]. [events] is oldest first. */
class FakeEventLog :
    EventLog,
    ExpectedRings {
    private val log = MutableStateFlow(emptyList<AlarmEvent>())
    val events: List<AlarmEvent> get() = log.value
    val expected = mutableMapOf<Long, Instant>()

    fun types(): List<AlarmEvent.Type> = events.map { it.type }

    override fun add(event: AlarmEvent) {
        log.value += event
    }

    override fun observeRecent(limit: Int): Flow<List<AlarmEvent>> = log.map { it.asReversed().take(limit) }

    override suspend fun all(): Map<Long, Instant> = expected.toMap()

    override suspend fun set(alarmId: Long, at: Instant) {
        expected[alarmId] = at
    }

    override suspend fun clear(alarmId: Long) {
        expected -= alarmId
    }
}
