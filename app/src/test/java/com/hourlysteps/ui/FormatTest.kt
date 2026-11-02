package com.hourlysteps.ui

import com.hourlysteps.domain.DayAssignment
import com.hourlysteps.domain.Schedule
import com.hourlysteps.domain.ScheduleMode
import com.hourlysteps.domain.Settings
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    private val uk = Locale.UK // the week starts on Monday
    private val us = Locale.US // the week starts on Sunday

    private fun week(vararg pairs: Pair<DayOfWeek, DayAssignment>) =
        DayOfWeek.entries.associateWith { DayAssignment.PLAN_A } + pairs

    @Test
    fun `week order follows the locale`() {
        assertEquals(MONDAY, orderedWeek(uk).first())
        assertEquals(SUNDAY, orderedWeek(us).first())
    }

    @Test
    fun `every day on is summarised`() {
        assertEquals("Every day", formatWeek(Schedule(), uk))
    }

    @Test
    fun `one plan lists only the days off`() {
        val s = Schedule(assignments = week(SATURDAY to DayAssignment.OFF, SUNDAY to DayAssignment.OFF))
        assertEquals("Off: Sat, Sun", formatWeek(s, uk))
    }

    @Test
    fun `all days off is called out`() {
        val s = Schedule(assignments = DayOfWeek.entries.associateWith { DayAssignment.OFF })
        assertEquals("All days off", formatWeek(s, uk))
    }

    @Test
    fun `two plans group runs of three or more into ranges`() {
        val s = Schedule(
            mode = ScheduleMode.TWO_PLANS,
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
        assertEquals("A: Thu–Sun · B: Mon · Off: Tue, Wed", formatWeek(s, uk))
    }

    @Test
    fun `one plan on all seven days reads as every day`() {
        assertEquals("A: Every day", formatWeek(Schedule(mode = ScheduleMode.TWO_PLANS), us))
    }

    @Test
    fun `an unused plan is left out`() {
        val s = Schedule(mode = ScheduleMode.TWO_PLANS, assignments = week(SUNDAY to DayAssignment.OFF))
        assertEquals("A: Mon–Sat · Off: Sun", formatWeek(s, uk))
    }

    @Test
    fun `split runs stay separate`() {
        val s = Schedule(
            mode = ScheduleMode.TWO_PLANS,
            assignments = week(MONDAY to DayAssignment.PLAN_B, WEDNESDAY to DayAssignment.PLAN_B, FRIDAY to DayAssignment.PLAN_B),
        )
        assertEquals("A: Tue, Thu, Sat, Sun · B: Mon, Wed, Fri", formatWeek(s, uk))
    }

    @Test
    fun `pause end reads naturally`() {
        val today = LocalDate.of(2026, 11, 2)
        assertEquals("the end of today", formatPauseEnd(today, today, us))
        assertEquals("Tue, Nov 3", formatPauseEnd(today.plusDays(1), today, us))
        assertEquals("you resume", formatPauseEnd(Settings.PAUSED_UNTIL_RESUMED, today, us))
    }


    private fun time(hour: Int, minute: Int) = formatTime(java.time.LocalTime.of(hour, minute))

    @Test
    fun `a plan shows its real check times for the interval`() {
        val plan = com.hourlysteps.domain.DayPlan()
        assertEquals("7,000 · ${time(8, 50)}–${time(19, 50)}", formatPlan(plan, 60))
        assertEquals("7,000 · ${time(8, 25)}–${time(19, 55)}", formatPlan(plan, 30))
        assertEquals("7,000 · ${time(9, 40)}–${time(19, 40)}", formatPlan(plan, 120))
    }

    @Test
    fun `the plan caption follows the interval`() {
        val plan = com.hourlysteps.domain.DayPlan()
        assertEquals("24 reminders · ≈ 292 steps per 30 min", formatPlanCaption(plan, 30))
        assertEquals("12 reminders · ≈ 584 steps per hour", formatPlanCaption(plan, 60))
        assertEquals("6 reminders · ≈ 1,167 steps per 2 hours", formatPlanCaption(plan, 120))
    }

    @Test
    fun `the picker caption follows the interval`() {
        val plan = com.hourlysteps.domain.DayPlan()
        assertEquals("24 reminders · ≈ 292/30 min", formatPickerCaption(plan, 30))
        assertEquals("12 reminders · ≈ 584/hr", formatPickerCaption(plan, 60))
        assertEquals("≈ 1,167/2 hr", formatSlotGoal(plan, 120))
    }

    @Test
    fun `one reminder is singular`() {
        val plan = com.hourlysteps.domain.DayPlan(startMinute = 8 * 60, endMinute = 9 * 60)
        assertEquals("1 reminder · ≈ 7,000 steps per hour", formatPlanCaption(plan, 60))
    }


    @Test
    fun `the pace line reads naturally`() {
        assertEquals("1,197 behind pace", formatPaceLine(-1197))
        assertEquals("On pace", formatPaceLine(0))
        assertEquals("583 ahead of pace", formatPaceLine(583))
    }
}
