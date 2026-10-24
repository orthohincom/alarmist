package com.orthohin.alarmist.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * One row per alarm, flattened so every field is a plain column. Dates are epoch days, times are minutes
 * since midnight. See [AlarmMapper] for the encoding.
 */
@Entity(tableName = "alarms")
internal data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @ColumnInfo(name = "minute_of_day") val minuteOfDay: Int,
    /** Bit 0 is Monday, bit 6 is Sunday. 0 means a one-off alarm. */
    @ColumnInfo(name = "repeat_days") val repeatDays: Int,
    @ColumnInfo(name = "once_date") val onceDate: Long?,
    val enabled: Boolean,
    val label: String,
    @ColumnInfo(name = "skipped_date") val skippedDate: Long?,
    @ColumnInfo(name = "sound_kind") val soundKind: String,
    @ColumnInfo(name = "sound_value") val soundValue: String,
    @ColumnInfo(name = "backup_sound_id") val backupSoundId: String,
    @ColumnInfo(name = "volume_percent") val volumePercent: Int,
    @ColumnInfo(name = "ramp_up_seconds") val rampUpSeconds: Int,
    val vibrate: Boolean,
    @ColumnInfo(name = "snooze_enabled") val snoozeEnabled: Boolean,
    @ColumnInfo(name = "snooze_minutes") val snoozeMinutes: Int,
    @ColumnInfo(name = "snooze_max_count") val snoozeMaxCount: Int?,
    // Added in schema 2; the defaults fill existing rows.
    @ColumnInfo(name = "auto_silence_enabled", defaultValue = "1") val autoSilenceEnabled: Boolean,
    @ColumnInfo(name = "auto_silence_ring_minutes", defaultValue = "3") val autoSilenceRingMinutes: Int,
    @ColumnInfo(name = "auto_silence_snooze_minutes", defaultValue = "5") val autoSilenceSnoozeMinutes: Int,
    @ColumnInfo(name = "auto_silence_max_rings", defaultValue = "3") val autoSilenceMaxRings: Int,
    @ColumnInfo(name = "wake_check_enabled") val wakeCheckEnabled: Boolean,
    @ColumnInfo(name = "wake_check_delay_minutes") val wakeCheckDelayMinutes: Int,
    @ColumnInfo(name = "wake_check_response_seconds") val wakeCheckResponseSeconds: Int,
    @ColumnInfo(name = "relaunch_if_left") val relaunchIfLeft: Boolean,
    @ColumnInfo(name = "lock_volume") val lockVolume: Boolean,
    @ColumnInfo(name = "power_menu_guard") val powerMenuGuard: Boolean,
    /** Epoch millis of the last save, from the injected `Clock`. */
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

/** One mission of an alarm's sequence. [config] is opaque JSON owned by the mission plugin. */
@Entity(
    tableName = "alarm_missions",
    primaryKeys = ["alarm_id", "position"],
    foreignKeys = [
        ForeignKey(
            entity = AlarmEntity::class,
            parentColumns = ["id"],
            childColumns = ["alarm_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
internal data class MissionEntity(
    @ColumnInfo(name = "alarm_id") val alarmId: Long,
    val position: Int,
    val type: String,
    @ColumnInfo(name = "config_version") val configVersion: Int,
    val config: String
)

internal data class AlarmWithMissions(
    @Embedded val alarm: AlarmEntity,
    @Relation(parentColumn = "id", entityColumn = "alarm_id") val missions: List<MissionEntity>
)
