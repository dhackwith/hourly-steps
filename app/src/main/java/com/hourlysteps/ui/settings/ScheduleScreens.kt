package com.hourlysteps.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.hourlysteps.AppContainer
import com.hourlysteps.domain.DayAssignment
import com.hourlysteps.domain.GoalCalculator
import com.hourlysteps.domain.PlanId
import com.hourlysteps.domain.ScheduleMode
import com.hourlysteps.domain.Settings
import com.hourlysteps.ui.formatPauseEnd
import com.hourlysteps.ui.formatSlot
import com.hourlysteps.ui.formatSteps
import com.hourlysteps.ui.formatWeek
import com.hourlysteps.ui.fullDayName
import com.hourlysteps.ui.orderedWeek
import java.time.ZonedDateTime
import kotlinx.coroutines.launch

/** One plan's goal and reminder window, with the resulting steps per hour. */
@Composable
fun PlanEditorScreen(
    container: AppContainer,
    planId: PlanId,
    onPick: (PickerTarget) -> Unit,
) {
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(null)
    val loaded = settings ?: return
    val plan = loaded.schedule.plan(planId)
    val twoPlans = loaded.schedule.mode == ScheduleMode.TWO_PLANS
    val listState = rememberScalingLazyListState()

    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(state = listState, contentPadding = padding) {
            item {
                ListHeader {
                    Text(
                        when {
                            !twoPlans -> "Daily plan"
                            planId == PlanId.A -> "Plan A"
                            else -> "Plan B"
                        },
                    )
                }
            }
            item {
                FilledTonalButton(
                    onClick = { onPick(PickerTarget.DAILY_GOAL) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Daily goal") },
                    secondaryLabel = { Text("${formatSteps(plan.dailyGoal)} steps") },
                )
            }
            item {
                FilledTonalButton(
                    onClick = { onPick(PickerTarget.FIRST_HOUR) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("First reminder") },
                    secondaryLabel = { Text(formatSlot(plan.firstHour)) },
                )
            }
            item {
                FilledTonalButton(
                    onClick = { onPick(PickerTarget.LAST_HOUR) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Last reminder") },
                    secondaryLabel = { Text(formatSlot(plan.lastHour)) },
                )
            }
            item {
                val slotMinutes = loaded.schedule.slotMinutes
                Text(
                    "${GoalCalculator.slotCount(plan, slotMinutes)} reminders · ≈ ${formatSteps(GoalCalculator.slotGoal(plan, slotMinutes))} steps/hour",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Tap a day to cycle what it does: Plan A, Plan B, Off with two plans; On, Off with one. Full-width
 * rows rather than a row of seven chips, which would be too small to tap on a round watch.
 */
@Composable
fun WeekEditorScreen(container: AppContainer) {
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(null)
    val loaded = settings ?: return
    val schedule = loaded.schedule
    val scope = rememberCoroutineScope()
    val listState = rememberScalingLazyListState()
    val week = remember { orderedWeek() }
    val colors = MaterialTheme.colorScheme

    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(state = listState, contentPadding = padding) {
            item { ListHeader { Text("Week") } }
            item {
                Text(
                    formatWeek(schedule),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            week.forEach { day ->
                item(key = day.name) {
                    val assignment = schedule.assignmentFor(day)
                    val buttonColors = when (assignment) {
                        DayAssignment.PLAN_A -> ButtonDefaults.buttonColors(
                            containerColor = colors.primaryContainer,
                            contentColor = colors.onPrimaryContainer,
                            secondaryContentColor = colors.onPrimaryContainer,
                        )
                        DayAssignment.PLAN_B -> ButtonDefaults.buttonColors(
                            containerColor = colors.tertiaryContainer,
                            contentColor = colors.onTertiaryContainer,
                            secondaryContentColor = colors.onTertiaryContainer,
                        )
                        DayAssignment.OFF -> ButtonDefaults.filledTonalButtonColors()
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                container.settingsRepository.update { it.copy(schedule = it.schedule.cycled(day)) }
                                container.ensureArmed()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = buttonColors,
                        label = { Text(fullDayName(day)) },
                        secondaryLabel = {
                            Text(
                                when (assignment) {
                                    DayAssignment.OFF -> "Off"
                                    DayAssignment.PLAN_A -> if (schedule.mode == ScheduleMode.TWO_PLANS) "Plan A" else "On"
                                    DayAssignment.PLAN_B -> "Plan B"
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

/** Pause reminders for a while, or resume them. Steps keep counting either way. */
@Composable
fun PauseScreen(container: AppContainer, onDone: () -> Unit) {
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(null)
    val loaded = settings ?: return
    val scope = rememberCoroutineScope()
    val listState = rememberScalingLazyListState()
    val today = ZonedDateTime.now(container.clock).toLocalDate()
    val paused = loaded.pausedThrough?.takeIf { loaded.isPaused(today) }

    fun pause(through: java.time.LocalDate?) {
        scope.launch {
            container.setPausedThrough(through)
            onDone()
        }
    }

    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(state = listState, contentPadding = padding) {
            item { ListHeader { Text("Pause reminders") } }
            if (paused != null) {
                item {
                    Text(
                        "Paused until ${formatPauseEnd(paused, today)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                item {
                    Button(
                        onClick = { pause(null) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Resume now") },
                    )
                }
            }
            item {
                FilledTonalButton(
                    onClick = { pause(today) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Rest of today") },
                )
            }
            item {
                FilledTonalButton(
                    onClick = { pause(today.plusDays(1)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Through tomorrow") },
                )
            }
            item {
                FilledTonalButton(
                    onClick = { pause(today.plusDays(6)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("One week") },
                )
            }
            item {
                FilledTonalButton(
                    onClick = { pause(Settings.PAUSED_UNTIL_RESUMED) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Until I resume") },
                )
            }
        }
    }
}
