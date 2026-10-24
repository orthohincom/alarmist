package com.orthohin.alarmist.core.model

import java.time.Instant

/**
 * One entry of the local event log, for the reliability screen. Never leaves the device.
 * [due] is when the alarm was meant to ring (for [Type.Scheduled], [Type.Fired], [Type.Missed]).
 * [detail] is a token whose meaning depends on [type] (a [MissReason] name, a reschedule reason).
 */
data class AlarmEvent(
    val at: Instant,
    val type: Type,
    val alarmId: Long? = null,
    val due: Instant? = null,
    val detail: String = ""
) {
    /** Stored by name: never rename an entry. */
    enum class Type {
        /** The next ring was booked (logged only when it changes). */
        Scheduled,

        /** The system alarm went off, or a missed ring was caught up late (then [due] is well before [at]). */
        Fired,
        Snoozed,
        Dismissed,

        /** Nobody answered within the ring time; quiet until the next ring. */
        AutoSilenced,

        /** Stopped after the last unanswered ring. */
        Unanswered,

        /** The ring never came: the phone was off or Android stopped the app ([detail] is a [MissReason]). */
        Missed,

        /** Every alarm was booked again ([detail]: boot, time, update, permission, start). */
        Rescheduled,

        /** The ringing service couldn't start; a notification rang instead. */
        RingingFallback,

        /** The chosen sound couldn't play; the backup did. */
        SoundFallback,

        /** The user answered the wake-up check. */
        Awake,

        /** Nobody answered the wake-up check, so the alarm rang again. */
        NotAwake
    }
}

/** Why an expected ring never came. */
enum class MissReason {
    /** The phone was off (or restarting) at the time. */
    PhoneOff,

    /** The phone was on, so Android (or a task killer or a force stop) stopped the app and its alarms. */
    AppStopped
}
