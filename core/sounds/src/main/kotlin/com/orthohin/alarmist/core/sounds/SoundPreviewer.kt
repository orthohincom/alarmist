package com.orthohin.alarmist.core.sounds

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import com.orthohin.alarmist.core.model.AlarmSound
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Loops one sound on the alarm stream at its current volume, for the sound picker. Not for ringing: no
 * fallbacks, no wake lock. Main thread only; call [stop] when the picker goes away.
 */
class SoundPreviewer @Inject constructor(@ApplicationContext context: Context, private val sources: SoundSources) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(AlarmAudio.ATTRIBUTES)
        .build()
    private var player: MediaPlayer? = null
    private val current = MutableStateFlow<AlarmSound?>(null)

    /** The sound playing (or about to), null when quiet. */
    val playing: StateFlow<AlarmSound?> = current.asStateFlow()

    fun play(sound: AlarmSound) {
        stop()
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(AlarmAudio.ATTRIBUTES)
            sources.setDataSource(mp, sound)
            mp.isLooping = true
            mp.setOnPreparedListener { if (player === it) it.start() }
            mp.setOnErrorListener { it, what, extra ->
                Log.w(TAG, "Preview of $sound failed ($what, $extra)")
                if (player === it) stop()
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            Log.w(TAG, "Can't preview $sound", e)
            mp.release()
            return
        }
        player = mp
        current.value = sound
        audio.requestAudioFocus(focus)
    }

    fun stop() {
        val mp = player ?: return
        player = null
        mp.release()
        current.value = null
        audio.abandonAudioFocusRequest(focus)
    }

    private companion object {
        const val TAG = "Alarmist"
    }
}
