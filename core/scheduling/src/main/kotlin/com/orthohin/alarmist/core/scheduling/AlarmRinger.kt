package com.orthohin.alarmist.core.scheduling

import com.orthohin.alarmist.core.model.MissReason
import java.time.Instant

/** Rings alarms. Implemented by `core:ringing`; core:scheduling calls it. */
interface AlarmRinger {
    /** An alarm went off. Called on the receiver's goAsync coroutine: must start ringing promptly. */
    suspend fun ring(alarmId: Long)

    /** A snooze set with [SnoozeAlarms] is over. */
    suspend fun snoozeEnded(alarmId: Long)

    /**
     * Called after every `rescheduleAll` (reboot, clock change, app start): re-arms snoozes (the system forgets
     * them on reboot) and resumes a ring that a reboot or process death cut off. Idempotent.
     */
    suspend fun restore()

    /** The ring due at [due] never came and is too late to catch up: tell the user. */
    suspend fun missed(alarmId: Long, due: Instant, reason: MissReason)
}
