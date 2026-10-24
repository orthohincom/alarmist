package com.orthohin.alarmist.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.orthohin.alarmist.core.model.Alarm
import kotlinx.coroutines.flow.Flow

@Dao
internal abstract class AlarmDao {
    @Transaction
    @Query("SELECT * FROM alarms ORDER BY minute_of_day, id")
    abstract fun observeAll(): Flow<List<AlarmWithMissions>>

    @Transaction
    @Query("SELECT * FROM alarms WHERE id = :id")
    abstract suspend fun get(id: Long): AlarmWithMissions?

    @Transaction
    @Query("SELECT * FROM alarms WHERE enabled = 1")
    abstract suspend fun getEnabled(): List<AlarmWithMissions>

    @Query("UPDATE alarms SET enabled = :enabled, updated_at = :updatedAt WHERE id = :id")
    abstract suspend fun setEnabled(id: Long, enabled: Boolean, updatedAt: Long)

    /** Missions go with it (foreign key cascade). */
    @Query("DELETE FROM alarms WHERE id = :id")
    abstract suspend fun delete(id: Long)

    /**
     * Inserts the alarm if its id is [Alarm.NEW_ID], otherwise writes it under its id (re-creating it if it
     * was deleted meanwhile), and replaces its missions. Returns the alarm's id.
     */
    @Transaction
    open suspend fun save(alarm: AlarmEntity, missions: List<MissionEntity>): Long {
        val id = if (alarm.id == Alarm.NEW_ID) {
            insert(alarm)
        } else {
            upsert(alarm)
            alarm.id
        }
        deleteMissions(id)
        insertMissions(missions.map { it.copy(alarmId = id) })
        return id
    }

    @Insert
    protected abstract suspend fun insert(alarm: AlarmEntity): Long

    @Upsert
    protected abstract suspend fun upsert(alarm: AlarmEntity)

    @Insert
    protected abstract suspend fun insertMissions(missions: List<MissionEntity>)

    @Query("DELETE FROM alarm_missions WHERE alarm_id = :alarmId")
    protected abstract suspend fun deleteMissions(alarmId: Long)
}
