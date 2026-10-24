package com.orthohin.alarmist.core.scheduling

import java.time.Instant
import javax.inject.Inject

/**
 * One snooze system alarm per alarm, independent of its regular ring. When it goes off,
 * `AlarmRinger.snoozeEnded` runs. The system forgets it on reboot; `AlarmRinger.restore` sets it again.
 */
interface SnoozeAlarms {
    /** Replaces any pending snooze of [alarmId]. */
    fun set(alarmId: Long, at: Instant)

    fun cancel(alarmId: Long)
}

internal class DefaultSnoozeAlarms @Inject constructor(private val systemAlarms: SystemAlarms) : SnoozeAlarms {
    override fun set(alarmId: Long, at: Instant) = systemAlarms.set(alarmId, at, AlarmKind.Snooze)

    override fun cancel(alarmId: Long) = systemAlarms.cancel(alarmId, AlarmKind.Snooze)
}
