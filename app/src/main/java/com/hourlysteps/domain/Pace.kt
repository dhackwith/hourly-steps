package com.hourlysteps.domain

/** How the day is going against an even spread of the daily goal across its slots. */
object Pace {

    /** Steps to have by the end of [slot] to be on track: that slot's running share of the goal. */
    fun target(dailyGoal: Int, slot: Slot): Int =
        GoalCalculator.ceilDiv(dailyGoal * (slot.index + 1), slot.count.coerceAtLeast(1))

    /** Steps ahead of pace, or negative when behind. */
    fun gap(dailyGoal: Int, slot: Slot, todayTotal: Int): Int = todayTotal - target(dailyGoal, slot)
}
