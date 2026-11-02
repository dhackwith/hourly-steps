package com.hourlysteps.domain

/** How the day is going against an even spread of the daily goal across its slots. */
object Pace {

    /**
     * Steps to have by [minuteOfDay] to be on track: the finished slots' share of the goal, plus
     * the part of [slot] that has passed. It follows the clock so that being on track reads as on
     * pace at any moment, not only near a slot's end.
     */
    fun target(dailyGoal: Int, slot: Slot, minuteOfDay: Int): Int {
        val length = slot.lengthMinutes.coerceAtLeast(1)
        val elapsed = (minuteOfDay - slot.startMinute).coerceIn(0, length)
        return GoalCalculator.ceilDiv(dailyGoal * (slot.index * length + elapsed), slot.count.coerceAtLeast(1) * length)
    }

    /** Steps ahead of pace, or negative when behind. */
    fun gap(dailyGoal: Int, slot: Slot, minuteOfDay: Int, todayTotal: Int): Int =
        todayTotal - target(dailyGoal, slot, minuteOfDay)
}
