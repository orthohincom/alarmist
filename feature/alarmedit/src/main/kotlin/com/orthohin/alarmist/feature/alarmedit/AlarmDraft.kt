package com.orthohin.alarmist.feature.alarmedit

import com.orthohin.alarmist.core.model.Alarm
import com.orthohin.alarmist.core.model.AlarmSound
import com.orthohin.alarmist.core.model.MissionSpec
import com.orthohin.alarmist.core.model.Schedule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** The lowest volume allowed without vibration, so an alarm can never be silent. */
internal const val MIN_AUDIBLE_VOLUME = 5

internal val RAMP_UP_SECONDS = listOf(0, 15, 30, 60, 120, 300)
internal val SNOOZE_MINUTES = listOf(1, 3, 5, 10, 15, 20, 30)

/** Null is unlimited. */
internal val SNOOZE_MAX_COUNTS = listOf(1, 2, 3, 5, 10, null)

internal val CHECK_DELAY_MINUTES = listOf(1, 3, 5, 10, 15, 30)
internal val CHECK_RESPONSE_SECONDS = listOf(30, 60, 100, 180, 300)

/** A new alarm starts at the next whole hour, once, with the model's defaults. */
internal fun newAlarm(now: Instant, zone: ZoneId): Alarm =
    Alarm(time = now.atZone(zone).toLocalTime().truncatedTo(ChronoUnit.HOURS).plusHours(1))

internal fun Alarm.withTime(hour: Int, minute: Int): Alarm = copy(time = LocalTime.of(hour, minute))

/** The weekly days, or empty for a one-off alarm. */
internal val Alarm.days: Set<DayOfWeek>
    get() = (schedule as? Schedule.Weekly)?.days.orEmpty()

internal fun Alarm.toggleDay(day: DayOfWeek): Alarm {
    val days = if (day in days) days - day else days + day
    return copy(schedule = if (days.isEmpty()) Schedule.Once() else Schedule.Weekly(days))
}

internal val Alarm.isDaily: Boolean
    get() = days.size == DayOfWeek.entries.size

/** "Daily" on rings every day; off makes it a one-off alarm. */
internal fun Alarm.withDaily(on: Boolean): Alarm =
    copy(schedule = if (on) Schedule.Weekly(DayOfWeek.entries.toSet()) else Schedule.Once())

/** Rounds a slider position to the 5 % steps the volume uses. */
internal fun volumeStep(position: Float): Int = (Math.round(position / 5f) * 5).coerceIn(0, 100)

/** Without vibration the volume can't go below [MIN_AUDIBLE_VOLUME]; 0 with vibration is "vibrate only". */
internal fun Alarm.withVolume(percent: Int): Alarm {
    val volume = if (sound.vibrate) percent.coerceIn(0, 100) else percent.coerceIn(MIN_AUDIBLE_VOLUME, 100)
    return copy(sound = sound.copy(volumePercent = volume))
}

/** A bundled sound also becomes the backup, so the backup is the last bundled sound the user chose. */
internal fun Alarm.withSound(choice: AlarmSound): Alarm = copy(
    sound = sound.copy(sound = choice, backup = choice as? AlarmSound.Bundled ?: sound.backup)
)

internal val Alarm.canAddMission: Boolean
    get() = missions.size < Alarm.MAX_MISSIONS

/** Replaces the mission at [index], or adds [spec] at the end when [index] is null (if there is room). */
internal fun Alarm.withMission(index: Int?, spec: MissionSpec): Alarm = when {
    index != null && index in missions.indices -> copy(missions = missions.toMutableList().also { it[index] = spec })
    index == null && canAddMission -> copy(missions = missions + spec)
    else -> this
}

internal fun Alarm.withoutMission(index: Int): Alarm =
    if (index in missions.indices) copy(missions = missions.filterIndexed { i, _ -> i != index }) else this

/** Vibration can't be turned off while the volume is 0: the alarm would be silent. */
internal val Alarm.canTurnOffVibration: Boolean
    get() = sound.volumePercent > 0

internal fun Alarm.withVibrate(on: Boolean): Alarm =
    if (!on && !canTurnOffVibration) this else copy(sound = sound.copy(vibrate = on))

/**
 * What gets saved: the alarm is turned on, a one-off alarm rings at the next occurrence of its time (any old
 * date is dropped), and a skip survives only if the time and days are unchanged.
 */
internal fun Alarm.forSave(original: Alarm?): Alarm {
    val keepSkip = original != null && original.time == time && original.schedule == schedule
    return copy(
        enabled = true,
        label = label.trim(),
        schedule = if (schedule is Schedule.Once) Schedule.Once() else schedule,
        skippedDate = skippedDate.takeIf { keepSkip }
    )
}
