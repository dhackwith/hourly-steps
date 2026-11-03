package com.hourlysteps.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.hourlysteps.data.settings.SettingsRepository
import com.hourlysteps.domain.Settings
import com.hourlysteps.domain.StretchGoals
import com.hourlysteps.reminder.ReminderControls
import com.hourlysteps.ui.formatSteps
import kotlinx.coroutines.launch

/** Up to three goals past the daily goal, each counted on top of the one before. */
@Composable
fun StretchGoalsScreen(settingsRepository: SettingsRepository, controls: ReminderControls, onPick: (PickerTarget) -> Unit) {
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(null)
    val loaded = settings ?: return
    val goals = loaded.stretchGoals
    val scope = rememberCoroutineScope()
    val listState = rememberScalingLazyListState()

    fun edit(transform: (List<Int>) -> List<Int>) {
        scope.launch {
            settingsRepository.update { it.copy(stretchGoals = transform(it.stretchGoals)) }
            controls.ensureArmed()
        }
    }

    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(state = listState, contentPadding = padding) {
            item { ListHeader { Text("Stretch goals") } }
            goals.forEachIndexed { index, goal ->
                item {
                    FilledTonalButton(
                        onClick = { onPick(PickerTarget.stretchGoal(index)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(StretchGoals.name(index + 1, goals.size)) },
                        secondaryLabel = { Text("${formatSteps(goal)} more steps") },
                    )
                }
            }
            if (goals.size < Settings.MAX_STRETCH_GOALS) {
                item {
                    FilledTonalButton(
                        onClick = { edit { it + Settings.DEFAULT_STRETCH_GOAL } },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Add stretch goal") },
                        secondaryLabel = { Text(if (goals.isEmpty()) "After your daily goal" else "After the last one") },
                    )
                }
            }
            if (goals.isNotEmpty()) {
                item {
                    FilledTonalButton(
                        onClick = { edit { it.dropLast(1) } },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Remove the last one") },
                    )
                }
            }
        }
    }
}
