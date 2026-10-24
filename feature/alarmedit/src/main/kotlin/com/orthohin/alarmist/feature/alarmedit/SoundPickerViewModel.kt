package com.orthohin.alarmist.feature.alarmedit

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orthohin.alarmist.core.model.AlarmSound
import com.orthohin.alarmist.core.sounds.DeviceRingtone
import com.orthohin.alarmist.core.sounds.DeviceRingtones
import com.orthohin.alarmist.core.sounds.ImportResult
import com.orthohin.alarmist.core.sounds.ImportedSound
import com.orthohin.alarmist.core.sounds.ImportedSounds
import com.orthohin.alarmist.core.sounds.SoundPreviewer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Lists, imports and previews sounds for [SoundPicker]. The choice itself lives in the alarm draft. */
@HiltViewModel
internal class SoundPickerViewModel @Inject constructor(
    private val imported: ImportedSounds,
    ringtones: DeviceRingtones,
    private val previewer: SoundPreviewer
) : ViewModel() {
    val importedSounds: StateFlow<List<ImportedSound>> = imported.sounds

    private val device = MutableStateFlow<List<DeviceRingtone>?>(null)

    /** Null while loading. */
    val deviceRingtones: StateFlow<List<DeviceRingtone>?> = device.asStateFlow()

    val playing: StateFlow<AlarmSound?> = previewer.playing

    private val failures = Channel<ImportResult>(Channel.BUFFERED)
    val importFailures: Flow<ImportResult> = failures.receiveAsFlow()

    init {
        viewModelScope.launch { device.value = ringtones.load() }
    }

    /** Re-reads "My sounds"; the picker calls it each time it opens. */
    fun refresh() {
        viewModelScope.launch { imported.refresh() }
    }

    fun play(sound: AlarmSound) = previewer.play(sound)

    fun stopPreview() = previewer.stop()

    fun import(uri: Uri, onImported: (AlarmSound) -> Unit) {
        viewModelScope.launch {
            when (val result = imported.import(uri)) {
                is ImportResult.Imported -> onImported(result.sound.sound)
                else -> failures.send(result)
            }
        }
    }

    fun delete(sound: ImportedSound) {
        if (playing.value == sound.sound) previewer.stop()
        viewModelScope.launch { imported.delete(sound) }
    }

    override fun onCleared() = previewer.stop()
}
