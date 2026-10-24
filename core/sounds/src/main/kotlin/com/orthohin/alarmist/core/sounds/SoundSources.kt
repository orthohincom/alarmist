package com.orthohin.alarmist.core.sounds

import android.content.Context
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import com.orthohin.alarmist.core.common.AppDispatchers
import com.orthohin.alarmist.core.model.AlarmSound
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import javax.inject.Inject
import kotlinx.coroutines.withContext

/** Opens any [AlarmSound] for playback and names it. */
class SoundSources @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imported: ImportedSounds,
    private val dispatchers: AppDispatchers
) {
    /**
     * Sets [sound] as [player]'s data source. Throws if it can't be opened: an unknown bundled id, a deleted
     * import, or a ringtone whose provider isn't readable (e.g. before the first unlock).
     */
    fun setDataSource(player: MediaPlayer, sound: AlarmSound) {
        when (sound) {
            is AlarmSound.Bundled -> {
                val bundled =
                    BundledSounds.find(sound.id) ?: throw FileNotFoundException("No bundled sound ${sound.id}")
                context.resources.openRawResourceFd(bundled.resource).use { player.setDataSource(it) }
            }

            is AlarmSound.Imported -> {
                val file = imported.file(sound.fileName)
                if (!file.isFile) throw FileNotFoundException("Imported sound ${sound.fileName} is gone")
                player.setDataSource(file.path)
            }

            is AlarmSound.Custom -> player.setDataSource(context, Uri.parse(sound.uri))
        }
    }

    /** The name to show, or null if the sound is gone (ringing then plays the backup). */
    suspend fun title(sound: AlarmSound): String? = when (sound) {
        is AlarmSound.Bundled -> BundledSounds.find(sound.id)?.let { context.getString(it.name) }

        is AlarmSound.Imported -> withContext(dispatchers.io) {
            ImportedSound(sound.fileName).takeIf { imported.file(it.fileName).isFile }?.title
        }

        is AlarmSound.Custom -> withContext(dispatchers.io) {
            try {
                RingtoneManager.getRingtone(context, Uri.parse(sound.uri))?.getTitle(context)
            } catch (e: Exception) {
                Log.w(TAG, "No title for ${sound.uri}", e)
                null
            }
        }
    }

    private companion object {
        const val TAG = "Alarmist"
    }
}
