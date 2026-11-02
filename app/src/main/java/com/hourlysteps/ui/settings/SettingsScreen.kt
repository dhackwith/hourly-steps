package com.hourlysteps.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.hourlysteps.AppContainer
import com.hourlysteps.BuildConfig
import com.hourlysteps.domain.GoalCalculator
import com.hourlysteps.domain.Settings
import com.hourlysteps.domain.VibrationStrength
import com.hourlysteps.ui.formatSlot
import com.hourlysteps.ui.formatSteps
import kotlinx.coroutines.launch

enum class PickerTarget { DAILY_GOAL, FIRST_HOUR, LAST_HOUR, INACTIVITY_THRESHOLD }

@Composable
fun SettingsScreen(
    container: AppContainer,
    onPick: (PickerTarget) -> Unit,
    onOpenPermissions: () -> Unit,
) {
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(Settings())
    val scope = rememberCoroutineScope()
    val listState = rememberScalingLazyListState()
    val exactOk = container.reminderScheduler.canScheduleExact()
    val permsOk = container.hasActivityRecognition() && container.hasNotificationPermission()

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
                            Text(if (!permsOk) "Reminders are off" else "Reminders may be a few minutes late")
                        },
                    )
                }
            }

            item {
                FilledTonalButton(
                    onClick = { onPick(PickerTarget.DAILY_GOAL) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Daily goal") },
                    secondaryLabel = { Text("${formatSteps(settings.dailyGoal)} steps") },
                )
            }
            item {
                FilledTonalButton(
                    onClick = { onPick(PickerTarget.FIRST_HOUR) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("First reminder") },
                    secondaryLabel = { Text(formatSlot(settings.firstHour)) },
                )
            }
            item {
                FilledTonalButton(
                    onClick = { onPick(PickerTarget.LAST_HOUR) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Last reminder") },
                    secondaryLabel = { Text(formatSlot(settings.lastHour)) },
                )
            }
            item {
                Text(
                    "${GoalCalculator.slotCount(settings)} reminders · ≈ ${formatSteps(GoalCalculator.hourlyGoal(settings))} steps/hour",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    onClick = { onPick(PickerTarget.INACTIVITY_THRESHOLD) },
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
                    checked = settings.skipWhileCharging,
                    onCheckedChange = { checked -> update { it.copy(skipWhileCharging = checked) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Skip while charging") },
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
            }
        }
    }
}
