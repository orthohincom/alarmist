package com.orthohin.alarmist.core.data.db

import com.orthohin.alarmist.core.model.Alarm
import com.orthohin.alarmist.core.model.AlarmSound
import com.orthohin.alarmist.core.model.AntiCheatConfig
import com.orthohin.alarmist.core.model.AutoSilenceConfig
import com.orthohin.alarmist.core.model.MissionSpec
import com.orthohin.alarmist.core.model.Schedule
import com.orthohin.alarmist.core.model.SnoozeConfig
import com.orthohin.alarmist.core.model.SoundConfig
import com.orthohin.alarmist.core.model.WakeUpCheckConfig
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Converts between the domain [Alarm] and its rows. The column encoding is part of the schema: don't change it. */
internal object AlarmMapper {
    private const val SOUND_BUNDLED = "bundled"
    private const val SOUND_CUSTOM = "custom"
    private const val SOUND_IMPORTED = "imported"

    fun toEntity(alarm: Alarm, updatedAt: Long): AlarmEntity {
        val schedule = alarm.schedule
        return AlarmEntity(
            id = alarm.id,
            minuteOfDay = alarm.time.hour * 60 + alarm.time.minute,
            repeatDays = if (schedule is Schedule.Weekly) daysToMask(schedule.days) else 0,
            onceDate = (schedule as? Schedule.Once)?.date?.toEpochDay(),
            enabled = alarm.enabled,
            label = alarm.label,
            skippedDate = alarm.skippedDate?.toEpochDay(),
            soundKind = when (alarm.sound.sound) {
                is AlarmSound.Bundled -> SOUND_BUNDLED
                is AlarmSound.Custom -> SOUND_CUSTOM
                is AlarmSound.Imported -> SOUND_IMPORTED
            },
            soundValue = when (val sound = alarm.sound.sound) {
                is AlarmSound.Bundled -> sound.id
                is AlarmSound.Custom -> sound.uri
                is AlarmSound.Imported -> sound.fileName
            },
            backupSoundId = alarm.sound.backup.id,
            volumePercent = alarm.sound.volumePercent,
            rampUpSeconds = alarm.sound.rampUpSeconds,
            vibrate = alarm.sound.vibrate,
            snoozeEnabled = alarm.snooze.enabled,
            snoozeMinutes = alarm.snooze.minutes,
            snoozeMaxCount = alarm.snooze.maxCount,
            autoSilenceEnabled = alarm.autoSilence.enabled,
            autoSilenceRingMinutes = alarm.autoSilence.ringMinutes,
            autoSilenceSnoozeMinutes = alarm.autoSilence.snoozeMinutes,
            autoSilenceMaxRings = alarm.autoSilence.maxRings,
            wakeCheckEnabled = alarm.wakeUpCheck.enabled,
            wakeCheckDelayMinutes = alarm.wakeUpCheck.delayMinutes,
            wakeCheckResponseSeconds = alarm.wakeUpCheck.responseSeconds,
            relaunchIfLeft = alarm.antiCheat.relaunchIfLeft,
            lockVolume = alarm.antiCheat.lockVolume,
            powerMenuGuard = alarm.antiCheat.powerMenuGuard,
            updatedAt = updatedAt
        )
    }

    /** [MissionEntity.alarmId] is filled in by [AlarmDao.save]. */
    fun toMissionEntities(alarm: Alarm): List<MissionEntity> = alarm.missions.mapIndexed { position, mission ->
        MissionEntity(alarm.id, position, mission.type, mission.configVersion, mission.config)
    }

    fun toModel(row: AlarmWithMissions): Alarm {
        val e = row.alarm
        return Alarm(
            id = e.id,
            time = LocalTime.of(e.minuteOfDay / 60, e.minuteOfDay % 60),
            schedule = if (e.repeatDays == 0) {
                Schedule.Once(e.onceDate?.let(LocalDate::ofEpochDay))
            } else {
                Schedule.Weekly(maskToDays(e.repeatDays))
            },
            enabled = e.enabled,
            label = e.label,
            skippedDate = e.skippedDate?.let(LocalDate::ofEpochDay),
            sound = SoundConfig(
                sound = when (e.soundKind) {
                    SOUND_CUSTOM -> AlarmSound.Custom(e.soundValue)
                    SOUND_IMPORTED -> AlarmSound.Imported(e.soundValue)
                    else -> AlarmSound.Bundled(e.soundValue)
                },
                backup = AlarmSound.Bundled(e.backupSoundId),
                volumePercent = e.volumePercent,
                rampUpSeconds = e.rampUpSeconds,
                vibrate = e.vibrate
            ),
            snooze = SnoozeConfig(e.snoozeEnabled, e.snoozeMinutes, e.snoozeMaxCount),
            autoSilence = AutoSilenceConfig(
                e.autoSilenceEnabled,
                e.autoSilenceRingMinutes,
                e.autoSilenceSnoozeMinutes,
                e.autoSilenceMaxRings
            ),
            missions = row.missions.sortedBy { it.position }.map { MissionSpec(it.type, it.configVersion, it.config) },
            wakeUpCheck = WakeUpCheckConfig(e.wakeCheckEnabled, e.wakeCheckDelayMinutes, e.wakeCheckResponseSeconds),
            antiCheat = AntiCheatConfig(e.relaunchIfLeft, e.lockVolume, e.powerMenuGuard)
        )
    }

    fun daysToMask(days: Set<DayOfWeek>): Int = days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

    fun maskToDays(mask: Int): Set<DayOfWeek> = DayOfWeek.entries.filterTo(mutableSetOf()) {
        mask and (1 shl (it.value - 1)) != 0
    }
}
