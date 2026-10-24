package com.orthohin.alarmist.feature.alarmlist

import com.orthohin.alarmist.core.model.Alarm
import com.orthohin.alarmist.core.model.Schedule
import com.orthohin.alarmist.core.model.nextTrigger
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

internal data class AlarmListUiState(
    val alarms: List<AlarmItem>,
    val now: Instant,
    /** The soonest ring of all enabled alarms, or null if none will ring. */
    val nextRing: Instant?
)

internal data class AlarmItem(
    val id: Long,
    val time: LocalTime,
    val label: String,
    /** Empty for a one-off alarm. */
    val days: Set<DayOfWeek>,
    val enabled: Boolean,
    /** The date of the next occurrence that is skipped, if the skip still applies. */
    val skipping: LocalDate?,
    val nextRing: Instant?
)

internal fun alarmListUiState(alarms: List<Alarm>, now: Instant, zone: ZoneId): AlarmListUiState {
    val items = alarms.map { alarm ->
        AlarmItem(
            id = alarm.id,
            time = alarm.time,
            label = alarm.label,
            days = (alarm.schedule as? Schedule.Weekly)?.days.orEmpty(),
            enabled = alarm.enabled,
            skipping = alarm.activeSkip(now, zone),
            nextRing = alarm.nextTrigger(now, zone)
        )
    }
    return AlarmListUiState(items, now, items.mapNotNull { it.nextRing }.minOrNull())
}

/** The skipped date, if it is the date the alarm would ring next without the skip. A stale skip is ignored. */
private fun Alarm.activeSkip(now: Instant, zone: ZoneId): LocalDate? {
    val skipped = skippedDate ?: return null
    if (!enabled) return null
    val unskipped = copy(skippedDate = null).nextTrigger(now, zone) ?: return null
    return skipped.takeIf { unskipped.atZone(zone).toLocalDate() == it }
}

/** [days] in [weekOrder]; all seven is "every day" (null). */
internal fun orderedDays(days: Set<DayOfWeek>, weekOrder: List<DayOfWeek>): List<DayOfWeek>? =
    if (days.size == 7) null else weekOrder.filter { it in days }

/** Turned on. A one-off alarm whose date has passed would never ring, so it rings at its next time instead. */
internal fun Alarm.turnedOn(now: Instant, zone: ZoneId): Alarm {
    val on = copy(enabled = true)
    return if (schedule is Schedule.Once &&
        on.nextTrigger(now, zone) == null
    ) {
        on.copy(schedule = Schedule.Once())
    } else {
        on
    }
}
