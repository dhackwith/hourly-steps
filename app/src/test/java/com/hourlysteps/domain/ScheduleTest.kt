package com.hourlysteps.domain

import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleTest {

    private val date: LocalDate = LocalDate.of(2026, 6, 10) // a Wednesday
    private val zone: ZoneId = ZoneId.of("America/New_York")
    private val planB = DayPlan(dailyGoal = 5000, startMinute = 9 * 60, endMinute = 18 * 60)

    /** Thursday to Sunday on Plan A, Monday on Plan B, Tuesday and Wednesday off. */
    private val irregular = Schedule(
        mode = ScheduleMode.TWO_PLANS,
        planB = planB,
        assignments = mapOf(
            MONDAY to DayAssignment.PLAN_B,
            TUESDAY to DayAssignment.OFF,
            WEDNESDAY to DayAssignment.OFF,
            THURSDAY to DayAssignment.PLAN_A,
            FRIDAY to DayAssignment.PLAN_A,
            SATURDAY to DayAssignment.PLAN_A,
            SUNDAY to DayAssignment.PLAN_A,
        ),
    )

    private fun at(day: LocalDate, hour: Int, minute: Int) =
        ZonedDateTime.of(day, LocalTime.of(hour, minute), zone)

    @Test
    fun `default plan has the same 12 hourly slots as v1`() {
        val slots = Slots.forDay(DayPlan(), 60, date)
        assertEquals(12, slots.size)
        assertEquals(8 * 60 + 50, slots.first().checkMinute)
        assertEquals(19 * 60 + 50, slots.last().checkMinute)
    }

    @Test
    fun `check leads the slot end by a sixth of its length`() {
        val lead = { minutes: Int -> Slots.forDay(DayPlan(), minutes, date).first().leadMinutes }
        assertEquals(5, lead(30))
        assertEquals(10, lead(60))
        assertEquals(20, lead(120))
    }

    @Test
    fun `30 minute slots double the count and check at 25 and 55`() {
        val slots = Slots.forDay(DayPlan(), 30, date)
        assertEquals(24, slots.size)
        assertEquals(8 * 60 + 25, slots[0].checkMinute)
        assertEquals(8 * 60 + 55, slots[1].checkMinute)
    }

    @Test
    fun `120 minute slots halve the count and check at 40 past the second hour`() {
        val slots = Slots.forDay(DayPlan(), 120, date)
        assertEquals(6, slots.size)
        assertEquals(9 * 60 + 40, slots[0].checkMinute)
    }

    @Test
    fun `a window that is not a multiple of the slot length ends with a shorter slot`() {
        val plan = DayPlan(startMinute = 9 * 60, endMinute = 20 * 60) // 11 hours
        val slots = Slots.forDay(plan, 120, date)
        assertEquals(6, slots.size)
        assertEquals(60, slots.last().lengthMinutes)
        assertEquals(20 * 60, slots.last().endMinute)
        assertEquals(10, slots.last().leadMinutes)
    }

    @Test
    fun `a one hour window with two hour slots still has one slot`() {
        val plan = DayPlan(startMinute = 12 * 60, endMinute = 13 * 60)
        assertEquals(listOf(12 * 60 to 13 * 60), Slots.boundaries(plan, 120))
    }

    @Test
    fun `an empty window has no slots`() {
        assertEquals(emptyList<Pair<Int, Int>>(), Slots.boundaries(DayPlan(startMinute = 600, endMinute = 600), 60))
    }

    @Test
    fun `a window ending at midnight gives a slot ending on the next day`() {
        val plan = DayPlan(startMinute = 23 * 60, endMinute = 24 * 60)
        val slot = Slots.forDay(plan, 60, date).single()
        assertEquals(at(date, 23, 50), slot.checkAt(zone))
        assertEquals(at(date.plusDays(1), 0, 0), slot.endAt(zone))
    }

    @Test
    fun `slot lookup finds the slot containing a time`() {
        val slot = Slots.at(Schedule(), at(date, 10, 20))
        assertEquals(10 * 60, slot?.startMinute)
        assertEquals(2, slot?.index)
        assertEquals(12, slot?.count)
    }

    @Test
    fun `slot lookup treats the end of a slot as the next one`() {
        assertEquals(11 * 60, Slots.at(Schedule(), at(date, 11, 0))?.startMinute)
    }

    @Test
    fun `slot lookup is null outside the window`() {
        assertNull(Slots.at(Schedule(), at(date, 7, 59)))
        assertNull(Slots.at(Schedule(), at(date, 20, 0)))
    }

    @Test
    fun `slot lookup is null on a day off`() {
        assertNull(Slots.at(irregular, at(date, 10, 0))) // date is a Wednesday
    }

    @Test
    fun `an irregular week picks the assigned plan for each day`() {
        assertEquals(planB, irregular.planFor(MONDAY))
        assertNull(irregular.planFor(TUESDAY))
        assertNull(irregular.planFor(WEDNESDAY))
        assertEquals(irregular.planA, irregular.planFor(THURSDAY))
        assertEquals(irregular.planA, irregular.planFor(SUNDAY))
    }

    @Test
    fun `plans can have different goals and windows`() {
        val monday = Slots.forDay(irregular.planFor(MONDAY)!!, 60, date)
        assertEquals(9, monday.size)
        assertEquals(556, GoalCalculator.slotGoal(planB, 60))
        assertEquals(584, GoalCalculator.slotGoal(irregular.planA, 60))
    }

    @Test
    fun `one plan mode ignores Plan B assignments but keeps days off`() {
        val onePlan = irregular.copy(mode = ScheduleMode.EVERY_DAY)
        assertEquals(onePlan.planA, onePlan.planFor(MONDAY))
        assertNull(onePlan.planFor(TUESDAY))
    }

    @Test
    fun `cycling goes A then B then off with two plans`() {
        val start = Schedule(mode = ScheduleMode.TWO_PLANS)
        val b = start.cycled(FRIDAY)
        assertEquals(DayAssignment.PLAN_B, b.assignmentFor(FRIDAY))
        val off = b.cycled(FRIDAY)
        assertEquals(DayAssignment.OFF, off.assignmentFor(FRIDAY))
        assertEquals(DayAssignment.PLAN_A, off.cycled(FRIDAY).assignmentFor(FRIDAY))
    }

    @Test
    fun `cycling toggles on and off with one plan`() {
        val off = Schedule().cycled(SATURDAY)
        assertEquals(DayAssignment.OFF, off.assignmentFor(SATURDAY))
        assertEquals(DayAssignment.PLAN_A, off.cycled(SATURDAY).assignmentFor(SATURDAY))
    }

    @Test
    fun `cycling one day leaves the others alone`() {
        val cycled = Schedule().cycled(SATURDAY)
        (DayOfWeek.entries - SATURDAY).forEach { assertEquals(DayAssignment.PLAN_A, cycled.assignmentFor(it)) }
    }

    @Test
    fun `the first hour edit pushes the last hour later if needed`() {
        val plan = DayPlan().withFirstHour(21)
        assertEquals(21, plan.firstHour)
        assertEquals(21, plan.lastHour)
    }

    @Test
    fun `the last hour edit pulls the first hour earlier if needed`() {
        val plan = DayPlan().withLastHour(6)
        assertEquals(6, plan.firstHour)
        assertEquals(6, plan.lastHour)
    }

    @Test
    fun `the last hour can be 11 PM`() {
        val plan = DayPlan().withLastHour(23)
        assertEquals(23, plan.lastHour)
        assertEquals(24 * 60, plan.endMinute)
    }
}
