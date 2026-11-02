package com.hourlysteps.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaceTest {

    private val day: LocalDate = LocalDate.of(2026, 6, 10)
    private val plan = DayPlan() // 7000/day, 8:00 to 20:00
    private val slots = Slots.forDay(plan, 60, day)

    private fun minute(hour: Int, minute: Int = 0) = hour * 60 + minute

    @Test
    fun `pace follows the clock`() {
        assertEquals(0, Pace.target(7000, slots[0], minute(8)))
        assertEquals(2334, Pace.target(7000, slots[4], minute(12)))
        assertEquals(2820, Pace.target(7000, slots[4], minute(12, 50)))
    }

    @Test
    fun `pace reaches the daily goal exactly at the end of the window`() {
        assertEquals(7000, Pace.target(7000, slots.last(), minute(20)))
    }

    @Test
    fun `pace stays within the slot when the time is outside it`() {
        assertEquals(Pace.target(7000, slots[4], minute(12)), Pace.target(7000, slots[4], minute(11)))
        assertEquals(Pace.target(7000, slots[4], minute(13)), Pace.target(7000, slots[4], minute(14)))
    }

    @Test
    fun `behind pace is negative and ahead is positive`() {
        assertEquals(-1100, Pace.gap(7000, slots[4], minute(12, 50), 1720))
        assertEquals(0, Pace.gap(7000, slots[4], minute(12, 50), 2820))
        assertEquals(680, Pace.gap(7000, slots[4], minute(12, 50), 3500))
    }

    @Test
    fun `being on track at the top of the hour is on pace`() {
        assertEquals(0, Pace.gap(7000, slots[4], minute(12), 2334))
    }

    @Test
    fun `the interval doesn't change where pace is`() {
        val time = minute(10, 20)
        val expected = Pace.target(7000, slots[2], time)
        assertEquals(expected, Pace.target(7000, Slots.forDay(plan, 30, day)[4], time))
        assertEquals(expected, Pace.target(7000, Slots.forDay(plan, 120, day)[1], time))
    }

    @Test
    fun `a day status only has pace while the goal is open`() {
        val at = java.time.ZonedDateTime.of(day, java.time.LocalTime.of(12, 50), java.time.ZoneId.of("America/New_York"))
        assertEquals(-1100, DayStatus.compute(at, Settings(), 100, 1720).paceGap)
        assertNull(DayStatus.compute(at, Settings(), 100, 7000).paceGap)
        assertNull(DayStatus.compute(at.withHour(22), Settings(), 0, 1720).paceGap)
    }
}
