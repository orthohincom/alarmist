package com.orthohin.alarmist.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * A complete alarm. Every feature's fields are here from the start, with defaults,
 * so the database schema doesn't need to change for them.
 *
 * [time] is wall-clock time: the alarm follows the device's current time zone.
 */
data class Alarm(
    val id: Long = NEW_ID,
    val time: LocalTime,
    val schedule: Schedule = Schedule.Once(),
    val enabled: Boolean = true,
    val label: String = "",
    /** The occurrence on this local date doesn't ring. A date in the past has no effect. */
    val skippedDate: LocalDate? = null,
    val sound: SoundConfig = SoundConfig(),
    val snooze: SnoozeConfig = SnoozeConfig(),
    val autoSilence: AutoSilenceConfig = AutoSilenceConfig(),
    /** Solved in order to dismiss the alarm. Empty means a plain dismiss button. */
    val missions: List<MissionSpec> = emptyList(),
    val wakeUpCheck: WakeUpCheckConfig = WakeUpCheckConfig(),
    val antiCheat: AntiCheatConfig = AntiCheatConfig()
) {
    init {
        require(time.second == 0 && time.nano == 0) { "Alarm time must be whole minutes: $time" }
        require(missions.size <= MAX_MISSIONS) { "At most $MAX_MISSIONS missions, got ${missions.size}" }
    }

    companion object {
        /** The id of an alarm that hasn't been saved yet. */
        const val NEW_ID = 0L
        const val MAX_MISSIONS = 5
    }
}

sealed interface Schedule {
    /**
     * Rings once, then the alarm is turned off. With [date] null it rings at the next occurrence of the time;
     * otherwise only on [date].
     */
    data class Once(val date: LocalDate? = null) : Schedule

    /** Rings on each of [days] every week. */
    data class Weekly(val days: Set<DayOfWeek>) : Schedule {
        init {
            require(days.isNotEmpty()) { "A weekly schedule needs at least one day" }
        }
    }
}
