package com.orthohin.alarmist.feature.reliability

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orthohin.alarmist.core.data.AlarmRepository
import com.orthohin.alarmist.core.data.EventLog
import com.orthohin.alarmist.core.model.Alarm
import com.orthohin.alarmist.core.model.AlarmEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal data class EventLogState(val events: List<AlarmEvent>, val alarms: Map<Long, Alarm>)

@HiltViewModel
internal class ReliabilityViewModel @Inject constructor(
    val checker: ReliabilityChecks,
    private val setup: ReliabilitySetup,
    repository: AlarmRepository,
    eventLog: EventLog
) : ViewModel() {
    private val checkList = MutableStateFlow(checker.current())
    val checks: StateFlow<List<Check>> = checkList.asStateFlow()

    /** A required setting is off: alarms may not ring or show. */
    val hasProblems: StateFlow<Boolean> = checkList.map { list -> list.any { it.kind.required && !it.ok } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val log: StateFlow<EventLogState?> =
        combine(eventLog.observeRecent(LOG_SIZE), repository.observeAlarms()) { events, alarms ->
            EventLogState(events, alarms.associateBy { it.id })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Settings change outside the app: call on every resume. */
    fun refresh() {
        checkList.value = checker.current()
    }

    fun finishSetup() = setup.markDone()

    private companion object {
        const val LOG_SIZE = 100
    }
}
