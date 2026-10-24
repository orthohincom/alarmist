package com.orthohin.alarmist.core.sounds

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.orthohin.alarmist.core.common.AppDispatchers
import com.orthohin.alarmist.core.model.AlarmSound
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** A sound in "My sounds". */
data class ImportedSound(val fileName: String) {
    val title: String get() = SoundFileNames.title(fileName)
    val sound: AlarmSound.Imported get() = AlarmSound.Imported(fileName)
}

sealed interface ImportResult {
    data class Imported(val sound: ImportedSound) : ImportResult

    /** Bigger than [ImportedSounds.MAX_BYTES]. */
    data object TooLarge : ImportResult

    /** The file has no audio track Android can read. */
    data object NotAudio : ImportResult

    data object Failed : ImportResult
}

/**
 * "My sounds": copies of files the user picked, kept in device-protected storage so they play before the
 * first unlock after a reboot and keep working when the original is deleted or moved.
 */
@Singleton
class ImportedSounds @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: AppDispatchers
) {
    private val dir = File(context.createDeviceProtectedStorageContext().filesDir, DIR)
    private val list = MutableStateFlow<List<ImportedSound>>(emptyList())

    /** Sorted by title. Call [refresh] once to load it. */
    val sounds: StateFlow<List<ImportedSound>> = list.asStateFlow()

    suspend fun refresh() = withContext(dispatchers.io) {
        list.value = dir.listFiles { f -> f.isFile && !f.name.endsWith(PART) }.orEmpty()
            .map { ImportedSound(it.name) }
            .sortedBy { it.title.lowercase() }
    }

    /** Copies the document at [uri] (from the system file picker) into "My sounds". */
    suspend fun import(uri: Uri): ImportResult = withContext(dispatchers.io) {
        val part = File(dir, "import$PART")
        try {
            dir.mkdirs()
            val (displayName, size) = describe(uri)
            if (size != null && size > MAX_BYTES) return@withContext ImportResult.TooLarge
            if (!copy(uri, part)) return@withContext ImportResult.TooLarge
            if (!hasAudio(part)) return@withContext ImportResult.NotAudio
            val taken = dir.list().orEmpty().toSet()
            val name = SoundFileNames.unique(SoundFileNames.safe(displayName), taken)
            if (!part.renameTo(File(dir, name))) throw IOException("Rename to $name failed")
            refresh()
            ImportResult.Imported(ImportedSound(name))
        } catch (e: Exception) { // Providers throw all sorts: SecurityException, IllegalArgumentException, IO.
            Log.w(TAG, "Import of $uri failed", e)
            ImportResult.Failed
        } finally {
            part.delete()
        }
    }

    /** Alarms that still use it fall back to their backup sound. */
    suspend fun delete(sound: ImportedSound) = withContext(dispatchers.io) {
        file(sound.fileName).delete()
        refresh()
    }

    internal fun file(fileName: String): File = File(dir, fileName)

    private fun describe(uri: Uri): Pair<String?, Long?> {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )
            ?.use { c ->
                if (c.moveToFirst()) {
                    val name = c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 && !c.isNull(it) }
                    val size = c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }
                    return name?.let(c::getString) to size?.let(c::getLong)
                }
            }
        return null to null
    }

    /** False if the file turns out bigger than [MAX_BYTES] (the provider's size can be missing or wrong). */
    private fun copy(uri: Uri, target: File): Boolean {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("No stream for $uri")
        input.use {
            target.outputStream().use { out ->
                val buffer = ByteArray(BUFFER)
                var total = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) return true
                    total += n
                    if (total > MAX_BYTES) return false
                    out.write(buffer, 0, n)
                }
            }
        }
    }

    private fun hasAudio(file: File): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
        } catch (e: RuntimeException) {
            Log.w(TAG, "Not a readable audio file", e)
            false
        } finally {
            retriever.release()
        }
    }

    companion object {
        const val MAX_BYTES = 30L * 1024 * 1024
        private const val DIR = "sounds"
        private const val PART = ".part"
        private const val BUFFER = 64 * 1024
        private const val TAG = "Alarmist"
    }
}
