package com.orthohin.alarmist.core.ringing

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * "Display over other apps": lets the ringing service bring the ringing screen back from the background
 * (`AntiCheatConfig.relaunchIfLeft`). Android blocks background activity starts without it.
 */
object OverlayPermission {
    fun isGranted(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun requestIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
}

/**
 * Closes the power menu while an alarm with `AntiCheatConfig.powerMenuGuard` rings. An accessibility
 * service, so only builds whose store allows it provide one (the app's fdroid flavor): inject
 * `Optional<PowerMenuGuard>`, empty elsewhere.
 */
interface PowerMenuGuard {
    /** The user turned the accessibility service on. */
    fun isEnabled(): Boolean

    /** The system's accessibility settings, where the user turns it on. */
    fun settingsIntent(): Intent
}
