package com.orthohin.alarmist.feature.alarmedit

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.DateFormatSymbols
import java.time.LocalTime
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Hour and minute wheels (plus AM/PM in the 12-hour format) that snap to the middle row. Hours and minutes
 * loop. Each wheel changes only its part of the time, through [onEdit], so two wheels moving at once can't
 * overwrite each other.
 */
@Composable
internal fun TimeWheel(time: LocalTime, onEdit: ((LocalTime) -> LocalTime) -> Unit, modifier: Modifier = Modifier) {
    val is24h = DateFormat.is24HourFormat(LocalContext.current)
    val locale = LocalConfiguration.current.locales[0]
    val number = { n: Int -> String.format(locale, "%02d", n) }
    Box(modifier.fillMaxWidth().height(ROW_HEIGHT * VISIBLE_ROWS), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(ROW_HEIGHT)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (is24h) {
                Wheel(24, time.hour, number, stringResource(R.string.edit_hour), onSelect = { h ->
                    onEdit { it.withHour(h) }
                })
            } else {
                // Index 0 is 12.
                Wheel(
                    count = 12,
                    selected = time.hour % 12,
                    label = { number(if (it == 0) 12 else it) },
                    description = stringResource(R.string.edit_hour),
                    onSelect = { h -> onEdit { it.withHour(h + if (it.hour >= 12) 12 else 0) } }
                )
            }
            Text(
                ":",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Wheel(60, time.minute, number, stringResource(R.string.edit_minute), onSelect = { m ->
                onEdit { it.withMinute(m) }
            })
            if (!is24h) {
                val amPm = remember(locale) { DateFormatSymbols.getInstance(locale).amPmStrings }
                Wheel(
                    count = 2,
                    selected = if (time.hour >= 12) 1 else 0,
                    label = { amPm[it] },
                    description = stringResource(R.string.edit_am_pm),
                    onSelect = { pm -> onEdit { it.withHour(it.hour % 12 + if (pm == 1) 12 else 0) } },
                    loop = false,
                    small = true
                )
            }
        }
    }
}

/** One snapping column of [count] values. A looping wheel repeats them [LOOP_CYCLES] times and starts mid-way. */
@Composable
private fun Wheel(
    count: Int,
    selected: Int,
    label: (Int) -> String,
    description: String,
    onSelect: (Int) -> Unit,
    loop: Boolean = true,
    small: Boolean = false,
    width: Dp = if (small) 72.dp else 80.dp
) {
    val total = if (loop) count * LOOP_CYCLES else count
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = if (loop) count * (LOOP_CYCLES / 2) + selected else selected
    )
    val rowPx = with(LocalDensity.current) { ROW_HEIGHT.roundToPx() }
    // The content padding is one row, so the first visible item sits in the middle row.
    val centered by remember(state, rowPx) {
        derivedStateOf {
            state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > rowPx / 2) 1 else 0
        }
    }
    val select by rememberUpdatedState(onSelect)
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state, count) {
        snapshotFlow { centered % count }.distinctUntilChanged().drop(1).collect {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            select(it)
        }
    }
    val scope = rememberCoroutineScope()
    val value = centered % count
    LazyColumn(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state),
        contentPadding = PaddingValues(vertical = ROW_HEIGHT),
        modifier = Modifier
            .width(width)
            .height(ROW_HEIGHT * VISIBLE_ROWS)
            // One adjustable control for TalkBack instead of a list of numbers.
            .clearAndSetSemantics {
                contentDescription = description
                stateDescription = label(value)
                progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), 0f..(count - 1).toFloat(), count - 2)
                setProgress { target ->
                    val delta = target.roundToInt().coerceIn(0, count - 1) - value
                    scope.launch { state.animateScrollToItem(centered + delta) }
                    true
                }
            }
    ) {
        items(total) { index ->
            val isCentered = index == centered
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(ROW_HEIGHT)
                    .clickable(enabled = !isCentered) { scope.launch { state.animateScrollToItem(index) } },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label(index % count),
                    style = if (small) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineLarge,
                    fontWeight = if (isCentered) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isCentered) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = FADED_ALPHA)
                    },
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private val ROW_HEIGHT = 52.dp
private const val VISIBLE_ROWS = 3
private const val LOOP_CYCLES = 200
private const val FADED_ALPHA = 0.5f
