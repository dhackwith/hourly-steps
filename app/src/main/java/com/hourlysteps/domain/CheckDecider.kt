package com.hourlysteps.domain

enum class CheckMode { GOAL, INACTIVITY, OFF }

sealed interface CheckResult {
    data object None : CheckResult

    /** Short of the slot goal. [catchUpPerSlot] is set only when it exceeds the slot goal. */
    data class GoalShortfall(val need: Int, val catchUpPerSlot: Int?) : CheckResult

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
        val slotMinutes = settings.schedule.slotMinutes
        return when (modeFor(settings, plan, todayTotal)) {
            CheckMode.GOAL -> {
                val goal = GoalCalculator.slotGoal(plan, slotMinutes)
                if (stepsInSlot >= goal) {
                    CheckResult.None
                } else {
                    val catchUp = GoalCalculator.catchUpPerSlot(plan, slotMinutes, todayTotal, slot.startMinute)
                        ?.takeIf { it > goal }
                    CheckResult.GoalShortfall(need = goal - stepsInSlot, catchUpPerSlot = catchUp)
                }
            }
            CheckMode.INACTIVITY -> {
                val threshold = settings.inactivityThreshold
                if (stepsInSlot >= threshold) CheckResult.None
                else CheckResult.InactivityShortfall(need = threshold - stepsInSlot)
            }
            CheckMode.OFF -> CheckResult.None
        }
    }
}
