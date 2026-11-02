package com.hourlysteps.domain

object GoalCalculator {

    fun slotCount(plan: DayPlan, slotMinutes: Int): Int = Slots.boundaries(plan, slotMinutes).size

    /** An even share of the daily goal. This is the plan's rough figure; see [slotGoalFromRemaining] for the day's real one. */
    fun slotGoal(plan: DayPlan, slotMinutes: Int): Int =
        ceilDiv(plan.dailyGoal, slotCount(plan, slotMinutes).coerceAtLeast(1))

    /** The inactivity threshold is a rate per hour, so a slot's share follows its length. */
    fun inactivityGoal(stepsPerHour: Int, slotLengthMinutes: Int): Int =
        ceilDiv(stepsPerHour * slotLengthMinutes, 60)

    /**
     * Steps to aim for in [slot]: what was left of the daily goal when it began, shared between it
     * and the slots after it. Steps taken during the slot don't move it, so it holds still until
     * the next slot, which then accounts for how this one went.
     *
     * Walking extra lowers it, but falling behind never raises it above the even share. [Pace]
     * says how far behind the day is instead.
     */
    fun slotGoalFromRemaining(dailyGoal: Int, slot: Slot, totalAtSlotStart: Int): Int =
        minOf(
            ceilDiv(dailyGoal, slot.count.coerceAtLeast(1)),
            ceilDiv((dailyGoal - totalAtSlotStart).coerceAtLeast(0), (slot.count - slot.index).coerceAtLeast(1)),
        )

    fun ceilDiv(numerator: Int, denominator: Int): Int =
        (numerator + denominator - 1) / denominator
}
