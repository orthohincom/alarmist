package com.orthohin.alarmist.core.ringing

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.orthohin.alarmist.core.common.Clock
import com.orthohin.alarmist.core.data.EventLog
import com.orthohin.alarmist.core.model.AlarmEvent
import com.orthohin.alarmist.core.model.AlarmSound
import com.orthohin.alarmist.core.model.SoundConfig
import com.orthohin.alarmist.core.sounds.AlarmAudio
import com.orthohin.alarmist.core.sounds.SoundSources
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Loops one alarm sound on the alarm stream. Sets the stream to the alarm's volume while ringing and puts it
 * back afterwards. If the chosen sound can't play (deleted file, not readable before unlock), it plays the
 * backup, then the default bundled sound. One instance per [RingingService]; main thread only.
 */
internal class AlarmAudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sources: SoundSources,
    private val events: EventLog,
    private val clock: Clock
) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var player: MediaPlayer? = null
    private var fallbacks = emptyList<AlarmSound>()
    private var gain = 0f
    private var focus: AudioFocusRequest? = null
    private var savedVolume: Int? = null
    private var targetVolume: Int? = null
    private var alarmId = 0L
    private var chosen: AlarmSound? = null

    /** Starts at gain 0: raise it with [setGain]. Volume 0 means silent (vibration only). */
    fun start(config: SoundConfig, alarmId: Long) {
        stop()
        this.alarmId = alarmId
        chosen = config.sound
        if (config.volumePercent == 0) return
        setStreamVolume(config.volumePercent)
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AlarmAudio.ATTRIBUTES)
            .build()
            .also { audio.requestAudioFocus(it) }
        fallbacks = listOf(config.sound, config.backup, AlarmSound.Default).distinct()
        playNext()
    }

    fun setGain(value: Float) {
        gain = value
        player?.setVolume(value, value)
    }

    fun stop() {
        player?.release()
        player = null
        fallbacks = emptyList()
        focus?.let(audio::abandonAudioFocusRequest)
        focus = null
        savedVolume?.let { restore -> runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, restore, 0) } }
        savedVolume = null
        targetVolume = null
    }

    /** Puts the alarm stream back to the alarm's volume if something lowered it (anti-cheat `lockVolume`). */
    fun holdVolume() {
        val target = targetVolume ?: return
        val stream = AudioManager.STREAM_ALARM
        if (audio.getStreamVolume(stream) >= target) return
        Log.i(TAG, "Alarm volume was lowered, restoring it")
        runCatching { audio.setStreamVolume(stream, target, 0) }
    }

    /** Plays the first sound in [fallbacks] that works; a playback error later moves on to the next. */
    private fun playNext() {
        player?.release()
        player = null
        while (fallbacks.isNotEmpty()) {
            val sound = fallbacks.first()
            fallbacks = fallbacks.drop(1)
            val started = create(sound) ?: continue
            started.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "Playback of $sound failed ($what, $extra)")
                playNext()
                true
            }
            started.setVolume(gain, gain)
            started.start()
            player = started
            if (sound != chosen) events.add(AlarmEvent(clock.now(), AlarmEvent.Type.SoundFallback, alarmId))
            return
        }
        Log.e(TAG, "No alarm sound could play")
    }

    private fun create(sound: AlarmSound): MediaPlayer? {
        val mp = MediaPlayer()
        return try {
            mp.setAudioAttributes(AlarmAudio.ATTRIBUTES)
            sources.setDataSource(mp, sound)
            mp.isLooping = true
            mp.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            mp.prepare()
            mp
        } catch (e: Exception) {
            Log.w(TAG, "Can't play $sound", e)
            mp.release()
            null
        }
    }

    private fun setStreamVolume(percent: Int) {
        val stream = AudioManager.STREAM_ALARM
        val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) audio.getStreamMinVolume(stream) else 0
        try {
            if (savedVolume == null) savedVolume = audio.getStreamVolume(stream)
            val target = streamVolume(percent, min, audio.getStreamMaxVolume(stream))
            audio.setStreamVolume(stream, target, 0)
            targetVolume = target
        } catch (e: SecurityException) { // Some devices refuse while Do Not Disturb is on.
            Log.w(TAG, "Can't set the alarm volume", e)
        }
    }
}
