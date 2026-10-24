package com.orthohin.alarmist.feature.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orthohin.alarmist.core.common.AppDispatchers
import com.orthohin.alarmist.core.common.Clock
import com.orthohin.alarmist.core.data.AlarmRepository
import com.orthohin.alarmist.core.data.AppSettings
import com.orthohin.alarmist.core.data.Backup
import com.orthohin.alarmist.core.data.BackupCodec
import com.orthohin.alarmist.core.data.BackupException
import com.orthohin.alarmist.core.data.SettingsRepository
import com.orthohin.alarmist.core.data.ThemeMode
import com.orthohin.alarmist.core.data.TimeInput
import com.orthohin.alarmist.core.scheduling.AlarmEditor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface BackupResult {
    data class Exported(val alarms: Int) : BackupResult

    /** [added] new alarms, [skipped] already here, [failed] couldn't be saved. */
    data class Restored(val added: Int, val skipped: Int, val failed: Int) : BackupResult

    data class Failed(val reason: Reason) : BackupResult

    enum class Reason { WRITE, READ, TOO_LARGE, NOT_A_BACKUP, NEWER_VERSION, INVALID, NO_FILE_APP, OTHER }
}

@HiltViewModel
internal class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val repository: AlarmRepository,
    private val editor: AlarmEditor,
    private val clock: Clock,
    private val dispatchers: AppDispatchers
) : ViewModel() {
    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val busyFlow = MutableStateFlow(false)

    /** A backup or restore is running. */
    val busy: StateFlow<Boolean> = busyFlow.asStateFlow()

    private val resultChannel = Channel<BackupResult>(Channel.BUFFERED)
    val results: Flow<BackupResult> = resultChannel.receiveAsFlow()

    fun setTheme(theme: ThemeMode) = change { it.copy(theme = theme) }

    fun setDynamicColor(enabled: Boolean) = change { it.copy(dynamicColor = enabled) }

    fun setTimeInput(input: TimeInput) = change { it.copy(timeInput = input) }

    fun backupFileName(): String = "alarmist-backup-${clock.now().atZone(clock.zone()).toLocalDate()}.json"

    fun export(uri: Uri) = backupTask {
        val alarms = repository.observeAlarms().first()
        val text = BackupCodec.encode(Backup(alarms, settingsRepository.settings.first()), clock.now())
        try {
            withContext(dispatchers.io) {
                context.contentResolver.openOutputStream(uri, "w")?.use { it.write(text.toByteArray()) }
                    ?: throw IOException("No stream for $uri")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Couldn't write the backup", e)
            return@backupTask BackupResult.Failed(BackupResult.Reason.WRITE)
        }
        BackupResult.Exported(alarms.size)
    }

    fun restore(uri: Uri) = backupTask {
        val text = try {
            withContext(dispatchers.io) {
                context.contentResolver.openInputStream(uri)?.use { it.readAtMost(MAX_BYTES) }
                    ?: throw IOException("No stream for $uri")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Couldn't read the backup", e)
            return@backupTask BackupResult.Failed(BackupResult.Reason.READ)
        } ?: return@backupTask BackupResult.Failed(BackupResult.Reason.TOO_LARGE)
        val backup = try {
            withContext(dispatchers.default) { BackupCodec.decode(text) }
        } catch (e: BackupException) {
            Log.w(TAG, "Not a backup this version can read", e)
            return@backupTask BackupResult.Failed(
                when (e.reason) {
                    BackupException.Reason.NOT_A_BACKUP -> BackupResult.Reason.NOT_A_BACKUP
                    BackupException.Reason.NEWER_VERSION -> BackupResult.Reason.NEWER_VERSION
                    BackupException.Reason.INVALID -> BackupResult.Reason.INVALID
                }
            )
        }
        backup.settings?.let { restored ->
            // Dismissed hints belong to this phone.
            settingsRepository.update { restored.copy(overlayHintDismissed = it.overlayHintDismissed) }
        }
        val toAdd = alarmsToRestore(repository.observeAlarms().first(), backup.alarms, clock.now(), clock.zone())
        val failed = toAdd.count { alarm ->
            try {
                editor.save(alarm)
                false
            } catch (e: Exception) {
                Log.e(TAG, "Couldn't restore an alarm", e)
                true
            }
        }
        BackupResult.Restored(added = toAdd.size - failed, skipped = backup.alarms.size - toAdd.size, failed = failed)
    }

    /** The system has no app to pick or create a file. */
    fun noFileApp() {
        resultChannel.trySend(BackupResult.Failed(BackupResult.Reason.NO_FILE_APP))
    }

    private fun change(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            try {
                settingsRepository.update(transform)
            } catch (e: IOException) {
                Log.e(TAG, "Couldn't save settings", e)
            }
        }
    }

    /** One backup or restore at a time; it finishes even if the screen goes away. */
    private fun backupTask(block: suspend () -> BackupResult) {
        if (!busyFlow.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            withContext(NonCancellable) {
                try {
                    resultChannel.send(block())
                } catch (e: Exception) {
                    Log.e(TAG, "Backup or restore failed", e)
                    resultChannel.send(BackupResult.Failed(BackupResult.Reason.OTHER))
                } finally {
                    busyFlow.value = false
                }
            }
        }
    }

    private companion object {
        const val TAG = "Alarmist"

        /** Photo missions hold a ~50 KB photo each, so this is room for hundreds of them. */
        const val MAX_BYTES = 32 * 1024 * 1024
    }
}

/** The stream as UTF-8 text, or null if it is longer than [limit] bytes. */
private fun InputStream.readAtMost(limit: Int): String? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (out.size() <= limit) {
        val n = read(buffer)
        if (n < 0) return out.toString(Charsets.UTF_8.name())
        out.write(buffer, 0, n)
    }
    return null
}
