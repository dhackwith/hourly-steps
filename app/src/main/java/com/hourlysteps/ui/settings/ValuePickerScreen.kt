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
import com.hourlysteps.data.settings.SettingsRepository
import com.hourlysteps.domain.DayPlan
import com.hourlysteps.domain.PlanId
import com.hourlysteps.domain.Settings
import com.hourlysteps.reminder.ReminderControls
import com.hourlysteps.ui.formatFirstCheck
import com.hourlysteps.ui.formatLastCheck
import com.hourlysteps.ui.formatPickerCaption
import com.hourlysteps.ui.formatSlotGoal
import com.hourlysteps.ui.formatSteps
import kotlinx.coroutines.launch

enum class PickerTarget { DAILY_GOAL, FIRST_HOUR, LAST_HOUR, INACTIVITY_THRESHOLD }

/** Options, labels, and the setter for one picker target. */
private data class PickerSpec(
    val title: String,
    val values: List<Int>,
    val label: (Settings, Int) -> String,
    val current: (Settings) -> Int,
    val apply: (Settings, Int) -> Settings,
    val caption: (Settings, Int) -> String? = { _, _ -> null },
)

private fun Settings.withPlan(id: PlanId, transform: (DayPlan) -> DayPlan): Settings =
    copy(schedule = schedule.withPlan(id, transform(schedule.plan(id))))

private fun specFor(target: PickerTarget, planId: PlanId): PickerSpec = when (target) {
    PickerTarget.DAILY_GOAL -> {
        val edit = { s: Settings, v: Int -> s.withPlan(planId) { it.copy(dailyGoal = v) } }
        PickerSpec(
            title = "Daily goal",
            values = (Settings.MIN_DAILY_GOAL..Settings.MAX_DAILY_GOAL step Settings.DAILY_GOAL_STEP).toList(),
            label = { _, v -> formatSteps(v) },
            current = { it.schedule.plan(planId).dailyGoal },
            apply = edit,
            caption = { s, v ->
                val edited = edit(s, v).schedule
                formatSlotGoal(edited.plan(planId), edited.slotMinutes)
            },
        )
    }
    PickerTarget.FIRST_HOUR -> {
        // Keeps first <= last by pushing the last reminder later if needed.
        val edit = { s: Settings, v: Int -> s.withPlan(planId) { it.withFirstHour(v) } }
        PickerSpec(
            title = "First reminder",
            values = (0..23).toList(),
            label = { s, v -> formatFirstCheck(edit(s, v).schedule.plan(planId), s.schedule.slotMinutes) },
            current = { it.schedule.plan(planId).firstHour },
            apply = edit,
            caption = { s, v -> slotsCaption(edit(s, v), planId) },
        )
    }
    PickerTarget.LAST_HOUR -> {
        val edit = { s: Settings, v: Int -> s.withPlan(planId) { it.withLastHour(v) } }
        PickerSpec(
            title = "Last reminder",
            values = (0..23).toList(),
            label = { s, v -> formatLastCheck(edit(s, v).schedule.plan(planId), s.schedule.slotMinutes) },
            current = { it.schedule.plan(planId).lastHour },
            apply = edit,
            caption = { s, v -> slotsCaption(edit(s, v), planId) },
        )
    }
    PickerTarget.INACTIVITY_THRESHOLD -> PickerSpec(
        title = "Inactivity goal",
        values = (Settings.MIN_INACTIVITY_THRESHOLD..Settings.MAX_INACTIVITY_THRESHOLD step Settings.INACTIVITY_THRESHOLD_STEP).toList(),
        label = { _, v -> formatSteps(v) },
        current = { it.inactivityThreshold },
        apply = { s, v -> s.copy(inactivityThreshold = v) },
        caption = { _, _ -> "steps per hour" },
    )
}

private fun slotsCaption(s: Settings, planId: PlanId): String {
    return formatPickerCaption(s.schedule.plan(planId), s.schedule.slotMinutes)
}

@Composable
fun ValuePickerScreen(settingsRepository: SettingsRepository, controls: ReminderControls, target: PickerTarget, planId: PlanId, onDone: () -> Unit) {
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(null)
    val loaded = settings ?: return
    val spec = remember(target, planId) { specFor(target, planId) }
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
                contentDescription = { spec.label(loaded, selected) },
                modifier = Modifier.fillMaxWidth().height(110.dp),
            ) { index ->
                Text(spec.label(loaded, spec.values[index]), style = MaterialTheme.typography.displaySmall)
            }
            spec.caption(loaded, selected)?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        EdgeButton(
            onClick = {
                scope.launch {
                    settingsRepository.update { spec.apply(it, selected) }
                    controls.ensureArmed()
                    onDone()
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Text("Save")
        }
    }
}
