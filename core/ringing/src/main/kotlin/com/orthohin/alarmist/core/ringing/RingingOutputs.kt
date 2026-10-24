package com.orthohin.alarmist.core.ringing

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import com.orthohin.alarmist.core.common.Clock
import com.orthohin.alarmist.core.data.EventLog
import com.orthohin.alarmist.core.model.Alarm
import com.orthohin.alarmist.core.model.AlarmEvent
import com.orthohin.alarmist.core.model.MissReason
import com.orthohin.alarmist.core.model.RingSession
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject

internal const val TAG = "Alarmist"

/** The controller's effects on the device, behind an interface so the controller is unit-tested. */
internal interface RingingOutputs {
    /** Starts [RingingService], which plays whatever session is current. Safe to call while it runs. */
    fun startRinging(session: RingSession)

    /** [alarm] null means unknown (then Dismiss is offered, as on the ringing notification). */
    fun showSnoozed(session: RingSession, alarm: Alarm?)

    /** A wake-up check: when it will ask (`CheckPending`), or the question itself (`Checking`). */
    fun showCheck(session: RingSession, label: String)

    /** The alarm stopped after its last unanswered ring. */
    fun showMissed(session: RingSession, label: String)

    /** A ring that never came (phone off, app stopped). */
    fun showMissedRing(alarmId: Long, label: String, due: Instant, reason: MissReason)

    /** Removes the alarm's snoozed, fallback or missed notification. */
    fun clear(alarmId: Long)
}

internal class AndroidRingingOutputs @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notifications: RingingNotifications,
    private val wakeLock: RingingWakeLock,
    private val events: EventLog,
    private val clock: Clock
) : RingingOutputs {
    override fun startRinging(session: RingSession) {
        // The broadcast's wake lock ends with the receiver; this one bridges the gap until the service runs.
        wakeLock.acquire()
        try {
            ContextCompat.startForegroundService(context, RingingService.intent(context))
        } catch (e: IllegalStateException) { // ForegroundServiceStartNotAllowedException (Android 12+)
            fallback(session, e)
        } catch (e: SecurityException) {
            fallback(session, e)
        }
    }

    /** No service, so no player: a notification that repeats the bundled sound until it's answered. */
    private fun fallback(session: RingSession, e: Exception) {
        Log.e(TAG, "Can't start the ringing service, using a notification for alarm ${session.alarmId}", e)
        notifications.showFallback(session)
        events.add(AlarmEvent(clock.now(), AlarmEvent.Type.RingingFallback, session.alarmId))
    }

    override fun showSnoozed(session: RingSession, alarm: Alarm?) =
        notifications.showSnoozed(session, alarm?.label.orEmpty(), canDismiss = alarm?.missions.isNullOrEmpty())

    override fun showCheck(session: RingSession, label: String) {
        notifications.showCheck(session, label)
        // While the phone is in use the full-screen intent is only a heads-up; with "display over other apps"
        // (anti-cheat) the "Still awake?" card can come to the front anyway.
        if (session.state is RingSession.State.Checking && OverlayPermission.isGranted(context)) {
            try {
                context.startActivity(notifications.ringingScreen())
            } catch (e: RuntimeException) {
                Log.w(TAG, "Can't show the wake-up check", e)
            }
        }
    }

    override fun showMissed(session: RingSession, label: String) = notifications.showMissed(session, label)

    override fun showMissedRing(alarmId: Long, label: String, due: Instant, reason: MissReason) =
        notifications.showMissedRing(alarmId, label, due, reason)

    override fun clear(alarmId: Long) = notifications.cancel(alarmId)
}
