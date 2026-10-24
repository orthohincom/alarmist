package com.orthohin.alarmist.core.data

import com.orthohin.alarmist.core.common.Clock
import com.orthohin.alarmist.core.data.db.AlarmDao
import com.orthohin.alarmist.core.data.db.AlarmMapper
import com.orthohin.alarmist.core.model.Alarm
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomAlarmRepository @Inject constructor(private val dao: AlarmDao, private val clock: Clock) :
    AlarmRepository {
    override fun observeAlarms(): Flow<List<Alarm>> = dao.observeAll().map { rows -> rows.map(AlarmMapper::toModel) }

    override suspend fun getAlarm(id: Long): Alarm? = dao.get(id)?.let(AlarmMapper::toModel)

    override suspend fun getEnabledAlarms(): List<Alarm> = dao.getEnabled().map(AlarmMapper::toModel)

    override suspend fun save(alarm: Alarm): Long =
        dao.save(AlarmMapper.toEntity(alarm, nowMillis()), AlarmMapper.toMissionEntities(alarm))

    override suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled, nowMillis())

    override suspend fun delete(id: Long) = dao.delete(id)

    private fun nowMillis() = clock.now().toEpochMilli()
}
