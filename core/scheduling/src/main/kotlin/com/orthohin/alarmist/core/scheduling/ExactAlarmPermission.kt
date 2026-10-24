package com.orthohin.alarmist.core.scheduling

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Whether alarms can ring at the exact minute. Only Android 12 (API 31–32) lets the user revoke it;
 * from 13 on, `USE_EXACT_ALARM` is granted to alarm clock apps. Without it, alarms fall back to inexact
 * (they may ring minutes late), so the UI should ask for it.
 */
interface ExactAlarmPermission {
    fun isGranted(): Boolean

    /** Opens the system screen to grant it, or null when there is nothing to grant (below Android 12). */
    fun requestIntent(): Intent?
}

internal class AndroidExactAlarmPermission @Inject constructor(@ApplicationContext private val context: Context) :
    ExactAlarmPermission {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun isGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    override fun requestIntent(): Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.fromParts("package", context.packageName, null))
    } else {
        null
    }
}
