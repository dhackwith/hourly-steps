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
 * Debug-only: trigger a celebration from the background, as if a goal had just been reached.
 *
 * adb shell am broadcast -a com.hourlysteps.debug.CELEBRATE -p com.hourlysteps
 * adb shell am broadcast -a com.hourlysteps.debug.CELEBRATE --ez daily true -p com.hourlysteps
 */
class DebugCelebrateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as HourlyStepsApp).container
        val daily = intent.getBooleanExtra("daily", false)
        val pending = goAsync()
        container.appScope.launch {
            try {
                val settings = container.settingsRepository.current()
                val plan = settings.schedule.planA
                val goal = GoalCalculator.slotGoal(plan, settings.schedule.slotMinutes)
                val celebration = if (daily) {
                    Celebration(
                        kind = CelebrationKind.DAILY_GOAL,
                        stepsInSlot = goal,
                        target = plan.dailyGoal,
                        todayTotal = plan.dailyGoal + 40,
                        dailyGoal = plan.dailyGoal,
                        streak = container.streakRepository.current().current + 1,
                    )
                } else {
                    Celebration(CelebrationKind.SLOT_GOAL, goal + 23, goal, plan.dailyGoal / 2, plan.dailyGoal, slotMinutes = settings.schedule.slotMinutes)
                }
                container.celebrationManager.celebrate(celebration)
            } finally {
                pending.finish()
            }
        }
    }
}
