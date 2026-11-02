package com.hourlysteps.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CelebrationDeciderTest {

    private val defaults = Settings() // 7000/day, 8:00 to 20:00, inactivity off, celebrate on
    private val plan = defaults.schedule.planA

    /** Decides for the first slot starting at or after [hour]. */
    private fun decide(s: Settings = defaults, steps: Int, total: Int, already: Boolean = false, p: DayPlan = plan, hour: Int = 8) =
        CelebrationDecider.decide(
            s,
            p,
            Slots.forDay(p, s.schedule.slotMinutes, LocalDate.of(2026, 6, 10)).first { it.startMinute >= hour * 60 },
            steps,
            total,
            already,
        )

    @Test
    fun `reaching the slot goal celebrates`() {
        assertEquals(
            Celebration(CelebrationKind.SLOT_GOAL, 584, 584, 584, 7000),
            decide(steps = 584, total = 584),
        )
    }

    @Test
    fun `below the slot goal does not celebrate`() {
        assertNull(decide(steps = 583, total = 583))
    }

    @Test
    fun `walking extra earlier lowers the goal to celebrate`() {
        // 2,400 before 11 AM, 4,600 to go across 9 hours -> 512.
        assertNull(decide(steps = 511, total = 2911, hour = 11))
        assertEquals(512, decide(steps = 512, total = 2912, hour = 11)?.target)
    }

    @Test
    fun `falling behind doesn't raise the goal to celebrate`() {
        assertNull(decide(steps = 583, total = 1083, hour = 11))
        assertEquals(584, decide(steps = 584, total = 1084, hour = 11)?.target)
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
        // 6,600 before the last slot, 600 in it: the daily goal and the slot goal fall together.
        val c = decide(steps = 600, total = 7200, hour = 19)
        assertEquals(CelebrationKind.SLOT_GOAL, c?.kind)
        assertEquals(400, c?.target)
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

    private fun stepsToNext(
        s: Settings = defaults,
        steps: Int,
        total: Int,
        slotDone: Boolean = false,
        dayDone: Boolean = false,
        hour: Int? = 8,
    ) = CelebrationDecider.stepsToNext(
        s,
        plan,
        hour?.let { h -> Slots.forDay(plan, s.schedule.slotMinutes, LocalDate.of(2026, 6, 10)).first { it.startMinute == h * 60 } },
        steps,
        total,
        slotDone,
        dayDone,
    )

    @Test
    fun `the next goal is this slot's while it's still open`() {
        assertEquals(84, stepsToNext(steps = 500, total = 500))
    }

    @Test
    fun `the next goal is the daily one once this slot has been celebrated`() {
        assertEquals(6300, stepsToNext(steps = 700, total = 700, slotDone = true))
    }

    @Test
    fun `the daily goal is next when it's closer than the slot's`() {
        // 6,900 before the last slot, so this hour asks for 100, and 50 of them are done.
        assertEquals(50, stepsToNext(steps = 50, total = 6950, hour = 19))
        // The same hour, but the wearer only cares about the day.
        assertEquals(50, stepsToNext(s = defaults.copy(celebrate = false), steps = 50, total = 6950, hour = 19))
    }

    @Test
    fun `outside the window only the daily goal is next`() {
        assertEquals(1000, stepsToNext(steps = 0, total = 6000, hour = null))
    }

    @Test
    fun `nothing is next when both have been celebrated`() {
        assertNull(stepsToNext(steps = 700, total = 7100, slotDone = true, dayDone = true))
    }

    @Test
    fun `nothing is next when celebrations are off`() {
        assertNull(stepsToNext(s = defaults.copy(celebrate = false, celebrateDaily = false), steps = 500, total = 500))
    }

    @Test
    fun `an inactivity threshold counts as a goal once the day's is met`() {
        val s = defaults.copy(inactivityEnabled = true, inactivityThreshold = 250)
        assertEquals(50, stepsToNext(s = s, steps = 200, total = 8000, dayDone = true))
    }

    @Test
    fun `goals already passed aren't next`() {
        assertNull(stepsToNext(steps = 900, total = 8000, dayDone = true))
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
