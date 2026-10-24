package com.orthohin.alarmist.feature.ringing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orthohin.alarmist.core.common.Clock
import com.orthohin.alarmist.core.ringing.RingingController
import com.orthohin.alarmist.mission.api.Mission
import com.orthohin.alarmist.mission.api.MissionRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
internal class RingingViewModel @Inject constructor(
    private val controller: RingingController,
    private val missions: MissionRegistry,
    private val clock: Clock
) : ViewModel(),
    RingingActions {
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<RingingUiState> = controller.sessions
        .map { it.current ?: it.checking }
        .distinctUntilChanged()
        .mapLatest { session -> ringingUiState(session, session?.let { controller.alarm(it.alarmId) }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, RingingUiState.Loading)

    override fun snooze(alarmId: Long) = act { controller.snooze(alarmId) }

    override fun dismiss(alarmId: Long) = act { controller.dismiss(alarmId) }

    override fun openMissions(alarmId: Long) = act { controller.openMissions(alarmId) }

    override fun missionActivity(alarmId: Long) = act { controller.missionActivity(alarmId) }

    override fun missionCompleted(alarmId: Long, index: Int) = act { controller.missionCompleted(alarmId, index) }

    override fun leaveMissions(alarmId: Long) = act { controller.leaveMissions(alarmId) }

    override fun mission(type: String): Mission? = missions[type]

    override fun awake(alarmId: Long) = act { controller.awake(alarmId) }

    override fun now(): Instant = clock.now()

    /** Runs to the end even though the screen closes (and clears this ViewModel) as soon as the state changes. */
    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch { withContext(NonCancellable) { block() } }
    }
}
