package com.hourlysteps.domain

import kotlin.math.max

object GoalCalculator {

    /** Number of :50 checks in the window, counted inclusively. */
    fun slotCount(firstHour: Int, lastHour: Int): Int = max(1, lastHour - firstHour + 1)

    fun slotCount(settings: Settings): Int = slotCount(settings.firstHour, settings.lastHour)

    /** Steps required in each slot so that all slots together reach the daily goal. */
    fun hourlyGoal(settings: Settings): Int =
        ceilDiv(settings.dailyGoal, slotCount(settings))

    /** Slots from [hour] through the last slot, inclusive. Zero once the window is over. */
    fun remainingSlots(settings: Settings, hour: Int): Int {
        val from = max(hour, settings.firstHour)
        return if (from > settings.lastHour) 0 else settings.lastHour - from + 1
    }

    /**
     * Steps per slot needed over the remaining slots to still reach the daily goal.
     * Null when no slots remain or the goal is already met.
     */
    fun catchUpPerHour(settings: Settings, todayTotal: Int, hour: Int): Int? {
        val remaining = remainingSlots(settings, hour)
        val deficit = settings.dailyGoal - todayTotal
        if (remaining == 0 || deficit <= 0) return null
        return ceilDiv(deficit, remaining)
    }

    fun ceilDiv(numerator: Int, denominator: Int): Int =
        (numerator + denominator - 1) / denominator
}
