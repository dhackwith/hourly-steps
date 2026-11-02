package com.hourlysteps.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hourlysteps.HourlyStepsApp
import com.hourlysteps.domain.Celebration
import com.hourlysteps.domain.CelebrationKind
import com.hourlysteps.domain.GoalCalculator
import kotlinx.coroutines.launch

/**
 * Debug-only: trigger a celebration from the background, as if this hour's goal had just been
 * reached.
 *
 * adb shell am broadcast -a com.hourlysteps.debug.CELEBRATE -p com.hourlysteps
 */
class DebugCelebrateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as HourlyStepsApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                val settings = container.settingsRepository.current()
                val goal = GoalCalculator.hourlyGoal(settings)
                container.celebrationManager.celebrate(
                    Celebration(CelebrationKind.HOURLY_GOAL, goal + 23, goal, settings.dailyGoal / 2, settings.dailyGoal),
                )
            } finally {
                pending.finish()
            }
        }
    }
}
