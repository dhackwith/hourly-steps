package com.hourlysteps.domain

enum class CheckMode { GOAL, INACTIVITY, OFF }

sealed interface CheckResult {
    data object None : CheckResult

    /** Short of the slot goal. */
    data class GoalShortfall(val need: Int) : CheckResult

    /** Daily goal met, inactivity reminders on, and short of the inactivity threshold. */
    data class InactivityShortfall(val need: Int) : CheckResult
}

object CheckDecider {

    fun modeFor(settings: Settings, plan: DayPlan, todayTotal: Int): CheckMode = when {
        todayTotal < plan.dailyGoal -> CheckMode.GOAL
        settings.inactivityEnabled -> CheckMode.INACTIVITY
        else -> CheckMode.OFF
    }

    /** What the check for [slot] should do. The caller handles paused days and days off. */
    fun decide(settings: Settings, plan: DayPlan, slot: Slot, stepsInSlot: Int, todayTotal: Int): CheckResult {
        return when (modeFor(settings, plan, todayTotal)) {
            CheckMode.GOAL -> {
                val goal = GoalCalculator.slotGoalFromRemaining(plan.dailyGoal, slot, todayTotal - stepsInSlot)
                if (stepsInSlot >= goal) CheckResult.None
                else CheckResult.GoalShortfall(need = goal - stepsInSlot)
            }
            CheckMode.INACTIVITY -> {
                val threshold = GoalCalculator.inactivityGoal(settings.inactivityThreshold, slot.lengthMinutes)
                if (stepsInSlot >= threshold) CheckResult.None
                else CheckResult.InactivityShortfall(need = threshold - stepsInSlot)
            }
            CheckMode.OFF -> CheckResult.None
        }
    }
}
