package com.hourlysteps.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class PaceTest {

    private val day: LocalDate = LocalDate.of(2026, 6, 10)
    private val slots = Slots.forDay(DayPlan(), 60, day) // 7000/day, 8:00 to 20:00

    @Test
    fun `pace grows by an even share each slot`() {
        assertEquals(584, Pace.target(7000, slots[0]))
        assertEquals(2917, Pace.target(7000, slots[4]))
    }

    @Test
    fun `the last slot's pace is exactly the daily goal`() {
        assertEquals(7000, Pace.target(7000, slots.last()))
    }

    @Test
    fun `behind pace is negative and ahead is positive`() {
        assertEquals(-1197, Pace.gap(7000, slots[4], 1720))
        assertEquals(0, Pace.gap(7000, slots[4], 2917))
        assertEquals(583, Pace.gap(7000, slots[4], 3500))
    }

    @Test
    fun `pace follows the interval`() {
        val halfHours = Slots.forDay(DayPlan(), 30, day)
        val twoHours = Slots.forDay(DayPlan(), 120, day)
        assertEquals(292, Pace.target(7000, halfHours[0]))
        assertEquals(1167, Pace.target(7000, twoHours[0]))
        assertEquals(7000, Pace.target(7000, halfHours.last()))
        assertEquals(7000, Pace.target(7000, twoHours.last()))
    }
}
