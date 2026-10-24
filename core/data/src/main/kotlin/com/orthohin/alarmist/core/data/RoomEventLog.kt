package com.orthohin.alarmist.core.data

import android.util.Log
import com.orthohin.alarmist.core.common.AppDispatchers
import com.orthohin.alarmist.core.common.Clock
import com.orthohin.alarmist.core.data.db.EventDao
import com.orthohin.alarmist.core.data.db.EventEntity
import com.orthohin.alarmist.core.data.db.ExpectedRingEntity
import com.orthohin.alarmist.core.model.AlarmEvent
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
internal class RoomEventLog @Inject constructor(
    private val dao: EventDao,
    private val clock: Clock,
    dispatchers: AppDispatchers
) : EventLog,
    ExpectedRings {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)

    // Keeps entries in the order they were added.
    private val writes = Mutex()

    override fun add(event: AlarmEvent) {
        scope.launch {
            try {
                writes.withLock {
                    dao.insert(event.toEntity())
                    dao.deleteBefore((clock.now() - Duration.ofDays(EventLog.RETENTION_DAYS)).toEpochMilli())
                }
            } catch (e: Exception) {
                Log.e("Alarmist", "Couldn't log $event", e)
            }
        }
    }

    override fun observeRecent(limit: Int): Flow<List<AlarmEvent>> =
        dao.observeRecent(limit).map { rows -> rows.mapNotNull { it.toModel() } }

    override suspend fun all(): Map<Long, Instant> =
        dao.getExpected().associate { it.alarmId to Instant.ofEpochMilli(it.triggerAt) }

    override suspend fun set(alarmId: Long, at: Instant) =
        dao.upsertExpected(ExpectedRingEntity(alarmId, at.toEpochMilli()))

    override suspend fun clear(alarmId: Long) = dao.deleteExpected(alarmId)

    private fun AlarmEvent.toEntity() = EventEntity(
        at = at.toEpochMilli(),
        type = type.name,
        alarmId = alarmId,
        due = due?.toEpochMilli(),
        detail = detail
    )

    /** Null for a type this version doesn't know (written by a newer one). */
    private fun EventEntity.toModel(): AlarmEvent? {
        val type = AlarmEvent.Type.entries.firstOrNull { it.name == type } ?: return null
        return AlarmEvent(Instant.ofEpochMilli(at), type, alarmId, due?.let(Instant::ofEpochMilli), detail)
    }
}
