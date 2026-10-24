package com.orthohin.alarmist.feature.reliability

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.orthohin.alarmist.core.ringing.OverlayPermission
import com.orthohin.alarmist.core.scheduling.ExactAlarmPermission
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** A setting that can stop alarms. [required]: alarms may not ring or show without it. */
internal enum class CheckKind(val required: Boolean) {
    Notifications(true),
    FullScreen(true),
    ExactAlarms(true),
    Background(true),
    Battery(false),

    /** "Display over other apps": the wake-up check comes to the front, anti-cheat brings the alarm back. */
    Overlay(false)
}

internal data class Check(val kind: CheckKind, val ok: Boolean)

/** Reads the settings that matter for alarms, and the system screens that change them. */
internal class ReliabilityChecks @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exactAlarms: ExactAlarmPermission
) {
    private val packageUri = Uri.fromParts("package", context.packageName, null)

    /** Only the checks that apply to this Android version. */
    fun current(): List<Check> = buildList {
        add(Check(CheckKind.Notifications, NotificationManagerCompat.from(context).areNotificationsEnabled()))
        if (Build.VERSION.SDK_INT >= 34) {
            val manager = context.getSystemService(NotificationManager::class.java)
            add(Check(CheckKind.FullScreen, manager.canUseFullScreenIntent()))
        }
        // From Android 13, USE_EXACT_ALARM is granted for good; only 12 lets the user revoke it.
        if (Build.VERSION.SDK_INT in 31..32) add(Check(CheckKind.ExactAlarms, exactAlarms.isGranted()))
        if (Build.VERSION.SDK_INT >= 28) {
            val activities = context.getSystemService(ActivityManager::class.java)
            add(Check(CheckKind.Background, !activities.isBackgroundRestricted))
        }
        val power = context.getSystemService(PowerManager::class.java)
        add(Check(CheckKind.Battery, power.isIgnoringBatteryOptimizations(context.packageName)))
        add(Check(CheckKind.Overlay, OverlayPermission.isGranted(context)))
    }

    /** Whether to ask for the notification permission in-app instead of opening settings. */
    fun canRequestNotificationPermission(): Boolean = Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED

    /** The system screen that fixes [kind]. */
    @SuppressLint("BatteryLife") // Only when the flavor declares the permission (fdroid); alarms are its use case.
    fun fixIntent(kind: CheckKind): Intent = when (kind) {
        CheckKind.Notifications ->
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

        CheckKind.FullScreen -> if (Build.VERSION.SDK_INT >= 34) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri)
        } else {
            appInfoIntent()
        }

        CheckKind.ExactAlarms -> exactAlarms.requestIntent() ?: appInfoIntent()

        CheckKind.Background -> appInfoIntent()

        CheckKind.Battery -> if (hasPermission(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
        } else {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        }

        CheckKind.Overlay -> OverlayPermission.requestIntent(context)
    }

    fun appInfoIntent(): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
