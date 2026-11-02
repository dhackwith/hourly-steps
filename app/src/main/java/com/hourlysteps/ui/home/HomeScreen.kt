package com.hourlysteps.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.CircularProgressIndicatorDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.drawCircularProgressIndicator
import com.hourlysteps.domain.CheckMode
import com.hourlysteps.ui.formatNextCheck
import com.hourlysteps.ui.formatPauseEnd
import com.hourlysteps.ui.formatSteps

@Composable
fun HomeScreen(viewModel: HomeViewModel, onOpenSettings: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    state?.let { HomeContent(it, onResume = viewModel::resume, onOpenSettings = onOpenSettings) }
}

@Composable
fun HomeContent(state: HomeUiState, onResume: () -> Unit, onOpenSettings: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // "Goal met and nothing left to do": show a finished ring. Outside the window no checks run,
    // so inactivity reminders don't change that.
    val finished = state.dailyGoalMet &&
        (state.status == HomeStatus.OUTSIDE_WINDOW || (state.status == HomeStatus.ACTIVE && state.mode == CheckMode.OFF))
    val ringColors = if (finished) {
        ProgressIndicatorDefaults.colors(indicatorColor = colors.tertiary)
    } else {
        ProgressIndicatorDefaults.colors()
    }
    val progress = when {
        finished -> 1f
        state.status == HomeStatus.ACTIVE -> state.ringProgress
        else -> 0f
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Drawn directly rather than with the animated CircularProgressIndicator composable: that
        // one keeps the progress it was first composed with and drops the next update, so after
        // navigating back it can show a stale value indefinitely.
        val strokeWidth = CircularProgressIndicatorDefaults.largeStrokeWidth
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .drawBehind {
                    drawCircularProgressIndicator(
                        progress = progress,
                        colors = ringColors,
                        strokeWidth = strokeWidth,
                        startAngle = 120f,
                        endAngle = 60f,
                    )
                },
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when {
                state.status == HomeStatus.PAUSED -> PausedContent(state, onResume)
                state.status == HomeStatus.DAY_OFF -> DayOffContent(state)
                finished -> FinishedContent(state)
                state.status == HomeStatus.OUTSIDE_WINDOW -> OutsideWindowContent(state)
                else -> ActiveContent(state)
            }
        }

        EdgeButton(
            onClick = onOpenSettings,
            modifier = Modifier.align(Alignment.BottomCenter),
            buttonSize = EdgeButtonSize.ExtraSmall,
        ) {
            Text("Settings")
        }
    }
}

@Composable
private fun ColumnScope.ActiveContent(state: HomeUiState) {
    val colors = MaterialTheme.colorScheme
    if (state.dailyGoalMet) {
        Text("Goal met · stay active", style = MaterialTheme.typography.labelSmall, color = colors.tertiary)
    }
    Text(formatSteps(state.stepsInSlot), style = MaterialTheme.typography.displayMedium)
    Text(
        "of ${formatSteps(state.ringTarget)} this hour",
        style = MaterialTheme.typography.labelMedium,
        color = colors.onSurfaceVariant,
    )
    TodayLine(state)
    state.catchUpPerSlot?.let {
        Text(
            "${formatSteps(it)}/hr to catch up",
            style = MaterialTheme.typography.labelSmall,
            color = colors.secondary,
        )
    }
    NextCheckLine(state)
}

@Composable
private fun ColumnScope.FinishedContent(state: HomeUiState) {
    val colors = MaterialTheme.colorScheme
    Text("Goal met", style = MaterialTheme.typography.titleMedium, color = colors.tertiary)
    Text(formatSteps(state.todayTotal), style = MaterialTheme.typography.displaySmall)
    Text("today", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
}

@Composable
private fun ColumnScope.OutsideWindowContent(state: HomeUiState) {
    Text(formatSteps(state.todayTotal), style = MaterialTheme.typography.displayMedium)
    TodayLine(state, showTotal = false)
    NextCheckLine(state)
}

@Composable
private fun ColumnScope.DayOffContent(state: HomeUiState) {
    val colors = MaterialTheme.colorScheme
    Text("Day off", style = MaterialTheme.typography.titleMedium, color = colors.secondary)
    Text(formatSteps(state.todayTotal), style = MaterialTheme.typography.displaySmall)
    Text("steps today", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
    NextCheckLine(state)
}

@Composable
private fun ColumnScope.PausedContent(state: HomeUiState, onResume: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text("Paused", style = MaterialTheme.typography.titleMedium, color = colors.secondary)
    state.pausedThrough?.let {
        Text(
            "until ${formatPauseEnd(it, state.today)}",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    Text(
        "${formatSteps(state.todayTotal)} steps today",
        style = MaterialTheme.typography.labelMedium,
    )
    CompactButton(onClick = onResume, label = { Text("Resume") })
}

@Composable
private fun TodayLine(state: HomeUiState, showTotal: Boolean = true) {
    val goal = state.dailyGoal?.let { formatSteps(it) } ?: return
    Text(
        if (showTotal) "Today ${formatSteps(state.todayTotal)} / $goal" else "of $goal today",
        style = MaterialTheme.typography.labelSmall,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun NextCheckLine(state: HomeUiState) {
    state.nextCheck?.let {
        Text(
            "Next check ${formatNextCheck(it, state.today)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
