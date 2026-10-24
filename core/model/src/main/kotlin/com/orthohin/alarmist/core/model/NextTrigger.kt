package com.orthohin.alarmist.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The first instant strictly after [after] at which this alarm rings in [zone], or null if it won't ring
 * (disabled, or a one-off date that has passed or is skipped). Each eligible local date has one occurrence:
 * - Occurrences on [Alarm.skippedDate] are skipped.
 * - DST gap (the time doesn't exist that day, e.g. 02:30 when clocks jump 02:00→03:00): rings when the gap
 *   ends (03:00). Ringing early beats ringing an hour late.
 * - DST overlap (the time happens twice, e.g. 01:30 when clocks fall back 02:00→01:00): rings once, on the
 *   first pass. If [after] is already past the transition (the clock has been turned back), the second pass
 *   is the next one, so an alarm set during the repeated hour still rings. Rescheduling inside the repeated
 *   hour after the first pass rang therefore rings again; an extra ring beats a missed one.
 *
 * Only Java 8 time APIs here: minSdk 26 has no desugaring.
 */
fun Alarm.nextTrigger(after: Instant, zone: ZoneId): Instant? = nextOccurrence(after, zone)?.instant

/** Skips only the next occurrence; later ones still ring. Unchanged if the alarm has no next occurrence. */
fun Alarm.skippingNext(after: Instant, zone: ZoneId): Alarm {
    val next = copy(skippedDate = null).nextOccurrence(after, zone) ?: return this
    return copy(skippedDate = next.date)
}

private class Occurrence(val date: LocalDate, val instant: Instant)

private fun Alarm.nextOccurrence(after: Instant, zone: ZoneId): Occurrence? {
    if (!enabled) return null
    val today = after.atZone(zone).toLocalDate()
    val days = generateSequence(today) { it.plusDays(1) }
    val dates = when (val s = schedule) {
        is Schedule.Once -> s.date?.let { sequenceOf(it) } ?: days.take(ONCE_LOOKAHEAD_DAYS)
        is Schedule.Weekly -> days.take(WEEKLY_LOOKAHEAD_DAYS).filter { it.dayOfWeek in s.days }
    }
    return dates
        .filter { it != skippedDate }
        .map { Occurrence(it, ringInstant(it, time, zone, after)) }
        .firstOrNull { it.instant > after }
}

private fun ringInstant(date: LocalDate, time: LocalTime, zone: ZoneId, after: Instant): Instant {
    val local = date.atTime(time)
    val transition = zone.rules.getTransition(local) ?: return local.atZone(zone).toInstant()
    return when {
        transition.isGap -> transition.instant
        after >= transition.instant -> local.atOffset(transition.offsetAfter).toInstant()
        else -> local.atOffset(transition.offsetBefore).toInstant()
    }
}

// Today may have passed and tomorrow may be skipped.
private const val ONCE_LOOKAHEAD_DAYS = 3

// Today may have passed and the same weekday next week may be skipped.
private const val WEEKLY_LOOKAHEAD_DAYS = 15
