package com.hourlysteps.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CelebrationDeciderTest {

    private val defaults = Settings() // 7000/day, slot goal 584, inactivity off, celebrate on
    private val plan = defaults.schedule.planA

    private fun decide(s: Settings = defaults, steps: Int, total: Int, already: Boolean = false, p: DayPlan = plan) =
        CelebrationDecider.decide(s, p, s.schedule.slotMinutes, steps, total, already)

    @Test
    fun `reaching the slot goal celebrates`() {
        assertEquals(
            Celebration(CelebrationKind.SLOT_GOAL, 584, 584, 2000, 7000),
            decide(steps = 584, total = 2000),
        )
    }

    @Test
    fun `below the slot goal does not celebrate`() {
        assertNull(decide(steps = 583, total = 2000))
    }

    @Test
    fun `only once per slot`() {
        assertNull(decide(steps = 900, total = 2000, already = true))
    }

    @Test
    fun `disabled setting never celebrates`() {
        assertNull(decide(s = defaults.copy(celebrate = false), steps = 900, total = 2000))
    }

    @Test
    fun `crossing the daily goal in the same batch still celebrates the slot`() {
        // 6,600 before this slot, 600 in it: the daily goal and the slot goal fall together.
        val c = decide(steps = 600, total = 7200)
        assertEquals(CelebrationKind.SLOT_GOAL, c?.kind)
        assertEquals(584, c?.target)
    }

    @Test
    fun `daily goal already met with inactivity off does not celebrate`() {
        assertNull(decide(steps = 900, total = 8000))
    }

    @Test
    fun `daily goal already met with inactivity on celebrates the inactivity threshold`() {
        val s = defaults.copy(inactivityEnabled = true, inactivityThreshold = 250)
        assertEquals(
            Celebration(CelebrationKind.INACTIVITY_GOAL, 250, 250, 8000, 7000),
            decide(s = s, steps = 250, total = 8000),
        )
        assertNull(decide(s = s, steps = 249, total = 8000))
    }

    @Test
    fun `a lighter plan celebrates at its own goal`() {
        val lighter = DayPlan(dailyGoal = 3000, startMinute = 9 * 60, endMinute = 15 * 60) // 500 per slot
        assertEquals(
            Celebration(CelebrationKind.SLOT_GOAL, 500, 500, 500, 3000),
            decide(steps = 500, total = 500, p = lighter),
        )
    }
}
