package com.orthohin.alarmist.core.data

import com.orthohin.alarmist.core.model.Alarm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [AlarmRepository] with the same ordering and id rules as the Room one. */
class FakeAlarmRepository(initial: List<Alarm> = emptyList()) : AlarmRepository {
    private val alarms = MutableStateFlow(initial.associateBy { it.id })
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1

    /** Set to make every call throw, e.g. to test a broken database. */
    var failure: Exception? = null

    val current: List<Alarm> get() = alarms.value.values.sortedWith(compareBy({ it.time }, { it.id }))

    override fun observeAlarms(): Flow<List<Alarm>> = alarms.map { current }

    override suspend fun getAlarm(id: Long): Alarm? = check { alarms.value[id] }

    override suspend fun getEnabledAlarms(): List<Alarm> = check { current.filter { it.enabled } }

    override suspend fun save(alarm: Alarm): Long = check {
        val id = if (alarm.id == Alarm.NEW_ID) nextId++ else alarm.id
        alarms.update { it + (id to alarm.copy(id = id)) }
        id
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) = check {
        alarms.update { map -> map[id]?.let { map + (id to it.copy(enabled = enabled)) } ?: map }
    }

    override suspend fun delete(id: Long) = check { alarms.update { it - id } }

    private inline fun <T> check(block: () -> T): T {
        failure?.let { throw it }
        return block()
    }
}
