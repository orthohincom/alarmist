package com.orthohin.alarmist.feature.alarmedit

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orthohin.alarmist.core.common.Clock
import com.orthohin.alarmist.core.common.minuteTicks
import com.orthohin.alarmist.core.data.AlarmRepository
import com.orthohin.alarmist.core.data.SettingsRepository
import com.orthohin.alarmist.core.data.TimeInput
import com.orthohin.alarmist.core.model.Alarm
import com.orthohin.alarmist.core.model.nextTrigger
import com.orthohin.alarmist.core.ringing.PowerMenuGuard
import com.orthohin.alarmist.core.ringing.RingingController
import com.orthohin.alarmist.core.scheduling.AlarmEditor
import com.orthohin.alarmist.core.sounds.SoundSources
import com.orthohin.alarmist.mission.api.MissionRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import java.util.Optional
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class AlarmEditUiState(
    val alarm: Alarm,
    val isNew: Boolean,
    val now: Instant,
    /** When the alarm would ring if saved now. */
    val nextRing: Instant?,
    /** The chosen sound's name, null if it's gone (the backup plays). */
    val soundTitle: String?,
    /** Wheels or clock face (app setting). */
    val timeInput: TimeInput = TimeInput.WHEELS
)

internal sealed interface AlarmEditEvent {
    /** Saved: close the screen. */
    data class Saved(val now: Instant, val nextRing: Instant?) : AlarmEditEvent

    data object Deleted : AlarmEditEvent

    data object PreviewSet : AlarmEditEvent
}

@HiltViewModel
internal class AlarmEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AlarmRepository,
    private val editor: AlarmEditor,
    private val ringing: RingingController,
    private val sounds: SoundSources,
    private val clock: Clock,
    settings: SettingsRepository,
    val missions: MissionRegistry,
    powerMenuGuard: Optional<PowerMenuGuard>
) : ViewModel() {
    /** Null in builds without the guard (Play). */
    val powerMenuGuard: PowerMenuGuard? = powerMenuGuard.orElse(null)

    private val alarmId: Long = savedStateHandle[ALARM_ID_ARG] ?: Alarm.NEW_ID
    private var original: Alarm? = null
    private val draft = MutableStateFlow<Alarm?>(null)
    private var busy = false

    private val eventChannel = Channel<AlarmEditEvent>(Channel.BUFFERED)
    val events: Flow<AlarmEditEvent> = eventChannel.receiveAsFlow()

    private val soundTitle = draft.filterNotNull().map {
        it.sound.sound
    }.distinctUntilChanged().map { sounds.title(it) }

    val state: StateFlow<AlarmEditUiState?> = combine(
        draft.filterNotNull(),
        clock.minuteTicks(),
        soundTitle,
        settings.settings.map { it.timeInput }.distinctUntilChanged()
    ) { alarm, now, title, timeInput ->
        val nextRing = alarm.forSave(original).nextTrigger(now, clock.zone())
        AlarmEditUiState(alarm, original == null, now, nextRing, title, timeInput)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            original = if (alarmId == Alarm.NEW_ID) null else load(alarmId)
            draft.value = original ?: newAlarm(clock.now(), clock.zone())
        }
    }

    fun edit(transform: (Alarm) -> Alarm) = draft.update { it?.let(transform) }

    fun save() = once { alarm ->
        val saved = alarm.forSave(original)
        // Finishes even if the screen closes meanwhile.
        withContext(NonCancellable) { editor.save(saved) }
        val now = clock.now()
        eventChannel.send(AlarmEditEvent.Saved(now, saved.nextTrigger(now, clock.zone())))
    }

    fun delete() = once {
        original?.let { withContext(NonCancellable) { editor.delete(it.id) } }
        eventChannel.send(AlarmEditEvent.Deleted)
    }

    fun preview() {
        val alarm = draft.value ?: return
        viewModelScope.launch {
            ringing.preview(alarm, clock.now() + PREVIEW_DELAY)
            eventChannel.send(AlarmEditEvent.PreviewSet)
        }
    }

    /** Ignores taps while a save or delete is running. */
    private fun once(block: suspend (Alarm) -> Unit) {
        val alarm = draft.value ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                block(alarm)
            } finally {
                busy = false
            }
        }
    }

    private suspend fun load(id: Long): Alarm? = try {
        repository.getAlarm(id)
    } catch (e: Exception) {
        Log.e(TAG, "Couldn't read alarm $id", e)
        null
    }

    companion object {
        val PREVIEW_DELAY: Duration = Duration.ofSeconds(10)
        private const val TAG = "Alarmist"
    }
}
