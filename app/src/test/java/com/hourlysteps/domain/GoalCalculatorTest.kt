package com.hourlysteps.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoalCalculatorTest {

    private val defaults = Settings()

    @Test
    fun `default window is 12 slots`() {
        assertEquals(12, GoalCalculator.slotCount(defaults))
    }

    @Test
    fun `default hourly goal is 584`() {
        assertEquals(584, GoalCalculator.hourlyGoal(defaults))
    }

    @Test
    fun `window through 8 50 PM is 13 slots and 539 per hour`() {
        val s = defaults.copy(lastHour = 20)
        assertEquals(13, GoalCalculator.slotCount(s))
        assertEquals(539, GoalCalculator.hourlyGoal(s))
    }

    @Test
    fun `hourly goal rounds up so slots sum to at least the daily goal`() {
        val s = defaults.copy(dailyGoal = 7001)
        val perHour = GoalCalculator.hourlyGoal(s)
        assertEquals(584, perHour)
        assert(perHour * GoalCalculator.slotCount(s) >= s.dailyGoal)
    }

    @Test
    fun `exact division has no rounding`() {
        assertEquals(500, GoalCalculator.hourlyGoal(defaults.copy(dailyGoal = 6000)))
    }

    @Test
    fun `single slot window puts the whole goal in one hour`() {
        val s = defaults.copy(firstHour = 12, lastHour = 12)
        assertEquals(1, GoalCalculator.slotCount(s))
        assertEquals(7000, GoalCalculator.hourlyGoal(s))
    }

    @Test
    fun `remaining slots counts current through last inclusive`() {
        assertEquals(12, GoalCalculator.remainingSlots(defaults, 8))
        assertEquals(10, GoalCalculator.remainingSlots(defaults, 10))
        assertEquals(1, GoalCalculator.remainingSlots(defaults, 19))
        assertEquals(0, GoalCalculator.remainingSlots(defaults, 20))
    }

    @Test
    fun `remaining slots before the window counts the whole window`() {
        assertEquals(12, GoalCalculator.remainingSlots(defaults, 5))
    }

    @Test
    fun `catch up matches the plan example when behind`() {
        // 400 steps by the 10:50 check, 10 slots left -> ceil(6600 / 10) = 660
        assertEquals(660, GoalCalculator.catchUpPerHour(defaults, todayTotal = 400, hour = 10))
    }

    @Test
    fun `catch up is below the hourly goal when ahead of pace`() {
        // 1200 steps by 10:50 -> ceil(5800 / 10) = 580, which is under 584
        assertEquals(580, GoalCalculator.catchUpPerHour(defaults, todayTotal = 1200, hour = 10))
    }

    @Test
    fun `catch up in the last slot is the whole deficit`() {
        assertEquals(1500, GoalCalculator.catchUpPerHour(defaults, todayTotal = 5500, hour = 19))
    }

    @Test
    fun `catch up is null when the goal is met or the window is over`() {
        assertNull(GoalCalculator.catchUpPerHour(defaults, todayTotal = 7000, hour = 10))
        assertNull(GoalCalculator.catchUpPerHour(defaults, todayTotal = 9000, hour = 10))
        assertNull(GoalCalculator.catchUpPerHour(defaults, todayTotal = 100, hour = 20))
    }
}
