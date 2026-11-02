package com.hourlysteps.domain

enum class CelebrationKind {
    /** This slot's share of the daily goal was reached. */
    SLOT_GOAL,

    /** Daily goal already met; this slot's inactivity threshold was reached. */
    INACTIVITY_GOAL,

    /** Today's total reached the daily goal. */
    DAILY_GOAL,
}

data class Celebration(
    val kind: CelebrationKind,
    val stepsInSlot: Int,
    val target: Int,
    val todayTotal: Int,
    val dailyGoal: Int,
    /** Days in a row including today; only set for the daily goal. */
    val streak: Int = 0,
    /** Length of the slot being celebrated, for its wording. */
    val slotMinutes: Int = Settings.DEFAULT_SLOT_MINUTES,
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
        slot: Slot,
        stepsInSlot: Int,
        todayTotal: Int,
        alreadyCelebratedThisSlot: Boolean,
    ): Celebration? {
        if (!settings.celebrate || alreadyCelebratedThisSlot) return null

        val (kind, target) = slotTarget(settings, plan, slot, stepsInSlot, todayTotal) ?: return null
        if (stepsInSlot < target) return null
        return Celebration(kind, stepsInSlot, target, todayTotal, plan.dailyGoal, slotMinutes = slot.lengthMinutes)
    }

    /** Whether today's total has reached the daily goal and not yet been celebrated. */
    fun decideDaily(
        settings: Settings,
        plan: DayPlan,
        stepsInSlot: Int,
        todayTotal: Int,
        alreadyCelebratedToday: Boolean,
    ): Celebration? {
        if (!settings.celebrateDaily || alreadyCelebratedToday || todayTotal < plan.dailyGoal) return null
        return Celebration(CelebrationKind.DAILY_GOAL, stepsInSlot, plan.dailyGoal, todayTotal, plan.dailyGoal)
    }

    /** Steps until the next celebration, or null when none is still to come. */
    fun stepsToNext(
        settings: Settings,
        plan: DayPlan,
        slot: Slot?,
        stepsInSlot: Int,
        todayTotal: Int,
        alreadyCelebratedThisSlot: Boolean,
        alreadyCelebratedToday: Boolean,
    ): Int? {
        val toSlot = slot?.takeIf { settings.celebrate && !alreadyCelebratedThisSlot }
            ?.let { slotTarget(settings, plan, it, stepsInSlot, todayTotal) }
            ?.let { (_, target) -> target - stepsInSlot }
        val toDay = (plan.dailyGoal - todayTotal).takeIf { settings.celebrateDaily && !alreadyCelebratedToday }
        return listOfNotNull(toSlot, toDay).filter { it > 0 }.minOrNull()
    }

    private fun slotTarget(settings: Settings, plan: DayPlan, slot: Slot, stepsInSlot: Int, todayTotal: Int): Pair<CelebrationKind, Int>? {
        val totalAtSlotStart = todayTotal - stepsInSlot
        return when {
            totalAtSlotStart < plan.dailyGoal ->
                CelebrationKind.SLOT_GOAL to GoalCalculator.slotGoalFromRemaining(plan.dailyGoal, slot, totalAtSlotStart)
            settings.inactivityEnabled ->
                CelebrationKind.INACTIVITY_GOAL to GoalCalculator.inactivityGoal(settings.inactivityThreshold, slot.lengthMinutes)
            else -> null
        }
    }

    /**
     * Within this many steps of a goal, it's worth asking for steps rather than waiting for the
     * watch's next batch. Batches can be 6 minutes apart, which is about this far at a walk.
     */
    const val CLOSE_STEPS = 400
}
