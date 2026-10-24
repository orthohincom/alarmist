package com.orthohin.alarmist.feature.alarmedit

import android.text.format.DateFormat
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import kotlinx.coroutines.flow.drop

/** Material's clock face, the alternative to [TimeWheel] (setting `TimeInput.DIAL`). Follows the phone's 24-hour setting. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeDial(time: LocalTime, onTime: (LocalTime) -> Unit, modifier: Modifier = Modifier) {
    val state = rememberTimePickerState(time.hour, time.minute, DateFormat.is24HourFormat(LocalContext.current))
    val currentOnTime by rememberUpdatedState(onTime)
    LaunchedEffect(state) {
        // Skips the first value: it's the alarm's own time.
        snapshotFlow { LocalTime.of(state.hour, state.minute) }.drop(1).collect { currentOnTime(it) }
    }
    TimePicker(state, modifier.padding(top = 8.dp))
}
