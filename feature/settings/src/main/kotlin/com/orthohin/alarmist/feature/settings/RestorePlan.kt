package com.orthohin.alarmist.feature.settings

import com.orthohin.alarmist.core.model.Alarm
import com.orthohin.alarmist.core.model.nextTrigger
import java.time.Instant
import java.time.ZoneId

/**
 * The alarms to add from a backup. Restoring never changes or deletes an alarm: one that is already here
 * (same settings, ignoring on/off and a skip) is left out, so restoring twice adds nothing. Each stored alarm
 * matches one backup alarm, so duplicates in the backup are kept. An alarm that is on but can never ring
 * again (a one-off whose date has passed) is restored off.
 */
internal fun alarmsToRestore(existing: List<Alarm>, backup: List<Alarm>, now: Instant, zone: ZoneId): List<Alarm> {
    val unmatched = existing.mapTo(mutableListOf()) { it.restoreKey() }
    return backup.filterNot { unmatched.remove(it.restoreKey()) }.map { alarm ->
        val fresh = alarm.copy(id = Alarm.NEW_ID)
        if (fresh.enabled && fresh.nextTrigger(now, zone) == null) fresh.copy(enabled = false) else fresh
    }
}

private fun Alarm.restoreKey() = copy(id = Alarm.NEW_ID, enabled = true, skippedDate = null)
