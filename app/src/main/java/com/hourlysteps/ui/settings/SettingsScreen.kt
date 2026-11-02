package com.hourlysteps.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.hourlysteps.AppContainer
import com.hourlysteps.BuildConfig
import com.hourlysteps.domain.Celebration
import com.hourlysteps.domain.CelebrationKind
import com.hourlysteps.domain.GoalCalculator
import com.hourlysteps.domain.PlanId
import com.hourlysteps.domain.ScheduleMode
import com.hourlysteps.domain.Settings
import com.hourlysteps.domain.SlotWording
import com.hourlysteps.domain.VibrationStrength
import com.hourlysteps.ui.formatPauseEnd
import com.hourlysteps.ui.formatPlan
import com.hourlysteps.ui.formatSteps
import com.hourlysteps.ui.formatWeek
import java.time.ZonedDateTime
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    container: AppContainer,
    onOpenPlan: (PlanId) -> Unit,
    onOpenWeek: () -> Unit,
    onOpenPause: () -> Unit,
    onPickInactivity: () -> Unit,
    onOpenPermissions: () -> Unit,
) {
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(Settings())
    val scope = rememberCoroutineScope()
    val listState = rememberScalingLazyListState()
    val exactOk = container.reminderScheduler.canScheduleExact()
    val permsOk = container.hasActivityRecognition() && container.hasNotificationPermission()
    val schedule = settings.schedule
    val twoPlans = schedule.mode == ScheduleMode.TWO_PLANS

    fun update(transform: (Settings) -> Settings) {
        scope.launch {
            container.settingsRepository.update(transform)
            container.ensureArmed()
        }
    }

    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(state = listState, contentPadding = padding) {
            item { ListHeader { Text("Settings") } }

            if (!permsOk || !exactOk) {
                item {
                    Button(
                        onClick = onOpenPermissions,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Permissions needed") },
                        secondaryLabel = {
                            Text(if (!permsOk) "Reminders are off" else "Reminders may be a few minutes early")
                        },
                    )
                }
            }

            item { ListHeader { Text("Schedule") } }
            item {
                FilledTonalButton(
                    onClick = {
                        val next = if (twoPlans) ScheduleMode.EVERY_DAY else ScheduleMode.TWO_PLANS
                        update { it.copy(schedule = it.schedule.copy(mode = next)) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Plans") },
                    secondaryLabel = { Text(if (twoPlans) "Two plans" else "One plan") },
                )
            }
            item {
                FilledTonalButton(
                    onClick = { onOpenPlan(PlanId.A) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (twoPlans) "Plan A" else "Daily plan") },
                    secondaryLabel = { Text(formatPlan(schedule.planA, schedule.slotMinutes)) },
                )
            }
            if (twoPlans) {
                item {
                    FilledTonalButton(
                        onClick = { onOpenPlan(PlanId.B) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Plan B") },
                        secondaryLabel = { Text(formatPlan(schedule.planB, schedule.slotMinutes)) },
                    )
                }
            }
            item {
                FilledTonalButton(
                    onClick = onOpenWeek,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Week") },
                    secondaryLabel = { Text(formatWeek(schedule)) },
                )
            }
            item {
                FilledTonalButton(
                    onClick = {
                        val options = Settings.SLOT_MINUTE_OPTIONS
                        val next = options[(options.indexOf(schedule.slotMinutes) + 1) % options.size]
                        scope.launch { container.setSlotMinutes(next) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Reminder interval") },
                    secondaryLabel = { Text(SlotWording.interval(schedule.slotMinutes)) },
                )
            }

            item { ListHeader { Text("Reminders") } }
            item {
                val today = ZonedDateTime.now(container.clock).toLocalDate()
                val active = settings.pausedThrough?.takeIf { settings.isPaused(today) }
                FilledTonalButton(
                    onClick = onOpenPause,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Pause reminders") },
                    secondaryLabel = { Text(active?.let { "Until ${formatPauseEnd(it, today)}" } ?: "Off") },
                )
            }
            item {
                SwitchButton(
                    checked = settings.inactivityEnabled,
                    onCheckedChange = { checked -> update { it.copy(inactivityEnabled = checked) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Inactivity reminders") },
                    secondaryLabel = { Text("After daily goal is met") },
                )
            }
            item {
                FilledTonalButton(
                    onClick = onPickInactivity,
                    enabled = settings.inactivityEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Inactivity goal") },
                    secondaryLabel = { Text("${formatSteps(settings.inactivityThreshold)} steps/hour") },
                )
            }
            item {
                FilledTonalButton(
                    onClick = {
                        val next = VibrationStrength.entries[(settings.vibration.ordinal + 1) % VibrationStrength.entries.size]
                        update { it.copy(vibration = next) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Vibration") },
                    secondaryLabel = { Text(settings.vibration.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
            item {
                SwitchButton(
                    checked = settings.secondNudge,
                    onCheckedChange = { checked -> update { it.copy(secondNudge = checked) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Second nudge") },
                    secondaryLabel = { Text("Buzz again near the end if short") },
                )
            }
            item {
                SwitchButton(
                    checked = settings.skipDuringWorkouts,
                    onCheckedChange = { checked -> update { it.copy(skipDuringWorkouts = checked) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Skip during workouts") },
                )
            }
            item {
                SwitchButton(
                    checked = settings.skipWhileAsleep,
                    onCheckedChange = { checked -> update { it.copy(skipWhileAsleep = checked) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Skip while asleep") },
                )
            }
            item {
                SwitchButton(
                    checked = settings.skipWhileCharging,
                    onCheckedChange = { checked -> update { it.copy(skipWhileCharging = checked) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Skip while charging") },
                )
            }

            item { ListHeader { Text("Celebrations") } }
            item {
                SwitchButton(
                    checked = settings.celebrate,
                    onCheckedChange = { checked -> update { it.copy(celebrate = checked) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(SlotWording.goalName(schedule.slotMinutes)) },
                    secondaryLabel = { Text("Fireworks when you reach it") },
                )
            }
            item {
                SwitchButton(
                    checked = settings.celebrateDaily,
                    onCheckedChange = { checked -> update { it.copy(celebrateDaily = checked) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Daily goal") },
                    secondaryLabel = { Text("Gold fireworks and your streak") },
                )
            }

            if (BuildConfig.DEBUG) {
                item { ListHeader { Text("Debug") } }
                item {
                    FilledTonalButton(
                        onClick = { scope.launch { container.reminderCheck.run(scheduledAt = null, force = true) } },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Run check now") },
                    )
                }
                item {
                    FilledTonalButton(
                        onClick = {
                            val plan = schedule.planA
                            val goal = GoalCalculator.slotGoal(plan, schedule.slotMinutes)
                            scope.launch {
                                container.celebrationManager.celebrate(
                                    Celebration(
                                        kind = CelebrationKind.SLOT_GOAL,
                                        stepsInSlot = goal + 37,
                                        target = goal,
                                        todayTotal = plan.dailyGoal / 2,
                                        dailyGoal = plan.dailyGoal,
                                        slotMinutes = schedule.slotMinutes,
                                    ),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Show celebration") },
                    )
                }
            }
        }
    }
}
