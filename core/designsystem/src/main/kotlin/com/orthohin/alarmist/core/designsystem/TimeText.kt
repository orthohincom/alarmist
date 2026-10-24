package com.orthohin.alarmist.core.designsystem

import android.content.res.Resources
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

/** Formats a time of day the way the system does (12 or 24 hours, per the user's setting and locale). */
@Composable
fun rememberTimeFormatter(): DateTimeFormatter {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val is24Hour = DateFormat.is24HourFormat(context)
    return remember(locale, is24Hour) { bestFormatter(locale, if (is24Hour) "Hm" else "hm") }
}

/** A formatter for [skeleton] (e.g. "EEEMMMd") in [locale]'s preferred order and punctuation. */
fun bestFormatter(locale: Locale, skeleton: String): DateTimeFormatter =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)

/** The seven days in [locale]'s week order (Sunday first in the US, Monday in most of Europe). */
fun weekOrder(locale: Locale): List<DayOfWeek> {
    val first = WeekFields.of(locale).firstDayOfWeek
    return (0L until 7L).map { first.plus(it) }
}

/** Time left until something rings, rounded up to whole minutes (a ring 30 s away is "1 minute"). */
data class Countdown(val days: Long, val hours: Int, val minutes: Int) {
    companion object {
        fun between(from: Instant, to: Instant): Countdown {
            val seconds = Duration.between(from, to).seconds.coerceAtLeast(0)
            val total = (seconds + 59) / 60
            return Countdown(total / MINUTES_PER_DAY, (total % MINUTES_PER_DAY / 60).toInt(), (total % 60).toInt())
        }

        private const val MINUTES_PER_DAY = 24 * 60
    }
}

/** "2 days 3 hours", "7 hours 12 minutes", "1 minute". Zero parts are left out. */
fun Resources.countdownText(countdown: Countdown): String {
    val parts = buildList {
        if (countdown.days >
            0
        ) {
            add(getQuantityString(R.plurals.ds_days, countdown.days.toInt(), countdown.days.toInt()))
        }
        if (countdown.hours > 0) add(getQuantityString(R.plurals.ds_hours, countdown.hours, countdown.hours))
        if (countdown.minutes > 0 || isEmpty()) {
            add(getQuantityString(R.plurals.ds_minutes, countdown.minutes, countdown.minutes))
        }
    }
    return parts.joinToString(" ")
}

/** "Rings in 7 hours 12 minutes". */
fun Resources.ringsInText(from: Instant, to: Instant): String =
    getString(R.string.ds_rings_in, countdownText(Countdown.between(from, to)))

/** "30 seconds", "2 minutes": for short settings such as the volume ramp. */
fun Resources.secondsText(seconds: Int): String = if (seconds >= 60 && seconds % 60 == 0) {
    getQuantityString(R.plurals.ds_minutes, seconds / 60, seconds / 60)
} else {
    getQuantityString(R.plurals.ds_seconds, seconds, seconds)
}
