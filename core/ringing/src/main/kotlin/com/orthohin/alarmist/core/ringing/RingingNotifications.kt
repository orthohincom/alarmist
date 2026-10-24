package com.orthohin.alarmist.core.ringing

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.orthohin.alarmist.core.model.Alarm
import com.orthohin.alarmist.core.model.MissReason
import com.orthohin.alarmist.core.model.RingSession
import com.orthohin.alarmist.core.model.SnoozeConfig
import com.orthohin.alarmist.core.sounds.AlarmAudio
import com.orthohin.alarmist.core.sounds.BundledSounds
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.util.Date
import javax.inject.Inject

/**
 * Channels and notifications. The ringing channel is silent: [RingingService] plays the sound itself. The
 * fallback channel plays the bundled sound, for when the service can't start.
 */
internal class RingingNotifications @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    /** The foreground service's notification. [alarm] null means unknown (not loaded yet, or the DB failed). */
    fun ringing(session: RingSession?, alarm: Alarm?): Notification = ringingBuilder(RINGING_CHANNEL, session, alarm)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .build()

    fun showRinging(session: RingSession, alarm: Alarm?) = notify(null, RINGING_ID, ringing(session, alarm))

    fun showFallback(session: RingSession) {
        val notification = ringingBuilder(FALLBACK_CHANNEL, session, null).build()
        notification.flags = notification.flags or Notification.FLAG_INSISTENT
        notify(ALARM_TAG, session.alarmId.hashCode(), notification)
    }

    /** [canDismiss] false for alarms with missions: snoozing mustn't become a way around them. */
    fun showSnoozed(session: RingSession, label: String, canDismiss: Boolean) {
        val until = (session.state as? RingSession.State.Snoozed)?.until ?: return
        ensureChannels()
        val notification = NotificationCompat.Builder(context, SNOOZED_CHANNEL)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(label.ifBlank { context.getString(R.string.alarm_default_title) })
            .setContentText(context.getString(R.string.snoozed_until, time(until)))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setShowWhen(false)
            .apply {
                if (canDismiss) {
                    addAction(
                        0,
                        context.getString(R.string.action_dismiss),
                        action(RingingActionReceiver.ACTION_DISMISS, session)
                    )
                }
            }
            .build()
        notify(ALARM_TAG, session.alarmId.hashCode(), notification)
    }

    /**
     * A wake-up check. Waiting: a quiet note of when it will ask. Asking: an alerting notification (alarm
     * audio, so it's heard like an alarm) counting down to the re-ring; its button or a tap answers it.
     */
    fun showCheck(session: RingSession, label: String) {
        ensureChannels()
        val builder = when (val st = session.state) {
            is RingSession.State.CheckPending -> NotificationCompat.Builder(context, SNOOZED_CHANNEL)
                .setContentTitle(label.ifBlank { context.getString(R.string.alarm_default_title) })
                .setContentText(context.getString(R.string.check_at, time(st.askAt)))
                .setShowWhen(false)
                // Opens the "Stay awake" countdown.
                .setContentIntent(showRingingIntent())

            is RingSession.State.Checking -> {
                val awake = action(RingingActionReceiver.ACTION_AWAKE, session)
                // Posted fresh, not as an update of the quiet one, so it alerts like a new notification.
                cancel(session.alarmId)
                NotificationCompat.Builder(context, CHECK_CHANNEL)
                    .setContentTitle(context.getString(R.string.check_title))
                    .setContentText(
                        if (label.isBlank()) {
                            context.getString(R.string.check_text)
                        } else {
                            context.getString(R.string.check_text_label, label)
                        }
                    )
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setWhen(st.until.toEpochMilli())
                    .setShowWhen(true)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    // Over the lock screen (or a heads-up while the phone is in use): the "Still awake?" card.
                    .setContentIntent(showRingingIntent())
                    .setFullScreenIntent(showRingingIntent(), true)
                    .addAction(0, context.getString(R.string.action_awake), awake)
            }

            else -> return
        }
        val notification = builder
            .setSmallIcon(R.drawable.ic_alarm)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .build()
        notify(ALARM_TAG, session.alarmId.hashCode(), notification)
    }

    fun showMissed(session: RingSession, label: String) {
        ensureChannels()
        val notification = NotificationCompat.Builder(context, MISSED_CHANNEL)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(label.ifBlank { context.getString(R.string.alarm_default_title) })
            .setContentText(context.getString(R.string.missed_at, time(session.firedAt)))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .build()
        notify(ALARM_TAG, session.alarmId.hashCode(), notification)
    }

    /** A ring that never came (found by core:scheduling). Opens the reliability check. */
    fun showMissedRing(alarmId: Long, label: String, due: Instant, reason: MissReason) {
        ensureChannels()
        val text = context.getString(
            if (reason == MissReason.PhoneOff) R.string.missed_phone_off else R.string.missed_app_stopped,
            time(due)
        )
        val notification = NotificationCompat.Builder(context, MISSED_CHANNEL)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(label.ifBlank { context.getString(R.string.alarm_default_title) })
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(activityIntent(RingingController.ACTION_SHOW_RELIABILITY))
            .setAutoCancel(true)
            .build()
        notify(ALARM_TAG, alarmId.hashCode(), notification)
    }

    fun cancel(alarmId: Long) = manager.cancel(ALARM_TAG, alarmId.hashCode())

    private fun ringingBuilder(channel: String, session: RingSession?, alarm: Alarm?): NotificationCompat.Builder {
        ensureChannels()
        val show = showRingingIntent()
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(alarm?.label?.ifBlank { null } ?: context.getString(R.string.alarm_default_title))
            .setContentText(session?.let { time(it.firedAt) })
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(show)
            .setFullScreenIntent(show, true)
            .apply {
                if (session == null) return@apply
                if (session.canSnooze(alarm?.snooze ?: SnoozeConfig())) {
                    addAction(
                        0,
                        context.getString(R.string.action_snooze),
                        action(RingingActionReceiver.ACTION_SNOOZE, session)
                    )
                }
                // With missions, only solving them dismisses. Unknown alarm: allow it rather than trap the user.
                if (alarm?.missions.isNullOrEmpty()) {
                    addAction(
                        0,
                        context.getString(R.string.action_dismiss),
                        action(RingingActionReceiver.ACTION_DISMISS, session)
                    )
                }
            }
    }

    private fun showRingingIntent(): PendingIntent =
        activityIntent(RingingController.ACTION_SHOW_RINGING, Intent.FLAG_ACTIVITY_NO_USER_ACTION)

    /** The ringing screen, for starting it directly (anti-cheat relaunch). */
    fun ringingScreen(): Intent =
        launchIntent(RingingController.ACTION_SHOW_RINGING, Intent.FLAG_ACTIVITY_NO_USER_ACTION)

    private fun activityIntent(action: String, flags: Int = 0): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        launchIntent(action, flags),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /** An activity of this app handling [action], or the launcher activity if none does. */
    private fun launchIntent(action: String, flags: Int): Intent {
        val wanted = Intent(action).setPackage(context.packageName)
        val intent = if (context.packageManager.resolveActivity(wanted, 0) != null) {
            wanted
        } else {
            context.packageManager.getLaunchIntentForPackage(context.packageName) ?: wanted
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or flags)
    }

    private fun action(action: String, session: RingSession): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        RingingActionReceiver.intent(context, action, session.alarmId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun time(instant: Instant): String = DateFormat.getTimeFormat(context).format(Date(instant.toEpochMilli()))

    /** Idempotent. Channel settings can't change after creation; a change needs a new channel id. */
    private fun ensureChannels() {
        val sound = Uri.parse("android.resource://${context.packageName}/${BundledSounds.default.resource}")
        // The channel's sound URI holds a resource id, which can change between builds: a new id gets a new channel.
        manager.notificationChannelsCompat
            .filter { it.id.startsWith(FALLBACK_CHANNEL_PREFIX) && it.id != FALLBACK_CHANNEL }
            .forEach { manager.deleteNotificationChannel(it.id) }
        manager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(RINGING_CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName(context.getString(R.string.ringing_channel))
                    .setSound(null, null)
                    .setVibrationEnabled(false)
                    .build(),
                NotificationChannelCompat.Builder(FALLBACK_CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName(context.getString(R.string.ringing_fallback_channel))
                    .setDescription(context.getString(R.string.ringing_fallback_channel_description))
                    .setSound(sound, AlarmAudio.ATTRIBUTES)
                    .setVibrationEnabled(true)
                    .build(),
                NotificationChannelCompat.Builder(SNOOZED_CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(context.getString(R.string.snoozed_channel))
                    .build(),
                NotificationChannelCompat.Builder(CHECK_CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName(context.getString(R.string.check_channel))
                    .setSound(Settings.System.DEFAULT_NOTIFICATION_URI, AlarmAudio.ATTRIBUTES)
                    .setVibrationEnabled(true)
                    .build(),
                NotificationChannelCompat.Builder(MISSED_CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.missed_channel))
                    .build()
            )
        )
    }

    @SuppressLint("MissingPermission") // Checked by canNotify().
    private fun notify(tag: String?, id: Int, notification: Notification) {
        if (canNotify()) manager.notify(tag, id, notification) else Log.w(TAG, "Notifications not allowed")
    }

    private fun canNotify(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    companion object {
        const val RINGING_ID = 1
        private const val ALARM_TAG = "alarm"
        private const val RINGING_CHANNEL = "ringing"
        private const val FALLBACK_CHANNEL_PREFIX = "ringing_fallback"
        private val FALLBACK_CHANNEL = "${FALLBACK_CHANNEL_PREFIX}_${BundledSounds.default.resource}"
        private const val SNOOZED_CHANNEL = "snoozed"
        private const val MISSED_CHANNEL = "missed"
        private const val CHECK_CHANNEL = "wake_up_check"
    }
}
