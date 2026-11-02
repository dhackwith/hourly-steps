package com.hourlysteps.domain

object GoalCalculator {

    fun slotCount(plan: DayPlan, slotMinutes: Int): Int = Slots.boundaries(plan, slotMinutes).size

    /** Steps required in each slot so that all slots together reach the plan's daily goal. */
    fun slotGoal(plan: DayPlan, slotMinutes: Int): Int =
        ceilDiv(plan.dailyGoal, slotCount(plan, slotMinutes).coerceAtLeast(1))

    /** The inactivity threshold is a rate per hour, so a slot's share follows its length. */
    fun inactivityGoal(stepsPerHour: Int, slotLengthMinutes: Int): Int =
        ceilDiv(stepsPerHour * slotLengthMinutes, 60)

    /** Slots still open at [minuteOfDay], including the current one. Zero once the window is over. */
    fun remainingSlots(plan: DayPlan, slotMinutes: Int, minuteOfDay: Int): Int =
        Slots.boundaries(plan, slotMinutes).count { (_, end) -> end > minuteOfDay }

    /**
     * Steps per slot needed over the remaining slots to still reach the daily goal.
     * Null when no slots remain or the goal is already met.
     */
    fun catchUpPerSlot(plan: DayPlan, slotMinutes: Int, todayTotal: Int, minuteOfDay: Int): Int? {
        val remaining = remainingSlots(plan, slotMinutes, minuteOfDay)
        val deficit = plan.dailyGoal - todayTotal
        if (remaining == 0 || deficit <= 0) return null
        return ceilDiv(deficit, remaining)
    }

    fun ceilDiv(numerator: Int, denominator: Int): Int =
        (numerator + denominator - 1) / denominator
}
