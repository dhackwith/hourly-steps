package com.hourlysteps.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CelebrationDeciderTest {

    private val defaults = Settings() // 7000/day, 8..19, hourly goal 584, inactivity off, celebrate on

    private fun decide(
        s: Settings = defaults,
        stepsThisHour: Int,
        todayTotal: Int,
        hour: Int = 10,
        already: Boolean = false,
    ) = CelebrationDecider.decide(s, stepsThisHour, todayTotal, hour, already)

    @Test
    fun `reaching the hourly goal celebrates`() {
        assertEquals(
            Celebration(CelebrationKind.HOURLY_GOAL, 584, 584, 2000, 7000),
            decide(stepsThisHour = 584, todayTotal = 2000),
        )
    }

    @Test
    fun `below the hourly goal does not celebrate`() {
        assertNull(decide(stepsThisHour = 583, todayTotal = 2000))
    }

    @Test
    fun `only once per hour`() {
        assertNull(decide(stepsThisHour = 900, todayTotal = 2000, already = true))
    }

    @Test
    fun `disabled setting never celebrates`() {
        assertNull(decide(s = defaults.copy(celebrate = false), stepsThisHour = 900, todayTotal = 2000))
    }

    @Test
    fun `outside the reminder window does not celebrate`() {
        assertNull(decide(stepsThisHour = 900, todayTotal = 900, hour = 7))
        assertNull(decide(stepsThisHour = 900, todayTotal = 900, hour = 20))
    }

    @Test
    fun `last slot of the window still celebrates`() {
        assertEquals(CelebrationKind.HOURLY_GOAL, decide(stepsThisHour = 600, todayTotal = 5000, hour = 19)?.kind)
    }

    @Test
    fun `crossing the daily goal in the same batch still celebrates the hour`() {
        // 6,600 before this hour, 600 this hour: the daily goal and the hourly goal fall together.
        val c = decide(stepsThisHour = 600, todayTotal = 7200)
        assertEquals(CelebrationKind.HOURLY_GOAL, c?.kind)
        assertEquals(584, c?.target)
    }

    @Test
    fun `daily goal already met with inactivity off does not celebrate`() {
        assertNull(decide(stepsThisHour = 900, todayTotal = 8000))
    }

    @Test
    fun `daily goal already met with inactivity on celebrates the inactivity threshold`() {
        val s = defaults.copy(inactivityEnabled = true, inactivityThreshold = 250)
        assertEquals(
            Celebration(CelebrationKind.INACTIVITY_GOAL, 250, 250, 8000, 7000),
            decide(s = s, stepsThisHour = 250, todayTotal = 8000),
        )
        assertNull(decide(s = s, stepsThisHour = 249, todayTotal = 8000))
    }
}
