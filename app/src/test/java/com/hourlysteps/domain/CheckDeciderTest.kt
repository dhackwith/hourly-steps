package com.hourlysteps.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class CheckDeciderTest {

    private val defaults = Settings() // 7000/day, 8:00 to 20:00, slot goal 584, inactivity off
    private val plan = defaults.schedule.planA
    private val slots = Slots.forDay(plan, 60, LocalDate.of(2026, 6, 10))

    /** The slot starting at [hour]. */
    private fun slot(hour: Int) = slots.first { it.startMinute == hour * 60 }

    private fun decide(s: Settings = defaults, steps: Int, total: Int, hour: Int = 10) =
        CheckDecider.decide(s, plan, slot(hour), steps, total)

    @Test
    fun `short of the slot goal and on pace reports shortfall without catch up`() {
        assertEquals(CheckResult.GoalShortfall(need = 284, catchUpPerSlot = null), decide(steps = 300, total = 1500))
    }

    @Test
    fun `short of the slot goal and behind pace includes catch up`() {
        // total 400 at 10:50 -> 660 per slot needed over 10 slots
        assertEquals(CheckResult.GoalShortfall(need = 212, catchUpPerSlot = 660), decide(steps = 372, total = 400))
    }

    @Test
    fun `catch up equal to the slot goal is not shown`() {
        // deficit 5840 over 10 slots = 584 exactly
        assertEquals(CheckResult.GoalShortfall(need = 484, catchUpPerSlot = null), decide(steps = 100, total = 1160))
    }

    @Test
    fun `meeting the slot goal is none`() {
        assertEquals(CheckResult.None, decide(steps = 584, total = 2000))
        assertEquals(CheckResult.None, decide(steps = 900, total = 2000))
    }

    @Test
    fun `daily goal met with inactivity off is none`() {
        assertEquals(CheckResult.None, decide(steps = 0, total = 7000, hour = 14))
        assertEquals(CheckMode.OFF, CheckDecider.modeFor(defaults, plan, 7000))
    }

    @Test
    fun `daily goal met with inactivity on and short reports inactivity shortfall`() {
        val s = defaults.copy(inactivityEnabled = true, inactivityThreshold = 250)
        assertEquals(CheckMode.INACTIVITY, CheckDecider.modeFor(s, plan, 7000))
        assertEquals(CheckResult.InactivityShortfall(need = 70), decide(s, steps = 180, total = 7000, hour = 14))
    }

    @Test
    fun `daily goal met with inactivity on and threshold met is none`() {
        val s = defaults.copy(inactivityEnabled = true, inactivityThreshold = 250)
        assertEquals(CheckResult.None, decide(s, steps = 250, total = 7000, hour = 14))
    }

    @Test
    fun `inactivity on but goal not yet met is still a goal check`() {
        val s = defaults.copy(inactivityEnabled = true, inactivityThreshold = 250)
        assertEquals(CheckMode.GOAL, CheckDecider.modeFor(s, plan, 6999))
        assertEquals(CheckResult.GoalShortfall(need = 284, catchUpPerSlot = null), decide(s, steps = 300, total = 6999, hour = 18))
    }

    @Test
    fun `last slot catch up is the full deficit`() {
        assertEquals(CheckResult.GoalShortfall(need = 584, catchUpPerSlot = 1500), decide(steps = 0, total = 5500, hour = 19))
    }

    @Test
    fun `a different plan uses its own goal`() {
        val lighter = DayPlan(dailyGoal = 3000, startMinute = 9 * 60, endMinute = 15 * 60) // 500 per slot
        val slot = Slots.forDay(lighter, 60, LocalDate.of(2026, 6, 10)).first()
        assertEquals(CheckResult.None, CheckDecider.decide(defaults, lighter, slot, 500, 500))
        assertEquals(
            CheckResult.GoalShortfall(need = 100, catchUpPerSlot = null),
            CheckDecider.decide(defaults, lighter, slot, 400, 400),
        )
    }

    @Test
    fun `30 minute slots use the smaller goal`() {
        val s = defaults.copy(schedule = defaults.schedule.copy(slotMinutes = 30))
        val slot = Slots.forDay(plan, 30, LocalDate.of(2026, 6, 10)).first()
        assertEquals(CheckResult.None, CheckDecider.decide(s, plan, slot, 292, 292))
        assertEquals(CheckResult.GoalShortfall(need = 2, catchUpPerSlot = null), CheckDecider.decide(s, plan, slot, 290, 290))
    }
}
