package com.orthohin.alarmist.core.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject

/**
 * `setAlarmClock`: exempt from Doze and app standby, and shows the alarm icon in the status bar. Without the
 * exact-alarm permission (only revocable on Android 12) it falls back to an inexact, Doze-allowed alarm.
 */
internal class AndroidSystemAlarms @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permission: ExactAlarmPermission
) : SystemAlarms {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun set(alarmId: Long, triggerAt: Instant, kind: AlarmKind) {
        val operation = firePendingIntent(alarmId, kind, triggerAt, PendingIntent.FLAG_UPDATE_CURRENT)!!
        val millis = triggerAt.toEpochMilli()
        try {
            if (permission.isGranted()) {
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(millis, showIntent()), operation)
                Log.i(TAG, "Scheduled $kind $alarmId at $triggerAt")
                return
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm permission revoked", e)
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation)
        Log.w(TAG, "Scheduled $kind $alarmId at $triggerAt (inexact: no exact-alarm permission)")
    }

    override fun cancel(alarmId: Long, kind: AlarmKind) {
        val operation = firePendingIntent(alarmId, kind, null, PendingIntent.FLAG_NO_CREATE) ?: return
        alarmManager.cancel(operation)
        operation.cancel()
        Log.i(TAG, "Cancelled $kind $alarmId")
    }

    /** One per alarm and kind: the data URI makes the intents distinct (extras don't count). */
    private fun firePendingIntent(alarmId: Long, kind: AlarmKind, triggerAt: Instant?, flag: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            0,
            AlarmFiredReceiver.intent(context, alarmId, kind, triggerAt),
            flag or PendingIntent.FLAG_IMMUTABLE
        )

    /** Where tapping the alarm in the system UI leads: the app. */
    private fun showIntent(): PendingIntent? = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.let { PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE) }
}
