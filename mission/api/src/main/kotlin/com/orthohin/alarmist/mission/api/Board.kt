package com.orthohin.alarmist.mission.api

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Layout for missions played on a square board (memory, tap in order, Simon).

/**
 * [status] above a square [board] in portrait and beside it in landscape. Pass the given modifier to the board's
 * root: it sizes the board (square, at most 480 dp, shrinking to fit).
 */
@Composable
fun BoardLayout(status: @Composable ColumnScope.() -> Unit, board: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val size = Modifier
            .widthIn(max = 480.dp)
            .aspectRatio(1f)
        if (maxWidth > maxHeight) {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, content = status)
                board(Modifier.weight(1f, fill = false).then(size))
            }
        } else {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
            ) {
                status()
                board(Modifier.weight(1f, fill = false).then(size))
            }
        }
    }
}
