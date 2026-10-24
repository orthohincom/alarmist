package com.orthohin.alarmist.core.scheduling

/** Keeps the system's alarms in line with the database. */
interface AlarmScheduler {
    /**
     * Schedules the next ring of every enabled alarm and cancels the rest. Idempotent: the reschedule
     * receivers and app start call it, and calling it again changes nothing. First catches up on rings that
     * never came: up to [LATE_RING_MINUTES] late it rings now, later it reports the alarm as missed. Not after
     * a clock change: Android delivers the rings the clock jumped over itself.
     */
    suspend fun rescheduleAll(reason: RescheduleReason)

    companion object {
        const val LATE_RING_MINUTES = 10L
    }
}

/** Why everything was rescheduled; logged as the `Rescheduled` event's detail (by name: never rename). */
enum class RescheduleReason { Boot, TimeChanged, AppUpdated, PermissionChanged, AppStart }
