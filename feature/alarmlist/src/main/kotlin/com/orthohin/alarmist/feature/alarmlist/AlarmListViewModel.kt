package com.orthohin.alarmist.feature.alarmlist

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orthohin.alarmist.core.common.Clock
import com.orthohin.alarmist.core.common.minuteTicks
import com.orthohin.alarmist.core.data.AlarmRepository
import com.orthohin.alarmist.core.data.SettingsRepository
import com.orthohin.alarmist.core.model.Alarm
import com.orthohin.alarmist.core.model.Schedule
import com.orthohin.alarmist.core.model.nextTrigger
import com.orthohin.alarmist.core.model.skippingNext
import com.orthohin.alarmist.core.scheduling.AlarmEditor
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface AlarmListEvent {
    /** A repeating alarm was turned off: offer to skip only its next ring instead. */
    data class TurnedOff(val alarmId: Long) : AlarmListEvent

    data class TurnedOn(val now: Instant, val nextRing: Instant?) : AlarmListEvent

    data class Skipped(val date: LocalDate) : AlarmListEvent
}

@HiltViewModel
internal class AlarmListViewModel @Inject constructor(
    private val repository: AlarmRepository,
    private val editor: AlarmEditor,
    private val settings: SettingsRepository,
    private val clock: Clock
) : ViewModel() {
    private val eventChannel = Channel<AlarmListEvent>(Channel.BUFFERED)
    val events: Flow<AlarmListEvent> = eventChannel.receiveAsFlow()

    val state: StateFlow<AlarmListUiState?> = combine(
        repository.observeAlarms().catch { e ->
            Log.e(TAG, "Couldn't read alarms", e)
            emit(emptyList())
        },
        clock.minuteTicks()
    ) { alarms, now -> alarmListUiState(alarms, now, clock.zone()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** "Don't show again" on the "display over other apps" card. Starts true, so the card doesn't flash. */
    val overlayHintDismissed: StateFlow<Boolean> = settings.settings.map { it.overlayHintDismissed }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun dismissOverlayHint() {
        viewModelScope.launch { settings.update { it.copy(overlayHintDismissed = true) } }
    }

    fun setEnabled(id: Long, enabled: Boolean) = act(id) { alarm ->
        if (enabled) {
            val now = clock.now()
            val on = alarm.turnedOn(now, clock.zone())
            if (on.schedule == alarm.schedule) editor.setEnabled(id, true) else editor.save(on)
            eventChannel.send(AlarmListEvent.TurnedOn(now, on.nextTrigger(now, clock.zone())))
        } else {
            editor.setEnabled(id, false)
            if (alarm.schedule is Schedule.Weekly && alarm.enabled) eventChannel.send(AlarmListEvent.TurnedOff(id))
        }
    }

    /** Turns the alarm back on, skipping only its next ring. */
    fun skipOnce(id: Long) = act(id) { alarm ->
        val skipping = alarm.copy(enabled = true, skippedDate = null).skippingNext(clock.now(), clock.zone())
        editor.save(skipping)
        skipping.skippedDate?.let { eventChannel.send(AlarmListEvent.Skipped(it)) }
    }

    fun undoSkip(id: Long) = act(id) { alarm -> editor.save(alarm.copy(skippedDate = null)) }

    /** Runs [block] on the stored alarm [id]; writes finish even if the screen goes away. */
    private fun act(id: Long, block: suspend (Alarm) -> Unit) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                try {
                    repository.getAlarm(id)?.let { block(it) }
                } catch (e: Exception) {
                    Log.e(TAG, "Couldn't change alarm $id", e)
                }
            }
        }
    }

    private companion object {
        const val TAG = "Alarmist"
    }
}
