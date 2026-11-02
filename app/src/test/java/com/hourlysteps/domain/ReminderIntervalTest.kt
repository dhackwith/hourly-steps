package com.hourlysteps.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderIntervalTest {

    private val zone: ZoneId = ZoneId.of("America/New_York")
    private val day: LocalDate = LocalDate.of(2026, 6, 10) // a Wednesday
    private val plan = DayPlan() // 7000/day, 8:00 to 20:00

    /** The default window cut short to 7 PM, so two-hour slots leave a one-hour slot at the end. */
    private val oddWindow = DayPlan(endMinute = 19 * 60)

    private fun settings(slotMinutes: Int, p: DayPlan = plan, inactivity: Boolean = false) =
        Settings(schedule = Schedule(planA = p, slotMinutes = slotMinutes), inactivityEnabled = inactivity)

    private fun at(hour: Int, minute: Int = 0) = ZonedDateTime.of(day, LocalTime.of(hour, minute), zone)

    private fun slotAt(slotMinutes: Int, hour: Int, minute: Int = 0, p: DayPlan = plan) =
        Slots.at(settings(slotMinutes, p).schedule, at(hour, minute))!!

    @Test
    fun `each interval divides the daily goal across its slots`() {
        assertEquals(24, GoalCalculator.slotCount(plan, 30))
        assertEquals(292, GoalCalculator.slotGoal(plan, 30))
        assertEquals(12, GoalCalculator.slotCount(plan, 60))
        assertEquals(584, GoalCalculator.slotGoal(plan, 60))
        assertEquals(6, GoalCalculator.slotCount(plan, 120))
        assertEquals(1167, GoalCalculator.slotGoal(plan, 120))
    }

    @Test
    fun `the first and last checks follow the interval`() {
        assertEquals(8 * 60 + 25, Slots.checkMinutes(plan, 30).first())
        assertEquals(19 * 60 + 55, Slots.checkMinutes(plan, 30).last())
        assertEquals(8 * 60 + 50, Slots.checkMinutes(plan, 60).first())
        assertEquals(19 * 60 + 50, Slots.checkMinutes(plan, 60).last())
        assertEquals(9 * 60 + 40, Slots.checkMinutes(plan, 120).first())
        assertEquals(19 * 60 + 40, Slots.checkMinutes(plan, 120).last())
    }

    @Test
    fun `the next check follows the interval`() {
        assertEquals(at(8, 55), ReminderSchedule.nextCheckTime(at(8, 25), settings(30)))
        assertEquals(at(11, 40), ReminderSchedule.nextCheckTime(at(9, 40), settings(120)))
    }

    @Test
    fun `the second nudge comes halfway between the check and the end`() {
        assertEquals(at(8, 27).plusSeconds(30), slotAt(30, 8).secondNudgeAt(zone))
        assertEquals(at(8, 55), slotAt(60, 8).secondNudgeAt(zone))
        assertEquals(at(9, 50), slotAt(120, 8).secondNudgeAt(zone))
    }

    @Test
    fun `a window that doesn't divide evenly ends with a shorter slot`() {
        val last = slotAt(120, 18, p = oddWindow)
        assertEquals(60, last.lengthMinutes)
        assertEquals(at(18, 50), last.checkAt(zone))
    }

    @Test
    fun `snooze only fits when the slot lasts long enough`() {
        assertNull(ReminderSchedule.snoozeUntil(slotAt(30, 8), at(8, 25)))
        assertEquals(at(8, 55), ReminderSchedule.snoozeUntil(slotAt(60, 8), at(8, 50)))
        assertEquals(at(9, 45), ReminderSchedule.snoozeUntil(slotAt(120, 8), at(9, 40)))
    }

    @Test
    fun `the inactivity rate scales with the slot's length`() {
        assertEquals(125, GoalCalculator.inactivityGoal(250, 30))
        assertEquals(250, GoalCalculator.inactivityGoal(250, 60))
        assertEquals(500, GoalCalculator.inactivityGoal(250, 120))
        assertEquals(128, GoalCalculator.inactivityGoal(255, 30))
    }

    @Test
    fun `an inactivity check asks for the slot's share of the rate`() {
        fun need(slotMinutes: Int, slot: Slot, p: DayPlan = plan) =
            (CheckDecider.decide(settings(slotMinutes, p, inactivity = true), p, slot, 100, 8000) as CheckResult.InactivityShortfall).need

        assertEquals(25, need(30, slotAt(30, 8)))
        assertEquals(150, need(60, slotAt(60, 8)))
        assertEquals(400, need(120, slotAt(120, 8)))
        assertEquals(150, need(120, slotAt(120, 18, p = oddWindow), oddWindow))
    }

    @Test
    fun `an inactivity check passes once the slot's share is reached`() {
        val result = CheckDecider.decide(settings(30, inactivity = true), plan, slotAt(30, 8), 125, 8000)
        assertEquals(CheckResult.None, result)
    }

    @Test
    fun `a goal check reports the shortfall against the interval's goal`() {
        val result = CheckDecider.decide(settings(30), plan, slotAt(30, 14), 100, 1000)
        // 900 before this half hour, 6,100 to go across 12 half hours -> 509.
        assertEquals(CheckResult.GoalShortfall(need = 409), result)
    }

    @Test
    fun `the status target for inactivity follows the interval`() {
        val status = DayStatus.compute(at(8, 10), settings(30, inactivity = true), 50, 8000)
        assertEquals(125, status.slotTarget)
    }

    @Test
    fun `a celebration carries its slot's length`() {
        val slot = CelebrationDecider.decide(settings(120), plan, slotAt(120, 8), 1167, 1167, false)
        assertEquals(CelebrationKind.SLOT_GOAL, slot?.kind)
        assertEquals(1167, slot?.target)
        assertEquals(120, slot?.slotMinutes)
    }

    @Test
    fun `an inactivity celebration asks for the slot's share of the rate`() {
        val s = settings(30, inactivity = true)
        assertNull(CelebrationDecider.decide(s, plan, slotAt(30, 8), 124, 8000, false))
        val reached = CelebrationDecider.decide(s, plan, slotAt(30, 8), 125, 8000, false)
        assertEquals(CelebrationKind.INACTIVITY_GOAL, reached?.kind)
        assertEquals(125, reached?.target)
    }

    @Test
    fun `every interval the setting offers has its own words`() {
        assertEquals(listOf(30, 60, 120), Settings.SLOT_MINUTE_OPTIONS)
        assertEquals(
            listOf("this half hour", "this hour", "these two hours"),
            Settings.SLOT_MINUTE_OPTIONS.map(SlotWording::current),
        )
        assertEquals(
            listOf("Every 30 minutes", "Every hour", "Every 2 hours"),
            Settings.SLOT_MINUTE_OPTIONS.map(SlotWording::interval),
        )
        assertEquals(
            listOf("/30 min", "/hr", "/2 hr"),
            Settings.SLOT_MINUTE_OPTIONS.map(SlotWording::perSlotShort),
        )
        assertEquals(
            listOf("Half-hour goal", "Hourly goal", "Two-hour goal"),
            Settings.SLOT_MINUTE_OPTIONS.map(SlotWording::goalName),
        )
        assertTrue(SlotWording.plural(30) != SlotWording.plural(120))
    }
}
