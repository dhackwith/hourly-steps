package com.hourlysteps.domain

enum class CelebrationKind {
    /** This slot's share of the daily goal was reached. */
    HOURLY_GOAL,

    /** Daily goal already met; this slot's inactivity threshold was reached. */
    INACTIVITY_GOAL,
}

data class Celebration(
    val kind: CelebrationKind,
    val stepsThisHour: Int,
    val target: Int,
    val todayTotal: Int,
    val dailyGoal: Int,
)

object CelebrationDecider {

    /**
     * Whether reaching [stepsInSlot] in the current slot deserves a celebration right now. The
     * caller passes a real slot, so paused days, days off and times outside the window never
     * reach this.
     *
     * The target follows the daily goal as it stood when the slot began, not after the latest
     * steps: otherwise the batch that crosses both the slot and the daily goal would switch the
     * slot into "goal met" mode and the slot's celebration would be lost.
     */
    fun decide(
        settings: Settings,
        plan: DayPlan,
        stepsInSlot: Int,
        todayTotal: Int,
        alreadyCelebratedThisSlot: Boolean,
    ): Celebration? {
        if (!settings.celebrate || alreadyCelebratedThisSlot) return null

        val totalAtSlotStart = todayTotal - stepsInSlot
        val (kind, target) = when {
            totalAtSlotStart < plan.dailyGoal ->
                CelebrationKind.HOURLY_GOAL to GoalCalculator.slotGoal(plan, settings.schedule.slotMinutes)
            settings.inactivityEnabled ->
                CelebrationKind.INACTIVITY_GOAL to settings.inactivityThreshold
            else -> return null
        }
        if (stepsInSlot < target) return null
        return Celebration(kind, stepsInSlot, target, todayTotal, plan.dailyGoal)
    }
}
