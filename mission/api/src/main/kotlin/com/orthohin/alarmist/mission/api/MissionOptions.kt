package com.orthohin.alarmist.mission.api

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// Building blocks for Mission.ConfigEditor, so every mission's options look the same.

/** A titled radio list; [detail] is a second line, e.g. an example of that difficulty. */
@Composable
fun <T> RadioOptions(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    detail: @Composable (T) -> String? = { null }
) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    Column(Modifier.selectableGroup()) {
        for (option in options) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(option == selected, role = Role.RadioButton) { onSelect(option) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = option == selected, onClick = null)
                Column(Modifier.padding(start = 16.dp)) {
                    Text(label(option))
                    detail(option)?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** A whole-number slider in steps of [step]; [title] shows the current value, e.g. "3 problems". */
@Composable
fun IntSlider(title: String, value: Int, range: IntRange, step: Int = 1, onChange: (Int) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange(range.first + ((it - range.first) / step).roundToInt() * step) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = (range.last - range.first) / step - 1
    )
}
