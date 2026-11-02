package com.hourlysteps.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
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

    private val slots = Slots.forDay(plan, 60, LocalDate.of(2026, 6, 10))

    /** The slot starting at [hour]. */
    private fun slot(hour: Int) = slots.first { it.startMinute == hour * 60 }

    @Test
    fun `a day with no steps yet asks for the even share`() {
        assertEquals(584, GoalCalculator.slotGoalFromRemaining(7000, slots.first(), 0))
    }

    @Test
    fun `walking extra lowers the goal for the hours left`() {
        // 2,400 by 11 AM, 4,600 to go across 9 hours.
        assertEquals(512, GoalCalculator.slotGoalFromRemaining(7000, slot(11), 2400))
    }

    @Test
    fun `falling behind never raises the goal above the even share`() {
        assertEquals(584, GoalCalculator.slotGoalFromRemaining(7000, slot(11), 500))
        assertEquals(584, GoalCalculator.slotGoalFromRemaining(7000, slot(11), 0))
    }

    @Test
    fun `the goal is the even share when exactly on track`() {
        assertEquals(584, GoalCalculator.slotGoalFromRemaining(7000, slot(11), 1752))
    }

    @Test
    fun `the last slot asks for what's left, up to the even share`() {
        assertEquals(584, GoalCalculator.slotGoalFromRemaining(7000, slot(19), 5500))
        assertEquals(200, GoalCalculator.slotGoalFromRemaining(7000, slot(19), 6800))
    }

    @Test
    fun `nothing is asked for once the daily goal is met`() {
        assertEquals(0, GoalCalculator.slotGoalFromRemaining(7000, slot(11), 7000))
        assertEquals(0, GoalCalculator.slotGoalFromRemaining(7000, slot(11), 9000))
    }

    @Test
    fun `reaching every slot's goal exactly reaches the daily goal`() {
        for (dailyGoal in listOf(7000, 7001, 10000, 13)) {
            var total = 0
            for (slot in slots) total += GoalCalculator.slotGoalFromRemaining(dailyGoal, slot, total)
            assertEquals(dailyGoal, total)
        }
    }

    @Test
    fun `the goal follows the slot length`() {
        // 2,400 by 11 AM, 4,600 to go across 18 half hours.
        val halfHours = Slots.forDay(plan, 30, LocalDate.of(2026, 6, 10))
        assertEquals(256, GoalCalculator.slotGoalFromRemaining(7000, halfHours.first { it.startMinute == 11 * 60 }, 2400))
    }
}
