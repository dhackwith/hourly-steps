package com.hourlysteps.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
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
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.drawCircularProgressIndicator
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.Text
import com.hourlysteps.domain.CheckMode
import com.hourlysteps.ui.formatSteps
import com.hourlysteps.ui.formatTime

@Composable
fun HomeScreen(viewModel: HomeViewModel, onOpenSettings: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    state?.let { HomeContent(it, onOpenSettings) }
}

@Composable
fun HomeContent(state: HomeUiState, onOpenSettings: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val ringColors = if (state.dailyGoalMet && state.mode != CheckMode.INACTIVITY) {
        ProgressIndicatorDefaults.colors(indicatorColor = colors.tertiary)
    } else {
        ProgressIndicatorDefaults.colors()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Drawn directly rather than with the animated CircularProgressIndicator composable: that
        // one keeps the progress it was first composed with and drops the next update, so after
        // navigating back it can show a stale value indefinitely.
        val progress = if (state.dailyGoalMet && state.mode == CheckMode.OFF) 1f else state.ringProgress
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
            if (state.dailyGoalMet && state.mode == CheckMode.OFF) {
                Text("Goal met", style = MaterialTheme.typography.titleMedium, color = colors.tertiary)
                Text(formatSteps(state.todayTotal), style = MaterialTheme.typography.displaySmall)
                Text("today", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            } else {
                if (state.dailyGoalMet) {
                    Text("Goal met · stay active", style = MaterialTheme.typography.labelSmall, color = colors.tertiary)
                }
                Text(formatSteps(state.stepsThisHour), style = MaterialTheme.typography.displayMedium)
                Text(
                    "of ${formatSteps(state.ringTarget)} this hour",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    "Today ${formatSteps(state.todayTotal)} / ${formatSteps(state.settings.dailyGoal)}",
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
                state.catchUpPerHour?.let {
                    Text(
                        "${formatSteps(it)}/hr to catch up",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.secondary,
                    )
                }
            }
            state.nextCheck?.let {
                if (!(state.dailyGoalMet && state.mode == CheckMode.OFF)) {
                    Text(
                        "Next check ${formatTime(it.toLocalTime())}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
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
