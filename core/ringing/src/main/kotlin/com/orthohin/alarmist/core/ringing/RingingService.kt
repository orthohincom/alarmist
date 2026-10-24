package com.orthohin.alarmist.core.ringing

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import com.orthohin.alarmist.core.common.Clock
import com.orthohin.alarmist.core.model.Alarm
import com.orthohin.alarmist.core.model.AntiCheatConfig
import com.orthohin.alarmist.core.model.AutoSilenceConfig
import com.orthohin.alarmist.core.model.RingSession
import com.orthohin.alarmist.core.model.SoundConfig
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Plays the current ring session (sound with ramp-up, vibration) as a foreground service, and stops itself
 * when nothing is ringing. It follows [DefaultRingingController.sessions], so callers only change state and
 * call [intent] to make sure it runs. Sticky: if the system kills it, it comes back and resumes.
 */
@AndroidEntryPoint
internal class RingingService : Service() {
    @Inject lateinit var controller: DefaultRingingController

    @Inject lateinit var notifications: RingingNotifications

    @Inject lateinit var wakeLock: RingingWakeLock

    @Inject lateinit var player: AlarmAudioPlayer

    @Inject lateinit var vibrator: AlarmVibrator

    @Inject lateinit var clock: Clock

    private val scope = MainScope()
    private var watcher: Job? = null
    private var alarm: Alarm? = null
    private var lastRelaunch = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // First, within the 5 s the system allows, before anything that could fail or wait.
        val session = controller.sessions.value.current
        val notification = notifications.ringing(session, alarm?.takeIf { it.id == session?.alarmId })
        ServiceCompat.startForeground(this, RingingNotifications.RINGING_ID, notification, FOREGROUND_TYPE)
        if (watcher == null) watcher = scope.launch { watch() }
        return START_STICKY
    }

    private suspend fun watch() {
        // Keyed on the ring itself, so mission progress (mute) doesn't restart the sound and its ramp.
        controller.sessions.map { it.current }.distinctUntilChanged { a, b ->
            a?.alarmId == b?.alarmId && a?.state == b?.state
        }.collectLatest { session ->
            when {
                session != null -> ring(session)

                // A newer alarm may have started ringing since this value was emitted.
                controller.sessions.value.current == null -> stop()
            }
        }
    }

    private suspend fun ring(session: RingSession) {
        wakeLock.acquire()
        Log.i(TAG, "Ringing alarm ${session.alarmId}")
        val alarm = controller.alarmOrNull(session.alarmId).also { alarm = it }
        val sound = alarm?.sound ?: SoundConfig()
        notifications.showRinging(session, alarm)
        player.start(sound, session.alarmId)
        vibrator.stop()
        // Auto-silence counts audible time from when it started playing here, so an alarm that waited behind
        // another gets its full time, and a mission's mute pauses it. After process death the count starts
        // again (rings a little longer, never shorter).
        val antiCheat = alarm?.antiCheat ?: AntiCheatConfig()
        val silence = alarm?.autoSilence ?: AutoSilenceConfig()
        val limit = silence.ringMinutes * 60_000L
        var audibleMs = 0L
        var fullVolume = false
        var muted = false
        var vibrating = false
        while (true) {
            val current = controller.sessions.value[session.alarmId] ?: return
            if (antiCheat.lockVolume) player.holdVolume()
            if (antiCheat.relaunchIfLeft) keepScreenInFront()
            if (!current.isAudible(clock.now())) {
                if (!muted) {
                    player.setGain(0f)
                    vibrator.stop()
                    vibrating = false
                    muted = true
                }
            } else {
                if (muted) {
                    // Back from a mission's mute (idle or left): full volume at once, no ramp.
                    muted = false
                    fullVolume = true
                }
                if (sound.vibrate && !vibrating) {
                    vibrator.start()
                    vibrating = true
                }
                val gain = if (fullVolume) 1f else rampGain(audibleMs, sound.rampUpSeconds)
                fullVolume = gain >= 1f
                player.setGain(gain)
                if (silence.enabled && audibleMs >= limit) {
                    // Not cancellable: the state change it makes cancels this collectLatest block.
                    withContext(NonCancellable) { controller.timedOut(session.alarmId) }
                    return
                }
                audibleMs += TICK_MS
            }
            delay(TICK_MS)
        }
    }

    /**
     * Brings the ringing screen back if it isn't in front while the screen is on (pressing power isn't leaving:
     * the screen is still on top when it comes back on). Needs "display over other apps" on Android 10+.
     */
    private fun keepScreenInFront() {
        if (controller.isScreenVisible || !getSystemService(PowerManager::class.java).isInteractive) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastRelaunch < RELAUNCH_MS) return
        lastRelaunch = now
        try {
            startActivity(notifications.ringingScreen())
        } catch (e: RuntimeException) {
            Log.w(TAG, "Can't bring the ringing screen back", e)
        }
    }

    private fun stop() {
        Log.i(TAG, "Nothing ringing, stopping")
        player.stop()
        vibrator.stop()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        wakeLock.release()
    }

    override fun onDestroy() {
        scope.cancel()
        player.stop()
        vibrator.stop()
        wakeLock.release()
        super.onDestroy()
    }

    companion object {
        private const val TICK_MS = 250L
        private const val RELAUNCH_MS = 2_000L
        private val FOREGROUND_TYPE =
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED else 0

        fun intent(context: Context) = Intent(context, RingingService::class.java)
    }
}
