package com.orthohin.alarmist.core.scheduling

import com.orthohin.alarmist.core.model.Alarm

/**
 * The only way to change alarms. Each call updates the database and the system schedule together, so they
 * can't drift apart. Read alarms from `AlarmRepository`.
 */
interface AlarmEditor {
    /** Saves (see `AlarmRepository.save`) and schedules or cancels the alarm's next ring. Returns its id. */
    suspend fun save(alarm: Alarm): Long

    suspend fun setEnabled(id: Long, enabled: Boolean)

    suspend fun delete(id: Long)
}
