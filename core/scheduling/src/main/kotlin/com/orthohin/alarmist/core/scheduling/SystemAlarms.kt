package com.orthohin.alarmist.core.scheduling

import java.time.Instant

/** What a system alarm is for. Each alarm id has at most one pending system alarm of each kind. */
internal enum class AlarmKind(val uriHost: String) {
    /** The alarm's regular ring. */
    Ring("alarm"),

    /** The end of a snooze. */
    Snooze("snooze")
}

/** The system alarm service. Behind an interface so the logic is unit-tested. */
internal interface SystemAlarms {
    /** Replaces any pending alarm for [alarmId] of the same [kind]. */
    fun set(alarmId: Long, triggerAt: Instant, kind: AlarmKind = AlarmKind.Ring)

    fun cancel(alarmId: Long, kind: AlarmKind = AlarmKind.Ring)
}
