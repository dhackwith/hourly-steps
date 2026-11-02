package com.hourlysteps.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalCalculatorTest {

    private val plan = DayPlan() // 7000/day, 8:00 to 20:00

    private fun minute(hour: Int) = hour * 60

    @Test
    fun `default window is 12 slots`() {
        assertEquals(12, GoalCalculator.slotCount(plan, 60))
    }

    @Test
    fun `default slot goal is 584`() {
        assertEquals(584, GoalCalculator.slotGoal(plan, 60))
    }

    @Test
    fun `window through 8 50 PM is 13 slots and 539 per hour`() {
        val longer = plan.withLastHour(20)
        assertEquals(13, GoalCalculator.slotCount(longer, 60))
        assertEquals(539, GoalCalculator.slotGoal(longer, 60))
    }

    @Test
    fun `interval length changes the slot goal`() {
        assertEquals(292, GoalCalculator.slotGoal(plan, 30))
        assertEquals(1167, GoalCalculator.slotGoal(plan, 120))
    }

    @Test
    fun `slot goal rounds up so slots sum to at least the daily goal`() {
        val rounded = plan.copy(dailyGoal = 7001)
        val perSlot = GoalCalculator.slotGoal(rounded, 60)
        assertEquals(584, perSlot)
        assertTrue(perSlot * GoalCalculator.slotCount(rounded, 60) >= rounded.dailyGoal)
    }

    @Test
    fun `exact division has no rounding`() {
        assertEquals(500, GoalCalculator.slotGoal(plan.copy(dailyGoal = 6000), 60))
    }

    @Test
    fun `single slot window puts the whole goal in one slot`() {
        val single = DayPlan(startMinute = minute(12), endMinute = minute(13))
        assertEquals(1, GoalCalculator.slotCount(single, 60))
        assertEquals(7000, GoalCalculator.slotGoal(single, 60))
    }

    @Test
    fun `an empty window does not divide by zero`() {
        assertEquals(7000, GoalCalculator.slotGoal(DayPlan(startMinute = 600, endMinute = 600), 60))
    }

    @Test
    fun `remaining slots counts current through last inclusive`() {
        assertEquals(12, GoalCalculator.remainingSlots(plan, 60, minute(8)))
        assertEquals(10, GoalCalculator.remainingSlots(plan, 60, minute(10) + 50))
        assertEquals(1, GoalCalculator.remainingSlots(plan, 60, minute(19)))
        assertEquals(0, GoalCalculator.remainingSlots(plan, 60, minute(20)))
    }

    @Test
    fun `remaining slots before the window counts the whole window`() {
        assertEquals(12, GoalCalculator.remainingSlots(plan, 60, minute(5)))
    }

    @Test
    fun `remaining slots follows the slot length`() {
        assertEquals(20, GoalCalculator.remainingSlots(plan, 30, minute(10)))
    }

    @Test
    fun `catch up matches the plan example when behind`() {
        // 400 steps by the 10:50 check, 10 slots left -> ceil(6600 / 10) = 660
        assertEquals(660, GoalCalculator.catchUpPerSlot(plan, 60, 400, minute(10)))
    }

    @Test
    fun `catch up is below the slot goal when ahead of pace`() {
        // 1200 steps by 10:50 -> ceil(5800 / 10) = 580, which is under 584
        assertEquals(580, GoalCalculator.catchUpPerSlot(plan, 60, 1200, minute(10)))
    }

    @Test
    fun `catch up in the last slot is the whole deficit`() {
        assertEquals(1500, GoalCalculator.catchUpPerSlot(plan, 60, 5500, minute(19)))
    }

    @Test
    fun `catch up is null when the goal is met or the window is over`() {
        assertNull(GoalCalculator.catchUpPerSlot(plan, 60, 7000, minute(10)))
        assertNull(GoalCalculator.catchUpPerSlot(plan, 60, 9000, minute(10)))
        assertNull(GoalCalculator.catchUpPerSlot(plan, 60, 100, minute(20)))
    }
}
