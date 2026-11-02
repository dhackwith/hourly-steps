package com.hourlysteps.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.hourlysteps.domain.CheckRecord
import com.hourlysteps.domain.DayStatusKind
import com.hourlysteps.domain.SlotOutcome
import com.hourlysteps.domain.SlotRow
import com.hourlysteps.domain.TodaySummary
import com.hourlysteps.surfaces.RingColors
import com.hourlysteps.ui.formatSteps
import com.hourlysteps.ui.formatTime
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Today hour by hour, so "it didn't buzz, is it broken?" has an answer on the watch. */
@Composable
fun TodayPage(summary: TodaySummary) {
    val listState = rememberScalingLazyListState()
    val colors = MaterialTheme.colorScheme

    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(state = listState, contentPadding = padding) {
            item { ListHeader { Text("Today") } }
            item {
                Text(
                    summaryLine(summary),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
            }
            summary.lastCheck?.let { check ->
                item {
                    Text(
                        lastCheckLine(check),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            items(summary.rows.size) { index -> SlotRowItem(summary.rows[index]) }
        }
    }
}

private fun summaryLine(summary: TodaySummary): String {
    val total = formatSteps(summary.todayTotal)
    return when {
        summary.kind == DayStatusKind.DAY_OFF -> "Day off · $total steps"
        summary.slotsTotal == 0 -> "$total steps"
        else -> "${summary.slotsHit} of ${summary.slotsTotal} hours hit"
    }
}

private fun lastCheckLine(check: CheckRecord): String {
    val time = Instant.ofEpochMilli(check.timeMillis).atZone(ZoneId.systemDefault()).toLocalTime()
    val outcome = when {
        check.skippedReason != null -> "skipped, ${check.skippedReason}"
        check.notified -> "reminded"
        else -> "no reminder needed"
    }
    return "Last check ${formatTime(time)}: $outcome"
}

@Composable
private fun SlotRowItem(row: SlotRow) {
    val colors = MaterialTheme.colorScheme
    val current = row.outcome == SlotOutcome.IN_PROGRESS
    val dim = row.outcome == SlotOutcome.UPCOMING || row.outcome == SlotOutcome.GOAL_ALREADY_MET
    val mainColor = when {
        current -> Color(RingColors.SLOT)
        dim -> colors.onSurfaceVariant
        else -> colors.onSurface
    }
    val time = LocalTime.of(row.slot.startMinute / 60, row.slot.startMinute % 60)

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            formatTime(time),
            style = MaterialTheme.typography.labelMedium,
            color = mainColor,
            modifier = Modifier.width(74.dp),
            maxLines = 1,
        )
        Text(
            detailText(row),
            style = MaterialTheme.typography.labelSmall,
            color = if (current) mainColor else colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        Text(glyph(row.outcome), style = MaterialTheme.typography.labelMedium, color = glyphColor(row.outcome))
    }
}

private fun detailText(row: SlotRow): String = when (row.outcome) {
    SlotOutcome.UPCOMING -> "goal ${formatSteps(row.goal)}"
    SlotOutcome.SKIPPED -> "${formatSteps(row.steps)} · ${row.reason}"
    SlotOutcome.GOAL_ALREADY_MET -> formatSteps(row.steps)
    else -> "${formatSteps(row.steps)} / ${formatSteps(row.goal)}"
}

private fun glyph(outcome: SlotOutcome): String = when (outcome) {
    SlotOutcome.HIT -> "✓"
    SlotOutcome.SHORT -> "✗"
    SlotOutcome.SKIPPED, SlotOutcome.GOAL_ALREADY_MET -> "–"
    SlotOutcome.IN_PROGRESS -> "•"
    SlotOutcome.UPCOMING -> ""
}

@Composable
private fun glyphColor(outcome: SlotOutcome): Color = when (outcome) {
    SlotOutcome.HIT -> Color(RingColors.GOAL_MET)
    SlotOutcome.SHORT -> MaterialTheme.colorScheme.error
    SlotOutcome.IN_PROGRESS -> Color(RingColors.SLOT)
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
