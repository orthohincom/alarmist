package com.orthohin.alarmist.core.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.orthohin.alarmist.core.common.AppDispatchers
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal const val TAG = "Alarmist"

/** Receives the system alarm for one alarm: its regular ring or the end of a snooze. */
@AndroidEntryPoint
internal class AlarmFiredReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: DefaultAlarmScheduler

    @Inject lateinit var ringer: AlarmRinger

    @Inject lateinit var dispatchers: AppDispatchers

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1)
        if (alarmId < 0) return
        val triggerAt = intent.getLongExtra(EXTRA_TRIGGER_AT, -1).takeIf { it >= 0 }?.let(Instant::ofEpochMilli)
        if (intent.data?.host == AlarmKind.Snooze.uriHost) {
            Log.i(TAG, "Snooze of alarm $alarmId ended (due $triggerAt)")
            runAsync(dispatchers) { ringer.snoozeEnded(alarmId) }
        } else {
            Log.i(TAG, "Alarm $alarmId fired (due $triggerAt)")
            runAsync(dispatchers) { scheduler.onFired(alarmId, triggerAt) }
        }
    }

    companion object {
        private const val EXTRA_ALARM_ID = "alarm_id"
        private const val EXTRA_TRIGGER_AT = "trigger_at"

        fun intent(context: Context, alarmId: Long, kind: AlarmKind, triggerAt: Instant?): Intent =
            Intent(context, AlarmFiredReceiver::class.java)
                .setData(Uri.parse("alarmist://${kind.uriHost}/$alarmId"))
                .putExtra(EXTRA_ALARM_ID, alarmId)
                .apply { triggerAt?.let { putExtra(EXTRA_TRIGGER_AT, it.toEpochMilli()) } }
    }
}

/** Reschedules everything after a reboot, a clock or time zone change, an app update or a permission grant. */
@AndroidEntryPoint
internal class RescheduleReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: AlarmScheduler

    @Inject lateinit var dispatchers: AppDispatchers

    override fun onReceive(context: Context, intent: Intent) {
        val reason = ACTIONS[intent.action] ?: return
        Log.i(TAG, "Rescheduling all alarms: ${intent.action}")
        runAsync(dispatchers) { scheduler.rescheduleAll(reason) }
    }

    private companion object {
        val ACTIONS = mapOf(
            Intent.ACTION_LOCKED_BOOT_COMPLETED to RescheduleReason.Boot,
            Intent.ACTION_BOOT_COMPLETED to RescheduleReason.Boot,
            "android.intent.action.QUICKBOOT_POWERON" to RescheduleReason.Boot,
            "com.htc.intent.action.QUICKBOOT_POWERON" to RescheduleReason.Boot,
            Intent.ACTION_TIME_CHANGED to RescheduleReason.TimeChanged,
            Intent.ACTION_TIMEZONE_CHANGED to RescheduleReason.TimeChanged,
            Intent.ACTION_MY_PACKAGE_REPLACED to RescheduleReason.AppUpdated,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" to RescheduleReason.PermissionChanged
        )
    }
}

/** Runs [block] off the main thread and keeps the receiver alive until it finishes. Logs instead of crashing. */
private fun BroadcastReceiver.runAsync(dispatchers: AppDispatchers, block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + dispatchers.io).launch {
        try {
            block()
        } catch (e: Exception) {
            Log.e(TAG, "Receiver failed", e)
        } finally {
            pending.finish()
        }
    }
}
