package com.orthohin.alarmist.core.data

import com.orthohin.alarmist.core.model.Alarm
import kotlinx.coroutines.flow.Flow

/** The stored alarms. All functions are main-safe. */
interface AlarmRepository {
    /** All alarms, ordered by time of day, then by id. Emits again after every change. */
    fun observeAlarms(): Flow<List<Alarm>>

    suspend fun getAlarm(id: Long): Alarm?

    /** For rescheduling after boot, a time change or an app update. */
    suspend fun getEnabledAlarms(): List<Alarm>

    /**
     * Inserts the alarm if its id is [Alarm.NEW_ID], otherwise replaces the stored one (re-creating it if it
     * was deleted meanwhile). Returns its id.
     */
    suspend fun save(alarm: Alarm): Long

    /** Does nothing if there is no alarm with [id]. */
    suspend fun setEnabled(id: Long, enabled: Boolean)

    /** Does nothing if there is no alarm with [id]. */
    suspend fun delete(id: Long)
}
