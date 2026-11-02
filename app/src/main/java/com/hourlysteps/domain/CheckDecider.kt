package com.hourlysteps.domain

enum class CheckMode { GOAL, INACTIVITY, OFF }

sealed interface CheckResult {
    data object None : CheckResult

    /** Short of the hourly goal. [catchUpPerHour] is set only when it exceeds the hourly goal. */
    data class GoalShortfall(val need: Int, val catchUpPerHour: Int?) : CheckResult

    /** Daily goal met, inactivity reminders on, and short of the inactivity threshold. */
    data class InactivityShortfall(val need: Int) : CheckResult
}

object CheckDecider {

    fun modeFor(settings: Settings, todayTotal: Int): CheckMode = when {
        todayTotal < settings.dailyGoal -> CheckMode.GOAL
        settings.inactivityEnabled -> CheckMode.INACTIVITY
        else -> CheckMode.OFF
    }

    fun decide(settings: Settings, stepsThisHour: Int, todayTotal: Int, hour: Int): CheckResult {
        if (hour !in settings.firstHour..settings.lastHour) return CheckResult.None
        return when (modeFor(settings, todayTotal)) {
            CheckMode.GOAL -> {
                val goal = GoalCalculator.hourlyGoal(settings)
                if (stepsThisHour >= goal) {
                    CheckResult.None
                } else {
                    val catchUp = GoalCalculator.catchUpPerHour(settings, todayTotal, hour)
                        ?.takeIf { it > goal }
                    CheckResult.GoalShortfall(need = goal - stepsThisHour, catchUpPerHour = catchUp)
                }
            }
            CheckMode.INACTIVITY -> {
                val threshold = settings.inactivityThreshold
                if (stepsThisHour >= threshold) CheckResult.None
                else CheckResult.InactivityShortfall(need = threshold - stepsThisHour)
            }
            CheckMode.OFF -> CheckResult.None
        }
    }
}
