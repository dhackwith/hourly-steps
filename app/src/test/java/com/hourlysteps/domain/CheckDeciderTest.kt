package com.hourlysteps.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CheckDeciderTest {

    private val defaults = Settings() // 7000/day, 8..19, hourly goal 584, inactivity off

    @Test
    fun `short of the hourly goal and on pace reports shortfall without catch up`() {
        val result = CheckDecider.decide(defaults, stepsThisHour = 300, todayTotal = 1500, hour = 10)
        assertEquals(CheckResult.GoalShortfall(need = 284, catchUpPerHour = null), result)
    }

    @Test
    fun `short of the hourly goal and behind pace includes catch up`() {
        // total 400 at 10:50 -> 660/hr needed over 10 slots
        val result = CheckDecider.decide(defaults, stepsThisHour = 372, todayTotal = 400, hour = 10)
        assertEquals(CheckResult.GoalShortfall(need = 212, catchUpPerHour = 660), result)
    }

    @Test
    fun `catch up equal to the hourly goal is not shown`() {
        // deficit 5840 over 10 slots = 584 exactly
        val result = CheckDecider.decide(defaults, stepsThisHour = 100, todayTotal = 1160, hour = 10)
        assertEquals(CheckResult.GoalShortfall(need = 484, catchUpPerHour = null), result)
    }

    @Test
    fun `meeting the hourly goal is none`() {
        assertEquals(CheckResult.None, CheckDecider.decide(defaults, 584, 2000, 10))
        assertEquals(CheckResult.None, CheckDecider.decide(defaults, 900, 2000, 10))
    }

    @Test
    fun `outside the window is none even when short`() {
        assertEquals(CheckResult.None, CheckDecider.decide(defaults, 0, 0, 7))
        assertEquals(CheckResult.None, CheckDecider.decide(defaults, 0, 0, 20))
    }

    @Test
    fun `daily goal met with inactivity off is none`() {
        assertEquals(CheckResult.None, CheckDecider.decide(defaults, 0, 7000, 14))
        assertEquals(CheckMode.OFF, CheckDecider.modeFor(defaults, 7000))
    }

    @Test
    fun `daily goal met with inactivity on and short reports inactivity shortfall`() {
        val s = defaults.copy(inactivityEnabled = true, inactivityThreshold = 250)
        assertEquals(CheckMode.INACTIVITY, CheckDecider.modeFor(s, 7000))
        assertEquals(CheckResult.InactivityShortfall(need = 70), CheckDecider.decide(s, 180, 7000, 14))
    }

    @Test
    fun `daily goal met with inactivity on and threshold met is none`() {
        val s = defaults.copy(inactivityEnabled = true, inactivityThreshold = 250)
        assertEquals(CheckResult.None, CheckDecider.decide(s, 250, 7000, 14))
    }

    @Test
    fun `inactivity on but goal not yet met is still a goal check`() {
        val s = defaults.copy(inactivityEnabled = true, inactivityThreshold = 250)
        assertEquals(CheckMode.GOAL, CheckDecider.modeFor(s, 6999))
        assertEquals(CheckResult.GoalShortfall(need = 284, catchUpPerHour = null), CheckDecider.decide(s, 300, 6999, 18))
    }

    @Test
    fun `last slot catch up is the full deficit`() {
        val result = CheckDecider.decide(defaults, stepsThisHour = 0, todayTotal = 5500, hour = 19)
        assertEquals(CheckResult.GoalShortfall(need = 584, catchUpPerHour = 1500), result)
    }
}
