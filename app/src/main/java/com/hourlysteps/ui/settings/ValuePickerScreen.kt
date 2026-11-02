package com.hourlysteps.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Picker
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.rememberPickerState
import com.hourlysteps.AppContainer
import com.hourlysteps.domain.GoalCalculator
import com.hourlysteps.domain.Settings
import com.hourlysteps.ui.formatSlot
import com.hourlysteps.ui.formatSteps
import kotlinx.coroutines.launch

/** Options, labels, and the setter for one picker target. */
private data class PickerSpec(
    val title: String,
    val values: List<Int>,
    val label: (Int) -> String,
    val current: (Settings) -> Int,
    val apply: (Settings, Int) -> Settings,
    val caption: (Settings, Int) -> String? = { _, _ -> null },
)

private fun specFor(target: PickerTarget): PickerSpec = when (target) {
    PickerTarget.DAILY_GOAL -> PickerSpec(
        title = "Daily goal",
        values = (Settings.MIN_DAILY_GOAL..Settings.MAX_DAILY_GOAL step Settings.DAILY_GOAL_STEP).toList(),
        label = ::formatSteps,
        current = { it.dailyGoal },
        apply = { s, v -> s.copy(dailyGoal = v) },
        caption = { s, v -> "≈ ${formatSteps(GoalCalculator.hourlyGoal(s.copy(dailyGoal = v)))}/hour" },
    )
    PickerTarget.FIRST_HOUR -> PickerSpec(
        title = "First reminder",
        values = (0..23).toList(),
        label = ::formatSlot,
        current = { it.firstHour },
        // Keep first <= last by pushing the last reminder later if needed.
        apply = { s, v -> s.copy(firstHour = v, lastHour = maxOf(v, s.lastHour)) },
        caption = { s, v -> slotsCaption(s.copy(firstHour = v, lastHour = maxOf(v, s.lastHour))) },
    )
    PickerTarget.LAST_HOUR -> PickerSpec(
        title = "Last reminder",
        values = (0..23).toList(),
        label = ::formatSlot,
        current = { it.lastHour },
        apply = { s, v -> s.copy(lastHour = v, firstHour = minOf(v, s.firstHour)) },
        caption = { s, v -> slotsCaption(s.copy(lastHour = v, firstHour = minOf(v, s.firstHour))) },
    )
    PickerTarget.INACTIVITY_THRESHOLD -> PickerSpec(
        title = "Inactivity goal",
        values = (Settings.MIN_INACTIVITY_THRESHOLD..Settings.MAX_INACTIVITY_THRESHOLD step Settings.INACTIVITY_THRESHOLD_STEP).toList(),
        label = { "${formatSteps(it)}" },
        current = { it.inactivityThreshold },
        apply = { s, v -> s.copy(inactivityThreshold = v) },
        caption = { _, _ -> "steps per hour" },
    )
}

private fun slotsCaption(s: Settings): String =
    "${GoalCalculator.slotCount(s)} reminders · ≈ ${formatSteps(GoalCalculator.hourlyGoal(s))}/hour"

@Composable
fun ValuePickerScreen(container: AppContainer, target: PickerTarget, onDone: () -> Unit) {
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(null)
    val loaded = settings ?: return
    val spec = remember(target) { specFor(target) }
    val initialIndex = remember(loaded, spec) {
        spec.values.indexOf(spec.current(loaded)).takeIf { it >= 0 }
            ?: spec.values.indexOfFirst { it >= spec.current(loaded) }.coerceAtLeast(0)
    }
    val state = rememberPickerState(
        initialNumberOfOptions = spec.values.size,
        initiallySelectedIndex = initialIndex,
        shouldRepeatOptions = target == PickerTarget.FIRST_HOUR || target == PickerTarget.LAST_HOUR,
    )
    val scope = rememberCoroutineScope()
    val selected = spec.values[state.selectedOptionIndex]

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(top = 24.dp, bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(spec.title, style = MaterialTheme.typography.titleSmall)
            Picker(
                state = state,
                contentDescription = { spec.label(selected) },
                modifier = Modifier.fillMaxWidth().height(110.dp),
            ) { index ->
                Text(spec.label(spec.values[index]), style = MaterialTheme.typography.displaySmall)
            }
            spec.caption(loaded, selected)?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        EdgeButton(
            onClick = {
                scope.launch {
                    container.settingsRepository.update { spec.apply(it, selected) }
                    container.ensureArmed()
                    onDone()
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Text("Save")
        }
    }
}
