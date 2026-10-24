package com.orthohin.alarmist.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Event log rows. Times are epoch milliseconds; `type` is an `AlarmEvent.Type` name. */
@Entity(tableName = "events", indices = [Index("at")])
internal data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val type: String,
    @ColumnInfo(name = "alarm_id") val alarmId: Long?,
    val due: Long?,
    val detail: String
)

/** The ring each alarm is booked for, to notice one that never came. No foreign key: saving replaces alarm rows. */
@Entity(tableName = "expected_rings")
internal data class ExpectedRingEntity(
    @PrimaryKey @ColumnInfo(name = "alarm_id") val alarmId: Long,
    @ColumnInfo(name = "trigger_at") val triggerAt: Long
)

@Dao
internal interface EventDao {
    @Insert
    suspend fun insert(event: EventEntity)

    @Query("DELETE FROM events WHERE at < :before")
    suspend fun deleteBefore(before: Long)

    @Query("SELECT * FROM events ORDER BY at DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<EventEntity>>

    @Upsert
    suspend fun upsertExpected(ring: ExpectedRingEntity)

    @Query("DELETE FROM expected_rings WHERE alarm_id = :alarmId")
    suspend fun deleteExpected(alarmId: Long)

    @Query("SELECT * FROM expected_rings")
    suspend fun getExpected(): List<ExpectedRingEntity>
}
